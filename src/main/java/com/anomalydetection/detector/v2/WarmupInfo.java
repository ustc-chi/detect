package com.anomalydetection.detector.v2;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Warmup-phase specific information attached to a {@link DetectionResult}.
 */
public final class WarmupInfo {

    private final int matchingRuleCount;
    private final List<String> triggeredRules;
    private final double confidence;
    private final boolean addToBaseline;

    public WarmupInfo(int matchingRuleCount,
                      List<String> triggeredRules,
                      double confidence,
                      boolean addToBaseline) {
        this.matchingRuleCount = matchingRuleCount;
        this.triggeredRules = triggeredRules == null
                ? Collections.emptyList()
                : List.copyOf(triggeredRules);
        this.confidence = confidence;
        this.addToBaseline = addToBaseline;
    }

    public int getMatchingRuleCount() { return matchingRuleCount; }
    public List<String> getTriggeredRules() { return triggeredRules; }
    public double getConfidence() { return confidence; }
    public boolean isAddToBaseline() { return addToBaseline; }
}
