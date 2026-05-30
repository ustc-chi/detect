package com.anomalydetection.detector.heuristic;

import com.anomalydetection.features.FeatureType;
import com.anomalydetection.features.FeatureVector;

/**
 * Triggers when per_type_entropy is very low, indicating highly concentrated
 * operation types (e.g., nearly all MODIFIED) — a signal of automated encryption.
 * <p>
 * Note: adapted from old "file_type_concentration" (max extension ratio among modified)
 * to the new "per_type_entropy" (entropy of MODIFIED/CREATED/DELETED distribution).
 * Low entropy = one operation type dominates.
 */
public class FileTypeConcentrationRule implements HeuristicRule {
    private static final String RULE_NAME = "LOW_PER_TYPE_ENTROPY";
    private static final double ENTROPY_THRESHOLD = 0.3;
    private static final double MIN_OPS = 100;
    private static final double CONFIDENCE = 0.80;

    @Override
    public RuleResult evaluate(FeatureVector vector) {
        double entropy = vector.get(FeatureType.PER_TYPE_ENTROPY);
        double dailyOps = vector.get(FeatureType.TOTAL_OPERATIONS_NORMALIZED);
        // Low entropy = concentrated operation type (potential encryption)
        if (entropy < ENTROPY_THRESHOLD && dailyOps > MIN_OPS) {
            return RuleResult.triggered(RULE_NAME, CONFIDENCE);
        }
        return RuleResult.notTriggered();
    }

    @Override
    public String getRuleName() { return RULE_NAME; }
}
