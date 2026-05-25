package com.anomalydetection.detector.v2;

import org.junit.jupiter.api.Test;
import java.nio.file.Paths;
import static org.junit.jupiter.api.Assertions.*;

class AnomalyDetectionServiceTest {

    @Test
    void testWarmupPhaseRouting() {
        BaselineDataProvider provider = new BaselineDataProvider() {
            public java.util.List<FeatureVector> getHistoryNormals(String r) { return java.util.List.of(); }
            public java.util.List<FeatureVector> getHistoryAnomalies(String r) { return java.util.List.of(); }
            public BaselineStatsDTO getBaselineStats(String r) { return null; }
        };
        AnomalyDetectionService service = new AnomalyDetectionService(provider, 5);

        // detect with a non-existent file path — pre-check will fail but proceed
        FeatureVector vector = new FeatureVector(new double[14]);
        DetectionResult result = service.detect("res-1", vector, Paths.get("nonexistent.json"));

        assertEquals(Phase.WARMUP, result.getPhase());
        assertNotNull(result.getWarmupInfo());
        assertEquals("res-1", result.getResourceId());
    }

    @Test
    void testActivePhaseRouting() {
        BaselineDataProvider provider = new BaselineDataProvider() {
            public java.util.List<FeatureVector> getHistoryNormals(String r) {
                java.util.List<FeatureVector> list = new java.util.ArrayList<>();
                for (int i = 0; i < 3; i++) list.add(new FeatureVector(new double[14]));
                return list;
            }
            public java.util.List<FeatureVector> getHistoryAnomalies(String r) { return java.util.List.of(); }
            public BaselineStatsDTO getBaselineStats(String r) {
                return new BaselineStatsDTO(r, new double[14], new double[14], 10.0);
            }
        };
        AnomalyDetectionService service = new AnomalyDetectionService(provider, 3);

        DetectionResult result = service.detect("res-2", new FeatureVector(new double[14]), Paths.get("nonexistent.json"));
        assertEquals(Phase.ACTIVE, result.getPhase());
    }

    @Test
    void testActivePhaseFallbackWhenNoStats() {
        BaselineDataProvider provider = new BaselineDataProvider() {
            public java.util.List<FeatureVector> getHistoryNormals(String r) {
                java.util.List<FeatureVector> list = new java.util.ArrayList<>();
                for (int i = 0; i < 3; i++) list.add(new FeatureVector(new double[14]));
                return list;
            }
            public java.util.List<FeatureVector> getHistoryAnomalies(String r) { return java.util.List.of(); }
            public BaselineStatsDTO getBaselineStats(String r) { return null; }
        };
        AnomalyDetectionService service = new AnomalyDetectionService(provider, 2);

        DetectionResult result = service.detect("res-3", new FeatureVector(new double[14]), Paths.get("nonexistent.json"));
        assertNotNull(result);
        assertEquals(Phase.WARMUP, result.getPhase());
    }
}
