package com.anomalydetection.detector.v2;

import java.util.Collections;
import java.util.List;

/**
 * Warmup-phase specific information attached to {@link DetectionResult}.
 * <p>
 * Carries layer-specific info:
 * <ul>
 *   <li>Layer 2: triggeredRules, confidence, matchingRuleCount</li>
 *   <li>Layer 3: statisticalScore (Euclidean distance), dynamicThreshold, historySize</li>
 * </ul>
 */
public final class WarmupInfo {

    private final int matchingRuleCount;
    private final List<String> triggeredRules;
    private final double confidence;
    private final boolean addToBaseline;
    private final int layer;                 // 0=normal, 1=L1, 2=L2, 3=L3
    private final double statisticalScore;   // Layer 3 Euclidean distance score
    private final double dynamicThreshold;   // Layer 3 dynamic threshold
    private final int historySize;           // history normals available for Layer 3

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
