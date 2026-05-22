package com.anomalydetection.detector.v2.heuristic;

import com.anomalydetection.detector.v2.FeatureVector14;

/**
 * Layer 2 heuristic: inter_op_time_cv < 0.05 AND total_operations > 50.
 * Extremely regular timing (low CV) suggests automation rather than human behavior.
 */
public class InterOpTimeCvRule implements HeuristicRule {

    private static final String RULE_NAME = "ROBOTIC_TIMING_PATTERN";
    private static final double CV_THRESHOLD = 0.05;
    private static final double MIN_OPS = 50;
    private static final double CONFIDENCE = 0.85;

    @Override
    public RuleResult evaluate(FeatureVector14 vector) {
        if (vector.getInterOpTimeCv() < CV_THRESHOLD
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
