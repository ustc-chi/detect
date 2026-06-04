# Int 灵敏度迁移 — 代码变更对比

> 变更范围：15 个文件，+174 / -83 行
> 测试结果：51/51 全部通过，BUILD SUCCESS

---

## 1. SensitivityAdjuster — 核心映射

### 替换前（double [0,1]）

```java
// multiplier = 2.0 - sensitivity * 1.5
// sensitivity=0.7 → multiplier=0.95 (default)
// sensitivity=1.0 → multiplier=0.5
// sensitivity=0.0 → multiplier=2.0
public static double getThresholdMultiplier(double sensitivity) {
    if (sensitivity < 0.0 || sensitivity > 1.0) { throw ... }
    return 2.0 - sensitivity * 1.5;
}
public static double getDefaultSensitivity() { return 0.7; }
```

### 替换后（int [1,10]，分段线性）

```java
// [1,5]:  multiplier = 2.0 - (s-1) * 0.2625
// [5,10]: multiplier = 0.95 - (s-5) * 0.09
// sensitivity=5 → multiplier=0.95 (default, 同旧版 0.7)
// sensitivity=10 → multiplier=0.5
// sensitivity=1 → multiplier=2.0
public static final int MIN_SENSITIVITY = 1;
public static final int MAX_SENSITIVITY = 10;
public static final int DEFAULT_SENSITIVITY = 5;

public static double getThresholdMultiplier(int sensitivity) {
    if (sensitivity < 1 || sensitivity > 10) { throw ... }
    if (sensitivity <= 5) {
        return 2.0 - (sensitivity - 1) * 0.2625;
    } else {
        return 0.95 - (sensitivity - 5) * 0.09;
    }
}
public static int getDefaultSensitivity() { return DEFAULT_SENSITIVITY; }
```

---

## 2. AnomalyDetectionService — 入口签名变更

### 构造器

```diff
- public AnomalyDetectionService()
- public AnomalyDetectionService(int normalThreshold)
- public AnomalyDetectionService(int normalThreshold, BaselineDataProvider provider)
+ // 保留以上3个（均委托到新增的全参构造，默认 5,5）
+ public AnomalyDetectionService(int normalThreshold, BaselineDataProvider provider,
+                                 int defaultWarmupSensitivity, int defaultActiveSensitivity)
+ // 新增成员：
+ private final int defaultWarmupSensitivity;
+ private final int defaultActiveSensitivity;
```

### 检测入口

```diff
- public DetectionResult detect(FeatureVector vector, String resourceId, double sensitivity)
+ public DetectionResult detect(FeatureVector vector, String resourceId)
+     // 使用构造器传入的默认灵敏度
+ public DetectionResult detect(FeatureVector vector, String resourceId,
+                                int warmupSensitivity, int activeSensitivity)
+     // 显式传参
```

### 检测流程内部

```diff
  // Warmup 分支
- warmupDetector.detect(vector, historyNormals, sensitivity)
+ warmupDetector.detect(vector, historyNormals, warmupSensitivity)

  // Active 分支
- baselineProvider.getBaselineStats(resourceId, sensitivity)
+ baselineProvider.getBaselineStats(resourceId, activeSensitivity)
```

---

## 3. WarmupDetector — 入参类型变更

```diff
- public WarmupDetectionResult detect(FeatureVector vector, List<FeatureVector> historyNormals,
-                                      double sensitivity)
+ public WarmupDetectionResult detect(FeatureVector vector, List<FeatureVector> historyNormals,
+                                      int sensitivity)

-     double thresholdMultiplier = SensitivityAdjuster.getThresholdMultiplier(sensitivity);
+     double thresholdMultiplier = SensitivityAdjuster.getThresholdMultiplier(sensitivity);
      // 方法体零改动——just 入参类型变了
```

无参重载：
```diff
- return detect(vector, historyNormals, SensitivityAdjuster.getDefaultSensitivity());
+ return detect(vector, historyNormals, SensitivityAdjuster.DEFAULT_SENSITIVITY);
```

---

## 4. HeuristicRule 接口 + 4 条规则实现

### 接口

```diff
  @FunctionalInterface
  public interface HeuristicRule {
      RuleResult evaluate(FeatureVector vector);
-     default RuleResult evaluate(FeatureVector vector, double sensitivity) {
+     default RuleResult evaluate(FeatureVector vector, int sensitivity) {
          return evaluate(vector);
      }
      default String getRuleName() { ... }
  }
```

### 4 条规则（仅方法签名 + 内部调用）

```diff
  // ModificationRatioRule, BurstModPurityRule, FileTypeConcentrationRule, InterOpTimeCvRule
- public RuleResult evaluate(FeatureVector vector, double sensitivity) {
-     double multiplier = SensitivityAdjuster.getThresholdMultiplier(sensitivity);
+ public RuleResult evaluate(FeatureVector vector, int sensitivity) {
+     double multiplier = SensitivityAdjuster.getThresholdMultiplier(sensitivity);
      // 方法体零改动
  }
```

> **未变更：** `HighValueTargetingRule`、`DeletionIntensityRule` 不覆盖灵敏度方法，使用接口默认实现

---

## 5. BaselineDataProvider + ExternalBaselineProvider

```diff
  public interface BaselineDataProvider {
      ...
-     BaselineStatsDTO getBaselineStats(String resourceId, double sensitivity);
+     BaselineStatsDTO getBaselineStats(String resourceId, int sensitivity);
  }

  // ExternalBaselineProvider
- public BaselineStatsDTO getBaselineStats(String resourceId, double sensitivity) {
+ public BaselineStatsDTO getBaselineStats(String resourceId, int sensitivity) {
      LOG.warning("[NOT YET IMPLEMENTED] ...");
      return null;
  }
```

---

## 6. 辅助工具类（optimizer 包）

### WarmupValidator

```diff
- private final double sensitivity;
- public WarmupValidator(..., double sensitivity)
+ private final int sensitivity;
+ public WarmupValidator(..., int sensitivity)
```

### WeightOptimizationRunner

```diff
- double sensitivity = 0.7;
+ int sensitivity = SensitivityAdjuster.DEFAULT_SENSITIVITY;

- for (double testSens : new double[]{0.7, 1.0})
+ for (int testSens : new int[]{SensitivityAdjuster.DEFAULT_SENSITIVITY, SensitivityAdjuster.MAX_SENSITIVITY})
```

---

## 7. 测试适配

### SensitivityAdjusterTest
- 移除：9 个 double 测试用例
- 新增：**19 个测试** — int 1-10 每个值精确验证 + 边界 + 异常 + 单调性验证

### AnomalyDetectionServiceTest
- 6 处 `detect(vector, "id", 0.7)` → `detect(vector, "id")`（无参重载）
- 5 处匿名 `BaselineDataProvider` 的 `getBaselineStats(String r, double s)` → `int s`

### WarmupActiveIntegrationTest
- 2 处 `detect(vec, "integ-res", 0.7)` → `detect(vec, "integ-res")`
- 1 处 `getBaselineStats(String r, double s)` → `int s`

### 无需改动的测试
- `WarmupDetectorTest`（使用无参 detect）
- `HeuristicRuleTest`（使用无参 evaluate）
- `ActiveDetectorTest`（不涉及 sensitivity）
- `PreCheckServiceTest`（不涉及 sensitivity）

---

## 8. 映射验证表

| int | multiplier | 语义 |
|:---:|:----------:|------|
| 1 | 2.0 | 最保守 |
| 2 | 1.738 | |
| 3 | 1.475 | |
| 4 | 1.213 | |
| **5** | **0.95** | **中灵敏度（默认）** |
| 6 | 0.86 | |
| 7 | 0.77 | |
| 8 | 0.68 | |
| 9 | 0.59 | |
| 10 | 0.5 | 最灵敏 |

---

## 汇总：15 个变更文件

| 文件 | 变更类型 |
|------|---------|
| `SensitivityAdjuster.java` | 重写（int 映射 + 分段线性） |
| `AnomalyDetectionService.java` | 构造器 + 双 int 入口 + 无参重载 |
| `WarmupDetector.java` | 入参 double→int |
| `HeuristicRule.java` | 接口方法 double→int |
| `ModificationRatioRule.java` | 方法签名 double→int |
| `BurstModPurityRule.java` | 同上 |
| `FileTypeConcentrationRule.java` | 同上 |
| `InterOpTimeCvRule.java` | 同上 |
| `BaselineDataProvider.java` | 接口方法 double→int |
| `ExternalBaselineProvider.java` | 实现方法 double→int |
| `WarmupValidator.java` | 字段 + 构造器 double→int |
| `WeightOptimizationRunner.java` | 常量替换 + 迭代数组 int |
| `SensitivityAdjusterTest.java` | 重写（19 个用例） |
| `AnomalyDetectionServiceTest.java` | 6 处调用适配 |
| `WarmupActiveIntegrationTest.java` | 3 处调用适配 |
