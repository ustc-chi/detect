package com.anomalydetection.detector.v2.heuristic;

import com.anomalydetection.detector.v2.FeatureVector14;

/**
 * Layer 2 heuristic: deletion_intensity > 5.0.
 * Very high deletion intensity indicates destructive ransomware (e.g., Ryuk, wiper).
 */
public class DeletionIntensityRule implements HeuristicRule {

    private static final String RULE_NAME = "HIGH_DELETION_INTENSITY";
    private static final double DELETION_THRESHOLD = 5.0;
    private static final double CONFIDENCE = 0.70;

    @Override
    public RuleResult evaluate(FeatureVector14 vector) {
        if (vector.getDeletionIntensity() > DELETION_THRESHOLD) {
            return RuleResult.triggered(RULE_NAME, CONFIDENCE);
        }
        return RuleResult.notTriggered();
    }

    @Override
    public String getRuleName() {
        return RULE_NAME;
    }
}
