package com.anomalydetection.detector.v2.heuristic;

import com.anomalydetection.detector.v2.FeatureVector14;

/**
 * Pluggable heuristic rule interface for warmup-phase anomaly detection.
 * <p>
 * Each rule independently evaluates a {@link FeatureVector14} and returns
 * a {@link RuleResult} indicating whether the rule triggered and with what confidence.
 */
@FunctionalInterface
public interface HeuristicRule {

    /**
     * Evaluate this rule against the given feature vector.
     *
     * @param vector the feature vector to evaluate
     * @return the rule evaluation result
     */
    RuleResult evaluate(FeatureVector14 vector);

    /**
     * Human-readable name of this rule.
     */
    default String getRuleName() {
        return getClass().getSimpleName().replace("Rule", "");
    }
}
