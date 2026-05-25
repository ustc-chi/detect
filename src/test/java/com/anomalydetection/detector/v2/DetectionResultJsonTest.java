package com.anomalydetection.detector.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DetectionResultJsonTest {

    private ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        return mapper;
    }

    @Test
    void testWarmupResultSerializable() throws Exception {
        WarmupDetector.WarmupDetectionResult wr = WarmupDetector.WarmupDetectionResult.normal(0);
        DetectionResult result = DetectionResult.warmupResult("test-res", new FeatureVector(new double[14]), wr);

        String json = createMapper().writeValueAsString(result);
        assertTrue(json.contains("test-res"));
        assertTrue(json.contains("WARMUP"));
    }

    @Test
    void testActiveResultWithDimensions() throws Exception {
        double[] median = new double[14], mad = new double[14], weights = new double[14];
        java.util.Arrays.fill(median, 100);
        java.util.Arrays.fill(mad, 10);
        java.util.Arrays.fill(weights, 1.0);
        double[] values = new double[14];
        java.util.Arrays.fill(values, 100);

        BaselineStatsDTO stats = new BaselineStatsDTO("res-1", median, mad, 5.0);
        DetectionResult result = new ActiveDetector(0).detect(new FeatureVector(values), stats, "res-1", weights);

        String json = createMapper().writeValueAsString(result);
        assertTrue(json.contains("res-1"));
        assertTrue(json.contains("dimensions"));
        assertTrue(json.contains("ACTIVE"));
    }

    @Test
    void testSignatureAnomalySerializable() throws Exception {
        DetectionResult result = DetectionResult.signatureAnomaly("res-x", Phase.WARMUP, "Found .locked file");
        String json = createMapper().writeValueAsString(result);
        assertTrue(json.contains("res-x"));
        assertTrue(json.contains(".locked"));
    }
}
