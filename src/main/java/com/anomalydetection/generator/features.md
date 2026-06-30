# 特征定义

共 **16 个聚合特征**（索引 0-15），按计算阶段分为**增量特征**（accumulate 阶段流式累积）和**排序后特征**（需全局排序后滑动窗口计算）。

---

## 增量特征（F0, F1, F2, F3, F6, F8, F10, F11, F12, F13, F14, F15）

在 `computeIncrementalFeatures()` 中计算，数据来源为 `accumulate(record)` 中累积的计数器。

---

### F0 — modification_ratio

| 项目 | 内容 |
|------|------|
| key | `modification_ratio` |
| 名称 | 修改操作占比 |
| 含义 | MODIFIED 类型操作数占总操作数的比例 |
| 公式 | `modifiedCount / totalRecords` |

**检测意义**：加密型勒索软件（LockBit、Conti 等）的核心信号。正常用户活动是增/删/改混合的，而加密型勒索几乎全是修改操作，比值接近 1.0。

**Demo 用例**：
```
输入: 5 条记录 = 3 MODIFIED + 1 CREATED + 1 DELETED
modifiedCount = 3, totalRecords = 5
F0 = 3 / 5 = 0.6
```

**代码实现**：
```java
// accumulate 中：
if (record.isModified()) modifiedCount++;
totalRecords++;

// finalize 中：
double modRatio = totalRecords > 0 ? (double) modifiedCount / totalRecords : 0.0;
fv.set(FeatureType.MODIFICATION_RATIO, modRatio);
```

---

### F1 — deletion_ratio

| 项目 | 内容 |
|------|------|
| key | `deletion_ratio` |
| 名称 | 删除操作占比 |
| 含义 | DELETED 类型操作数占总操作数的比例 |
| 公式 | `deletedCount / totalRecords` |

**检测意义**：破坏型攻击（删除勒索、数据擦除）的核心信号。接近 1.0 表示纯删除操作。

**Demo 用例**：
```
输入: 10 条记录 = 10 DELETED
deletedCount = 10, totalRecords = 10
F1 = 10 / 10 = 1.0
```

**代码实现**：
```java
// accumulate 中：
if (record.isDeleted()) deletedCount++;
totalRecords++;

// finalize 中：
double delRatio = totalRecords > 0 ? (double) deletedCount / totalRecords : 0.0;
fv.set(FeatureType.DELETION_RATIO, delRatio);
```

---

### F2 — creation_ratio

| 项目 | 内容 |
|------|------|
| key | `creation_ratio` |
| 名称 | 创建操作占比 |
| 含义 | CREATED 类型操作数占总操作数的比例 |
| 公式 | `createdCount / totalRecords` |

**检测意义**：大规模文件创建信号。某些勒索变种会创建大量加密副本，或者攻击者创建勒索信文件时该值显著升高。

**Demo 用例**：
```
输入: 10 条记录 = 7 CREATED + 3 MODIFIED
createdCount = 7, totalRecords = 10
F2 = 7 / 10 = 0.7
```

**代码实现**：
```java
// accumulate 中：
if (record.isCreated()) createdCount++;
totalRecords++;

// finalize 中：
double creRatio = totalRecords > 0 ? (double) createdCount / totalRecords : 0.0;
fv.set(FeatureType.CREATION_RATIO, creRatio);
```

---

### F3 — total_operations_normalized

| 项目 | 内容 |
|------|------|
| key | `total_operations_normalized` |
| 名称 | 日均操作速率 |
| 含义 | 操作总数除以快照间隔天数，转换为日均速率 |
| 公式 | `totalRecords / daysBetween` |

**检测意义**：勒索软件加密大量文件时，操作总数会出现数量级的激增。这是最通用的检测信号——无论攻击者是否改变扩展名或使用间歇性加密，大规模文件操作始终存在。

**Demo 用例**：
```
输入: 10 条记录, daysBetween = 2.0
totalRecords = 10
F3 = 10 / 2.0 = 5.0
```

**代码实现**：
```java
// finalize 中：
double dailyOps = totalRecords / daysBetween;
fv.set(FeatureType.TOTAL_OPERATIONS_NORMALIZED, dailyOps);
```

---

### F6 — high_value_ext_ratio

| 项目 | 内容 |
|------|------|
| key | `high_value_ext_ratio` |
| 名称 | 高价值文件类型占比 |
| 含义 | 高价值扩展名（docx, xlsx, pdf, db 等）的操作数占总操作数的比例 |
| 公式 | `highValueExtCount / totalRecords` |

**检测意义**：勒索软件会优先加密高价值文件类型（文档、数据库、备份文件）以最大化勒索效果。当高价值文件操作比例偏离基线时，表明发生了有针对性的加密。

**高价值扩展名列表**：
```
docx, xlsx, pptx, pdf, db, sql, csv, doc, xls, mdb, accdb, bak, backup
```

**Demo 用例**：
```
输入: 5 条记录 = docx, pdf, db, txt, txt
highValueExtCount = 3 (docx + pdf + db)
F6 = 3 / 5 = 0.6
```

**代码实现**：
```java
// accumulate 中：
if (HIGH_VALUE_EXTENSIONS.contains(ext)) {
    highValueExtCount++;
}

// finalize 中：
double hvRatio = totalRecords > 0 ? (double) highValueExtCount / totalRecords : 0.0;
fv.set(FeatureType.HIGH_VALUE_EXT_RATIO, hvRatio);
```

---

### F8 — directory_coverage_depth

| 项目 | 内容 |
|------|------|
| key | `directory_coverage_depth` |
| 名称 | 目录覆盖深度 |
| 含义 | 受影响目录广度 × 路径深度一致性因子 |
| 公式 | `uniqueDirs × (1 / (1 + σ(depths)))`，其中 depths 为每条记录父路径的"/"层级数 |

**检测意义**：勒索软件横向遍历多级目录，导致受影响的目录范围远超正常行为。同时乘以深度一致性因子——正常用户操作集中在特定深度（如 /user/docs/ → depth=2），攻击者可能遍历不同深度，导致 σ(depths) 增大，降低覆盖度值。

**Demo 用例**：
```
记录:
  /user1/docs/report.docx    → parentDir = /user1/docs,  depth=2
  /user1/docs/finance/a.xlsx → parentDir = /user1/docs/finance, depth=3
  /user2/projects/b.txt      → parentDir = /user2/projects, depth=2

uniqueDirs = {/user1/docs, /user1/docs/finance, /user2/projects}  → 3
depths = [2, 3, 2]
σ(depths) ≈ 0.47
F8 = 3 × (1 / (1 + 0.47)) = 3 × 0.68 ≈ 2.04
```

**代码实现**：
```java
// accumulate 中：
String parentPath = extractParentPath(record.getPath());
uniqueDirs.add(parentPath);
int depth = countOccurrences(parentPath, '/');
pathDepths.add(depth);

// finalize 中：
double coverage = computeDirectoryCoverageDepth();
fv.set(FeatureType.DIRECTORY_COVERAGE_DEPTH, coverage);
```

---

### F10 — rename_correlation

| 项目 | 内容 |
|------|------|
| key | `rename_correlation` |
| 名称 | 重命名关联度 |
| 含义 | 同一目录下 DELETED+CREATED 文件名前缀匹配的对数占总操作数的比例 |
| 公式 | `renameCount / totalRecords` |

**检测意义**：许多勒索软件加密文件后，删除原始文件并创建新的加密文件（如 `report.docx` → `report.encrypted`）。通过检测同目录下 DELETED 和 CREATED 记录的前缀匹配（≥3 字符），识别这种加密-重命名模式。

**配置常量**：
```java
private static final int RENAME_PREFIX_MIN_LENGTH = 3;
```

**Demo 用例**：
```
目录 /user1/docs/:
  DELETED  report.docx         → 前缀 "rep"（前 3 字符）
  CREATED  report.encrypted    → 前缀 "rep"（前 3 字符）
  → 匹配！renameCount++

totalRecords = 10, renameCount = 2
F10 = 2 / 10 = 0.2
```

**代码实现**：
```java
// accumulate 中：
String prefix = filename.substring(0, RENAME_PREFIX_MIN_LENGTH).toLowerCase();
if (record.isDeleted()) {
    deletedPrefixesByDir.computeIfAbsent(parentPath, k -> new HashSet<>()).add(prefix);
} else if (record.isCreated()) {
    createdPrefixesByDir.computeIfAbsent(parentPath, k -> new HashSet<>()).add(prefix);
}

// finalize 中：
double renameCorr = totalRecords > 0 ? (double) computeRenameCount() / totalRecords : 0.0;
fv.set(FeatureType.RENAME_CORRELATION, renameCorr);
```

---

### F11 — hourly_concentration

| 项目 | 内容 |
|------|------|
| key | `hourly_concentration` |
| 名称 | 时间集中度 |
| 含义 | 操作最密集的那个小时占总操作的比例 |
| 公式 | `max(hourlyCounts) / totalRecords`，hourlyCounts 为 24 小时内每小时的记录数 |

**检测意义**：勒索软件加密通常在凌晨或非工作时间集中在 1-2 小时内完成。此时某个小时的操作量占总量的绝大部分，远超正常工作时间分布。

**Demo 用例**：
```
凌晨 3 点加密 500 个文件（全天共 500 条记录）：
  hourlyCounts = [0,0,0,500,0,...,0]
  max = 500, totalRecords = 500
  F11 = 500 / 500 = 1.0（极高度集中 → 异常）

正常工作日（共 500 条记录分散在 9-18 点）：
  hourlyCounts = [0,...,50,60,70,80,60,50,40,30,...]
  max = 80, totalRecords = 500
  F11 = 80 / 500 = 0.16（正常范围）
```

**代码实现**：
```java
// accumulate 中：
int hour = getHourOfDay(record.getChangeTime());
hourlyCounts[hour]++;

// finalize 中：
double conc = computeHourlyConcentration();
fv.set(FeatureType.HOURLY_CONCENTRATION, conc);
```

---

### F12 — hourly_entropy

| 项目 | 内容 |
|------|------|
| key | `hourly_entropy` |
| 名称 | 时间熵 |
| 含义 | 操作在 24 小时中分布的香农熵，衡量分散程度 |
| 公式 | `- Σₕ p(h) × log₂(p(h))`，p(h) = hourlyCounts[h] / totalRecords，h ∈ [0,23] |

**检测意义**：与 F11 互补。F11 检测集中度，F12 检测全时段的分散程度。两者联合可以区分：

| F11 | F12 | 含义 |
|-----|-----|------|
| 高 | 低 | 操作集中在极少数小时（凌晨攻击） |
| 中 | 中 | 正常工作时间分布 |
| 低 | 高 | 操作均匀分散（慢速全时段加密） |

**边界条件**：`p(h) = 0` 时，`0 × log₂(0) = 0`。

**Demo 用例**：
```
凌晨攻击（所有操作在 3:00）：
  p(3) = 1.0, 其他 = 0
  F12 = -1.0 × log₂(1.0) - 0 - 0 - ... = 0（最低熵）

均匀分布在全部 24 小时：
  p(h) = 1/24 ≈ 0.0417
  F12 = -24 × (0.0417 × log₂(0.0417)) = 4.58

正常工作分布在 9:00-17:00（8 小时均匀）：
  p(h) = 1/8 ≈ 0.125（9-17点），其他 = 0
  F12 = -8 × (0.125 × log₂(0.125)) = 3.0
```

**代码实现**：
```java
// finalize 中：
double entropy = computeHourlyEntropy();
fv.set(FeatureType.HOURLY_ENTROPY, entropy);
```

---

### F13 — per_type_entropy

| 项目 | 内容 |
|------|------|
| key | `per_type_entropy` |
| 名称 | 操作类型熵 |
| 含义 | 三种操作类型（CREATED, MODIFIED, DELETED）在记录中的分布熵 |
| 公式 | `- Σ p(i) × log₂(p(i))`，i ∈ {CREATED, MODIFIED, DELETED} |

**检测意义**：纯加密攻击几乎全是修改操作 → 单一类型主导 → 低熵。正常用户活动混合三种类型 → 高熵。

| 场景 | 类型分布 | 熵值 |
|------|---------|------|
| 纯加密（全部 MODIFIED） | p_m=1.0, p_c=0, p_d=0 | 0 |
| 纯破坏（全部 DELETED） | p_d=1.0, p_m=0, p_c=0 | 0 |
| 正常混合 | 三种接近均匀 | ≈ 1.58 |
| 加密+勒索信 | MODIFIED 为主 + CREATED | ≈ 0.5-1.0 |

**Demo 用例**：
```
纯加密：10 条记录全部 MODIFIED
p_m = 1.0, p_c = 0, p_d = 0
F13 = -1.0 × log₂(1.0) - 0 - 0 = 0

正常混合：10 条 = 6 MODIFIED + 2 CREATED + 2 DELETED
p_m = 0.6, p_c = 0.2, p_d = 0.2
F13 = -0.6×log₂(0.6) - 0.2×log₂(0.2) - 0.2×log₂(0.2) ≈ 1.37
```

**代码实现**：
```java
// finalize 中：
double ptEntropy = computePerTypeEntropy();
fv.set(FeatureType.PER_TYPE_ENTROPY, ptEntropy);
```

---

### F14 — extension_count_cv

| 项目 | 内容 |
|------|------|
| key | `extension_count_cv` |
| 名称 | 扩展名变异系数 |
| 含义 | 按扩展名统计修改次数的变异系数（σ/μ） |
| 公式 | `σ(counts) / μ(counts)`，counts 为每个修改扩展名的出现次数 |

**检测意义**：正常用户操作文件类型分散，修改次数在不同扩展名间相对均匀 → CV 较低。勒索软件加密集中攻击特定文件类型（如 .docx, .pdf），导致某些扩展名的修改次数异常高 → CV 升高。

**Demo 用例**：
```
勒索攻击：
  .docx → 500 次, .pdf → 300 次, .xlsx → 200 次
  μ ≈ 333, σ ≈ 125
  F14 ≈ 125 / 333 ≈ 0.375（高 CV → 集中攻击）

正常使用：
  .txt → 50 次, .java → 45 次, .md → 40 次, .json → 35 次
  μ ≈ 42.5, σ ≈ 5.6
  F14 ≈ 5.6 / 42.5 ≈ 0.13（低 CV → 分散操作）
```

**代码实现**：
```java
// accumulate 中：
if (ext != null && record.isModified()) {
    modifiedExtCounts.merge(ext, 1L, Long::sum);
}

// finalize 中：
double extCV = computeExtensionCountCV();
fv.set(FeatureType.EXTENSION_COUNT_CV, extCV);
```

---

### F15 — created_ext_novelty

| 项目 | 内容 |
|------|------|
| key | `created_ext_novelty` |
| 名称 | 新建扩展名新颖度 |
| 含义 | 新建文件的扩展名中，未出现在修改/删除文件中的比例 |
| 公式 | `1 - |createdExts ∩ otherExts| / |createdExts|` |

**检测意义**：勒索软件加密后创建的新文件通常使用新的扩展名（如 .encrypted, .lockbit），这些扩展名在正常操作中不会出现。当新建文件的扩展名大部分是"新颖"的，说明可能发生了加密。

**Demo 用例**：
```
新建文件扩展名: {encrypted, lockbit, txt}
修改/删除文件扩展名: {docx, pdf, txt}
重叠集: {txt}
新颖度 = 1 - 1/3 ≈ 0.67（67% 的新建扩展名未见 → 异常）
```

**代码实现**：
```java
// accumulate 中：
if (ext != null) {
    if (record.isCreated()) createdExts.add(ext);
    if (record.isModified() || record.isDeleted()) otherExts.add(ext);
}

// finalize 中：
double novelty = computeCreatedExtNovelty();
fv.set(FeatureType.CREATED_EXT_NOVELTY, novelty);
```

---

## 排序后特征（F4, F5, F7, F9）

在 `computePostSortFeatures()` 中计算，需要对全量记录按 changeTime 排序后，通过滑动窗口计算。

---

### F4 — peak_burst_velocity

| 项目 | 内容 |
|------|------|
| key | `peak_burst_velocity` |
| 名称 | 突发峰值速率 |
| 含义 | 最密集 5 分钟窗口的操作数折算为每小时速率 |
| 公式 | `maxOpsInWindow × (3600 / 300)` = `maxOpsInWindow × 12` |

**检测意义**：勒索软件的标志性特征是短时间突发——在 30-300 秒内加密数百到数千个文件，远超正常用户的操作速率。即使攻击者尝试放慢速度，在微突发窗口内的密度仍然异常。

**Demo 用例**：
```
输入: 50 条记录，时间戳集中在 300 秒内
maxOpsInWindow = 50（所有记录都在一个窗口内）
F4 = 50 × 12 = 600 ops/hr
```

**代码实现**：
```java
// 全局排序后滑动窗口扫描（300秒窗口）
fv.set(FeatureType.PEAK_BURST_VELOCITY, maxOpsInWindow * HOURLY_RATE_MULTIPLIER);
```

---

### F5 — burst_mod_purity

| 项目 | 内容 |
|------|------|
| key | `burst_mod_purity` |
| 名称 | 突发修改纯度 |
| 含义 | 在 F4 找到的最密集 5 分钟窗口内，修改操作数占该窗口总操作数的比例 |
| 公式 | `maxModifiedInWindow / maxOpsInWindow` |

**检测意义**：区分突发操作的性质。正常用户的突发活动（如批量复制文件）通常是增/删/改混合的，纯度较低；勒索软件的突发窗口内几乎全是修改操作（加密），纯度接近 1.0。

**Demo 用例**：
```
输入: 峰值窗口 50 条 = 40 MODIFIED + 10 CREATED
maxModifiedInWindow = 40, maxOpsInWindow = 50
F5 = 40 / 50 = 0.8
```

**代码实现**：
```java
// 在同一个滑窗扫描中，发现新峰值时同时记录 modifiedInWindow
double burstPurity = maxOpsInWindow > 0 ? (double) maxModifiedInWindow / maxOpsInWindow : 0.0;
fv.set(FeatureType.BURST_MOD_PURITY, burstPurity);
```

---

### F7 — inter_op_time_cv_burst

| 项目 | 内容 |
|------|------|
| key | `inter_op_time_cv_burst` |
| 名称 | 突发窗口时间间隔变异系数 |
| 含义 | 在 F4 的峰值窗口中，连续操作时间戳差值的变异系数 |
| 公式 | `σ(deltas) / μ(deltas)` within burst window |

**检测意义**：衡量操作间隔的规律性。勒索软件自动化加密以匀速执行，间隔均匀 → CV 低。正常用户操作间隔不规则 → CV 高。

**Demo 用例**：
```
峰值窗口内有 5 次操作，时间戳：10:00:00, 10:00:01, 10:00:02, 10:00:03, 10:00:04
deltas = [1, 1, 1, 1] 秒
μ = 1, σ = 0
F7 = 0 / 1 = 0（极低 CV → 自动化攻击）
```

**代码实现**：
```java
// 使用峰值窗口内的时间戳列表计算
double burstCV = computeBurstWindowCV(peakWindowTimestamps);
fv.set(FeatureType.INTER_OP_TIME_CV_BURST, burstCV);
```

---

### F9 — temporal_uniformity

| 项目 | 内容 |
|------|------|
| key | `temporal_uniformity` |
| 名称 | 时间均匀性 |
| 含义 | F4 的峰值 300 秒窗口内，操作在 10 秒子桶中分布的均匀程度 |
| 公式 | `1 - (σ(binCounts) / μ(binCounts))`，其中 binCounts 为 30 个 10 秒桶的计数 |

**检测意义**：自动化攻击的操作在时间上均匀分布→各桶计数接近→σ 低→CV 低→F9 接近 1。人类操作有峰谷→各桶计数差异大→σ 高→CV 高→F9 接近 0。

**配置常量**：
```java
private static final int UNIFORMITY_BIN_WIDTH = 10; // 10秒
private static final int UNIFORMITY_BIN_COUNT = 300 / 10; // 30个桶
```

**Demo 用例**：
```
峰值窗口 300 秒，30 个 10 秒桶：
  桶计数 = [12, 10, 11, 9, 13, 10, 11, 12, 10, 11, ...] 均匀分布
  μ ≈ 10, σ ≈ 1.2
  F9 = 1 - (1.2 / 10) = 0.88（高均匀性 → 自动化攻击）

  桶计数 = [40, 2, 0, 0, 0, 0, 0, 0, 0, 35, ...] 集中分布
  μ ≈ 10, σ ≈ 15
  F9 = 1 - (15 / 10) = -0.5（低均匀性 → 人类操作）
```

**代码实现**：
```java
// 确定峰值窗口后，将窗口内记录按时间分配到 30 个 10 秒桶
double uniformity = computeTemporalUniformity(peakWindowTimestamps);
fv.set(FeatureType.TEMPORAL_UNIFORMITY, uniformity);
```

---

## 汇总表

| 索引 | 特征 key | 核心数据源 | 计算阶段 |
|------|---------|-----------|---------|
| F0 | modification_ratio | modifiedCount, totalRecords | 增量 |
| F1 | deletion_ratio | deletedCount, totalRecords | 增量 |
| F2 | creation_ratio | createdCount, totalRecords | 增量 |
| F3 | total_operations_normalized | totalRecords, daysBetween | 增量 |
| F4 | peak_burst_velocity | 滑窗扫描 maxOpsInWindow | 排序后 |
| F5 | burst_mod_purity | 滑窗扫描 maxModifiedInWindow | 排序后 |
| F6 | high_value_ext_ratio | highValueExtCount, totalRecords | 增量 |
| F7 | inter_op_time_cv_burst | 峰值窗口内 deltas | 排序后 |
| F8 | directory_coverage_depth | uniqueDirs, pathDepths | 增量 |
| F9 | temporal_uniformity | 峰值窗口内桶计数 | 排序后 |
| F10 | rename_correlation | deletedPrefixesByDir, createdPrefixesByDir | 增量 |
| F11 | hourly_concentration | hourlyCounts[24], totalRecords | 增量 |
| F12 | hourly_entropy | hourlyCounts[24] | 增量 |
| F13 | per_type_entropy | modifiedCount, createdCount, deletedCount | 增量 |
| F14 | extension_count_cv | modifiedExtCounts | 增量 |
| F15 | created_ext_novelty | createdExts, otherExts | 增量 |

**增量阶段**：在 `accumulate(record)` 中收集原始数据，在 `computeIncrementalFeatures()` 中计算。

**排序后阶段**：在 `computePostSortFeatures()` 中基于全局排序 + 滑动窗口计算。
