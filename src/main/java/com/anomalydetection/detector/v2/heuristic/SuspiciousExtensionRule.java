package com.anomalydetection.detector.v2.heuristic;

import com.anomalydetection.detector.v2.FeatureVector14;

/**
 * Layer 1 (Deterministic) rule: suspicious_extension_ratio > 0 means
 * a known ransomware extension was detected. This is a high-confidence signal.
 */
public class SuspiciousExtensionRule implements HeuristicRule {

    private static final String RULE_NAME = "SUSPICIOUS_EXTENSION";
    private static final double CONFIDENCE = 1.0;

    @Override
    public RuleResult evaluate(FeatureVector14 vector) {
        if (vector.getSuspiciousExtensionRatio() > 0) {
            return RuleResult.triggered(RULE_NAME, CONFIDENCE);
        }
        return RuleResult.notTriggered();
    }

    @Override
    public String getRuleName() {
        return RULE_NAME;
    }
}
