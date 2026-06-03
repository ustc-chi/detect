package com.anomalydetection.optimizer;

import com.anomalydetection.features.FeatureType;
import com.anomalydetection.features.FeatureVector;

import java.util.*;

/**
 * 在线权重更新工具 — 用真实积累的 FeatureVector 重新优化权重。
 *
 * <p>设计用于生产环境定期调用（如每周一次），当积累的正常/攻击向量足够多时，
 * 重新运行随机搜索优化，输出比默认权重 AUC 更高的专属权重。
 *
 * <p>使用方式：
 * <pre>{@code
 *   List<FeatureVector> normals = db.queryNormals(resourceId);
 *   List<FeatureVector> attacks = db.queryAttacks(resourceId);
 *
 *   OnlineWeightUpdater updater = new OnlineWeightUpdater(normals, attacks);
 *   OnlineWeightUpdater.UpdateResult result = updater.optimize(
 *       AnomalyDetectionService.FALLBACK_WEIGHTS,  // 当前 Active 权重
 *       WarmupDetector.WARMUP_WEIGHTS,             // 当前 Warmup 权重
 *       10000                                      // 迭代次数
 *   );
 *
 *   if (result.activeImproved) {
 *       db.saveWeights(resourceId, "active", result.activeWeights);
 *   }
 *   if (result.warmupImproved) {
 *       db.saveWeights(resourceId, "warmup", result.warmupWeights);
 *   }
 * }</pre>
 *
 * <p>Warmup 与 Active 分开优化的原因：
 * <ul>
 *   <li>Active — 历史向量充足，直接用样本 MAD，评分稳定可靠</li>
 *   <li>Warmup — 历史向量只有 2~9 条，MAD 估计不可靠。
 *       优化时模拟小样本条件（向先验收缩 MAD），
 *       自动降低高方差特征的权重，防止小样本误报</li>
 * </ul>
 */
public class OnlineWeightUpdater {

    private final List<FeatureVector> normalVectors;
    private final List<FeatureVector> attackVectors;
    private final int featureCount;
    private final Random random;

    private static final double[] PRIOR_MEDIAN = {
        0.50, 0.15, 0.25, 12000, 3000, 0.60, 0.28, 1.20,
        40, 0.50, 0.03, 0.20, 3.50, 1.20, 1.50, 0.10
    };
    private static final double[] PRIOR_MAD = {
        0.10, 0.05, 0.08, 5000, 2000, 0.12, 0.06, 0.25,
        15, 0.12, 0.02, 0.06, 0.50, 0.15, 0.60, 0.08
    };
    private static final double PRIOR_EFFECTIVE_N = 20.0;

    public OnlineWeightUpdater(List<FeatureVector> normalVectors,
                               List<FeatureVector> attackVectors) {
        this.normalVectors = normalVectors;
        this.attackVectors = attackVectors;
        this.featureCount = FeatureType.COUNT;
        this.random = new Random();
    }

    /**
     * 执行权重优化，并与当前权重对比。
     *
     * @param currentActiveWeights  当前 ActiveDetector 使用的权重（如 FALLBACK_WEIGHTS）
     * @param currentWarmupWeights  当前 WarmupDetector 使用的权重（如 WARMUP_WEIGHTS）
     * @param iterations            随机搜索迭代次数（推荐 10000~20000）
     * @return 优化结果，包含新权重、AUC 对比和改进建议
     */
    public UpdateResult optimize(double[] currentActiveWeights,
                                 double[] currentWarmupWeights,
                                 int iterations) {
        // 1. 计算基线
        double[] median = new double[featureCount];
        double[] mad = new double[featureCount];
        computeMedianMad(normalVectors, median, mad);

        // 2. 评估当前权重的 AUC
        double currentActiveAuc = computeAuc(normalVectors, attackVectors, median, mad, currentActiveWeights);
        double currentWarmupAuc = computeAuc(normalVectors, attackVectors, median, mad, currentWarmupWeights);

        // 3. 优化 Active 权重（标准评分）
        OptimizationResult activeResult = search(median, mad, iterations, false);

        // 4. 优化 Warmup 权重（模拟小样本收缩）
        OptimizationResult warmupResult = search(median, mad, iterations, true);

        // 5. 评估新权重
        double newActiveAuc = activeResult.auc;
        double newWarmupAuc = warmupResult.auc;
        boolean activeImproved = newActiveAuc > currentActiveAuc;
        boolean warmupImproved = newWarmupAuc > currentWarmupAuc;

        // 6. 用最优权重算检出和误报
        int caught = 0, fp = 0;
        double[] best = activeResult.weights;
        double threshold = activeResult.threshold;
        if (best != null) {
            for (FeatureVector fv : attackVectors) {
                if (score(fv, median, mad, best, false) > threshold) caught++;
            }
            for (FeatureVector fv : normalVectors) {
                if (score(fv, median, mad, best, false) > threshold) fp++;
            }
        }

        return new UpdateResult(
            activeResult.weights, newActiveAuc,
            warmupResult.weights, newWarmupAuc,
            currentActiveWeights, currentActiveAuc,
            currentWarmupWeights, currentWarmupAuc,
            activeImproved, warmupImproved,
            caught, attackVectors.size(),
            fp, normalVectors.size(),
            activeResult.threshold, warmupResult.threshold
        );
    }

    // ========================================================================
    // 内部：随机搜索（Active 和 Warmup 共享同一搜索逻辑，仅评分方式不同）
    // ========================================================================

    private OptimizationResult search(double[] median, double[] mad,
                                       int iterations, boolean simulateWarmupShrinkage) {
        double[] bestWeights = null;
        double bestAuc = -1.0;
        double bestThreshold = 0.0;

        for (int i = 0; i < iterations; i++) {
            double[] candidate = sampleSimplexWeights();

            List<Double> normalScores = new ArrayList<>(normalVectors.size());
            List<Double> attackScores = new ArrayList<>(attackVectors.size());

            for (FeatureVector fv : normalVectors) {
                normalScores.add(score(fv, median, mad, candidate, simulateWarmupShrinkage));
            }
            for (FeatureVector fv : attackVectors) {
                attackScores.add(score(fv, median, mad, candidate, simulateWarmupShrinkage));
            }

            Collections.sort(normalScores);
            Collections.sort(attackScores);

            double auc = computeAUC(normalScores, attackScores);
            if (auc > bestAuc) {
                bestAuc = auc;
                bestWeights = candidate.clone();
                bestThreshold = percentile(normalScores, 97.0);
            }
        }

        return new OptimizationResult(bestWeights, bestAuc, bestThreshold);
    }

    /** 单向量评分。simulateWarmupShrinkage=true 时 MAD 向先验收缩。 */
    private double score(FeatureVector fv, double[] median, double[] mad,
                          double[] weights, boolean simulateWarmupShrinkage) {
        double[] values = fv.toArray();
        double sum = 0;
        for (int i = 0; i < featureCount; i++) {
            double effectiveMad = mad[i];
            if (simulateWarmupShrinkage) {
                // 模拟 Warmup 小样本条件：MAD 向先验收缩
                // 假设当前有 n=5 个历史向量（Warmup 中段水平）
                double shrinkage = 5.0 / (5.0 + PRIOR_EFFECTIVE_N);
                effectiveMad = shrinkage * mad[i] + (1 - shrinkage) * PRIOR_MAD[i];
            }
            double z = Math.max(-10, Math.min(10, (values[i] - median[i]) / Math.max(effectiveMad, 0.001)));
            sum += weights[i] * z * z;
        }
        return Math.sqrt(sum);
    }

    // ========================================================================
    // 评估：用指定权重计算 AUC
    // ========================================================================

    private double computeAuc(List<FeatureVector> normals, List<FeatureVector> attacks,
                               double[] median, double[] mad, double[] weights) {
        List<Double> ns = new ArrayList<>();
        List<Double> as = new ArrayList<>();
        for (FeatureVector fv : normals) ns.add(score(fv, median, mad, weights, false));
        for (FeatureVector fv : attacks) as.add(score(fv, median, mad, weights, false));
        Collections.sort(ns);
        Collections.sort(as);
        return computeAUC(ns, as);
    }

    // ========================================================================
    // Pure helpers（与 WeightOptimizer 中逻辑一致）
    // ========================================================================

    private void computeMedianMad(List<FeatureVector> vectors, double[] median, double[] mad) {
        int n = vectors.size();
        double[][] values = new double[n][featureCount];
        for (int h = 0; h < n; h++) values[h] = vectors.get(h).toArray();
        for (int i = 0; i < featureCount; i++) {
            double[] col = new double[n];
            for (int h = 0; h < n; h++) col[h] = values[h][i];
            Arrays.sort(col);
            median[i] = col[n / 2];
            double[] absDev = new double[n];
            for (int h = 0; h < n; h++) absDev[h] = Math.abs(col[h] - median[i]);
            Arrays.sort(absDev);
            mad[i] = absDev[n / 2] * 1.4826 + 0.001;
        }
    }

    private double[] sampleSimplexWeights() {
        double[] w = new double[featureCount];
        double sum = 0.0;
        for (int i = 0; i < featureCount; i++) {
            w[i] = random.nextDouble();
            sum += w[i];
        }
        for (int i = 0; i < featureCount; i++) w[i] /= sum;
        w[FeatureType.RENAME_CORRELATION.ordinal()] *= 0.3;
        return w;
    }

    static double computeAUC(List<Double> normalScores, List<Double> attackScores) {
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

    static double percentile(List<Double> sorted, double pct) {
        int n = sorted.size();
        double rank = (pct / 100.0) * (n - 1);
        int lower = (int) Math.floor(rank);
        int upper = (int) Math.ceil(rank);
        if (lower == upper) return sorted.get(lower);
        double frac = rank - lower;
        return sorted.get(lower) * (1 - frac) + sorted.get(upper) * frac;
    }

    // ========================================================================
    // 内部数据类
    // ========================================================================

    private static class OptimizationResult {
        final double[] weights;
        final double auc;
        final double threshold;

        OptimizationResult(double[] weights, double auc, double threshold) {
            this.weights = weights;
            this.auc = auc;
            this.threshold = threshold;
        }
    }

    /** 在线更新结果：包含新旧权重对比及改进建议。 */
    public static class UpdateResult {
        public final double[] activeWeights;
        public final double activeAuc;
        public final double[] warmupWeights;
        public final double warmupAuc;

        public final double[] currentActiveWeights;
        public final double currentActiveAuc;
        public final double[] currentWarmupWeights;
        public final double currentWarmupAuc;

        public final boolean activeImproved;
        public final boolean warmupImproved;

        public final int attacksCaught;
        public final int totalAttacks;
        public final int falsePositives;
        public final int totalNormals;

        public final double activeThreshold;
        public final double warmupThreshold;

        UpdateResult(double[] activeWeights, double activeAuc,
                     double[] warmupWeights, double warmupAuc,
                     double[] currentActiveWeights, double currentActiveAuc,
                     double[] currentWarmupWeights, double currentWarmupAuc,
                     boolean activeImproved, boolean warmupImproved,
                     int attacksCaught, int totalAttacks,
                     int falsePositives, int totalNormals,
                     double activeThreshold, double warmupThreshold) {
            this.activeWeights = activeWeights;
            this.activeAuc = activeAuc;
            this.warmupWeights = warmupWeights;
            this.warmupAuc = warmupAuc;
            this.currentActiveWeights = currentActiveWeights;
            this.currentActiveAuc = currentActiveAuc;
            this.currentWarmupWeights = currentWarmupWeights;
            this.currentWarmupAuc = currentWarmupAuc;
            this.activeImproved = activeImproved;
            this.warmupImproved = warmupImproved;
            this.attacksCaught = attacksCaught;
            this.totalAttacks = totalAttacks;
            this.falsePositives = falsePositives;
            this.totalNormals = totalNormals;
            this.activeThreshold = activeThreshold;
            this.warmupThreshold = warmupThreshold;
        }

        /** 生成人类可读的对比报告。 */
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Online Weight Update Report ===\n");
            sb.append(String.format("Data: %d normal, %d attack\n", totalNormals, totalAttacks));
            sb.append("\n");

            sb.append("--- Active Phase ---\n");
            sb.append(String.format("  Current AUC: %.4f\n", currentActiveAuc));
            sb.append(String.format("  New     AUC: %.4f", activeAuc));
            sb.append(activeImproved ? " ✅ improved\n" : " ❌ not improved\n");
            if (activeImproved) {
                sb.append("  Threshold:   ").append(String.format("%.4f\n", activeThreshold));
                sb.append("  Detection:   ").append(attacksCaught).append("/").append(totalAttacks).append("\n");
                sb.append("  FP:          ").append(falsePositives).append("/").append(totalNormals).append("\n");
                sb.append("  Recommended: update active weights\n");
            } else {
                sb.append("  Recommended: keep current active weights\n");
            }
            sb.append("\n");

            sb.append("--- Warmup Phase ---\n");
            sb.append(String.format("  Current AUC: %.4f\n", currentWarmupAuc));
            sb.append(String.format("  New     AUC: %.4f", warmupAuc));
            sb.append(warmupImproved ? " ✅ improved\n" : " ❌ not improved\n");
            if (warmupImproved) {
                sb.append("  Recommended: update warmup weights\n");
            } else {
                sb.append("  Recommended: keep current warmup weights\n");
            }

            if (activeImproved) {
                sb.append("\n--- Top 5 Active Weights ---\n");
                printTopWeights(sb, activeWeights);
            }
            if (warmupImproved) {
                sb.append("\n--- Top 5 Warmup Weights ---\n");
                printTopWeights(sb, warmupWeights);
            }

            return sb.toString();
        }

        private void printTopWeights(StringBuilder sb, double[] weights) {
            Integer[] idx = new Integer[weights.length];
            for (int i = 0; i < weights.length; i++) idx[i] = i;
            Arrays.sort(idx, (a, b) -> Double.compare(weights[b], weights[a]));
            for (int r = 0; r < Math.min(5, weights.length); r++) {
                int i = idx[r];
                sb.append(String.format("  [%d] %s = %.4f\n",
                        i, FeatureType.values()[i].key(), weights[i]));
            }
        }
    }
}
