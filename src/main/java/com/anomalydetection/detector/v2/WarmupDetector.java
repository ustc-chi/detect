package com.anomalydetection.detector.v2;

import com.anomalydetection.detector.BaselineStatistics;
import com.anomalydetection.detector.WeightedEuclideanScorer;
import com.anomalydetection.detector.v2.heuristic.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * Warmup-phase detector using a three-layer defense strategy.
 * <p>
 * <b>Layer 1</b> — Deterministic rules (absolute certainty): suspicious extension, ransom note patterns.
 * <b>Layer 2</b> — Strong heuristic rules (single-round available): modification ratio, burst purity, etc.
 * <b>Layer 3</b> — Dynamic statistical detection (requires ≥2 normal samples): weighted Euclidean distance
 * with a threshold that tightens as more data accumulates.
 * <p>
 * Only Layer 3 normal results are added to the baseline for future statistical scoring.
 */
public class WarmupDetector {

    private static final Logger LOG = Logger.getLogger(WarmupDetector.class.getName());

    /**
     * Warmup-phase weights — features requiring stable baselines (avg_modified_size,
     * size_std_dev, size_change_kurtosis) are disabled (weight=0).
     */
    static final double[] WARMUP_WEIGHTS = {
        1.0,   // 0: total_operations
        5.0,   // 1: modification_ratio
        0.5,   // 2: deletion_intensity
        0.5,   // 3: directory_spread
        0.5,   // 4: extension_diversity
        15.0,  // 5: suspicious_extension_ratio
        2.0,   // 6: peak_burst_velocity
        0.0,   // 7: avg_modified_size — DISABLED
        0.0,   // 8: size_std_dev — DISABLED
        1.5,   // 9: high_value_ext_ratio
        5.0,   // 10: burst_mod_purity
        3.0,   // 11: file_type_concentration
        0.0,   // 12: size_change_kurtosis — DISABLED
        3.0    // 13: inter_op_time_cv
    };

    private static final double Z_CAP = 10.0;
    private static final double EPSILON = 0.001;
    private static final double MAD_SCALE = 1.4826;

    // Dynamic threshold multipliers: relaxed when data is scarce, tighter with more data
    static final double[] MULTIPLIER_BOUNDARIES = {2, 4, 6, 8};
    static final double[] MULTIPLIER_VALUES = {10.0, 5.0, 3.0, 2.0};

    private final List<HeuristicRule> rules;

    public WarmupDetector() {
        this.rules = List.of(
                new SuspiciousExtensionRule(),     // Layer 1
                new ModificationRatioRule(),       // Layer 2
                new BurstModPurityRule(),
                new FileTypeConcentrationRule(),
                new InterOpTimeCvRule(),
                new HighValueTargetingRule(),
                new DeletionIntensityRule()
        );
    }

    /**
     * Detect anomaly in warmup phase.
     *
     * @param vector         current feature vector
     * @param historyNormals previously accumulated normal vectors for this resource
     * @return warmup detection result information
     */
    public WarmupDetectionResult detect(FeatureVector14 vector, List<FeatureVector14> historyNormals) {
        List<String> triggeredRules = new ArrayList<>();
        double maxConfidence = 0.0;

        // ========== Layer 1 & 2: Evaluate all rules ==========
        for (HeuristicRule rule : rules) {
            RuleResult result = rule.evaluate(vector);
            if (result.isTriggered()) {
                triggeredRules.add(result.getRuleName());
                if (result.getConfidence() > maxConfidence) {
                    maxConfidence = result.getConfidence();
                }
            }
        }

        // Layer 1 (deterministic) triggers: SuspiciousExtensionRule
        // Layer 2 (heuristic) triggers: any other rule
        if (!triggeredRules.isEmpty()) {
            boolean addToBaseline = false;
            return new WarmupDetectionResult(
                    WarmupStatus.ANOMALY,
                    maxConfidence,
                    triggeredRules,
                    addToBaseline
            );
        }

        // ========== Layer 3: Dynamic statistical detection ==========
        if (historyNormals != null && historyNormals.size() >= 2) {
            WarmupDetectionResult statResult = checkStatisticalAnomaly(vector, historyNormals);
            if (statResult != null) {
                return statResult;
            }
        }

        // ========== Normal: add to baseline ==========
        return new WarmupDetectionResult(
                WarmupStatus.NORMAL,
                0.0,
                List.of(),
                true
        );
    }

    /**
     * Layer 3: dynamic statistical anomaly detection using accumulated normal history.
     * Computes v2 weighted Euclidean score directly (no bridge to v1 infrastructure).
     */
    private WarmupDetectionResult checkStatisticalAnomaly(FeatureVector14 vector,
                                                           List<FeatureVector14> historyNormals) {
        int n = historyNormals.size();

        // Compute per-dimension median and MAD from history
        double[] median = new double[FeatureVector14.FEATURE_COUNT];
        double[] mad = new double[FeatureVector14.FEATURE_COUNT];

        for (int d = 0; d < FeatureVector14.FEATURE_COUNT; d++) {
            List<Double> vals = new ArrayList<>(n);
            for (FeatureVector14 hv : historyNormals) {
                vals.add(hv.get(d));
            }
            Collections.sort(vals);
            median[d] = medianOf(vals);

            List<Double> absDevs = new ArrayList<>(n);
            for (double v : vals) {
                absDevs.add(Math.abs(v - median[d]));
            }
            Collections.sort(absDevs);
            double rawMad = medianOf(absDevs);
            mad[d] = rawMad * MAD_SCALE;
            if (mad[d] < EPSILON) {
                mad[d] = Math.sqrt(EPSILON);
            }
        }

        // Compute score for current vector
        double score = computeWeightedEuclideanScore(vector, median, mad, WARMUP_WEIGHTS);

        // Compute max historical score
        double maxHistoricalScore = 0.0;
        for (FeatureVector14 hv : historyNormals) {
            double s = computeWeightedEuclideanScore(hv, median, mad, WARMUP_WEIGHTS);
            if (s > maxHistoricalScore) maxHistoricalScore = s;
        }

        double multiplier = 2.0;
        for (int i = 0; i < MULTIPLIER_BOUNDARIES.length; i++) {
            if (n <= MULTIPLIER_BOUNDARIES[i]) {
                multiplier = MULTIPLIER_VALUES[i];
                break;
            }
        }

        double dynamicThreshold = maxHistoricalScore * multiplier;

        LOG.fine(String.format(
                "Warmup Layer 3: n=%d, score=%.4f, maxHistorical=%.4f, multiplier=%.1f, threshold=%.4f",
                n, score, maxHistoricalScore, multiplier, dynamicThreshold));

        if (score > dynamicThreshold) {
            return new WarmupDetectionResult(
                    WarmupStatus.SUSPICIOUS,
                    0.70,
                    List.of("STATISTICAL_ANOMALY"),
                    false
            );
        }

        return null; // normal
    }

    /**
     * Compute weighted Euclidean distance score for a 14-dim vector against baseline.
     * score = sqrt(Σ w_i × z_i²) where z_i = (value_i - median_i) / mad_i, clamped to ±Z_CAP.
     */
    private static double computeWeightedEuclideanScore(
            FeatureVector14 vector, double[] median, double[] mad, double[] weights) {
        double sum = 0.0;
        for (int i = 0; i < FeatureVector14.FEATURE_COUNT; i++) {
            double z = (vector.get(i) - median[i]) / Math.max(mad[i], EPSILON);
            z = Math.max(-Z_CAP, Math.min(Z_CAP, z));
            sum += weights[i] * z * z;
        }
        return Math.sqrt(sum);
    }

    private static double medianOf(List<Double> sorted) {
        int mid = sorted.size() / 2;
        if (sorted.size() % 2 == 0) {
            return (sorted.get(mid - 1) + sorted.get(mid)) / 2.0;
        } else {
            return sorted.get(mid);
        }
    }

    /**
     * Container for warmup detection result before it is wrapped into DetectionResult.
     */
    public static class WarmupDetectionResult {
        private final WarmupStatus status;
        private final double confidence;
        private final List<String> triggeredRules;
        private final boolean addToBaseline;

        public WarmupDetectionResult(WarmupStatus status, double confidence,
                                      List<String> triggeredRules, boolean addToBaseline) {
            this.status = status;
            this.confidence = confidence;
            this.triggeredRules = triggeredRules;
            this.addToBaseline = addToBaseline;
        }

        public WarmupStatus getStatus() { return status; }
        public double getConfidence() { return confidence; }
        public List<String> getTriggeredRules() { return triggeredRules; }
        public boolean isAddToBaseline() { return addToBaseline; }
    }
}
