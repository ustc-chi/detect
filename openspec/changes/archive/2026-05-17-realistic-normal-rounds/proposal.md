## Why

The benchmark's normal baseline rounds (`evolveNormalRound()`) are too sterile — they only contain modify/add/delete operations, all timestamped during business hours, with uniform activity levels. Real NAS filesystems show normal rename activity (versioning, organization), legitimate after-hours work (night builds, late developers, automated scripts), and natural volatility (some days busy, some quiet). The current baseline makes features like `rename_correlation` and `wall_clock_anomaly` too sensitive because they never see legitimate examples of those patterns during calibration.

## What Changes

- **Add rename operations to normal rounds**: 5–15% of operations in `evolveNormalRound()` will be renames (delete old path + add new path with versioning suffix like `_v2`, `_backup`, `_old`, `_final`, `_copy`). This creates legitimate rename_correlation signal in the baseline.
- **Add after-hours activity**: ~15–25% of normal rounds will have some operations timestamped outside 8–18h business hours (evening 18–23h or early morning 5–8h). Simulates late developers, automated backup scripts, and cron jobs. This gives `wall_clock_anomaly` a realistic hour-level baseline.
- **Add activity volatility**: Activity levels will vary more naturally — some rounds at 40% of average (quiet days), some at 200% (busy days), with occasional concentrated bursts (50–200 ops in 5–15 minutes). This broadens the baseline distribution for `peak_burst_velocity` and `temporal_uniformity`.
- **Regenerate benchmark data**: After code changes, re-run `BenchmarkDataGenerator` to regenerate `benchmark-data/` with the enhanced normal rounds.
- **Run full benchmark verification**: Verify ≥99% attack detection rate, 0% vanilla normal FP rate.

## Capabilities

### New Capabilities

- `realistic-normal-baseline`: Enhances `evolveNormalRound()` with rename operations, after-hours activity, and activity volatility for more realistic baseline calibration.

### Modified Capabilities

- `ransomware-test-generator`: Normal round generation now includes renames, after-hours timestamps, and volatility. Benchmark data files must be regenerated.

## Impact

- **Code**: `FilesystemState.java` (evolveNormalRound), `BenchmarkDataGenerator.java` (data regeneration), possibly `IntermittentEncryptionBenchmark.java` (if manifest format changes)
- **Data**: `benchmark-data/` directory fully regenerated (all files rewritten)
- **Detection**: Baseline statistics shift slightly (rename_correlation and wall_clock_anomaly baselines will have non-zero values). Threshold may change. All 68 attack cases must still be detected. False positive rate on vanilla normals must remain 0%.
- **README/benchmark.md**: Update normal round description to reflect new activity patterns
