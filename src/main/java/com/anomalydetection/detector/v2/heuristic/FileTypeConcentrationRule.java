package com.anomalydetection.detector.v2.heuristic;

import com.anomalydetection.detector.v2.FeatureVector;

public class FileTypeConcentrationRule implements HeuristicRule {
    private static final String RULE_NAME = "HIGH_FILE_TYPE_CONCENTRATION";
    private static final double CONCENTRATION_THRESHOLD = 0.90;
    private static final double MIN_OPS = 100;
    private static final double CONFIDENCE = 0.80;

    @Override
    public RuleResult evaluate(FeatureVector vector) {
        if (vector.getFileTypeConcentration() > CONCENTRATION_THRESHOLD && vector.getTotalOperations() > MIN_OPS) {
            return RuleResult.triggered(RULE_NAME, CONFIDENCE);
        }
        return RuleResult.notTriggered();
    }

    @Override
    public String getRuleName() { return RULE_NAME; }
}
