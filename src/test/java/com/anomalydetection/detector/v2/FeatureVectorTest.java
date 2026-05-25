package com.anomalydetection.detector.v2;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class FeatureVectorTest {

    @Test
    void testValidConstruction() {
        double[] values = new double[14];
        for (int i = 0; i < 14; i++) values[i] = i * 1.5;
        FeatureVector v = new FeatureVector(values);
        assertEquals(14, v.toArray().length);
        assertEquals(0.0, v.get(0));
        assertEquals(1.5, v.get(1));
        assertEquals(13 * 1.5, v.get(13));
    }

    @Test
    void testRejectsInvalidLength() {
        assertThrows(IllegalArgumentException.class, () -> new FeatureVector(new double[13]));
        assertThrows(IllegalArgumentException.class, () -> new FeatureVector(new double[15]));
    }

    @Test
    void testRejectsOutOfRangeIndex() {
        FeatureVector v = new FeatureVector(new double[14]);
        assertThrows(IllegalArgumentException.class, () -> v.get(-1));
        assertThrows(IllegalArgumentException.class, () -> v.get(14));
    }

    @Test
    void testSupplementaryData() {
        double[] values = new double[14];
        Map<String, Object> supp = Map.of("matched_extensions", java.util.List.of(".locked", ".encrypted"));
        FeatureVector v = new FeatureVector(values, Map.of(5, supp));
        assertTrue(v.hasSupplementary(5));
        assertFalse(v.hasSupplementary(0));
    }

    @Test
    void testNullSupplementaryBecomesEmpty() {
        FeatureVector v = new FeatureVector(new double[14], null);
        assertTrue(v.getSupplementary(0).isEmpty());
    }

    @Test
    void testFeatureMetadata() {
        assertEquals(14, FeatureVector.FEATURE_NAMES.length);
        assertEquals(14, FeatureVector.FEATURE_DESCRIPTIONS.length);
        assertEquals(14, FeatureVector.FEATURE_UNITS.length);
        assertEquals("peak_burst_velocity", FeatureVector.FEATURE_NAMES[6]);
    }

    @Test
    void testNamedGetters() {
        double[] values = new double[14];
        values[0] = 10000;
        values[1] = 0.85;
        values[6] = 5000;
        FeatureVector v = new FeatureVector(values);
        assertEquals(10000, v.getTotalOperations());
        assertEquals(0.85, v.getModificationRatio());
        assertEquals(5000, v.getPeakBurstVelocity());
    }
}
