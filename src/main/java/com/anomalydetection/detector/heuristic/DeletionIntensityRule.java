package com.anomalydetection.detector.heuristic;

import com.anomalydetection.features.FeatureType;
import com.anomalydetection.features.FeatureVector;

/**
 * Triggers when deletion_ratio is abnormally high — characteristic of destructive
 * ransomware that deletes or overwrites files before encryption.
 * <p>
 * Note: adapted from old 14-dim "deletion_intensity" composite score to the new
 * 16-dim "deletion_ratio" (simple ratio of deleted records to total).
 * Threshold needs calibration with real data.
 */
public class DeletionIntensityRule implements HeuristicRule {
    private static final String RULE_NAME = "HIGH_DELETION_INTENSITY";
    private static final double DELETION_THRESHOLD = 0.5;
    private static final double CONFIDENCE = 0.70;

    @Override
    public RuleResult evaluate(FeatureVector vector) {
        double delRatio = vector.get(FeatureType.DELETION_RATIO);
        if (delRatio > DELETION_THRESHOLD) {
            return RuleResult.triggered(RULE_NAME, CONFIDENCE);
        }
        return RuleResult.notTriggered();
    }

    @Override
    public String getRuleName() { return RULE_NAME; }
}
