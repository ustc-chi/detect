package com.anomalydetection.detector;

import com.anomalydetection.features.FeatureDescription;
import com.anomalydetection.features.FeatureType;
import com.anomalydetection.features.FeatureVector;
import com.anomalydetection.detector.heuristic.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * Warmup-phase detector using a two-layer defense strategy.
 * Layer 2 — Strong heuristic rules. Layer 3 — Dynamic statistical detection.
 */
public class WarmupDetector {

    private static final Logger LOG = Logger.getLogger(WarmupDetector.class.getName());

    private static final double Z_CAP = 10.0;
    private static final double EPSILON = 0.001;
    private static final double MAD_SCALE = 1.4826;

    /** 16-dim warmup weights aligned with FeatureType order. */
    static final double[] WARMUP_WEIGHTS = {
        1.0,   // F0  modification_ratio
        0.5,   // F1  deletion_ratio
        0.5,   // F2  creation_ratio
        0.5,   // F3  total_operations_normalized
        2.0,   // F4  peak_burst_velocity
        5.0,   // F5  burst_mod_purity
        1.5,   // F6  high_value_ext_ratio
        3.0,   // F7  inter_op_time_cv_burst
        0.5,   // F8  directory_coverage_depth
        0.0,   // F9  temporal_uniformity
        0.0,   // F10 rename_correlation
        0.0,   // F11 hourly_concentration
        0.0,   // F12 hourly_entropy
        1.0,   // F13 per_type_entropy
        0.0,   // F14 extension_count_cv
        0.0    // F15 created_ext_novelty
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
     * Detect anomaly in warmup phase with the given sensitivity.
     *
     * @param vector         current feature vector from feature-extractor
     * @param historyNormals previously accumulated normal vectors
     * @param sensitivity    detection sensitivity in [0.0, 1.0] (1.0 = most sensitive)
     * @return warmup detection result
     */
    public WarmupDetectionResult detect(FeatureVector vector, List<FeatureVector> historyNormals,
                                        double sensitivity) {
        double thresholdMultiplier = SensitivityAdjuster.getThresholdMultiplier(sensitivity);

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

        // Apply sensitivity multiplier to Layer 2 rule confidence threshold.
        // Lower multiplier (high sensitivity) → lower effective threshold → more triggers.
        // Higher multiplier (low sensitivity) → higher effective threshold → fewer triggers.
        if (!triggeredRules.isEmpty() && maxConfidence >= 0.5 * thresholdMultiplier) {
            return WarmupDetectionResult.anomaly(2, maxConfidence, triggeredRules);
        }

        // Layer 3: Dynamic statistical detection (requires >=2 normals)
        if (historyNormals != null && historyNormals.size() >= 2) {
            WarmupDetectionResult statResult = checkStatisticalAnomaly(vector, historyNormals);
            if (statResult != null) return statResult;
        }

        return WarmupDetectionResult.normal(triggeredRules);
    }

    /**
     * Detect anomaly in warmup phase with default sensitivity (0.7).
     *
     * @param vector         current feature vector from feature-extractor
     * @param historyNormals previously accumulated normal vectors
     * @return warmup detection result
     */
    public WarmupDetectionResult detect(FeatureVector vector, List<FeatureVector> historyNormals) {
        return detect(vector, historyNormals, SensitivityAdjuster.getDefaultSensitivity());
    }

    // =====================================================================
    // Private: Layer 3 statistical detection
    // =====================================================================

    private WarmupDetectionResult checkStatisticalAnomaly(FeatureVector vector,
                                                           List<FeatureVector> historyNormals) {
        double[] median = new double[FeatureType.COUNT];
        double[] mad = new double[FeatureType.COUNT];

        // Compute median and MAD from history vectors
        double[][] historyValues = new double[historyNormals.size()][FeatureType.COUNT];
        for (int h = 0; h < historyNormals.size(); h++) {
            historyValues[h] = historyNormals.get(h).toArray();
        }

        for (int i = 0; i < FeatureType.COUNT; i++) {
            double[] col = new double[historyNormals.size()];
            for (int h = 0; h < historyNormals.size(); h++) {
                col[h] = historyValues[h][i];
            }
            java.util.Arrays.sort(col);
            median[i] = col[col.length / 2];
            // MAD: median of absolute deviations
            double[] absDev = new double[col.length];
            for (int h = 0; h < col.length; h++) {
                absDev[h] = Math.abs(col[h] - median[i]);
            }
            java.util.Arrays.sort(absDev);
            mad[i] = absDev[absDev.length / 2] * MAD_SCALE + EPSILON;
        }

        // Score the current vector
        double[] current = vector.toArray();
        double[] zScores = new double[FeatureType.COUNT];
        double[] contributions = new double[FeatureType.COUNT];
        double sumWeightedZ2 = 0;

        for (int i = 0; i < FeatureType.COUNT; i++) {
            double z = Math.max(-Z_CAP, Math.min(Z_CAP, (current[i] - median[i]) / mad[i]));
            zScores[i] = z;
            contributions[i] = WARMUP_WEIGHTS[i] * z * z;
            sumWeightedZ2 += WARMUP_WEIGHTS[i] * z * z;
        }

        double score = Math.sqrt(sumWeightedZ2);
        double threshold = computeDynamicThreshold(historyValues);

        if (score > threshold) {
            List<DimensionReport> dims = buildDimensionReports(vector, current, zScores, contributions);
            List<DimensionReport> topDevs = computeTopDeviations(dims);
            return WarmupDetectionResult.statisticalAnomaly(score, threshold, dims, topDevs);
        }

        return null; // normal
    }

    private double computeDynamicThreshold(double[][] historyValues) {
        int n = historyValues.length;
        double[] historyScores = new double[n];
        for (int h = 0; h < n; h++) {
            // Use last n-1 as baseline to score current
            double sum = 0;
            for (int i = 0; i < FeatureType.COUNT; i++) {
                sum += WARMUP_WEIGHTS[i] * historyValues[h][i] * historyValues[h][i];
            }
            historyScores[h] = Math.sqrt(sum);
        }
        java.util.Arrays.sort(historyScores);
        double median = historyScores[n / 2];
        double[] absDev = new double[n];
        for (int h = 0; h < n; h++) {
            absDev[h] = Math.abs(historyScores[h] - median);
        }
        java.util.Arrays.sort(absDev);

        // Adaptive multiplier based on available history size
        int idx = java.util.Arrays.binarySearch(MULTIPLIER_BOUNDARIES, n);
        if (idx < 0) idx = -idx - 1;
        idx = Math.min(idx, MULTIPLIER_VALUES.length - 1);
        double multiplier = MULTIPLIER_VALUES[idx];

        double madScore = absDev[n / 2] * MAD_SCALE + EPSILON;
        return median + multiplier * madScore;
    }

    private List<DimensionReport> buildDimensionReports(FeatureVector vector, double[] values,
                                                         double[] zScores, double[] contributions) {
        List<DimensionReport> reports = new ArrayList<>(FeatureType.COUNT);
        for (int i = 0; i < FeatureType.COUNT; i++) {
            FeatureType ft = FeatureType.values()[i];
            String desc = buildFeatureDescription(vector, ft);
            reports.add(new DimensionReport(
                    i, ft.key(), values[i], zScores[i], contributions[i],
                    WARMUP_WEIGHTS[i], desc, "", Collections.emptyMap()));
        }
        return reports;
    }

    private static String buildFeatureDescription(FeatureVector vector, FeatureType ft) {
        if (vector.getExtendInfo().containsKey(ft.key())) {
            FeatureDescription fd = vector.getDes(ft);
            return fd.cn() + " / " + fd.en();
        }
        return ft.desEN();
    }

    private List<DimensionReport> computeTopDeviations(List<DimensionReport> dimensions) {
        List<DimensionReport> sorted = new ArrayList<>(dimensions);
        sorted.sort((a, b) -> Double.compare(b.getContribution(), a.getContribution()));
        return sorted.subList(0, Math.min(5, sorted.size()));
    }

    // =====================================================================
    // Nested: WarmupDetectionResult
    // =====================================================================

    public static class WarmupDetectionResult {
        private final boolean isAnomaly;
        private final int layer;
        private final double confidence;
        private final List<String> triggeredRules;
        private final double statisticalScore;
        private final double dynamicThreshold;
        private final List<DimensionReport> dimensionReports;
        private final List<DimensionReport> topDeviations;

        private WarmupDetectionResult(boolean isAnomaly, int layer, double confidence,
                                       List<String> triggeredRules, double statisticalScore,
                                       double dynamicThreshold, List<DimensionReport> dimensionReports,
                                       List<DimensionReport> topDeviations) {
            this.isAnomaly = isAnomaly;
            this.layer = layer;
            this.confidence = confidence;
            this.triggeredRules = triggeredRules;
            this.statisticalScore = statisticalScore;
            this.dynamicThreshold = dynamicThreshold;
            this.dimensionReports = dimensionReports;
            this.topDeviations = topDeviations;
        }

        static WarmupDetectionResult anomaly(int layer, double confidence, List<String> rules) {
            return new WarmupDetectionResult(true, layer, confidence, rules, 0, 0, null, null);
        }

        static WarmupDetectionResult statisticalAnomaly(double score, double threshold,
                                                         List<DimensionReport> dims,
                                                         List<DimensionReport> topDevs) {
            return new WarmupDetectionResult(true, 3, 0, List.of(), score, threshold, dims, topDevs);
        }

        static WarmupDetectionResult normal(List<String> triggeredRules) {
            return new WarmupDetectionResult(false, 0, 0, triggeredRules, 0, 0, null, null);
        }

        WarmupInfo toWarmupInfo() {
            return new WarmupInfo(
                    triggeredRules != null ? triggeredRules.size() : 0,
                    triggeredRules != null ? triggeredRules : Collections.emptyList(),
                    confidence, !isAnomaly, layer,
                    statisticalScore, dynamicThreshold,
                    0);
        }

        DetectionResult toDetectionResult(String resourceId, FeatureVector vector) {
            return DetectionResult.warmupResult(resourceId, vector, this);
        }

        public boolean isAnomaly() { return isAnomaly; }
        public int getLayer() { return layer; }
        public double getConfidence() { return confidence; }
        public List<String> getTriggeredRules() { return triggeredRules; }
        public double getStatisticalScore() { return statisticalScore; }
        public double getDynamicThreshold() { return dynamicThreshold; }
        public List<DimensionReport> getDimensionReports() { return dimensionReports; }
        public List<DimensionReport> getTopDeviations() { return topDeviations; }
    }
}
