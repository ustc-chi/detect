package com.anomalydetection.detector.v2;

import com.anomalydetection.detector.v2.heuristic.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * Warmup-phase detector using a two-layer defense strategy.
 * Layer 2 — Strong heuristic rules. Layer 3 — Dynamic statistical detection.
 * (Pre-check for suspicious extensions/ransom notes is handled before warmup.)
 */
public class WarmupDetector {

    private static final Logger LOG = Logger.getLogger(WarmupDetector.class.getName());

    private static final double Z_CAP = 10.0;
    private static final double EPSILON = 0.001;
    private static final double MAD_SCALE = 1.4826;

    static final double[] WARMUP_WEIGHTS = {
        1.0, 5.0, 0.5, 0.5, 0.5, 15.0, 2.0, 0.0, 0.0, 1.5, 5.0, 3.0, 0.0, 3.0
    };

    static final double[] MULTIPLIER_BOUNDARIES = {2, 4, 6, 8};
    static final double[] MULTIPLIER_VALUES = {10.0, 5.0, 3.0, 2.0};

    private final List<HeuristicRule> rules;

    public WarmupDetector() {
        this.rules = List.of(
                new ModificationRatioRule(),
                new BurstModPurityRule(),
                new FileTypeConcentrationRule(),
                new InterOpTimeCvRule(),
                new HighValueTargetingRule(),
                new DeletionIntensityRule()
        );
    }

    /**
     * Detect anomaly in warmup phase.
     * @param vector         current feature vector
     * @param historyNormals previously accumulated normal vectors
     * @return warmup detection result
     */
    public WarmupDetectionResult detect(FeatureVector vector, List<FeatureVector> historyNormals) {
        List<String> triggeredRules = new ArrayList<>();
        double maxConfidence = 0.0;

        // Layer 2: Evaluate heuristic rules
        for (HeuristicRule rule : rules) {
            RuleResult result = rule.evaluate(vector);
            if (result.isTriggered()) {
                triggeredRules.add(result.getRuleName());
                if (result.getConfidence() > maxConfidence) {
                    maxConfidence = result.getConfidence();
                }
            }
        }

        if (!triggeredRules.isEmpty()) {
            return WarmupDetectionResult.anomaly(2, maxConfidence, triggeredRules);
        }

        // Layer 3: Dynamic statistical detection (requires >=2 normals)
        if (historyNormals != null && historyNormals.size() >= 2) {
            WarmupDetectionResult statResult = checkStatisticalAnomaly(vector, historyNormals);
            if (statResult != null) return statResult;
        }

        int hs = historyNormals != null ? historyNormals.size() : 0;
        return WarmupDetectionResult.normal(hs);
    }

    private WarmupDetectionResult checkStatisticalAnomaly(FeatureVector vector, List<FeatureVector> historyNormals) {
        int n = historyNormals.size();
        double[] median = new double[FeatureVector.FEATURE_COUNT];
        double[] mad = new double[FeatureVector.FEATURE_COUNT];

        for (int d = 0; d < FeatureVector.FEATURE_COUNT; d++) {
            List<Double> vals = new ArrayList<>(n);
            for (FeatureVector hv : historyNormals) vals.add(hv.get(d));
            Collections.sort(vals);
            median[d] = medianOf(vals);
            List<Double> absDevs = new ArrayList<>(n);
            for (double v : vals) absDevs.add(Math.abs(v - median[d]));
            Collections.sort(absDevs);
            double rawMad = medianOf(absDevs);
            mad[d] = rawMad * MAD_SCALE;
            if (mad[d] < EPSILON) mad[d] = Math.sqrt(EPSILON);
        }

        double score = weightedEuclideanScore(vector, median, mad, WARMUP_WEIGHTS);
        double maxHistoricalScore = 0.0;
        for (FeatureVector hv : historyNormals) {
            double s = weightedEuclideanScore(hv, median, mad, WARMUP_WEIGHTS);
            if (s > maxHistoricalScore) maxHistoricalScore = s;
        }

        double multiplier = 2.0;
        for (int i = 0; i < MULTIPLIER_BOUNDARIES.length; i++) {
            if (n <= MULTIPLIER_BOUNDARIES[i]) { multiplier = MULTIPLIER_VALUES[i]; break; }
        }

        double dynamicThreshold = maxHistoricalScore * multiplier;
        if (score > dynamicThreshold) {
            return WarmupDetectionResult.suspicious(score, dynamicThreshold, n);
        }
        return null;
    }

    private static double weightedEuclideanScore(FeatureVector vector, double[] median, double[] mad, double[] weights) {
        double sum = 0.0;
        for (int i = 0; i < FeatureVector.FEATURE_COUNT; i++) {
            double z = (vector.get(i) - median[i]) / Math.max(mad[i], EPSILON);
            z = Math.max(-Z_CAP, Math.min(Z_CAP, z));
            sum += weights[i] * z * z;
        }
        return Math.sqrt(sum);
    }

    private static double medianOf(List<Double> sorted) {
        int mid = sorted.size() / 2;
        return sorted.size() % 2 == 0 ? (sorted.get(mid - 1) + sorted.get(mid)) / 2.0 : sorted.get(mid);
    }

    /**
     * Container for warmup detection result.
     */
    public static class WarmupDetectionResult {
        private final WarmupStatus status;
        private final double confidence;
        private final List<String> triggeredRules;
        private final boolean addToBaseline;
        private final int layer;                 // 0=normal, 1=Layer1, 2=Layer2, 3=Layer3
        private final double statisticalScore;   // Layer 3 Euclidean score (0 for L1/L2)
        private final double dynamicThreshold;   // Layer 3 threshold (0 for L1/L2)
        private final int historySize;           // normals used in Layer 3 (0 for L1/L2)

        public WarmupDetectionResult(WarmupStatus status, double confidence,
                                      List<String> triggeredRules, boolean addToBaseline,
                                      int layer, double statisticalScore,
                                      double dynamicThreshold, int historySize) {
            this.status = status;
            this.confidence = confidence;
            this.triggeredRules = triggeredRules;
            this.addToBaseline = addToBaseline;
            this.layer = layer;
            this.statisticalScore = statisticalScore;
            this.dynamicThreshold = dynamicThreshold;
            this.historySize = historySize;
        }

        // Backward-compatible constructor for simple cases
        public static WarmupDetectionResult normal(int historySize) {
            return new WarmupDetectionResult(WarmupStatus.NORMAL, 0.0, List.of(), true,
                    0, 0, 0, historySize);
        }

        public static WarmupDetectionResult anomaly(int layer, double confidence, List<String> rules) {
            return new WarmupDetectionResult(WarmupStatus.ANOMALY, confidence, rules, false,
                    layer, 0, 0, 0);
        }

        public static WarmupDetectionResult suspicious(double score, double threshold, int historySize) {
            return new WarmupDetectionResult(WarmupStatus.SUSPICIOUS, 0.70,
                    List.of("STATISTICAL_ANOMALY"), false,
                    3, score, threshold, historySize);
        }

        public WarmupStatus getStatus() { return status; }
        public double getConfidence() { return confidence; }
        public List<String> getTriggeredRules() { return triggeredRules; }
        public boolean isAddToBaseline() { return addToBaseline; }
        public boolean isAnomaly() { return status == WarmupStatus.ANOMALY || status == WarmupStatus.SUSPICIOUS; }
        public int getLayer() { return layer; }
        public double getStatisticalScore() { return statisticalScore; }
        public double getDynamicThreshold() { return dynamicThreshold; }
        public int getHistorySize() { return historySize; }
    }
}
