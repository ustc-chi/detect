package com.anomalydetection.detector.v2;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * N-dimensional feature vector for ransomware anomaly detection.
 * <p>
 * Each dimension can carry supplementary data (e.g., time windows, matched extension names)
 * via the supplementaryData map, keyed by dimension index.
 */
public final class FeatureVector {

    public static final int FEATURE_COUNT = 14;

    public static final String[] FEATURE_NAMES = {
        "total_operations", "modification_ratio", "deletion_intensity",
        "directory_spread", "extension_diversity", "suspicious_extension_ratio",
        "peak_burst_velocity", "avg_modified_size", "size_std_dev",
        "high_value_ext_ratio", "burst_mod_purity", "file_type_concentration",
        "size_change_kurtosis", "inter_op_time_cv"
    };

    public static final String[] FEATURE_DESCRIPTIONS = {
        "每日标准化操作总数, 按快照间隔天数归一化",
        "修改操作占总操作的比例, 正常业务通常在30-70%",
        "删除强度综合得分, log1p(Σ size(deleted)/days) × (deletedCount/totalOps)",
        "目录扩散广度, 不同父目录数量除以快照间隔天数",
        "扩展名多样性, 不同扩展名数量除以快照间隔天数",
        "可疑扩展名占比, 命中已知勒索软件扩展名的比例（出现即高度可疑）",
        "5分钟滑动窗口内最大操作速率, 以 ops/hour 为单位",
        "修改文件平均大小的 log1p 值",
        "修改文件大小的标准差(log1p空间), 降低可能表示加密导致分布均匀化（反向信号）",
        "高价值文件(文档/数据库等)操作占总操作的比例",
        "突发窗口内修改操作纯度, 高值表示突发中几乎全是修改（加密特征）",
        "文件类型集中度, 单种扩展名在修改操作中的最高占比",
        "修改文件大小变化的 excess kurtosis, 加密导致分布更尖峰",
        "操作时间间隔变异系数 CV = σ/μ, 低值表示规律间隔（自动化/加密特征）"
    };

    public static final String[] FEATURE_UNITS = {
        "ops/day", "ratio", "score", "dirs/day", "exts/day", "ratio",
        "ops/hour", "log(bytes)", "log(bytes)", "ratio", "ratio", "ratio", "score", "CV"
    };

    private final double[] values;
    private final Map<Integer, Map<String, Object>> supplementaryData;

    public FeatureVector(double[] values, Map<Integer, Map<String, Object>> supplementaryData) {
        Objects.requireNonNull(values, "values must not be null");
        if (values.length != FEATURE_COUNT) {
            throw new IllegalArgumentException("values must have length " + FEATURE_COUNT + ", got " + values.length);
        }
        this.values = values.clone();
        this.supplementaryData = (supplementaryData == null || supplementaryData.isEmpty())
                ? Collections.emptyMap() : new HashMap<>(supplementaryData);
    }

    public FeatureVector(double[] values) {
        this(values, Collections.emptyMap());
    }

    public double getTotalOperations() { return values[0]; }
    public double getModificationRatio() { return values[1]; }
    public double getDeletionIntensity() { return values[2]; }
    public double getDirectorySpread() { return values[3]; }
    public double getExtensionDiversity() { return values[4]; }
    public double getSuspiciousExtensionRatio() { return values[5]; }
    public double getPeakBurstVelocity() { return values[6]; }
    public double getAvgModifiedSize() { return values[7]; }
    public double getSizeStdDev() { return values[8]; }
    public double getHighValueExtRatio() { return values[9]; }
    public double getBurstModPurity() { return values[10]; }
    public double getFileTypeConcentration() { return values[11]; }
    public double getSizeChangeKurtosis() { return values[12]; }
    public double getInterOpTimeCv() { return values[13]; }

    public double get(int index) {
        if (index < 0 || index >= FEATURE_COUNT) {
            throw new IllegalArgumentException("index must be 0-" + (FEATURE_COUNT - 1) + ", got " + index);
        }
        return values[index];
    }

    public double[] toArray() { return values.clone(); }
    public Map<String, Object> getSupplementary(int index) {
        return supplementaryData.getOrDefault(index, Collections.emptyMap());
    }
    public boolean hasSupplementary(int index) { return supplementaryData.containsKey(index); }
    public Map<Integer, Map<String, Object>> getAllSupplementary() {
        return Collections.unmodifiableMap(supplementaryData);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FeatureVector)) return false;
        return Arrays.equals(values, ((FeatureVector) o).values);
    }

    @Override
    public int hashCode() { return Arrays.hashCode(values); }
}
