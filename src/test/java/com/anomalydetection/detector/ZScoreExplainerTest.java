package com.anomalydetection.detector;

import com.anomalydetection.features.RansomwareFeatureVector;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

public class ZScoreExplainerTest {

    @Test
    void testZScoreComputation() {
        RansomwareFeatureVector v1 = new RansomwareFeatureVector(new double[]{0,0,0,0,0,0,0,0,0,0,0,0});
        RansomwareFeatureVector v2 = new RansomwareFeatureVector(new double[]{2,2,2,2,2,2,2,2,2,2,2,2});
        BaselineStatistics stats = new BaselineStatistics(Arrays.asList(v1, v2));
        ZScoreExplainer explainer = new ZScoreExplainer(stats);

        RansomwareFeatureVector testVec = new RansomwareFeatureVector(new double[]{1,1,1,1,1,1,1,1,1,1,1,1});
        Map<String, Double> zScores = explainer.computeZScores(testVec);

        for (Map.Entry<String, Double> entry : zScores.entrySet()) {
            assertThat(entry.getValue()).isCloseTo(0.0, within(1e-6));
        }
    }

    @Test
    void testTopDeviationsSortedByAbsZScore() {
        RansomwareFeatureVector base = new RansomwareFeatureVector(new double[]{0,0,0,0,0,0,0,0,0,0,0,0});
        BaselineStatistics stats = new BaselineStatistics(Arrays.asList(base, base, base));
        ZScoreExplainer explainer = new ZScoreExplainer(stats);

        RansomwareFeatureVector testVec = new RansomwareFeatureVector(new double[]{5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5});
        List<Map.Entry<String, Double>> top = explainer.topDeviations(testVec, 2);
        assertThat(top).isNotEmpty();
        assertThat(top).hasSize(2);
    }
}
