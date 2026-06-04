package com.anomalydetection.detector;

import com.anomalydetection.features.FeatureVector;

import java.util.List;

/**
 * Interface for external data sources (history vectors, baseline statistics).
 * Implement this to integrate with an external history module.
 */
public interface BaselineDataProvider {

    List<FeatureVector> getHistoryNormals(String resourceId);

    List<FeatureVector> getHistoryAnomalies(String resourceId);

    BaselineStatsDTO getBaselineStats(String resourceId);

    /**
     * Returns baseline statistics (median, MAD, threshold) for the given resource,
     * with the given detection sensitivity. Implementations SHOULD pass the
     * sensitivity to the external module API so that an adjusted threshold is returned.
     *
     * @param resourceId  the resource identifier
     * @param sensitivity detection sensitivity in [1, 10], where 10 is most sensitive
     * @return baseline statistics, or null if not available
     */
    BaselineStatsDTO getBaselineStats(String resourceId, int sensitivity);
}
