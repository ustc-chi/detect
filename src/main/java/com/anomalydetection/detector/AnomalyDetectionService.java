package com.anomalydetection.detector;

import com.anomalydetection.features.FeatureType;
import com.anomalydetection.features.FeatureVector;
import com.anomalydetection.precheck.PreCheckService;
import com.anomalydetection.precheck.PreCheckResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

/**
 * Unified entry point for anomaly detection.
 * <p>
 * Detection flow:
 * <ol>
 *   <li><b>Pre-check</b> — Scan the snapdiff JSON file for signature matches.
 *       If hit, return anomaly immediately.</li>
 *   <li><b>Phase determination</b> — Query the resource's normal history count.
 *       If &lt; NORMAL_THRESHOLD, use WarmupDetector; otherwise use ActiveDetector.</li>
 *   <li><b>Warmup phase</b> — Heuristic rules + dynamic threshold.</li>
 *   <li><b>Active phase</b> — Weighted Euclidean distance with pre-computed
 *       baseline statistics + directional validation.</li>
 * </ol>
 */
public class AnomalyDetectionService {

    private static final Logger LOG = Logger.getLogger(AnomalyDetectionService.class.getName());

    /** Default number of normal vectors needed to transition to Active phase. */
    public static final int DEFAULT_NORMAL_THRESHOLD = 10;

    private final int normalThreshold;
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
    private static final double[] FALLBACK_WEIGHTS = {
        2.0,   // F0  modification_ratio
        0.5,   // F1  deletion_ratio
        0.5,   // F2  creation_ratio
        2.5,   // F3  total_operations_normalized
        2.0,   // F4  peak_burst_velocity
        3.0,   // F5  burst_mod_purity
        10.0,  // F6  high_value_ext_ratio
        5.0,   // F7  inter_op_time_cv_burst
        0.0,   // F8  directory_coverage_depth
        0.0,   // F9  temporal_uniformity
        0.0,   // F10 rename_correlation
        0.0,   // F11 hourly_concentration
        0.0,   // F12 hourly_entropy
        2.0,   // F13 per_type_entropy
        0.0,   // F14 extension_count_cv
        2.0    // F15 created_ext_novelty
    };

    // ===== Constructors =====

    public AnomalyDetectionService() {
        this(DEFAULT_NORMAL_THRESHOLD);
    }

    public AnomalyDetectionService(int normalThreshold) {
        this(normalThreshold, new ExternalBaselineProvider());
    }

    public AnomalyDetectionService(int normalThreshold, BaselineDataProvider baselineProvider) {
        this.normalThreshold = normalThreshold;
        this.warmupDetector = new WarmupDetector();
        this.activeDetector = new ActiveDetector();
        this.baselineProvider = baselineProvider;
        this.preCheckService = new PreCheckService();
    }

    // ===== Public API =====

    /**
     * Run the full detection pipeline on a snapdiff file with its feature vector.
     *
     * @param snapdiffFile path to the raw snapdiff JSON file (for PreCheck)
     * @param vector       feature vector from feature-extractor
     * @param resourceId   resource identifier for baseline lookup
     * @return detection result
     * @throws IOException if the snapdiff file cannot be read
     */
    public DetectionResult detect(Path snapdiffFile, FeatureVector vector, String resourceId) throws IOException {
        // Step 1: Pre-check — quick signature scan
        PreCheckResult preCheck = preCheckService.check(snapdiffFile);
        if (preCheck.isMatch()) {
            String signature = preCheck.getMatchedExtensions().isEmpty()
                    ? preCheck.getRansomNotePaths().get(0)
                    : preCheck.getMatchedExtensions().get(0);
            LOG.info("Resource " + resourceId + ": pre-check hit — " + signature);
            return DetectionResult.signatureMatchResult(resourceId, signature);
        }

        // Step 2: Phase determination
        List<FeatureVector> historyNormals = baselineProvider.getHistoryNormals(resourceId);
        int normalCount = historyNormals != null ? historyNormals.size() : 0;

        if (normalCount < normalThreshold) {
            // Warmup phase
            LOG.fine("Resource " + resourceId + ": warmup phase (" + normalCount + "/" + normalThreshold + " normals)");
            return warmupDetector.detect(vector, historyNormals).toDetectionResult(resourceId, vector);
        }

        // Active phase
        BaselineStatsDTO stats = baselineProvider.getBaselineStats(resourceId);
        if (stats == null) {
            LOG.warning("Resource " + resourceId + ": baseline stats not available, falling back to warmup");
            return warmupDetector.detect(vector, historyNormals).toDetectionResult(resourceId, vector);
        }

        double[] weights = queryWeights(resourceId);
        LOG.fine("Resource " + resourceId + ": active phase with " + normalCount + " normals");
        return activeDetector.detect(vector, stats, resourceId, weights);
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
