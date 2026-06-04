## Why

当前检测入口 `AnomalyDetectionService.detect(FeatureVector, String, double)` 用 `double [0.0, 1.0]` 表示灵敏度，但前端只感知 1-10 的整数刻度，且 Warmup 和 Active 两个阶段需要独立的灵敏度控制。Active 阶段调用外部模块处理 int 即可，Warmup 阶段需要在内部做阈值映射。同时后台需要可配置的默认值，不依赖前端传参。

## What Changes

- **BREAKING**: `AnomalyDetectionService.detect()` 签名变更：`double sensitivity` → `int warmupSensitivity, int activeSensitivity`，范围 1-10（1=最不灵敏, 10=最灵敏）
- **BREAKING**: `SensitivityAdjuster.getThresholdMultiplier()` 改为接受 int 1-10 并映射为对应的阈值乘数，旧 double 方法保留兼容
- **BREAKING**: `BaselineDataProvider.getBaselineStats(String, double)` → `getBaselineStats(String, int)`，Active 阶段直接传递 int
- 新增 `SensitivityConfig`：可配置的默认灵敏度（Warmup/Active 独立默认值），支持构造注入，不依赖前端传参时使用
- WarmupDetector 内灵敏度使用链保持不变（通过 `SensitivityAdjuster` 映射为 multiplier）
- 测试全面更新：`SensitivityAdjusterTest`、`AnomalyDetectionServiceTest`、启发式规则测试

## Capabilities

### New Capabilities
- `sensitivity-config`: 可注入的灵敏度配置对象，支持 Warmup/Active 独立默认值，可被 `SensitivityAdjuster` 和 `AnomalyDetectionService` 消费
- `int-sensitivity-mapping`: int 1-10 到阈值乘数的映射逻辑（含新映射公式和边界测试）

### Modified Capabilities

- `anomaly-detection-api`: 检测入口签名变更，拆分为 Warmup/Active 两个 int 参数
- `baseline-data-provider`: Active 阶段灵敏度参数类型从 double 变为 int

## Impact

| 组件 | 影响 |
|------|------|
| `AnomalyDetectionService.java` | `detect()` 签名变更；新增 `warmupSensitivity` 成员；构造器注入 `SensitivityConfig` |
| `SensitivityAdjuster.java` | 新增 int 1-10 映射方法；原 double 方法标记 `@Deprecated` |
| `BaselineDataProvider.java` | 接口 `getBaselineStats(String, int)` 替换 double 版本 |
| `ExternalBaselineProvider.java` | 同步更新接口实现 |
| `WarmupDetector.java` | `detect()` 内部调用适配（变化小，仅外部传参不同） |
| `HeuristicRule.java` | `evaluate(FeatureVector, double)` 改为 `evaluate(FeatureVector, int)` |
| 6 条启发式规则实现 | 入参类型变更 |
| `SensitivityAdjusterTest.java` | 重写为 int 1-10 测试用例 |
| `AnomalyDetectionServiceTest.java` | 测试调用方适配 |
| 其他测试文件 | 涉及 `double sensitivity` 的地方全部适配 |
