package com.anomalydetection.detector.v2;

import java.util.Arrays;

/**
 * DTO for receiving pre-computed baseline statistics from an external API.
 * <p>
 * Contains median, MAD, threshold, and weights — all pre-computed by an
 * external module. All arrays are length 14.
 */
public final class BaselineStatsDTO {

    private final String resourceId;
    private final double[] median;
    private final double[] mad;
    private final double threshold;
    private final double[] weights;

    public BaselineStatsDTO(String resourceId,
                            double[] median,
                            double[] mad,
                            double threshold,
                            double[] weights) {
        this.resourceId = resourceId;
        this.median = median.clone();
        this.mad = mad.clone();
        this.threshold = threshold;
        this.weights = weights.clone();
    }

    public String getResourceId() { return resourceId; }
    public double[] getMedian() { return median.clone(); }
    public double[] getMad() { return mad.clone(); }
    public double getThreshold() { return threshold; }
    public double[] getWeights() { return weights.clone(); }

    @Override
    public String toString() {
        return "BaselineStatsDTO{resourceId='" + resourceId
                + "', threshold=" + threshold
                + ", median=" + Arrays.toString(median)
                + ", mad=" + Arrays.toString(mad)
                + ", weights=" + Arrays.toString(weights)
                + "}";
    }
}
