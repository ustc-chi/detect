package com.anomalydetection.detector;

import com.anomalydetection.features.FeatureDescription;
import com.anomalydetection.features.FeatureType;
import com.anomalydetection.features.FeatureVector;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Complete detection result containing score, phase info, per-dimension reports.
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
            score = wr.getConfidence();
            threshold = 0.5;
            dims = buildBasicDimensionReports(vector);
            topDevs = List.of();
        }

        return new DetectionResult(resourceId, null, Phase.WARMUP,
                score, threshold, isAnomaly, dims, topDevs,
                null, null, wr.toWarmupInfo());
    }

    // ===== Factory for active results =====
    public static DetectionResult activeResult(String resourceId, FeatureVector vector,
                                                double score, double threshold, boolean isAnomaly,
                                                List<DimensionReport> dimensions,
                                                List<DimensionReport> topDeviations,
                                                DirectionValidation dv) {
        return new DetectionResult(resourceId, null, Phase.ACTIVE,
                score, threshold, isAnomaly, dimensions, topDeviations,
                dv, null, null);
    }

    // ===== Factory for pre-check signature match =====
    public static DetectionResult signatureMatchResult(String resourceId, String matchedSignature) {
        return new DetectionResult(resourceId, null, Phase.WARMUP,
                1.0, 0.5, true, List.of(), List.of(),
                null, matchedSignature, null);
    }

    /** Build basic per-dimension reports from the feature vector (used when no z-scores available). */
    static List<DimensionReport> buildBasicDimensionReports(FeatureVector vector) {
        double[] values = vector.toArray();
        List<DimensionReport> reports = new ArrayList<>(FeatureType.COUNT);
        for (int i = 0; i < FeatureType.COUNT; i++) {
            FeatureType ft = FeatureType.values()[i];
            String desc = buildFeatureDescription(vector, ft);
            reports.add(new DimensionReport(
                    i, ft.key(), values[i], 0.0, 0.0, 1.0,
                    desc, "", Collections.emptyMap()));
        }
        return reports;
    }

    private static String buildFeatureDescription(FeatureVector vector, FeatureType ft) {
        if (vector.getExtendInfo().containsKey(ft.key())) {
            FeatureDescription fd = vector.getDes(ft);
            return fd.cn() + " / " + fd.en();
        }
        return ft.desEN();
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
}
