package com.anomalydetection.detector;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SensitivityAdjusterTest {

    private static final double DELTA = 0.001;

    @Test
    void testMaximumSensitivity() {
        // sensitivity=1.0 → multiplier=0.5 (most sensitive, thresholds halved)
        assertEquals(0.5, SensitivityAdjuster.getThresholdMultiplier(1.0), DELTA);
    }

    @Test
    void testDefaultSensitivity() {
        // sensitivity=0.7 → multiplier≈0.95 (preserves current behavior)
        assertEquals(0.95, SensitivityAdjuster.getThresholdMultiplier(0.7), DELTA);
    }

    @Test
    void testMinimumSensitivity() {
        // sensitivity=0.0 → multiplier=2.0 (least sensitive, thresholds doubled)
        assertEquals(2.0, SensitivityAdjuster.getThresholdMultiplier(0.0), DELTA);
    }

    @Test
    void testMidRangeSensitivity() {
        // sensitivity=0.5 → multiplier=2.0-0.5*1.5=1.25
        assertEquals(1.25, SensitivityAdjuster.getThresholdMultiplier(0.5), DELTA);
    }

    @Test
    void testDefaultValue() {
        assertEquals(0.7, SensitivityAdjuster.getDefaultSensitivity(), DELTA);
    }

    @Test
    void testInvalidBelowRange() {
        assertThrows(IllegalArgumentException.class,
                () -> SensitivityAdjuster.getThresholdMultiplier(-0.1));
    }

    @Test
    void testInvalidAboveRange() {
        assertThrows(IllegalArgumentException.class,
                () -> SensitivityAdjuster.getThresholdMultiplier(1.1));
    }

    @Test
    void testBoundaryZero() {
        // sensitivity=0.0 is valid (boundary)
        assertEquals(2.0, SensitivityAdjuster.getThresholdMultiplier(0.0), DELTA);
    }

    @Test
    void testBoundaryOne() {
        // sensitivity=1.0 is valid (boundary)
        assertEquals(0.5, SensitivityAdjuster.getThresholdMultiplier(1.0), DELTA);
    }
}
