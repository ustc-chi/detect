package com.anomalydetection.detector;

import java.util.Collections;
import java.util.List;

/**
 * Warmup-phase specific information attached to {@link DetectionResult}.
 */
public final class WarmupInfo {

    private final int matchingRuleCount;
    private final List<String> triggeredRules;
    private final double confidence;
    private final boolean addToBaseline;
    private final int layer;
    private final double statisticalScore;
    private final double dynamicThreshold;
    private final int historySize;

    public WarmupInfo(int matchingRuleCount, List<String> triggeredRules,
                      double confidence, boolean addToBaseline,
                      int layer, double statisticalScore,
                      double dynamicThreshold, int historySize) {
        this.matchingRuleCount = matchingRuleCount;
        this.triggeredRules = triggeredRules == null ? Collections.emptyList() : List.copyOf(triggeredRules);
        this.confidence = confidence;
        this.addToBaseline = addToBaseline;
        this.layer = layer;
        this.statisticalScore = statisticalScore;
        this.dynamicThreshold = dynamicThreshold;
        this.historySize = historySize;
    }

    public int getMatchingRuleCount() { return matchingRuleCount; }
    public List<String> getTriggeredRules() { return triggeredRules; }
    public double getConfidence() { return confidence; }
    public boolean isAddToBaseline() { return addToBaseline; }
    public int getLayer() { return layer; }
    public double getStatisticalScore() { return statisticalScore; }
    public double getDynamicThreshold() { return dynamicThreshold; }
    public int getHistorySize() { return historySize; }
}
