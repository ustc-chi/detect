package com.anomalydetection.features;

import com.anomalydetection.model.SnapdiffFile;
import com.anomalydetection.model.SnapdiffRecord;
import com.anomalydetection.parser.StreamingSnapdiffParser;
import com.anomalydetection.detector.BaselineStatistics;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;
import java.util.Comparator;

public class RansomwareFeatureExtractor {
    private final SuspiciousExtensions suspiciousExtensions;
    private final double daysBetweenSnapshots;
    private double hvExtEma = Double.NaN;
    private BaselineStatistics baselineStats;
    private static final double HV_EMA_ALPHA = 0.3;
    private static final double MIN_DAYS = 0.25;
    private static final double Z_CAP = 10.0;

    private static final Set<String> HIGH_VALUE_EXTS = new HashSet<>(Arrays.asList(
        ".docx", ".xlsx", ".pptx", ".pdf", ".db", ".sql", ".csv",
        ".doc", ".xls", ".mdb", ".accdb", ".bak", ".backup"
    ));

    public RansomwareFeatureExtractor(SuspiciousExtensions suspiciousExtensions) {
        this(suspiciousExtensions, 2.0);
    }

    public RansomwareFeatureExtractor(SuspiciousExtensions suspiciousExtensions, double daysBetweenSnapshots) {
        this.suspiciousExtensions = suspiciousExtensions != null
            ? suspiciousExtensions
            : new SuspiciousExtensions();
        this.daysBetweenSnapshots = Math.max(MIN_DAYS, daysBetweenSnapshots);
    }

    public double getDaysBetweenSnapshots() {
        return daysBetweenSnapshots;
    }

    public void setBaselineStats(BaselineStatistics baselineStats) {
        this.baselineStats = baselineStats;
    }

    private double computeWallClockAnomaly(int totalOps, List<SnapdiffRecord> records) {
        if (baselineStats == null || records == null || records.isEmpty()) {
            return 0.0;
        }
        Instant earliest = null;
        for (SnapdiffRecord rec : records) {
            Instant time = rec.getChangeTime();
            if (time != null && !time.equals(Instant.EPOCH) && !time.equals(Instant.MAX)) {
                if (earliest == null || time.isBefore(earliest)) {
                    earliest = time;
                }
            }
        }
        if (earliest == null) {
            return 0.0;
        }
        int hour = earliest.atOffset(ZoneOffset.UTC).getHour();
        double[] hourlyStats = baselineStats.getHourlyStats(hour);
        if (hourlyStats == null) {
            return 0.0;
        }
        double median = hourlyStats[0];
        double mad = hourlyStats[1];
        if (mad < 0.001) {
            mad = Math.sqrt(0.001);
        }
        double z = (totalOps - median) / mad;
        return Math.max(-Z_CAP, Math.min(Z_CAP, z));
    }

    private double computeWallClockAnomalyFromEarliest(int totalOps, Instant earliest) {
        if (baselineStats == null || earliest == null) {
            return 0.0;
        }
        int hour = earliest.atOffset(ZoneOffset.UTC).getHour();
        double[] hourlyStats = baselineStats.getHourlyStats(hour);
        if (hourlyStats == null) {
            return 0.0;
        }
        double median = hourlyStats[0];
        double mad = hourlyStats[1];
        if (mad < 0.001) {
            mad = Math.sqrt(0.001);
        }
        double z = (totalOps - median) / mad;
        return Math.max(-Z_CAP, Math.min(Z_CAP, z));
    }

    public RansomwareFeatureVector extract(SnapdiffFile snapdiff) {
        if (snapdiff == null || snapdiff.getDiffs() == null || snapdiff.getDiffs().isEmpty()) {
            return new RansomwareFeatureVector(new double[12]);
        }

        int totalOps = 0;
        int modifiedCount = 0;
        int addedCount = 0;
        int deletedCount = 0;
        int highValueExtCount = 0;
        List<RecordInfo> modifiedRecords = new ArrayList<>();
        List<RecordInfo> addedRecords = new ArrayList<>();
        List<RecordInfo> deletedRecords = new ArrayList<>();
        List<OpRecord> opRecords = new ArrayList<>();

        for (SnapdiffRecord rec : snapdiff.getDiffs()) {
            if (rec == null) continue;
            totalOps++;

            String t = rec.getType();
            String path = rec.getPath();
            Instant time = rec.getChangeTime();
            long epochSec = -1L;
            if (time != null && !time.equals(Instant.EPOCH) && !time.equals(Instant.MAX)) {
                epochSec = time.getEpochSecond();
            }

            boolean isModified = "modified".equalsIgnoreCase(t);
            boolean isAdded = "added".equalsIgnoreCase(t);
            boolean isDeleted = "deleted".equalsIgnoreCase(t);

            if (isModified) modifiedCount++;
            else if (isAdded) addedCount++;
            else if (isDeleted) deletedCount++;

            if (isModified) {
                modifiedRecords.add(new RecordInfo(path, rec.getSize(), epochSec));
            } else if (isAdded) {
                addedRecords.add(new RecordInfo(path, rec.getSize(), epochSec));
            } else if (isDeleted) {
                deletedRecords.add(new RecordInfo(path, rec.getSize(), epochSec));
            }

            if (epochSec >= 0) {
                opRecords.add(new OpRecord(epochSec, t));
            }

            if (path != null && !path.endsWith("/")) {
                int lastSlash = path.lastIndexOf('/');
                if (lastSlash >= 0 && lastSlash < path.length() - 1) {
                    int lastDot = path.lastIndexOf('.');
                    if (lastDot > lastSlash) {
                        String ext = path.substring(lastDot).toLowerCase();
                        if (ext.length() > 1 && HIGH_VALUE_EXTS.contains(ext)) {
                            highValueExtCount++;
                        }
                    }
                }
            }
        }

        double modificationRatio = totalOps == 0 ? 0.0 : ((double) modifiedCount) / totalOps;

        double rawHvRatio = totalOps == 0 ? 0.0 : ((double) highValueExtCount) / totalOps;
        if (Double.isNaN(hvExtEma)) {
            hvExtEma = rawHvRatio;
        } else {
            hvExtEma = (1.0 - HV_EMA_ALPHA) * hvExtEma + HV_EMA_ALPHA * rawHvRatio;
        }

        double highValueFileCoverage = Math.max(0.0, Math.min(1.0, rawHvRatio));

        double changeVelocity = 0.0;
        double burstPurity = 0.0;
        double interOpTimeCvBurst = 0.0;
        double temporalUniformity = 0.0;
        if (opRecords.size() >= 2) {
            List<OpRecord> sorted = new ArrayList<>(opRecords);
            sorted.sort(Comparator.comparingLong(o -> o.epochSeconds));

            long windowSecs = 300L;
            int tail = 0;
            int maxInWindow = 1;
            for (int head = 1; head < sorted.size(); head++) {
                while (sorted.get(head).epochSeconds - sorted.get(tail).epochSeconds > windowSecs) tail++;
                int count = head - tail + 1;
                if (count > maxInWindow) maxInWindow = count;
            }
            changeVelocity = maxInWindow / (windowSecs / 3600.0);

            tail = 0;
            int bestStart = 0, bestEnd = 0, bestCount = 0;
            for (int head = 0; head < sorted.size(); head++) {
                while (sorted.get(head).epochSeconds - sorted.get(tail).epochSeconds > windowSecs) tail++;
                int count = head - tail + 1;
                if (count > bestCount) {
                    bestCount = count;
                    bestStart = tail;
                    bestEnd = head;
                }
            }
            if (bestCount >= 2) {
                int modsInWindow = 0;
                for (int i = bestStart; i <= bestEnd; i++) {
                    if ("modified".equalsIgnoreCase(sorted.get(i).type)) modsInWindow++;
                }
                burstPurity = ((double) modsInWindow) / ((double) bestCount);
            }

            if (bestCount >= 2 && bestEnd > bestStart) {
                double[] burstDeltas = new double[bestEnd - bestStart];
                for (int i = bestStart; i < bestEnd; i++) {
                    burstDeltas[i - bestStart] = sorted.get(i + 1).epochSeconds - sorted.get(i).epochSeconds;
                }
                double burstDeltaMean = 0.0;
                for (double d : burstDeltas) burstDeltaMean += d;
                burstDeltaMean /= burstDeltas.length;
                if (burstDeltaMean >= 0.001) {
                    double burstDeltaVar = 0.0;
                    for (double d : burstDeltas) { double dd = d - burstDeltaMean; burstDeltaVar += dd * dd; }
                    burstDeltaVar /= burstDeltas.length;
                    interOpTimeCvBurst = Math.sqrt(burstDeltaVar) / burstDeltaMean;
                }
            }

            long spanSecs = sorted.get(sorted.size() - 1).epochSeconds - sorted.get(0).epochSeconds;
            if (spanSecs > 0) {
                int numBins = (int) (spanSecs / windowSecs) + 1;
                if (numBins >= 3) {
                    long baseEpoch = sorted.get(0).epochSeconds;
                    double[] binCounts = new double[numBins];
                    for (OpRecord entry : sorted) {
                        int bin = (int) ((entry.epochSeconds - baseEpoch) / windowSecs);
                        if (bin >= 0 && bin < numBins) binCounts[bin]++;
                    }
                    double binMean = 0.0;
                    for (double c : binCounts) binMean += c;
                    binMean /= binCounts.length;
                    if (binMean >= 0.001) {
                        double binVar = 0.0;
                        for (double c : binCounts) { double d = c - binMean; binVar += d * d; }
                        binVar /= binCounts.length;
                        temporalUniformity = 1.0 - (Math.sqrt(binVar) / binMean);
                    }
                }
            }
        }

        Set<String> uniqueDirs = new HashSet<>();
        List<Integer> depths = new ArrayList<>();
        for (RecordInfo ri : modifiedRecords) {
            if (ri.path != null) {
                String parentDir = parentDirectory(ri.path);
                if (parentDir != null) {
                    uniqueDirs.add(parentDir);
                    depths.add(pathDepth(ri.path));
                }
            }
        }
        double directoryCoverageDepth;
        if (modifiedRecords.isEmpty() || uniqueDirs.isEmpty()) {
            directoryCoverageDepth = 0.0;
        } else {
            double depthMean = 0.0;
            for (int d : depths) depthMean += d;
            depthMean /= depths.size();
            double depthVar = 0.0;
            for (int d : depths) { double dd = d - depthMean; depthVar += dd * dd; }
            depthVar /= depths.size();
            double depthStdDev = Math.sqrt(depthVar);
            directoryCoverageDepth = uniqueDirs.size() * (1.0 / (1.0 + depthStdDev));
        }

        double renameCorrelation = computeRenameCorrelation(addedRecords, deletedRecords, totalOps);

        double perTypeEntropy;
        int n = addedCount + modifiedCount + deletedCount;
        if (n == 0) {
            perTypeEntropy = 0.0;
        } else {
            double h = 0.0;
            if (addedCount > 0) { double p = (double) addedCount / n; h -= p * Math.log(p) / Math.log(2); }
            if (modifiedCount > 0) { double p = (double) modifiedCount / n; h -= p * Math.log(p) / Math.log(2); }
            if (deletedCount > 0) { double p = (double) deletedCount / n; h -= p * Math.log(p) / Math.log(2); }
            perTypeEntropy = h;
        }

        List<SnapdiffRecord> allRecords = snapdiff.getDiffs();
        double wallClockAnomaly = computeWallClockAnomaly(totalOps, allRecords);

        double[] values = new double[] {
            modificationRatio,
            (double) totalOps / daysBetweenSnapshots,
            changeVelocity,
            burstPurity,
            hvExtEma,
            interOpTimeCvBurst,
            highValueFileCoverage,
            directoryCoverageDepth,
            temporalUniformity,
            renameCorrelation,
            wallClockAnomaly,
            perTypeEntropy
        };
        return new RansomwareFeatureVector(values);
    }

    public RansomwareFeatureVector extractFromFile(Path filePath) throws IOException {
        if (filePath == null) {
            return new RansomwareFeatureVector(new double[12]);
        }

        StreamingSnapdiffParser streamingParser = new StreamingSnapdiffParser();
        try (InMemoryBurstAccumulator burstAccumulator = InMemoryBurstAccumulator.create()) {
            int[] totalOps = {0};
            int[] modifiedCount = {0};
            int[] addedCount = {0};
            int[] deletedCount = {0};
            int[] highValueExtCount = {0};
            Set<String> uniqueDirs = new HashSet<>();
            long[] depthSum = {0};
            long[] depthSumSq = {0};
            int[] depthCount = {0};
            List<RecordInfo> addedRecords = new ArrayList<>();
            List<RecordInfo> deletedRecords = new ArrayList<>();
            Instant[] earliestTime = {null};

            streamingParser.parse(filePath, rec -> {
                if (rec == null) return;
                totalOps[0]++;
                String t = rec.getType();
                String path = rec.getPath();
                Instant time = rec.getChangeTime();
                long epochSec = -1L;
                if (time != null && !time.equals(Instant.EPOCH) && !time.equals(Instant.MAX)) {
                    epochSec = time.getEpochSecond();
                    if (earliestTime[0] == null || time.isBefore(earliestTime[0])) {
                        earliestTime[0] = time;
                    }
                }

                boolean isModified = "modified".equalsIgnoreCase(t);
                boolean isAdded = "added".equalsIgnoreCase(t);
                boolean isDeleted = "deleted".equalsIgnoreCase(t);

                if (isModified) {
                    modifiedCount[0]++;
                    if (path != null) {
                        String parentDir = parentDirectory(path);
                        if (parentDir != null) {
                            uniqueDirs.add(parentDir);
                            int depth = pathDepth(path);
                            depthSum[0] += depth;
                            depthSumSq[0] += (long) depth * depth;
                            depthCount[0]++;
                        }
                    }
                } else if (isAdded) { addedCount[0]++; addedRecords.add(new RecordInfo(path, rec.getSize(), epochSec)); }
                else if (isDeleted) { deletedCount[0]++; deletedRecords.add(new RecordInfo(path, rec.getSize(), epochSec)); }

                if (epochSec >= 0) {
                    try {
                        burstAccumulator.write(epochSec, rec.getType());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }

                if (path != null && !path.endsWith("/")) {
                    int lastSlash = path.lastIndexOf('/');
                    if (lastSlash >= 0 && lastSlash < path.length() - 1) {
                        int lastDot = path.lastIndexOf('.');
                        if (lastDot > lastSlash) {
                            String ext = path.substring(lastDot).toLowerCase();
                            if (ext.length() > 1 && HIGH_VALUE_EXTS.contains(ext)) {
                                highValueExtCount[0]++;
                            }
                        }
                    }
                }
            });

            InMemoryBurstAccumulator.BurstFeatures burstFeatures = burstAccumulator.computeBurstFeatures();
            double changeVelocity = burstFeatures.peakBurstVelocity();
            double burstPurity = burstFeatures.burstModPurity();
            double interOpTimeCvBurst = burstFeatures.interOpTimeCvBurst();
            double temporalUniformity = burstFeatures.temporalUniformity();

            int ops = totalOps[0];
            double modificationRatio = ops == 0 ? 0.0 : ((double) modifiedCount[0]) / ops;

            double rawHvRatio = ops == 0 ? 0.0 : ((double) highValueExtCount[0]) / ops;
            if (Double.isNaN(hvExtEma)) {
                hvExtEma = rawHvRatio;
            } else {
                hvExtEma = (1.0 - HV_EMA_ALPHA) * hvExtEma + HV_EMA_ALPHA * rawHvRatio;
            }
            double highValueFileCoverage = Math.max(0.0, Math.min(1.0, rawHvRatio));

            double directoryCoverageDepth;
            if (depthCount[0] == 0 || uniqueDirs.isEmpty()) {
                directoryCoverageDepth = 0.0;
            } else {
                double depthMean = (double) depthSum[0] / depthCount[0];
                double depthVar = (double) depthSumSq[0] / depthCount[0] - depthMean * depthMean;
                double depthStdDev = Math.sqrt(Math.max(0.0, depthVar));
                directoryCoverageDepth = uniqueDirs.size() * (1.0 / (1.0 + depthStdDev));
            }

            double renameCorrelation = computeRenameCorrelation(addedRecords, deletedRecords, ops);

            int n = addedCount[0] + modifiedCount[0] + deletedCount[0];
            double perTypeEntropy;
            if (n == 0) {
                perTypeEntropy = 0.0;
            } else {
                double h = 0.0;
                if (addedCount[0] > 0) { double p = (double) addedCount[0] / n; h -= p * Math.log(p) / Math.log(2); }
                if (modifiedCount[0] > 0) { double p = (double) modifiedCount[0] / n; h -= p * Math.log(p) / Math.log(2); }
                if (deletedCount[0] > 0) { double p = (double) deletedCount[0] / n; h -= p * Math.log(p) / Math.log(2); }
                perTypeEntropy = h;
            }

            double wallClockAnomaly = computeWallClockAnomalyFromEarliest(ops, earliestTime[0]);

            double[] values = new double[] {
                modificationRatio,
                (double) ops / daysBetweenSnapshots,
                changeVelocity,
                burstPurity,
                hvExtEma,
                interOpTimeCvBurst,
                highValueFileCoverage,
                directoryCoverageDepth,
                temporalUniformity,
                renameCorrelation,
                wallClockAnomaly,
                perTypeEntropy
            };
            return new RansomwareFeatureVector(values);
        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException(e);
        }
    }

    private double computeRenameCorrelation(List<RecordInfo> addedRecords, List<RecordInfo> deletedRecords, int totalOps) {
        if (totalOps == 0 || addedRecords.isEmpty() || deletedRecords.isEmpty()) {
            return 0.0;
        }
        // Group deleted records by parent directory — O(D)
        Map<String, List<RecordInfo>> deletedByDir = new HashMap<>();
        for (RecordInfo del : deletedRecords) {
            if (del.path == null) continue;
            String dir = parentDirectory(del.path);
            if (dir == null) continue;
            deletedByDir.computeIfAbsent(dir, k -> new ArrayList<>()).add(del);
        }

        int renameCount = 0;
        for (RecordInfo added : addedRecords) {
            if (added.path == null) continue;
            String addedDir = parentDirectory(added.path);
            String addedName = filenameWithoutExtension(added.path);
            if (addedDir == null || addedName == null || addedName.length() < 3) continue;

            List<RecordInfo> candidates = deletedByDir.get(addedDir);
            if (candidates == null) continue;

            for (int j = candidates.size() - 1; j >= 0; j--) {
                RecordInfo deleted = candidates.get(j);
                if (deleted.path == null) continue;
                String deletedName = filenameWithoutExtension(deleted.path);
                if (deletedName == null || deletedName.length() < 3) continue;

                if (addedName.startsWith(deletedName) || deletedName.startsWith(addedName)) {
                    renameCount++;
                    // O(1) removal: swap with last element
                    candidates.set(j, candidates.get(candidates.size() - 1));
                    candidates.remove(candidates.size() - 1);
                    break;
                }
            }
        }
        return (double) renameCount / totalOps;
    }

    private static String parentDirectory(String path) {
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash <= 0) return null;
        return path.substring(0, lastSlash);
    }

    private static String filenameWithoutExtension(String path) {
        int lastSlash = path.lastIndexOf('/');
        int lastDot = path.lastIndexOf('.');
        if (lastSlash >= lastDot) return path.substring(lastSlash + 1);
        return path.substring(lastSlash + 1, lastDot);
    }

    private static int pathDepth(String path) {
        int depth = 0;
        for (int i = 0; i < path.length(); i++) {
            if (path.charAt(i) == '/') depth++;
        }
        return depth;
    }

    private static class OpRecord {
        long epochSeconds;
        String type;
        OpRecord(long epochSeconds, String type) {
            this.epochSeconds = epochSeconds;
            this.type = type;
        }
    }

    private static class RecordInfo {
        String path;
        long size;
        long epochSeconds;
        RecordInfo(String path, long size, long epochSeconds) {
            this.path = path;
            this.size = size;
            this.epochSeconds = epochSeconds;
        }
    }
}
