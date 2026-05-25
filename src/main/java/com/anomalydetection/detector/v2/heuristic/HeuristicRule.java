package com.anomalydetection.detector.v2.heuristic;

import com.anomalydetection.detector.v2.FeatureVector;

@FunctionalInterface
public interface HeuristicRule {
    RuleResult evaluate(FeatureVector vector);
    default String getRuleName() { return getClass().getSimpleName().replace("Rule", ""); }
}
