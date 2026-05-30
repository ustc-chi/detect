package com.anomalydetection.detector;

import com.anomalydetection.features.FeatureType;
import com.anomalydetection.features.FeatureVector;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActiveDetectorTest {

    private static FeatureVector createVector(double... values) {
        FeatureVector fv = new FeatureVector();
        for (int i = 0; i < values.length && i < FeatureType.COUNT; i++) {
            fv.set(FeatureType.values()[i], values[i]);
        }
        return fv;
    }

    private BaselineStatsDTO createStats(double[] median, double[] mad, double threshold) {
        return new BaselineStatsDTO("test-resource", median, mad, threshold);
    }

    @Test
    void testScoreComputation() {
        int n = FeatureType.COUNT;
        double[] median = new double[n], mad = new double[n], weights = new double[n];
        for (int i = 0; i < n; i++) { median[i] = 100; mad[i] = 10; weights[i] = 1.0; }
        double[] values = new double[n];
        for (int i = 0; i < n; i++) values[i] = 100;

        DetectionResult result = new ActiveDetector().detect(
                createVector(values), createStats(median, mad, 5.0), "test", weights);
        assertEquals(0.0, result.getScore(), 0.001);
        assertFalse(result.isAnomaly());
    }

    @Test
    void testAnomalyDetection() {
        int n = FeatureType.COUNT;
        double[] median = new double[n], mad = new double[n], weights = new double[n];
        for (int i = 0; i < n; i++) { median[i] = 100; mad[i] = 10; weights[i] = 1.0; }
        double[] values = new double[n];
        for (int i = 0; i < n; i++) values[i] = 150;

        DetectionResult result = new ActiveDetector().detect(
                createVector(values), createStats(median, mad, 4.0), "test", weights);
        assertTrue(result.isAnomaly());
        assertEquals(n, result.getDimensions().size());
    }

    @Test
    void testTopDeviationsByContribution() {
        int n = FeatureType.COUNT;
        double[] median = new double[n], mad = new double[n], weights = new double[n];
        java.util.Arrays.fill(median, 100);
        java.util.Arrays.fill(mad, 10);
        java.util.Arrays.fill(weights, 1.0);
        double[] values = new double[n];
        java.util.Arrays.fill(values, 100);
        values[0] = 200;  // modification_ratio: z=10, contribution=100
        values[1] = 50;   // deletion_ratio: z=-5, contribution=25

        DetectionResult result = new ActiveDetector(0).detect(
                createVector(values), createStats(median, mad, 100), "test", weights);
        assertEquals(FeatureType.values()[0].key(), result.getTopDeviations().get(0).getName());
        assertEquals(FeatureType.values()[1].key(), result.getTopDeviations().get(1).getName());
    }

    @Test
    void testDimensionReportContent() {
        int n = FeatureType.COUNT;
        double[] median = new double[n], mad = new double[n], weights = new double[n];
        java.util.Arrays.fill(median, 100);
        java.util.Arrays.fill(mad, 10);
        java.util.Arrays.fill(weights, 1.0);
        double[] values = new double[n];
        java.util.Arrays.fill(values, 100);

        DetectionResult result = new ActiveDetector(0).detect(
                createVector(values), createStats(median, mad, 5.0), "test", weights);
        assertFalse(result.getDimensions().isEmpty());
        DimensionReport first = result.getDimensions().get(0);
        assertEquals(FeatureType.values()[0].key(), first.getName());
        assertEquals(100.0, first.getValue(), 0.001);
    }
}
