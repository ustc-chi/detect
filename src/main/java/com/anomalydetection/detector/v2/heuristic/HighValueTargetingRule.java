package com.anomalydetection.detector.v2.heuristic;

import com.anomalydetection.detector.v2.FeatureVector;

public class HighValueTargetingRule implements HeuristicRule {
    private static final String RULE_NAME = "HIGH_VALUE_TARGETING";
    private static final double HV_RATIO_THRESHOLD = 0.8;
    private static final double MIN_OPS = 100;
    private static final double CONFIDENCE = 0.75;

    @Override
    public RuleResult evaluate(FeatureVector vector) {
        if (vector.getHighValueExtRatio() > HV_RATIO_THRESHOLD && vector.getTotalOperations() > MIN_OPS) {
            return RuleResult.triggered(RULE_NAME, CONFIDENCE);
        }
        return RuleResult.notTriggered();
    }

    @Override
    public String getRuleName() { return RULE_NAME; }
}
