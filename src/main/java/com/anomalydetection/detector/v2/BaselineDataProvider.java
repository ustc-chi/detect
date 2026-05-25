package com.anomalydetection.detector.v2;

import java.util.List;

/**
 * Interface for external data sources (history vectors, baseline statistics).
 * <p>
 * Implement this interface to integrate with the actual external module
 * that provides historical detection data and pre-computed baseline stats.
 *
 * <pre>
 * // Example usage:
 * BaselineDataProvider provider = new ExternalBaselineProvider();
 * AnomalyDetectionService service = new AnomalyDetectionService(provider);
 * </pre>
 */
public interface BaselineDataProvider {

    /**
     * Get all normal (non-anomalous) history feature vectors for a resource.
     *
     * @param resourceId the resource identifier
     * @return list of normal feature vectors, never null (empty if none)
     */
    List<FeatureVector> getHistoryNormals(String resourceId);

    /**
     * Get all anomaly history feature vectors for a resource.
     *
     * @param resourceId the resource identifier
     * @return list of anomaly feature vectors, never null (empty if none)
     */
    List<FeatureVector> getHistoryAnomalies(String resourceId);

    /**
     * Get pre-computed baseline statistics (median, MAD, threshold) for a resource.
     *
     * @param resourceId the resource identifier
     * @return baseline statistics DTO, or null if not available
     */
    BaselineStatsDTO getBaselineStats(String resourceId);
}
