package com.anomalydetection.detector;

import com.anomalydetection.features.FeatureVector;

import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * Default placeholder implementation of {@link BaselineDataProvider}.
 * Returns empty data — the service will always run in warmup mode.
 * Replace with your actual data source implementation.
 */
public class ExternalBaselineProvider implements BaselineDataProvider {

    private static final Logger LOG = Logger.getLogger(ExternalBaselineProvider.class.getName());

    @Override
    public List<FeatureVector> getHistoryNormals(String resourceId) {
        LOG.warning("[NOT YET IMPLEMENTED] getHistoryNormals(" + resourceId
                + ") — returning empty list.");
        return Collections.emptyList();
    }

    @Override
    public List<FeatureVector> getHistoryAnomalies(String resourceId) {
        LOG.warning("[NOT YET IMPLEMENTED] getHistoryAnomalies(" + resourceId
                + ") — returning empty list.");
        return Collections.emptyList();
    }

    @Override
    public BaselineStatsDTO getBaselineStats(String resourceId) {
        LOG.warning("[NOT YET IMPLEMENTED] getBaselineStats(" + resourceId
                + ") — returning null.");
        return null;
    }

    @Override
    public BaselineStatsDTO getBaselineStats(String resourceId, int sensitivity) {
        LOG.warning("[NOT YET IMPLEMENTED] getBaselineStats(" + resourceId
                + ", sensitivity=" + sensitivity + ") — returning null.");
        return null;
    }
}
