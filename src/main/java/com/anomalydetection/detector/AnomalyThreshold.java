package com.anomalydetection.detector;

import com.anomalydetection.features.RansomwareFeatureVector;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

public class AnomalyThreshold {
    private static final Logger LOG = Logger.getLogger(AnomalyThreshold.class.getName());

    private static final double DEFAULT_IQR_MULTIPLIER = 2.5;
    private static final double MEDIAN_CAP_MULTIPLIER = 3.0;
    private static final int MIN_BASELINE_FOR_IQR = 5;

    private final double threshold;

    public AnomalyThreshold(List<RansomwareFeatureVector> baselineVectors,
                            WeightedEuclideanScorer scorer,
                            double percentile) {
        this(baselineVectors, scorer, percentile, DEFAULT_IQR_MULTIPLIER);
    }

    public AnomalyThreshold(List<RansomwareFeatureVector> baselineVectors,
                            WeightedEuclideanScorer scorer,
                            double percentile,
                            double iqrMultiplier) {
        if (baselineVectors == null || baselineVectors.isEmpty()) {
            this.threshold = 0.0;
            return;
        }

        List<Double> distances = new ArrayList<>(baselineVectors.size());
        for (RansomwareFeatureVector v : baselineVectors) {
            distances.add(scorer.score(v));
        }
        Collections.sort(distances);

        if (distances.size() >= MIN_BASELINE_FOR_IQR && iqrMultiplier > 0) {
            double q1 = percentile(distances, 25.0);
            double q3 = percentile(distances, 75.0);
            double iqr = q3 - q1;
            double upperFence = q3 + iqrMultiplier * iqr;

            List<Double> filtered = new ArrayList<>(distances.size());
            List<Double> outliers = new ArrayList<>();
            for (double d : distances) {
                if (d <= upperFence) {
                    filtered.add(d);
                } else {
                    outliers.add(d);
                }
            }

            if (!outliers.isEmpty()) {
                LOG.warning("Filtered " + outliers.size() + " outlier baseline scores: " + outliers
                        + ". Using " + filtered.size() + " scores for threshold.");
                distances = filtered;
            }
        }

        double medianScore = medianOf(distances);
        double percentileValue = percentile(distances, percentile);

        this.threshold = Math.min(percentileValue, MEDIAN_CAP_MULTIPLIER * medianScore);
    }

    public AnomalyThreshold(double threshold) {
        this.threshold = threshold;
    }

    public boolean isAnomaly(double score) { return score > threshold; }
    public double getThreshold() { return threshold; }

    private static double percentile(List<Double> sorted, double p) {
        int idx = (int) Math.ceil((p / 100.0) * sorted.size()) - 1;
        if (idx < 0) idx = 0;
        if (idx >= sorted.size()) idx = sorted.size() - 1;
        return sorted.get(idx);
    }

    private static double medianOf(List<Double> sorted) {
        int n = sorted.size();
        if (n % 2 == 1) {
            return sorted.get(n / 2);
        }
        return (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }
}
