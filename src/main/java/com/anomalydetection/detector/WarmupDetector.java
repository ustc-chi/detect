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

    /** 16-dim warmup weights aligned with FeatureType order.
     *  Optimized via WeightOptimizationRunner (seed=42, 20000 iterations, 2026-06-02). */
    static final double[] WARMUP_WEIGHTS = {
        0.0444, // F0  modification_ratio
        0.1572, // F1  deletion_ratio
        0.0290, // F2  creation_ratio
        0.0150, // F3  total_operations_normalized
        0.1090, // F4  peak_burst_velocity
        0.0105, // F5  burst_mod_purity
        0.1378, // F6  high_value_ext_ratio
        0.0123, // F7  inter_op_time_cv_burst
        0.0043, // F8  directory_coverage_depth
        0.0767, // F9  temporal_uniformity
        0.0238, // F10 rename_correlation
        0.0171, // F11 hourly_concentration
        0.0128, // F12 hourly_entropy
        0.0964, // F13 per_type_entropy
        0.0358, // F14 extension_count_cv
        0.1625  // F15 created_ext_novelty
    };

    // Adaptive multipliers for z-score based dynamic threshold.
    // Calibrated for the z-score scoring scale (typical historyScores range 0.5~3.0).
    // Lower values than the original raw-scale multipliers since z-scores are ~1000x smaller.
    static final double[] MULTIPLIER_BOUNDARIES = {2, 4, 6, 8};
    static final double[] MULTIPLIER_VALUES = {4.0, 2.5, 1.8, 1.2};

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

    /** Prior median for Bayesian shrinkage (from N1 design baseline). */
    private static final double[] PRIOR_MEDIAN = {
        0.50, 0.15, 0.25, 12000, 3000, 0.60, 0.28, 1.20,
        40, 0.50, 0.03, 0.20, 3.50, 1.20, 1.50, 0.10
    };

    /** Prior MAD for Bayesian shrinkage (from N1 design baseline). */
    private static final double[] PRIOR_MAD = {
        0.10, 0.05, 0.08, 5000, 2000, 0.12, 0.06, 0.25,
        15, 0.12, 0.02, 0.06, 0.50, 0.15, 0.60, 0.08
    };

    /** Effective sample size of the prior (20 = moderate confidence). */
    private static final double PRIOR_EFFECTIVE_N = 20.0;

    /**
     * Detect anomaly in warmup phase with the given sensitivity.
     *
     * @param vector         current feature vector from feature-extractor
     * @param historyNormals previously accumulated normal vectors
     * @param sensitivity    detection sensitivity in [1, 10] (10 = most sensitive)
     * @return warmup detection result
     */
    public WarmupDetectionResult detect(FeatureVector vector, List<FeatureVector> historyNormals,
                                        int sensitivity) {
        double thresholdMultiplier = SensitivityAdjuster.getThresholdMultiplier(sensitivity);

        List<String> triggeredRules = new ArrayList<>();
        double maxConfidence = 0.0;

        // Layer 2: Evaluate heuristic rules with sensitivity awareness
        for (HeuristicRule rule : rules) {
            RuleResult result = rule.evaluate(vector, sensitivity);
            if (result.isTriggered()) {
                triggeredRules.add(result.getRuleName());
                if (result.getConfidence() > maxConfidence) {
                    maxConfidence = result.getConfidence();
                }
            }
        }

        // Layer 2 fires when >= 2 rules trigger AND confidence gate passes.
        // This avoids single-rule false positives on normal operations like
        // batch compile (N2) or data migration (N4).
        if (triggeredRules.size() >= 2 && maxConfidence >= 0.5 * thresholdMultiplier) {
            return WarmupDetectionResult.anomaly(2, maxConfidence, triggeredRules);
        }

        // Layer 3: Dynamic statistical detection (requires >=2 normals)
        // Threshold is adjusted by sensitivity: high sensitivity → lower threshold → more detection
        if (historyNormals != null && historyNormals.size() >= 2) {
            WarmupDetectionResult statResult = checkStatisticalAnomaly(vector, historyNormals, thresholdMultiplier);
            if (statResult != null) return statResult;
        }

        return WarmupDetectionResult.normal(triggeredRules);
    }

    /**
     * Detect anomaly in warmup phase with default sensitivity (5 — medium).
     *
     * @param vector         current feature vector from feature-extractor
     * @param historyNormals previously accumulated normal vectors
     * @return warmup detection result
     */
    public WarmupDetectionResult detect(FeatureVector vector, List<FeatureVector> historyNormals) {
        return detect(vector, historyNormals, SensitivityAdjuster.DEFAULT_SENSITIVITY);
    }

    // =====================================================================
    // Private: Layer 3 statistical detection
    // =====================================================================

    private WarmupDetectionResult checkStatisticalAnomaly(FeatureVector vector,
                                                           List<FeatureVector> historyNormals,
                                                           double thresholdMultiplier) {
        double[] median = new double[FeatureType.COUNT];
        double[] mad = new double[FeatureType.COUNT];

        // Compute median and MAD from history vectors
        double[][] historyValues = new double[historyNormals.size()][FeatureType.COUNT];
        for (int h = 0; h < historyNormals.size(); h++) {
            historyValues[h] = historyNormals.get(h).toArray();
        }

        // Step 1: Compute sample median and MAD (same as before)
        for (int i = 0; i < FeatureType.COUNT; i++) {
            double[] col = new double[historyNormals.size()];
            for (int h = 0; h < historyNormals.size(); h++) {
                col[h] = historyValues[h][i];
            }
            java.util.Arrays.sort(col);
            median[i] = col[col.length / 2];
            double[] absDev = new double[col.length];
            for (int h = 0; h < col.length; h++) {
                absDev[h] = Math.abs(col[h] - median[i]);
            }
            java.util.Arrays.sort(absDev);
            mad[i] = absDev[absDev.length / 2] * MAD_SCALE + EPSILON;
        }

        // Step 2: Bayesian shrinkage toward prior (stable when n < 20)
        // shrinkage = n/(n+PRIOR_EFFECTIVE_N): approaches 1.0 as n grows.
        double shrinkage = historyNormals.size()
                / (historyNormals.size() + PRIOR_EFFECTIVE_N);
        for (int i = 0; i < FeatureType.COUNT; i++) {
            median[i] = shrinkage * median[i] + (1 - shrinkage) * PRIOR_MEDIAN[i];
            mad[i] = Math.max(shrinkage * mad[i] + (1 - shrinkage) * PRIOR_MAD[i], EPSILON);
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
        // Use z-score based dynamic threshold (consistent with scoring formula)
        double threshold = computeDynamicThreshold(historyValues, median, mad, historyNormals.size());
        // Adjust threshold by sensitivity: higher sensitivity (lower multiplier) → easier to trigger
        double adjustedThreshold = threshold * Math.max(thresholdMultiplier, 0.3);

        if (score > adjustedThreshold) {
            List<DimensionReport> dims = buildDimensionReports(vector, current, zScores, contributions);
            List<DimensionReport> topDevs = computeTopDeviations(dims);
            return WarmupDetectionResult.statisticalAnomaly(score, threshold, dims, topDevs);
        }

        return null; // normal
    }

    /**
     * Computes dynamic threshold from history vectors using z-score based scoring
     * (same formula as detection scoring, ensuring comparable magnitudes).
     */
    private double computeDynamicThreshold(double[][] historyValues, double[] baseMedian,
                                            double[] baseMad, int nSamples) {
        int n = historyValues.length;
        double[] historyScores = new double[n];
        for (int h = 0; h < n; h++) {
            double sum = 0;
            for (int i = 0; i < FeatureType.COUNT; i++) {
                // Score history vectors using same z-score formula as detection
                double z = Math.max(-Z_CAP, Math.min(Z_CAP,
                        (historyValues[h][i] - baseMedian[i]) / Math.max(baseMad[i], EPSILON)));
                sum += WARMUP_WEIGHTS[i] * z * z;
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
            int ruleCount = triggeredRules != null ? triggeredRules.size() : 0;
            // Decouple alerting from baseline inclusion:
            // - Alert (isAnomaly) when >= 2 rules trigger (unchanged)
            // - Add to baseline only when 0 rules trigger (prevents baseline pollution)
            //   A vector with 1+ triggered rules has suspicious signals and
            //   should not contaminate the normal baseline even if it didn't trigger an alert.
            boolean baseline = !isAnomaly && ruleCount < 1;
            return new WarmupInfo(
                    ruleCount,
                    triggeredRules != null ? triggeredRules : Collections.emptyList(),
                    confidence, baseline, layer,
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
