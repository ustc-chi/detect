package com.anomalydetection.detector.v2.heuristic;

import com.anomalydetection.detector.v2.FeatureVector14;

/**
 * Layer 2 heuristic: file_type_concentration > 0.90 AND total_operations > 100.
 * Ransomware often targets specific file types, causing extreme concentration.
 */
public class FileTypeConcentrationRule implements HeuristicRule {

    private static final String RULE_NAME = "HIGH_FILE_TYPE_CONCENTRATION";
    private static final double CONCENTRATION_THRESHOLD = 0.90;
    private static final double MIN_OPS = 100;
    private static final double CONFIDENCE = 0.80;

    @Override
    public RuleResult evaluate(FeatureVector14 vector) {
        if (vector.getFileTypeConcentration() > CONCENTRATION_THRESHOLD
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
