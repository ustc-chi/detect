package com.anomalydetection.detector.v2;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AnomalyDetectionService phase routing and flow.
 */
class AnomalyDetectionServiceTest {

    @Test
    void testWarmupPhaseRouting() {
        AnomalyDetectionService service = new AnomalyDetectionService(
                rid -> null,  // data provider (won't be called in warmup)
                null,         // result handler
                5             // threshold = 5
        );

        FeatureVector14 vector = new FeatureVector14(new double[14]);
        DetectionResult result = service.detect("res-1", vector, java.util.List.of());

        assertEquals(Phase.WARMUP, result.getPhase());
        assertNotNull(result.getWarmupInfo());
        assertEquals("res-1", result.getResourceId());
    }

    @Test
    void testActivePhaseRouting() {
        AnomalyDetectionService service = new AnomalyDetectionService(
                rid -> {
                    // Return minimal baseline stats
                    return new BaselineStatsDTO(rid,
                            new double[14], new double[14], 10.0, new double[14]);
                },
                null,
                3  // threshold = 3, so 3 normals → active
        );

        FeatureVector14 vector = new FeatureVector14(new double[14]);
        java.util.List<FeatureVector14> normals = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            normals.add(new FeatureVector14(new double[14]));
        }

        DetectionResult result = service.detect("res-2", vector, normals);
        assertEquals(Phase.ACTIVE, result.getPhase());
        assertNull(result.getWarmupInfo());
    }

    @Test
    void testResultHandlerInvoked() {
        final boolean[] handlerCalled = {false};
        AnomalyDetectionService service = new AnomalyDetectionService(
                rid -> null,
                result -> handlerCalled[0] = true,
                5
        );

        service.detect("res-3", new FeatureVector14(new double[14]), java.util.List.of());
        assertTrue(handlerCalled[0]);
    }

    @Test
    void testActivePhaseFallbackWhenNoData() {
        // Data provider returns null, should fall back to warmup
        AnomalyDetectionService service = new AnomalyDetectionService(
                rid -> null,  // returns null!
                null,
                2  // 2 normals → should be active, but no data
        );

        FeatureVector14 vector = new FeatureVector14(new double[14]);
        java.util.List<FeatureVector14> normals = java.util.List.of(
                new FeatureVector14(new double[14]),
                new FeatureVector14(new double[14])
        );

        // Should not throw — falls back to warmup detection
        DetectionResult result = service.detect("res-4", vector, normals);
        assertNotNull(result);
        // Falls back to warmup
        assertEquals(Phase.WARMUP, result.getPhase());
    }
}
