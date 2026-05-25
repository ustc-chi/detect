package com.anomalydetection.detector.v2.heuristic;

import com.anomalydetection.detector.v2.FeatureVector;

public class InterOpTimeCvRule implements HeuristicRule {
    private static final String RULE_NAME = "ROBOTIC_TIMING_PATTERN";
    private static final double CV_THRESHOLD = 0.05;
    private static final double MIN_OPS = 50;
    private static final double CONFIDENCE = 0.85;

    @Override
    public RuleResult evaluate(FeatureVector vector) {
        if (vector.getInterOpTimeCv() < CV_THRESHOLD && vector.getTotalOperations() > MIN_OPS) {
            return RuleResult.triggered(RULE_NAME, CONFIDENCE);
        }
        return RuleResult.notTriggered();
    }

    @Override
    public String getRuleName() { return RULE_NAME; }
}
