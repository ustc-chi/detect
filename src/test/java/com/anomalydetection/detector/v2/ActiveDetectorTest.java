package com.anomalydetection.detector.v2;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActiveDetectorTest {

    private BaselineStatsDTO createStats(double[] median, double[] mad, double threshold) {
        return new BaselineStatsDTO("test-resource", median, mad, threshold);
    }

    @Test
    void testScoreComputation() {
        double[] median = new double[14], mad = new double[14], weights = new double[14];
        for (int i = 0; i < 14; i++) { median[i] = 100; mad[i] = 10; weights[i] = 1.0; }
        double[] values = new double[14];
        for (int i = 0; i < 14; i++) values[i] = 100;

        DetectionResult result = new ActiveDetector().detect(
                new FeatureVector(values), createStats(median, mad, 5.0), "test", weights);
        assertEquals(0.0, result.getScore(), 0.001);
        assertFalse(result.isAnomaly());
    }

    @Test
    void testAnomalyDetection() {
        double[] median = new double[14], mad = new double[14], weights = new double[14];
        for (int i = 0; i < 14; i++) { median[i] = 100; mad[i] = 10; weights[i] = 1.0; }
        double[] values = new double[14];
        for (int i = 0; i < 14; i++) values[i] = 150;

        DetectionResult result = new ActiveDetector().detect(
                new FeatureVector(values), createStats(median, mad, 4.0), "test", weights);
        assertTrue(result.isAnomaly());
        assertEquals(14, result.getDimensions().size());
    }

    @Test
    void testTopDeviationsByContribution() {
        // Verify top deviations are sorted by contribution, not zScore
        double[] median = new double[14], mad = new double[14], weights = new double[14];
        java.util.Arrays.fill(median, 100);
        java.util.Arrays.fill(mad, 10);
        java.util.Arrays.fill(weights, 1.0);
        double[] values = new double[14];
        java.util.Arrays.fill(values, 100);
        values[0] = 200;  // z=10, contribution=100
        values[1] = 50;   // z=-5, contribution=25

        DetectionResult result = new ActiveDetector(0).detect(
                new FeatureVector(values), createStats(median, mad, 100), "test", weights);
        // Top deviation should be index 0 (largest contribution)
        assertEquals("total_operations", result.getTopDeviations().get(0).getName());
        // Second should be index 1
        assertEquals("modification_ratio", result.getTopDeviations().get(1).getName());
    }

    @Test
    void testDimensionReportContent() {
        double[] median = new double[]{100, 0.5, 1, 30, 15, 0, 2000, 8, 2, 0.25, 0.7, 0.3, 0.5, 0.9};
        double[] mad = new double[]{5000, 0.1, 0.5, 10, 5, 0.01, 1000, 1, 0.5, 0.05, 0.1, 0.1, 0.3, 0.2};
        double[] weights = new double[]{2, 2, 1, 1, 1, 10, 3, 0, 0, 1, 3, 2, 0, 2};
        double[] values = new double[]{8000, 0.5, 1.5, 25, 12, 0, 1500, 8, 2, 0.3, 0.65, 0.35, 0.4, 0.85};

        DetectionResult result = new ActiveDetector(0).detect(
                new FeatureVector(values), createStats(median, mad, 5.0), "test", weights);
        DimensionReport dr = result.getDimensions().get(0);
        assertEquals(0, dr.getIndex());
        assertEquals(FeatureVector.FEATURE_NAMES[0], dr.getName());
        assertTrue(dr.getDescription().length() > 0);
    }
}
