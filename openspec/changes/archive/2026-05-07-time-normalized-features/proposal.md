## Why

Features 0 (`total_operations`), 3 (`bytes_removed`), 4 (`directory_spread`), and 5 (`extension_diversity`) are absolute counts/sums that scale linearly with the interval between snapshots. A snapdiff covering 7 days naturally has ~7× more operations than a 1-day snapdiff, inflating the anomaly score and causing false positives for longer-interval baselines or false negatives for very short intervals. The detector assumes all baseline and input snapdiffs share a fixed interval, which is not realistic in production.

## What Changes

- **BREAKING**: `RansomwareFeatureExtractor` constructor and both extraction methods (`extract()`, `extractFromFile()`) accept a new `double daysBetweenSnapshots` parameter (default 2.0). Features 0, 3, 4, 5 are divided by this value before being stored in the feature vector.
  - Feature 0: `total_operations` → `total_operations / days`
  - Feature 3: `bytes_removed` → `log1p(sum_deleted_bytes / days)` (divide raw sum, then apply log1p)
  - Feature 4: `directory_spread` → `unique_directories / days`
  - Feature 5: `extension_diversity` → `unique_extensions / days`
- The remaining 9 features (indices 1, 2, 6, 7, 8, 9, 10, 11, 12) are ratios, averages, velocities, or statistics and are already interval-invariant — no changes needed.
- CLI (`RansomwareDetectorCli`) gains a `--days-between` option (default 2.0) propagated to the extractor.
- Weight optimizer CLI (`WeightOptimizerCli`) similarly updated.
- Test data generator updated to produce snapdiffs with varying intervals; baseline tests verify interval-invariance.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `ransomware-feature-extraction`: Extractor now accepts a `daysBetweenSnapshots` parameter and normalizes absolute-valued features (0, 3, 4, 5) to per-day rates.
- `statistical-anomaly-detector`: Detector and CLI propagate the days-between parameter through to the feature extractor. The scoring/threshold pipeline is unchanged — only the feature values entering it are now per-day normalized.
- `ransomware-test-generator`: Generator can produce snapdiffs with configurable snapshot intervals for interval-sensitivity testing.

## Impact

- **API**: `RansomwareFeatureExtractor` constructor signature changes. All callers (CLI, tests, generator, optimizer) must pass the new parameter or use the default.
- **Feature semantics**: Features 0, 3, 4, 5 change from absolute values to per-day rates. Existing baselines calibrated with a fixed interval need recalibration with the correct `daysBetweenSnapshots` value.
- **Backward compatibility**: Default value of 2.0 days preserves approximate behavior for existing 2-day interval baselines. No changes to weights, thresholds, or scoring formula.
- **Files affected**: `RansomwareFeatureExtractor.java`, `RansomwareDetectorCli.java`, `WeightOptimizerCli.java`, `RansomwareDetector.java`, `RansomwareTestGenerator.java`, all test classes.
