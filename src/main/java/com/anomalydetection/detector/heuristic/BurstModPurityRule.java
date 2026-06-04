package com.anomalydetection.detector.heuristic;

import com.anomalydetection.features.FeatureType;
import com.anomalydetection.features.FeatureVector;

/**
 * Triggers when burst window has extremely high modification purity
 * combined with high velocity — characteristic of automated encryption.
 */
public class BurstModPurityRule implements HeuristicRule {
    private static final String RULE_NAME = "HIGH_BURST_PURITY";
    private static final double PURITY_THRESHOLD = 0.88;
    private static final double MIN_VELOCITY = 1000;
    private static final double CONFIDENCE = 0.80;

    @Override
    public RuleResult evaluate(FeatureVector vector) {
        double purity = vector.get(FeatureType.BURST_MOD_PURITY);
        double velocity = vector.get(FeatureType.PEAK_BURST_VELOCITY);
        if (purity > PURITY_THRESHOLD && velocity > MIN_VELOCITY) {
            return RuleResult.triggered(RULE_NAME, CONFIDENCE);
        }
        return RuleResult.notTriggered();
    }

    @Override
    public RuleResult evaluate(FeatureVector vector, int sensitivity) {
        double multiplier = com.anomalydetection.detector.SensitivityAdjuster.getThresholdMultiplier(sensitivity);
        double purity = vector.get(FeatureType.BURST_MOD_PURITY);
        double velocity = vector.get(FeatureType.PEAK_BURST_VELOCITY);
        double adjustedThreshold = PURITY_THRESHOLD * multiplier;
        if (purity > adjustedThreshold && velocity > MIN_VELOCITY) {
            return RuleResult.triggered(RULE_NAME, CONFIDENCE);
        }
        return RuleResult.notTriggered();
    }

    @Override
    public String getRuleName() { return RULE_NAME; }
}
