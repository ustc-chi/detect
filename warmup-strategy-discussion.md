# Warmup 阶段异常检测策略讨论记录

## 日期：2026-05-14
## 状态：进行中 - 待进一步讨论
## 最新更新：2026-05-14（当前会话）

---

## 一、项目背景

### 1.1 真实场景
- **资源**：某一资源下有多个副本
- **检测对象**：每个副本与前一个副本的 diff
- **特征提取**：从 diff 中提取 14 个特征，组成特征向量
- **检测逻辑**：根据已记录的正常/异常特征计算 MAD 和 threshold，再计算欧氏距离并与 threshold 比较

### 1.2 两阶段流程
```
副本1 → 副本2 → ... → 副本10 → 副本11 → ...
  │                    │         │
  └──── Warmup 阶段 ────┘         │
       (学习+判定)                │
                                  ▼
                          Active 阶段
                      (成熟基线+正式检测)
```

### 1.3 关键约束
- Warmup 阶段（前10轮）：没有成熟基线，需要边学习边判定
- 各资源 ID 独立，互不影响
- 可疑样本也会记录到数据库，后续可能用于权重调整
- 希望减少误报，但数据量不足时难以做到完全准确

---

## 二、14维特征深度分析

### 2.1 特征分类矩阵

| 类别 | 特征 | 权重 | Warmup可用性 | 判定方式 |
|------|------|------|-------------|----------|
| **🔴 确定性规则** | suspicious_extension_ratio | 10.0 | ✅ 绝对可用 | 阈值 > 0 |
| **🟡 强启发式** | modification_ratio, burst_mod_purity, file_type_concentration | 3.0 | ✅ 单轮可用 | 绝对阈值 |
| **🟠 中等启发式** | total_operations, peak_burst_velocity, inter_op_time_cv | 2.0-3.5 | ⚠️ 需2-3轮 | 相对历史 |
| **🔵 统计特征** | size_std_dev, size_change_kurtosis, avg_modified_size | 1.0-2.0 | ❌ 需5轮+ | 需稳定基线 |
| **🟢 辅助特征** | deletion_intensity, directory_spread, extension_diversity, high_value_ext_ratio | 0.5-2.5 | ⚠️ 需3-5轮 | 相对历史 |

### 2.2 各特征详细说明

#### F0: total_operations（总操作数/天）
- **计算**：`count(records) / daysBetweenSnapshots`
- **检测意义**：勒索软件加密大量文件时，操作总数会激增
- **Warmup 策略**：
  - 绝对阈值：> 10000 为可疑
  - 相对历史（需2轮+）：超过历史最大值 5 倍为异常

#### F1: modification_ratio（修改比例）
- **计算**：`count(type="modified") / total_operations`
- **检测意义**：加密型勒索软件几乎全是修改操作，比值接近 1.0
- **Warmup 策略**：
  - 绝对阈值：> 0.90 且操作数 > 100 为异常
  - 正常业务修改比例通常在 30-70%

#### F2: deletion_intensity（删除强度）
- **计算**：`log1p(Σ size(deleted) / days) × (deletedCount / totalOps)`
- **检测意义**：破坏型勒索软件（Ryuk、wiper）的特征
- **Warmup 策略**：
  - 绝对阈值：> 5.0 为可疑
  - 权重较低（0.5），因为此攻击模式相对少见

#### F3: directory_spread（目录扩散）
- **计算**：`|{ grandparent_path }| / daysBetweenSnapshots`
- **检测意义**：勒索软件横向遍历多个目录，正常活动集中在少数目录
- **Warmup 策略**：
  - 相对历史（需3轮+）：超过历史 median 5 倍为可疑

#### F4: extension_diversity（扩展名多样性）
- **计算**：`|{ ext }| / daysBetweenSnapshots`
- **检测意义**：加密后扩展名要么引入新类型，要么统一为单一类型
- **Warmup 策略**：
  - 相对历史（需3轮+）：降至历史 median 20% 以下为可疑

#### F5: suspicious_extension_ratio（可疑扩展名比例）⭐
- **计算**：`|{ ext ∈ SuspiciousList }| / extension_diversity`
- **检测意义**：直接确定性信号，出现 `.locked`、`.encrypted` 等几乎确认攻击
- **Warmup 策略**：
  - **绝对规则**：> 0 即为异常，confidence = 1.0
  - 权重最高（10.0），签名预检阶段优先检测

#### F6: peak_burst_velocity（突发速率）
- **计算**：`max_{window=5min}(ops_in_window) / (300/3600)`
- **检测意义**：勒索软件短时间突发加密数百到数千文件
- **Warmup 策略**：
  - 绝对阈值：> 1000 ops/hour 为可疑
  - 相对历史（需3轮+）：超过历史 median 10 倍为异常

#### F7: avg_modified_size（平均修改大小）
- **计算**：`log1p(Σ size(modified) / count(modified))`
- **检测意义**：加密导致文件大小一致性变化（+2-5%）
- **Warmup 策略**：
  - 相对历史（需3轮+）：偏离历史 median 3 倍为可疑
  - **Warmup 阶段建议禁用**（需稳定基线）

#### F8: size_std_dev（大小标准差）
- **计算**：`σ(log1p(size_i))` for modified files
- **检测意义**：**反向信号** - 加密使大小分布均匀，标准差降低
- **Warmup 策略**：
  - 相对历史（需5轮+）：降至历史 median 30% 以下为可疑
  - **Warmup 阶段建议禁用**（需稳定基线）

#### F9: high_value_ext_ratio（高价值文件比例）
- **计算**：`count(ext ∈ HVList) / total_operations`
- **检测意义**：勒索软件优先加密文档、数据库等高价值文件
- **Warmup 策略**：
  - 绝对阈值：> 0.8 且操作数 > 100 为可疑

#### F10: burst_mod_purity（突发修改纯度）
- **计算**：`count(type="modified" ∧ time ∈ burst_window) / ops_in_burst_window`
- **检测意义**：突发窗口内修改操作的比例，勒索软件接近 1.0
- **Warmup 策略**：
  - 绝对阈值：> 0.95 且突发速度 > 50 为异常

#### F11: file_type_concentration（文件类型集中度）
- **计算**：`max_{ext}(count(ext | type="modified")) / count(type="modified")`
- **检测意义**：勒索软件集中攻击特定类型，某一种扩展名占极高比例
- **Warmup 策略**：
  - 绝对阈值：> 0.90 且修改数 > 100 为异常

#### F12: size_change_kurtosis（大小变化峰度）
- **计算**：excess kurtosis of `log1p(size_i)` for modified files
- **检测意义**：加密使大小分布更尖峰（高峰度）
- **Warmup 策略**：
  - 相对历史（需8轮+）：超过历史 median 3 倍为可疑
  - **Warmup 阶段建议禁用**（需稳定基线）

#### F13: inter_op_time_cv（操作时间间隔变异系数）
- **计算**：`σ(deltas) / μ(deltas)` where deltas = consecutive time differences
- **检测意义**：勒索软件间隔规律（低 CV），正常用户间隔不规则（高 CV）
- **Warmup 策略**：
  - 绝对阈值：< 0.05 且操作数 > 50 为异常
  - < 0.1 且操作数 > 100 为可疑

---

## 三、Warmup 阶段检测策略

### 3.1 核心挑战
1. **基线不成熟**：只有 1-9 个样本，MAD/中位数不稳定
2. **没有历史参照**：无法计算"偏离正常多远"
3. **不能全信**：如果 warmup 阶段有攻击，会污染基线
4. **不能全放行**：如果前 10 轮真有攻击，必须检出

### 3.2 分层防御策略

```
┌─────────────────────────────────────────────────────────────┐
│                    Warmup 阶段检测流程                        │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  第1层: 确定性规则 (绝对可信)                                │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ • 可疑扩展名 (suspicious_extension_ratio > 0)        │   │
│  │ • 勒索信文件 (README_UNLOCK, HOW_TO_DECRYPT 等)      │   │
│  │ • 极端操作量 (total_operations > 历史最大值 × 10)     │   │
│  │                                                     │   │
│  │ 命中 → 立即异常 + 标记为"攻击"（不纳入基线）          │   │
│  └─────────────────────────────────────────────────────┘   │
│                          │                                  │
│                          ▼                                  │
│  第2层: 统计异常 (相对可信，随轮次增强)                       │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ • 基于当前积累的特征计算 MAD/中位数                   │   │
│  │ • 计算加权欧氏距离                                   │   │
│  │ • 使用动态阈值（随样本量增加而收紧）                   │   │
│  │                                                     │   │
│  │ 命中 → 异常 + 标记为"可疑"（不纳入基线，人工审核）    │   │
│  └─────────────────────────────────────────────────────┘   │
│                          │                                  │
│                          ▼                                  │
│  第3层: 正常轮次                                          │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ • 未命中任何规则                                     │   │
│  │ • 纳入基线窗口                                       │   │
│  │ • 继续积累统计量                                     │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 3.3 动态阈值策略

```java
// 样本量越少，阈值越宽松
private double getDynamicThresholdMultiplier() {
    if (history.size() < 3) return 5.0;   // 前3轮：非常宽松
    if (history.size() < 5) return 3.0;   // 4-5轮：宽松
    if (history.size() < 8) return 2.0;   // 6-8轮：中等
    return 1.5;                            // 9-10轮：接近正常
}
```

### 3.4 Warmup 阶段权重调整

| 特征 | 正常权重 | Warmup权重 | 调整原因 |
|------|---------|-----------|---------|
| suspicious_extension_ratio | 10.0 | **15.0** | 确定性信号，提高 |
| modification_ratio | 3.0 | **5.0** | 单轮可用，提高 |
| burst_mod_purity | 3.0 | **5.0** | 单轮可用，提高 |
| file_type_concentration | 2.0 | **3.0** | 单轮可用，提高 |
| inter_op_time_cv | 2.5 | **3.0** | 单轮可用，提高 |
| total_operations | 2.0 | **1.0** | 需历史，降低 |
| peak_burst_velocity | 3.5 | **2.0** | 需历史，降低 |
| avg_modified_size | 1.5 | **0.0** | 需稳定基线，禁用 |
| size_std_dev | 1.0 | **0.0** | 需稳定基线，禁用 |
| size_change_kurtosis | 2.0 | **0.0** | 需稳定基线，禁用 |

### 3.5 基线积累决策树

```
新样本到达
    │
    ▼
┌─────────────────┐
│ 命中确定性规则？ │──Yes──→ 标记异常，不纳入基线
│ (扩展名/勒索信)  │
└─────────────────┘
    │ No
    ▼
┌─────────────────┐
│ 命中统计异常？   │──Yes──→ 标记可疑，不纳入基线
│ (动态阈值)       │        （人工审核后决定是否纳入）
└─────────────────┘
    │ No
    ▼
┌─────────────────┐
│ 正常样本         │──→ 纳入基线窗口
└─────────────────┘
```

---

## 四、待讨论问题

### 4.1 已确认
- [x] Warmup 长度：暂定 10 轮
- [x] 可疑样本处理：记录到数据库，后续可能用于权重调整
- [x] 误报容忍：希望减少误报，但数据量不足时难以完全准确
- [x] 多资源独立性：各资源 ID 独立，互不影响

### 4.2 待确认
- [ ] **"可疑"样本的后续处理**：
  - 是自动隔离等待人工审核？
  - 还是继续业务但标记风险？
  - 人工审核后，如果确认是正常，是否回补到基线？

- [ ] **Warmup 长度动态调整**：
  - 如果前 5 轮都是正常的，是否可以提前进入 Active？
  - 如果前 5 轮有异常，是否延长 warmup？

- [ ] **多资源配置共享**：
  - 每个资源独立 warmup，但是否可以共享"确定性规则"的配置？
  - 不同资源的正常行为差异大吗？

- [ ] **统计特征禁用策略**：
  - size_std_dev、size_change_kurtosis、avg_modified_size 在 warmup 阶段完全禁用？
  - 还是降低权重而非完全禁用？

- [ ] **动态阈值系数调优**：
  - 当前系数：2轮=5.0, 4轮=3.0, 6轮=2.0, 9轮=1.5
  - 是否需要根据实际数据表现调整？

---

## 五、后续行动计划

1. **确认待讨论问题**（4.2 节）
2. **设计 WarmupDetector 类**的详细实现
3. **与现有 RansomwareDetector 集成**
4. **编写单元测试**验证 warmup 阶段的各种场景
5. **实际数据验证**动态阈值和权重调整的效果

---

## 六、参考文档

- 原始代码仓库：`C:\Users\17762\Desktop\anti\anomaly-detection`
- 核心类：
  - `RansomwareDetector.java` - 主检测器
  - `RansomwareFeatureExtractor.java` - 特征提取
  - `BaselineStatistics.java` - 基线统计
  - `WeightedEuclideanScorer.java` - 加权欧氏距离评分
  - `RansomwareSignatureDetector.java` - 签名检测

---

---

## 七、参考代码实现

### 7.1 WarmupDetector 核心类

```java
package com.anomalydetection.detector;

import com.anomalydetection.features.RansomwareFeatureVector;
import java.util.ArrayList;
import java.util.List;

/**
 * Warmup 阶段检测器
 * 在积累足够基线数据前，使用分层防御策略进行异常检测
 */
public class WarmupDetector {
    
    private final List<RansomwareFeatureVector> history = new ArrayList<>();
    private final int warmupRounds;
    private int currentRound = 0;
    
    /**
     * Warmup 阶段专用权重
     * 
     * 调整原则：
     * 1. 提高"确定性规则"和"强启发式"特征的权重（单轮可用、高置信度）
     * 2. 降低"中等启发式"特征的权重（需部分历史，避免过早下结论）
     * 3. 禁用"统计特征"（需稳定基线，warmup阶段不稳定会引入噪声）
     * 
     * 具体依据：
     * - 权重 > 5.0：确定性/强启发式特征， warmup 阶段的核心检测手段
     * - 权重 1.0-3.0：辅助特征，提供额外信息但不过度依赖
     * - 权重 0.0：统计特征， warmup 阶段禁用，避免基线不成熟导致的误判
     */
    private static final double[] WARMUP_WEIGHTS = {
        1.0,   // 0: total_operations - 降低（需历史参照才知道"多少算多"）
        5.0,   // 1: modification_ratio - 提高（强启发式，单轮可用，正常业务修改比例<80%）
        0.5,   // 2: deletion_intensity - 不变（权重本来就低，攻击模式少见）
        0.5,   // 3: directory_spread - 降低（需历史才知道"多少目录算多"）
        0.5,   // 4: extension_diversity - 降低（需历史才知道"多少扩展名算正常"）
        15.0,  // 5: suspicious_extension_ratio - 最高（确定性规则，出现即异常）
        2.0,   // 6: peak_burst_velocity - 降低（需历史才知道"多快算快"）
        0.0,   // 7: avg_modified_size - 禁用（需稳定基线估计"正常大小范围"）
        0.0,   // 8: size_std_dev - 禁用（反向信号，需5轮+才知道"正常标准差"）
        1.5,   // 9: high_value_ext_ratio - 降低（辅助信号，单独不够强）
        5.0,   // 10: burst_mod_purity - 提高（强启发式，单轮可用，>95%极不正常）
        3.0,   // 11: file_type_concentration - 提高（强启发式，单轮可用）
        0.0,   // 12: size_change_kurtosis - 禁用（需8轮+稳定基线）
        3.0    // 13: inter_op_time_cv - 提高（单轮可用，CV<0.05极不正常）
    };
    
    public WarmupDetector(int warmupRounds) {
        this.warmupRounds = warmupRounds;
    }
    
    /**
     * 执行 warmup 阶段检测
     * @param vector 当前轮次的特征向量
     * @return 检测结果
     */
    public WarmupResult detect(RansomwareFeatureVector vector) {
        currentRound++;
        
        // ========== 第1层：确定性规则（100%可信）==========
        DetectionResult deterministicResult = checkDeterministicRules(vector);
        if (deterministicResult.isAnomaly()) {
            // 确定性异常不纳入基线，但记录到数据库
            return new WarmupResult(
                WarmupStatus.ANOMALY,
                deterministicResult.getConfidence(),
                deterministicResult.getRule(),
                false  // 不纳入基线
            );
        }
        
        // ========== 第2层：强启发式规则（单轮可用）==========
        DetectionResult heuristicResult = checkHeuristicRules(vector);
        if (heuristicResult.isAnomaly()) {
            return new WarmupResult(
                WarmupStatus.ANOMALY,
                heuristicResult.getConfidence(),
                heuristicResult.getRule(),
                false  // 不纳入基线
            );
        }
        
        // ========== 第3层：动态统计检测（需2轮+历史）==========
        if (history.size() >= 2) {
            DetectionResult statisticalResult = checkStatisticalAnomaly(vector);
            if (statisticalResult.isAnomaly()) {
                return new WarmupResult(
                    WarmupStatus.SUSPICIOUS,  // 统计异常标记为可疑
                    statisticalResult.getConfidence(),
                    statisticalResult.getRule(),
                    false  // 不纳入基线，等待人工审核
                );
            }
        }
        
        // ========== 正常样本：纳入基线 ==========
        history.add(vector);
        
        // 检查是否完成 warmup
        boolean warmupComplete = currentRound >= warmupRounds;
        
        return new WarmupResult(
            WarmupStatus.NORMAL,
            0.0,
            "NORMAL",
            true,  // 纳入基线
            warmupComplete
        );
    }
    
    /**
     * 确定性规则检测（不依赖历史基线）
     */
    private DetectionResult checkDeterministicRules(RansomwareFeatureVector vector) {
        // 规则1：可疑扩展名出现
        if (vector.getSuspiciousExtensionRatio() > 0) {
            return DetectionResult.anomaly("SUSPICIOUS_EXTENSION", 1.0);
        }
        
        // 规则2：勒索信文件（通过签名检测器）
        // 注：实际实现中需要在特征提取阶段记录此信息
        
        return DetectionResult.normal();
    }
    
    /**
     * 强启发式规则检测（单轮可用，基于业务常识）
     */
    private DetectionResult checkHeuristicRules(RansomwareFeatureVector vector) {
        double totalOps = vector.getTotalOperations();
        
        // 规则1：修改比例极高（>95%）且操作数较多
        if (vector.getModificationRatio() > 0.95 && totalOps > 50) {
            return DetectionResult.anomaly("EXTREME_MODIFICATION_RATIO", 0.90);
        }
        
        // 规则2：突发修改纯度极高（>95%）且速度较快
        if (vector.getBurstModPurity() > 0.95 && vector.getPeakBurstVelocity() > 50) {
            return DetectionResult.anomaly("HIGH_BURST_PURITY", 0.80);
        }
        
        // 规则3：文件类型集中度极高（>90%）且修改数较多
        if (vector.getFileTypeConcentration() > 0.90 && totalOps > 100) {
            return DetectionResult.anomaly("HIGH_FILE_TYPE_CONCENTRATION", 0.80);
        }
        
        // 规则4：操作时间间隔过于规律（自动化特征）
        if (vector.getInterOpTimeCv() < 0.05 && totalOps > 50) {
            return DetectionResult.anomaly("ROBOTIC_TIMING_PATTERN", 0.85);
        }
        
        // 规则5：高价值文件比例极高
        if (vector.getHighValueExtRatio() > 0.8 && totalOps > 100) {
            return DetectionResult.anomaly("HIGH_VALUE_TARGETING", 0.75);
        }
        
        // 规则6：删除强度极高
        if (vector.getDeletionIntensity() > 5.0) {
            return DetectionResult.anomaly("HIGH_DELETION_INTENSITY", 0.70);
        }
        
        return DetectionResult.normal();
    }
    
    /**
     * 动态统计异常检测（基于当前积累的历史）
     */
    private DetectionResult checkStatisticalAnomaly(RansomwareFeatureVector vector) {
        // 使用当前历史计算基线统计
        BaselineStatistics stats = new BaselineStatistics(history);
        WeightedEuclideanScorer scorer = new WeightedEuclideanScorer(stats, WARMUP_WEIGHTS);
        
        double score = scorer.score(vector);
        
        // 计算动态阈值
        double threshold = computeDynamicThreshold();
        
        if (score > threshold) {
            return DetectionResult.anomaly("STATISTICAL_ANOMALY", 0.70);
        }
        
        return DetectionResult.normal();
    }
    
    /**
     * 计算动态阈值（样本量越少，阈值越宽松）
     */
    private double computeDynamicThreshold() {
        double multiplier;
        switch (history.size()) {
            case 2:
            case 3:
                multiplier = 10.0;  // 极宽松
                break;
            case 4:
            case 5:
                multiplier = 5.0;   // 宽松
                break;
            case 6:
            case 7:
                multiplier = 3.0;   // 中等
                break;
            default:
                multiplier = 2.0;   // 接近正常
        }
        
        // 计算历史最大得分
        double maxScore = computeMaxHistoricalScore();
        return maxScore * multiplier;
    }
    
    /**
     * 计算历史样本中的最大得分
     */
    private double computeMaxHistoricalScore() {
        if (history.isEmpty()) return 0.0;
        
        BaselineStatistics stats = new BaselineStatistics(history);
        WeightedEuclideanScorer scorer = new WeightedEuclideanScorer(stats, WARMUP_WEIGHTS);
        
        return history.stream()
            .mapToDouble(scorer::score)
            .max()
            .orElse(0.0);
    }
    
    /**
     * 获取 warmup 阶段积累的正常样本（用于构建正式基线）
     */
    public List<RansomwareFeatureVector> getBaselineVectors() {
        return new ArrayList<>(history);
    }
    
    /**
     * 获取当前轮次
     */
    public int getCurrentRound() {
        return currentRound;
    }
    
    /**
     * 获取已积累的历史样本数
     */
    public int getHistorySize() {
        return history.size();
    }
}
```

### 7.2 检测结果枚举

```java
package com.anomalydetection.detector;

/**
 * Warmup 阶段检测状态
 */
public enum WarmupStatus {
    /** 确定性或强启发式异常，不纳入基线 */
    ANOMALY,
    
    /** 统计异常，标记为可疑，不纳入基线，需人工审核 */
    SUSPICIOUS,
    
    /** 正常样本，纳入基线 */
    NORMAL,
    
    /** Warmup 完成，可过渡到 Active 阶段 */
    WARMUP_COMPLETE
}
```

### 7.3 检测结果类

```java
package com.anomalydetection.detector;

/**
 * Warmup 阶段检测结果
 */
public class WarmupResult {
    private final WarmupStatus status;
    private final double confidence;
    private final String rule;
    private final boolean addToBaseline;
    private final boolean warmupComplete;
    
    public WarmupResult(WarmupStatus status, double confidence, String rule, boolean addToBaseline) {
        this(status, confidence, rule, addToBaseline, false);
    }
    
    public WarmupResult(WarmupStatus status, double confidence, String rule, 
                       boolean addToBaseline, boolean warmupComplete) {
        this.status = status;
        this.confidence = confidence;
        this.rule = rule;
        this.addToBaseline = addToBaseline;
        this.warmupComplete = warmupComplete;
    }
    
    // Getters
    public WarmupStatus getStatus() { return status; }
    public double getConfidence() { return confidence; }
    public String getRule() { return rule; }
    public boolean isAddToBaseline() { return addToBaseline; }
    public boolean isWarmupComplete() { return warmupComplete; }
    
    public boolean isAnomaly() {
        return status == WarmupStatus.ANOMALY || status == WarmupStatus.SUSPICIOUS;
    }
}
```

### 7.4 与现有 RansomwareDetector 集成

```java
package com.anomalydetection.detector;

import com.anomalydetection.features.RansomwareFeatureVector;
import java.io.IOException;
import java.nio.file.Path;

/**
 * 增强版勒索软件检测器，支持 Warmup 阶段
 */
public class EnhancedRansomwareDetector {
    
    private enum Phase { WARMUP, ACTIVE }
    
    private Phase phase;
    private final WarmupDetector warmupDetector;
    private RansomwareDetector activeDetector;
    private final int warmupRounds;
    
    public EnhancedRansomwareDetector(int warmupRounds) {
        this.warmupRounds = warmupRounds;
        this.phase = Phase.WARMUP;
        this.warmupDetector = new WarmupDetector(warmupRounds);
    }
    
    /**
     * 检测入口
     */
    public DetectionResult detect(RansomwareFeatureVector vector) {
        if (phase == Phase.WARMUP) {
            WarmupResult result = warmupDetector.detect(vector);
            
            if (result.isWarmupComplete()) {
                transitionToActivePhase();
            }
            
            // 将 WarmupResult 转换为 DetectionResult
            return convertToDetectionResult(result, vector);
        } else {
            return activeDetector.detect(vector);
        }
    }
    
    /**
     * 从文件检测（流式处理）
     */
    public DetectionResult detectFromFile(Path filePath) throws IOException {
        // 先进行签名检测（确定性规则）
        RansomwareSignatureDetector signatureDetector = new RansomwareSignatureDetector();
        RansomwareSignatureDetector.SignatureResult sig = signatureDetector.scanStream(filePath);
        
        if (sig.matched()) {
            return new DetectionResult(
                Double.MAX_VALUE, 
                0.0, 
                true,
                null, 
                null, 
                null, 
                sig.describe()
            );
        }
        
        // 提取特征并检测
        RansomwareFeatureExtractor extractor = new RansomwareFeatureExtractor(null);
        RansomwareFeatureVector vector = extractor.extractFromFile(filePath);
        
        return detect(vector);
    }
    
    /**
     * 从 Warmup 过渡到 Active 阶段
     */
    private void transitionToActivePhase() {
        List<RansomwareFeatureVector> baselineVectors = warmupDetector.getBaselineVectors();
        
        if (baselineVectors.isEmpty()) {
            throw new IllegalStateException("Warmup 阶段没有积累到正常样本，无法进入 Active 阶段");
        }
        
        BaselineStatistics stats = new BaselineStatistics(baselineVectors);
        WeightedEuclideanScorer scorer = new WeightedEuclideanScorer(stats);
        AnomalyThreshold threshold = new AnomalyThreshold(baselineVectors, scorer, 97.0);
        
        this.activeDetector = new RansomwareDetector(stats, threshold);
        this.phase = Phase.ACTIVE;
        
        System.out.println("Warmup 完成，进入 Active 阶段。基线样本数：" + baselineVectors.size());
    }
    
    /**
     * 将 WarmupResult 转换为标准的 DetectionResult
     */
    private DetectionResult convertToDetectionResult(WarmupResult warmupResult, 
                                                     RansomwareFeatureVector vector) {
        switch (warmupResult.getStatus()) {
            case ANOMALY:
            case SUSPICIOUS:
                return new DetectionResult(
                    Double.MAX_VALUE,  // 异常分数
                    0.0,               // 阈值
                    true,              // 是异常
                    null,              // z-scores
                    null,              // top deviations
                    vector,            // 特征向量
                    warmupResult.getRule()  // 签名/规则描述
                );
            case NORMAL:
            case WARMUP_COMPLETE:
                return new DetectionResult(
                    0.0,               // 分数
                    0.0,               // 阈值
                    false,             // 不是异常
                    null,
                    null,
                    vector,
                    null
                );
            default:
                throw new IllegalStateException("未知的 Warmup 状态：" + warmupResult.getStatus());
        }
    }
    
    /**
     * 获取当前阶段
     */
    public String getCurrentPhase() {
        return phase.name();
    }
    
    /**
     * 获取当前轮次
     */
    public int getCurrentRound() {
        if (phase == Phase.WARMUP) {
            return warmupDetector.getCurrentRound();
        }
        return warmupRounds;  // Active 阶段返回总轮次
    }
}
```

### 7.5 使用示例

```java
public class WarmupExample {
    public static void main(String[] args) throws Exception {
        // 创建增强版检测器，warmup 10 轮
        EnhancedRansomwareDetector detector = new EnhancedRansomwareDetector(10);
        
        // 模拟 15 轮检测
        for (int round = 1; round <= 15; round++) {
            Path diffFile = Path.of("data/round_" + round + ".json");
            
            DetectionResult result = detector.detectFromFile(diffFile);
            
            System.out.printf("Round %d [%s]: %s (rule: %s)%n",
                round,
                detector.getCurrentPhase(),
                result.isAnomaly() ? "ANOMALY" : "NORMAL",
                result.getSignatureMatch()
            );
        }
    }
}
```

### 7.6 预期输出

```
Round 1 [WARMUP]: NORMAL (rule: null)
Round 2 [WARMUP]: NORMAL (rule: null)
Round 3 [WARMUP]: ANOMALY (rule: EXTREME_MODIFICATION_RATIO)
Round 4 [WARMUP]: NORMAL (rule: null)
Round 5 [WARMUP]: SUSPICIOUS (rule: STATISTICAL_ANOMALY)
Round 6 [WARMUP]: NORMAL (rule: null)
Round 7 [WARMUP]: NORMAL (rule: null)
Round 8 [WARMUP]: NORMAL (rule: null)
Round 9 [WARMUP]: NORMAL (rule: null)
Round 10 [WARMUP]: NORMAL (rule: null)
Warmup 完成，进入 Active 阶段。基线样本数：8
Round 11 [ACTIVE]: NORMAL (rule: null)
Round 12 [ACTIVE]: ANOMALY (rule: null)
...
```

---

## 八、关键设计说明

### 8.1 为什么分层？

1. **确定性规则**：不依赖历史，100%可信，立即告警
2. **强启发式**：基于业务常识，单轮可用，高置信度
3. **动态统计**：随样本量增加而增强，避免过早下结论

### 8.2 为什么禁用部分特征？

- `size_std_dev`、`size_change_kurtosis`、`avg_modified_size` 需要稳定的"正常范围"参考
- 前 5 轮样本不足以估计这些统计量的稳定分布
- 禁用它们避免误报，同时降低计算开销

### 8.3 动态阈值如何工作？

- 样本量 < 3：阈值 = 历史最大值 × 10（极宽松，几乎不告警）
- 样本量 4-5：阈值 = 历史最大值 × 5（宽松）
- 样本量 6-7：阈值 = 历史最大值 × 3（中等）
- 样本量 8+：阈值 = 历史最大值 × 2（接近正常）

### 8.4 基线污染防护

- 异常和可疑样本**不纳入**基线窗口
- 只将明确正常的样本纳入基线
- 如果 warmup 阶段异常过多，可能导致基线样本不足，需要告警

---

*本文件为讨论中间产物，后续会根据讨论结果更新*
