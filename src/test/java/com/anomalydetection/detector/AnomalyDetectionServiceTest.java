package com.anomalydetection.detector;

import com.anomalydetection.features.FeatureType;
import com.anomalydetection.features.FeatureVector;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class AnomalyDetectionServiceTest {

    private static FeatureVector createVector() {
        return new FeatureVector();
    }

    private static Path createEmptySnapdiff() throws IOException {
        Path f = Files.createTempFile("snapdiff-", ".json");
        Files.writeString(f, "[]");
        return f;
    }

    @Test
    void testWarmupPhaseRouting() throws IOException {
        Path tmp = createEmptySnapdiff();
        try {
            BaselineDataProvider provider = new BaselineDataProvider() {
                public java.util.List<FeatureVector> getHistoryNormals(String r) { return java.util.List.of(); }
                public java.util.List<FeatureVector> getHistoryAnomalies(String r) { return java.util.List.of(); }
                public BaselineStatsDTO getBaselineStats(String r) { return null; }
            };
            AnomalyDetectionService service = new AnomalyDetectionService(5, provider);
            DetectionResult result = service.detect(tmp, createVector(), "res-1");
            assertEquals(Phase.WARMUP, result.getPhase());
            assertNotNull(result.getWarmupInfo());
            assertEquals("res-1", result.getResourceId());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void testActivePhaseRouting() throws IOException {
        Path tmp = createEmptySnapdiff();
        try {
            BaselineDataProvider provider = new BaselineDataProvider() {
                public java.util.List<FeatureVector> getHistoryNormals(String r) {
                    java.util.List<FeatureVector> list = new java.util.ArrayList<>();
                    for (int i = 0; i < 3; i++) list.add(new FeatureVector());
                    return list;
                }
                public java.util.List<FeatureVector> getHistoryAnomalies(String r) { return java.util.List.of(); }
                public BaselineStatsDTO getBaselineStats(String r) {
                    return new BaselineStatsDTO(r, new double[FeatureType.COUNT],
                            new double[FeatureType.COUNT], 10.0);
                }
            };
            AnomalyDetectionService service = new AnomalyDetectionService(3, provider);
            DetectionResult result = service.detect(tmp, createVector(), "res-2");
            assertEquals(Phase.ACTIVE, result.getPhase());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void testActivePhaseFallbackWhenNoStats() throws IOException {
        Path tmp = createEmptySnapdiff();
        try {
            BaselineDataProvider provider = new BaselineDataProvider() {
                public java.util.List<FeatureVector> getHistoryNormals(String r) {
                    java.util.List<FeatureVector> list = new java.util.ArrayList<>();
                    for (int i = 0; i < 3; i++) list.add(new FeatureVector());
                    return list;
                }
                public java.util.List<FeatureVector> getHistoryAnomalies(String r) { return java.util.List.of(); }
                public BaselineStatsDTO getBaselineStats(String r) { return null; }
            };
            AnomalyDetectionService service = new AnomalyDetectionService(3, provider);
            DetectionResult result = service.detect(tmp, createVector(), "res-3");
            assertEquals(Phase.WARMUP, result.getPhase());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void testPreCheckFileNotFound() {
        AnomalyDetectionService service = new AnomalyDetectionService(3, new ExternalBaselineProvider());
        assertThrows(IOException.class, () ->
            service.detect(Path.of("nonexistent.json"), createVector(), "res-4"));
    }
}
