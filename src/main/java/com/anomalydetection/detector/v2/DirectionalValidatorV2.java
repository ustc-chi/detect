package com.anomalydetection.detector.v2;

import com.anomalydetection.features.RansomwareFeatureVector;

import java.util.ArrayList;
import java.util.List;

/**
 * Directional validator for quiet-day reversal detection (14-dim version).
 * <p>
 * Separates weighted z-score energy into upward (positive z) and downward (negative z)
 * components. When downward energy dominates beyond the configured threshold ratio,
 * the anomaly is likely a "quiet day" and should be reversed to normal.
 */
public class DirectionalValidatorV2 {

    private final double[] weights;
    private final double threshold;

    /**
     * A single feature deviation entry for reporting.
     */
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

    /**
     * Validation result containing energy split and reversal decision.
     */
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

    public DirectionalValidatorV2(double[] weights, double threshold) {
        if (weights == null || weights.length != FeatureVector14.FEATURE_COUNT) {
            throw new IllegalArgumentException(
                    "weights must have " + FeatureVector14.FEATURE_COUNT + " elements");
        }
        this.weights = weights.clone();
        this.threshold = threshold;
    }

    /**
     * Validate direction of z-scores.
     *
     * @param zScores array of 14 z-scores
     * @return validation result with energy split and reversal decision
     */
    public ValidationResult validate(double[] zScores) {
        if (zScores == null || zScores.length != weights.length) {
            throw new IllegalArgumentException(
                    "zScores must have " + weights.length + " elements");
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
        List<FeatureDeviation> devs = new ArrayList<>();
        for (int i = 0; i < zScores.length; i++) {
            if (i < FeatureVector14.FEATURE_NAMES.length) {
                String dir = zScores[i] > 0 ? "up" : (zScores[i] < 0 ? "down" : "flat");
                devs.add(new FeatureDeviation(FeatureVector14.FEATURE_NAMES[i], zScores[i], dir));
            }
        }
        devs.sort((a, b) -> Double.compare(Math.abs(b.zScore()), Math.abs(a.zScore())));
        if (n <= 0 || n >= devs.size()) return devs;
        return devs.subList(0, n);
    }
}
