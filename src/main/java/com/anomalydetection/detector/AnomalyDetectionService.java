package com.anomalydetection.detector;

import com.anomalydetection.features.FeatureVector;
import com.anomalydetection.precheck.PreCheckService;
import com.anomalydetection.precheck.PreCheckResult;

import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Unified entry point for anomaly detection.
 * <p>
 * Detection flow:
 * <ol>
 *   <li><b>Pre-check</b> — Inspect the FeatureVector for signature matches
 *       (suspicious extensions / ransom note patterns extracted by feature-extractor).
 *       If hit, return anomaly immediately.</li>
 *   <li><b>Phase determination</b> — Query the resource's normal history count.
 *       If &lt; NORMAL_THRESHOLD, use WarmupDetector; otherwise use ActiveDetector.</li>
 *   <li><b>Warmup phase</b> — Heuristic rules + dynamic threshold (sensitivity-aware).</li>
 *   <li><b>Active phase</b> — Weighted Euclidean distance with pre-computed
 *       baseline statistics (threshold adjusted via sensitivity by external module).</li>
 * </ol>
 */
public class AnomalyDetectionService {

    private static final Logger LOG = Logger.getLogger(AnomalyDetectionService.class.getName());

    /** Default number of normal vectors needed to transition to Active phase. */
    public static final int DEFAULT_NORMAL_THRESHOLD = 10;

    private final int normalThreshold;
    private final int defaultWarmupSensitivity;
    private final int defaultActiveSensitivity;
    private final WarmupDetector warmupDetector;
    private final ActiveDetector activeDetector;
    private final BaselineDataProvider baselineProvider;
    private final PreCheckService preCheckService;

    // =====================================================================
    // TODO: 权重从数据库查询 —— 表名和结构尚未确定
    // 当前使用默认权重占位，后续需实现：
    //   double[] queryWeightsFromDB(String resourceId)
    //
    // 数据库表参考结构:
    //   CREATE TABLE detection_weights (
    //       resource_id VARCHAR(64) NOT NULL,
    //       dim_index INT NOT NULL,
    //       weight DOUBLE NOT NULL,
    //       PRIMARY KEY (resource_id, dim_index)
    //   );
    // =====================================================================
    // Optimized via WeightOptimizationRunner (seed=42, 20000 iterations, 2026-06-02)
    // Dataset: 50 core normals (N1+N2+N3+N5+N6) vs 69 attack variants (14 types)
    // AUC=0.9626, detected=59/69, core FP=2/48
    private static final double[] FALLBACK_WEIGHTS = {
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

    // ===== Constructors =====

    public AnomalyDetectionService() {
        this(DEFAULT_NORMAL_THRESHOLD, new ExternalBaselineProvider(),
             SensitivityAdjuster.DEFAULT_SENSITIVITY, SensitivityAdjuster.DEFAULT_SENSITIVITY);
    }

    public AnomalyDetectionService(int normalThreshold) {
        this(normalThreshold, new ExternalBaselineProvider(),
             SensitivityAdjuster.DEFAULT_SENSITIVITY, SensitivityAdjuster.DEFAULT_SENSITIVITY);
    }

    public AnomalyDetectionService(int normalThreshold, BaselineDataProvider baselineProvider) {
        this(normalThreshold, baselineProvider,
             SensitivityAdjuster.DEFAULT_SENSITIVITY, SensitivityAdjuster.DEFAULT_SENSITIVITY);
    }

    public AnomalyDetectionService(int normalThreshold, BaselineDataProvider baselineProvider,
                                    int defaultWarmupSensitivity, int defaultActiveSensitivity) {
        this.normalThreshold = normalThreshold;
        this.defaultWarmupSensitivity = defaultWarmupSensitivity;
        this.defaultActiveSensitivity = defaultActiveSensitivity;
        this.warmupDetector = new WarmupDetector();
        this.activeDetector = new ActiveDetector();
        this.baselineProvider = baselineProvider;
        this.preCheckService = new PreCheckService();
    }

    // ===== Public API =====

    /**
     * Run the full detection pipeline using configured default sensitivities.
     *
     * @param vector     feature vector from feature-extractor
     * @param resourceId resource identifier for baseline lookup
     * @return detection result
     */
    public DetectionResult detect(FeatureVector vector, String resourceId) {
        return detect(vector, resourceId, defaultWarmupSensitivity, defaultActiveSensitivity);
    }

    /**
     * Run the full detection pipeline with explicit warmup and active sensitivities.
     *
     * @param vector             feature vector from feature-extractor (includes precheck data)
     * @param resourceId         resource identifier for baseline lookup
     * @param warmupSensitivity  warmup phase sensitivity in [1, 10] (10 = most sensitive)
     * @param activeSensitivity  active phase sensitivity in [1, 10] (10 = most sensitive)
     * @return detection result
     */
    public DetectionResult detect(FeatureVector vector, String resourceId,
                                   int warmupSensitivity, int activeSensitivity) {
        // Step 1: Pre-check — inspect FeatureVector for signature matches
        PreCheckResult preCheck = preCheckService.check(vector);
        if (preCheck.isMatch()) {
            String signature = formatPreCheckSignature(preCheck);
            LOG.info("Resource " + resourceId + ": pre-check hit — " + signature);
            return DetectionResult.signatureMatchResult(resourceId, signature);
        }

        // Step 2: Phase determination
        List<FeatureVector> historyNormals = baselineProvider.getHistoryNormals(resourceId);
        int normalCount = historyNormals != null ? historyNormals.size() : 0;

        if (normalCount < normalThreshold) {
            // Warmup phase — sensitivity used internally via SensitivityAdjuster
            LOG.fine("Resource " + resourceId + ": warmup phase (" + normalCount + "/" + normalThreshold
                    + " normals, sensitivity=" + warmupSensitivity + ")");
            return warmupDetector.detect(vector, historyNormals, warmupSensitivity)
                    .toDetectionResult(resourceId, vector);
        }

        // Active phase — sensitivity passed to external module via BaselineDataProvider
        BaselineStatsDTO stats = baselineProvider.getBaselineStats(resourceId, activeSensitivity);
        if (stats == null) {
            LOG.warning("Resource " + resourceId + ": baseline stats not available, falling back to warmup");
            return warmupDetector.detect(vector, historyNormals, warmupSensitivity)
                    .toDetectionResult(resourceId, vector);
        }

        double[] weights = queryWeights(resourceId);
        LOG.fine("Resource " + resourceId + ": active phase with " + normalCount + " normals");
        return activeDetector.detect(vector, stats, resourceId, weights);
    }

    /**
     * Formats a detailed pre-check signature string from all matched items.
     */
    private static String formatPreCheckSignature(PreCheckResult preCheck) {
        StringBuilder sb = new StringBuilder();
        if (!preCheck.getMatchedExtensions().isEmpty()) {
            String exts = preCheck.getMatchedExtensions().stream()
                    .map(p -> {
                        int dot = p.lastIndexOf('.');
                        return dot >= 0 ? p.substring(dot) : p;
                    })
                    .distinct()
                    .collect(Collectors.joining(", "));
            sb.append("suspicious_extensions: ").append(exts);
        }
        if (!preCheck.getRansomNotePaths().isEmpty()) {
            if (sb.length() > 0) sb.append("; ");
            String notes = preCheck.getRansomNotePaths().stream()
                    .map(p -> {
                        int slash = Math.max(p.lastIndexOf('/'), p.lastIndexOf('\\'));
                        return slash >= 0 ? p.substring(slash + 1) : p;
                    })
                    .distinct()
                    .collect(Collectors.joining(", "));
            sb.append("ransom_notes: ").append(notes);
        }
        return sb.toString();
    }

    // ===== Weight resolution =====

    /**
     * Resolve feature weights — currently returns fallback defaults.
     * TODO: implement database-backed weight query.
     */
    private double[] queryWeights(String resourceId) {
        return FALLBACK_WEIGHTS.clone();
    }
}
