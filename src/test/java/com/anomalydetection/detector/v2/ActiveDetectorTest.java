package com.anomalydetection.detector.v2;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ActiveDetector scoring and ActiveDetector + DirectionalValidatorV2.
 */
class ActiveDetectorTest {

    private BaselineStatsDTO createBaselineStats(double[] median, double[] mad,
                                                  double threshold, double[] weights) {
        return new BaselineStatsDTO("test-resource", median, mad, threshold, weights);
    }

    @Test
    void testScoreComputation() {
        // All values exactly at median → score ≈ 0
        double[] median = new double[14];
        double[] mad = new double[14];
        double[] weights = new double[14];
        for (int i = 0; i < 14; i++) {
            median[i] = 100;
            mad[i] = 10;
            weights[i] = 1.0;
        }
        double[] values = new double[14];
        for (int i = 0; i < 14; i++) values[i] = 100; // exactly at median

        BaselineStatsDTO stats = createBaselineStats(median, mad, 5.0, weights);
        FeatureVector14 vector = new FeatureVector14(values);

        // Need to test via detection path
        ActiveDetector detector = new ActiveDetector();
        DetectionResult result = detector.detect(vector, stats, "test-resource");

        assertEquals(0.0, result.getScore(), 0.001);
        assertFalse(result.isAnomaly());
    }

    @Test
    void testAnomalyDetection() {
        double[] median = new double[14];
        Arrays.fill(median, 100);
        double[] mad = new double[14];
        Arrays.fill(mad, 10);
        double[] weights = new double[14];
        Arrays.fill(weights, 1.0);

        // All dimensions deviate significantly
        double[] values = new double[14];
        for (int i = 0; i < 14; i++) values[i] = 100 + 5 * 10; // 5 MAD above median

        BaselineStatsDTO stats = createBaselineStats(median, mad, 4.0, weights);
        FeatureVector14 vector = new FeatureVector14(values);
        ActiveDetector detector = new ActiveDetector();
        DetectionResult result = detector.detect(vector, stats, "test-resource");

        assertTrue(result.isAnomaly());
        assertTrue(result.getScore() > 4.0);
        assertEquals(14, result.getDimensions().size());
    }

    @Test
    void testTopDeviations() {
        double[] median = new double[14];
        Arrays.fill(median, 100);
        double[] mad = new double[14];
        Arrays.fill(mad, 10);
        double[] weights = new double[14];
        Arrays.fill(weights, 1.0);

        double[] values = new double[14];
        for (int i = 0; i < 14; i++) values[i] = 100;
        values[0] = 200;  // big deviation at index 0
        values[1] = 50;   // big deviation at index 1

        BaselineStatsDTO stats = createBaselineStats(median, mad, 10.0, weights);
        FeatureVector14 vector = new FeatureVector14(values);
        ActiveDetector detector = new ActiveDetector(0); // disable direction reversal
        DetectionResult result = detector.detect(vector, stats, "test-resource");

        assertEquals(5, result.getTopDeviations().size());
        // Top 2 deviations should be index 0 and 1
        String topName = result.getTopDeviations().get(0).getName();
        assertTrue(topName.equals("total_operations") || topName.equals("modification_ratio"));
    }

    @Test
    void testDimensionReportContent() {
        double[] median = new double[]{100, 0.5, 1, 30, 15, 0, 2000, 8, 2, 0.25, 0.7, 0.3, 0.5, 0.9};
        double[] mad = new double[]{5000, 0.1, 0.5, 10, 5, 0.01, 1000, 1, 0.5, 0.05, 0.1, 0.1, 0.3, 0.2};
        double[] weights = new double[]{2, 2, 1, 1, 1, 10, 3, 0, 0, 1, 3, 2, 0, 2};
        double[] values = new double[]{8000, 0.5, 1.5, 25, 12, 0, 1500, 8, 2, 0.3, 0.65, 0.35, 0.4, 0.85};

        BaselineStatsDTO stats = createBaselineStats(median, mad, 5.0, weights);
        FeatureVector14 vector = new FeatureVector14(values);
        ActiveDetector detector = new ActiveDetector(0);
        DetectionResult result = detector.detect(vector, stats, "test-resource");

        DimensionReport dr = result.getDimensions().get(0);
        assertEquals(0, dr.getIndex());
        assertEquals(FeatureVector14.FEATURE_NAMES[0], dr.getName());
        assertTrue(dr.getDescription().length() > 0);
        assertTrue(dr.getUnit().length() > 0);
    }

    // Helper
    static class Arrays {
        static void fill(double[] a, double val) {
            for (int i = 0; i < a.length; i++) a[i] = val;
        }
    }
}
