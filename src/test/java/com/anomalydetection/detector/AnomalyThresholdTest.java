package com.anomalydetection.detector;

import com.anomalydetection.features.RansomwareFeatureVector;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.*;

class AnomalyThresholdTest {

    private static final double[] ONES = {1,1,1,1,1,1,1,1,1,1,1,1};
    private static final double[] ZEROS = {0,0,0,0,0,0,0,0,0,0,0,0};

    private RansomwareFeatureVector zeros() {
        return new RansomwareFeatureVector(ZEROS);
    }

    private RansomwareFeatureVector filled(double v) {
        double[] d = new double[12];
        Arrays.fill(d, v);
        return new RansomwareFeatureVector(d);
    }

    private List<RansomwareFeatureVector> baselineOf(int normalCount, double normalValue, int outlierCount, double outlierValue) {
        List<RansomwareFeatureVector> list = new ArrayList<>();
        for (int i = 0; i < normalCount; i++) {
            list.add(filled(normalValue));
        }
        for (int i = 0; i < outlierCount; i++) {
            list.add(filled(outlierValue));
        }
        return list;
    }

    @Test
    void outlierBaselineRoundIsFilteredOut() {
        List<RansomwareFeatureVector> baseline = new ArrayList<>();
        for (int i = 0; i < 23; i++) {
            baseline.add(filled(1.0));
        }
        baseline.add(filled(20.0));

        BaselineStatistics stats = new BaselineStatistics(baseline);
        WeightedEuclideanScorer scorer = new WeightedEuclideanScorer(stats, ONES);

        AnomalyThreshold threshold = new AnomalyThreshold(baseline, scorer, 97.0);

        double outlierScore = scorer.score(filled(20.0));
        assertThat(threshold.getThreshold()).isLessThan(outlierScore);
    }

    @Test
    void cleanBaselineProducesSameThreshold() {
        List<RansomwareFeatureVector> baseline = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            baseline.add(zeros());
        }
        BaselineStatistics stats = new BaselineStatistics(baseline);
        WeightedEuclideanScorer scorer = new WeightedEuclideanScorer(stats, ONES);

        AnomalyThreshold threshold = new AnomalyThreshold(baseline, scorer, 97.0);

        assertThat(threshold.getThreshold()).isEqualTo(0.0);
    }

    @Test
    void smallBaselineSkipsIqrFiltering() {
        List<RansomwareFeatureVector> baseline = new ArrayList<>();
        baseline.add(zeros());
        baseline.add(zeros());
        baseline.add(filled(10.0));

        BaselineStatistics stats = new BaselineStatistics(baseline);
        WeightedEuclideanScorer scorer = new WeightedEuclideanScorer(stats, ONES);

        AnomalyThreshold threshold = new AnomalyThreshold(baseline, scorer, 97.0);

        double outlierScore = scorer.score(filled(10.0));
        assertThat(threshold.getThreshold()).isLessThan(outlierScore);
    }

    @Test
    void iqrMultiplierZeroDisablesFiltering() {
        List<RansomwareFeatureVector> baseline = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            double[] vals = new double[12];
            Arrays.fill(vals, (double) i);
            baseline.add(new RansomwareFeatureVector(vals));
        }
        for (int i = 0; i < 11; i++) {
            double[] vals = new double[12];
            Arrays.fill(vals, (double) (i + 1));
            baseline.add(new RansomwareFeatureVector(vals));
        }
        baseline.add(filled(100.0));

        BaselineStatistics stats = new BaselineStatistics(baseline);
        WeightedEuclideanScorer scorer = new WeightedEuclideanScorer(stats, ONES);

        AnomalyThreshold withFiltering = new AnomalyThreshold(baseline, scorer, 97.0, 2.5);
        AnomalyThreshold withoutFiltering = new AnomalyThreshold(baseline, scorer, 97.0, 0.0);

        assertThat(withoutFiltering.getThreshold()).isGreaterThan(withFiltering.getThreshold());
    }

    @Test
    void medianCapAppliesWhenPercentileExceedsMedian() {
        List<RansomwareFeatureVector> baseline = baselineOf(20, 0.0, 4, 5.0);
        BaselineStatistics stats = new BaselineStatistics(baseline);
        WeightedEuclideanScorer scorer = new WeightedEuclideanScorer(stats, ONES);

        AnomalyThreshold threshold = new AnomalyThreshold(baseline, scorer, 97.0);

        List<Double> allScores = new ArrayList<>();
        for (RansomwareFeatureVector v : baseline) {
            allScores.add(scorer.score(v));
        }
        allScores.sort(Double::compare);
        double median = allScores.get(allScores.size() / 2);

        assertThat(threshold.getThreshold()).isLessThanOrEqualTo(3.0 * median);
    }

    @Test
    void diagnosticWarningLoggedWhenOutliersFiltered() {
        List<RansomwareFeatureVector> baseline = new ArrayList<>();
        for (int i = 0; i < 23; i++) {
            baseline.add(filled(1.0));
        }
        baseline.add(filled(20.0));

        BaselineStatistics stats = new BaselineStatistics(baseline);
        WeightedEuclideanScorer scorer = new WeightedEuclideanScorer(stats, ONES);

        Logger log = Logger.getLogger(AnomalyThreshold.class.getName());
        final List<LogRecord> records = new ArrayList<>();
        Handler handler = new Handler() {
            @Override public void publish(LogRecord record) { records.add(record); }
            @Override public void flush() {}
            @Override public void close() {}
        };
        log.addHandler(handler);
        try {
            new AnomalyThreshold(baseline, scorer, 97.0);

            assertThat(records).anySatisfy(r -> {
                assertThat(r.getMessage()).contains("Filtered");
                assertThat(r.getMessage()).contains("1 outlier baseline scores");
            });
        } finally {
            log.removeHandler(handler);
        }
    }
}
