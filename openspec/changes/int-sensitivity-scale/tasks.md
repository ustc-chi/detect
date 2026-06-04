## 1. SensitivityAdjuster — 替换为 int 映射

- [ ] 1.1 移除旧 `getThresholdMultiplier(double)` 和 `getDefaultSensitivity()` 方法
- [ ] 1.2 新增 `getThresholdMultiplier(int sensitivity)`：分段线性映射 `[1,5]: 2.0→0.95, [5,10]: 0.95→0.5`，范围验证 1-10
- [ ] 1.3 新增常量 `DEFAULT_SENSITIVITY = 5`
- [ ] 1.4 重写 `SensitivityAdjusterTest`：参数化测试覆盖 int 1-10 全部值 + 边界 + 异常输入

## 2. AnomalyDetectionService — 入口签名变更

- [ ] 2.1 构造器重载：新增 `(int normalThreshold, BaselineDataProvider provider, int defaultWarmup, int defaultActive)`，保留无参构造（默认 5,5）
- [ ] 2.2 新增成员 `defaultWarmupSensitivity` 和 `defaultActiveSensitivity`
- [ ] 2.3 新增 `detect(FeatureVector, String)` 便利重载（使用构造器默认值）
- [ ] 2.4 新增 `detect(FeatureVector, String, int warmupSensitivity, int activeSensitivity)` 主入口
- [ ] 2.5 移除旧 `detect(FeatureVector, String, double)`
- [ ] 2.6 Warmup 分支传入 `warmupSensitivity` 给 `WarmupDetector.detect()`
- [ ] 2.7 Active 分支传入 `activeSensitivity` 给 `BaselineDataProvider.getBaselineStats(String, int)`

## 3. WarmupDetector — 入参类型变更

- [ ] 3.1 `detect(FeatureVector, List<FeatureVector>, int)` 替换旧 double 版本
- [ ] 3.2 内部调用改为 `SensitivityAdjuster.getThresholdMultiplier(int)`
- [ ] 3.3 无参重载 `detect(FeatureVector, List<FeatureVector>)` 使用 `SensitivityAdjuster.DEFAULT_SENSITIVITY`

## 4. HeuristicRule 接口及实现适配

- [ ] 4.1 `HeuristicRule.evaluate(FeatureVector, int)` 替换 `evaluate(FeatureVector, double)`
- [ ] 4.2 `ModificationRatioRule` — 入参类型变更，内部换用 `SensitivityAdjuster.getThresholdMultiplier(int)`
- [ ] 4.3 `BurstModPurityRule` — 同上
- [ ] 4.4 `FileTypeConcentrationRule` — 同上
- [ ] 4.5 `InterOpTimeCvRule` — 同上

## 5. BaselineDataProvider 接口适配

- [ ] 5.1 `getBaselineStats(String resourceId, int sensitivity)` 替换旧 double 版本
- [ ] 5.2 移除旧 `getBaselineStats(String, double)`
- [ ] 5.3 `ExternalBaselineProvider` 实现 int 版本（保持返回 null 的占位行为）

## 6. 测试适配

- [ ] 6.1 `AnomalyDetectionServiceTest` — 更新所有 `detect()` 调用为 int 参数或无参重载
- [ ] 6.2 `WarmupDetectorTest` — 更新入参类型
- [ ] 6.3 `WarmupActiveIntegrationTest` — 更新测试调用
- [ ] 6.4 `HeuristicRuleTest` — 更新规则测试调用

## 7. 集成验证

- [ ] 7.1 运行 `mvn test -pl detect -am` 全部测试通过
- [ ] 7.2 运行 `WeightOptimizationRunner` 验证 seed=42 结果可复现
- [ ] 7.3 全局搜索 `double sensitivity` 确认无遗漏引用
