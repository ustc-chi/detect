package com.anomalydetection.detector.v2;

import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Unified entry point for anomaly detection.
 * <p>
 * Automatically routes to either {@link WarmupDetector} or {@link ActiveDetector}
 * based on the number of accumulated normal feature vectors for the given resource.
 * <p>
 * <b>Warmup phase</b>: normalCount &lt; NORMAL_THRESHOLD (default 10).
 * Uses heuristic rules + dynamic threshold.
 * <p>
 * <b>Active phase</b>: normalCount &gt;= NORMAL_THRESHOLD.
 * Uses weighted Euclidean distance with pre-computed baseline statistics.
 */
public class AnomalyDetectionService {

    private static final Logger LOG = Logger.getLogger(AnomalyDetectionService.class.getName());

    /** Default number of normal vectors needed to transition to Active phase. */
    public static final int DEFAULT_NORMAL_THRESHOLD = 10;

    private final int normalThreshold;
    private final WarmupDetector warmupDetector;
    private final ActiveDetector activeDetector;

    /**
     * Provider interface for external data sources.
     * Implementations fetch baseline stats from the external data layer.
     */
    @FunctionalInterface
    public interface BaselineDataProvider {
        /**
         * Retrieve pre-computed baseline statistics for a resource.
         *
         * @param resourceId the resource identifier
         * @return baseline stats DTO, or null if not available
         */
        BaselineStatsDTO getBaselineStats(String resourceId);
    }

    /**
     * Callback interface for persisting detection results.
     */
    @FunctionalInterface
    public interface ResultHandler {
        /**
         * Handle a completed detection result (e.g., persist to database).
         *
         * @param result the detection result
         */
        void handle(DetectionResult result);
    }

    private final BaselineDataProvider dataProvider;
    private final ResultHandler resultHandler;

    public AnomalyDetectionService(BaselineDataProvider dataProvider,
                                    ResultHandler resultHandler) {
        this(dataProvider, resultHandler, DEFAULT_NORMAL_THRESHOLD);
    }

    public AnomalyDetectionService(BaselineDataProvider dataProvider,
                                    ResultHandler resultHandler,
                                    int normalThreshold) {
        this.dataProvider = Objects.requireNonNull(dataProvider, "dataProvider must not be null");
        this.resultHandler = resultHandler;
        this.normalThreshold = normalThreshold;
        this.warmupDetector = new WarmupDetector();
        this.activeDetector = new ActiveDetector();
    }

    /**
     * Perform anomaly detection for a resource.
     *
     * @param resourceId       the resource identifier
     * @param vector           the 14-dim feature vector to evaluate
     * @param historyNormals   all previously accumulated normal vectors for this resource
     * @return detection result with full dimension reports
     */
    public DetectionResult detect(String resourceId,
                                   FeatureVector14 vector,
                                   List<FeatureVector14> historyNormals) {
        Objects.requireNonNull(resourceId, "resourceId must not be null");
        Objects.requireNonNull(vector, "vector must not be null");

        List<FeatureVector14> normals = historyNormals != null
                ? List.copyOf(historyNormals) : List.of();

        DetectionResult result;

        if (normals.size() < normalThreshold) {
            // ===== WARMUP phase =====
            LOG.fine("Warmup phase for resource '" + resourceId
                    + "': " + normals.size() + "/" + normalThreshold + " normals");

            WarmupDetector.WarmupDetectionResult warmupResult =
                    warmupDetector.detect(vector, normals);

            WarmupInfo warmupInfo = new WarmupInfo(
                    warmupResult.getTriggeredRules().size(),
                    warmupResult.getTriggeredRules(),
                    warmupResult.getConfidence(),
                    warmupResult.isAddToBaseline()
            );

            double score = warmupResult.getStatus() == WarmupStatus.NORMAL ? 0 : 1;
            double threshold = 0;
            boolean isAnomaly = warmupResult.getStatus() == WarmupStatus.ANOMALY;

            result = DetectionResult.warmupResult(
                    resourceId, vector, score, threshold, isAnomaly,
                    null, warmupInfo
            );
        } else {
            // ===== ACTIVE phase =====
            LOG.fine("Active phase for resource '" + resourceId
                    + "': " + normals.size() + " normals available");

            BaselineStatsDTO stats = dataProvider.getBaselineStats(resourceId);
            if (stats == null) {
                LOG.severe("No baseline stats available for resource '" + resourceId
                        + "' in Active phase. Falling back to warmup detection.");
                // Fallback: use warmup detection
                WarmupDetector.WarmupDetectionResult warmupResult =
                        warmupDetector.detect(vector, normals);
                WarmupInfo warmupInfo = new WarmupInfo(
                        warmupResult.getTriggeredRules().size(),
                        warmupResult.getTriggeredRules(),
                        warmupResult.getConfidence(),
                        warmupResult.isAddToBaseline()
                );
                result = DetectionResult.warmupResult(
                        resourceId, vector,
                        warmupResult.getStatus() == WarmupStatus.NORMAL ? 0 : 1,
                        0,
                        warmupResult.getStatus() == WarmupStatus.ANOMALY,
                        null, warmupInfo
                );
            } else {
                result = activeDetector.detect(vector, stats, resourceId);
            }
        }

        // Persist result via callback (non-blocking for caller)
        if (resultHandler != null) {
            try {
                resultHandler.handle(result);
            } catch (Exception e) {
                LOG.warning("ResultHandler failed for resource '" + resourceId
                        + "': " + e.getMessage());
            }
        }

        return result;
    }
}
