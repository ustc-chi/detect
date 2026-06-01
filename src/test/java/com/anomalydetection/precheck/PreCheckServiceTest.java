package com.anomalydetection.precheck;

import com.anomalydetection.features.FeatureVector;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

class PreCheckServiceTest {

    private static FeatureVector createVectorWithPrecheck(String[] extensions, String[] notes) {
        FeatureVector v = new FeatureVector();
        Map<String, String[]> info = new HashMap<>();
        if (extensions != null) {
            info.put(PreCheckService.PRECHECK_EXTENSIONS_KEY, extensions);
        }
        if (notes != null) {
            info.put(PreCheckService.PRECHECK_RANSOM_NOTES_KEY, notes);
        }
        v.getExtendInfo().putAll(info);
        return v;
    }

    private final PreCheckService service = new PreCheckService();

    @Test
    void testMatchSuspiciousExtensions() {
        FeatureVector vector = createVectorWithPrecheck(
                new String[]{"/data/file.locked", "/docs/info.encrypted"},
                null);
        PreCheckResult result = service.check(vector);
        assertTrue(result.isMatch());
        assertEquals(2, result.getMatchedExtensions().size());
        assertTrue(result.getMatchedExtensions().contains("/data/file.locked"));
        assertTrue(result.getMatchedExtensions().contains("/docs/info.encrypted"));
        assertTrue(result.getRansomNotePaths().isEmpty());
    }

    @Test
    void testMatchRansomNotes() {
        FeatureVector vector = createVectorWithPrecheck(
                null,
                new String[]{"/tmp/README_UNLOCK.txt", "/data/HOW_TO_DECRYPT.html"});
        PreCheckResult result = service.check(vector);
        assertTrue(result.isMatch());
        assertEquals(2, result.getRansomNotePaths().size());
        assertTrue(result.getRansomNotePaths().contains("/tmp/README_UNLOCK.txt"));
        assertTrue(result.getRansomNotePaths().contains("/data/HOW_TO_DECRYPT.html"));
        assertTrue(result.getMatchedExtensions().isEmpty());
    }

    @Test
    void testMatchBoth() {
        FeatureVector vector = createVectorWithPrecheck(
                new String[]{"/data/file.locked"},
                new String[]{"/README_UNLOCK.txt"});
        PreCheckResult result = service.check(vector);
        assertTrue(result.isMatch());
        assertEquals(1, result.getMatchedExtensions().size());
        assertEquals(1, result.getRansomNotePaths().size());
    }

    @Test
    void testNoMatch() {
        FeatureVector vector = createVectorWithPrecheck(null, null);
        PreCheckResult result = service.check(vector);
        assertFalse(result.isMatch());
        assertTrue(result.getMatchedExtensions().isEmpty());
        assertTrue(result.getRansomNotePaths().isEmpty());
    }

    @Test
    void testEmptyArrays() {
        FeatureVector vector = createVectorWithPrecheck(new String[0], new String[0]);
        PreCheckResult result = service.check(vector);
        assertFalse(result.isMatch());
    }

    @Test
    void testNoExtendInfo() {
        FeatureVector vector = new FeatureVector();
        PreCheckResult result = service.check(vector);
        assertFalse(result.isMatch());
    }

    @Test
    void testSkipsNullEntries() {
        FeatureVector vector = createVectorWithPrecheck(
                new String[]{"/data/file.locked", null, ""},
                new String[]{null, "/HOW_TO_DECRYPT.html"});
        PreCheckResult result = service.check(vector);
        assertTrue(result.isMatch());
        assertEquals(1, result.getMatchedExtensions().size());
        assertEquals(1, result.getRansomNotePaths().size());
    }
}
