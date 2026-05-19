package com.anomalydetection.detector;

import com.anomalydetection.features.RansomwareFeatureVector;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WarmupDetectorTest {

    private final WarmupDetector detector = new WarmupDetector();

    private RansomwareFeatureVector normalVector() {
        return new RansomwareFeatureVector(0.4, 5000.0, 200.0, 0.5, 0.25, 1.0, 0.25, 45.0, 0.3, 0.0, 0.0, 1.4);
    }

    private RansomwareFeatureVector ransomwareVector() {
        return new RansomwareFeatureVector(0.95, 12000.0, 15000.0, 0.95, 0.3, 1.0, 0.3, 50.0, 0.85, 0.6, 0.0, 1.2);
    }

    @Test
    void testRansomwareLikeVector() {
        assertTrue(detector.isAnomalous(ransomwareVector()));
        assertTrue(detector.classify(ransomwareVector()) >= 2);
    }

    @Test
    void testNormalVector() {
        assertFalse(detector.isAnomalous(normalVector()));
    }

    @Test
    void testSingleRuleMatch() {
        RansomwareFeatureVector vector = new RansomwareFeatureVector(
            0.90, 5000.0, 200.0, 0.5, 0.25, 1.0, 0.25, 45.0, 0.3, 0.0, 0.0, 1.4);
        assertFalse(detector.isAnomalous(vector));
        assertEquals(1, detector.classify(vector));
    }

    @Test
    void testThresholdBoundary() {
        RansomwareFeatureVector vector = new RansomwareFeatureVector(
            0.85, 5000.0, 200.0, 0.5, 0.25, 1.0, 0.25, 45.0, 0.3, 0.0, 0.0, 1.4);
        assertFalse(detector.isAnomalous(vector));
        assertEquals(0, detector.classify(vector));
    }

    @Test
    void testAllFiveRulesMatching() {
        RansomwareFeatureVector vector = new RansomwareFeatureVector(
            0.95, 12000.0, 15000.0, 0.95, 0.3, 1.0, 0.3, 50.0, 0.85, 0.6, 0.0, 1.2);
        assertTrue(detector.isAnomalous(vector));
        assertEquals(5, detector.classify(vector));
    }

    @Test
    void testExactlyTwoRulesMatching() {
        RansomwareFeatureVector vector = new RansomwareFeatureVector(
            0.90, 5000.0, 200.0, 0.5, 0.25, 1.0, 0.25, 45.0, 0.75, 0.0, 0.0, 1.4);
        assertTrue(detector.isAnomalous(vector));
        assertEquals(2, detector.classify(vector));
    }
}
