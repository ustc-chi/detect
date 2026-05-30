package com.anomalydetection.detector;

import com.anomalydetection.features.FeatureType;
import com.anomalydetection.features.FeatureVector;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class WarmupActiveIntegrationTest {

    private static FeatureVector createVector() {
        return new FeatureVector();
    }

    @Test
    void testFullWarmupToActiveTransition() throws IOException {
        Path tempFile = Files.createTempFile("snapdiff-", ".json");
        Files.writeString(tempFile, "[]");
        try {
            BaselineDataProvider provider = new BaselineDataProvider() {
                private int callCount = 0;
                public java.util.List<FeatureVector> getHistoryNormals(String r) {
                    callCount++;
                    java.util.List<FeatureVector> list = new java.util.ArrayList<>();
                    for (int i = 0; i < Math.min(callCount, 6); i++) list.add(new FeatureVector());
                    return list;
                }
                public java.util.List<FeatureVector> getHistoryAnomalies(String r) { return java.util.List.of(); }
                public BaselineStatsDTO getBaselineStats(String r) {
                    return new BaselineStatsDTO(r, new double[FeatureType.COUNT],
                            new double[FeatureType.COUNT], 10.0);
                }
            };

            AnomalyDetectionService service = new AnomalyDetectionService(5, provider);
            FeatureVector vec = createVector();

            for (int i = 0; i < 4; i++) {
                DetectionResult r = service.detect(tempFile, vec, "integ-res");
                assertEquals(Phase.WARMUP, r.getPhase(), "Round " + (i + 1) + " should be WARMUP");
            }

            DetectionResult r5 = service.detect(tempFile, vec, "integ-res");
            assertEquals(Phase.ACTIVE, r5.getPhase());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
