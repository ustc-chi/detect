package com.anomalydetection.detector.v2;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Complete detection result containing all information needed for reporting.
 */
public final class DetectionResult {

    private final String resourceId;
    private final Instant detectionTime;
    private final Phase phase;
    private final double score;
    private final double threshold;
    private final boolean isAnomaly;
    private final List<DimensionReport> dimensions;
    private final List<DimensionReport> topDeviations;
    private final DirectionValidation directionValidation;
    private final String signatureMatch;
    private final WarmupInfo warmupInfo;

    // ===== Full constructor =====
    public DetectionResult(String resourceId, Instant detectionTime, Phase phase,
                           double score, double threshold, boolean isAnomaly,
                           List<DimensionReport> dimensions, List<DimensionReport> topDeviations,
                           DirectionValidation directionValidation, String signatureMatch,
                           WarmupInfo warmupInfo) {
        this.resourceId = resourceId;
        this.detectionTime = detectionTime != null ? detectionTime : Instant.now();
        this.phase = Objects.requireNonNull(phase);
        this.score = score;
        this.threshold = threshold;
        this.isAnomaly = isAnomaly;
        this.dimensions = dimensions != null ? List.copyOf(dimensions) : List.of();
        this.topDeviations = topDeviations != null ? List.copyOf(topDeviations) : List.of();
        this.directionValidation = directionValidation != null ? directionValidation : DirectionValidation.notReversed();
        this.signatureMatch = signatureMatch;
        this.warmupInfo = warmupInfo;
    }

    // ===== Factory for warmup results =====
    public static DetectionResult warmupResult(String resourceId, FeatureVector vector,
                                                 WarmupDetector.WarmupDetectionResult wr) {
        boolean isAnomaly = wr.isAnomaly();
        int layer = wr.getLayer();
        double score, threshold;
        List<DimensionReport> dims;
        List<DimensionReport> topDevs;

        if (layer == 3) {
            score = wr.getStatisticalScore();
            threshold = wr.getDynamicThreshold();
            dims = wr.getDimensionReports() != null ? wr.getDimensionReports() : buildBasicDimensionReports(vector);
            topDevs = wr.getTopDeviations() != null ? wr.getTopDeviations() : List.of();
        } else {
            score = wr.getTriggeredRules().size();
            threshold = 2.0;
            dims = buildBasicDimensionReports(vector);
            topDevs = List.of();
        }

        WarmupInfo info = new WarmupInfo(
                wr.getTriggeredRules().size(), wr.getTriggeredRules(),
                wr.getConfidence(), wr.isAddToBaseline(),
                wr.getLayer(), wr.getStatisticalScore(),
                wr.getDynamicThreshold(), wr.getHistorySize()
        );
        return new DetectionResult(resourceId, Instant.now(), Phase.WARMUP,
                score, threshold, isAnomaly, dims, topDevs,
                DirectionValidation.notReversed(), null, info);
    }

    // ===== Factory for signature-precheck anomaly =====
    public static DetectionResult signatureAnomaly(String resourceId, Phase phase, String signatureMatch) {
        return new DetectionResult(resourceId, Instant.now(), phase,
                Double.MAX_VALUE, 0, true, Collections.emptyList(), Collections.emptyList(),
                DirectionValidation.notReversed(), signatureMatch, null);
    }

    // ===== Getters =====
    public String getResourceId() { return resourceId; }
    public Instant getDetectionTime() { return detectionTime; }
    public Phase getPhase() { return phase; }
    public double getScore() { return score; }
    public double getThreshold() { return threshold; }
    public boolean isAnomaly() { return isAnomaly; }
    public List<DimensionReport> getDimensions() { return dimensions; }
    public List<DimensionReport> getTopDeviations() { return topDeviations; }
    public DirectionValidation getDirectionValidation() { return directionValidation; }
    public String getSignatureMatch() { return signatureMatch; }
    public WarmupInfo getWarmupInfo() { return warmupInfo; }

    private static List<DimensionReport> buildBasicDimensionReports(FeatureVector vector) {
        List<DimensionReport> reports = new ArrayList<>(FeatureVector.FEATURE_COUNT);
        for (int i = 0; i < FeatureVector.FEATURE_COUNT; i++) {
            reports.add(new DimensionReport(i, FeatureVector.FEATURE_NAMES[i], vector.get(i),
                    0, 0, 0, FeatureVector.FEATURE_DESCRIPTIONS[i],
                    FeatureVector.FEATURE_UNITS[i], vector.getSupplementary(i)));
        }
        return reports;
    }
}
