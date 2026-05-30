package com.anomalydetection.detector.heuristic;

import com.anomalydetection.features.FeatureType;
import com.anomalydetection.features.FeatureVector;

/**
 * Triggers when modification_ratio is extremely high (most operations are modifications),
 * which is the primary signal for encryption ransomware.
 */
public class ModificationRatioRule implements HeuristicRule {
    private static final String RULE_NAME = "EXTREME_MODIFICATION_RATIO";
    private static final double MOD_RATIO_THRESHOLD = 0.95;
    private static final double MIN_OPS = 50;
    private static final double CONFIDENCE = 0.90;

    @Override
    public RuleResult evaluate(FeatureVector vector) {
        double modRatio = vector.get(FeatureType.MODIFICATION_RATIO);
        double dailyOps = vector.get(FeatureType.TOTAL_OPERATIONS_NORMALIZED);
        if (modRatio > MOD_RATIO_THRESHOLD && dailyOps > MIN_OPS) {
            return RuleResult.triggered(RULE_NAME, CONFIDENCE);
        }
        return RuleResult.notTriggered();
    }

    @Override
    public String getRuleName() { return RULE_NAME; }
}
