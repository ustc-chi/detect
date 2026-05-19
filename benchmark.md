# 基准测试系统 — 完整概览

## 目的

基准测试（`IntermittentEncryptionBenchmark`）是一个自包含的测试框架，用于验证勒索软件异常检测器对各种攻击模式的检测能力。它回答的核心问题是：**"统计检测器能否捕获所有攻击类型——包括专门为规避检测而设计的对抗性攻击——同时不误报正常活动？"**

实现方式：

1. 模拟一个包含 300,000 个文件的网络共享存储，使用真实的文件类型和目录结构
2. 生成模拟真实勒索软件家族行为的攻击数据（snapdiff JSON 文件）
3. 在每个测试用例上运行完整的检测流水线（特征提取 → 评分 → 阈值比较）
4. 报告逐用例的检测结果、汇总检出率和误报数量

## 模拟文件系统（`FilesystemState`）

**结构**：10 个用户 × 10 个目录 × 3,000 个文件 = 300,000 个文件

```
/vol/share/user{1-10}/{projects,docs,mail,logs,configs,media,backup,archive,temp,data}/file_{0000-2999}.{ext}
```

**28 种文件扩展名**：`.docx`, `.xlsx`, `.pptx`, `.pdf`, `.txt`, `.log`, `.conf`, `.sh`, `.py`, `.java`, `.cpp`, `.h`, `.md`, `.json`, `.xml`, `.sql`, `.jpg`, `.png`, `.gif`, `.mp3`, `.mp4`, `.wav`, `.zip`, `.tar`, `.gz`, `.db`, `.yaml`, `.csv`

**文件大小分布**（偏向小文件，模拟企业共享存储的真实情况）：

| 概率 | 大小范围 |
|---|---|
| 70% | 1–100 KB |
| 20% | 100 KB – 10 MB |
| 9% | 10–100 MB |
| 1% | 100 MB – 1 GB |

**关键操作**：

- `snapshot()` / `restore()` — 复制整个文件映射用于隔离（攻击轮次会修改状态，之后恢复）
- `evolveNormalRound(dayStart)` — 生成 4,700–25,600 个操作（修改/新增/删除/重命名），涉及 3–10 个活跃用户，时间跨度 2–12 小时，含重命名、非工作时间和活动波动。永久修改状态。
- `evolveIrregularNormalRound(dayStart, pattern)` — 生成 8 种边界情况下的正常模式（批量编译、日志轮转等）

## 正常轮次生成（`evolveNormalRound`）

每个正常轮次通过多个独立的随机轴产生差异，确保基线覆盖"正常行为"的广泛分布。

### 变异轴

| 轴 | 范围 | 公式 | 效果 |
|---|---|---|---|
| **活动水平** | 300K 文件的 2%–10% | `0.02 + random×0.08` → 6,000–30,000 操作（下限 1,000） | 总操作量变化 30 倍 |
| **操作类型比例** | 修改 35–65%，重命名 5–15%，新增 15–35%，其余删除 | `modFrac = 0.35 + random×0.30`，`renameFrac = 0.05 + random×0.10`，`addFrac = 0.15 + random×0.20` | 增/删/改/重命名比例每轮不同 |
| **活跃用户** | 3–10 个（共 10 用户） | `3 + random(8)`，Fisher-Yates 洗牌选取 | 少则涉及 30% 文件系统，多则全部 |
| **时间窗口** | 2–12 小时 | `2 + random(11)` | 操作分散在长短不同的时间跨度内 |
| **非工作时间比例** | 8–18% 的操作 | 随机分配到 18:00~23:59 或 05:00~07:59 | 为 wall_clock_anomaly 提供小时级基线 |
| **活动波动** | 三级模型 | 安静 15%（0.6×）/ 普通 70% / 繁忙 15%（1.5× + 突发） | 双峰+突发分布 |

### 重命名操作

每轮 5~15% 的操作为文件版本化重命名（delete+add 对），使用后缀 `_v2`、`_v3`、`_new`、`_backup`、`_old`、`_final`、`_copy`、`_draft`，保留原始扩展名。这为 `rename_correlation` 特征提供合法的基线信号。

### 非工作时间活动

每轮约 8~18% 的操作时间戳落在非工作时间（18:00~23:59 或 05:00~07:59），模拟夜间自动化脚本、晚加班等正常行为。这为 `wall_clock_anomaly` 特征提供更真实的小时级基线。

### 活动波动（三级模型）

- 安静日（15% 概率）：操作量降至基准的 60%，时间窗口扩大至 1.5 倍
- 普通日（70% 概率）：正常操作量
- 繁忙日（15% 概率）：操作量增至基准的 150%，额外包含 30~100 个操作的 5~10 分钟集中突发

### 文件大小变化（修改操作）

```java
double delta = (random.nextDouble() * 0.6) - 0.3;  // -30% 到 +30%
long newSize = info.size * (1.0 + delta);
```

每个被修改的文件增长或缩减最多 30%，均匀分布。重命名操作的大小变化在 ±5% 或保持不变。这故意设计为**对称且高方差**——与勒索软件一致增长文件的行为形成对比。

### 用户选择

用户 1–10 被 Fisher-Yates 洗牌，前 `activeUsers`（3–10）个被选中。文件仅从活跃用户的目录中选择：
- 3 个活跃用户的轮次仅触及约 30% 的文件系统
- 10 个活跃用户的轮次触及全部文件

### 时间戳分布

所有操作的时间戳为 `dayStart + random(0 .. hoursWindow×3600)` 秒——在时间窗口内**均匀分布**。其中约 8~18% 的操作被随机分配到非工作时间（18:00~23:59 或 05:00~07:59）。没有突发模式，没有聚类。这与攻击将操作集中在紧凑突发窗口内的行为形成对比。

### 新增文件

新增文件具有以下特征：
- 路径：`/vol/share/user{activeUser}/{randomDir}/file_added_{rand}.{randomExt}`
- 大小：从相同的 `randomSize()` 分布中抽取（70% 小文件、20% 中等文件等）
- 时间戳：在时间窗口内均匀随机

### 删除操作

被删除的文件从文件系统状态中永久移除（不同于攻击生成器中使用 `Instant.MAX` 强制时间戳排序）。文件系统在轮次间**自然演化**——文件积累并被移除。

### 跨轮次状态持久化

`evolveNormalRound()` **永久修改文件系统状态**。修改改变文件大小，新增引入新文件，删除移除它们。下一轮操作在所有先前正常轮次之后的文件系统状态上进行。这创造了随时间推移的现实漂移。

### 轮次类型分布

变异轴的组合产生三种典型轮次：

| 轮次类型 | 概率 | 特征 |
|---|---|---|
| 安静日 | 15% | 操作量降至基准 60%，时间窗口扩大至 1.5 倍 |
| 普通日 | 70% | 正常操作量 |
| 繁忙日 | 15% | 操作量增至基准 150%，含 30~100 操作的 5~10 分钟集中突发 |

**关键属性**：没有两个轮次具有相同的活动特征——多个轴创造了足够的熵，使基线捕获"正常"行为的广泛分布，这对鲁棒的阈值校准至关重要。

## Snapdiff 文件格式

每个测试轮次生成一个符合 `SnapdiffFile` schema 的 JSON 文件：

```json
{
  "diffs": [
    {"path": "/vol/share/user1/docs/file_0042.docx", "type": "modified", "size": "56832", "change_time": "2026-04-22T10:30:00Z"},
    {"path": "/vol/share/user3/logs/file_0100.log",   "type": "deleted",  "size": "2048",   "change_time": "2026-04-22T10:31:00Z"},
    {"path": "/vol/share/user5/temp/file_added_42.tmp","type": "added",   "size": "4096",   "change_time": "2026-04-22T10:32:00Z"}
  ],
  "summary": {"files_added": 1, "files_modified": 1, "files_deleted": 1}
}
```

字段说明：`path`（完整路径）、`type`（`added`/`modified`/`deleted`）、`size`（字符串，操作后的大小）、`change_time`（ISO-8601 UTC 格式）。

## 填充（噪声注入）

每个攻击都与看起来正常的操作混合，模拟真实环境中攻击与正常活动同时发生的场景：

| 填充级别 | 攻击 : 正常 比例 | 目的 |
|---|---|---|
| p50 | 50 : 50 | 等量混合 — 中等难度 |
| p70 | 30 : 70 | 70% 噪声 — 最难检测，掩护最强 |

填充操作通过 `generateNormalPadding()` 生成 — 它们创建具有正常文件大小和时间戳的真实新增/修改/删除操作，确保每轮至少 5,000 个总操作。

## 基准测试流程

```
┌─────────────────────────────────────────────────────────────────────┐
│  初始化                                                             │
│  FilesystemState(42, 300K) → 300K 文件，确定性随机数生成器         │
│  RansomwareFeatureExtractor(null, 2.0) → 12 个特征，2 天间隔       │
│  SnapdiffParser → Jackson JSON → SnapdiffFile → SnapdiffRecord[]   │
│  WorkDir = 临时目录，用于存放 JSON 文件                              │
└─────────────────────────────────────────────────────────────────────┘

Phase 1a: 10 个基线正常轮次 → baselineVectors[]（阈值校准）
          evolveNormalRound() × 10，每个在工作时间 (9/10/14/15 点)
          → 写入 JSON → 解析 → 提取 12 个特征 → 存储向量

Phase 1b: 18 个攻击 → attackTestCases[]（9 种类型 × 2 种填充）
          snapshot() → dispatchAttack() → restore()
          → 写入 JSON → 解析 → 提取（更新 EMA）→ 添加到测试列表

Phase 1c: 10 个额外正常轮次 → normalVectors[]（误报压力测试）
           与 1a 相同，但不参与基线

Phase 1.5: 16 个不规则正常轮次 → irregularVectors[]
           8 种边界模式 × 2 次重复（batch_compile、log_rotation 等）
           不参与基线 — 纯误报压力测试

Phase 1.6: 4 个极安静日轮次 → quietDayVectors[]
           4 个安静日模式（操作量极低、特征全面低于基线）
           验证方向验证反转效果 — 无验证时应触发误报，有验证时应反转为正常

Phase 2: 4 个间歇性变体（A9, A12）× 2 种填充 = 4 个用例
         dispatchVariant() → snapshot/restore → 写入/解析/提取

Phase 2.5: 16 个对抗性变体（B1-B8）× 2 种填充 = 16 个用例
           dispatchAdversarial() → snapshot/restore → 写入/解析/提取

Phase 2.7: 14 个高容量变体（C2-C8）× 2 种填充 = 14 个用例
           dispatchHighVolume() → snapshot/restore → 写入/解析/提取

Phase 2.8: 16 个组合特征变体（D1-D8）× 2 种填充 = 16 个用例
           dispatchCombo() → snapshot/restore → 写入/解析/提取

── 预缓存：解析所有 68 个攻击 JSON → 提取向量（一次性） ──

Phase 3: 突发速度权重扫描
          对 6 个候选权重 (0.5, 2.0, 5.0, 8.0, 10.0, 15.0) 逐一测试：
            构建检测器（BaselineStatistics + AnomalyThreshold + 权重）
            测试所有 68 个缓存的攻击向量 → 统计检出/漏检数
            测试所有 10 个基线向量 → 统计误报数
            输出：权重 | 检出数 | 误报数 | 漏检列表
            跟踪最佳权重（最大检出数，相同时取最少误报）

Phase 0: 预热检测（冷启动测试）
          在 Phase 3 之前执行，验证 WarmupDetector 启发式检测：
            构建无基线的 RansomwareDetector（预热模式）
            输入 2 个干净向量 → 正常（累积基线）
            输入 1 个攻击向量 → 启发式检测（score ≥ 2）
            输入 3 个干净向量 → 基线累积至 5，自动切换统计检测
            输入 1 个攻击向量 → 统计检测确认
          3 项验证全部通过

Phase 4: 使用最佳权重输出详细结果
          用最佳权重构建最终检测器
          输出逐用例：描述、评分、阈值、是否检出、最大偏差特征
          输出按填充级别的检出率
          输出香草正常误报检查（仅 baselineVectors）
          输出不规则正常误报检查（含前 3 个特征解释）
          输出误报汇总表
          输出极安静日方向验证测试（quietDayVectors）
          清理临时文件

── 总计：68 个攻击测试用例 + 10 个基线正常 + 10 个额外正常 + 16 个不规则正常 + 4 个安静日 = 108 个文件 ──
```

## 攻击生成器清单（`AttackGenerator`）

### 9 种原始攻击类型（Phase 1b）

每种类型参考一个真实的勒索软件家族：

| 类型 | 家族 | 行为 |
|---|---|---|
| lockbit_fast_mode | LockBit | 4KB 追加，90-120 秒突发 |
| conti_size_tiered | Conti | 按文件大小分级加密（小→全量，中→头部，大→部分） |
| database_priority | 通用 | 优先数据库文件（.db/.sql），然后文档，+3-5% 大小 |
| single_user_rapid | 内部威胁 | 单用户，30-60 秒极速突发，1500-3000 操作 |
| slow_distributed | APT | 100 操作/10 秒微突发，60-90 秒间隔，6 小时窗口 |
| creeping_shrink | 破坏型 | 文件大小缩减 10-20%（数据损坏，非加密） |
| revil_random_ext | REvil | 随机 8 字符字母数字扩展名 |
| clop_companion | Cl0p | 原地修改 + .key 伴随文件 |
| wannacry_staged | WannaCry | 分阶段重命名为 .WNCRY |

### 8 种对抗性变体（Phase 2.5, B1-B8）

每种针对特定的检测特征设计规避策略：

| 类型 | 规避特征 | 策略 |
|---|---|---|
| B1 backup_disguise | temporal_uniformity | 3-5K 操作分散在 2-4 小时，模拟备份软件 |
| B2 slow_drip_encrypt | temporal_uniformity + per_type_entropy | 50 操作/5 分钟持续 6 小时 |
| B3 random_jitter_burst | inter_op_time_cv_burst | 随机 1-15 秒抖动 + 随机暂停 |
| B4 mixed_operation_mask | modification_ratio | 80/10/10 修改/新增/删除混合比 |
| B5 size_mimic_normal | 突发大小一致性 | 每个文件随机 -10% 到 +10% |
| B6 selective_high_value | high_value_ext_ratio | 仅针对 .docx/.xlsx/.pdf/.db/.sql |
| B7 multi_family_combo | temporal_uniformity | 50% 均匀加密 + 50% 追加加密 |
| B8 rename_and_encrypt | rename_correlation | 随机文件名 |

### 7 种高容量变体（Phase 2.7, C2-C8）

大规模文件操作攻击：

| 类型 | 规模 |
|---|---|
| C2 massive_add | 30K-50K 个文件新增 |
| C3 massive_delete | 20K-40K 个文件删除，3-5 分钟突发 |
| C4 balanced_mix | 60K-80K 总操作（40/30/30 修改/新增/删除） |
| C5 multi_wave | 3 波递增：10K → 20K → 40K |
| C6 added_heavy | 40K-60K 新增 + 40K-60K 删除对 |
| C7 delete_heavy | 30K-50K 删除 + 5K-10K 缩减 |
| C8 baseline_mimicking | 30K-40K 操作分散在 6-8 小时，看起来正常的大小 |

### 8 种组合特征变体（Phase 2.8, D1-D8）

每种同时触发 3 个以上特征的中等强度偏差：

| 类型 | 家族 | 特征组合 |
|---|---|---|
| D1 ryuk_lateral | Ryuk | 慢速高价值定位 + 快速突发 → temporal_uniformity, peak_burst_velocity, high_value_ext_ratio |
| D2 darkside_staged | DarkSide | 添加副本 → 修改原文 → 删除副本 → temporal_uniformity, rename_correlation, per_type_entropy |
| D3 lockbit3_adaptive | LockBit 3.0 | 交替快/慢突发 → temporal_uniformity, burst_mod_purity, inter_op_time_cv_burst |
| D4 blackcat_variable | BlackCat | 混合全量/间歇加密，非工作时间 → temporal_uniformity, wall_clock_anomaly, modification_ratio |
| D5 royal_selective | Royal | 部分损坏 + 15% 重命名 → temporal_uniformity, rename_correlation, directory_coverage_depth |
| D6 play_intermittent_noext | Play | 128KB 块间歇加密，仅业务文件 → temporal_uniformity, high_value_ext_ratio, peak_burst_velocity |
| D7 medusa_multi_stage | Medusa | 破坏 → 递增加密 → temporal_uniformity, peak_burst_velocity, per_type_entropy |
| D8 akira_vpn_gradual | Akira | 通过 VPN 渗透后慢速到快速递增，非工作时间 → temporal_uniformity, wall_clock_anomaly, peak_burst_velocity |

## 检测流水线（每个测试用例）

```
SnapdiffFile（解析后的 JSON）
  → RansomwareFeatureExtractor.extract(snapdiffFile)
    → 单次遍历所有记录，计算 12 个特征：
       0. modification_ratio（修改比率）           6. high_value_file_coverage（高价值文件覆盖度）
       1. total_ops_normalized（标准化操作总数）   7. directory_coverage_depth（目录覆盖深度）
       2. peak_burst_velocity（峰值突发速度）      8. temporal_uniformity（时间均匀性）
       3. burst_mod_purity（突发修改纯度）          9. rename_correlation（重命名关联度）
       4. high_value_ext_ratio（高价值扩展名比率） 10. wall_clock_anomaly（时钟异常）
       5. inter_op_time_cv_burst（突发窗口时间间隔变异系数） 11. per_type_entropy（操作类型熵）
  → RansomwareFeatureVector（12 个 double 值）
  → RansomwareDetector.detect(vector)
    → RansomwareSignatureDetector: 检查可疑扩展名 + 勒索信文件名
      → 匹配？→ 立即判定异常（跳过统计评分）
    → WeightedEuclideanScorer.score(vector)
      → z_i = (x_i - median_i) / MAD_i，截断至 ±10
      → score = √(Σ w_i × z_i²)
    → score > threshold？→ 异常
  → DetectionResult（评分、阈值、是否异常、zScores、topDeviations）
```

## 数据流概览

```
FilesystemState（300K 文件，可变状态）
  └─ AttackGenerator（通过 snapshot/restore 实现隔离）
       └─ List<DiffEntry>（路径、类型、大小、变更时间）
            └─ SnapdiffOutput（封装 diffs + summary）
                 └─ Jackson → JSON 文件（1KB – 52MB）
                      └─ SnapdiffParser → SnapdiffFile（解析后的记录）
                           └─ RansomwareFeatureExtractor → RansomwareFeatureVector（12 个 double 值）
                                └─ RansomwareDetector.detect() → DetectionResult
```

通过 JSON 的往返是为了保持生产环境的保真度 — 基准测试使用的是与真实检测相同的解析器和特征提取器。

## 数据预生成（`BenchmarkDataGenerator`）

基准测试采用两步架构：先用 `BenchmarkDataGenerator` 预生成所有测试数据，再由 `IntermittentEncryptionBenchmark` 加载运行。

**第一步：数据预生成**

`BenchmarkDataGenerator`（SEED=42）生成所有测试 JSON 文件到 `benchmark-data/` 目录，并输出 `MANIFEST.json` 索引文件。生成过程依次执行 Phase 1a（10 个基线正常）→ Phase 1b（18 个攻击）→ Phase 1c（10 个额外正常）→ Phase 1.5（16 个不规则正常）→ Phase 1.6（4 个安静日轮次）→ Phase 2/2.5/2.7/2.8（68 个攻击变体），总计 108 个 JSON 文件。

**第二步：基准测试运行**

`IntermittentEncryptionBenchmark` 读取 `benchmark-data/MANIFEST.json`，直接加载所有预生成的 JSON 文件（约 108 个文件，加载耗时 ~7s），然后执行 Phase 3/4（权重扫描 + 结果输出）。

## 运行方式

```bash
# 第一步：生成测试数据（仅首次或需重新生成时执行）
mvn compile -q && java -cp target/rcf-snapdiff-anomaly-detector-1.0.jar com.anomalydetection.generator.BenchmarkDataGenerator

# 第二步：运行基准测试
java -cp target/rcf-snapdiff-anomaly-detector-1.0.jar com.anomalydetection.generator.IntermittentEncryptionBenchmark
```

**运行时间**：数据预生成约 2 分钟（生成 ~108 个 JSON 文件）。基准测试运行约 7 秒（加载预生成数据 + 权重扫描 + 结果输出）。

## 最新结果

- **检出率**：68/68（100%）
- **香草正常误报**：0/10（0.0%）
- **不规则正常误报**：16/16（100.0%）
- **最佳突发速度权重**：0.50
- **异常阈值**：7.95
- **漏检**：无
- **无异常或运行时错误**

## 安静日方向验证结果（Phase 1.6）

Phase 1.6 包含 4 个极安静日轮次，操作量极低（~2,000 ops），特征值全面低于基线。这些轮次在统计评分中触发高异常得分（因为远离基线中位数），但异常能量方向全部向下（低于基线），属于典型的安静日误报模式。

### 安静日测试用例

| 用例 | 特征 |
|------|------|
| quiet_day_1 | 操作量 ~2,000，所有特征低于基线中位数 |
| quiet_day_2 | 操作量 ~2,000，时间窗口极度分散 |
| quiet_day_3 | 操作量 ~2,000，高价值扩展名比例极低 |
| quiet_day_4 | 操作量 ~2,000，目录覆盖极窄 |

### 验证结果

| 指标 | 数值 |
|------|------|
| 安静日测试用例 | 4 |
| 无方向验证时误报 | 4/4（100%，得分 16.3~22.3） |
| 方向验证阈值 0.75 时反转 | 4/4（100%，方向比率 0.885~0.941） |
| 攻击检出率影响 | 无（68/68 保持不变） |
| 香草正常误报影响 | 无（0/10 保持不变） |

### 方向验证原理

DirectionalValidator 在统计评分超过阈值后执行，计算异常能量的方向比率：

```
E_up = Σᵢ w_i × max(0, z_i)²          ← 高于基线的异常能量
E_down = Σᵢ w_i × max(0, -z_i)²        ← 低于基线的异常能量
ratio = E_down / (E_up + E_down + ε)    ← ε = 1e-10
```

勒索软件加密操作会增加特征指标（z > 0，如突发速度、操作量、修改纯度上升），而安静日轮次会降低特征指标（z < 0，如操作量骤降、覆盖度降低）。方向验证利用这一不对称性，当异常能量几乎全部来自低于基线的方向时，判定为安静日误报并反转为正常。

被反转的轮次不纳入自学习窗口，防止安静日轮次拉偏基线统计量。设 `--direction-threshold 0` 可禁用此功能。
