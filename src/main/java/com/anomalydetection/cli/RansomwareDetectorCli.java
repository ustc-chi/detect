package com.anomalydetection.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import com.anomalydetection.features.RansomwareFeatureVector;
import com.anomalydetection.features.RansomwareFeatureExtractor;
import com.anomalydetection.features.SuspiciousExtensions;
import com.anomalydetection.detector.BaselineStatistics;
import com.anomalydetection.detector.WeightedEuclideanScorer;
import com.anomalydetection.detector.AnomalyThreshold;
import com.anomalydetection.detector.RansomwareDetector;
import com.anomalydetection.detector.DetectionResult;
import com.anomalydetection.model.SnapdiffRecord;
import com.anomalydetection.parser.StreamingSnapdiffParser;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/** Command line interface for ransomware detector.
 *<p>
 * This CLI:
 * - collects baseline vectors from a directory of snapdiff JSONs
 * - calibrates a detector using those vectors
 * - processes input snapdiff files and writes a CSV report
 */
@Command(name = "ransomware-detector", mixinStandardHelpOptions = true, version = "1.0")
public class RansomwareDetectorCli implements Callable<Integer> {

    @Option(names = {"--baseline-dir"})
    Path baselineDir;

    @Option(names = {"--input-dir"}, required = true)
    Path inputDir;

    @Option(names = {"--output-file"}, required = true)
    Path outputFile;

    @Option(names = {"--window-size"}, defaultValue = "10")
    int windowSize;

    @Option(names = {"--threshold-percentile"}, defaultValue = "97.0")
    double thresholdPercentile;

    @Option(names = {"--threshold-iqr-multiplier"}, defaultValue = "2.5",
            description = "IQR multiplier for baseline outlier filtering (0 to disable)")
    double thresholdIqrMultiplier;

    @Option(names = {"--suspicious-extensions-file"})
    Path suspiciousExtensionsFile;

    @Option(names = {"--weights"}, split = ",", description = "12 comma-separated weights for features")
    double[] weights;

    @Option(names = {"--include-normal"})
    boolean includeNormal;

    @Option(names = {"--days-between"}, defaultValue = "2.0", description = "Days between snapshots for feature normalization")
    double daysBetween;

    @Option(names = {"--direction-threshold"}, defaultValue = "0.75",
            description = "Direction validation threshold (0 to disable, default 0.75)")
    double directionThreshold;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new RansomwareDetectorCli()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        try {
            if (directionThreshold < 0 || directionThreshold > 1) {
                System.err.println("Error: --direction-threshold must be between 0 and 1");
                return 1;
            }

            // 1) load baseline vectors
            List<RansomwareFeatureVector> baselineVectors = new ArrayList<>();
            List<Path> baselineFiles = new ArrayList<>();
            RansomwareFeatureExtractor extractor = new RansomwareFeatureExtractor(null, daysBetween);

            if (baselineDir != null) {
                baselineFiles = Files.list(baselineDir)
                        .filter(Files::isRegularFile)
                        .sorted(Comparator.naturalOrder())
                        .collect(Collectors.toList());

                for (Path f : baselineFiles) {
                    RansomwareFeatureVector vec = extractor.extractFromFile(f);
                    baselineVectors.add(vec);
                }
            }

            double[] w = (weights != null && weights.length == RansomwareFeatureVector.FEATURE_COUNT)
                    ? weights
                    : WeightedEuclideanScorer.DEFAULT_WEIGHTS;

            // 2) build detector (warmup or statistical)
            RansomwareDetector detector;
            if (baselineVectors.isEmpty()) {
                detector = new RansomwareDetector(w);
            } else {
                BaselineStatistics stats = new BaselineStatistics(baselineVectors);

                StreamingSnapdiffParser streamingParser = new StreamingSnapdiffParser();
                for (Path f : baselineFiles) {
                    List<SnapdiffRecord> records = new ArrayList<>();
                    streamingParser.parse(f, records::add);
                    int totalOps = records.size();
                    int hour = extractDominantHourFromRecords(records);
                    if (hour >= 0) {
                        stats.addHourlyObservation(hour, totalOps);
                    }
                }
                stats.computeHourlyStats();

                WeightedEuclideanScorer scorer = new WeightedEuclideanScorer(stats, w);
                AnomalyThreshold threshold = new AnomalyThreshold(baselineVectors, scorer, thresholdPercentile, thresholdIqrMultiplier);
                detector = new RansomwareDetector(stats, threshold, w, extractor, directionThreshold);
            }

            // 3) process inputs and write CSV
            try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
                writer.write("snapshot,score,threshold,anomaly,signature,top_deviations,warmup,baseline_count");
                writer.newLine();

                List<Path> inputFiles = Files.list(inputDir)
                        .filter(Files::isRegularFile)
                        .sorted(Comparator.naturalOrder())
                        .collect(Collectors.toList());

                for (Path inFile : inputFiles) {
                    boolean wasWarmup = detector.isInWarmupMode();
                    DetectionResult result = detector.detectFromFile(inFile);
                    int baselineCount = detector.getBaselineCount();

                    String snapshotName = inFile.getFileName().toString();
                    String topDev = result.getTopDeviations().toString();
                    String sigMatch = result.getSignatureMatch() != null ? result.getSignatureMatch() : "";
                    String line = String.format("%s,%f,%f,%b,%s,%s,%b,%d",
                            snapshotName,
                            result.getScore(),
                            result.getThreshold(),
                            result.isAnomaly(),
                            sigMatch,
                            topDev,
                            wasWarmup,
                            baselineCount);
                    writer.write(line);
                    writer.newLine();
                }
            }
            return 0;
        } catch (IOException e) {
            System.err.println("IO error: " + e.getMessage());
            return 1;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    private static int extractDominantHourFromRecords(List<SnapdiffRecord> records) {
        if (records == null || records.isEmpty()) {
            return -1;
        }
        Instant earliest = null;
        for (SnapdiffRecord rec : records) {
            Instant time = rec.getChangeTime();
            if (time != null && !time.equals(Instant.EPOCH)) {
                if (earliest == null || time.isBefore(earliest)) {
                    earliest = time;
                }
            }
        }
        if (earliest == null) {
            return -1;
        }
        return earliest.atOffset(ZoneOffset.UTC).getHour();
    }
}
