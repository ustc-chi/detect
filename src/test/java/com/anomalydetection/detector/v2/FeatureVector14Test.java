package com.anomalydetection.detector.v2;

import com.anomalydetection.detector.v2.heuristic.*;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FeatureVector14 construction, validation, and supplementary data.
 */
class FeatureVector14Test {

    @Test
    void testValidConstruction() {
        double[] values = new double[14];
        for (int i = 0; i < 14; i++) values[i] = i * 1.5;
        FeatureVector14 v = new FeatureVector14(values);
        assertEquals(14, v.toArray().length);
        assertEquals(0.0, v.get(0));
        assertEquals(1.5, v.get(1));
        assertEquals(13 * 1.5, v.get(13));
    }

    @Test
    void testRejectsInvalidLength() {
        assertThrows(IllegalArgumentException.class, () -> new FeatureVector14(new double[13]));
        assertThrows(IllegalArgumentException.class, () -> new FeatureVector14(new double[15]));
    }

    @Test
    void testSupplementaryData() {
        double[] values = new double[14];
        Map<String, Object> supp = Map.of("matched_extensions", java.util.List.of(".locked", ".encrypted"));
        Map<Integer, Map<String, Object>> allSupp = Map.of(5, supp);
        FeatureVector14 v = new FeatureVector14(values, allSupp);

        assertTrue(v.hasSupplementary(5));
        assertEquals(2, ((java.util.List<?>) v.getSupplementary(5).get("matched_extensions")).size());
        assertFalse(v.hasSupplementary(0));
    }

    @Test
    void testNullSupplementaryBecomesEmpty() {
        FeatureVector14 v = new FeatureVector14(new double[14], null);
        assertTrue(v.getSupplementary(0).isEmpty());
    }

    @Test
    void testFeatureMetadata() {
        assertEquals(14, FeatureVector14.FEATURE_NAMES.length);
        assertEquals(14, FeatureVector14.FEATURE_DESCRIPTIONS.length);
        assertEquals(14, FeatureVector14.FEATURE_UNITS.length);
        assertEquals("peak_burst_velocity", FeatureVector14.FEATURE_NAMES[6]);
        assertEquals("ops/hour", FeatureVector14.FEATURE_UNITS[6]);
    }

    @Test
    void testNamedGetters() {
        double[] values = new double[14];
        values[0] = 10000;  // total_operations
        values[1] = 0.85;   // modification_ratio
        values[6] = 5000;   // peak_burst_velocity
        FeatureVector14 v = new FeatureVector14(values);

        assertEquals(10000, v.getTotalOperations());
        assertEquals(0.85, v.getModificationRatio());
        assertEquals(5000, v.getPeakBurstVelocity());
    }
}
