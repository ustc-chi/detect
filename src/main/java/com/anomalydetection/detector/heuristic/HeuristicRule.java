package com.anomalydetection.detector.heuristic;

import com.anomalydetection.features.FeatureVector;

@FunctionalInterface
public interface HeuristicRule {
    RuleResult evaluate(FeatureVector vector);
    default RuleResult evaluate(FeatureVector vector, double sensitivity) {
        return evaluate(vector);
    }
    default String getRuleName() { return getClass().getSimpleName().replace("Rule", ""); }
}
