package com.anomalydetection.detector.heuristic;

import com.anomalydetection.features.FeatureType;
import com.anomalydetection.features.FeatureVector;

/**
 * Triggers when inter-operation time coefficient of variation is very low,
 * indicating highly regular timing patterns — characteristic of automated
 * tools and scripted ransomware behavior.
 */
public class InterOpTimeCvRule implements HeuristicRule {
    private static final String RULE_NAME = "ROBOTIC_TIMING_PATTERN";
    private static final double CV_THRESHOLD = 0.55;
    private static final double MIN_OPS = 100;
    private static final double CONFIDENCE = 0.85;

    @Override
    public RuleResult evaluate(FeatureVector vector) {
        double cv = vector.get(FeatureType.INTER_OP_TIME_CV_BURST);
        double dailyOps = vector.get(FeatureType.TOTAL_OPERATIONS_NORMALIZED);
        if (cv < CV_THRESHOLD && dailyOps > MIN_OPS) {
            return RuleResult.triggered(RULE_NAME, CONFIDENCE);
        }
        return RuleResult.notTriggered();
    }

    @Override
    public RuleResult evaluate(FeatureVector vector, int sensitivity) {
        double multiplier = com.anomalydetection.detector.SensitivityAdjuster.getThresholdMultiplier(sensitivity);
        double cv = vector.get(FeatureType.INTER_OP_TIME_CV_BURST);
        double dailyOps = vector.get(FeatureType.TOTAL_OPERATIONS_NORMALIZED);
        double adjustedThreshold = CV_THRESHOLD * multiplier;
        if (cv < adjustedThreshold && dailyOps > MIN_OPS) {
            return RuleResult.triggered(RULE_NAME, CONFIDENCE);
        }
        return RuleResult.notTriggered();
    }

    @Override
    public String getRuleName() { return RULE_NAME; }
}
