package com.anomalydetection.detector;

import com.anomalydetection.features.RansomwareFeatureExtractor;
import com.anomalydetection.features.RansomwareFeatureVector;
import com.anomalydetection.model.SnapdiffFile;
import com.anomalydetection.model.SnapdiffRecord;
import com.anomalydetection.parser.StreamingSnapdiffParser;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class RansomwareDetector {
    private static final Logger LOG = Logger.getLogger(RansomwareDetector.class.getName());

    private BaselineStatistics baselineStats;
    private WeightedEuclideanScorer scorer;
    private ZScoreExplainer explainer;
    private AnomalyThreshold threshold;
    private final List<RansomwareFeatureVector> window;
    private final int maxWindowSize;
    private final RansomwareSignatureDetector signatureDetector;
    private final RansomwareFeatureExtractor featureExtractor;
    private final double daysBetweenSnapshots;
    private DirectionalValidator directionalValidator;
    private boolean lastResultReversed = false;

    private WarmupDetector warmupDetector;
    private List<RansomwareFeatureVector> warmupBaseline;
    private int warmupRoundCount;
    private static final int MIN_BASELINE_FOR_STATISTICAL = 5;
    private boolean inWarmupMode;
    private double[] weights;

    public double getDaysBetweenSnapshots() { return daysBetweenSnapshots; }

    public RansomwareDetector(BaselineStatistics stats, AnomalyThreshold threshold) {
        this(stats, threshold, WeightedEuclideanScorer.DEFAULT_WEIGHTS);
    }

    public RansomwareDetector(BaselineStatistics stats, AnomalyThreshold threshold, double[] weights) {
        this(stats, threshold, weights, new RansomwareFeatureExtractor(null));
    }

    public RansomwareDetector(BaselineStatistics stats, AnomalyThreshold threshold, double[] weights, RansomwareFeatureExtractor extractor) {
        this.weights = weights;
        this.window = new ArrayList<>();
        this.maxWindowSize = 10;
        this.signatureDetector = new RansomwareSignatureDetector();
        this.featureExtractor = extractor;
        this.daysBetweenSnapshots = extractor.getDaysBetweenSnapshots();
        this.directionalValidator = null;

        if (stats == null || threshold == null) {
            this.warmupDetector = new WarmupDetector();
            this.warmupBaseline = new ArrayList<>();
            this.warmupRoundCount = 0;
            this.inWarmupMode = true;
            this.baselineStats = null;
            this.threshold = null;
            this.scorer = null;
            this.explainer = null;
        } else {
            this.inWarmupMode = false;
            this.baselineStats = stats;
            this.threshold = threshold;
            this.scorer = new WeightedEuclideanScorer(stats, weights);
            this.explainer = new ZScoreExplainer(stats);
            if (this.featureExtractor != null) {
                this.featureExtractor.setBaselineStats(stats);
            }
        }
    }

    public RansomwareDetector(double[] weights) {
        this(null, null, weights, new RansomwareFeatureExtractor(null));
    }

    public RansomwareDetector(BaselineStatistics stats, AnomalyThreshold threshold, double[] weights, RansomwareFeatureExtractor extractor, double directionThreshold) {
        this.weights = weights;
        this.window = new ArrayList<>();
        this.maxWindowSize = 10;
        this.signatureDetector = new RansomwareSignatureDetector();
        this.featureExtractor = extractor;
        this.daysBetweenSnapshots = extractor.getDaysBetweenSnapshots();

        if (directionThreshold > 0) {
            this.directionalValidator = new DirectionalValidator(weights, directionThreshold);
        } else {
            this.directionalValidator = null;
        }

        if (stats == null || threshold == null) {
            this.warmupDetector = new WarmupDetector();
            this.warmupBaseline = new ArrayList<>();
            this.warmupRoundCount = 0;
            this.inWarmupMode = true;
            this.baselineStats = null;
            this.threshold = null;
            this.scorer = null;
            this.explainer = null;
        } else {
            this.inWarmupMode = false;
            this.baselineStats = stats;
            this.threshold = threshold;
            this.scorer = new WeightedEuclideanScorer(stats, weights);
            this.explainer = new ZScoreExplainer(stats);
            if (this.featureExtractor != null) {
                this.featureExtractor.setBaselineStats(stats);
            }
        }
    }

    public DetectionResult detect(RansomwareFeatureVector vector) {
        return detect(vector, Collections.emptyList());
    }

    public DetectionResult detectFromFile(Path filePath) throws IOException {
        lastResultReversed = false;

        StreamingSnapdiffParser streamingParser = new StreamingSnapdiffParser();
        List<SnapdiffRecord> records = new ArrayList<>();
        streamingParser.parse(filePath, records::add);

        RansomwareSignatureDetector.SignatureResult sig = signatureDetector.scan(records);
        if (sig.matched()) {
            double thresh = inWarmupMode ? 2.0 : threshold.getThreshold();
            return new DetectionResult(
                Double.MAX_VALUE, thresh, true,
                Map.of(), List.of(), null, sig.describe()
            );
        }

        RansomwareFeatureVector vector = featureExtractor.extract(new SnapdiffFile(records, null));

        if (inWarmupMode) {
            return detectWarmup(vector, records);
        }

        double score = scorer.score(vector);
        Map<String, Double> z = explainer.computeZScores(vector);
        List<Map.Entry<String, Double>> top = explainer.topDeviations(vector, 5);
        boolean isAnom = threshold.isAnomaly(score);

        boolean directionReversed = false;
        if (isAnom && directionalValidator != null) {
            double[] zArr = new double[RansomwareFeatureVector.FEATURE_COUNT];
            for (int i = 0; i < RansomwareFeatureVector.FEATURE_COUNT; i++) {
                zArr[i] = z.getOrDefault(RansomwareFeatureVector.FEATURE_NAMES[i], 0.0);
            }
            DirectionalValidator.ValidationResult vr = directionalValidator.validate(zArr);
            if (vr.reversed) {
                isAnom = false;
                directionReversed = true;
                lastResultReversed = true;
                LOG.log(java.util.logging.Level.WARNING,
                    "Directional validation reversed anomaly: score={0}, ratio={1}, top={2}",
                    new Object[]{
                        String.format("%.2f", score),
                        String.format("%.4f", vr.ratio),
                        vr.topDeviations.stream().limit(5)
                            .map(d -> d.name() + "=" + String.format("%.2f", d.zScore()) + "(" + d.direction() + ")")
                            .reduce((a, b) -> a + ", " + b).orElse("")
                    });
            }
        }

        return new DetectionResult(score, threshold.getThreshold(), isAnom, z, top, vector, null, directionReversed);
    }

    public DetectionResult detect(RansomwareFeatureVector vector, List<SnapdiffRecord> records) {
        lastResultReversed = false;

        if (records != null && !records.isEmpty()) {
            RansomwareSignatureDetector.SignatureResult sig = signatureDetector.scan(records);
            if (sig.matched()) {
                double thresh = inWarmupMode ? 2.0 : threshold.getThreshold();
                return new DetectionResult(
                    Double.MAX_VALUE, thresh, true,
                    Map.of(), List.of(), vector, sig.describe()
                );
            }
        }

        if (inWarmupMode) {
            return detectWarmup(vector, records);
        }

        double score = scorer.score(vector);
        Map<String, Double> z = explainer.computeZScores(vector);
        List<Map.Entry<String, Double>> top = explainer.topDeviations(vector, 5);
        boolean isAnom = threshold.isAnomaly(score);

        boolean directionReversed = false;
        if (isAnom && directionalValidator != null) {
            double[] zArr = new double[RansomwareFeatureVector.FEATURE_COUNT];
            for (int i = 0; i < RansomwareFeatureVector.FEATURE_COUNT; i++) {
                zArr[i] = z.getOrDefault(RansomwareFeatureVector.FEATURE_NAMES[i], 0.0);
            }
            DirectionalValidator.ValidationResult vr = directionalValidator.validate(zArr);
            if (vr.reversed) {
                isAnom = false;
                directionReversed = true;
                lastResultReversed = true;
                LOG.log(java.util.logging.Level.WARNING,
                    "Directional validation reversed anomaly: score={0}, ratio={1}, top={2}",
                    new Object[]{
                        String.format("%.2f", score),
                        String.format("%.4f", vr.ratio),
                        vr.topDeviations.stream().limit(5)
                            .map(d -> d.name() + "=" + String.format("%.2f", d.zScore()) + "(" + d.direction() + ")")
                            .reduce((a, b) -> a + ", " + b).orElse("")
                    });
            }
        }

        return new DetectionResult(score, threshold.getThreshold(), isAnom, z, top, vector, null, directionReversed);
    }

    private DetectionResult detectWarmup(RansomwareFeatureVector vector, List<SnapdiffRecord> records) {
        warmupRoundCount++;

        int matchingRules = warmupDetector.classify(vector);
        boolean isAnom = matchingRules >= 2;

        if (!isAnom) {
            warmupBaseline.add(vector);
            window.add(vector);
            if (window.size() > maxWindowSize) {
                window.remove(0);
            }
        }

        if (warmupBaseline.size() >= MIN_BASELINE_FOR_STATISTICAL) {
            transitionToStatisticalMode();
        }

        if (warmupRoundCount > 10 && inWarmupMode) {
            LOG.warning("Warmup period has exceeded 10 rounds. Baseline accumulated: " + warmupBaseline.size() + " vectors.");
        }

        return new DetectionResult(
            (double) matchingRules,
            2.0,
            isAnom,
            Map.of(),
            List.of(),
            vector,
            null,
            false
        );
    }

    private void transitionToStatisticalMode() {
        this.baselineStats = new BaselineStatistics(warmupBaseline);
        this.scorer = new WeightedEuclideanScorer(baselineStats, weights);
        this.explainer = new ZScoreExplainer(baselineStats);
        this.threshold = new AnomalyThreshold(warmupBaseline, scorer, 97.0, 2.5);
        if (this.featureExtractor != null) {
            this.featureExtractor.setBaselineStats(baselineStats);
        }
        this.inWarmupMode = false;
    }

    public void update(RansomwareFeatureVector newVector) {
        window.add(newVector);
        if (window.size() > maxWindowSize) {
            window.remove(0);
        }
        BaselineStatistics newStats = new BaselineStatistics(window);
        this.baselineStats = newStats;
        this.scorer = new WeightedEuclideanScorer(newStats, scorer.getWeights());
        this.explainer = new ZScoreExplainer(newStats);
        if (this.featureExtractor != null) {
            this.featureExtractor.setBaselineStats(newStats);
        }
    }

    public BaselineStatistics getBaselineStats() { return baselineStats; }
    public WeightedEuclideanScorer getScorer() { return scorer; }
    public AnomalyThreshold getThreshold() { return threshold; }
    public boolean wasLastResultReversed() { return lastResultReversed; }
    public boolean isInWarmupMode() { return inWarmupMode; }
    public int getBaselineCount() {
        if (warmupBaseline != null) return warmupBaseline.size();
        return window != null ? window.size() : 0;
    }
}
