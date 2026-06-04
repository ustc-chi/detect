## Context

当前灵敏度体系：`double [0.0, 1.0]` → `SensitivityAdjuster` → `multiplier [2.0, 0.5]`（1.0 最灵敏）。

问题：前端只理解 1-10 整数刻度，且 Warmup/Active 两个阶段需要独立控制。Active 阶段只需透传 int 给外部模块，Warmup 阶段在内部用 multiplier 调整阈值。后台需要能通过修改 SensitivityAdjuster 的公式来调整映射关系。

用户确认的设计原则：
- **int 5 = 中灵敏度**（对应旧版 0.7 的"中"语义），不是简单把旧 0.7 数值搬过来
- **不需要单独的 Config 类**，全在 `SensitivityAdjuster` 里
- **旧 double 方法直接移除**，不留 `@Deprecated`

受影响文件清单（基于当前代码分析）：
- `AnomalyDetectionService.java` — 主入口，持有 double sensitivity
- `WarmupDetector.java` — detect() 入参用 double，内部调用 `SensitivityAdjuster`
- `SensitivityAdjuster.java` — 核心映射工具
- `BaselineDataProvider.java` — 接口含 `getBaselineStats(String, double)`
- `ExternalBaselineProvider.java` — 占位实现
- `HeuristicRule.java` — `evaluate(FeatureVector, double)` 方法签名
- 6 条启发式规则实现 — 每个都实现了 `evaluate(FeatureVector, double)`
- 8 个测试文件 — 涉及 double sensitivity 的测试用例

## Goals / Non-Goals

**Goals:**
- `AnomalyDetectionService.detect()` 入口改为两个 int 参数（warmup/active 各一个），范围 1-10，10=最灵敏
- `SensitivityAdjuster` 新增 int→multiplier 映射
- 新增 `SensitivityConfig` 承载后台可配置的默认灵敏度
- Active 阶段直接透传 int 给 `BaselineDataProvider`
- Warmup 阶段通过 `SensitivityAdjuster` 完成 int→multiplier 映射，后续链路不变
- 所有测试适配通过

**Non-Goals:**
- 不涉及 Active 阶段外部模块的内部处理逻辑（调用方自行处理 int 语义）
- 不涉及前端传参机制
- 不涉及数据库持久化（权重、基线等）

## Decisions

### D1: Int 1-10 → Multiplier 分段线性映射

**选择：** 在 `SensitivityAdjuster` 中直接分段线性映射，保证三个锚点：
- int 1 → multiplier 2.0（最保守）
- int 5 → multiplier **0.95**（中灵敏度，对齐旧版 double 0.7 的行为）
- int 10 → multiplier 0.5（最灵敏）

```java
if (sensitivity <= 5) {
    return 2.0 - (sensitivity - 1) * 0.2625;   // [1,5]: 2.0 → 0.95
} else {
    return 0.95 - (sensitivity - 5) * 0.09;    // [5,10]: 0.95 → 0.5
}
```

| int | multiplier | 语义 |
|:---:|:----------:|------|
| 1 | 2.0 | 最保守，阈值翻倍 |
| 2 | 1.738 | |
| 3 | 1.475 | |
| 4 | 1.213 | |
| **5** | **0.95** | **中灵敏度（默认，对齐旧版 0.7→0.95）** |
| 6 | 0.86 | |
| 7 | 0.77 | |
| 8 | 0.68 | |
| 9 | 0.59 | |
| 10 | 0.5 | 最灵敏，阈值减半 |

**为什么选分段线性：**
- 必须同时满足 1→2.0、5→0.95、10→0.5 三个锚点，简单线性做不到
- 分段线性可读性最高，两端斜率不同语义清晰：前半段快速降低（从保守区到中灵敏度），后半段平缓下降（从中灵敏度到高灵敏度）
- 用户只需改三个锚点值（2.0 / 0.95 / 0.5）即可重新调整曲线

### D2: 保留旧 double 方法还是移除

**选择：** 保留 `SensitivityAdjuster.getThresholdMultiplier(double)` 标记 `@Deprecated`，在新方法内部调用它。不删除，避免 break 外部调用者。

```
public static double getThresholdMultiplier(int sensitivity) {
    return getThresholdMultiplier(mapIntToDouble(sensitivity));
}

@Deprecated
public static double getThresholdMultiplier(double sensitivity) {
    // 原有逻辑不变
}
```

### D3: 默认灵敏度处理

**选择：** `AnomalyDetectionService` 构造器直接支持 int 参数，不引入单独的 Config 类。

```java
// 构造器重载
public AnomalyDetectionService()                                    // warmup=5, active=5
public AnomalyDetectionService(int normalThreshold)                 // 同默认 5,5
public AnomalyDetectionService(int normalThreshold,
                                BaselineDataProvider provider,
                                int defaultWarmup,
                                int defaultActive)

// 检测入口重载
public DetectionResult detect(FeatureVector vector, String resourceId)
    // 用构造时传入的默认值

public DetectionResult detect(FeatureVector vector, String resourceId,
                               int warmupSensitivity, int activeSensitivity)
    // 显式传参，覆盖默认值
```

`SensitivityAdjuster` 中只保留一个常量 `DEFAULT_SENSITIVITY = 5` 作为参考值。

**为什么这样做：**
- 用户明确说不需要单独的 Config 类，"一个SensitivityAdjuster类不就行了吗"
- Java 构造器参数是零依赖、最直接的配置方式
- 调用方如果是 Spring 应用，通过 `@Value` 读取配置后传入即可
- 显式传参的 detect() 可覆盖构造器默认值，灵活度足够

### D4: HeuristicRule 接口变更

**选择：** 接口 `evaluate(FeatureVector, double)` → `evaluate(FeatureVector, int)`，统一使用 int。

```java
@FunctionalInterface
public interface HeuristicRule {
    RuleResult evaluate(FeatureVector vector);

    default RuleResult evaluate(FeatureVector vector, int sensitivity) {
        return evaluate(vector);  // 默认不处理灵敏度
    }

    default String getRuleName() { ... }
}
```

6 条规则中支持灵敏度的 4 条（ModificationRatioRule、BurstModPurityRule、FileTypeConcentrationRule、InterOpTimeCvRule）将 `double sensitivity` 改为 `int sensitivity`，内部调用 `SensitivityAdjuster.getThresholdMultiplier(int)`。

### D5: BaselineDataProvider 接口变更

**选择：** 重载而非原地修改，减少外部实现者的 breakage

```java
public interface BaselineDataProvider {
    // 新增 int 版本
    BaselineStatsDTO getBaselineStats(String resourceId, int sensitivity);

    // 旧 double 版本标记 @Deprecated，保留默认实现委托到 int
    @Deprecated
    default BaselineStatsDTO getBaselineStats(String resourceId, double sensitivity) {
        return getBaselineStats(resourceId, (int) Math.round(sensitivity * 9 + 1));
    }
}
```

但这样 double→int 转换会丢失精度。更好方案：

```java
public interface BaselineDataProvider {
    BaselineStatsDTO getBaselineStats(String resourceId, int sensitivity);

    @Deprecated
    BaselineStatsDTO getBaselineStats(String resourceId, double sensitivity);
}
```

让外部实现者自行处理升级。`ExternalBaselineProvider` 更新两个方法。

### D6: WarmupDetector.detect() 入参变更

`detect(FeatureVector, List<FeatureVector>, double)` → `detect(FeatureVector, List<FeatureVector>, int)`

内部变化仅一行：`SensitivityAdjuster.getThresholdMultiplier(sensitivity)` 用 int 重载。

## Risks / Trade-offs

| 风险 | 缓解措施 |
|------|---------|
| 外部调用方（测试平台等）使用旧 `double` 签名的代码需更新 | `@Deprecated` 保留旧方法一个版本周期，但 `AnomalyDetectionService.detect()` 签名必须 break（无法重载），需通知调用方 |
| 映射公式中 int 7 对应 multiplier 1.0 而非旧版 0.95，默认行为微变 | 接受——新体系下 int 7 是"中性"语义更直观；若需精确恢复旧行为，设置默认灵敏度为 int 8（对应 multiplier 0.833） |
| 旧版 `SensitivityAdjusterTest` 的 9 个用例需全部重写 | 用参数化测试覆盖 int 1-10 全部值，比原来更全面 |
| 6 条启发式规则的 `evaluate(FeatureVector, double)` 实现改入参类型 | 纯入参类型变更，逻辑不变，风险低 |
