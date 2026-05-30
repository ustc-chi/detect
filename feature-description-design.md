# 特征描述丰富方案

## 设计原则

每个特征的描述分三层：
1. **事实层** — 当前值 + 上下文（数值、时间、数量等）
2. **解读层** — 该特征的含义 + 正常范围参考
3. **排查层** — 具体排查方向

描述通过 `FeatureType` 模板 + `FeatureCalculator.putExtendInfo()` 填充实现，detect 调用 `FeatureVector.getDes(FeatureType)` 获取完整描述。

---

## F0 — modification_ratio

**模板：**
```
CN: "修改占比: %s，共 %s 条操作记录。该特征衡量修改操作占总操作的比例。正常业务为混合操作(增删改)，修改占比通常在 30%%-70%%。超过 90%% 表明几乎全部操作都是修改，是加密型勒索软件(LockBit/Conti/REvil)的典型行为。排查方向: 检查该时间段被修改文件的扩展名是否统一变成 .locked/.encrypted 等异常格式"
EN: "Modification Ratio: %s (%s records). Measures the proportion of MODIFIED operations. Normal business: 30%%-70%%. Above 90%% indicates encryption ransomware (LockBit/Conti/REvil). Investigation: Check if modified files have uniform suspicious extensions (.locked, .encrypted)"
```

**extendInfo 参数：** `[formatPercent(modRatio), totalRecords]`

---

## F1 — deletion_ratio

**模板：**
```
CN: "删除占比: %s，共 %s 条操作记录。该特征衡量删除操作占总操作的比例。正常维护性删除通常在 5%% 以下。超过 30%% 可能是破坏型攻击(删除原文件后再加密)或数据清理行为。排查方向: 确认删除的文件类型，是否存在备份可用，检查是否有异常进程批量删除"
EN: "Deletion Ratio: %s (%s records). Measures the proportion of DELETED operations. Normal: <5%%. Above 30%% indicates destructive attacks. Investigation: Check deleted file types, backup availability, abnormal bulk-deletion processes"
```

**extendInfo 参数：** `[formatPercent(delRatio), totalRecords]`

---

## F2 — creation_ratio

**模板：**
```
CN: "创建占比: %s，共 %s 条操作记录。该特征衡量新建文件占总操作的比例。大量新建文件可能是勒索软件释放加密文件或勒索信。排查方向: 检查新建文件的扩展名是否异常，是否存在 README_UNLOCK 等勒索信文件名"
EN: "Creation Ratio: %s (%s records). Measures the proportion of CREATED operations. High creation rate may indicate ransomware dropping encrypted files or ransom notes. Investigation: Check new file extensions, look for ransom note filenames (README_UNLOCK)"
```

**extendInfo 参数：** `[formatPercent(creRatio), totalRecords]`

---

## F3 — total_operations_normalized

**模板：**
```
CN: "日均操作量: %s 次/天(共 %s 条操作，跨 %s 天)。该特征衡量操作规模。勒索软件加密时操作量会激增至数万甚至数十万。排查方向: 比对历史同期的日均操作量，确认是否存在异常增长"
EN: "Daily Operations: %s ops/day (%s total, %s days). Measures operation volume. Ransomware encryption causes surges to 10K+ ops/day. Investigation: Compare with historical daily averages for abnormal growth"
```

**extendInfo 参数：** `[(long)dailyOps, totalRecords, daysBetween]`

---

## F4 — peak_burst_velocity

**模板：**
```
CN: "突发时间窗口: %s → %s，窗口内操作: %s 次(速率 %s)。该特征检测 5 分钟滑动窗口内最大操作密度。正常人类操作分散在较长时间段内，高密度突发(>5000次/小时)是自动化工具特征。排查方向: 排查该 5 分钟内操作的源 IP、涉及文件路径和扩展名，确认是否属于已知的自动化任务"
EN: "Burst Window: %s → %s, ops in window: %s (rate: %s). Detects max operation density in any 5-min sliding window. High density (>5000/hr) = automated tool. Investigation: Check source IPs, file paths, and extensions in this window"
```

**extendInfo 参数：** `[formattedStartTime, formattedEndTime, opsInWindow, formattedRate]`

---

## F5 — burst_mod_purity

**模板：**
```
CN: "突发窗口修改纯度: %s(%s 次操作中 %s 次为修改)。该特征衡量突发窗口内的修改操作集中度。正常突发(如批量复制)是混合操作，纯度通常低于 70%%。纯度 > 95%% 表明突发几乎全是修改操作，是加密行为的确凿特征。排查方向: 结合 F4 的时间窗口，确认该突发是否为已知的自动化维护任务"
EN: "Burst Modification Purity: %s (%s modified of %s ops). Measures modification concentration in the burst window. Normal bursts < 70%%. Purity > 95%% indicates encryption behavior. Investigation: Cross-reference with F4 burst window, verify if it's a known maintenance task"
```

**extendInfo 参数：** `[formatPercent(burstPurity), maxModifiedInWindow, maxOpsInWindow]`

---

## F6 — high_value_ext_ratio

**模板：**
```
CN: "高价值文件占比: %s(涉及 %s 等扩展名)。该特征衡量文档/数据库/备份等高价值文件被操作的比例。勒索软件优先加密高价值文件以最大化勒索效果。排查方向: 检查这些高价值文件的扩展名是否被篡改，尝试打开确认文件是否正常"
EN: "High-Value Extension Ratio: %s (extensions: %s). Measures operations on high-value files (docs, databases, backups). Ransomware prioritizes these for maximum impact. Investigation: Check if high-value files open normally, verify extensions haven't been altered"
```

**extendInfo 参数：** `[formatPercent(hvRatio), matchedExtensionsSample]`

---

## F7 — inter_op_time_cv_burst

**模板：**
```
CN: "突发窗口操作间隔变异系数: %s(极低 = 间隔高度均匀)。该特征衡量操作时间间隔的规律性。CV 越低说明操作间隔越均匀。CV < 0.05 表明操作由脚本或自动化工具执行，正常人类操作 CV 通常 > 0.2。排查方向: 极均匀的定时操作高度怀疑为自动化攻击脚本，结合 F4 时间窗口确认"
EN: "Inter-Op Time CV (Burst): %s. Measures regularity of operation intervals. CV < 0.05 = scripted/automated (human CV > 0.2). Investigation: Highly uniform timing strongly suggests automated attack scripts"
```

**extendInfo 参数：** `[String.format("%.4f", burstCV)]`

---

## F8 — directory_coverage_depth

**模板：**
```
CN: "目录覆盖率: %s(涉及 %s 个不同目录，平均深度 %s 层)。该特征衡量文件操作分布在多少目录中。勒索软件会遍历目录树加密文件，覆盖范围异常广。排查方向: 对比正常业务通常访问的目录范围，确认是否有越界访问到不应操作的目录"
EN: "Directory Coverage Depth: %s (%s unique dirs, avg depth %s). Measures directory spread of operations. Ransomware traverses directory trees broadly. Investigation: Compare with normal access patterns, check for out-of-bounds directory access"
```

**extendInfo 参数：** `[String.format("%.2f", coverage), uniqueDirsCount, avgDepth]`

---

## F9 — temporal_uniformity

**模板：**
```
CN: "时间均匀度: %s(越接近 1.0 说明窗口内操作分布越均匀)。该特征衡量突发窗口内操作在时间轴上的分布均匀程度。人类操作集中在某几秒内(不均匀)，自动化工具以接近匀速执行(均匀)。高均匀度(>0.8) + 高纯度(结合 F5) = 自动化加密工具的强信号。排查方向: 高均匀度 + 纯度说明该突发是自动化行为"
EN: "Temporal Uniformity: %s (closer to 1.0 = more uniform). Measures distribution of ops within the burst window. Human ops are bursty within the window, automated tools are uniform. High uniformity + high purity (F5) = strong automation signal. Investigation: Confirm"
```

**extendInfo 参数：** `[String.format("%.4f", uniformity)]`

---

## F10 — rename_correlation

**模板：**
```
CN: "重命名关联度: %s(新增文件中 %s 与删除文件的文件名前缀匹配)。该特征检测"删除原文件→创建加密版本"的加密-重命名模式。正常业务中该比例通常低于 5%%。排查方向: 检查匹配的文件对，确认是否为加密替换操作(如 document.docx → document.encrypted)"
EN: "Rename Correlation: %s (%s of created files match deleted file prefixes). Detects delete-and-replace encryption pattern. Normal: <5%%. Investigation: Check matched file pairs for encryption replacement (document.docx → document.encrypted)"
```

**extendInfo 参数：** `[formatPercent(renameCorr), matchedPrefixCount]`

---

## F11 — hourly_concentration

**模板：**
```
CN: "小时集中度: %s(最忙小时: %d点，操作数占比 %s)。该特征衡量操作是否集中在某 1 小时内。正常业务分散在白天工作时间(上午9-12点，下午14-18点)。攻击者常选择非工作时间(凌晨 0-5 点)集中操作。排查方向: 确认最忙时段是否符合业务的正常使用习惯"
EN: "Hourly Concentration: %%s (peak hour: %d:00, %s of total). Measures operation concentration in a single hour. Normal business distributed across work hours. Attackers favor off-hours (0:00-5:00). Investigation: Verify peak hour aligns with normal business patterns"
```

**extendInfo 参数：** `[formatPercent(conc), peakHour, formatPercent(conc)]`

---

## F12 — hourly_entropy

**模板：**
```
CN: "小时分布熵: %s bits(最大值约 4.6 bits)。该特征衡量 24 小时操作分布的随机性。高熵(>3.5)表示操作均匀分布全天; 低熵(<2.0)表明集中在少量时段(非正常模式)。排查方向: 低熵时结合 F11 确认集中时段，高熵自动化工具全天候运行"
EN: "Hourly Entropy: %s bits (max ~4.6 bits). Measures randomness of 24h operation distribution. High entropy (>3.5) = uniform all day; low entropy (<2.0) = concentrated in few hours. Investigation: Low entropy signals abnormal concentration; high entropy may indicate 24/7 automated tools"
```

**extendInfo 参数：** `[String.format("%.2f", hourlyEntropy)]`

---

## F13 — per_type_entropy

**模板：**
```
CN: "操作类型分布熵: %s bits(最大约 1.6 bits)。该特征衡量 MODIFIED/CREATED/DELETED 三种操作类型分布的多样性。低熵(<0.5)表明单种操作类型主导。全是修改 = 加密特征; 全是删除 = 破坏特征。排查方向: 确认主导的操作类型，结合 F0/F1/F2 定位具体哪类操作异常"
EN: "Per-Type Entropy: %s bits (max ~1.6 bits). Measures diversity of MODIFIED/CREATED/DELETED distribution. Low entropy (<0.5) = one type dominates. All MODIFIED = encryption; all DELETED = destruction. Investigation: Identify dominant type, cross-reference with F0/F1/F2"
```

**extendInfo 参数：** `[String.format("%.2f", perTypeEntropy)]`

---

## F14 — extension_count_cv

**模板：**
```
CN: "扩展名变异系数: %s(涉及 %s 种扩展名)。该特征衡量各扩展名出现频率的均匀程度。高 CV = 集中在少数扩展名; 低 CV = 均匀分布在多个扩展名。勒索软件加密后产生的加密扩展名会集中在某一种(如 .encrypted)。排查方向: 检查涉及的扩展名中是否有可疑/未知扩展名，确认文件的打开情况"
EN: "Extension Count CV: %s (%s unique extensions). Measures uniformity of extension distribution. Low CV = many extensions equally; high CV = dominated by few extensions. Ransomware produces uniform encrypted extensions. Investigation: Check for suspicious extensions, verify file integrity"
```

**extendInfo 参数：** `[String.format("%.2f", extCountCV), uniqueExtCount, extensionSample]`

---

## F15 — created_ext_novelty

**模板：**
```
CN: "新建扩展名新颖度: %s(新建扩展名中 %s 未曾在其他操作中出现)。该特征衡量新建文件的扩展名是否全新。全新扩展名(.encrypted, .locked)表示加密后的文件，是老练攻击者的特征。排查方向: 重点检查这些新颖扩展名对应的文件内容，确认是否需要解密恢复"
EN: "Created Extension Novelty: %s (%s of new extensions never seen in other ops). Measures how novel newly created file extensions are. Novel extensions (.encrypted, .locked) indicate encryption outputs. Investigation: Check files with novel extensions, determine if decryption is needed"
```

**extendInfo 参数：** `[formatPercent(novelty), novelCount, extensionSample]`
