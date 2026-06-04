package com.anomalydetection.detector;

import com.anomalydetection.features.FeatureType;
import com.anomalydetection.features.FeatureVector;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AnomalyDetectionServiceTest {

    private static FeatureVector createVector() {
        return new FeatureVector();
    }

    private static FeatureVector createVectorWithPrecheck(String[] extensions, String[] notes) {
        FeatureVector v = new FeatureVector();
        Map<String, String[]> info = new HashMap<>();
        if (extensions != null) {
            info.put("precheck_suspicious_extensions", extensions);
        }
        if (notes != null) {
            info.put("precheck_ransom_notes", notes);
        }
        // Override the extendInfo via the existing getter — extendInfo has a @Getter
        // We need to set it. Since FeatureVector.extendInfo has @Getter only (no setter),
        // we use putExtendInfo which writes per-FeatureType. Instead, we directly
        // populate the map returned by getExtendInfo().
        v.getExtendInfo().putAll(info);
        return v;
    }

    @Test
    void testWarmupPhaseRouting() {
        BaselineDataProvider provider = new BaselineDataProvider() {
            public List<FeatureVector> getHistoryNormals(String r) { return List.of(); }
            public List<FeatureVector> getHistoryAnomalies(String r) { return List.of(); }
            public BaselineStatsDTO getBaselineStats(String r) { return null; }
            public BaselineStatsDTO getBaselineStats(String r, int s) { return null; }
        };
        AnomalyDetectionService service = new AnomalyDetectionService(5, provider);
        DetectionResult result = service.detect(createVector(), "res-1");
        assertEquals(Phase.WARMUP, result.getPhase());
        assertNotNull(result.getWarmupInfo());
        assertEquals("res-1", result.getResourceId());
    }

    @Test
    void testActivePhaseRouting() {
        BaselineDataProvider provider = new BaselineDataProvider() {
            public List<FeatureVector> getHistoryNormals(String r) {
                List<FeatureVector> list = new java.util.ArrayList<>();
                for (int i = 0; i < 3; i++) list.add(new FeatureVector());
                return list;
            }
            public List<FeatureVector> getHistoryAnomalies(String r) { return List.of(); }
            public BaselineStatsDTO getBaselineStats(String r) {
                return new BaselineStatsDTO(r, new double[FeatureType.COUNT],
                        new double[FeatureType.COUNT], 10.0);
            }
            public BaselineStatsDTO getBaselineStats(String r, int s) {
                return new BaselineStatsDTO(r, new double[FeatureType.COUNT],
                        new double[FeatureType.COUNT], 10.0);
            }
        };
        AnomalyDetectionService service = new AnomalyDetectionService(3, provider);
        DetectionResult result = service.detect(createVector(), "res-2");
        assertEquals(Phase.ACTIVE, result.getPhase());
    }

    @Test
    void testActivePhaseFallbackWhenNoStats() {
        BaselineDataProvider provider = new BaselineDataProvider() {
            public List<FeatureVector> getHistoryNormals(String r) {
                List<FeatureVector> list = new java.util.ArrayList<>();
                for (int i = 0; i < 3; i++) list.add(new FeatureVector());
                return list;
            }
            public List<FeatureVector> getHistoryAnomalies(String r) { return List.of(); }
            public BaselineStatsDTO getBaselineStats(String r) { return null; }
            public BaselineStatsDTO getBaselineStats(String r, int s) { return null; }
        };
        AnomalyDetectionService service = new AnomalyDetectionService(3, provider);
        DetectionResult result = service.detect(createVector(), "res-3");
        assertEquals(Phase.WARMUP, result.getPhase());
    }

    @Test
    void testPreCheckHitWithExtensions() {
        BaselineDataProvider provider = new BaselineDataProvider() {
            public List<FeatureVector> getHistoryNormals(String r) { return List.of(); }
            public List<FeatureVector> getHistoryAnomalies(String r) { return List.of(); }
            public BaselineStatsDTO getBaselineStats(String r) { return null; }
            public BaselineStatsDTO getBaselineStats(String r, int s) { return null; }
        };
        AnomalyDetectionService service = new AnomalyDetectionService(5, provider);
        FeatureVector vector = createVectorWithPrecheck(
                new String[]{"/data/file.locked", "/docs/info.encrypted"},
                new String[]{"/data/README_UNLOCK.txt"});
        DetectionResult result = service.detect(vector, "res-4");
        // signatureMatchResult uses Phase.WARMUP — distinguish by non-null signatureMatch
        assertNotNull(result.getSignatureMatch());
        assertTrue(result.isAnomaly());
        String sig = result.getSignatureMatch();
        assertNotNull(sig);
        assertTrue(sig.contains(".locked") || sig.contains(".encrypted"));
        assertTrue(sig.contains("README_UNLOCK"));
    }

    @Test
    void testPreCheckHitWithRansomNotesOnly() {
        BaselineDataProvider provider = new BaselineDataProvider() {
            public List<FeatureVector> getHistoryNormals(String r) { return List.of(); }
            public List<FeatureVector> getHistoryAnomalies(String r) { return List.of(); }
            public BaselineStatsDTO getBaselineStats(String r) { return null; }
            public BaselineStatsDTO getBaselineStats(String r, int s) { return null; }
        };
        AnomalyDetectionService service = new AnomalyDetectionService(5, provider);
        FeatureVector vector = createVectorWithPrecheck(
                null,
                new String[]{"/tmp/HOW_TO_DECRYPT.html"});
        DetectionResult result = service.detect(vector, "res-5");
        assertNotNull(result.getSignatureMatch());
        assertTrue(result.isAnomaly());
        String sig = result.getSignatureMatch();
        assertNotNull(sig);
        assertTrue(sig.contains("HOW_TO_DECRYPT"));
    }

    @Test
    void testPreCheckNoHit() {
        BaselineDataProvider provider = new BaselineDataProvider() {
            public List<FeatureVector> getHistoryNormals(String r) { return List.of(); }
            public List<FeatureVector> getHistoryAnomalies(String r) { return List.of(); }
            public BaselineStatsDTO getBaselineStats(String r) { return null; }
            public BaselineStatsDTO getBaselineStats(String r, int s) { return null; }
        };
        AnomalyDetectionService service = new AnomalyDetectionService(5, provider);
        FeatureVector vector = createVector(); // no precheck data
        DetectionResult result = service.detect(vector, "res-6");
        assertEquals(Phase.WARMUP, result.getPhase()); // falls through to warmup
    }
}
