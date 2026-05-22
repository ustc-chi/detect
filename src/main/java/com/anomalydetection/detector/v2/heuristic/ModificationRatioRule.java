package com.anomalydetection.detector.v2.heuristic;

import com.anomalydetection.detector.v2.FeatureVector14;

/**
 * Layer 2 heuristic: modification_ratio > 0.95 AND total_operations > 50.
 * Ransomware encryption is nearly all modify operations, with sufficient volume.
 */
public class ModificationRatioRule implements HeuristicRule {

    private static final String RULE_NAME = "EXTREME_MODIFICATION_RATIO";
    private static final double MOD_RATIO_THRESHOLD = 0.95;
    private static final double MIN_OPS = 50;
    private static final double CONFIDENCE = 0.90;

    @Override
    public RuleResult evaluate(FeatureVector14 vector) {
        if (vector.getModificationRatio() > MOD_RATIO_THRESHOLD
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
