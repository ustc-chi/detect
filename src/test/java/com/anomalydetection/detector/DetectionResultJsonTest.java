package com.anomalydetection.detector;

import com.anomalydetection.features.FeatureType;
import com.anomalydetection.features.FeatureVector;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DetectionResultJsonTest {

    private static FeatureVector createVector() {
        return new FeatureVector();
    }

    private ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        return mapper;
    }

    @Test
    void testWarmupResultSerializable() throws Exception {
        WarmupDetector.WarmupDetectionResult wr = WarmupDetector.WarmupDetectionResult.normal(java.util.List.of());
        DetectionResult result = DetectionResult.warmupResult("test-res", createVector(), wr);

        String json = createMapper().writeValueAsString(result);
        assertTrue(json.contains("test-res"));
        assertTrue(json.contains("WARMUP"));
    }

    @Test
    void testActiveResultWithDimensions() throws Exception {
        int n = FeatureType.COUNT;
        double[] median = new double[n], mad = new double[n], weights = new double[n];
        java.util.Arrays.fill(median, 100);
        java.util.Arrays.fill(mad, 10);
        java.util.Arrays.fill(weights, 1.0);

        BaselineStatsDTO stats = new BaselineStatsDTO("res-1", median, mad, 5.0);
        DetectionResult result = new ActiveDetector(0).detect(createVector(), stats, "res-1", weights);

        String json = createMapper().writeValueAsString(result);
        assertTrue(json.contains("res-1"));
        assertTrue(json.contains("dimensions"));
        assertTrue(json.contains("ACTIVE"));
    }

    @Test
    void testSignatureAnomalySerializable() throws Exception {
        DetectionResult result = DetectionResult.signatureMatchResult("res-x", "Found .locked file");
        String json = createMapper().writeValueAsString(result);
        assertTrue(json.contains("res-x"));
        assertTrue(json.contains("Found .locked file"));
    }
}
