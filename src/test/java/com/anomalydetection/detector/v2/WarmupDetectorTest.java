package com.anomalydetection.detector.v2;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WarmupDetectorTest {

    private FeatureVector normalVector() {
        double[] v = new double[14];
        v[0] = 8000; v[1] = 0.50; v[2] = 1.0; v[3] = 30; v[4] = 15;
        v[5] = 0; v[6] = 1500; v[7] = 8; v[8] = 2; v[9] = 0.25;
        v[10] = 0.70; v[11] = 0.30; v[12] = 0.5; v[13] = 0.9;
        return new FeatureVector(v);
    }

    private FeatureVector makeControlledNormal(double totalOps, double modRatio, double peakVel,
                                                double burstPurity, double hvExtRatio, double interOpCv, double fileConc) {
        double[] v = new double[14];
        v[0] = totalOps; v[1] = modRatio; v[2] = 1.0; v[3] = 30; v[4] = 15;
        v[5] = 0; v[6] = peakVel; v[7] = 8; v[8] = 2; v[9] = hvExtRatio;
        v[10] = burstPurity; v[11] = fileConc; v[12] = 0.5; v[13] = interOpCv;
        return new FeatureVector(v);
    }

    @Test
    void testLayer2_HeuristicTriggers() {
        WarmupDetector detector = new WarmupDetector();
        FeatureVector attack = makeControlledNormal(15000, 0.97, 8000, 0.98, 0.85, 0.03, 0.95);
        WarmupDetector.WarmupDetectionResult r = detector.detect(attack, java.util.List.of());
        assertEquals(WarmupStatus.ANOMALY, r.getStatus());
        assertFalse(r.isAddToBaseline());
    }

    @Test
    void testNormalPassesThrough() {
        WarmupDetector detector = new WarmupDetector();
        WarmupDetector.WarmupDetectionResult r = detector.detect(normalVector(), java.util.List.of());
        assertEquals(WarmupStatus.NORMAL, r.getStatus());
        assertTrue(r.isAddToBaseline());
    }

    @Test
    void testLayer3_DynamicThreshold() {
        WarmupDetector detector = new WarmupDetector();
        java.util.List<FeatureVector> normals = java.util.List.of(
                makeControlledNormal(7000, 0.45, 1200, 0.65, 0.22, 0.80, 0.25),
                makeControlledNormal(8000, 0.50, 1500, 0.70, 0.25, 0.90, 0.30),
                makeControlledNormal(9000, 0.55, 1800, 0.75, 0.28, 1.00, 0.35)
        );
        FeatureVector deviant = makeControlledNormal(25000, 0.50, 4500, 0.70, 0.25, 0.90, 0.30);
        assertEquals(WarmupStatus.NORMAL, detector.detect(deviant, normals).getStatus());
    }

    @Test
    void testLayer3_FlaggedAsSuspicious() {
        WarmupDetector detector = new WarmupDetector();
        java.util.List<FeatureVector> normals = java.util.List.of(
                makeControlledNormal(7000, 0.45, 1200, 0.65, 0.22, 0.80, 0.25),
                makeControlledNormal(8000, 0.50, 1500, 0.70, 0.25, 0.90, 0.30),
                makeControlledNormal(9000, 0.55, 1800, 0.75, 0.28, 1.00, 0.35)
        );
        FeatureVector extreme = makeControlledNormal(100000, 0.50, 50000, 0.70, 0.25, 0.90, 0.30);
        WarmupDetector.WarmupDetectionResult r = detector.detect(extreme, normals);
        assertTrue(r.getStatus() == WarmupStatus.SUSPICIOUS || r.getStatus() == WarmupStatus.ANOMALY);
    }
}
