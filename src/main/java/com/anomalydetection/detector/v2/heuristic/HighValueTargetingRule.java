package com.anomalydetection.detector.v2.heuristic;

import com.anomalydetection.detector.v2.FeatureVector14;

/**
 * Layer 2 heuristic: high_value_ext_ratio > 0.8 AND total_operations > 100.
 * Ransomware preferentially targets high-value documents and databases.
 */
public class HighValueTargetingRule implements HeuristicRule {

    private static final String RULE_NAME = "HIGH_VALUE_TARGETING";
    private static final double HV_RATIO_THRESHOLD = 0.8;
    private static final double MIN_OPS = 100;
    private static final double CONFIDENCE = 0.75;

    @Override
    public RuleResult evaluate(FeatureVector14 vector) {
        if (vector.getHighValueExtRatio() > HV_RATIO_THRESHOLD
                && vector.getTotalOperations() > MIN_OPS) {
            return RuleResult.triggered(RULE_NAME, CONFIDENCE);
        }
        return RuleResult.notTriggered();
    }

    @Override
    public String getRuleName() {
        return RULE_NAME;
    }
}
