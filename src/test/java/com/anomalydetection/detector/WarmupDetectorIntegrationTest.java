package com.anomalydetection.detector;

import com.anomalydetection.features.RansomwareFeatureVector;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WarmupDetectorIntegrationTest {

    private RansomwareFeatureVector normalVector() {
        return new RansomwareFeatureVector(0.4, 5000.0, 200.0, 0.5, 0.25, 1.0, 0.25, 45.0, 0.3, 0.0, 0.0, 1.4);
    }

    private RansomwareFeatureVector ransomwareVector() {
        return new RansomwareFeatureVector(0.95, 12000.0, 15000.0, 0.95, 0.3, 1.0, 0.3, 50.0, 0.85, 0.6, 0.0, 1.2);
    }

    @Test
    void testColdStartDetection() {
        RansomwareDetector detector = new RansomwareDetector(WeightedEuclideanScorer.DEFAULT_WEIGHTS);
        assertTrue(detector.isInWarmupMode());

        DetectionResult result = detector.detect(ransomwareVector());
        assertTrue(result.isAnomaly());
        assertTrue(result.getScore() >= 2.0);
        assertTrue(detector.isInWarmupMode());
    }

    @Test
    void testWarmupTransitionAtRound5() {
        RansomwareDetector detector = new RansomwareDetector(WeightedEuclideanScorer.DEFAULT_WEIGHTS);
        assertTrue(detector.isInWarmupMode());

        for (int i = 0; i < 5; i++) {
            detector.detect(normalVector());
        }

        assertFalse(detector.isInWarmupMode());
        assertEquals(5, detector.getBaselineCount());

        DetectionResult result = detector.detect(ransomwareVector());
        assertTrue(result.isAnomaly());
    }

    @Test
    void testBaselineExclusionOfAnomalous() {
        RansomwareDetector detector = new RansomwareDetector(WeightedEuclideanScorer.DEFAULT_WEIGHTS);

        detector.detect(normalVector());
        assertEquals(1, detector.getBaselineCount());

        detector.detect(ransomwareVector());
        assertEquals(1, detector.getBaselineCount());

        detector.detect(normalVector());
        assertEquals(2, detector.getBaselineCount());
    }

    @Test
    void testWarmupExceeds10RoundsWarning() {
        RansomwareDetector detector = new RansomwareDetector(WeightedEuclideanScorer.DEFAULT_WEIGHTS);

        for (int i = 0; i < 11; i++) {
            detector.detect(ransomwareVector());
        }

        assertTrue(detector.isInWarmupMode());
        assertEquals(0, detector.getBaselineCount());
    }
}
