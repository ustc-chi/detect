package com.anomalydetection.optimizer;

import com.anomalydetection.detector.ActiveDetector;
import com.anomalydetection.detector.BaselineStatsDTO;
import com.anomalydetection.features.FeatureType;
import com.anomalydetection.features.FeatureVector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Weight optimizer using random search on the 16-dim feature space.
 * <p>
 * Uses ActiveDetector internally for scoring, searches for the weight combination
 * that maximizes AUC (normal vs. attack separation) on historical data.
 */
public class WeightOptimizer {

    private final List<FeatureVector> normalVectors;
    private final List<FeatureVector> attackVectors;
    private final int featureCount;
    private final Random random;

    public WeightOptimizer(List<FeatureVector> normalVectors,
                           List<FeatureVector> attackVectors) {
        this.normalVectors = normalVectors;
        this.attackVectors = attackVectors;
        this.featureCount = FeatureType.COUNT;
        this.random = new Random(42);
    }

    public OptimizationResult optimize(int iterations) {
        return optimize(iterations, 97.0);
    }

    public OptimizationResult optimize(int iterations, double targetPercentile) {
        // Compute baseline statistics from normal vectors
        double[] median = new double[featureCount];
        double[] mad = new double[featureCount];
        computeMedianMad(normalVectors, median, mad);

        // We'll use a fixed threshold based on target percentile of normal scores
        double[] bestWeights = null;
        double bestAuc = -1.0;
        double bestThreshold = 0.0;

        for (int i = 0; i < iterations; i++) {
            double[] candidate = sampleSimplexWeights();

            // Score all normal and attack vectors
            List<Double> normalScores = scoreVectors(normalVectors, median, mad, candidate);
            List<Double> attackScores = scoreVectors(attackVectors, median, mad, candidate);

            double auc = computeAUC(normalScores, attackScores);
            if (auc > bestAuc) {
                bestAuc = auc;
                bestWeights = candidate.clone();
                bestThreshold = percentile(normalScores, targetPercentile);
            }
        }

        // Evaluate final result
        int caught = 0;
        int fpCount = 0;
        if (bestWeights != null) {
            List<Double> normalScores = scoreVectors(normalVectors, median, mad, bestWeights);
            List<Double> attackScores = scoreVectors(attackVectors, median, mad, bestWeights);
            for (double s : attackScores) {
                if (s > bestThreshold) caught++;
            }
            for (double s : normalScores) {
                if (s > bestThreshold) fpCount++;
            }
        }

        return new OptimizationResult(bestWeights, bestAuc, bestThreshold,
                caught, attackVectors.size(), fpCount, normalVectors.size());
    }

    // =====================================================================
    // Private helpers
    // =====================================================================

    private void computeMedianMad(List<FeatureVector> vectors, double[] median, double[] mad) {
        int n = vectors.size();
        double[][] values = new double[n][featureCount];
        for (int h = 0; h < n; h++) {
            values[h] = vectors.get(h).toArray();
        }

        for (int i = 0; i < featureCount; i++) {
            double[] col = new double[n];
            for (int h = 0; h < n; h++) col[h] = values[h][i];
            java.util.Arrays.sort(col);
            median[i] = col[n / 2];
            double[] absDev = new double[n];
            for (int h = 0; h < n; h++) absDev[h] = Math.abs(col[h] - median[i]);
            java.util.Arrays.sort(absDev);
            mad[i] = absDev[n / 2] * 1.4826 + 0.001;
        }
    }

    private List<Double> scoreVectors(List<FeatureVector> vectors, double[] median,
                                       double[] mad, double[] weights) {
        List<Double> scores = new ArrayList<>(vectors.size());
        for (FeatureVector fv : vectors) {
            double[] values = fv.toArray();
            double sum = 0;
            for (int i = 0; i < featureCount; i++) {
                double z = Math.max(-10, Math.min(10, (values[i] - median[i]) / mad[i]));
                sum += weights[i] * z * z;
            }
            scores.add(Math.sqrt(sum));
        }
        Collections.sort(scores);
        return scores;
    }

    double[] sampleSimplexWeights() {
        double[] w = new double[featureCount];
        double sum = 0.0;
        for (int i = 0; i < featureCount; i++) {
            w[i] = random.nextDouble();
            sum += w[i];
        }
        for (int i = 0; i < featureCount; i++) {
            w[i] /= sum;
        }
        // Constrain F10 (rename_correlation): known false-positive risk,
        // cap its weight at 30% of its sampled value.
        w[FeatureType.RENAME_CORRELATION.ordinal()] *= 0.3;
        return w;
    }

    double computeAUC(List<Double> normalScores, List<Double> attackScores) {
        int correct = 0;
        int total = 0;
        for (double ns : normalScores) {
            for (double as : attackScores) {
                if (as > ns) correct++;
                else if (as == ns) correct += 0.5;
                total++;
            }
        }
        return total == 0 ? 0.0 : (double) correct / total;
    }

    double percentile(List<Double> values, double pct) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int n = sorted.size();
        double rank = (pct / 100.0) * (n - 1);
        int lower = (int) Math.floor(rank);
        int upper = (int) Math.ceil(rank);
        if (lower == upper) return sorted.get(lower);
        double frac = rank - lower;
        return sorted.get(lower) * (1 - frac) + sorted.get(upper) * frac;
    }

    // =====================================================================
    // Nested: OptimizationResult
    // =====================================================================

    public static class OptimizationResult {
        public final double[] weights;
        public final double auc;
        public final double threshold;
        public final int attacksCaught;
        public final int totalAttacks;
        public final int falsePositives;
        public final int totalNormals;

        public OptimizationResult(double[] weights, double auc, double threshold,
                                  int attacksCaught, int totalAttacks,
                                  int falsePositives, int totalNormals) {
            this.weights = weights;
            this.auc = auc;
            this.threshold = threshold;
            this.attacksCaught = attacksCaught;
            this.totalAttacks = totalAttacks;
            this.falsePositives = falsePositives;
            this.totalNormals = totalNormals;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("AUC=%.4f caught=%d/%d FP=%d/%d threshold=%.4f\n",
                    auc, attacksCaught, totalAttacks, falsePositives, totalNormals, threshold));
            if (weights != null) {
                sb.append("Weights:\n");
                for (int i = 0; i < weights.length; i++) {
                    sb.append(String.format("  [%d] %s = %.4f\n",
                            i, FeatureType.values()[i].key(), weights[i]));
                }
            }
            return sb.toString();
        }
    }
}
