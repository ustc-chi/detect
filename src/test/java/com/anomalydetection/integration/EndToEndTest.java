package com.anomalydetection.integration;

import com.anomalydetection.features.RansomwareFeatureVector;
import com.anomalydetection.detector.BaselineStatistics;
import com.anomalydetection.detector.WeightedEuclideanScorer;
import com.anomalydetection.detector.RansomwareDetector;
import com.anomalydetection.detector.DetectionResult;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

public class EndToEndTest {

    @Test
    void simpleEndToEndDetection()
    {
        // Build a small baseline and run a basic attack vector
        com.anomalydetection.features.RansomwareFeatureVector base = new com.anomalydetection.features.RansomwareFeatureVector(new double[]{0,0,0,0,0,0,0,0,0,0,0,0});
        BaselineStatistics stats = new BaselineStatistics(Arrays.asList(base, base, base));
        double[] w = new double[]{1,1,1,1,1,1,1,1,1,1,1,1};
        WeightedEuclideanScorer scorer = new WeightedEuclideanScorer(stats, w);
        com.anomalydetection.detector.AnomalyThreshold threshold = new com.anomalydetection.detector.AnomalyThreshold(Arrays.asList(base, base, base), scorer, 99.0);
        RansomwareDetector detector = new RansomwareDetector(stats, threshold, w);

        com.anomalydetection.features.RansomwareFeatureVector attack = new com.anomalydetection.features.RansomwareFeatureVector(new double[]{9,9,9,9,9,9,9,9,9,9,9,9});
        DetectionResult result = detector.detect(attack);
        // Should be flagged as anomaly given extreme deviation
        assertThat(result.isAnomaly()).isTrue();
    }
}
