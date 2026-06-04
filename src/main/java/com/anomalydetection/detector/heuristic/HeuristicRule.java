package com.anomalydetection.detector.heuristic;

import com.anomalydetection.features.FeatureVector;

@FunctionalInterface
public interface HeuristicRule {
    RuleResult evaluate(FeatureVector vector);
    default RuleResult evaluate(FeatureVector vector, int sensitivity) {
        return evaluate(vector);
    }
    default String getRuleName() { return getClass().getSimpleName().replace("Rule", ""); }
}
