## Why

Current benchmark shows 2/24 false positives (8.3%) with threshold ≈ 7.7 at 90th percentile. The primary FP driver is `size_std_dev` at weight 4.0 — filesystems with regular minor updates (log rotation, config file updates, database WAL writes) produce uniformly small size changes that look statistically similar to encryption. Meanwhile, `total_operations` at weight 1.0 is underweighted: ransomware's most reliable behavioral signal is a sheer volume spike (mass modification of files), and this feature should carry more discriminative power.

## What Changes

- Increase `total_operations` weight from 1.0 to 2.5 — ransomware mass-encryption events produce operation counts far above baseline, even when padded
- Reduce `size_std_dev` weight from 4.0 to 2.0 — uniform size changes from regular filesystem maintenance (cron jobs, log rotation, DB checkpoints) cause FPs at higher weights
- Increase `avg_modified_size` weight from 1.0 to 1.5 — compensates for reduced `size_std_dev` by emphasizing the absolute size shift signal (encryption typically increases file sizes by 1-5%, which shifts the mean)
- Adjust threshold percentile from 90% to 95% — reduces FPs with minimal impact on detection (lowest attack score is 10.2 vs threshold 7.7, providing 1.32× margin)
- Update `WeightedEuclideanScorer.DEFAULT_WEIGHTS` array
- Update `IntermittentEncryptionBenchmark.BASE_WEIGHTS` array
- Re-run full benchmark (72 attack cases + 24 normal) to validate 0 FPs while maintaining 72/72 detection

## Capabilities

### New Capabilities

_(none)_

### Modified Capabilities

- `statistical-anomaly-detector`: weight array values changing, default threshold percentile changing from 90% to 95%

## Impact

- `WeightedEuclideanScorer.java` — DEFAULT_WEIGHTS array updated
- `IntermittentEncryptionBenchmark.java` — BASE_WEIGHTS array updated
- `AnomalyThreshold.java` — default percentile parameter changes from 90.0 to 95.0 (if hardcoded default exists)
- `RansomwareDetectorCli.java` — default `--threshold-percentile` changes from 90.0 to 95.0
- `README.md` — weights table, threshold documentation, and benchmark results updated
- Existing weight optimization (`WeightOptimizer`) should still work — weights are just different defaults
