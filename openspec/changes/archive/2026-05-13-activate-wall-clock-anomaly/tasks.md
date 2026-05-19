## 1. Core Feature Implementation

- [x] 1.1 Add `BaselineStatistics` field to `RansomwareFeatureExtractor` with setter method
- [x] 1.2 Implement `computeWallClockAnomaly()` private method in `RansomwareFeatureExtractor`:
  - Find earliest non-EPOCH `changeTime` to determine hour-of-day
  - Look up hourly stats from `BaselineStatistics` via `getHourlyStats(hour)`
  - Compute z-score: `(totalOps - median_h) / mad_h` (mad already scaled by 1.4826)
  - Clamp to [-10.0, 10.0]; return 0.0 if no baseline or no valid timestamps
- [x] 1.3 Update `extract()` method to call `computeWallClockAnomaly()` instead of hard-coding 0.0 at index 10
- [x] 1.4 Update `extractFromFile()` method to call `computeWallClockAnomaly()` instead of hard-coding 0.0 at index 10

## 2. Pipeline Wiring

- [x] 2.1 Modify `RansomwareDetector` constructor to pass `BaselineStatistics` to the `RansomwareFeatureExtractor` via setter
- [x] 2.2 Update `RansomwareDetectorCli` baseline loading logic to:
  - Call `addHourlyObservation(hour, opsCount)` for each baseline round after feature extraction
  - Call `baselineStats.computeHourlyStats()` after all baseline rounds are loaded
  - Pass the populated `BaselineStatistics` to the extractor before detection begins
- [x] 2.3 Ensure backward compatibility: if `BaselineStatistics` is null in extractor, feature returns 0.0

## 3. Test Data and Benchmark Updates

- [x] 3.1 Modify `IntermittentEncryptionBenchmark` baseline generation to vary `dayStart` hours (e.g., 09:00, 10:00, 14:00, 15:00) instead of all at 08:00
- [x] 3.2 Add after-hours attack test case generator (e.g., 03:00 burst with high op count) to benchmark
- [x] 3.3 Ensure normal rounds span multiple hours so per-hour baselines have distinct statistics
- [x] 3.4 Run benchmark and record new threshold after `wall_clock_anomaly` is active

## 4. Unit Tests

- [x] 4.1 Add test for `wall_clock_anomaly` with off-hours burst (3 AM, 3000 ops vs baseline median=50, MAD=20) → expect clamped z-score ≈ 10.0
- [x] 4.2 Add test for `wall_clock_anomaly` during normal hours (2 PM, 500 ops vs baseline median=450, MAD=150) → expect z-score ≈ 1.2
- [x] 4.3 Add test for `wall_clock_anomaly` during learning period (no hourly baseline) → expect 0.0
- [x] 4.4 Add test for `wall_clock_anomaly` with all EPOCH timestamps → expect 0.0
- [x] 4.5 Add test for `wall_clock_anomaly` when extractor has no `BaselineStatistics` → expect 0.0
- [x] 4.6 Update existing tests that assert on hard-coded 0.0 for feature index 10 to use dynamic values

## 5. Documentation and Verification

- [x] 5.1 Update README.md feature table to remove "placeholder (0.0)" note for `wall_clock_anomaly`
- [x] 5.2 Add description of `wall_clock_anomaly` behavior and computation to README
- [x] 5.3 Run full test suite (`mvn test`) and verify all tests pass
- [x] 5.4 Run benchmark and verify detection rates remain at 100% with new threshold
- [x] 5.5 Verify no regression in vanilla normal false positive rate
