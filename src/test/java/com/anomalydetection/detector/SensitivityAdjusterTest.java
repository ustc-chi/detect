package com.anomalydetection.detector;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SensitivityAdjusterTest {

    private static final double DELTA = 0.001;

    @Test
    void testInt1_minimumSensitivity() {
        assertEquals(2.0, SensitivityAdjuster.getThresholdMultiplier(1), DELTA);
    }

    @Test
    void testInt5_defaultMediumSensitivity() {
        assertEquals(0.95, SensitivityAdjuster.getThresholdMultiplier(5), DELTA);
    }

    @Test
    void testInt10_maximumSensitivity() {
        assertEquals(0.5, SensitivityAdjuster.getThresholdMultiplier(10), DELTA);
    }

    @Test
    void testInt2() {
        assertEquals(1.7375, SensitivityAdjuster.getThresholdMultiplier(2), DELTA);
    }

    @Test
    void testInt3() {
        assertEquals(1.475, SensitivityAdjuster.getThresholdMultiplier(3), DELTA);
    }

    @Test
    void testInt4() {
        assertEquals(1.2125, SensitivityAdjuster.getThresholdMultiplier(4), DELTA);
    }

    @Test
    void testInt6() {
        assertEquals(0.86, SensitivityAdjuster.getThresholdMultiplier(6), DELTA);
    }

    @Test
    void testInt7() {
        assertEquals(0.77, SensitivityAdjuster.getThresholdMultiplier(7), DELTA);
    }

    @Test
    void testInt8() {
        assertEquals(0.68, SensitivityAdjuster.getThresholdMultiplier(8), DELTA);
    }

    @Test
    void testInt9() {
        assertEquals(0.59, SensitivityAdjuster.getThresholdMultiplier(9), DELTA);
    }

    @Test
    void testDefaultValue() {
        assertEquals(5, SensitivityAdjuster.getDefaultSensitivity());
    }

    @Test
    void testDefaultConstant() {
        assertEquals(5, SensitivityAdjuster.DEFAULT_SENSITIVITY);
    }

    @Test
    void testInvalidBelowRange_zero() {
        assertThrows(IllegalArgumentException.class,
                () -> SensitivityAdjuster.getThresholdMultiplier(0));
    }

    @Test
    void testInvalidBelowRange_negative() {
        assertThrows(IllegalArgumentException.class,
                () -> SensitivityAdjuster.getThresholdMultiplier(-5));
    }

    @Test
    void testInvalidAboveRange_11() {
        assertThrows(IllegalArgumentException.class,
                () -> SensitivityAdjuster.getThresholdMultiplier(11));
    }

    @Test
    void testInvalidAboveRange_100() {
        assertThrows(IllegalArgumentException.class,
                () -> SensitivityAdjuster.getThresholdMultiplier(100));
    }

    @Test
    void testBoundaryInt1() {
        assertEquals(2.0, SensitivityAdjuster.getThresholdMultiplier(1), DELTA);
    }

    @Test
    void testBoundaryInt10() {
        assertEquals(0.5, SensitivityAdjuster.getThresholdMultiplier(10), DELTA);
    }

    @Test
    void testMonotonicIncreasingSensitivity() {
        // Higher sensitivity should always give lower (more sensitive) multiplier
        for (int s = 1; s < 10; s++) {
            assertTrue(SensitivityAdjuster.getThresholdMultiplier(s) >
                       SensitivityAdjuster.getThresholdMultiplier(s + 1),
                    "multiplier should strictly decrease as sensitivity increases at s=" + s);
        }
    }
}
