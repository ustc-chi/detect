## 1. FeatureVector14 数据结构

- [x] 1.1 创建 `FeatureVector14` 类：values[14]、supplementaryData、FEATURE_NAMES、FEATURE_DESCRIPTIONS、FEATURE_UNITS
- [x] 1.2 实现 14 维特征的 getter 方法（如 `getModificationRatio()`、`getPeakBurstVelocity()`）
- [x] 1.3 实现 `toArray()` 和 `get(int index)` 方法
- [x] 1.4 创建 `Phase` 枚举（WARMUP / ACTIVE）
- [x] 1.5 创建 `DimensionReport` 类：index、name、value、zScore、contribution、weight、description、unit、isAnomalyDimension、supplementary

## 2. WarmupDetector 核心实现

- [x] 2.1 创建 `HeuristicRule` 接口及 `RuleResult` 类（triggered、ruleName、confidence）
- [x] 2.2 实现 `SuspiciousExtensionRule`（可疑扩展名 > 0 → 异常）
- [x] 2.3 实现 `ModificationRatioRule`（修改比 > 0.95 且 操作数 > 50）
- [x] 2.4 实现 `BurstModPurityRule`（突发纯度 > 0.95 且 速度 > 50）
- [x] 2.5 实现 `FileTypeConcentrationRule`（集中度 > 0.90 且 总数 > 100）
- [x] 2.6 实现 `InterOpTimeCvRule`（CV < 0.05 且 总数 > 50）
- [x] 2.7 实现 `HighValueTargetingRule`（高价值比 > 0.8 且 总数 > 100）
- [x] 2.8 实现 `DeletionIntensityRule`（删除强度 > 5.0）
- [x] 2.9 实现 `WarmupDetector` 入口：三层检测流程编排
- [x] 2.10 实现 Layer 3 动态统计检测（Warmup 专用权重、动态阈值计算）
- [x] 2.11 创建 `WarmupInfo` 类（matchingRuleCount、triggeredRules、confidence、addToBaseline）
- [x] 2.12 创建 `WarmupStatus` 枚举（ANOMALY / SUSPICIOUS / NORMAL）

## 3. ActiveDetector 实现

- [x] 3.1 创建 `BaselineStatsDTO`（median[14]、mad[14]、threshold、weights[14]、resourceId）
- [x] 3.2 实现 `ActiveDetector` 类：加权欧氏距离评分
- [x] 3.3 实现每维 contribution = w_i × z_i² 计算
- [x] 3.4 实现 `DirectionalValidatorV2` (14 维)：eUp/eDown 分离、ratio 计算、安静日反转判定
- [x] 3.5 创建 `DirectionValidation` 类（reversed、ratio、eUp、eDown）

## 4. DetectionResult 报告结构

- [x] 4.1 实现 `DetectionResult` 类：含 resourceId、detectionTime、phase、score、threshold、isAnomaly
- [x] 4.2 集成 `dimensions[14]` 和 `topDeviations`（按 |zScore| 降序 Top-5）
- [x] 4.3 集成方向验证信息（DirectionValidation）
- [x] 4.4 集成签名匹配信息（signatureMatch）
- [x] 4.5 集成 Warmup 阶段信息（WarmupInfo）
- [x] 4.6 实现统一构建器/工厂方法确保所有字段非 null

## 5. AnomalyDetectionService 统一入口

- [x] 5.1 实现 `AnomalyDetectionService` 类：接收 resourceId + FeatureVector14 + 历史数据
- [x] 5.2 实现 phase 判断逻辑（正常历史数 < NORMAL_THRESHOLD → Warmup）
- [x] 5.3 集成 WarmupDetector 和 ActiveDetector 路由
- [x] 5.4 添加可配置的 NORMAL_THRESHOLD（默认 10）
- [x] 5.5 添加结果持久化回调机制（ResultHandler）

## 6. 测试

- [x] 6.1 单元测试：FeatureVector14 构造和越界校验 (FeatureVector14Test)
- [x] 6.2 单元测试：每条 HeuristicRule 独立测试 (HeuristicRuleTest)
- [x] 6.3 单元测试：WarmupDetector 三层检测流程全场景 (WarmupDetectorTest)
- [x] 6.4 单元测试：WarmupDetector 动态阈值随样本量变化 (WarmupDetectorTest)
- [x] 6.5 单元测试：ActiveDetector 评分计算 (ActiveDetectorTest)
- [x] 6.6 单元测试：DirectionalValidator 安静日反转逻辑 (ActiveDetectorTest / DirectionalValidatorV2)
- [x] 6.7 单元测试：AnomalyDetectionService 阶段路由 (AnomalyDetectionServiceTest)
- [x] 6.8 集成测试：完整 Warmup → Active 流程 (WarmupActiveIntegrationTest)
- [x] 6.9 测试：DetectionResult 序列化为 JSON（确保报告格式正确）(DetectionResultJsonTest)
