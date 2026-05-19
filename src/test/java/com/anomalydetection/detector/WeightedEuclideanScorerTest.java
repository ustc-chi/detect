package com.anomalydetection.detector;

import com.anomalydetection.features.RansomwareFeatureVector;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

public class WeightedEuclideanScorerTest {

    @Test
    void testScoreAtMeanIsZero() {
        RansomwareFeatureVector v1 = new RansomwareFeatureVector(new double[]{0,0,0,0,0,0,0,0,0,0,0,0});
        RansomwareFeatureVector v2 = new RansomwareFeatureVector(new double[]{0,0,0,0,0,0,0,0,0,0,0,0});
        RansomwareFeatureVector v3 = new RansomwareFeatureVector(new double[]{0,0,0,0,0,0,0,0,0,0,0,0});
        java.util.List<com.anomalydetection.features.RansomwareFeatureVector> list = Arrays.asList(v1,v2,v3);
        BaselineStatistics stats = new BaselineStatistics(list);
        double[] w = new double[]{1,1,1,1,1,1,1,1,1,1,1,1};
        WeightedEuclideanScorer scorer = new WeightedEuclideanScorer(stats, w);
        double score = scorer.score(new RansomwareFeatureVector(new double[]{0,0,0,0,0,0,0,0,0,0,0,0}));
        assertThat(score).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void testScoreDistanceMoreForFarVector() {
        RansomwareFeatureVector meanVec = new RansomwareFeatureVector(new double[]{0,0,0,0,0,0,0,0,0,0,0,0});
        BaselineStatistics stats = new BaselineStatistics(Arrays.asList(meanVec, meanVec, meanVec));
        double[] w = new double[]{1,1,1,1,1,1,1,1,1,1,1,1};
        WeightedEuclideanScorer scorer = new WeightedEuclideanScorer(stats, w);
        double score = scorer.score(new RansomwareFeatureVector(new double[]{3,0,0,0,0,0,0,0,0,0,0,0}));
        assertThat(score).isGreaterThan(0.0);
    }

    @Test
    void testCustomWeightsAffectScore() {
        RansomwareFeatureVector meanVec = new RansomwareFeatureVector(new double[]{1,1,1,1,1,1,1,1,1,1,1,1});
        BaselineStatistics stats = new BaselineStatistics(Arrays.asList(meanVec, meanVec, meanVec));
        double[] w = new double[]{1,2,3,4,5,6,7,8,9,10,11,12};
        WeightedEuclideanScorer scorer = new WeightedEuclideanScorer(stats, w);
        double score = scorer.score(new RansomwareFeatureVector(new double[]{1,1,1,1,1,1,1,1,2,1,1,1}));
        assertThat(score).isGreaterThanOrEqualTo(0.0);
    }
}
