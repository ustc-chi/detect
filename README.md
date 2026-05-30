# Snapdiff 勒索软件异常检测引擎

> **生产级异常检测 library** — 基于加权欧氏距离的 NetApp snapdiff 实时检测系统。  
> 双阶段检测架构（启发式预热 → 统计活跃），16 维特征空间，中位数/MAD 鲁棒归一化，方向验证防误报。  
> 作为 **纯 Java library（无框架依赖）** 嵌入现有系统运行。

---

## 目录

- [系统架构](#系统架构)
- [检测流程](#检测流程)
- [特征体系](#特征体系)
- [快速开始](#快速开始)
- [API 参考](#api-参考)
- [基线数据接入](#基线数据接入)
- [权重优化工具](#权重优化工具)
- [构建与测试](#构建与测试)
- [性能调优指南](#性能调优指南)
- [项目结构](#项目结构)

---

## 系统架构

```
┌──────────────────────────────────────────────────────────────────────────┐
│                             调用方系统                                      │
│  (测试平台 / 编排服务 / 自动化流水线)                                       │
└──────────┬───────────────────────────────────────┬────────────────────────┘
           │                                       │
           │ ① snapdiff JSON                       │ ② FeatureVector
           │   (原始快照差异文件)                     │   (16维特征向量)
           ▼                                       ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                      AnomalyDetectionService                              │
│                                                                          │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │  阶段 0：签名预检 (PreCheckService)                                  │  │
│  │  ├─ 50+ 勒索软件扩展名匹配                                           │  │
│  │  ├─ 12 种勒索信文件名模式匹配                                         │  │
│  │  └─ 流式 JSON 解析，支持 GB 级文件                                      │  │
│  │  ✦ 命中 → 直接返回异常，零延迟                                         │  │
│  └──────────────────────┬──────────────────────────────────────────────┘  │
│                         ▼                                                 │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │  阶段判定：历史正常向量计数与阈值比较                                   │  │
│  │                                                                     │  │
│  │  normalCount < threshold?                                            │  │
│  │       │                      │                                       │  │
│  │       ├── YES ───────────────┤                                       │  │
│  │       │                     ▼                                       │  │
│  │       │              ┌──────────────┐                               │  │
│  │       │              │  预热阶段     │                               │  │
│  │       │              │ WarmupPhase  │                               │  │
│  │       │              └──────┬───────┘                               │  │
│  │       │                     ▼                                       │  │
│  │       │  ┌──────────────────────────────────────────────────────┐   │  │
│  │       │  │ L2：6 条启发式规则并行判定                              │   │  │
│  │       │  │  任一触发 → 异常，规则名 + 置信度随结果返回                │   │  │
│  │       │  └──────────────────────┬───────────────────────────────┘   │  │
│  │       │                         ▼                                   │  │
│  │       │  ┌──────────────────────────────────────────────────────┐   │  │
│  │       │  │ L3：动态统计检测 (需 ≥2 条历史正常向量)                  │   │  │
│  │       │  │  中位数/MAD 归一化 → 自适应阈值                        │   │  │
│  │       │  │  epsilon + MAD_SCALE 防零除                           │   │  │
│  │       │  │  样本量自适应倍数 {2→10x, 4→5x, 6→3x, 8→2x}          │   │  │
│  │       │  └──────────────────────────────────────────────────────┘   │  │
│  │       │                      │                                      │  │
│  │       ├── NO ────────────────┤                                      │  │
│  │       │                     ▼                                       │  │
│  │       │              ┌──────────────┐                               │  │
│  │       │              │  活跃阶段     │                               │  │
│  │       │              │  ActivePhase │                               │  │
│  │       │              └──────┬───────┘                               │  │
│  │       │                     ▼                                       │  │
│  │       │  ┌──────────────────────────────────────────────────────┐   │  │
│  │       │  │ ① z-score 计算（z = (x - median) / mad）               │   │  │
│  │       │  │   截断至 ±10，防止单一特征主导                            │   │  │
│  │       │  ├──────────────────────────────────────────────────────┤   │  │
│  │       │  │ ② 加权欧氏距离评分                                     │   │  │
│  │       │  │   score = √(Σ w_i × z_i²)                          │   │  │
│  │       │  ├──────────────────────────────────────────────────────┤   │  │
│  │       │  │ ③ 方向验证 (Quiet-Day Reversal Detection)            │   │  │
│  │       │  │   ratio = Σw_i·z_i²(负) / Σw_i·|z_i²|               │   │  │
│  │       │  │   ratio > 0.75 → 反转判定为正常                        │   │  │
│  │       │  ├──────────────────────────────────────────────────────┤   │  │
│  │       │  │ ④ Top-5 偏离维度报告                                  │   │  │
│  │       │  │   按贡献度降序排列 (w_i × z_i²)                      │   │  │
│  │       │  └──────────────────────────────────────────────────────┘   │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│                                                                          │
│                       ▼                                                  │
│               DetectionResult                                            │
│         返回给调用方 (含完整上下文)                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 检测流程

### 阶段 0：签名预检 (PreCheckService)

第一道防线，在进入任何统计计算之前快速扫描原始 snapdiff JSON 文件。

**检测机制：**
- 使用 Jackson `JsonParser` 流式解析（非 DOM），内存消耗 O(1)，支持超大文件
- 对每条记录检查文件路径的扩展名和文件名
- 扩展名匹配 50+ 已知勒索软件扩展（HashSet O(1) 查找）
- 文件名匹配 12 种勒索信模式（大写转换 + contains 检查）

**示例：**
```
输入路径: /data/docs/report.docx    → 扩展名 .docx 非可疑，跳过
输入路径: /backup/README_UNLOCK.html → 文件名匹配勒索信模式 → 立即标记异常
输入路径: /encrypted/files.encrypted → 扩展名 .encrypted 命中 → 立即标记异常
```

**性能：** 线性扫描，百万级记录实测 < 1s。

---

### 阶段 1：预热检测 (WarmupDetector)

系统冷启动或资源历史数据不足时启用。设计目标：在缺乏统计基线的情况下提供可靠的检测覆盖。

#### L2 — 启发式规则层（6 条规则并行判定）

每条规则基于领域知识设计，捕获勒索软件的行为指纹：

| 规则 | 触发条件 | 检测能力 | 置信度 |
|------|----------|----------|--------|
| **EXTREME_MODIFICATION_RATIO** | `mod_ratio > 0.95 ∧ dailyOps > 50` | 加密型勒索（LockBit/Conti/REvil）— 操作几乎全是修改 | **90%** |
| **HIGH_BURST_PURITY** | `burst_purity > 0.95 ∧ peak_velocity > 50` | 突发窗口内加密纯度极高 | **80%** |
| **LOW_PER_TYPE_ENTROPY** | `per_type_entropy < 0.3 ∧ dailyOps > 100` | 单一操作类型极端主导 | **80%** |
| **ROBOTIC_TIMING_PATTERN** | `inter_op_cv < 0.05 ∧ dailyOps > 50` | 规律化间隔（脚本/自动化行为） | **85%** |
| **HIGH_VALUE_TARGETING** | `hv_ext_ratio > 0.8 ∧ dailyOps > 100` | 定向加密高价值文件（文档/数据库） | **75%** |
| **HIGH_DELETION_INTENSITY** | `deletion_ratio > 0.5` | 破坏型勒索（删除+加密双重攻击） | **70%** |

> **设计原则：** 保守触发、高置信度。宁可漏过少数边缘情况，也要确保零误报。漏过的样本会被 L3 兜底。

#### L3 — 动态统计检测层

当历史正常向量累积 ≥ 2 条后自动激活：

```
算法流程:
  1. 汇集历史正常向量，构建 n×16 矩阵
  2. 逐维计算 median[i] 和 MAD[i]
     MAD[i] = median(|x_j - median[i]|) × 1.4826 + 0.001
  3. 当前向量逐维 z-score 归一化（截断 ±10）
  4. 加权欧氏距离: score = √(Σ w_i × z_i²)
  5. 对历史向量自评分，计算动态阈值:
     threshold = median(historyScores) + multiplier × MAD(historyScores)
     multiplier 随样本量自适应 {2→10x, 4→5x, 6→3x, 8→2x}
  6. score > threshold → 异常
```

**自适应倍数原理：** 样本量越少 → 统计量越不稳定 → 阈值越宽松以防止误报。样本量越多 → 阈值越紧致以提高检出率。

---

### 阶段 2：活跃检测 (ActiveDetector)

基线数据充足后进入生产模式。

#### 加权欧氏距离评分

```
z_i = clamp((x_i - median_i) / mad_i, -10, +10)

score = √(Σ w_i × z_i²)

isAnomaly = score > threshold
```

- **median_i**: 第 i 维的历史中位数（比均值更鲁棒，不受极端值影响）
- **mad_i**: 第 i 维的中位数绝对偏差（MAD），乘以 1.4826 使其与标准差尺度一致
- **clamp(±10)**: 防止单一维度因极端值主导评分
- **w_i**: 第 i 维的可配置权重（可从数据库查询）

#### 方向验证 (Directional Validation)

```
eUp   = Σ w_i × z_i²  (对于 z_i > 0 的维度)
eDown = Σ w_i × z_i²  (对于 z_i < 0 的维度)

ratio = eDown / (eUp + eDown)

ratio > threshold (默认 0.75) → 反转判定为正常
```

**解决安静日问题：** 某天业务量骤降，几乎所有特征都低于基线，导致欧氏距离异常大——但这不是攻击。方向验证检测到"异常主要由下降驱动"时，反转判定，消除此类误报。

---

## 特征体系

系统使用 **16 维特征向量**，由 [feature-extractor](../feature-extractor/) 模块计算。特征定义在 `FeatureType` 枚举中，类型安全，自带中英文元数据。

| 索引 | 特征名 | 含义 | 检测信号 |
|------|--------|------|----------|
| **F0** | modification_ratio | 修改操作数 / 总操作数 | **加密型勒索信号** — 正常用户增删改混合，加密型勒索接近 1.0 |
| **F1** | deletion_ratio | 删除操作数 / 总操作数 | **破坏型攻击信号** — 删除文件后再加密的模式 |
| **F2** | creation_ratio | 创建操作数 / 总操作数 | **批量创建文件信号** — 释放勒索信/解密工具 |
| **F3** | total_operations_normalized | 总操作数 / 快照间隔天数 | **大规模操作通用信号** |
| **F4** | peak_burst_velocity | 最密集 5 分钟窗口操作数 × 12 | **自动化工具特征** — 脚本加密的高速突发 |
| **F5** | burst_mod_purity | 突发窗口内修改操作占比 | **加密行为特征** — 突发窗口几乎全是修改 |
| **F6** | high_value_ext_ratio | 高价值扩展名操作数 / 总操作数 | **针对性加密信号** — 优先加密文档/数据库 |
| **F7** | inter_op_time_cv_burst | 突发窗口内操作间隔变异系数 | **规律性自动化信号** |
| **F8** | directory_coverage_depth | 唯一目录数 × 深度一致性因子 | **扩散广度** — 遍历目录结构 |
| **F9** | temporal_uniformity | 300s 窗口内 10s 间隔均匀度 | **非人类操作模式** |
| **F10** | rename_correlation | 删除-创建文件名前缀匹配比例 | **加密-重命名模式** |
| **F11** | hourly_concentration | 最忙小时操作数 / 总操作数 | **操作时间分布特征** |
| **F12** | hourly_entropy | 24 小时分布的 Shannon 熵 | **时间随机性** — 低熵 = 集中在少量时段 |
| **F13** | per_type_entropy | MODIFIED/CREATED/DELETED 分布的熵 | **类型多样性** — 低熵 = 单一类型主导 |
| **F14** | extension_count_cv | 各扩展名出现次数的变异系数 | **扩展名均匀性** |
| **F15** | created_ext_novelty | 新建扩展名中未出现在其他操作的比例 | **新颖扩展名信号** |

### 类型安全的特征访问

```java
// ❌ 旧方式：下标访问，容易搞错顺序
double val = values[5];  // 5 是什么？得翻文档

// ✅ 新方式：枚举访问，IDE 自动补全
double val = fv.get(FeatureType.BURST_MOD_PURITY);

// 元数据自动关联
FeatureType ft = FeatureType.MODIFICATION_RATIO;
ft.key();              // "modification_ratio"
ft.desCN();            // "修改占比: %s (共%s条)"
ft.desEN();            // "Modification Ratio: %s (of %s)"

// 带数值的完整描述
FeatureDescription desc = fv.getDes(FeatureType.MODIFICATION_RATIO);
desc.cn();             // "修改占比: 85% (共100条)"
desc.en();             // "Modification Ratio: 85% (of 100)"
```

---

## 快速开始

### 前置要求

| 组件 | 版本 |
|------|------|
| JDK | 17+ |
| Maven | 3.8+ |
| feature-extractor | 同项目子模块 |

### 构建

```bash
# 回到父目录，一次性编译两个子模块
cd ..
mvn clean compile

# 运行全部测试
mvn test
```

### 最小示例

```java
import com.anomalydetection.detector.*;
import com.anomalydetection.features.FeatureVector;
import java.nio.file.Path;

public class Demo {
    public static void main(String[] args) throws Exception {
        // 初始化（使用默认空数据提供者，始终预热模式）
        AnomalyDetectionService detector = new AnomalyDetectionService();

        // 准备输入
        Path snapdiffFile = Path.of("round_001.json");
        FeatureVector fv = new FeatureVector();

        // 执行检测
        DetectionResult result = detector.detect(snapdiffFile, fv, "demo-resource");

        // 输出结果
        System.out.println("异常: " + result.isAnomaly());
        System.out.println("阶段: " + result.getPhase());
        System.out.println("评分: " + result.getScore());
    }
}
```

### 生产示例

```java
BaselineDataProvider myProvider = new BaselineDataProvider() {
    public List<FeatureVector> getHistoryNormals(String r) {
        return db.query("SELECT feature_json FROM normals WHERE resource_id = ?", r);
    }
    public List<FeatureVector> getHistoryAnomalies(String r) {
        return db.query("SELECT feature_json FROM anomalies WHERE resource_id = ?", r);
    }
    public BaselineStatsDTO getBaselineStats(String r) {
        return db.query("SELECT median, mad, threshold FROM baselines WHERE resource_id = ?", r);
    }
};

AnomalyDetectionService detector = new AnomalyDetectionService(15, myProvider);

for (SnapdiffTask task : batch) {
    FeatureVector fv = featureExtractor.extractFeatures(task.id, task.daysBetween);
    DetectionResult r = detector.detect(task.file, fv, task.resourceId);
    if (r.isAnomaly()) {
        alertService.send(r);
    }
}
```

---

## API 参考

### AnomalyDetectionService

| 构造方法 | 说明 |
|----------|------|
| `AnomalyDetectionService()` | 默认阈值 10，ExternalBaselineProvider |
| `AnomalyDetectionService(int normalThreshold)` | 自定义预热→活跃切换阈值 |
| `AnomalyDetectionService(int normalThreshold, BaselineDataProvider)` | 完整构造 |

| 方法 | 返回 | 说明 |
|------|------|------|
| `detect(Path, FeatureVector, String)` | `DetectionResult` | 主检测 API，可能抛 IOException |

### DetectionResult

| 方法 | 返回 | 说明 |
|------|------|------|
| `isAnomaly()` | boolean | 最终判定 |
| `getScore()` | double | 异常评分 |
| `getThreshold()` | double | 判定阈值 |
| `getPhase()` | Phase | WARMUP / ACTIVE |
| `getResourceId()` | String | 资源标识 |
| `getDetectionTime()` | Instant | 检测时间 |
| `getDimensions()` | List\<DimensionReport\> | 全部 16 维报告 |
| `getTopDeviations()` | List\<DimensionReport\> | Top 5 偏离维度 |
| `getDirectionValidation()` | DirectionValidation | 方向验证结果 |
| `getSignatureMatch()` | String | 预检签名（null=未命中） |
| `getWarmupInfo()` | WarmupInfo | 预热阶段信息 |

### DimensionReport

| 方法 | 返回 | 说明 |
|------|------|------|
| `getIndex()` | int | 维度索引 0-15 |
| `getName()` | String | 特征名 |
| `getValue()` | double | 原始特征值 |
| `getZScore()` | double | z-score |
| `getContribution()` | double | 贡献度 w×z² |
| `getWeight()` | double | 维度权重 |
| `isAnomalyDimension()` | boolean | \|z\| > 2.0 |
| `getDescription()` | String | 中英文完整描述 |
| `getSupplementary()` | Map | 附加信息 |

### BaselineDataProvider

```java
public interface BaselineDataProvider {
    List<FeatureVector> getHistoryNormals(String resourceId);
    List<FeatureVector> getHistoryAnomalies(String resourceId);
    BaselineStatsDTO getBaselineStats(String resourceId);  // null = 不可用
}
```

---

## 权重优化工具

`WeightOptimizer` 使用随机搜索在 16 维权重空间中搜索最优组合，最大化正常/攻击向量的 AUC。

```java
List<FeatureVector> normalVectors = loadFromDB("label = 'normal'");
List<FeatureVector> attackVectors = loadFromDB("label = 'attack'");

WeightOptimizer optimizer = new WeightOptimizer(normalVectors, attackVectors);
WeightOptimizer.OptimizationResult result = optimizer.optimize(5000);

System.out.println(result);
// AUC=0.9874 caught=47/50 FP=1/5000 threshold=3.2145
// Weights:
//   [0] modification_ratio = 0.1523
//   [5] burst_mod_purity = 0.1845
//   [6] high_value_ext_ratio = 0.3912
```

**算法：** 每次从 Dirichlet 分布采样一组权重（Σw_i=1），评分后计算 AUC，保留最优组合。最终用目标百分位（默认 97%）从正常评分分布确定阈值。

---

## 性能调优指南

### 关键参数

| 参数 | 影响 | 推荐值 | 调优方向 |
|------|------|--------|---------|
| `normalThreshold` | 预热→活跃切换 | 10-15 | 数据充足可降低 |
| `directionThreshold` | 方向验证灵敏度 | 0.75 | 安静日误报多→降低；攻击被反转→提高 |
| `Z_CAP` | z-score 截断 | 10 | 极端值多→降低到 5 |
| 特征权重 | 各维度重要性 | 见 FALLBACK_WEIGHTS | 用 WeightOptimizer 搜索 |

### 权重配置建议

**高权重（> 2.0）：** high_value_ext_ratio, inter_op_time_cv_burst, burst_mod_purity — 强特异性信号  
**中权重（0.5-2.0）：** modification_ratio, peak_burst_velocity, deletion_ratio, per_type_entropy  
**低权重/预留（0.0）：** temporal_uniformity, rename_correlation, hourly_*, extension_count_cv — 尚未校准

---

## 项目结构

```
detect/
├── pom.xml                                    # Java 17，依赖 feature-extractor
├── README.md
│
├── src/main/java/com/anomalydetection/
│   ├── detector/                              # 检测核心
│   │   ├── AnomalyDetectionService.java        # ★ 统一入口
│   │   ├── ActiveDetector.java                # 活跃阶段检测
│   │   ├── WarmupDetector.java                # 预热阶段检测
│   │   ├── DirectionalValidator.java          # 方向验证
│   │   ├── BaselineDataProvider.java          # ★ 数据接入接口
│   │   ├── ExternalBaselineProvider.java      # 默认空实现
│   │   ├── BaselineStatsDTO.java
│   │   ├── DetectionResult.java               # ★ 输出模型
│   │   ├── DimensionReport.java               # ★ 输出模型
│   │   ├── DirectionValidation.java
│   │   ├── Phase.java                         # WARMUP / ACTIVE
│   │   ├── WarmupInfo.java
│   │   ├── WarmupStatus.java
│   │   └── heuristic/                         # 启发式规则
│   │       ├── HeuristicRule.java
│   │       ├── RuleResult.java
│   │       └── 6 条规则实现
│   ├── precheck/                              # 签名预检
│   │   ├── PreCheckService.java
│   │   └── PreCheckResult.java
│   └── optimizer/                             # 权重优化工具
│       └── WeightOptimizer.java
│
└── src/test/java/com/anomalydetection/
    └── detector/                              # 23 个测试用例
```
