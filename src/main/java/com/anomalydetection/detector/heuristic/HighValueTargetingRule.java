package com.anomalydetection.detector.heuristic;

import com.anomalydetection.features.FeatureType;
import com.anomalydetection.features.FeatureVector;

/**
 * Triggers when high-value file (documents, databases) targeting ratio is elevated
 * and operation volume is sufficient — indicates targeted encryption of valuable files.
 */
public class HighValueTargetingRule implements HeuristicRule {
    private static final String RULE_NAME = "HIGH_VALUE_TARGETING";
    private static final double HV_RATIO_THRESHOLD = 0.65;
    private static final double MIN_OPS = 100;
    private static final double CONFIDENCE = 0.75;

    @Override
    public RuleResult evaluate(FeatureVector vector) {
        double hvRatio = vector.get(FeatureType.HIGH_VALUE_EXT_RATIO);
        double dailyOps = vector.get(FeatureType.TOTAL_OPERATIONS_NORMALIZED);
        if (hvRatio > HV_RATIO_THRESHOLD && dailyOps > MIN_OPS) {
            return RuleResult.triggered(RULE_NAME, CONFIDENCE);
        }
        return RuleResult.notTriggered();
    }

    @Override
    public String getRuleName() { return RULE_NAME; }
}
