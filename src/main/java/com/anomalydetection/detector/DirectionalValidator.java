package com.anomalydetection.detector;

import com.anomalydetection.features.RansomwareFeatureVector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DirectionalValidator {

    private final double[] weights;
    private final double threshold;

    public DirectionalValidator(double[] weights, double threshold) {
        if (weights == null || weights.length != RansomwareFeatureVector.FEATURE_COUNT) {
            throw new IllegalArgumentException("weights must have " + RansomwareFeatureVector.FEATURE_COUNT + " elements");
        }
        this.weights = weights;
        this.threshold = threshold;
    }

    public static class FeatureDeviation {
        private final String name;
        private final double zScore;
        private final String direction;

        public FeatureDeviation(String name, double zScore, String direction) {
            this.name = name;
            this.zScore = zScore;
            this.direction = direction;
        }

        public String name() { return name; }
        public double zScore() { return zScore; }
        public String direction() { return direction; }
    }

    public static class ValidationResult {
        public final boolean reversed;
        public final double ratio;
        public final double eUp;
        public final double eDown;
        public final List<FeatureDeviation> topDeviations;

        public ValidationResult(boolean reversed, double ratio, double eUp, double eDown,
                                List<FeatureDeviation> topDeviations) {
            this.reversed = reversed;
            this.ratio = ratio;
            this.eUp = eUp;
            this.eDown = eDown;
            this.topDeviations = topDeviations;
        }
    }

    public ValidationResult validate(double[] zScores) {
        if (zScores == null || zScores.length != weights.length) {
            throw new IllegalArgumentException("zScores must have " + weights.length + " elements");
        }

        if (threshold == 0) {
            return new ValidationResult(false, 0, 0, 0, computeTopDeviations(zScores, 5));
        }

        double eUp = 0;
        double eDown = 0;
        for (int i = 0; i < weights.length; i++) {
            double z = zScores[i];
            if (z > 0) {
                eUp += weights[i] * z * z;
            } else if (z < 0) {
                eDown += weights[i] * z * z;
            }
        }

        double ratio = eDown / (eUp + eDown + 1e-10);
        boolean reversed = ratio > threshold;

        return new ValidationResult(reversed, ratio, eUp, eDown, computeTopDeviations(zScores, 5));
    }

    private List<FeatureDeviation> computeTopDeviations(double[] zScores, int n) {
        List<FeatureDeviation> all = new ArrayList<>();
        for (int i = 0; i < zScores.length; i++) {
            String name = RansomwareFeatureVector.FEATURE_NAMES[i];
            double z = zScores[i];
            String direction = z >= 0 ? "ABOVE" : "BELOW";
            all.add(new FeatureDeviation(name, z, direction));
        }

        all.sort((a, b) -> Double.compare(Math.abs(b.zScore()), Math.abs(a.zScore())));

        int limit = Math.min(n, all.size());
        return new ArrayList<>(all.subList(0, limit));
    }
}
