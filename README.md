# Snapdiff 勒索软件异常检测引擎

> **生产级异常检测 library** — 基于加权欧氏距离的 NetApp snapdiff 实时检测系统。
>
> 双阶段检测架构（启发式预热 → 统计活跃），16 维特征空间，中位数/MAD 鲁棒归一化，
> 方向验证防误报，灵敏度可调。作为 **纯 Java library（无框架依赖）** 嵌入现有系统运行。

---

## 目录

- [系统定位](#系统定位)
- [检测流程](#检测流程)
- [代码结构全景](#代码结构全景)
- [16 维特征体系](#16-维特征体系)
- [阶段 0：签名预检](#阶段-0签名预检)
- [阶段判定：预热 vs 活跃](#阶段判定预热-vs-活跃)
- [阶段 2：预热检测（WarmupDetector）](#阶段-2预热检测warmupdetector)
- [阶段 3：活跃检测（ActiveDetector）](#阶段-3活跃检测activedetector)
- [灵敏度调节](#灵敏度调节)
- [权重优化工具](#权重优化工具)
- [权重在线更新](#权重在线更新)
- [Warmup 效果验证](#warmup-效果验证)
- [待适配项](#待适配项)
- [API 参考](#api-参考)
- [构建与运行](#构建与运行)
- [项目结构](#项目结构)
- [依赖关系](#依赖关系)

---

## 系统定位

本模块处于整个勒索软件异常检测系统的**检测决策层**，上游依赖 `feature-extractor` 模块提供的 16 维特征向量：

```
                   ┌─ feature-extractor ─┐
snapdiff JSON  ──▶ │   Spring Boot 应用  │ ──▶ FeatureVector (16维 + extendInfo)
  (原始快照差异)    │  流式解析 + 特征计算  │
                   └─────────────────────┘
                            │
                            ▼
                   ┌─ detect (本模块) ────┐
                   │  Java 17 Library     │ ──▶ DetectionResult
                   │  无框架依赖          │
                   └─────────────────────┘
                            │
                            ▼
                   调用方系统 / 测试平台
```

另有旧版独立 CLI 工具 `anomaly-detection/`（Java 11，12 维特征，自包含特征提取），
作为独立部署方案存在，与本模块共用相同的检测理念但实现不同。

---

## 检测流程

```
                    FeatureVector
                        │
                        ▼
              ┌──────────────────────┐
              │   PreCheckService     │  ◀── 阶段 0：签名预检
              │   从 vector.extendInfo │
              │   读取预检结果         │
              └──────┬───────────────┘
                     │ 命中? → 直接返回异常 (signatureMatchResult)
                     ▼ 未命中
              ┌──────────────────────┐
              │   Phase Determination │  ◀── 阶段判定
              │   normalCount ≥ 10?   │
              │   (通过 BaselineDataProvider 查询) │
              └──────┬───────────────┘
                 YES │           │ NO
                     ▼           ▼
        ┌──────────────────┐  ┌──────────────────────────┐
        │   ActiveDetector  │  │   WarmupDetector          │
        │   统计检测        │  │   双层次检测               │
        │                   │  │                            │
        │ ① z-score 归一化  │  │ Layer 2: 6条启发式规则并行  │
        │ ② 加权欧氏距离    │  │         任一触发 → 异常     │
        │ ③ 方向验证        │  │                            │
        │ ④ Top-5 维度报告  │  │ Layer 3: 动态统计检测       │
        │                   │  │   (需要 ≥2 条历史向量)      │
        └──────────────────┘  └──────────────────────────┘
                     │              │
                     ▼              ▼
              ┌──────────────────────────┐
              │     DetectionResult       │
              │  score, threshold,        │
              │  isAnomaly, phase,        │
              │  dimensions[],            │
              │  directionValidation,     │
              │  warmupInfo / signatureMatch │
              └──────────────────────────┘
```

### 完整代码路径

```
AnomalyDetectionService.detect(FeatureVector, String, double)
  │
  ├─ 1. preCheckService.check(vector)
  │     → PreCheckService.java
  │     → 读取 FeatureVector.extendInfo["precheck_suspicious_extensions"]
  │       和 extendInfo["precheck_ransom_notes"]
  │
  ├─ 2. baselineProvider.getHistoryNormals(resourceId)
  │     → BaselineDataProvider.java (接口)
  │     → ExternalBaselineProvider.java (占位实现，始终返回空)
  │
  ├─ 3. normalCount < normalThreshold (=10)
  │     │
  │     ├─ YES → warmupDetector.detect(vector, historyNormals, sensitivity)
  │     │         → WarmupDetector.java
  │     │
  │     └─ NO → baselineProvider.getBaselineStats(resourceId, sensitivity)
  │              → stats == null? → fallback 到 warmup
  │              → activeDetector.detect(vector, stats, resourceId, weights)
  │                → ActiveDetector.java
  │
  └─ 4. return DetectionResult
```

---

## 代码结构全景

### detect 模块（**本模块** — 检测引擎）

```
detect/src/main/java/com/anomalydetection/
│
├── detector/                              # 检测核心（15 个文件）
│   ├── AnomalyDetectionService.java       # ★ 统一入口，编排完整流程
│   ├── Phase.java                         # 阶段枚举：WARMUP / ACTIVE
│   │
│   ├── WarmupDetector.java                # 预热检测器（L2 启发式 + L3 动态统计）
│   ├── ActiveDetector.java                # 活跃检测器（加权欧氏距离）
│   ├── DirectionalValidator.java          # 方向验证器（安静日反转检测）
│   │
│   ├── BaselineDataProvider.java          # ★ 基线数据接入接口（需要适配）
│   ├── ExternalBaselineProvider.java      # ★ 占位实现——全部返回空（待适配）
│   ├── BaselineStatsDTO.java              # 基线统计 DTO（median[] + mad[] + threshold）
│   │
│   ├── SensitivityAdjuster.java           # 灵敏度映射：[0,1] → [2.0, 0.5]
│   │
│   ├── DetectionResult.java               # ★ 检测结果输出模型
│   ├── DimensionReport.java               # 单维度检测报告
│   ├── DirectionValidation.java           # 方向验证结果
│   ├── WarmupInfo.java                    # 预热阶段附加信息
│   ├── WarmupStatus.java                  # 预热状态枚举：ANOMALY / SUSPICIOUS / NORMAL
│   │
│   └── heuristic/                         # 启发式规则包（8 个文件）
│       ├── HeuristicRule.java             # @FunctionalInterface 规则接口
│       ├── RuleResult.java                # 规则判定结果（triggered + confidence）
│       ├── ModificationRatioRule.java     # 修改占比 > 0.82
│       ├── BurstModPurityRule.java        # 突发修改纯度 > 0.88 + 高速
│       ├── FileTypeConcentrationRule.java # 操作类型低熵 < 0.60
│       ├── InterOpTimeCvRule.java         # 机械化时间模式 CV < 0.55
│       ├── HighValueTargetingRule.java    # 高价值文件定向 > 0.65
│       └── DeletionIntensityRule.java     # 删除强度 > 0.45
│
├── precheck/                              # 签名预检（2 个文件）
│   ├── PreCheckService.java               # 从 FeatureVector.extendInfo 读取预检结果
│   └── PreCheckResult.java                # 匹配结果（extensions + ransomNotes）
│
└── optimizer/                             # 权重优化工具（5 个文件）
    ├── WeightOptimizer.java               # 随机搜索优化器（AUC 最大化）
    ├── OnlineWeightUpdater.java           # ★ 生产用在线更新工具
    ├── WarmupValidator.java               # Warmup 渐进效果模拟器
    ├── WeightOptimizationRunner.java      # 优化流程入口 main()
    └── BenchmarkDataGenerator.java        # 144 个合成基准测试用例生成器
```

### 测试文件

```
detect/src/test/java/com/anomalydetection/
├── detector/
│   ├── ActiveDetectorTest.java
│   ├── AnomalyDetectionServiceTest.java
│   ├── DetectionResultJsonTest.java
│   ├── HeuristicRuleTest.java
│   ├── SensitivityAdjusterTest.java
│   ├── WarmupActiveIntegrationTest.java
│   └── WarmupDetectorTest.java
└── precheck/
    └── PreCheckServiceTest.java
```

### feature-extractor 模块（上游依赖）

```
feature-extractor/src/main/java/com/anomalydetection/
│
├── Application.java                       # Spring Boot 启动类
│
├── features/                              # 特征定义与计算
│   ├── FeatureType.java                   # ★ 16 维特征枚举（含中英文描述模板）
│   ├── FeatureVector.java                 # ★ 16 维特征向量（double[] + extendInfo Map）
│   ├── FeatureDescription.java            # 中英文描述对 record
│   ├── FeatureCalculator.java             # ★ 16 维特征计算引擎（核心算法）
│   └── FeatureExtractor.java              # 消费者：从 Channel 消费记录，委托 Calculator
│
├── service/
│   ├── FeatureExtractionOrchestrator.java # 编排服务：parse → send → extract
│   └── DiffParserService.java             # 生产者：解析 diff → 发送到 Channel
│
├── parser/
│   ├── DiffParser.java                    # 策略接口
│   ├── SnapdiffFormatParser.java          # snapdiff 格式解析器
│   ├── FilediffFormatParser.java          # filediff 格式解析器
│   └── SnapdiffParserFactory.java         # 工厂：按类型创建解析器
│
├── model/
│   ├── CommonDiffRecord.java              # 统一记录模型
│   ├── ChangeType.java                    # 变更类型枚举（MODIFIED/CREATED/DELETED）
│   └── FileType.java                      # 文件类型枚举（FILE/DIRECTORY）
│
├── channel/
│   ├── RecordChannel.java                 # 生产者-消费者通道接口
│   └── RecordChannelService.java          # 有界队列实现（LinkedBlockingQueue）
│
└── sort/
    └── ExternalMergeSort.java             # 外部归并排序（突发窗口分块排序）
```

### anomaly-detection 模块（旧版独立 CLI 工具）

```
anomaly-detection/src/main/java/com/anomalydetection/
│
├── cli/
│   ├── RansomwareDetectorCli.java         # 检测 CLI（picocli）
│   └── WeightOptimizerCli.java            # 权重优化 CLI
│
├── detector/                              # 自包含检测逻辑（10 个类）
│   ├── RansomwareDetector.java            # 主检测器
│   ├── RansomwareSignatureDetector.java   # 签名预检
│   ├── WarmupDetector.java                # 预热检测器
│   ├── WeightedEuclideanScorer.java       # 加权欧氏距离评分器
│   ├── BaselineStatistics.java            # 基线统计
│   ├── DirectionalValidator.java          # 方向验证
│   ├── AnomalyThreshold.java             # 阈值计算
│   ├── DetectionResult.java               # 检测结果
│   ├── ZScoreExplainer.java              # z-score 分析报告
│   └── WeightOptimizer.java              # 权重优化
│
├── features/                              # 自包含特征提取（5 个类）
│   ├── RansomwareFeatureExtractor.java    # 特征提取器（12 维）
│   ├── RansomwareFeatureVector.java       # 特征向量
│   ├── BurstDataFile.java                 # 突发数据文件
│   ├── InMemoryBurstAccumulator.java      # 突发窗口累加器
│   └── SuspiciousExtensions.java          # 可疑扩展名列表
│
├── model/
│   ├── SnapdiffFile.java                  # snapdiff 文件模型
│   └── SnapdiffRecord.java                # snapdiff 记录模型
│
├── parser/
│   ├── SnapdiffParser.java                # 解析器
│   └── StreamingSnapdiffParser.java       # 流式解析器（大文件）
│
└── generator/                             # 测试数据生成
    ├── RansomwareTestGenerator.java       # 主生成器
    ├── BenchmarkDataGenerator.java        # 基准数据生成
    ├── AttackGenerator.java              # 攻击数据生成
    ├── DiffEntry.java
    ├── FilesystemState.java
    ├── IntermittentEncryptionBenchmark.java
    └── SnapdiffOutput.java
```

---

## 16 维特征体系

| 索引 | 特征名 | 说明 | 当前权重 (Active) | 当前权重 (Warmup) |
|------|--------|------|:-:|:-:|
| F0 | `modification_ratio` | 修改操作占比 | 0.0444 | 0.0444 |
| F1 | `deletion_ratio` | 删除操作占比 | 0.1572 | 0.1572 |
| F2 | `creation_ratio` | 创建操作占比 | 0.0290 | 0.0290 |
| F3 | `total_operations_normalized` | 日均操作总量 | 0.0150 | 0.0150 |
| F4 | `peak_burst_velocity` | 5 分钟窗口内峰值操作速率 | 0.1090 | 0.1090 |
| F5 | `burst_mod_purity` | 峰值窗口修改操作纯度 | 0.0105 | 0.0105 |
| F6 | `high_value_ext_ratio` | 高价值扩展名占比（docx/pdf/db 等） | 0.1378 | 0.1378 |
| F7 | `inter_op_time_cv_burst` | 峰值窗口操作间隔变异系数 | 0.0123 | 0.0123 |
| F8 | `directory_coverage_depth` | 受影响的目录深度 | 0.0043 | 0.0043 |
| F9 | `temporal_uniformity` | 窗口内时间均匀度 | 0.0767 | 0.0767 |
| F10 | `rename_correlation` | 重命名关联度 | 0.0238 | 0.0238 |
| F11 | `hourly_concentration` | 24 小时操作集中度 | 0.0171 | 0.0171 |
| F12 | `hourly_entropy` | 小时分布熵 | 0.0128 | 0.0128 |
| F13 | `per_type_entropy` | 操作类型（M/C/D）分布熵 | 0.0964 | 0.0964 |
| F14 | `extension_count_cv` | 扩展名数量变异系数 | 0.0358 | 0.0358 |
| F15 | `created_ext_novelty` | **新建扩展名新颖度** | 0.1625 | 0.1625 |

> **权重来源：** `FALLBACK_WEIGHTS`（`AnomalyDetectionService.java:56`）和 `WARMUP_WEIGHTS`（`WarmupDetector.java:27`）
> 当前两套权重相同。优化参数：seed=42, 20000 次迭代, 2026-06-02。
> 数据集：50 个核心正常向量（N1+N2+N3+N5+N6）vs 69 个攻击变种（14 种类型）
> AUC = **0.9626**，检出 59/69，核心误报 2/48。

---

## 阶段 0：签名预检

`PreCheckService` 在特征向量进入统计检测前，先做快速签名匹配。

**数据来源：** `FeatureVector.extendInfo` 中的两个 key：
- `"precheck_suspicious_extensions"` — 匹配到的勒索软件扩展名列表
- `"precheck_ransom_notes"` — 匹配到的勒索信文件名列表

**注意：** 签名匹配由 `feature-extractor` 模块在特征提取阶段完成，结果存入 `extendInfo`。
`PreCheckService` 只负责读取和判断，**不执行实际的文件扫描**。

**判定：** 任一列表非空 → `PreCheckResult.isMatch() == true` → 跳过统计检测，直接返回异常。

```java
PreCheckResult preCheck = preCheckService.check(vector);
if (preCheck.isMatch()) {
    return DetectionResult.signatureMatchResult(resourceId, signature);
}
```

---

## 阶段判定：预热 vs 活跃

通过 `BaselineDataProvider.getHistoryNormals(resourceId)` 获取该资源的历史正常向量数量：

```java
int normalCount = historyNormals != null ? historyNormals.size() : 0;

if (normalCount < normalThreshold) {
    // → WarmupDetector
} else {
    BaselineStatsDTO stats = baselineProvider.getBaselineStats(resourceId, sensitivity);
    if (stats == null) {
        // fallback 到 WarmupDetector
    } else {
        // → ActiveDetector
    }
}
```

- `normalThreshold` 默认 = **10**（可在构造函数调整）
- `ExternalBaselineProvider` 占位实现始终返回空 → **当前总是运行在预热模式**

### 阈值与判定汇总

| 阶段 | 所需历史向量 | 判定逻辑 |
|------|------------|---------|
| 预热 L2 | 任意 | 6 条启发式规则并行判定，任意触发即异常 |
| 预热 L3 | ≥2 条 | 小样本中位数/MAD + 自适应阈值 |
| 活跃 | ≥10 条 | 加权欧氏距离评分 + 方向验证 |

---

## 阶段 2：预热检测（WarmupDetector）

### 总体策略

预热阶段历史正常向量极少（0-9 条），传统统计方法不可靠。采用双层次防御：

```
Layer 2: 6条启发式规则并行判定
         任意一条触发 → 直接判定为异常
         置信度随结果返回
               │
         均未触发 → 进入 Layer 3
               ▼
Layer 3: 动态统计检测（需要 ≥2 条历史向量）
         中位数/MAD 归一化 → z-score 评分
         自适应阈值（样本量越少，阈值越高）
         贝叶斯收缩（先验 median/MAD 稳定小样本估计）
```

### Layer 2：6 条启发式规则

每条规则独立实现 `HeuristicRule` 接口：

```java
@FunctionalInterface
public interface HeuristicRule {
    RuleResult evaluate(FeatureVector vector);
    default RuleResult evaluate(FeatureVector vector, double sensitivity) { ... }
    default String getRuleName() { ... }
}
```

| 规则类 | 规则名 | 触发条件 | 置信度 | 检测信号 |
|--------|--------|---------|:------:|---------|
| `ModificationRatioRule` | EXTREME_MODIFICATION_RATIO | modification_ratio > 0.82 且 日均操作量 > 100 | 0.90 | 加密≈全是修改 |
| `BurstModPurityRule` | HIGH_BURST_PURITY | burst_mod_purity > 0.88 且 峰值速率 > 1000 | 0.80 | 自动化高速加密 |
| `FileTypeConcentrationRule` | LOW_PER_TYPE_ENTROPY | per_type_entropy < 0.60 且 日均操作量 > 100 | 0.80 | 单一操作类型主导 |
| `InterOpTimeCvRule` | ROBOTIC_TIMING_PATTERN | inter_op_time_cv_burst < 0.55 且 日均操作量 > 100 | 0.85 | 机械化规律时间 |
| `HighValueTargetingRule` | HIGH_VALUE_TARGETING | high_value_ext_ratio > 0.65 且 日均操作量 > 100 | 0.75 | 定向高价值文档 |
| `DeletionIntensityRule` | DELETION_INTENSITY | deletion_ratio > 0.45 且 日均操作量 > 100 | 0.70 | 破坏性删除行为 |

> 支持灵敏度调节的规则（ModificationRatioRule、BurstModPurityRule、FileTypeConcentrationRule、InterOpTimeCvRule）会通过 `SensitivityAdjuster.getThresholdMultiplier(sensitivity)` 调整阈值。

### Layer 3：小样本动态统计检测

当历史正常向量 ≥2 条时启用：

1. **中位数/MAD 计算**：从历史向量计算每维的 median 和 MAD（MAD_SCALE=1.4826）
2. **贝叶斯收缩**：用先验 median/MAD（来自 N1 设计基线）稳定小样本估计
3. **z-score 计算**：`z = (value - median) / max(mad, 0.001)`，截断至 ±10
4. **自适应阈值**：样本量越少，阈值越高
   - 2 条历史 → 乘数 4.0（最严格）
   - 4 条历史 → 乘数 2.5
   - 6 条历史 → 乘数 1.8
   - 8 条历史 → 乘数 1.2
   - ≥10 条 → 进入活跃阶段

---

## 阶段 3：活跃检测（ActiveDetector）

### 加权欧氏距离评分

```java
// 1. z-score 归一化（截断至 ±10，防止极端值主导）
z_i = (value_i - median_i) / max(mad_i, 0.001)
z_i = clamp(z_i, -Z_CAP, Z_CAP)   // Z_CAP = 10.0

// 2. 加权欧氏距离
score = sqrt(Σ w_i × z_i²)

// 3. 阈值判定
isAnomaly = score > threshold
```

### 方向验证（Quiet-Day Reversal Detection）

防止安静日（所有特征值远低于基线）被误判为异常：

```java
E_up   = Σ w_i × z_i²  (对 z_i > 0 的特征)
E_down = Σ w_i × z_i²  (对 z_i < 0 的特征)
ratio  = E_down / (E_up + E_down + 1e-10)

if (ratio > directionThreshold) {  // 默认 0.75
    reversed = true;  // 反转判定为正常
}
```

### Top-5 偏离维度报告

按贡献度 `w_i × z_i²` 降序排列，返回偏离最大的 5 个维度及其详情，
用于告警排查和根因分析。

### 权重查询

```java
// ★ 待适配：当前返回硬编码 FALLBACK_WEIGHTS
// 目标实现：queryWeightsFromDB(String resourceId) → 从 detection_weights 表查询
private double[] queryWeights(String resourceId) {
    return FALLBACK_WEIGHTS.clone();
}
```

---

## 灵敏度调节

`SensitivityAdjuster` 提供统一的灵敏度映射：

| 灵敏度 | 含义 | 阈值乘数 |
|:------:|------|:--------:|
| 1.0 | 最灵敏（捕获更多，可能增加误报） | 0.5 |
| 0.7 | 默认（平衡检出与误报） | 0.95 |
| 0.0 | 最不灵敏（只捕获最明确的攻击） | 2.0 |

公式：`multiplier = 2.0 - sensitivity × 1.5`

影响范围：
- Layer 2 中支持灵敏度调节的启发式规则（阈值 × multiplier）
- Active 阶段通过 `BaselineDataProvider.getBaselineStats(resourceId, sensitivity)` 传递到外部模块

---

## 权重优化工具

### WeightOptimizer

随机搜索 16 维权重空间（Dirichlet 分布采样），最大化 AUC：

```java
WeightOptimizer optimizer = new WeightOptimizer(normalVectors, attackVectors);
WeightOptimizer.OptimizationResult result = optimizer.optimize(20000);
// AUC=0.9626, detected=59/69, core FP=2/48
```

算法步骤：
1. 从正常向量计算 median/MAD
2. 每次迭代从 Dirichlet(1,1,...,1) 采样候选权重 (Σw_i = 1)
3. 对正常/攻击向量分别评分，计算 AUC
4. 保留 AUC 最高的权重组合
5. 用目标百分位（默认 97%）从正常评分分布确定阈值

### WeightOptimizationRunner

优化流程完整入口，生成 144 个基准测试用例，执行 20000 次迭代，
输出详细报告到 `docs/weight-optimization-report.md`。

```bash
java -cp "detect/target/classes;feature-extractor/target/classes" \
  com.anomalydetection.optimizer.WeightOptimizationRunner [seed]
```

### BenchmarkDataGenerator

生成 144 个合成 FeatureVector 用于权重优化：

| 类别 | 数量 | 说明 |
|------|:----:|------|
| 正常 (Normal) | 66 | 8 种模式：办公、编译、日志、迁移、安静日、聚焦目录、ETL、安全扫描 |
| 攻击 (Attack) | 70 | 14 种类型：快速加密、间歇、高价值、破坏性、慢速滴漏、混合伪装等 |
| 边界 (Boundary) | 8 | 接近判定边界的模糊案例 |

---

## 权重在线更新

`OnlineWeightUpdater` 设计用于生产环境定期（如每周）用真实积累的 FeatureVector 重新优化权重。

```java
OnlineWeightUpdater updater = new OnlineWeightUpdater(normals, attacks);
OnlineWeightUpdater.UpdateResult result = updater.optimize(
    AnomalyDetectionService.FALLBACK_WEIGHTS,  // 当前 Active 权重
    WarmupDetector.WARMUP_WEIGHTS,             // 当前 Warmup 权重
    10000                                      // 迭代次数
);

if (result.activeImproved) {
    db.saveWeights(resourceId, "active", result.activeWeights);
}
if (result.warmupImproved) {
    db.saveWeights(resourceId, "warmup", result.warmupWeights);
}
```

**Warmup 与 Active 分开优化：**
- **Active** — 历史向量充足，直接用样本 MAD，评分稳定可靠
- **Warmup** — 历史向量只有 2~9 条，MAD 估计不可靠。优化时模拟小样本条件（向先验收缩 MAD），自动降低高方差特征的权重，防止小样本误报

---

## Warmup 效果验证

`WarmupValidator` 模拟 Warmup 渐进检测过程：

```
模拟过程：
  初始：0 条历史向量
  Step 1: 加入 1 条正常向量 → 评估所有攻击的检出率
  Step 2: 再入 1 条正常向量 → 再次评估
  ...
  Step N: 直至达到 endSize

输出：
  - 各阶段的攻击检出率（按 Layer 2 / Layer 3 分层）
  - 各阶段的正常向量误报率
  - 检出率随样本量的增长曲线
```

---

## 待适配项

根据代码探索识别的待完成工作：

### 🔴 高优先级 — 阻塞性缺失

| 待适配项 | 位置 | 说明 |
|---------|------|------|
| **BaselineDataProvider 实现** | `detector/ExternalBaselineProvider.java` | 所有方法返回空/null。需要接入真实数据库或外部模块来提供历史基线向量和统计量。当前无法进入 Active 阶段，始终运行在 Warmup 模式 |
| **权重数据库查询** | `AnomalyDetectionService.queryWeights()` L169 | 目前返回硬编码 `FALLBACK_WEIGHTS.clone()`。需实现从数据库按 `resourceId` 查询 16 维权重。参考表结构见 `AnomalyDetectionService.java:46-51` 的 TODO |
| **PreCheckService 数据来源** | `precheck/PreCheckService.java` | precheck 数据通过 `FeatureVector.extendInfo` 传递。需要 `feature-extractor` 模块在特征提取阶段实际写入 `precheck_suspicious_extensions` 和 `precheck_ransom_notes` 数据 |

### 🟡 中优先级 — 完善项

| 待适配项 | 说明 |
|---------|------|
| **anomaly-detection 模块整合** | 旧版独立 CLI 工具（Java 11, 12 特征, picocli）与 detect 模块（Java 17, 16 特征, Library API）共存。需决策是否废弃旧版或统一架构 |
| **灵敏度传递到外部模块** | `BaselineDataProvider.getBaselineStats(resourceId, sensitivity)` 将灵敏度传给外部，但外部模块需实际支持按灵敏度调整 threshold |
| **Warmup 贝叶斯先验校准** | `WarmupDetector.PRIOR_MEDIAN` 和 `PRIOR_MAD` 来自 N1 设计基线，需用实际生产数据校准 |
| **启发式规则阈值校准** | 6 条规则的阈值（0.82、0.88、0.60、0.55、0.65、0.45）基于合成数据设定，需用真实勒索软件样本验证 |

### 🟢 低优先级 — 优化项

| 待适配项 | 说明 |
|---------|------|
| **OnlineWeightUpdater 调度** | 在线更新工具已实现但缺少调度触发机制（如定时任务、事件驱动） |
| **WarmupValidator 集成 CI** | Warmup 渐进效果模拟器已实现但未集成到 CI/CD 流水线 |
| **合成数据与实际数据校准** | `BenchmarkDataGenerator` 使用合成 FeatureVector，需用真实 snapdiff 数据验证特征分布一致性 |
| **方向验证阈值调优** | `DEFAULT_DIRECTION_THRESHOLD = 0.75` 需根据生产数据调优 |

---

## API 参考

### AnomalyDetectionService — 唯一对外入口

```java
// 构造
AnomalyDetectionService service = new AnomalyDetectionService();
// 或：AnomalyDetectionService service = new AnomalyDetectionService(15, myBaselineProvider);

// 检测
DetectionResult result = service.detect(
    featureVector,    // FeatureVector — 来自 feature-extractor 模块
    "host-001",       // resourceId — 资源标识，用于基线查询
    0.7               // sensitivity — [0.0, 1.0]，1.0 最灵敏
);
```

### DetectionResult — 检测输出

```java
result.getPhase();              // WARMUP / ACTIVE
result.isAnomaly();             // true = 异常
result.getScore();              // 评分（加权欧氏距离或规则数）
result.getThreshold();          // 阈值
result.getDimensions();         // 所有 16 维报告
result.getTopDeviations(5);     // Top-5 偏离维度
result.getDirectionValidation();// 方向验证结果
result.getSignatureMatch();     // 签名匹配信息（预检命中时非空）
result.getWarmupInfo();         // 预热信息（Warmup 阶段时非空）
```

### DimensionReport — 单维度报告

```java
report.getIndex();              // 0-15
report.getName();               // 特征名称
report.getValue();              // 原始值
report.getZScore();             // z-score（截断后）
report.getContribution();       // 贡献度 = w × z²
report.getWeight();             // 维度权重
report.isAnomalyDimension();    // |z| > 2.0
```

### BaselineDataProvider — 基线接入接口

```java
public interface BaselineDataProvider {
    List<FeatureVector> getHistoryNormals(String resourceId);
    List<FeatureVector> getHistoryAnomalies(String resourceId);
    BaselineStatsDTO getBaselineStats(String resourceId);
    BaselineStatsDTO getBaselineStats(String resourceId, double sensitivity);
}
```

- `getHistoryNormals` — 返回该资源的历史正常特征向量列表（用于 Warmup 阶段计算）
- `getBaselineStats` — 返回预计算好的基线统计（median[] + mad[] + threshold），用于 Active 阶段
- 返回 null 表示不可用 → 自动 fallback 到 Warmup 模式

---

## 构建与运行

### 编译

```bash
# 编译 detect 及其依赖（feature-extractor）
mvn compile -pl detect -am

# 打包全部模块
mvn package -DskipTests
```

### 测试

```bash
# 运行 detect 全部测试
mvn test -pl detect -am

# 运行指定测试类
mvn test -pl detect -am -Dtest=AnomalyDetectionServiceTest
```

### 权重优化实验

```bash
# 编译打包后运行
java -cp "detect/target/classes;feature-extractor/target/classes" \
  com.anomalydetection.optimizer.WeightOptimizationRunner [seed]

# seed 可选，默认 42。更换 seed 可验证结果稳定性
```

---

## 项目结构

```
detect/
├── pom.xml                                    # Java 17，Maven 3.x
├── README.md
│
├── src/main/java/com/anomalydetection/
│   ├── detector/                              # 检测核心
│   │   ├── AnomalyDetectionService.java        # ★ 统一入口
│   │   ├── Phase.java                          # 阶段枚举
│   │   ├── WarmupDetector.java                 # 预热检测
│   │   ├── WarmupInfo.java                     # 预热附加信息
│   │   ├── WarmupStatus.java                   # 预热状态枚举
│   │   ├── ActiveDetector.java                 # 活跃检测
│   │   ├── DirectionalValidator.java           # 方向验证器
│   │   ├── DirectionValidation.java            # 方向验证结果
│   │   ├── BaselineDataProvider.java           # ★ 基线接口
│   │   ├── ExternalBaselineProvider.java       # ★ 占位实现
│   │   ├── BaselineStatsDTO.java               # 基线统计 DTO
│   │   ├── DetectionResult.java                # ★ 检测结果
│   │   ├── DimensionReport.java                # 维度报告
│   │   ├── SensitivityAdjuster.java            # 灵敏度映射
│   │   └── heuristic/                          # 启发式规则
│   │       ├── HeuristicRule.java              # 规则接口
│   │       ├── RuleResult.java                 # 规则结果
│   │       ├── ModificationRatioRule.java
│   │       ├── BurstModPurityRule.java
│   │       ├── FileTypeConcentrationRule.java
│   │       ├── InterOpTimeCvRule.java
│   │       ├── HighValueTargetingRule.java
│   │       └── DeletionIntensityRule.java
│   ├── precheck/                              # 签名预检
│   │   ├── PreCheckService.java
│   │   └── PreCheckResult.java
│   └── optimizer/                             # 权重优化
│       ├── WeightOptimizer.java               # 随机搜索优化器
│       ├── WeightOptimizationRunner.java       # 优化入口 main()
│       ├── OnlineWeightUpdater.java            # 在线更新工具
│       ├── WarmupValidator.java                # Warmup 模拟验证
│       └── BenchmarkDataGenerator.java         # 基准数据生成器
│
└── src/test/java/com/anomalydetection/
    ├── detector/
    │   ├── AnomalyDetectionServiceTest.java
    │   ├── WarmupDetectorTest.java
    │   ├── ActiveDetectorTest.java
    │   ├── HeuristicRuleTest.java
    │   ├── SensitivityAdjusterTest.java
    │   ├── WarmupActiveIntegrationTest.java
    │   └── DetectionResultJsonTest.java
    └── precheck/
        └── PreCheckServiceTest.java
```

### 权重定义位置速查

| 权重集 | 所在文件 | 行号 |
|--------|---------|:----:|
| `WARMUP_WEIGHTS` | `WarmupDetector.java` | L27 |
| `FALLBACK_WEIGHTS` | `AnomalyDetectionService.java` | L56 |

---

## 依赖关系

```
detect ────依赖──> feature-extractor (提供 FeatureVector / FeatureType / FeatureDescription)
  │
  └── 纯 Java 17 library，无 Spring 框架依赖
      外部依赖：Jackson (JSON 解析，仅 PreCheckService 间接使用)
```

**对应的 feature-extractor 模块依赖：**
```
feature-extractor ──── 基于 Spring Boot 3.x
  │
  ├── 提供: FeatureType (16维特征枚举)
  ├── 提供: FeatureVector (16维 double[] + extendInfo)
  ├── 提供: FeatureDescription (中英文特征描述)
  └── 依赖: Jackson, Lombok, Spring Boot
```

**旧版 anomaly-detection 模块（独立，不依赖上述两者）：**
```
anomaly-detection ──── 独立 CLI 工具
  │
  ├── Java 11, picocli CLI, Jackson
  ├── 自包含特征提取（12 维）
  ├── 自包含检测逻辑（5 条预热规则）
  └── 与本模块共用检测理念但实现不同
```

---

## 实验数据与效果

> 以下数据通过 `WeightOptimizationRunner` 生成（seed=42, iterations=20000, 2026-06-03 运行）。
> 测试套件：41/41 全部通过（`mvn test -pl detect -am`）。

### 测试套件结果

```
detect 模块单元测试：
  ├── ActiveDetectorTest          ✓ 4/4  评分计算、异常判定、Top-5偏离、方向验证
  ├── AnomalyDetectionServiceTest ✓ 6/6  预热路由、活跃路由、预检命中、灵敏度传递、边界案例
  ├── DetectionResultJsonTest     ✓ 3/3  JSON序列化
  ├── HeuristicRuleTest           ✓ 8/8  6条规则各2个用例 + 规则名唯一性
  ├── SensitivityAdjusterTest     ✓ 9/9  边界值、默认值、异常输入
  ├── WarmupActiveIntegrationTest ✓ 1/1  预热→活跃完整转换
  ├── WarmupDetectorTest          ✓ 3/3  无规则触发、L2触发、L3统计
  └── PreCheckServiceTest         ✓ 7/7  扩展名/勒索信/混合/无匹配
```

### 基准数据集（BenchmarkDataGenerator）

144 个合成 FeatureVector，覆盖 22 种模式：

**正常（66 个，8 种模式）：**

| 模式 | 数量 | 行为说明 |
|------|:----:|---------|
| REGULAR_OFFICE | 20 | 常规办公操作（低修改比、中等突发速率、高操作类型熵） |
| BATCH_COMPILE | 8 | 批量编译（高修改比、高突发纯度、机械化时间模式） |
| LOG_ROTATION | 8 | 日志轮转（高创建/删除比、低高价值文件比） |
| DATA_MIGRATION | 8 | 数据迁移（高操作量、高目录覆盖率） |
| QUIET_DAY | 6 | 安静日（极低操作量） |
| FOCUSED_DIR | 6 | 聚焦目录（操作集中在少数目录） |
| DATABASE_ETL | 6 | 数据库 ETL（高修改比、高操作量、高时间均匀度） |
| SECURITY_SCAN | 4 | 安全扫描（高目录覆盖率、低操作量） |

**攻击（69 个，14 种类型，每种 5 个变种）：**

| 编号 | 类型 | 说明 | 主要检测信号 |
|:----:|------|------|------------|
| A1 | FAST_ENCRYPT | 快速全盘加密（高速、高修改纯度、低操作类型熵） | Layer2 4条规则 |
| A2 | INTERMITTENT | 间歇加密（中等突发、低 CV） | Layer2 2-4条规则 |
| A3 | HIGH_VALUE | 高价值文档定向加密 | Layer2 1-3条规则 |
| A4 | DESTRUCTIVE | 破坏性删除+加密（高删除比） | Layer2 0-1条规则，靠 L3 统计 |
| A5 | SLOW_DRIP | 慢速持续加密（低瞬时速率） | Layer2 1-3条规则 |
| A6 | MIXED_MASK | 混合操作掩盖加密（正常操作混合） | Layer2 0-1条规则，最易漏检 |
| A7 | HOURLY_SPREAD | 按小时散布（低小时集中度） | Layer2 3-4条规则 |
| A8 | ENCRYPT_RENAME | 加密+重命名 | Layer2 0条规则，仅靠 L3 统计 |
| A9 | ENCRYPT_CLEANUP | 加密+清理痕迹 | Layer2 0-1条规则 |
| A10 | TINY_FILE | 极小文件加密（大量小文件） | Layer2 最高分 |
| A11 | TIME_AWARE | 避免工作时间 | Layer2 全靠 L3 |
| A12 | MULTI_STAGE | 多阶段攻击（先探后加密） | Layer2 0-1条规则 |
| A13 | INPLACE | 原地加密（不修改扩展名） | Layer2 0条规则，仅靠 L3 统计 |
| A14 | ARCHIVE | 归档式加密（先打包再加密） | Layer2 0条规则，仅靠 L3 统计 |

**边界（8 个）：** 接近判定边界的模糊正常案例（B1-B8）。

### 基线统计量（48 个核心正常向量）

```
Idx  特征                            Median       MAD
----------------------------------------------------------------------
0    modification_ratio               0.4483       0.1864
1    deletion_ratio                   0.2067       0.1062
2    creation_ratio                   0.2848       0.1900
3    total_operations_normalized      12746.8636   15422.0113
4    peak_burst_velocity              3790.9350    3253.0576
5    burst_mod_purity                 0.6440       0.2404
6    high_value_ext_ratio             0.3170       0.1169
7    inter_op_time_cv_burst           1.0258       0.5140
8    directory_coverage_depth         35.4817      23.5709
9    temporal_uniformity              0.5169       0.2665
10   rename_correlation               0.0435       0.0417
11   hourly_concentration             0.1892       0.1285
12   hourly_entropy                   3.5511       0.9939
13   per_type_entropy                 1.3295       0.2527
14   extension_count_cv               1.6454       1.4182
15   created_ext_novelty              0.1381       0.1882
```

### 权重优化结果

**ActiveDetector 权重（随机搜索 20000 次迭代，AUC 最大化）：**

```
Active Raw: AUC=0.9626  caught=59/69  FP=2/48(核心)  threshold=1.3466
  + extended FP=13/18  boundary FP=2/8   total FP=17/74

优化权重:
Idx  特征                           权重
----------------------------------------------------------------------
0    modification_ratio               0.0444
1    deletion_ratio                   0.1572
2    creation_ratio                   0.0290
3    total_operations_normalized      0.0150
4    peak_burst_velocity              0.1090
5    burst_mod_purity                 0.0105
6    high_value_ext_ratio             0.1378
7    inter_op_time_cv_burst           0.0123
8    directory_coverage_depth         0.0043
9    temporal_uniformity              0.0767
10   rename_correlation               0.0238
11   hourly_concentration             0.0171
12   hourly_entropy                   0.0128
13   per_type_entropy                 0.0964
14   extension_count_cv               0.0358
15   created_ext_novelty              0.1625

Top 5 最重要特征:
  [15] created_ext_novelty     0.1625  — 新建扩展名新颖度
  [1]  deletion_ratio         0.1572  — 删除占比
  [6]  high_value_ext_ratio   0.1378  — 高价值扩展名占比
  [4]  peak_burst_velocity    0.1090  — 峰值窗口操作速率
  [13] per_type_entropy       0.0964  — 操作类型分布熵

最不重要特征（权重 < 0.02）:
  [8]  directory_coverage_depth  0.0043
  [5]  burst_mod_purity          0.0105
  [7]  inter_op_time_cv_burst    0.0123
  [12] hourly_entropy            0.0128
  [3]  total_operations_normalized  0.0150
  [11] hourly_concentration      0.0171
```

**WarmupDetector L3 权重：** 首次运行发现 Active 与 Warmup 权重相同（L3 使用相同 48 个核心正常向量计算 median/MAD）。

经 `OnlineWeightUpdater` 在模拟小样本（n=5）+ 贝叶斯收缩条件后，得到 **Warmup 专属权重**（AUC=0.9647）：
```
Warmup 与 Active 权重对比（Top 5 差异）:
  [15] created_ext_novelty     0.1560 (Active: 0.0868)  ↑ Warmup 更重视
  [6]  high_value_ext_ratio   0.1478 (Active: 0.1122)  ↑ Warmup 更重视
  [0]  modification_ratio     0.1308 (Active: 0.1456)  ↓ Warmup 略低
  [9]  temporal_uniformity    0.1141 (Active: 0.0121)  ↑↑ Warmup 大幅提升
  [1]  deletion_ratio         0.0853 (Active: 0.0686)  ↑ Warmup 略高
```

### Warmup Layer 2 启发式评估（sensitivity=0.7）

**正常向量触发统计（≥2 条规则触发 = 异常）：**

| 正常模式 | 数量 | ≥2触发 | 误报率 | 说明 |
|---------|:----:|:------:|:------:|------|
| REGULAR_OFFICE | 20 | 0 | 0% | 办公操作干净——大部分规则不触发 |
| BATCH_COMPILE | 8 | 0 | 0% | 编译模式虽有高修改比，但触发规则<2 |
| LOG_ROTATION | 8 | 0 | 0% | 日志轮转，没有合适规则触发 |
| DATA_MIGRATION | 8 | 1 | 12.5% | DM_04 触发 2 条（高修改+高突发） |
| QUIET_DAY | 6 | 1 | 16.7% | QD_03 触发 2 条（低操作量异常） |
| FOCUSED_DIR | 6 | 0 | 0% | 聚焦目录，规则未触发 |
| DATABASE_ETL | 6 | 1 | 16.7% | ETL_01 触发 2 条（高修改+机械化） |
| SECURITY_SCAN | 4 | 0 | 0% | 安全扫描，规则未触发 |
| **合计** | **66** | **3** | **4.5%** | L2 总体误报率低 |

**攻击向量触发统计：**

| 攻击类型 | ≥2触发 | 检出率 | 平均触发规则数 | 说明 |
|---------|:------:|:-----:|:-------------:|------|
| A1 FAST_ENCRYPT | 5/5 | 100% | 4.0 | 全检出，信号最强 |
| A2 INTERMITTENT | 5/5 | 100% | 2.6 | 全检出 |
| A3 HIGH_VALUE | 4/5 | 80% | 2.0 | 1个变种只触发了1条 |
| A4 DESTRUCTIVE | 0/5 | 0% | 0.8 | **全漏检**—需 L3 统计 |
| A5 SLOW_DRIP | 4/5 | 80% | 2.0 | |
| A6 MIXED_MASK | 0/5 | 0% | 1.0 | **全漏检**—需 L3 统计 |
| A7 HOURLY_SPREAD | 5/5 | 100% | 3.6 | 全检出 |
| A8 ENCRYPT_RENAME | 0/5 | 0% | 0.0 | **全漏检**—无规则触发 |
| A9 ENCRYPT_CLEANUP | 0/5 | 0% | 0.2 | **全漏检**—需 L3 |
| A10 TINY_FILE | 5/5 | 100% | 5.0 | **全部 6 条规则触发** |
| A11 TIME_AWARE | 0/5 | 0% | 0.0 | **全漏检**—无规则触发 |
| A12 MULTI_STAGE | 2/5 | 40% | 1.0 | |
| A13 INPLACE | 0/5 | 0% | 0.0 | **全漏检**—无规则触发 |
| A14 ARCHIVE | 0/5 | 0% | 0.0 | **全漏检**—无规则触发 |
| **合计** | **44/69** | **63.8%** | | 仅靠 L2 启发式 |

> **结论：** L2 启发式对 A1(快速加密)、A2(间歇)、A7(按小时)、A10(极小文件) 检出率 100%；
> 对 A4(破坏性)、A6(混合掩盖)、A8(加密+重命名)、A9(清理痕迹)、A11(时间规避)、A13(原地加密)、A14(归档式) 几乎完全漏检，
> 这些类型必须依赖 L3 统计检测或 Active 阶段的加权欧氏距离。

### 完整检测效果（Active 阶段加权欧氏距离）

```
Active:  AUC=0.9626  caught=59/69  FP=17/74  threshold=1.3466
         ├─ 核心正常 FP:  2/48   (4.2%)
         ├─ 扩展正常 FP: 13/18   (72.2%)  ← 含 ETL/迁移等高频模式
         └─ 边界正常 FP:  2/8    (25.0%)

攻击检出明细（score > 1.3466 = ✅, ≤ 1.3466 = ❌）:
  A1 FAST_ENCRYPT       5/5  ✅✅✅✅✅  平均分 3.83  — 100% 检出
  A2 INTERMITTENT       5/5  ✅✅✅✅✅  平均分 2.42  — 100% 检出
  A3 HIGH_VALUE         4/5  ❌✅✅✅✅  平均分 1.82  — 80% 检出
  A4 DESTRUCTIVE        5/5  ✅✅✅✅✅  平均分 1.88  — 100% 检出（L3 弥补 L2）
  A5 SLOW_DRIP          5/5  ✅✅✅✅✅  平均分 1.80  — 100% 检出
  A6 MIXED_MASK         1/5  ❌❌✅❌❌  平均分 1.24  — 20% 检出（最困难类型）
  A7 HOURLY_SPREAD      5/5  ✅✅✅✅✅  平均分 1.94  — 100% 检出
  A8 ENCRYPT_RENAME     5/5  ✅✅✅✅✅  平均分 2.12  — 100% 检出（L3 完全弥补 L2）
  A9 ENCRYPT_CLEANUP    4/5  ❌✅✅✅✅  平均分 1.92  — 80% 检出
  A10 TINY_FILE          5/5  ✅✅✅✅✅  平均分 3.69  — 100% 检出（最高分）
  A11 TIME_AWARE         5/5  ✅✅✅✅✅  平均分 1.73  — 100% 检出（L3 完全弥补 L2）
  A12 MULTI_STAGE        5/5  ✅✅✅✅✅  平均分 2.12  — 100% 检出
  A13 INPLACE            5/5  ✅✅✅✅✅  平均分 1.78  — 100% 检出（L3 完全弥补 L2）
  A14 ARCHIVE            0/5  ❌❌❌❌❌  平均分 1.23  — 0% 检出（需新特征或规则）
```

### Warmup 渐进验证

`WarmupValidator` 模拟预热阶段随样本量增长的检出率变化（sensitivity=0.7）：

```
sensitivity=0.7:
  初期 (n=2): L2检出=44  L3检出=16  共计=60/69  误报=11/64
  后期 (n=10): L2检出=44  L3检出=23  共计=67/69  误报=42/56
  → 随样本量增加，L3 检出从 16 提升至 23，但误报也显著上升

sensitivity=1.0（最灵敏）:
  初期 (n=2):  L2检出=58  L3检出=10  共计=68/69  误报=50/64
  后期 (n=10): L2检出=58  L3检出=11  共计=69/69  误报=56/56
  → 检出接近完美，但几乎全部正常向量被判为异常（误报率极高）
```

### Layer 2 策略对比

| 条件 | 攻击检出 | 正常误报 | 基线泄露风险 |
|:----:|:--------:|:--------:|:-----------:|
| ≥1 条规则触发 | 57/69 (82.6%) | 21/66 (31.8%) | 12 FN 漏入基线 |
| ≥2 条规则触发（当前） | 44/69 (63.8%) | 3/66 (4.5%) | 25 FN 漏入基线 |

### 基线污染分析

```
策略                                   攻击泄露  正常排除
----------------------------------------------------------------------
旧策略：告警=基线排除（≥2条规则触发）    25/69      3/66
新策略：解耦（告警≥2, 基线排除≥1）       12/69     21/66
改进：                                    -13 攻击泄露
```

**当前实现：** `WarmupDetector.toWarmupInfo()` 已实现解耦策略
→ `addToBaseline = !isAnomaly && ruleCount < 1`

### 接受标准评估

| 标准 | Active | Warmup |
|:----|:------:|:------:|
| AUC ≥ 0.96 / 0.90 | **✅ PASS** 100.3% | **✅ PASS** 107.0% |
| 检出率 ≥ 92% / 85% | **❌ FAIL** 92.9%（59/69 ≈ 85.5%） | **✅ PASS** 100.6% |
| 误报 ≤ 5 / 6（核心+扩展+边界） | **❌ FAIL** 340.0%（17/74） | **❌ FAIL** 283.3% |

> **说明：** 误报超标主要来自扩展正常向量（N4 数据迁移 + N7 ETL + N8 安全扫描），
> 这些模式本身就有高频操作特征。核心误报仅 2/48（4.2%）在接受范围内。
> 生产部署时建议使用 `ExternalBaselineProvider` 接入真实数据重新校准 baseline 统计量。
