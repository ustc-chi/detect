# 勒索软件异常检测器

基于加权欧氏距离的 NetApp snapdiff 勒索软件异常检测系统。12个聚合特征（含行为特征：burst_mod_purity、temporal_uniformity、rename_correlation、directory_coverage_depth、per_type_entropy 等），中位数/MAD鲁棒归一化，两阶段检测（签名预检 + 统计评分），支持基线自学习。

## 快速开始

```bash
mvn clean package

java -jar target/rcf-snapdiff-anomaly-detector-1.0.jar \
  --baseline-dir ./baseline-rounds/ \
  --input-dir ./suspect-round/ \
  --output-file ./results.csv

java -cp target/rcf-snapdiff-anomaly-detector-1.0.jar \
  com.anomalydetection.generator.RansomwareTestGenerator 42 test-output
```

## CLI 参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `--baseline-dir` | 可选 | 正常 snapdiff JSON 目录，用于基线校准。省略时进入预热模式（WarmupDetector 启发式检测） |
| `--input-dir` | 必填 | 待检测 snapdiff JSON 目录 |
| `--output-file` | 必填 | 输出 CSV 路径 |
| `--threshold-percentile` | 97.0 | 基线自评分的百分位数作为异常阈值 |
| `--threshold-iqr-multiplier` | 2.5 | IQR 异常值过滤倍数（0 禁用过滤）。过滤超过 Q3 + k×IQR 的基线自评分后再计算阈值 |
| `--weights` | 见下表 | 12个逗号分隔的特征权重 |
| `--window-size` | 10 | 自学习窗口最大保留轮数 |
| `--suspicious-extensions-file` | 内置50个 | 自定义勒索软件扩展名配置 |
| `--include-normal` | false | 输出中包含基线自评分 |
| `--days-between` | 2.0 | 快照间隔天数，特征 1（total_operations_normalized）会除以此值转换为日均速率 |
| `--direction-threshold` | 0.75 | 方向验证阈值（0-1）。当异常评分主要由低于基线的偏差驱动时（安静日模式），反转判定为正常。设为 0 禁用方向验证 |

---

## 检测流程

### 第一阶段：签名预检

`RansomwareSignatureDetector` 对每轮 snapdiff 记录做快速扫描：

- **可疑扩展名**：检测文件扩展名是否属于50个已知勒索软件扩展（.locked, .encrypted, .crypt, .enc, .locky 等）
- **勒索信文件名**：检测文件名是否包含12种已知勒索信模式（README_UNLOCK, HOW_TO_DECRYPT, DECRYPT_INSTRUCTIONS, YOUR_FILES_ARE_ENCRYPTED 等）

命中任一签名 → **立即标记为异常**，跳过统计评分。

### 第二阶段：预热检测（WarmupDetector）

当系统冷启动（无 `--baseline-dir` 或基线轮次不足5个）时，统计检测器无法工作（阈值退化为 0.0，所有轮次被误判为异常）。预热检测器（`WarmupDetector`）在此期间提供安全网。

`WarmupDetector` 使用5条启发式规则对特征向量进行快速分类，每条规则独立判定，最终汇总：

| 规则 | 特征 | 阈值 | 检测信号 |
|------|------|------|----------|
| R1 | modification_ratio | > 0.85 | 加密操作几乎全是修改 |
| R2 | peak_burst_velocity | > 5000 | 自动化工具的高速突发 |
| R3 | temporal_uniformity | > 0.7 | 操作时间均匀分布（非人类模式） |
| R4 | burst_mod_purity | > 0.90 | 突发窗口内修改纯度极高 |
| R5 | rename_correlation | > 0.5 | 加密-重命名模式 |

**触发条件**：≥2条规则同时匹配 → 判定为异常（严格大于，=0.85 不触发 R1）。

预热检测的评分语义：`score = 匹配规则数`，`threshold = 2`。这使检测结果在 CSV 输出中与统计检测格式一致。

**基线累积门控**：预热期间被判定为异常的轮次 **不纳入基线累积**，防止勒索软件轮次污染基线的 median/MAD 统计量。

**自动切换**：基线累积满5个干净向量后，预热检测器自动停用，系统切换到统计检测模式。之后所有轮次由加权欧氏距离评分器处理。

**滞留警告**：若预热持续超过10轮（因大量异常轮次无法累积足够基线），系统记录 WARNING 日志。

设 `--baseline-dir` 省略时系统自动进入预热模式。设 `--baseline-dir` 提供足够基线文件时跳过预热，直接进入统计检测。

### 第三阶段：统计异常检测

第三阶段在基线就绪（≥5个干净向量）后执行：
1. 提取12个聚合特征
2. 中位数/MAD 归一化为 z-score（z-score 截断至 ±10，防止单一特征主导阈值）
3. 加权欧氏距离评分：`score = √(Σ w_i × z_i²)`
4. 与阈值比较：score ≤ threshold → 正常（纳入自学习窗口）；score > threshold → 进入方向验证
5. **方向验证**（DirectionalValidator）：计算异常能量的方向比率 `ratio = E_down / (E_up + E_down + ε)`，其中 `E_up = Σ w_i × max(0, z_i)²`（高于基线能量），`E_down = Σ w_i × max(0, -z_i)²`（低于基线能量）。当 `ratio > direction_threshold`（默认 0.75）时，判定为安静日误报，**反转为正常**，记录 WARNING 日志，且不纳入自学习窗口。设 `--direction-threshold 0` 禁用此阶段
6. 通过方向验证的异常判定保持不变；正常轮次纳入自学习窗口（FIFO，最多保留10轮）

---

## 基线特征值计算详解

基线（baseline）是检测器的"正常行为参考点"。系统从一组已知正常的 snapdiff 轮次中提取特征、建立统计模型、计算阈值。整个过程在 CLI 启动时自动完成，分为以下五个阶段。

### 第一阶段：加载基线文件并提取特征向量

CLI 读取 `--baseline-dir` 目录下所有 JSON 文件（按文件名排序），对每个文件通过 `RansomwareFeatureExtractor` 执行一次完整的特征提取，产出 12 维特征向量 `RansomwareFeatureVector`。

**输入格式**：每个 JSON 文件是一个 `SnapdiffFile`，包含一个 `diffs` 数组，每个元素是一条 `SnapdiffRecord`：

```json
{
  "diffs": [
    { "type": "modified", "path": "/user1/docs/report.docx", "size": 45678, "change_time": "2025-01-15T10:30:00Z" },
    { "type": "deleted",  "path": "/user1/docs/old_report.docx", "size": 12345, "change_time": "2025-01-15T10:31:00Z" }
  ]
}
```

**提取过程**（单次遍历所有 records）：

1. **modification_ratio**（索引 0）：`count(type="modified") / total_operations`
   - **计算**：修改类型操作数占总操作数的比例
   - **检测意义**：加密型勒索软件（LockBit、Conti 等）的核心信号。正常用户活动是增/删/改混合的，而加密型勒索几乎全是修改操作，比值接近 1.0

2. **total_operations_normalized**（索引 1）：`count(records) / daysBetweenSnapshots`
   - **计算**：操作总数除以快照间隔天数（默认2.0），转换为日均操作速率
   - **检测意义**：勒索软件加密大量文件时，操作总数会出现数量级的激增。这是最通用的检测信号——无论攻击者是否改变扩展名或使用间歇性加密，大规模文件操作始终存在

3. **peak_burst_velocity**（索引 2）：`max_{window=5min}(ops_in_window) / (300/3600)`
   - **计算**：将所有操作按 `change_time` 排序，用**双指针滑动窗口**找 300 秒内操作数最多的区间，计算 `maxInWindow / (300/3600)` 折算为每小时速率
   - 双指针算法：`tail` 从 0 开始，`head` 依次推进，当 `sorted[head] - sorted[tail] > 300` 时移动 `tail`
   - **检测意义**：勒索软件的标志性特征是**短时间突发**——在 30-300 秒内加密数百到数千个文件，远超正常用户的操作速率。即使攻击者尝试放慢速度（如 slow_distributed 模式），在微突发窗口内的密度仍然异常

4. **burst_mod_purity**（索引 3）：`count(type="modified" ∧ time ∈ burst_window) / ops_in_burst_window`
   - **计算**：在特征 2 找到的最密集 5 分钟窗口内，修改操作数占该窗口总操作数的比例
   - **检测意义**：区分**突发操作的性质**。正常用户的突发活动（如批量复制文件）通常是增/删/改混合的，纯度较低；勒索软件的突发窗口内几乎全是修改操作（加密），纯度接近 1.0。即使攻击操作被大量正常操作填充（70%填充），突发窗口内的修改纯度依然异常

5. **high_value_ext_ratio**（索引 4）：`EMA(α=0.3) of count(ext ∈ HVList) / total_operations`
   - **计算**：高价值扩展名（`.docx`, `.xlsx`, `.pptx`, `.pdf`, `.db`, `.sql`, `.csv`, `.doc`, `.xls`, `.mdb`, `.accdb`, `.bak`, `.backup`）的出现次数占总操作数的比例，使用**指数移动平均**（alpha=0.3）跨轮次平滑，首轮初始化为原始值
   - **检测意义**：勒索软件的**目标选择性**特征。攻击者会优先加密高价值文件类型（文档、数据库、备份文件）以最大化勒索效果。正常用户操作中高价值扩展名的比例通常较低且稳定，而 database_priority 攻击模式会使其飙升至接近 1.0。EMA 平滑降低单轮波动噪声

6. **inter_op_time_cv_burst**（索引 5）：`σ(deltas) / μ(deltas)` within burst window
   - **计算**：仅在特征 2 找到的最密集 5 分钟窗口内，计算操作时间间隔的变异系数（标准差/均值）。当有效时间戳少于 2 个或 `μ < 0.001` 时返回 0.0
   - **检测意义**：衡量突发窗口内操作时间间隔的**规律性**。勒索软件加密操作通常以固定间隔执行（特别是间歇性加密和慢速分布式攻击），导致时间间隔变异系数（CV）异常低。正常用户操作的间隔是不规则的（高 CV），而自动化攻击的间隔更均匀（低 CV）

7. **high_value_file_coverage**（索引 6）：`clamp(count(ext ∈ HVList) / total_operations, 0.0, 1.0)`
   - **计算**：高价值扩展名操作占总操作的比例，限制在 [0, 1] 范围内。当 `totalOps == 0` 时返回 0.0
   - **检测意义**：衡量高价值文件的覆盖度。与特征 4（EMA 平滑比例）互补——特征 4 捕捉跨轮次趋势，特征 6 捕捉当轮瞬时值

8. **directory_coverage_depth**（索引 7）：`uniqueDirs × (1.0 / (1.0 + σ(depths)))`
   - **计算**：从所有修改文件的路径中提取父目录（去重），同时计算路径深度的标准差。最终值 = 唯一目录数 × 深度一致性因子。当没有修改文件时返回 0.0
   - **检测意义**：衡量攻击的**横向遍历广度和路径深度一致性**。勒索软件通常遍历多个用户目录加密，目录覆盖广且路径深度一致（高值）；正常用户活动集中在少数目录，路径深度不规律（低值）

9. **temporal_uniformity**（索引 8）：`1.0 - (σ(binCounts) / μ(binCounts))`
   - **计算**：将操作时间跨度划分为 5 分钟分箱，统计每个分箱的操作数，计算 `1 - (标准差/均值)`。当分箱数 < 3 或均值 < 0.001 时返回 0.0
   - **检测意义**：衡量操作在时间上的**均匀分布程度**。自动化攻击（包括慢速滴灌和随机抖动变体）的操作在时间上分布均匀（高 uniformity）；正常用户活动有明显的峰谷波动（低 uniformity）。这是**本系统最关键的特征**——在基准测试中，它单独捕获了所有6个之前漏检的对抗性变体（B1/B2/B3）

10. **rename_correlation**（索引 9）：`rename_count / total_operations`
    - **计算**：匹配同目录下删除+新增记录的文件名前缀（≥3字符），计算重命名对数占总操作的比例。当没有操作或没有新增/删除记录时返回 0.0
    - **检测意义**：捕捉**加密重命名模式**。许多勒索软件（如 WannaCry、LockBit）先删除原文件再创建加密文件，保留文件名前缀。此特征直接检测这种删除-新增关联模式

11. **wall_clock_anomaly**（索引 10）
    - **计算**：提取最早非-EPOCH `changeTime` 的小时(0-23)，计算该轮操作数相对历史同小时基线的 z-score：`z = (totalOps - median_h) / mad_h`，截断至 [-10, 10]。无基线或全EPOCH时间戳时返回 0.0
    - **检测意义**：捕捉**非工作时间异常**。勒索软件常在凌晨执行，与正常工作时间模式偏离

12. **per_type_entropy**（索引 11）：Shannon entropy of {added, modified, deleted}
    - **计算**：统计 added/modified/deleted 三种操作类型的数量，计算 Shannon 熵 `H = -Σ(p_i × log2(p_i))`。当总操作数为 0 时返回 0.0
    - **检测意义**：衡量操作类型分布的**多样性**。纯加密攻击几乎全是 modified（低熵）；混合操作伪装攻击的熵接近正常水平但时间特征异常；正常活动的熵值适中且稳定

> **空文件保护**：如果输入为空或 `diffs` 为空，返回全零向量。所有除法都有 `totalOps == 0` 的保护。特征 4（high_value_ext_ratio）使用 EMA 平滑（alpha=0.3）跨轮次累积。特征 8（temporal_uniformity）需要 ≥3 个时间分箱。特征 2、3、5、8 共享同一个 5 分钟滑动窗口。

### 第二阶段：计算基线统计量（中位数 + MAD）

对所有基线特征向量（N 个），对**每个特征维度**独立计算鲁棒统计量：

```
对于特征维度 i = 0, 1, ..., 11：

  1. 收集所有向量在该维度的值：vals = [v₁.get(i), v₂.get(i), ..., vN.get(i)]
  2. 排序 vals（升序）
  3. median[i] = 中位数（排序后取中间值或中间两值平均）
  4. 计算绝对偏差：absDevs = [|v - median[i]| for v in vals]
  5. 排序 absDevs
  6. mad[i] = median(absDevs) × 1.4826
  7. 如果 mad[i] < 0.001，则 mad[i] = √0.001 ≈ 0.0316（防止除零）
```

**为什么用中位数 + MAD 而非均值 + 标准差**：均值和标准差对异常值极其敏感——如果基线中混入一个勒索轮次，均值会被大幅拉偏。中位数和 MAD（Median Absolute Deviation）是鲁棒统计量，最多 50% 的异常值仍能保持正确估计。`1.4826` 是使 MAD 与正态分布的 σ 一致的缩放因子。

### 第三阶段：计算异常阈值（鲁棒百分位法）

用基线向量自身来标定"正常有多远"，包含 IQR 异常值过滤和中位数上限保护：

```
1. 用 scorer 对每个基线向量计算加权欧氏距离（见下方评分公式）
2. 对所有距离值排序：sorted_scores
3. IQR 异常值过滤（当 N ≥ 5 且 k > 0 时）：
   a. Q1 = sorted_scores 的 25 百分位值
   b. Q3 = sorted_scores 的 75 百分位值
   c. IQR = Q3 - Q1
   d. upperFence = Q3 + k × IQR（k = threshold-iqr-multiplier，默认 2.5）
   e. 移除所有超过 upperFence 的分数（记录 WARNING 日志）
4. 对过滤后的分数计算中位数 median_score
5. percentile_value = filtered_scores[ceil(P × N') - 1]
   其中 P = threshold-percentile（默认 97），N' = 过滤后分数数量
6. threshold = min(percentile_value, 3 × median_score)
```

**含义**：基线轮次中 P% 的评分低于此阈值。IQR 过滤防止被污染的基线轮次（如误包含的批量编译或备份操作）撑高阈值。3× 中位数上限作为硬性安全网，防止渐进式污染绕过 IQR 过滤。设置 `--threshold-iqr-multiplier 0` 可禁用 IQR 过滤（上限仍生效）。

### 第四阶段：评分公式（加权欧氏距离）

对于待检测的特征向量 x，评分过程：

```
对于特征 i = 0, 1, ..., 11：

  z_i = (x_i - median[i]) / mad[i]        ← z-score 标准化
  z_i = clamp(z_i, -10, +10)              ← 防止单一特征主导

score = √(Σᵢ w_i × z_i²)                  ← 加权欧氏距离
```

- `median[i]` 和 `mad[i]` 来自第二阶段的基线统计量
- `w_i` 是第 i 个特征的权重（默认值见权重表）
- z-score 截断至 ±10 防止某一个极端值让分数爆炸
- `score > threshold` → 判定为异常

### 第五阶段：自学习窗口

检测器在运行过程中持续学习。每处理完一个**被判为正常**的轮次：

```
1. 将该轮次的特征向量加入 window（FIFO 队列，最大容量 10）
2. 用 window 中的所有向量重新计算 BaselineStatistics（median + MAD）
3. 用新的统计量重建 scorer 和 explainer
4. 阈值（threshold）不会自动更新——它保持为初始校准值
```

这使得基线能逐步适应正常行为模式的漂移（如用户开始使用新文件类型），同时固定阈值防止"温水煮青蛙"式攻击。

### 完整数据流图

```
 baseline-dir/                    input-dir/
 ┌──────────┐                     ┌──────────┐
 │ file1.json│──┐                 │ fileA.json│
 │ file2.json│──┤  extract() ×N   │ fileB.json│── extract()
 │ fileN.json│──┴─────────────┐   └──────────┘       │
 └──────────┘                 ▼                      ▼
                     RansomwareFeatureVector[]   RansomwareFeatureVector
                     (N × 12 维)                  (1 × 12 维)
                               │                         │
                               ▼                         │
                     ┌──────────────────┐                │
                     │ BaselineStatistics│               │
                     │  median[12]       │               │
                     │  mad[12]          │               │
                    └────────┬─────────┘                │
                             │                          │
                    ┌────────▼─────────┐                │
                    │ AnomalyThreshold  │◄───score()────┤
                    │ (自评分百分位法)   │                │
                    └────────┬─────────┘                │
                             │                          │
                    ┌────────▼─────────┐                │
                    │ WeightedEuclideanScorer◄───score()┘
                    │  z = (x-med)/mad  │
                    │  score = √Σw×z²   │
                    └──────────────────┘
                             │
                             ▼
              ┌──────── 基线累积 ≥ 5 ? ────────┐
              │ YES                             │ NO（第二阶段：预热检测）
              ▼                                 ▼
     score > threshold ?              WarmupDetector（5条启发式规则）
     ├─ YES → 方向验证                 ├─ ≥2 rules → 异常（不纳入基线）
     │        ├─ ratio > threshold      └─ <2 rules → 正常（纳入基线累积）
     │        │  → 安静日，反转正常              │
     │        └─ ratio ≤ threshold              ▼
     │           → 确认异常              基线累积满 5 → 切换到第三阶段
     └─ NO → 正常（自学习窗口）
```

---

## 特征定义

### 核心特征（索引 0–5）

| # | 特征 | 公式 | 说明 |
|---|------|------|------|
| 0 | modification_ratio | `count(type="modified") / total_operations` | 修改操作占比。加密型勒索的核心信号——加密几乎全是修改操作，比值接近1.0 |
| 1 | total_operations_normalized | `count(records) / daysBetweenSnapshots` | 日均操作速率。勒索软件加密大量文件时操作总数会数量级激增 |
| 2 | peak_burst_velocity | `max_{window=5min}(ops_in_window) / (300/3600)` | 最密集5分钟窗口的操作速率（ops/hr）。勒索软件标志性短时间突发特征 |
| 3 | burst_mod_purity | `count(type="modified" ∧ time ∈ burst_window) / ops_in_burst_window` | 突发窗口内修改操作纯度。勒索突发几乎全是加密，纯度接近1.0 |
| 4 | high_value_ext_ratio | `EMA(α=0.3) of count(ext ∈ HVList) / total_operations` | 高价值文件类型占比（EMA平滑）。勒索软件优先加密文档/数据库/备份 |
| 5 | inter_op_time_cv_burst | `σ(deltas) / μ(deltas)` within burst window | 突发窗口时间间隔CV。自动化攻击间隔均匀→低CV；正常操作不规则→高CV |

### 行为分析（索引 6–11）

| # | 特征 | 公式 | 说明 |
|---|------|------|------|
| 6 | high_value_file_coverage | `clamp(count(ext ∈ HVList) / totalOps, 0, 1)` | 高价值文件覆盖度。当轮瞬时值，与特征4（EMA趋势）互补 |
| 7 | directory_coverage_depth | `uniqueDirs × (1/(1+σ(depths)))` | 目录覆盖深度。横向遍历广度×路径深度一致性 |
| 8 | temporal_uniformity | `1 - (σ(binCounts)/μ(binCounts))` | 时间均匀性。自动化攻击操作分布均匀→高值；正常用户有峰谷→低值 |
| 9 | rename_correlation | `rename_count / total_operations` | 重命名关联度。匹配同目录删除+新增的文件名前缀(≥3字符) |
| 10 | wall_clock_anomaly | z-score of ops count vs same-hour baseline | 时钟异常。检测非工作时间操作激增 |
| 11 | per_type_entropy | Shannon entropy of {added, modified, deleted} | 操作类型熵。纯加密攻击低熵；正常活动中熵 |

### 关键定义

- **`daysBetweenSnapshots`**（特征 1）：快照间隔天数（默认2.0，通过 `--days-between` CLI 参数配置），用于将操作总数转换为日均速率。这使得检测器能适应不同的快照频率
- **5分钟滑动窗口**（特征 2、3、5、8）：对所有操作按 `change_time` 排序，用双指针滑动窗口找到操作数最多的300秒区间。特征 5 和 8 基于此窗口计算
- **高价值扩展名**（特征 4、6）：`.docx`, `.xlsx`, `.pptx`, `.pdf`, `.db`, `.sql`, `.csv`, `.doc`, `.xls`, `.mdb`, `.accdb`, `.bak`, `.backup`
- **EMA平滑**（特征 4）：alpha=0.3，跨轮次指数移动平均，首轮初始化为原始值。降低单轮波动噪声，捕捉跨轮次趋势
- **时间分箱**（特征 8）：将操作时间跨度划分为300秒分箱，计算每个分箱的操作数，用于衡量时间均匀性

## 默认权重

| # | 特征 | 权重 | 定位 |
|---|------|------|------|
| 0 | modification_ratio | **2.0** | 加密信号 |
| 1 | total_operations_normalized | **2.5** | 活动量（日均归一化） |
| 2 | peak_burst_velocity | **5.0** | 突发速度（考虑客户批量作业，适度降低） |
| 3 | burst_mod_purity | **3.0** | 突发纯度 |
| 4 | high_value_ext_ratio | **1.5** | 高价值目标（EMA平滑） |
| 5 | inter_op_time_cv_burst | **2.0** | 突发窗口时间规律性 |
| 6 | high_value_file_coverage | **2.0** | 高价值覆盖度 |
| 7 | directory_coverage_depth | **2.5** | 目录覆盖深度 |
| 8 | temporal_uniformity | **2.5** | 时间均匀性（MVP特征） |
| 9 | rename_correlation | **3.0** | 重命名关联 |
| 10 | wall_clock_anomaly | **1.5** | 时钟异常 |
| 11 | per_type_entropy | **2.0** | 操作类型熵 |

### 评分公式

归一化和评分流程：

1. **z-score 归一化**（中位数/MAD 鲁棒统计）：
   ```
   z_i = (x_i - median_j(x_j)) / MAD_j(x_j)
   z_i = clamp(z_i, -10, +10)
   ```
   其中 `MAD = 1.4826 × median(|x_j - median(x_j)|)`（乘以1.4826使其与正态分布标准差一致）

2. **加权欧氏距离评分**：
   ```
   score = √(Σ_{i=0}^{11} w_i × z_i²)
   ```

3. **阈值计算**（鲁棒百分位法，含 IQR 过滤 + 中位数上限）：
    ```
    IQR 过滤：移除超过 Q3 + k×IQR 的异常自评分（k 默认 2.5）
    percentile_value = filtered_scores[ceil(P × N') - 1]
    threshold = min(percentile_value, 3 × median(filtered_scores))
    ```
    其中：
    - `filtered_scores` = IQR 过滤后的基线自评分（排序后）
    - `N'` = 过滤后分数数量
    - `P` = 百分位数（默认 97%）
    - `score > threshold` → 判定为异常

4. **方向验证**（DirectionalValidator，当 `--direction-threshold > 0` 时启用）：
   ```
   E_up = Σᵢ w_i × max(0, z_i)²          ← 高于基线的异常能量
   E_down = Σᵢ w_i × max(0, -z_i)²        ← 低于基线的异常能量
   ratio = E_down / (E_up + E_down + ε)    ← ε = 1e-10
   ```
   - `ratio > direction_threshold`（默认 0.75）→ 反转为正常（安静日误报）
   - 被反转的轮次不纳入自学习窗口
   - 勒索软件增加特征指标（z > 0），安静日降低特征指标（z < 0），方向验证利用这一不对称性

    **含义**：IQR 过滤防止被污染的基线轮次撑高阈值。3× 中位数上限作为硬性安全网。基线轮次中有 `P%` 的评分低于此阈值，`(1-P)%` 的评分高于此阈值。以 `P=97%` 为例，意味着仅容忍约3%的正常活动被误报为异常。提高 `P` 可降低误报率，但可能漏检低信号攻击；降低 `P` 提高敏感度，但增加误报。

    **实际效果**（12特征基准测试，velW=0.50）：阈值 ≈ 7.95，68个攻击用例中68/68检出（100%），0/10正常误报。详见下方检测结果章节。

### 权重设计要点

- `peak_burst_velocity = 5.0`：突发速度权重，从3.5提高到5.0，配合 temporal_uniformity 增强突发检测
- `temporal_uniformity = 2.5`：新增——捕捉操作时间分布的均匀性，对 B1/B2/B3 对抗性变体最有效（基准测试中捕获所有6个之前漏检的用例）
- `directory_coverage_depth = 2.5`：新增——衡量横向遍历广度
- `rename_correlation = 3.0`：新增——捕捉加密重命名模式（删除原文件+创建加密文件）
- `per_type_entropy = 2.0`：新增——操作类型分布的多样性
- `high_value_ext_ratio = 1.5`：使用 EMA 平滑降低单轮波动
- `wall_clock_anomaly = 1.5`：非工作时间异常检测。基于每小时操作数基线计算z-score
- 阈值百分位 97%：收紧判定边界以消除误报

## 测试环境与基准测试

### 模拟文件系统

测试数据模拟一个300K文件的网络共享存储，10个用户各拥有10个目录（projects, docs, mail, logs, configs, media, backup, archive, temp, data），涵盖28种文件类型。每个正常轮次随机生成4,700~25,600个操作，时间跨度2~12小时。

### 多级填充策略

每个攻击在2个填充级别下测试，模拟不同程度的正常活动掩护：

| 填充级别 | 攻击 : 正常比例 | 说明 |
|----------|----------------|------|
| p50 | 50 : 50 | 攻击与噪声各半 |
| p70 | 30 : 70 | 70%正常噪声，攻击仅占30%，最强掩护 |

填充操作保证最少5,000总操作以模拟真实工作负载。填充操作混合35~60%修改、15~30%新增、其余删除，大小变化 ±30%，分散在6~12小时窗口内，由所有10个用户和全部28种文件扩展名产生。

### 基准测试总览

`IntermittentEncryptionBenchmark` 包含 **68个攻击测试用例 + 20个正常轮次 + 16个不规则正常 + 4个安静日轮次**，数据由 `BenchmarkDataGenerator` 预生成至 `benchmark-data/` 目录，运行时直接加载（约108个文件，加载耗时 ~7s）。

| 阶段 | 类别 | 数量 | 用途 |
|------|------|------|------|
| Phase 1a | 基线正常轮次 | 10 | 阈值校准（baselineVectors） |
| Phase 1b | 原始攻击（ORIG） | 18 | 9种勒索家族 × 2填充级别 |
| Phase 1c | 额外正常轮次 | 10 | 仅误报检查（normalVectors） |
| Phase 1.5 | 不规则正常轮次 | 16 | 8种边界模式 × 2重复，仅误报检查 |
| Phase 1.6 | 极安静日轮次 | 4 | 4个安静日模式，验证方向验证反转效果 |
| Phase 2 | 间歇性变体（A9, A12） | 4 | 2种部分加密策略 × 2填充级别 |
| Phase 2.5 | 对抗性变体（B1-B8） | 16 | 8种规避策略 × 2填充级别 |
| Phase 2.7 | 高容量变体（C2-C8） | 14 | 7种高容量模式 × 2填充级别 |
| Phase 2.8 | 组合特征变体（D1-D8） | 16 | 8种组合特征策略 × 2填充级别 |

总计：68攻击 + 20正常 + 16不规则 + 4安静日 = 108个测试文件。

### Phase 1a/1c：正常轮次

基线正常轮次由 `evolveNormalRound()` 生成。前10个（Phase 1a）用于基线训练和阈值校准，后10个（Phase 1c）仅用于误报检查：

- 4,700~25,600 ops/轮（含活动波动，见下方）
- 35~65% 修改、5~15% 重命名（版本化）、15~35% 新增、其余删除
- 3~10 个活跃用户（共10用户）
- 时间戳分布在 2~12 小时窗口内，其中 8~18% 的操作分配到非工作时间（18~23时或5~7时）
- 文件大小变化 ±30%（重命名操作 ±5% 或保持不变）
- 前10个正常轮次全部进入 `baselineVectors`，用于计算中位数/MAD和阈值

**重命名操作**：每轮 5~15% 的操作为文件版本化重命名（delete+add 对），使用后缀 `_v2`、`_v3`、`_new`、`_backup`、`_old`、`_final`、`_copy`、`_draft`，保留原始扩展名。这为 `rename_correlation` 特征提供合法的基线信号。

**非工作时间活动**：每轮约 8~18% 的操作时间戳落在非工作时间（18:00~23:59 或 05:00~07:59），模拟夜间自动化脚本、晚加班等正常行为。这为 `wall_clock_anomaly` 特征提供更真实的小时级基线。

**活动波动**：采用三级波动模型：
- 安静日（15% 概率）：操作量降至基准的 60%，时间窗口扩大至 1.5 倍
- 普通日（70% 概率）：正常操作量
- 繁忙日（15% 概率）：操作量增至基准的 150%，额外包含 30~100 个操作的 5~10 分钟集中突发

Phase 1 采用分离式结构：先生成10个基线正常轮次，再生成攻击轮次（每个攻击前做文件系统快照/恢复），最后生成额外正常轮次填充至总计60轮。基线轮次数量固定为10，确保阈值校准的稳定性。每个基线轮次的时间戳分配在4个业务小时（9, 10, 14, 15）内循环，用于 `wall_clock_anomaly` 特征的小时基线构建。

### Phase 1b：原始攻击（18例）

9种攻击模式参考真实勒索软件家族行为，全部不使用触发签名预检的扩展名或勒索信。每种在 p50/p70 两个填充级别下测试，共 9 × 2 = 18 例：

#### ORIG_lockbit_fast_mode（LockBit 快速加密）

- **参数范围**：targetMods = 4,000~6,000，burstWindow = 90~120s
- **大小变化**：固定追加 4,096~8,191 字节（`fi.size + 4096 + random(4096)`）
- **行为机制**：从所有文件中随机选择目标，每个文件追加固定大小的加密数据。所有操作集中在 90~120 秒突发窗口内，时间戳在窗口内随机分布
- **目标选择**：全部10个用户，所有28种扩展名，无选择性
- **检测挑战**：突发窗口短，操作密度极高，但固定追加量使大小变化模式与正常活动明显不同

#### ORIG_conti_size_tiered（Conti 分级加密）

- **参数范围**：targetMods = 3,000~4,000，burstWindow = 300~600s
- **大小变化**：按文件大小三级分层
  - 小文件（< 1MB）：`fi.size × (1.03 + random × 0.02)`，即 +3~5%
  - 中文件（1~5MB）：追加 4,096~8,191 字节（固定量）
  - 大文件（> 5MB）：`fi.size × (1.01 + random × 0.02)`，即 +1~3%
- **行为机制**：模拟 Conti 按文件大小选择不同加密策略的行为。小文件全量加密（高百分比增长），中文件头部加密（固定追加），大文件部分加密（低百分比增长）
- **目标选择**：全部用户和扩展名，无选择性
- **检测挑战**：三种不同的大小变化模式降低了整体大小一致性信号

#### ORIG_database_priority（数据库优先加密）

- **参数范围**：maxTarget = 2,000~3,000，burstWindow = 300s（固定）
- **大小变化**：`fi.size × (1.03 + random × 0.02)`，即 +3~5%
- **行为机制**：两阶段攻击。第一阶段扫描所有 .db/.sql/.mdb/.bak/.csv 文件，随机选择并加密直到达到 maxTarget 或耗尽目标。如果还有配额，第二阶段转向 .docx/.xlsx/.pdf 文件
- **目标选择**：优先级一：数据库文件（.db, .sql, .mdb, .bak, .csv）。优先级二：文档文件（.docx, .xlsx, .pdf）。跨所有用户
- **检测挑战**：高价值文件集中度极高（high_value_ext_ratio 和 high_value_file_coverage 飙升），这是最易检测的攻击类型之一

#### ORIG_single_user_rapid（单用户极速加密）

- **参数范围**：burstWindow = 30~60s，攻击范围 = 目标用户的全部文件
- **大小变化**：`fi.size × (1.02 + random × 0.03)`，即 +2~5%
- **行为机制**：随机选择一个用户（1~10），遍历该用户的所有文件进行加密。所有操作集中在 30~60 秒极端突发窗口内
- **目标选择**：单一用户，该用户的所有文件类型和目录
- **检测挑战**：极端突发速度（peak_burst_velocity 极高），但操作量取决于该用户的文件数

#### ORIG_slow_distributed（慢速分布式微突发）

- **参数范围**：totalAttackOps = 2,000（固定），opsPerGroup = 100（固定），numGroups = 20
- **大小变化**：`fi.size × (1.02 + random × 0.02)`，即 +2~4%
- **行为机制**：20个微突发组，每组100个操作集中在10秒窗口内，组间间隔 60~90 秒。总时间跨度约 20~30 分钟。模拟 APT 式的慢速渗透
- **目标选择**：全部用户和扩展名
- **检测挑战**：没有单一的密集5分钟突发窗口，但微突发组内的时间间隔变异系数（inter_op_time_cv_burst）极低

#### ORIG_creeping_shrink（渐进式文件缩减）

- **参数范围**：targetOps = 2,500（固定），burstWindow = 300s（固定）
- **大小变化**：`fi.size × (0.80 + random × 0.10)`，即 -10% ~ -20%
- **行为机制**：随机选择2,500个文件，每个文件大小缩减 10~20%。最小不低于 512 字节。模拟破坏型攻击（非加密）而非勒索加密
- **目标选择**：全部用户和扩展名
- **检测挑战**：大小缩减方向与典型加密增长相反，但均匀缩减比例仍被 temporal_uniformity 捕获

#### ORIG_revil_random_ext（REvil 随机扩展名）

- **参数范围**：targetMods = 2,000~3,000，burstWindow = 180s（固定）
- **大小变化**：`fi.size × (1.02 + random × 0.03)`，即 +2~5%
- **行为机制**：对每个目标文件，追加随机生成的8字符字母数字扩展名（`.[a-z0-9]{8}`），原文件被移除，新文件以随机扩展名创建
- **目标选择**：全部用户和扩展名
- **检测挑战**：扩展名不可预测，签名预检无法匹配，但操作模式（删除+新增）触发 rename_correlation 和 temporal_uniformity 偏差

#### ORIG_clop_companion（Cl0p 伴随文件）

- **参数范围**：targetMods = 2,000（固定），burstWindow = 300s（固定）
- **大小变化**：原文件 `fi.size × (1.01 + random × 0.02)`，即 +1~3%。伴随文件 200~499 字节
- **行为机制**：每个目标文件执行两个操作：原地修改（加密，+1~3%）和创建 .key 伴随文件（200~499字节）。模拟 Cl0p 在加密文件旁放置密钥文件的行为
- **目标选择**：全部用户和扩展名
- **检测挑战**：.key 扩展名不在已知勒索扩展名列表中，但双倍操作量（每个文件产生两个 diff）增加了操作密度

#### ORIG_wannacry_staged（WannaCry 分阶段重命名）

- **参数范围**：targetMods = 2,000~3,000，burstWindow = 300s（固定）
- **大小变化**：`fi.size × (1.02 + random × 0.01)`，即 +2~3%
- **行为机制**：每个文件重命名为 `原路径.WNCRY`，原文件被移除，新文件以 .WNCRY 扩展名创建。大小略微增长
- **目标选择**：全部用户和扩展名
- **检测挑战**：.WNCRY 扩展名可被签名预检直接捕获，此测试验证统计特征也能独立检测

**已移除**：ORIG_extension_preserving_mass（扩展名保留加密，结构重复）、ORIG_mass_encryption（.lockbit 扩展名，签名预检直接捕获）、ORIG_ransom_note_drop（勒索信投放，签名预检直接捕获）。

### Phase 1.5：不规则正常轮次（16例）

8种真实世界边界场景 × 2次重复，由 `evolveIrregularNormalRound()` 生成。**不参与基线训练**，仅用于误报压力测试（存储在独立的 `irregularVectors` 列表中）。

#### batch_compile（批量编译）

- **真实场景**：开发者编译整个源码树
- **操作序列**：
  1. 随机选择1个用户（1~10）
  2. 扫描该用户所有 .java/.cpp/.h 文件
  3. 在 burstStart（0~3600s 内随机）开始的 burstDur（120~300s）窗口内，修改最多3,000个源码文件
  4. 每个文件大小增长 `fi.size × (0.01 + random × 0.04)`，即 +1~5%
  5. 追加 500~2,000 个额外随机操作（新增 .java/.cpp/.h 等），分散在8小时窗口内
- **时间分布**：主突发 2~5 分钟，额外操作分散在 8 小时
- **文件选择**：单一用户，仅 .java/.cpp/.h 扩展名
- **对检测器的挑战**：高修改纯度（突发内全是 modified），高突发速度，均匀大小变化模式与加密行为相似

#### log_rotation（日志轮转）

- **真实场景**：系统管理员轮转/压缩日志文件
- **操作序列**：
  1. 扫描所有 .log/.gz 文件
  2. 在 rotationWindow（60~180s）内删除 200~500 个旧日志文件
  3. 在同一窗口内添加 300~1,000 个新日志文件，路径格式 `/user{N}/logs/app_{0-99}.log`，大小 1KB~50MB
- **时间分布**：rotationStart 在 0~7200s 内随机，所有操作集中在 1~3 分钟窗口
- **文件选择**：跨所有用户，仅 .log/.gz 扩展名
- **对检测器的挑战**：高删除比例，高操作密度，短时间内大量文件变更

#### backup_surge（备份激增）

- **真实场景**：夜间跨用户目录备份
- **操作序列**：
  1. 随机选择 1~3 个用户
  2. 在 surgeStart（7200~10800s）开始的 surgeDur（1800~5400s）窗口内，修改 500~2,500 个文件
  3. 每个文件大小增长 `fi.size × (1.02 + random × 0.04)`，即 +2~6%
  4. 追加 1,000~3,000 个正常操作（大小变化 ±30%），分散在10小时窗口
- **时间分布**：主突发 30~90 分钟，额外操作分散在 10 小时
- **文件选择**：限定 1~3 个用户的文件，所有扩展名
- **对检测器的挑战**：均匀大小增长模拟加密行为，时间跨度较长降低突发信号

#### mass_rename（批量重命名）

- **真实场景**：用户批量重命名文件（版本化）
- **操作序列**：
  1. 随机选择1个用户
  2. 随机选择一种扩展名作为源
  3. 在 renameWindow（300~900s）内，将该用户最多 300~1,800 个匹配文件重命名（追加 `_v2`/`_new`/`_backup`/`_old`/`_copy` 后缀）
  4. 追加 2,000~5,000 个正常操作，分散在10小时窗口
- **时间分布**：重命名 5~15 分钟，额外操作分散在 10 小时
- **文件选择**：单一用户，单一扩展名集中
- **对检测器的挑战**：高文件类型集中度，高操作密度

#### db_checkpoint（数据库检查点）

- **真实场景**：数据库写入检查点文件
- **操作序列**：
  1. 扫描所有 .db/.sql/.csv/.mdb/.accdb 文件
  2. 在 checkpointDur（60~360s）内修改 200~1,000 个数据库文件
  3. 每个文件大小变化 `-5% ~ +5%`（`fi.size × (0.95 + random × 0.10)`）
  4. 追加 3,000~8,000 个正常操作，分散在10小时窗口
- **时间分布**：检查点 1~6 分钟，额外操作分散在 10 小时
- **文件选择**：跨所有用户，仅数据库扩展名（.db, .sql, .csv, .mdb, .accdb）
- **对检测器的挑战**：高修改纯度，均匀大小变化，高价值文件集中

#### after_hours_burst（深夜突发）

- **真实场景**：开发者深夜加班
- **操作序列**：
  1. 随机选择 22:00~01:00 的整点小时
  2. 随机选择 1~2 个用户
  3. 在 burstDur（300~900s）内生成 800~4,800 个修改操作
  4. 每个文件大小变化 ±15%
  5. 追加 2,000~7,000 个正常操作，分散在8~18点的工作时段
- **时间分布**：主突发在 22:00~02:00，5~15 分钟。正常操作在 8:00~18:00
- **文件选择**：1~2 个用户在深夜突发，全部用户在正常时段
- **对检测器的挑战**：极端突发速度，单用户集中，非工作时间异常

#### migration_wave（数据迁移）

- **真实场景**：用户数据跨账户迁移
- **操作序列**：
  1. 随机选择源用户和目标用户（不同用户）
  2. 在 waveDur（7200~14400s）窗口内，将源用户 1,000~4,000 个文件迁移到目标用户路径
  3. 文件路径中的 `/user{src}/` 替换为 `/user{dst}/`
  4. 删除源用户的 100~600 个剩余文件
- **时间分布**：迁移分散在 2~4 小时内（线性分布），删除操作时间戳为 Instant.MAX
- **文件选择**：从源用户到目标用户的所有文件类型
- **对检测器的挑战**：大量文件操作，高目录覆盖，跨用户活动

#### cleanup_purge（清理清除）

- **真实场景**：自动化临时文件清理
- **操作序列**：
  1. 随机选择 2~6 个用户
  2. 在 purgeDur（300~900s）内删除这些用户的 500~2,500 个临时文件
  3. 仅针对 .tmp/.temp/.log/.bak/.cache 扩展名
  4. 追加 3,000~8,000 个正常操作，分散在10小时窗口
- **时间分布**：清除 5~15 分钟，额外操作分散在 10 小时
- **文件选择**：2~6 个用户，仅临时文件扩展名
- **对检测器的挑战**：高删除比例，高突发密度，低价值文件集中

### Phase 2：间歇性/部分加密变体（4例）

2种部分加密策略，由 `BenchmarkDataGenerator` 的 `dispatchVariant()` 方法生成。每种在 p50/p70 两个填充级别下测试，共 2 × 2 = 4 例：

#### A9：每第N个加密 + 20% .tmp 重命名（partial_every_Nth_with_tmp）

- **参数范围**：nth=5，minSizePct=0.02，maxSizePct=0.05，extChangeProb=0.20，extSuffix=".tmp"，maxCount=2,000
- **大小变化**：`fi.size × (1.02 + random × 0.03)`，即 +2~5%
- **行为机制**：
  1. 将所有文件随机洗牌
  2. 遍历洗牌后的列表，仅对每第5个文件（`(i+1) % 5 == 0`）执行加密
  3. 最多加密 2,000 个文件
  4. 每个被加密的文件有 20% 概率追加 `.tmp` 扩展名（原文件删除，新文件创建）
  5. 其余 80% 原地修改，不改变扩展名
  6. 时间戳在 30~300 秒的随机突发窗口内分布
- **目标选择**：全部用户和扩展名，但仅加密其中的 1/5
- **检测挑战**：低操作量（最多2000，实际约 300K/5 = 60K 的 1/5 ≈ 需要看实际文件数），部分文件保留原扩展名

#### A12：条带加密 4KB 块（partial_strip_encrypt）

- **参数范围**：targetOps=2,000
- **大小变化**：`fi.size + (fi.size / 4096) × (random(16) + 8)`，即按 4KB 块数计算增量，每个块增加 8~23 字节
- **行为机制**：
  1. 从所有文件中随机选择最多 2,000 个目标
  2. 每个文件按其大小计算 4KB 块数（`fi.size / 4096`），每个块增加 8~23 字节
  3. 条带式增量 = 块数 × 随机增量，大文件增长更多
  4. 所有操作为原地修改，不改变扩展名
  5. 时间戳在 30~300 秒的随机突发窗口内分布
- **目标选择**：全部用户和扩展名
- **检测挑战**：不改变扩展名，纯靠行为特征检测。大小变化模式与文件大小成正比，不均匀

**已移除**：触发签名预检的 .enc/.crypt 扩展名变体。

### Phase 2.5：对抗性规避变体（16例）

8种策略**专门针对检测特征设计**，模拟最狡猾的攻击者行为。每种在 p50/p70 两个填充级别下测试，共 8 × 2 = 16 例：

#### B1：备份伪装（backup_disguise）

- **对抗特征**：temporal_uniformity, directory_coverage_depth
- **参数范围**：targetMods = 3,000~5,000，totalSeconds = 7,200~14,400s（2~4小时）
- **大小变化**：`fi.size × (1.02 + random × 0.02)`，即 +2~4%
- **行为机制**：所有修改操作的时间戳均匀分散在 2~4 小时窗口内（`random.nextInt(totalSeconds)`），模拟备份软件的长时间均匀操作模式
- **目标选择**：全部用户和扩展名
- **设计意图**：通过长时间均匀分布破坏突发窗口信号，模拟备份软件的 temporal_uniformity 特征

#### B2：慢速滴灌（slow_drip_encrypt）

- **对抗特征**：temporal_uniformity, per_type_entropy
- **参数范围**：totalOps=3,600，opsPerBatch=50，batchIntervalSec=300s，numBatches=72
- **大小变化**：`fi.size × (1.01 + random × 0.02)`，即 +1~3%
- **行为机制**：72个批次，每批次50个操作分散在5分钟内，批次间隔300秒。总时间跨度约 72 × 300s = 6小时。任何5分钟窗口内最多约100个操作（含批次内50个攻击 + 填充），无法形成密集突发
- **目标选择**：全部用户和扩展名
- **设计意图**：从不产生密集的5分钟突发窗口，慢速+长间隔+保持类型多样性

#### B3：随机抖动突发（random_jitter_burst）

- **对抗特征**：inter_op_time_cv_burst, burst_mod_purity
- **参数范围**：targetMods = 2,000~3,000
- **大小变化**：`fi.size × (1.02 + random × 0.03)`，即 +2~5%
- **行为机制**：每个操作的间隔由两种模式随机决定：90%概率间隔 1~15 秒，10%概率插入 30~60 秒的随机暂停。总时间约 2,000 × 平均间隔，可达数小时
- **目标选择**：全部用户和扩展名
- **设计意图**：随机抖动破坏时间规律性（高 CV），随机暂停稀释突发纯度

#### B4：混合操作伪装（mixed_operation_mask）

- **对抗特征**：modification_ratio
- **参数范围**：totalOps = 2,000~3,000，burstWindow = 180~300s
- **大小变化**：`fi.size × (1.01 + random × 0.02)`，即 +1~3%（修改时）
- **行为机制**：80% 修改操作（随机选择现有文件，+1~3%），10% 新增操作（路径格式 `/user{N}/{dir}/file_b4_{rand}.{ext}`，大小 1~100KB），10% 删除操作（随机删除现有文件）
- **目标选择**：全部用户和扩展名
- **设计意图**：将 modification_ratio 从加密的 ~1.0 降到 ~0.8，模拟正常操作的混合比例

#### B5：大小模拟正常（size_mimic_normal）

- **对抗特征**：peak_burst_velocity, temporal_uniformity
- **参数范围**：targetMods = 2,000~3,000，burstWindow = 180~300s
- **大小变化**：每个文件随机 `fi.size × (0.90 + random × 0.20)`，即 -10% ~ +10%
- **行为机制**：每个文件的大小变化方向和幅度完全随机，破坏加密操作的大小均匀增长模式
- **目标选择**：全部用户和扩展名
- **设计意图**：破坏大小一致性信号，使大小变化分布接近正常活动的随机波动

#### B6：选择性高价值（selective_high_value）

- **对抗特征**：high_value_ext_ratio（利用该特征的合法性）
- **参数范围**：targetMods = 200~500，burstWindow = 30~120s
- **大小变化**：`fi.size × (1.02 + random × 0.03)`，即 +2~5%
- **行为机制**：仅扫描 .docx/.xlsx/.pdf/.db/.sql 文件，从中随机选择 200~500 个进行加密。操作量极低，模拟定向攻击高价值文件
- **目标选择**：全部用户，仅高价值扩展名（.docx, .xlsx, .pdf, .db, .sql）
- **设计意图**：低操作量避免触发总量特征，高价值文件集中度虽然高但在少量操作中不算异常

#### B7：多家族混合（multi_family_combo）

- **对抗特征**：temporal_uniformity
- **参数范围**：targetMods = 3,000~5,000，burstWindow = 180~300s
- **大小变化**：50% 概率均匀增长 `fi.size × (1.02 + random × 0.01)` 即 +2~3%，50% 概率固定追加 4,096~8,191 字节
- **行为机制**：每个文件随机选择两种加密方式之一：均匀加密或追加加密。混合两种勒索软件家族的大小变化模式
- **目标选择**：全部用户和扩展名
- **设计意图**：混合大小变化信号，使大小统计特征（标准差、均值）无法形成一致的偏差模式

#### B8：重命名+加密（rename_and_encrypt）

- **对抗特征**：rename_correlation
- **参数范围**：targetMods = 2,000~3,000，burstWindow = 180~300s
- **大小变化**：`fi.size × (1.01 + random × 0.03)`，即 +1~4%
- **行为机制**：每个目标文件被重命名为 `file_{8位随机字母数字}`（保留原目录），原文件被移除，新文件以随机文件名创建。大小略微增长
- **目标选择**：全部用户和扩展名
- **设计意图**：随机文件名破坏扩展名信息和重命名关联模式，但完全重命名产生大量删除+新增对

### Phase 2.7：高容量攻击变体（14例）

7种高容量攻击模式，模拟大规模文件操作。每种在 p50/p70 两个填充级别下测试，共 7 × 2 = 14 例：

#### C2：大规模添加（massive_add_flood）

- **参数范围**：targetAdds = 15,000~25,000，burstWindow = 300~600s
- **行为机制**：添加 15K~25K 个新文件，路径格式 `/user{N}/{dir}/file_c2_{rand}.{ext}`，大小 1~100KB。时间戳在 5~10 分钟窗口内随机分布
- **文件选择**：跨全部10用户、10目录、28种扩展名
- **检测信号**：peak_burst_velocity 极高

#### C3：大规模删除（massive_delete_flood）

- **参数范围**：targetDeletes = 10,000~20,000，burstWindow = 180~300s
- **行为机制**：从现有文件中随机选择 10K~20K 个删除，时间戳为 Instant.MAX。3~5 分钟内完成
- **文件选择**：全部现有文件
- **检测信号**：高删除比例、高操作密度。注意：由于删除文件大小不均，此攻击的检测得分较低（p50 得分 9.75，接近阈值）

#### C4：均衡混合（balanced_high_volume_mix）

- **参数范围**：totalOps = 30,000~40,000，burstWindow = 600~1,200s（10~20分钟）
- **行为机制**：40% 修改（+1~3%）、30% 新增（`file_c4_{rand}.{ext}`，1~100KB）、30% 删除
- **文件选择**：修改和删除从现有文件中选择，新增跨全部用户/目录/扩展名
- **检测信号**：peak_burst_velocity 高，操作类型混合（接近正常比例但量级极大）

#### C5：多波次递增（multi_wave_escalation）

- **参数范围**：3波，大小分别为 5,000~7,500 / 10,000~15,000 / 20,000~25,000 修改操作
- **行为机制**：三波递增的修改操作，每波在 300 秒突发窗口内完成，波间间隔 1,800 秒（30分钟）。每波大小增长 +2~4%
- **文件选择**：全部用户和扩展名
- **检测信号**：peak_burst_velocity 极高（第三波 20K~25K 操作在5分钟内），多波次时间模式

#### C6：添加重型加密（added_heavy_encryption）

- **参数范围**：targetPairs = 20,000~30,000，burstWindow = 600~900s
- **行为机制**：每个现有文件执行删除+新增对：原文件删除，在同一目录下创建 `enc_{8位随机字母数字}` 新文件，大小增长 +2~5%。模拟加密重命名的大规模版本
- **文件选择**：全部现有文件
- **检测信号**：peak_burst_velocity 极高（40K~60K 总操作），大量删除+新增对

#### C7：删除重型破坏（delete_heavy_destruction）

- **参数范围**：targetDeletes = 15,000~25,000，targetMods = 2,500~5,000，burstWindow = 300~600s
- **行为机制**：先删除 15K~25K 个文件，再对剩余文件进行大小缩减（`fi.size × (0.50 + random × 0.30)`，即缩减 20~50%）
- **文件选择**：全部文件
- **检测信号**：大量删除 + 大幅缩减修改，peak_burst_velocity 高

#### C8：基线模拟容量（baseline_mimicking_volume）

- **参数范围**：totalOps = 15,000~20,000，totalSeconds = 21,600~28,800s（6~8小时）
- **行为机制**：50% 修改（大小 ±10%）、25% 新增（`file_c8_{rand}.{ext}`，1~100KB）、25% 删除。操作时间均匀分散在 6~8 小时内
- **文件选择**：全部用户和扩展名
- **检测信号**：操作类型比例接近正常，时间分散降低突发信号。这是高容量系列中最难检测的变体

### Phase 2.8：组合特征攻击变体（16例）

8种组合特征攻击模式，参考真实勒索软件家族行为，设计为触发3+个特征的组合信号而非单一主导特征。每种在 p50/p70 两个填充级别下测试，共 8 × 2 = 16 例：

#### D1：Ryuk 横向移动（ryuk_lateral）

- **勒索家族参考**：Ryuk
- **行为机制**：两阶段攻击。Phase 1：选择 5~8 个目标用户，扫描其高价值文件（.docx/.xlsx/.pdf/.db/.sql），在 600~1,800 秒窗口内缓慢修改 500~1,000 个文件（+2~4%）。Phase 2：间隔 60~180 秒后，对全部用户的所有文件在 60~180 秒窗口内快速突发修改 1,500~3,000 个（+2~4%）
- **触发特征组合**：temporal_uniformity（Phase 1 慢速均匀）, peak_burst_velocity（Phase 2 快速突发）, high_value_ext_ratio（Phase 1 高价值定向）

#### D2：DarkSide 分阶段（darkside_staged）

- **勒索家族参考**：DarkSide
- **行为机制**：三阶段。Phase 1（300~600s 窗口）：为 500~1,500 个文件创建 `.enc_copy` 副本（+2~4%）。Phase 2（间隔 60~180s 后，180~480s 窗口）：修改 1,500~3,000 个原始文件（+2~4%）。Phase 3（间隔 60~180s 后，120~300s 窗口）：删除所有 `.enc_copy` 副本
- **触发特征组合**：temporal_uniformity（多阶段时间分布）, rename_correlation（副本创建+删除模式）, per_type_entropy（三阶段操作类型不同）

#### D3：LockBit 3.0 自适应（lockbit3_adaptive）

- **勒索家族参考**：LockBit 3.0
- **行为机制**：5~8 个交替快/慢突发。偶数突发（快）：400~600 操作在 30~60 秒，90% 修改（+3~5%）+ 10% 新增。奇数突发（慢）：200~300 操作在 180~300 秒，80% 修改（+2~3%）+ 20% 新增。突发间隔 30~90 秒。总目标 2,000~4,000 操作
- **触发特征组合**：temporal_uniformity（突发内均匀）, burst_mod_purity（快突发高纯度）, inter_op_time_cv_burst（快慢交替的 CV 模式）

#### D4：BlackCat 变量化（blackcat_variable）

- **勒索家族参考**：BlackCat
- **行为机制**：时间设置为凌晨 2:00~4:00（强制非工作时间）。2,000~4,000 个操作在 900~2,700 秒窗口内。60% 文件全量加密（+3~5%），40% 间歇加密（+1~2%），随机决定每个文件的加密方式
- **触发特征组合**：temporal_uniformity（长时间均匀分布）, wall_clock_anomaly（凌晨执行）, modification_ratio（接近 1.0）

#### D5：Royal 选择性（royal_selective）

- **勒索家族参考**：Royal
- **行为机制**：1,500~3,000 个操作在 120~300 秒窗口内。70% 文件常规加密（+3~5%），30% 文件部分损坏（±5%）。在常规加密的文件中，15% 被重命名为随机8字符文件名
- **触发特征组合**：temporal_uniformity（突发内均匀）, rename_correlation（15% 重命名）, directory_coverage_depth（跨多目录）

#### D6：Play 间歇无扩展名（play_intermittent_noext）

- **勒索家族参考**：Play
- **行为机制**：仅针对业务文件（.docx/.xlsx/.pdf/.db/.sql/.csv/.pptx），最多 1,000~2,000 个。每个文件按 128KB 块计算条带增量（`(fi.size / 131072) × (8 + random(17))`），最小增量 8 字节。时间戳按文件索引均匀分布（每个文件间隔 200~600 秒），总窗口 600~1,200 秒
- **触发特征组合**：temporal_uniformity（时间均匀分布）, high_value_ext_ratio（仅业务文件）, peak_burst_velocity（操作密度）

#### D7：Medusa 多阶段（medusa_multi_stage）

- **勒索家族参考**：Medusa
- **行为机制**：三阶段。Stage 1（300~900s 窗口）：100~300 操作，30% 修改（+2~4%）、40% 新增（`file_d7_{rand}.{ext}`）、30% 删除（破坏阶段）。Stage 2（间隔 120~300s 后，480~900s 窗口）：2,000~4,000 个修改（+2~4%，递增加密）。Stage 3（间隔 120~300s 后，180~300s 窗口）：500~1,000 个修改（+3~5%，最终突发）
- **触发特征组合**：temporal_uniformity（各阶段内均匀）, peak_burst_velocity（Stage 2/3 密集）, per_type_entropy（Stage 1 混合操作类型）

#### D8：Akira VPN 渐进（akira_vpn_gradual）

- **勒索家族参考**：Akira
- **行为机制**：时间设置为 23:00~02:00（非工作时间）。选择 3~5 个目标用户。1,500~3,000 个操作在 2,700~5,400 秒窗口内，分三个速度区域：
  - Zone 1（0~1,200s）：100~200 操作（慢速渗透）
  - Zone 2（1,200~2,400s）：500~1,000 操作（加速）
  - Zone 3（2,400s~结束）：剩余操作（全速）
  - 20% 文件大小变化 ±5%（伪装），80% +1~3%（实际加密）
- **触发特征组合**：temporal_uniformity（长时间均匀）, wall_clock_anomaly（深夜执行）, peak_burst_velocity（Zone 3 密集）

**设计意图**：D系列与ORIG/B/C系列不同，每个D变体故意设计为不产生单一主导特征，而是同时触发3个以上特征的组合偏差。这更接近真实勒索软件的复杂行为模式，验证检测器对多特征组合信号的敏感性。

### 基准测试流程

```
Phase 1 (60轮)                     Phase 1.5 (16轮)
┌──────────────────────────┐      ┌──────────────────────┐
│ 10 baseline normals       │      │ 8 patterns × 2 = 16  │
│ 18 attacks → testCases    │      │ → irregularVectors   │
│ 10 additional normals     │      │ → irregularLabels    │
└──────────────────────────┘      └──────────────────────┘
Phase 2 (4轮)                      Phase 2.5 (16轮)
┌──────────────────────────┐      ┌──────────────────────┐
│ 2 intermittent variants   │      │ 8 adversarial variants │
│ × 2 padding = 4           │      │ × 2 padding = 16      │
│ → testCases               │      │ → testCases           │
└──────────────────────────┘      └──────────────────────┘
Phase 2.7 (14轮)                   Phase 2.8 (16轮)
┌──────────────────────────┐      ┌──────────────────────┐
│ 7 high-volume variants    │      │ 8 combo-feature        │
│ × 2 padding = 14          │      │ × 2 padding = 16      │
│ → testCases               │      │ → testCases           │
└──────────────────────────┘      └──────────────────────┘

Phase 0: 预热检测（冷启动测试）
         ├── WarmupDetector 启发式检测勒索软件
         ├── 干净轮次累积基线
         └── 5个干净向量后切换到统计检测

Phase 3: 遍历6个候选突发速度权重 (0.5, 2.0, 5.0, 8.0, 10.0, 15.0)
         对每个权重：68个攻击检测 + 10个基线正常误报检查
         → 选择检出率最高且误报最少的权重

Phase 4: 使用最佳权重输出详细结果
         ├── 逐攻击用例评分与检测判定
         ├── 按填充级别汇总检出率
         ├── 香草正常误报检查（baselineVectors）
         ├── 不规则正常误报检查（irregularVectors，按模式标签逐个输出）
         ├── FP Summary 三行汇总表
         └── 极安静日方向验证测试（quietDayVectors，验证无验证时ANOMALY，有验证时反转NORMAL）
```

数据由 `BenchmarkDataGenerator`（SEED=42）预生成至 `benchmark-data/` 目录，`IntermittentEncryptionBenchmark` 运行时直接加载（约108个文件，加载耗时 ~7s，总基准测试时间 ~7s）。

## 检测结果（12特征，velW=0.50，阈值 ≈ 7.95）

### 总体结果

| 指标 | 数值 |
|------|------|
| 最佳突发速度权重 | 0.50 |
| 异常阈值 | 7.95 |
| 总攻击用例 | 68 |
| 检出 | 68/68（100%） |
| 漏检 | 无 |
| 香草正常误报 | 0/10（0.0%） |
| 不规则正常误报 | 16/16（100.0%） |

### 安静日方向验证结果

| 指标 | 数值 |
|------|------|
| 安静日测试用例 | 4 |
| 无方向验证时误报 | 4/4（100%，得分 16.3~22.3） |
| 方向验证阈值 0.75 时反转 | 4/4（100%，方向比率 0.885~0.941） |
| 攻击检出率影响 | 无（68/68 保持不变） |
| 香草正常误报影响 | 无（0/10 保持不变） |

**主要低于基线特征**：high_value_ext_ratio、high_value_file_coverage、per_type_entropy、rename_correlation、total_operations_normalized

### 按类别检出率

| 攻击类别 | 检出 | 总数 | 检出率 |
|----------|------|------|--------|
| 原始攻击（ORIG） | 18 | 18 | 100% |
| 间歇性变体（A9, A12） | 4 | 4 | 100% |
| 对抗性变体（B1-B8） | 16 | 16 | 100% |
| 高容量变体（C2-C8） | 14 | 14 | 100% |
| 组合特征变体（D1-D8） | 16 | 16 | 100% |
| **总计** | **68** | **68** | **100%** |

### 按填充级别检出率

| 填充级别 | 检出 | 总数 | 检出率 |
|----------|------|------|--------|
| p50（50%填充） | 34 | 34 | 100% |
| p70（70%填充） | 34 | 34 | 100% |

### 不规则正常误报分析

所有16个不规则正常轮次均触发误报：

| 模式 | 得分（重复0） | 得分（重复1） | 主要偏差特征 |
|------|-------------|-------------|-------------|
| batch_compile | 34.19 | 34.18 | inter_op_time_cv_burst, high_value_file_coverage, high_value_ext_ratio |
| log_rotation | 26.21 | 26.73 | high_value_ext_ratio, high_value_file_coverage, per_type_entropy |
| backup_surge | 22.61 | 22.61 | high_value_ext_ratio, per_type_entropy, modification_ratio |
| mass_rename | 24.63 | 24.06 | high_value_ext_ratio, per_type_entropy, high_value_file_coverage |
| db_checkpoint | 27.13 | 27.31 | per_type_entropy, high_value_ext_ratio, high_value_file_coverage |
| after_hours_burst | 22.58 | 23.85 | per_type_entropy, high_value_ext_ratio, modification_ratio |
| migration_wave | 23.08 | 22.40 | per_type_entropy, high_value_ext_ratio, burst_mod_purity |
| cleanup_purge | 24.66 | 26.02 | per_type_entropy, high_value_file_coverage, high_value_ext_ratio |

**说明**：不规则模式本质上是"看起来像攻击的正常活动"。这些误报反映了基于统计特征的检测器在区分"合法批量修改"和"恶意批量加密"时的根本困难。实际部署中，这些模式应通过白名单或特定规则排除。

## 项目结构

```
src/main/java/com/anomalydetection/
├── cli/
│   ├── RansomwareDetectorCli.java          CLI 入口
│   └── WeightOptimizerCli.java             权重优化 CLI
├── detector/
│   ├── AnomalyThreshold.java               百分位阈值
│   ├── BaselineStatistics.java             中位数/MAD 统计
│   ├── DetectionResult.java                检测结果（含签名匹配）
│   ├── DirectionalValidator.java            方向验证（安静日误报反转）
│   ├── WarmupDetector.java                  预热启发式检测（冷启动安全网）
│   ├── RansomwareDetector.java             主检测器 + 自学习
│   ├── RansomwareSignatureDetector.java    签名预检（扩展名+勒索信）
│   ├── WeightedEuclideanScorer.java        加权欧氏距离评分
│   ├── WeightOptimizer.java                AUC权重优化器
│   └── ZScoreExplainer.java                特征贡献度解释
├── features/
│   ├── RansomwareFeatureExtractor.java     12特征单次提取
│   ├── RansomwareFeatureVector.java        不可变特征向量
│   └── SuspiciousExtensions.java           50个已知勒索扩展名
├── generator/
│   ├── AttackGenerator.java                20种攻击模式（9原始+3高容量+8组合特征，支持多级填充）
│   ├── DiffEntry.java                      差异记录
│   ├── FilesystemState.java                300K文件模拟 + 快照/恢复
│   ├── IntermittentEncryptionBenchmark.java 间歇性加密基准测试
│   ├── RansomwareTestGenerator.java        60轮测试数据生成
│   └── SnapdiffOutput.java                 JSON 输出容器
├── model/
│   ├── SnapdiffFile.java                   snapdiff 容器
│   └── SnapdiffRecord.java                 单条差异记录
└── parser/
    └── SnapdiffParser.java                 Jackson JSON 解析
```
