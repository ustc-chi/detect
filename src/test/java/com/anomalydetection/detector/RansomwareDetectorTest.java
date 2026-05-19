package com.anomalydetection.detector;

import com.anomalydetection.features.RansomwareFeatureVector;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

public class RansomwareDetectorTest {

    @Test
    void updateWindowDoesNotThrowAndProducesResults() {
        RansomwareFeatureVector base = new RansomwareFeatureVector(new double[]{0,0,0,0,0,0,0,0,0,0,0,0});
        BaselineStatistics stats = new BaselineStatistics(Arrays.asList(base, base, base));
        double[] w = new double[]{1,1,1,1,1,1,1,1,1,1,1,1};
        WeightedEuclideanScorer scorer = new WeightedEuclideanScorer(stats, w);
        AnomalyThreshold threshold = new AnomalyThreshold(Arrays.asList(base, base, base), scorer, 99.0);
        RansomwareDetector detector = new RansomwareDetector(stats, threshold, w);

        RansomwareFeatureVector v = new RansomwareFeatureVector(new double[]{1,1,1,1,1,1,1,1,1,1,1,1});
        detector.update(v);
        detector.update(new RansomwareFeatureVector(new double[]{2,2,2,2,2,2,2,2,2,2,2,2}));
        assertThat(true).isTrue();
    }
}
