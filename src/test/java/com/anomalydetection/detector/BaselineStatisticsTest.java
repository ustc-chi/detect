package com.anomalydetection.detector;

import com.anomalydetection.features.RansomwareFeatureVector;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.*;

public class BaselineStatisticsTest {

    @Test
    void testMedianAndMadComputation() {
        RansomwareFeatureVector v1 = new RansomwareFeatureVector(new double[]{1,1,1,1,1,1,1,1,1,1,1,1});
        RansomwareFeatureVector v2 = new RansomwareFeatureVector(new double[]{1,1,1,1,1,1,1,1,1,1,1,1});
        RansomwareFeatureVector v3 = new RansomwareFeatureVector(new double[]{1,1,1,1,1,1,1,1,1,1,1,1});

        BaselineStatistics stats = new BaselineStatistics(Arrays.asList(v1, v2, v3));

        for (int i = 0; i < RansomwareFeatureVector.FEATURE_COUNT; i++) {
            assertThat(stats.getMedian(i)).isCloseTo(1.0, within(1e-9));
        }
        assertThat(stats.getMad(0)).isCloseTo(0.0, within(0.1));
    }

    @Test
    void testEmptyBaselineProducesArraysOfRightLength() {
        BaselineStatistics empty = new BaselineStatistics(Collections.emptyList());
        assertThat(empty.getMedian()).hasSize(RansomwareFeatureVector.FEATURE_COUNT);
        assertThat(empty.getMad()).hasSize(RansomwareFeatureVector.FEATURE_COUNT);
    }

    @Test
    void testNonZeroMad() {
        RansomwareFeatureVector v1 = new RansomwareFeatureVector(new double[]{0,0,0,0,0,0,0,0,0,0,0,0});
        RansomwareFeatureVector v2 = new RansomwareFeatureVector(new double[]{2,2,2,2,2,2,2,2,2,2,2,2});
        BaselineStatistics stats = new BaselineStatistics(Arrays.asList(v1, v2));
        assertThat(stats.getMedian(0)).isCloseTo(1.0, within(1e-9));
        assertThat(stats.getMad(0)).isCloseTo(1.4826, within(1e-4));
    }
}
