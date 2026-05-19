## 1. DirectionalValidator Implementation

- [x] 1.1 Create `DirectionalValidator.java` in `com.anomalydetection.detector` with constructor accepting `double[] weights` and `double threshold`
- [x] 1.2 Implement `ValidationResult validate(double[] zScores)` method computing E_up, E_down, ratio, and reversal decision (strict greater-than on ratio > threshold)
- [x] 1.3 Implement `ValidationResult` inner class or record containing: `boolean reversed`, `double ratio`, `double E_up`, `double E_down`, `List<FeatureDeviation> topDeviations`
- [x] 1.4 Implement `FeatureDeviation` record with: `String name`, `double zScore`, `String direction` ("ABOVE"/"BELOW")
- [x] 1.5 Handle edge case: when threshold is 0, `validate()` SHALL always return `reversed=false` (validation disabled)

## 2. Unit Tests for DirectionalValidator

- [x] 2.1 Test quiet-day scenario: all z-scores negative → ratio > 0.75 → reversed=true
- [x] 2.2 Test ransomware scenario: most z-scores positive → ratio < 0.25 → reversed=false
- [x] 2.3 Test borderline scenario: ratio exactly equals threshold → reversed=false (strict GT)
- [x] 2.4 Test disabled validation: threshold=0 → always reversed=false regardless of z-scores
- [x] 2.5 Test zero z-scores: all z=0 → ratio=0 → reversed=false
- [x] 2.6 Test single dominant feature: one large positive z, rest near zero → ratio low → reversed=false

## 3. Integration into RansomwareDetector

- [x] 3.1 Add `DirectionalValidator` field to `RansomwareDetector`, initialized during baseline calibration
- [x] 3.2 In the detection flow: after `score > threshold` triggers ANOMALY, call `DirectionalValidator.validate(zScores)` using z-scores from `ZScoreExplainer`
- [x] 3.3 If validator returns `reversed=true`: change verdict to NORMAL, log WARNING with score/ratio/top-5 deviations, skip self-learning
- [x] 3.4 If validator returns `reversed=false`: confirm ANOMALY verdict (existing behavior unchanged)
- [x] 3.5 Ensure normal rounds (score ≤ threshold) never invoke DirectionalValidator

## 4. CLI Parameter

- [x] 4.1 Add `--direction-threshold` parameter to `RansomwareDetectorCli` with default 0.75
- [x] 4.2 Validate range: reject values < 0 or > 1 with error message and exit code 1
- [x] 4.3 Pass the threshold value through to `RansomwareDetector` construction
- [x] 4.4 When value is 0, do not construct DirectionalValidator (disabled path)

## 5. Benchmark Test Cases

- [x] 5.1 Add "extremely quiet day" test case to `BenchmarkDataGenerator`: 10-20% of normal operation count, 2+ hour window, no bursts, mixed operation types — designed to exceed the current threshold
- [x] 5.2 Verify the quiet day IS flagged as ANOMALY without directional validation (baseline: proves the problem exists)
- [x] 5.3 Verify the quiet day is REVERSED to NORMAL with directional validation at threshold 0.75
- [x] 5.4 Run full benchmark (68 attacks + 10 vanilla normals + 16 irregular normals) with directional validation enabled and verify: 68/68 detection (no regressions), 0/10 vanilla FP
- [x] 5.5 Run full benchmark with `--direction-threshold 0` and verify results match current baseline exactly

## 6. Documentation Updates

- [x] 6.1 Update `README.md` CLI parameters table with `--direction-threshold` entry
- [x] 6.2 Update `README.md` detection flow section with directional validation phase
- [x] 6.3 Update `benchmark.md` with quiet-day reversal results
- [x] 6.4 Update `normalRounds.md` if the quiet-day test case affects normal round documentation
