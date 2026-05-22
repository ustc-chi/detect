package com.anomalydetection.detector.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that DetectionResult can be serialized to JSON for report generation.
 */
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
        DetectionResult result = DetectionResult.warmupResult(
                "test-res",
                new FeatureVector14(new double[14]),
                0.5, 2.0, false, null,
                new WarmupInfo(0, java.util.List.of(), 0.0, true)
        );

        String json = createMapper().writeValueAsString(result);
        assertTrue(json.contains("test-res"));
        assertTrue(json.contains("WARMUP"));
        assertTrue(json.contains("total_operations"));
        assertTrue(json.contains("supplementary"));
    }

    @Test
    void testActiveResultWithDimensions() throws Exception {
        double[] median = new double[14];
        double[] mad = new double[14];
        java.util.Arrays.fill(mad, 10);
        double[] weights = new double[14];
        java.util.Arrays.fill(weights, 1.0);
        double[] values = new double[14];
        for (int i = 0; i < 14; i++) values[i] = 100;

        BaselineStatsDTO stats = new BaselineStatsDTO("res-1", median, mad, 5.0, weights);
        FeatureVector14 vector = new FeatureVector14(values);

        ActiveDetector detector = new ActiveDetector(0);
        DetectionResult result = detector.detect(vector, stats, "res-1");

        String json = createMapper().writeValueAsString(result);

        // Verify JSON contains key report fields
        assertTrue(json.contains("res-1"), "Should contain resourceId");
        assertTrue(json.contains("dimensions"), "Should contain dimensions array");
        assertTrue(json.contains("score"), "Should contain score");
        assertTrue(json.contains("ACTIVE"), "Should contain ACTIVE phase");
        // Verify a dimension report has expected fields
        assertTrue(json.contains("zScore") || json.contains("contribution")
                || json.contains("description"), "Should contain dimension detail fields");
    }
}
