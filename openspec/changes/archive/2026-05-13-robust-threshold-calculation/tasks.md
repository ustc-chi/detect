## 1. Core Threshold Filtering

- [x] 1.1 Add IQR computation helper method to `AnomalyThreshold.java` — compute Q1, Q3, IQR from a sorted list of doubles. Use the same median helper pattern as `BaselineStatistics.java`
- [x] 1.2 Add `iqrMultiplier` field to `AnomalyThreshold` with default value 2.5. Add new 4-arg constructor: `AnomalyThreshold(List<RansomwareFeatureVector>, WeightedEuclideanScorer, double percentile, double iqrMultiplier)`
- [x] 1.3 Implement IQR-based outlier filtering: after computing all self-scores, sort them, compute Q1/Q3/IQR, filter scores exceeding Q3 + k × IQR. Skip filtering when N < 5 or k == 0
- [x] 1.4 Implement 3× median cap: compute median of filtered scores, cap threshold at `min(percentile_value, 3 × median)`
- [x] 1.5 Add diagnostic logging: when scores are filtered, log WARNING with count and values of filtered scores. Use `System.getLogger()` or `java.util.logging`

## 2. Constructor Backward Compatibility

- [x] 2.1 Update existing 3-arg constructor `AnomalyThreshold(List, WeightedEuclideanScorer, double)` to delegate to 4-arg constructor with default iqrMultiplier=2.5
- [x] 2.2 Verify `AnomalyThreshold(double threshold)` direct constructor is unchanged (used for testing)

## 3. CLI Integration

- [x] 3.1 Add `--threshold-iqr-multiplier` option to `RansomwareDetectorCli.java` with default value 2.5 and description "IQR multiplier for baseline outlier filtering (0 to disable)"
- [x] 3.2 Pass iqrMultiplier value from CLI to AnomalyThreshold constructor

## 4. Tests

- [x] 4.1 Add unit test: outlier baseline round is filtered out — construct AnomalyThreshold with 24 vectors where 1 has score ~42, verify threshold is not inflated
- [x] 4.2 Add unit test: clean baseline produces same threshold as before — verify IQR filtering removes nothing on normal data
- [x] 4.3 Add unit test: small baseline (N < 5) skips IQR filtering but applies median cap
- [x] 4.4 Add unit test: iqrMultiplier=0 disables filtering, threshold is raw percentile (capped by median)
- [x] 4.5 Add unit test: median cap applies when percentile exceeds 3× median
- [x] 4.6 Add unit test: diagnostic warning is emitted when outliers are filtered
- [x] 4.7 Verify existing `RansomwareDetectorTest` and `EndToEndTest` still pass unchanged

## 5. Benchmark Validation

- [x] 5.1 Run `IntermittentEncryptionBenchmark` with robust threshold (default k=2.5) and verify: 96/96 attack detection, 0/24 vanilla normal FP
- [x] 5.2 Run benchmark with 1-2 contaminated baseline rounds (e.g., inject a backup_surge pattern) and verify threshold is NOT inflated, attacks still detected
- [x] 5.3 Run benchmark with iqrMultiplier=0 and verify behavior matches old system (raw percentile, still with median cap)

## 6. Documentation

- [x] 6.1 Update README.md threshold calculation section to document IQR filtering, median cap, and new CLI parameter
- [x] 6.2 Update README.md CLI parameters table with `--threshold-iqr-multiplier` entry
