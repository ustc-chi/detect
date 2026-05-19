package com.anomalydetection.detector;

import com.anomalydetection.features.RansomwareFeatureVector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class WeightOptimizer {

    private final List<RansomwareFeatureVector> normalVectors;
    private final List<RansomwareFeatureVector> attackVectors;
    private final int featureCount;
    private final Random random;

    public WeightOptimizer(List<RansomwareFeatureVector> normalVectors,
                           List<RansomwareFeatureVector> attackVectors) {
        this.normalVectors = normalVectors;
        this.attackVectors = attackVectors;
        this.featureCount = RansomwareFeatureVector.FEATURE_COUNT;
        this.random = new Random(42);
    }

    public OptimizationResult optimize(int iterations) {
        return optimize(iterations, 97.0);
    }

    public OptimizationResult optimize(int iterations, double targetPercentile) {
        double[] bestWeights = null;
        double bestScore = -1.0;
        double bestThreshold = 0.0;

        for (int i = 0; i < iterations; i++) {
            double[] candidate = sampleSimplexWeights();

            BaselineStatistics stats = new BaselineStatistics(normalVectors);
            WeightedEuclideanScorer scorer = new WeightedEuclideanScorer(stats, candidate);

            List<Double> normalScores = new ArrayList<>();
            for (RansomwareFeatureVector v : normalVectors) {
                normalScores.add(scorer.score(v));
            }

            List<Double> attackScores = new ArrayList<>();
            for (RansomwareFeatureVector v : attackVectors) {
                attackScores.add(scorer.score(v));
            }

            double auc = computeAUC(normalScores, attackScores);
            if (auc > bestScore) {
                bestScore = auc;
                bestWeights = candidate.clone();
                bestThreshold = percentile(normalScores, targetPercentile);
            }
        }

        int caught = 0;
        int fpCount = 0;
        if (bestWeights != null) {
            BaselineStatistics stats = new BaselineStatistics(normalVectors);
            WeightedEuclideanScorer scorer = new WeightedEuclideanScorer(stats, bestWeights);
            for (RansomwareFeatureVector v : attackVectors) {
                if (scorer.score(v) > bestThreshold) caught++;
            }
            for (RansomwareFeatureVector v : normalVectors) {
                if (scorer.score(v) > bestThreshold) fpCount++;
            }
        }

        return new OptimizationResult(bestWeights, bestScore, bestThreshold,
                caught, attackVectors.size(), fpCount, normalVectors.size());
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
            sb.append("weights=");
            String[] names = RansomwareFeatureVector.FEATURE_NAMES;
            for (int i = 0; i < weights.length; i++) {
                sb.append(String.format("\n  %s=%.4f", names[i], weights[i]));
            }
            return sb.toString();
        }
    }
}
