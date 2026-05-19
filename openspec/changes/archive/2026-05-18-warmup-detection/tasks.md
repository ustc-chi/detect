## 1. WarmupDetector Core

- [x] 1.1 Create `WarmupDetector.java` with `classify(RansomwareFeatureVector) -> boolean` implementing 5 heuristic rules (modification_ratio > 0.85, peak_burst_velocity > 5000, temporal_uniformity > 0.7, burst_mod_purity > 0.90, rename_correlation > 0.5) with ≥2-rule trigger threshold
- [x] 1.2 Add unit tests for WarmupDetector: ransomware-like vector (true), normal vector (false), single-rule match (false), threshold boundary (strictly greater than), all 5 rules matching (true)

## 2. RansomwareDetector Warmup Integration

- [x] 2.1 Modify `RansomwareDetector` constructor to accept null BaselineStatistics/AnomalyThreshold and enter warmup mode
- [x] 2.2 Modify `processRound()` to delegate to WarmupDetector during warmup (baselineCount < 5), producing DetectionResult with score=matchingRuleCount, threshold=2
- [x] 2.3 Gate baseline accumulator: exclude vectors classified as anomalous by WarmupDetector during warmup period
- [x] 2.4 Add warmup duration tracking and WARN log when warmup exceeds 10 rounds
- [x] 2.5 Add integration tests: cold-start detection, warmup-to-statistical transition at round 5, baseline exclusion of anomalous rounds

## 3. CLI Changes

- [x] 3.1 Make `--baseline-dir` optional in `RansomwareDetectorCli` (default: no baseline, warmup mode)
- [x] 3.2 Add `warmup` (boolean) and `baseline_count` (integer) columns to CSV output
- [x] 3.3 Test CLI: run without `--baseline-dir` starts in warmup, run with `--baseline-dir` skips warmup

## 4. Benchmark Phase 0

- [x] 4.1 Add Phase 0 to `IntermittentEncryptionBenchmark`: process 5 rounds without baseline, testing warmup heuristic detection and baseline accumulation
- [x] 4.2 Verify Phase 0 ransomware rounds are detected by WarmupDetector
- [x] 4.3 Verify Phase 0 clean rounds accumulate in baseline and transition to statistical detection at round 5
- [x] 4.4 Run full benchmark and verify ≥99% attack detection, 0% vanilla normal FP, no exceptions
