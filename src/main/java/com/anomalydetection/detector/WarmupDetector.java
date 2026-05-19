package com.anomalydetection.detector;

import com.anomalydetection.features.RansomwareFeatureVector;

/**
 * Heuristic-based detector used during warmup (cold start) period
 * before statistical baseline is available.
 * <p>
 * Uses 5 simple threshold rules on individual features. A vector is
 * classified as anomalous when 2 or more rules match.
 */
public class WarmupDetector {

    private static final double MODIFICATION_RATIO_THRESHOLD = 0.85;
    private static final double PEAK_BURST_VELOCITY_THRESHOLD = 5000.0;
    private static final double TEMPORAL_UNIFORMITY_THRESHOLD = 0.7;
    private static final double BURST_MOD_PURITY_THRESHOLD = 0.90;
    private static final double RENAME_CORRELATION_THRESHOLD = 0.5;
    private static final int RULE_TRIGGER_THRESHOLD = 2;

    /**
     * Returns the number of heuristic rules matched by the given vector.
     * The count can be used as the score in a DetectionResult.
     */
    public int classify(RansomwareFeatureVector vector) {
        int matchingRules = 0;
        if (vector.getModificationRatio() > MODIFICATION_RATIO_THRESHOLD) matchingRules++;
        if (vector.getPeakBurstVelocity() > PEAK_BURST_VELOCITY_THRESHOLD) matchingRules++;
        if (vector.getTemporalUniformity() > TEMPORAL_UNIFORMITY_THRESHOLD) matchingRules++;
        if (vector.getBurstModPurity() > BURST_MOD_PURITY_THRESHOLD) matchingRules++;
        if (vector.getRenameCorrelation() > RENAME_CORRELATION_THRESHOLD) matchingRules++;
        return matchingRules;
    }

    /**
     * Returns true if the vector matches at least RULE_TRIGGER_THRESHOLD rules.
     */
    public boolean isAnomalous(RansomwareFeatureVector vector) {
        return classify(vector) >= RULE_TRIGGER_THRESHOLD;
    }
}
