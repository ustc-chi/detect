package com.anomalydetection.detector;

import com.anomalydetection.features.RansomwareFeatureVector;
import java.util.List;
import java.util.Map;

public class DetectionResult {
    private final double score;
    private final double threshold;
    private final boolean isAnomaly;
    private final Map<String, Double> zScores;
    private final List<Map.Entry<String, Double>> topDeviations;
    private final RansomwareFeatureVector vector;
    // Optional signature match description. If non-null, a signature was detected.
    private final String signatureMatch;
    private final boolean directionReversed;

    public DetectionResult(double score,
                           double threshold,
                           boolean isAnomaly,
                           Map<String, Double> zScores,
                           List<Map.Entry<String, Double>> topDeviations,
                           RansomwareFeatureVector vector) {
        this.score = score;
        this.threshold = threshold;
        this.isAnomaly = isAnomaly;
        this.zScores = zScores;
        this.topDeviations = topDeviations;
        this.vector = vector;
        this.signatureMatch = null;
        this.directionReversed = false;
    }

    // Overloaded constructor that accepts a signature-match description
    public DetectionResult(double score,
                           double threshold,
                           boolean isAnomaly,
                           Map<String, Double> zScores,
                           List<Map.Entry<String, Double>> topDeviations,
                           RansomwareFeatureVector vector,
                           String signatureMatch) {
        this.score = score;
        this.threshold = threshold;
        this.isAnomaly = isAnomaly;
        this.zScores = zScores;
        this.topDeviations = topDeviations;
        this.vector = vector;
        this.signatureMatch = signatureMatch;
        this.directionReversed = false;
    }

    public DetectionResult(double score,
                           double threshold,
                           boolean isAnomaly,
                           Map<String, Double> zScores,
                           List<Map.Entry<String, Double>> topDeviations,
                           RansomwareFeatureVector vector,
                           String signatureMatch,
                           boolean directionReversed) {
        this.score = score;
        this.threshold = threshold;
        this.isAnomaly = isAnomaly;
        this.zScores = zScores;
        this.topDeviations = topDeviations;
        this.vector = vector;
        this.signatureMatch = signatureMatch;
        this.directionReversed = directionReversed;
    }

    public double getScore() { return score; }
    public double getThreshold() { return threshold; }
    public boolean isAnomaly() { return signatureMatch != null || isAnomaly; }
    public Map<String, Double> getZScores() { return zScores; }
    public List<Map.Entry<String, Double>> getTopDeviations() { return topDeviations; }
    public RansomwareFeatureVector getVector() { return vector; }
    public String getSignatureMatch() { return signatureMatch; }
    public boolean isDirectionReversed() { return directionReversed; }
}
