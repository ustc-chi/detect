## Why

Feature 11 (`wall_clock_anomaly`, index 10) has been a placeholder returning 0.0 since the 12-feature redesign. The infrastructure exists (`BaselineStatistics` has per-hour bins and `addHourlyObservation`/`computeHourlyStats`/`getHourlyStats` methods), but the feature extractor never computes the actual z-score. This wastes a feature slot that was designed to catch after-hours attacks — a common ransomware pattern where operations spike at unusual hours (3 AM vs typical 9-5 baseline). Activating it requires wiring the extractor to the hourly baseline and adding test coverage that exercises off-hours scenarios.

## What Changes

- **Implement `wall_clock_anomaly` computation** in `RansomwareFeatureExtractor`:
  - Extract hour-of-day (0-23) from the earliest non-EPOCH `changeTime` in the round
  - Look up per-hour baseline (`median_h`, `mad_h`) from `BaselineStatistics`
  - Compute z-score: `(totalOps - median_h) / (mad_h * 1.4826)`
  - Clamp to [-10.0, 10.0]; return 0.0 if no hourly baseline exists or all timestamps are EPOCH
- **Wire hourly observation accumulation** into `RansomwareDetector` and CLI:
  - During baseline loading, populate per-hour operation counts via `addHourlyObservation`
  - After baseline construction, call `computeHourlyStats()`
  - Pass `BaselineStatistics` reference into `RansomwareFeatureExtractor` so it can query hourly stats
- **Update benchmark/test data** to exercise the feature:
  - Generate some baseline rounds at varied hours (not all at 08:00) so per-hour baselines have signal
  - Add after-hours attack test cases (e.g., 3 AM burst) that should trigger high `wall_clock_anomaly`
  - Add normal-hours activity that should score near-zero
- **Update unit tests**:
  - Verify `wall_clock_anomaly` returns non-zero for off-hours bursts
  - Verify it returns 0.0 during learning period (no hourly baseline)
  - Verify it returns 0.0 when all timestamps are EPOCH

## Capabilities

### New Capabilities
- `wall-clock-anomaly`: Compute z-score of current round's operation count against historical same-hour baseline, clamped to [-10, 10]. Already has an existing spec at `openspec/specs/wall-clock-anomaly/spec.md`.

### Modified Capabilities
- `ransomware-feature-extraction`: The `extract()` and `extractFromFile()` methods will no longer hard-code feature index 10 to 0.0; they will compute the actual wall-clock anomaly value. The method signatures will need a way to access `BaselineStatistics` (either passed in or held as a field).
- `statistical-anomaly-detector`: The `RansomwareDetector` will accumulate per-hour observations during baseline loading and pass baseline stats to the extractor.

## Impact

- `RansomwareFeatureExtractor` — adds `BaselineStatistics` dependency, implements actual feature 10 computation
- `RansomwareDetector` — accumulates hourly observations after baseline loading, passes stats to extractor
- `RansomwareDetectorCli` — may need to wire the baseline stats into the extractor
- `IntermittentEncryptionBenchmark` — test data generation must vary hours; add after-hours attack scenarios
- `RansomwareFeatureExtractorTest` / `RansomwareDetectorTest` — new assertions for wall_clock_anomaly behavior
- Threshold may shift slightly after re-running the benchmark with the new active feature
