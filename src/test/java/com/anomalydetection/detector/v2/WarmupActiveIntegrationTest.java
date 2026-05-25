package com.anomalydetection.detector.v2;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WarmupActiveIntegrationTest {

    @Test
    void testFullWarmupToActiveTransition() {
        // Provider that returns growing history
        BaselineDataProvider provider = new BaselineDataProvider() {
            private int callCount = 0;
            public java.util.List<FeatureVector> getHistoryNormals(String r) {
                callCount++;
                java.util.List<FeatureVector> list = new java.util.ArrayList<>();
                // Return increasing number of normals
                for (int i = 0; i < Math.min(callCount, 6); i++) {
                    list.add(new FeatureVector(new double[14]));
                }
                return list;
            }
            public java.util.List<FeatureVector> getHistoryAnomalies(String r) { return java.util.List.of(); }
            public BaselineStatsDTO getBaselineStats(String r) {
                return new BaselineStatsDTO(r, new double[14], new double[14], 10.0);
            }
        };

        AnomalyDetectionService service = new AnomalyDetectionService(provider, 5);
        FeatureVector vec = new FeatureVector(new double[14]);

        // Warmup phase (first 4 calls, provider returns 1-4 normals)
        for (int i = 0; i < 4; i++) {
            DetectionResult r = service.detect("integ-res", vec, java.nio.file.Paths.get("nonexistent.json"));
            if (i < 4) assertEquals(Phase.WARMUP, r.getPhase(), "Round " + (i+1) + " should be WARMUP");
        }

        // 5th call — provider returns 5 normals → ACTIVE
        DetectionResult r5 = service.detect("integ-res", vec, java.nio.file.Paths.get("nonexistent.json"));
        assertEquals(Phase.ACTIVE, r5.getPhase());
    }
}
