## Why

现有 `RansomwareDetector` 为单资源、自包含的异常检测实现（内部维护基线/阈值，12维特征），无法满足多资源隔离、外部数据源集成、丰富检测报告的需求。需要重新设计一个支持 **资源独立隔离**、**两阶段检测（Warmup→Active）**、**外部基线/权重注入**、**富报告输出** 的检测引擎 v2 版本。

## What Changes

- **新增** `com.anomalydetection.detector.v2` 包，与现有代码完全隔离
- **新增** `FeatureVector14` —— 14维特征向量，每维可携带附带数据(supplementary data)
- **新增** `AnomalyDetectionService` —— 统一外部检测入口，根据资源正常历史量自动切换 Warmup/Active 阶段
- **新增** `WarmupDetector` —— 预热阶段检测器，三层防御（确定性规则→强启发式→动态统计）
- **新增** `ActiveDetector` —— 激活阶段检测器，加权欧氏距离 + 方向验证（安静日反转）
- **新增** `DetectionResult` 及 `DimensionReport` —— 包含各维度zScore/contribution/附带数据的完整报告结构
- **新增** 可插拔启发式规则体系 `HeuristicRule` 接口
- **修改** 特征维度从12维扩展到14维（新增 `deletion_intensity`, `directory_spread`, `extension_diversity`, `suspicious_extension_ratio`, `avg_modified_size`, `size_std_dev`, `file_type_concentration`, `size_change_kurtosis`；调整 `total_operations` 含义；`inter_op_time_cv` 替代 `inter_op_time_cv_burst`）

## Capabilities

### New Capabilities

- `feature-vector-14`: 14维特征向量的数据结构、附带数据规范、维度描述体系
- `warmup-detector`: 预热阶段三层检测策略（确定性规则→强启发式→动态统计）
- `active-detector`: 激活阶段加权欧氏距离 + 方向验证（安静日反转）检测
- `detection-result`: 含各维度zScore/contribution/附带数据/描述/triggerRule的完整报告结构
- `anomaly-detection-service`: 统一检测入口，接收resourceId + FeatureVector14，自动阶段路由

### Modified Capabilities

<!-- 无现有spec变更，全部为新增 -->

## Impact

- 新建 `com.anomalydetection.detector.v2` 包，现有 `RansomwareDetector` 不受影响
- 外部调用方需提供：feature 提取层需产出 14 维 + 附带数据；外部接口需提供历史向量查询、基线统计量查询、权重查询
- 新增约 20+ Java 类文件
