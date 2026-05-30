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
}
