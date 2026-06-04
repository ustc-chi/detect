package com.anomalydetection.optimizer;

import com.anomalydetection.detector.*;
import com.anomalydetection.detector.heuristic.*;
import com.anomalydetection.features.FeatureType;
import com.anomalydetection.features.FeatureVector;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Warmup 效果验证模拟器。
 *
 * <p>Warmup 阶段的特殊性在于历史向量极少（2~9 条），
 * 传统的一次性 AUC 评估无法反映真实行为。
 * 本工具模拟 Warmup 的渐进过程：
 * 每次加入 1 个正常向量后，对所有攻击向量重新判定一次，
 * 记录检出率随样本量的变化曲线。
 */
public class WarmupValidator {

    private final List<FeatureVector> normalPool;
    private final List<FeatureVector> attackPool;
    private final int sensitivity;

    public WarmupValidator(List<FeatureVector> normalPool,
                           List<FeatureVector> attackPool,
                           int sensitivity) {
        this.normalPool = normalPool;
        this.attackPool = attackPool;
        this.sensitivity = sensitivity;
    }

    /**
     * 运行完整 Warmup 模拟。
     *
     * @param startSize  从几个正常向量开始（推荐 2）
     * @param endSize    到几个正常向量结束（推荐 10）
     * @return 逐阶段的检出率和误报记录
     */
    public ValidationResult simulate(int startSize, int endSize) {
        List<StageResult> stages = new ArrayList<>();
        WarmupDetector detector = new WarmupDetector();

        // 渐进加入正常向量，每一步都重新判定全部攻击
        List<FeatureVector> history = new ArrayList<>();
        for (int step = 0; step < endSize && step < normalPool.size(); step++) {
            history.add(normalPool.get(step));

            if (step + 1 < startSize) continue; // 不到起始样本量，跳过

            // 检查攻击检出
            int caught = 0;
            for (FeatureVector attack : attackPool) {
                WarmupDetector.WarmupDetectionResult r = detector.detect(attack, history, sensitivity);
                if (r.isAnomaly()) caught++;
            }

            // 检查正常误报（用当前未加入历史的正常向量做验证）
            int fp = 0;
            int fpTotal = 0;
            for (int i = step + 1; i < normalPool.size(); i++) {
                fpTotal++;
                WarmupDetector.WarmupDetectionResult r = detector.detect(normalPool.get(i), history, sensitivity);
                if (r.isAnomaly()) fp++;
            }

            // 记录是哪一层触发的
            int layer2Count = 0;
            int layer3Count = 0;
            for (FeatureVector attack : attackPool) {
                WarmupDetector.WarmupDetectionResult r = detector.detect(attack, history, sensitivity);
                if (r.isAnomaly()) {
                    if (r.getLayer() == 2) layer2Count++;
                    else layer3Count++;
                }
            }

            stages.add(new StageResult(
                    step + 1,            // 当前样本量
                    caught, attackPool.size(),
                    fp, fpTotal,
                    layer2Count, layer3Count
            ));
        }

        return new ValidationResult(stages, sensitivity,
                normalPool.size(), attackPool.size());
    }

    // ========================================================================
    // 数据类
    // ========================================================================

    /** 单一阶段的 Warmup 结果：在 N 个历史向量下的检出/误报 */
    public static class StageResult {
        public final int historySize;
        public final int attacksCaught;
        public final int totalAttacks;
        public final int falsePositives;
        public final int totalValidationNormals;
        public final int layer2Triggers;
        public final int layer3Triggers;

        public StageResult(int historySize, int attacksCaught, int totalAttacks,
                           int falsePositives, int totalValidationNormals,
                           int layer2Triggers, int layer3Triggers) {
            this.historySize = historySize;
            this.attacksCaught = attacksCaught;
            this.totalAttacks = totalAttacks;
            this.falsePositives = falsePositives;
            this.totalValidationNormals = totalValidationNormals;
            this.layer2Triggers = layer2Triggers;
            this.layer3Triggers = layer3Triggers;
        }

        public double detectionRate() {
            return (double) attacksCaught / totalAttacks;
        }

        public double fpRate() {
            return totalValidationNormals == 0 ? 0 :
                    (double) falsePositives / totalValidationNormals;
        }
    }

    /** 完整 Warmup 模拟结果 */
    public static class ValidationResult {
        public final List<StageResult> stages;
        public final double sensitivity;
        public final int totalNormals;
        public final int totalAttacks;

        ValidationResult(List<StageResult> stages, double sensitivity,
                         int totalNormals, int totalAttacks) {
            this.stages = stages;
            this.sensitivity = sensitivity;
            this.totalNormals = totalNormals;
            this.totalAttacks = totalAttacks;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Warmup Validation Report ===\n");
            sb.append(String.format("Total normals: %d, attacks: %d, sensitivity: %.1f\n",
                    totalNormals, totalAttacks, sensitivity));
            sb.append("\n");
            sb.append(String.format("%-12s %-12s %-12s %-12s %-12s %-12s\n",
                    "HistoryN", "Detection", "FP", "L2 Trig", "L3 Trig", "Note"));
            sb.append("-".repeat(72)).append("\n");

            for (StageResult s : stages) {
                String note;
                if (s.totalAttacks > 0 && s.detectionRate() >= 0.90) {
                    note = s.fpRate() <= 0.05 ? "✅ GOOD" : "⚠️ HIGH FP";
                } else if (s.totalAttacks > 0 && s.detectionRate() >= 0.70) {
                    note = "🟡 FAIR";
                } else {
                    note = "❌ POOR";
                }

                sb.append(String.format("%-12d %-12s %-12s %-12d %-12d %s\n",
                        s.historySize,
                        s.attacksCaught + "/" + s.totalAttacks,
                        s.falsePositives + "/" + s.totalValidationNormals,
                        s.layer2Triggers,
                        s.layer3Triggers,
                        note));
            }

            sb.append("\n");
            sb.append("Legend:\n");
            sb.append("  HistoryN   = number of normal vectors accumulated\n");
            sb.append("  Detection  = attacks caught / total attacks\n");
            sb.append("  FP         = false positives / validation normals\n");
            sb.append("  L2 Trig    = alarms triggered by heuristic rules (Layer 2)\n");
            sb.append("  L3 Trig    = alarms triggered by statistical detection (Layer 3)\n");

            return sb.toString();
        }
    }
}
