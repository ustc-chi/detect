package com.anomalydetection.detector;

import com.anomalydetection.features.FeatureType;

import java.util.Arrays;

/**
 * Pre-computed baseline statistics (median, MAD, threshold) for anomaly detection.
 * The array length matches {@link FeatureType#COUNT}.
 */
public final class BaselineStatsDTO {

    private final String resourceId;
    private final double[] median;
    private final double[] mad;
    private final double threshold;

    public BaselineStatsDTO(String resourceId, double[] median, double[] mad, double threshold) {
        this.resourceId = resourceId;
        this.median = median.clone();
        this.mad = mad.clone();
        this.threshold = threshold;
    }

    public String getResourceId() { return resourceId; }
    public double[] getMedian() { return median.clone(); }
    public double[] getMad() { return mad.clone(); }
    public double getThreshold() { return threshold; }

    @Override
    public String toString() {
        return "BaselineStatsDTO{resourceId='" + resourceId + "', threshold=" + threshold + "}";
    }
}
