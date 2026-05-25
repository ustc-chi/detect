package com.anomalydetection.detector.v2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

/**
 * Unified entry point for anomaly detection.
 * <p>
 * Detection flow:
 * <ol>
 *   <li><b>Pre-check</b> — Scan the snapdiff JSON file for signature matches
 *       (suspicious extensions, ransom note patterns). If hit, return anomaly immediately.</li>
 *   <li><b>Phase determination</b> — Query the resource's normal history count.
 *       If &lt; NORMAL_THRESHOLD, use WarmupDetector; otherwise use ActiveDetector.</li>
 *   <li><b>Warmup phase</b> — Heuristic rules + dynamic threshold.
 *       Normal results are eligible for baseline accumulation.</li>
 *   <li><b>Active phase</b> — Weighted Euclidean distance with pre-computed
 *       baseline statistics + directional validation for quiet-day reversal.
 *       Weights are queried from a database table.</li>
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
    //
    // 或者使用配置表:
    //   CREATE TABLE resource_config (
    //       resource_id VARCHAR(64) PRIMARY KEY,
    //       weights_json TEXT NOT NULL  -- JSON数组: "[2.0, 2.5, ...]"
    //   );
    // =====================================================================
    private static final double[] FALLBACK_WEIGHTS = {
        2.0, 2.5, 0.5, 2.5, 2.0, 10.0, 5.0, 0.0, 0.0, 1.5, 3.0, 2.0, 0.0, 2.5
    };

    // ===== Constructors =====

    /** No-arg constructor — uses default threshold and creates internal BaselineDataProvider. */
    public AnomalyDetectionService() {
        this(DEFAULT_NORMAL_THRESHOLD);
    }

    /** Constructor with custom threshold. BaselineDataProvider is created internally. */
    public AnomalyDetectionService(int normalThreshold) {
        this.normalThreshold = normalThreshold;
        this.baselineProvider = new ExternalBaselineProvider();
        this.warmupDetector = new WarmupDetector();
        this.activeDetector = new ActiveDetector();
        this.preCheckService = new PreCheckService();
    }

    /**
     * TEST-ONLY: Constructor with custom BaselineDataProvider (for testing/mocking).
     * External callers should use the no-arg constructor.
     */
    AnomalyDetectionService(BaselineDataProvider baselineProvider, int normalThreshold) {
        this.normalThreshold = normalThreshold;
        this.baselineProvider = baselineProvider;
        this.warmupDetector = new WarmupDetector();
        this.activeDetector = new ActiveDetector();
        this.preCheckService = new PreCheckService();
    }

    /**
     * Perform anomaly detection for a resource.
     *
     * @param resourceId the resource identifier
     * @param vector     the feature vector to evaluate
     * @param filePath   path to the original snapdiff JSON file (for pre-check)
     * @return detection result
     */
    public DetectionResult detect(String resourceId, FeatureVector vector, Path filePath) {
        // =====================================================================
        // Step 0: Pre-check — scan file for signature matches
        // =====================================================================
        try {
            PreCheckService.PreCheckResult preCheck = preCheckService.check(filePath);
            if (preCheck.matched()) {
                LOG.warning("Pre-check triggered for resource '" + resourceId + "': " + preCheck.describe());
                return DetectionResult.signatureAnomaly(resourceId, null, preCheck.describe());
            }
        } catch (IOException e) {
            LOG.warning("Pre-check failed for resource '" + resourceId + "': " + e.getMessage()
                    + " — proceeding without pre-check");
        }

        // =====================================================================
        // Step 1: Get historical data from external module
        // =====================================================================
        // TODO: 调用外部模块获取该资源的正常历史特征向量列表
        //
        // 当前通过 BaselineDataProvider 接口获取，外部模块的接口由
        // ExternalBaselineProvider 实现类桥接。
        // =====================================================================
        List<FeatureVector> historyNormals = baselineProvider.getHistoryNormals(resourceId);

        // =====================================================================
        // Step 2: Determine phase and delegate
        // =====================================================================
        if (historyNormals == null || historyNormals.size() < normalThreshold) {
            return detectWarmup(resourceId, vector, historyNormals);
        } else {
            return detectActive(resourceId, vector, historyNormals);
        }
    }

    private DetectionResult detectWarmup(String resourceId, FeatureVector vector,
                                          List<FeatureVector> historyNormals) {
        LOG.fine("Warmup phase for resource '" + resourceId
                + "': " + (historyNormals != null ? historyNormals.size() : 0) + "/" + normalThreshold + " normals");

        List<FeatureVector> normals = historyNormals != null ? historyNormals : List.of();
        WarmupDetector.WarmupDetectionResult wr = warmupDetector.detect(vector, normals);
        return DetectionResult.warmupResult(resourceId, vector, wr);
    }

    private DetectionResult detectActive(String resourceId, FeatureVector vector,
                                          List<FeatureVector> historyNormals) {
        LOG.fine("Active phase for resource '" + resourceId + "': " + historyNormals.size() + " normals");

        // Get baseline stats from external module
        BaselineStatsDTO stats = baselineProvider.getBaselineStats(resourceId);
        if (stats == null) {
            LOG.warning("No baseline stats for resource '" + resourceId
                    + "' in Active phase. Falling back to warmup detection.");
            WarmupDetector.WarmupDetectionResult wr = warmupDetector.detect(vector, historyNormals);
            return DetectionResult.warmupResult(resourceId, vector, wr);
        }

        // =====================================================================
        // TODO: 从数据库查询该资源的权重
        //
        // 当前使用默认权重 FALLBACK_WEIGHTS 占位。
        // 待数据库表结构确定后，替换为:
        //   double[] weights = weightRepository.getWeights(resourceId);
        //
        // 参考 SQL:
        //   SELECT dim_index, weight FROM detection_weights WHERE resource_id = ?
        //   ORDER BY dim_index
        // =====================================================================
        double[] weights = FALLBACK_WEIGHTS;

        return activeDetector.detect(vector, stats, resourceId, weights);
    }
}
