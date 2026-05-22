## Context

### 当前状态

现有 `RansomwareDetector` 位于 `com.anomalydetection.detector` 包，具有以下局限：
- 单资源模型：无 resourceId 概念，所有检测状态在单一实例内维护
- 12 维特征向量：特征集固定，无法扩展
- 自包含基线：内部维护 warmupBaseline 和 window，无法从外部获取数据
- 预热策略简单：仅 5 条固定阈值规则，≥2 规则触发即异常
- 检测结果有限：DetectionResult 仅包含基础评分和 zScore，缺乏各维度详细贡献和报告所需元数据

### 新场景需求

- **多资源隔离**：每个资源有独立的检测状态（基线、阈值、历史记录）
- **外部数据源**：基线统计量(median/MAD)、权重、阈值由外部模块提供
- **14 维特征**：扩展维度体系，每维可携带附带数据用于报告
- **两阶段检测**：Warmup（启发式+动态阈值）→ Active（欧氏距离+方向验证）
- **丰富报告**：检测结果需包含各维度贡献、描述、附带数据，直接可用于报告生成

## Goals / Non-Goals

**Goals:**
- 全新 `com.anomalydetection.detector.v2` 包，与现有代码完全隔离
- `FeatureVector14` 支持 values[14] + 每维附带数据 (Map&lt;Integer, Map&lt;String, Object&gt;&gt;)
- `AnomalyDetectionService` 统一入口，根据资源正常历史量自动切换 Warmup/Active
- `WarmupDetector` 三层防御：确定性规则 → 强启发式规则 → 动态统计检测
- `ActiveDetector` 加权欧氏距离评分 + DirectionalValidator（安静日反转）
- `DetectionResult` 包含完整维度报告（zScore、contribution、description、unit、supplementary）
- 启发式规则体系通过 `HeuristicRule` 接口实现可插拔
- Warmup 阶段使用专用权重（禁用不稳定特征）

**Non-Goals:**
- 不修改现有 `com.anomalydetection.detector` 包中的任何代码
- 不实现特征提取逻辑（特征提取由外部模块完成，检测服务只接收已提取好的 `FeatureVector14`）
- 不实现外部数据源接口（接口由其他模块定义，本模块定义 DTO 结构）
- 不做基准测试（后续单独变更）

## Decisions

### D1: v2 包隔离 vs 原地修改
**选择**：新建 `com.anomalydetection.detector.v2` 包  
**理由**：特征维度（12→14）、数据流（自包含→外部注入）、检测策略（简单规则→三层防御）均有根本性变化，原地修改风险高且影响现有调用方。v2 包允许新旧共存，平滑迁移。  
**替代方案**：原地重构 `RansomwareDetector` → 破坏现有调用方，且大量条件分支使代码难以维护。

### D2: 附带数据结构
**选择**：`Map<Integer, Map<String, Object>>` 按维度索引存储  
**理由**：
- 不同维度的附带数据形状完全不同（suspicious_extension_ratio 需要字符串列表，burst_mod_purity 需要时间窗口），统一固定字段不现实
- Map 结构对 JSON 序列化友好，直接可用于报告生成
- 索引 key 保证与 values[] 严格对齐  
**规则**：每个维度的 supplementary key 在 FeatureVector14 的 javadoc 中文档化

### D3: 启发式规则可插拔
**选择**：`HeuristicRule` 接口 + 具体实现类  
**理由**：
- 每条规则独立测试
- 新增规则只需新增实现类，无需修改 WarmupDetector
- 可通过配置动态启用/禁用特定规则
**模式**：策略模式（Strategy Pattern）

### D4: Warmup 动态阈值策略
**选择**：`threshold = historyMaxScore × multiplier`，multiplier 随样本量递减  
**理由**：
- 数据量越少，基线越不可靠，阈值应越宽松（减少误报）
- 不使用百分位法（数据量不足时百分位无统计意义）
- multiplier 系数可配置（后续可根据实际数据调优）

### D5: Warmup 阶段判定为"可疑"的处理
**选择**：第3层（动态统计检测）命中的标记为 SUSPICIOUS（非直接 ANOMALY），不纳入基线但也不阻断业务，留待人工审核  
**理由**：前两层（确定性/强启发式）置信度高，直接判异常；动态统计在数据量少时可靠度有限（仅 2-9 个样本），标记为可疑更为稳妥。  
**关联**：外部审核模块负责人工审核后的基线回补。

### D6: ActiveDetector 数据获取方式
**选择**：外部 API 直接返回预计算好的 `BaselineStatsDTO`（含 median[14]、mad[14]、threshold、weights[14]）  
**理由**：
- 检测服务聚焦检测逻辑，不负责基线计算
- 接口尚未定义，当前以 DTO 结构占位，后续对接

### D7: 安静日方向验证复用
**选择**：复用 `com.anomalydetection.detector.DirectionalValidator` 逻辑，在 v2 包中重新实现  
**理由**：现有 `DirectionalValidator` 验证机制合理（eDown/(eUp+eDown) > threshold → 反转），但定义为 12 维。v2 包中重写为 14 维版本，保持隔离。

## Risks / Trade-offs

| 风险 | 缓解措施 |
|------|---------|
| Warmup 阶段正常样本误判为异常导致基线无法积累（滞留 waming） | 3.3 节动态阈值机制已考虑：前几轮极宽松(10x)，随样本量递减。WarmupInfo 中标记置信度供外部参考 |
| 外部数据源接口未定义，ActiveDetector 无法验证 | 定义 BaselineStatsDTO、WeightDTO 等占位接口，确保结构合理。后续接口件实现后对接 |
| 14 维特征需外部提取层同步实现 | 本变更只定义数据结构和检测逻辑，特征提取为独立模块 |
| 启发式规则阈值固定（如 0.95、5000 等），可能不适应所有资源 | 阈值通过 HeuristicRule 配置传递，后续可通过配置中心调整 |
