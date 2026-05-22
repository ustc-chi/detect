package com.anomalydetection.detector.v2;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for WarmupDetector covering all three layers and the dynamic threshold.
 */
class WarmupDetectorTest {

    private FeatureVector14 normalVector() {
        double[] v = new double[14];
        v[0] = 8000;    // total_operations (normal)
        v[1] = 0.50;    // modification_ratio (normal ~50%)
        v[2] = 1.0;     // deletion_intensity
        v[3] = 30;      // directory_spread
        v[4] = 15;      // extension_diversity
        v[5] = 0;       // suspicious_extension_ratio (none)
        v[6] = 1500;    // peak_burst_velocity (normal)
        v[7] = 8;       // avg_modified_size
        v[8] = 2;       // size_std_dev
        v[9] = 0.25;    // high_value_ext_ratio (normal ~25%)
        v[10] = 0.70;   // burst_mod_purity (normal ~70%)
        v[11] = 0.30;   // file_type_concentration
        v[12] = 0.5;    // size_change_kurtosis
        v[13] = 0.9;    // inter_op_time_cv (normal, high CV)
        return new FeatureVector14(v);
    }

    private FeatureVector14 attackVector() {
        double[] v = new double[14];
        v[0] = 15000;
        v[1] = 0.97;    // HIGH modification_ratio
        v[2] = 0.5;
        v[3] = 15;
        v[4] = 5;
        v[5] = 0;
        v[6] = 8000;    // HIGH peak_burst_velocity
        v[7] = 9;
        v[8] = 0.5;
        v[9] = 0.85;    // HIGH hv_ext_ratio
        v[10] = 0.98;   // HIGH burst_mod_purity
        v[11] = 0.95;   // HIGH concentration
        v[12] = 3.0;
        v[13] = 0.03;   // LOW inter_op_time_cv (robotic)
        return new FeatureVector14(v);
    }

    @Test
    void testLayer1_DeterministicTriggers() {
        WarmupDetector detector = new WarmupDetector();
        double[] v = new double[14];
        v[5] = 0.5; // suspicious_extension_ratio > 0
        WarmupDetector.WarmupDetectionResult r = detector.detect(
                new FeatureVector14(v), java.util.List.of());
        assertEquals(WarmupStatus.ANOMALY, r.getStatus());
        assertFalse(r.isAddToBaseline());
        assertTrue(r.getTriggeredRules().contains("SUSPICIOUS_EXTENSION"));
    }

    @Test
    void testLayer2_HeuristicTriggers() {
        WarmupDetector detector = new WarmupDetector();
        FeatureVector14 attack = attackVector();
        WarmupDetector.WarmupDetectionResult r = detector.detect(
                attack, java.util.List.of());
        assertEquals(WarmupStatus.ANOMALY, r.getStatus());
        assertFalse(r.isAddToBaseline());
        assertTrue(r.getTriggeredRules().size() > 0);
    }

    @Test
    void testNormalPassesThrough() {
        WarmupDetector detector = new WarmupDetector();
        FeatureVector14 normal = normalVector();
        WarmupDetector.WarmupDetectionResult r = detector.detect(
                normal, java.util.List.of());
        assertEquals(WarmupStatus.NORMAL, r.getStatus());
        assertTrue(r.isAddToBaseline());
    }

    /**
     * Creates a controlled normal vector with explicit values for testing.
     */
    private FeatureVector14 makeControlledNormal(double totalOps, double modRatio,
                                                  double peakVel, double burstPurity,
                                                  double hvExtRatio, double interOpCv,
                                                  double fileConc) {
        double[] v = new double[14];
        v[0] = totalOps;
        v[1] = modRatio;
        v[2] = 1.0;
        v[3] = 30;
        v[4] = 15;
        v[5] = 0;
        v[6] = peakVel;
        v[7] = 8;
        v[8] = 2;
        v[9] = hvExtRatio;
        v[10] = burstPurity;
        v[11] = fileConc;
        v[12] = 0.5;
        v[13] = interOpCv;
        return new FeatureVector14(v);
    }

    @Test
    void testLayer3_DynamicThreshold() {
        WarmupDetector detector = new WarmupDetector();

        // 3 controlled normal vectors with moderate variance
        java.util.List<FeatureVector14> normals = new java.util.ArrayList<>();
        normals.add(makeControlledNormal(7000, 0.45, 1200, 0.65, 0.22, 0.80, 0.25));
        normals.add(makeControlledNormal(8000, 0.50, 1500, 0.70, 0.25, 0.90, 0.30));
        normals.add(makeControlledNormal(9000, 0.55, 1800, 0.75, 0.28, 1.00, 0.35));

        // Slightly deviant — within 10x multiplier range
        FeatureVector14 deviantVec = makeControlledNormal(25000, 0.50, 4500, 0.70, 0.25, 0.90, 0.30);

        WarmupDetector.WarmupDetectionResult r = detector.detect(deviantVec, normals);
        // With 3 normals, multiplier is 10x, so should pass as normal
        assertEquals(WarmupStatus.NORMAL, r.getStatus());
    }

    @Test
    void testLayer3_FlaggedAsSuspicious() {
        WarmupDetector detector = new WarmupDetector();

        // 3 controlled normal vectors
        java.util.List<FeatureVector14> normals = new java.util.ArrayList<>();
        normals.add(makeControlledNormal(7000, 0.45, 1200, 0.65, 0.22, 0.80, 0.25));
        normals.add(makeControlledNormal(8000, 0.50, 1500, 0.70, 0.25, 0.90, 0.30));
        normals.add(makeControlledNormal(9000, 0.55, 1800, 0.75, 0.28, 1.00, 0.35));

        // Extreme deviant — v[0]=100000 is 11-14x normal, v[6]=50000 is 28-42x normal
        FeatureVector14 extremeVec = makeControlledNormal(100000, 0.50, 50000, 0.70, 0.25, 0.90, 0.30);

        WarmupDetector.WarmupDetectionResult r = detector.detect(extremeVec, normals);
        assertTrue(r.getStatus() == WarmupStatus.SUSPICIOUS || r.getStatus() == WarmupStatus.ANOMALY,
                "Expected SUSPICIOUS or ANOMALY, got: " + r.getStatus());
    }

    @Test
    void testLayer3RequiresAtLeast2Normals() {
        WarmupDetector detector = new WarmupDetector();
        java.util.List<FeatureVector14> singleNormal = java.util.List.of(normalVector());

        // Even extreme values should pass as NORMAL when < 2 history vectors
        double[] extreme = new double[14];
        System.arraycopy(normalVector().toArray(), 0, extreme, 0, 14);
        extreme[0] = 999999;
        FeatureVector14 extremeVec = new FeatureVector14(extreme);

        // Only Layer 1 and 2 apply → this doesn't trigger heuristics because
        // modification_ratio is still 0.5 and others are normal
        WarmupDetector.WarmupDetectionResult r = detector.detect(extremeVec, singleNormal);
        assertEquals(WarmupStatus.NORMAL, r.getStatus());
    }
}
