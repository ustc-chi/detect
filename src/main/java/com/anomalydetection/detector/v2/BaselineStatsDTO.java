package com.anomalydetection.detector.v2;

import java.util.Arrays;

/**
 * DTO for receiving pre-computed baseline statistics from an external API.
 * Contains median, MAD, and threshold (NOT weights — weights come from DB).
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
