package com.anomalydetection.detector.v2.heuristic;

import com.anomalydetection.detector.v2.FeatureVector14;

/**
 * Layer 2 heuristic: burst_mod_purity > 0.95 AND peak_burst_velocity > 50.
 * High burst purity with non-trivial speed suggests automated encryption.
 */
public class BurstModPurityRule implements HeuristicRule {

    private static final String RULE_NAME = "HIGH_BURST_PURITY";
    private static final double PURITY_THRESHOLD = 0.95;
    private static final double MIN_VELOCITY = 50;
    private static final double CONFIDENCE = 0.80;

    @Override
    public RuleResult evaluate(FeatureVector14 vector) {
        if (vector.getBurstModPurity() > PURITY_THRESHOLD
                && vector.getPeakBurstVelocity() > MIN_VELOCITY) {
            return RuleResult.triggered(RULE_NAME, CONFIDENCE);
        }
        return RuleResult.notTriggered();
    }

    @Override
    public String getRuleName() {
        return RULE_NAME;
    }
}
