## Why

The current 14-feature ransomware detection set has 3 broken features (F7/F8/F12 measure file-size composition, not encryption deltas — the root cause is missing `old_size` in `SnapdiffRecord`), 4 weak/redundant features (F3/F4/F5/F11 produce near-zero signal for most attack patterns), and leaves 6 adversarial variants undetected at p50/p70 padding (B1 backup-disguise, B2 slow-drip, B3 random-jitter). A critical review against academic literature (SHIELD, Minerva, CryptoDrop) and real-world systems (NetApp ARP, Sentinel, REKD) confirms that the strongest missing signals — per-user concentration, directory coverage depth, temporal uniformity, and rename correlation — are all computable from existing metadata fields without requiring `old_size` or file content access.

## What Changes

- **REMOVE** 7 features: F3 `directory_spread`, F4 `extension_diversity`, F5 `suspicious_extension_ratio` (moved to Phase 1 only), F7 `avg_modified_size`, F8 `size_std_dev`, F11 `file_type_concentration`, F12 `size_change_kurtosis`
- **FIX** 4 features: F2 `total_operations` (normalize by baseline median), F9 `high_value_ext_ratio` (exponential moving average smoothing), F13 `inter_op_time_cv` (compute within burst window only), F14 `high_value_file_coverage` (floor/cap/undefined handling)
- **ADD** 5 features: `directory_coverage_depth` (breadth-first traversal detection), `temporal_uniformity` (batch regularity for slow-drip), `rename_correlation` (reconstructed rename detection), `wall_clock_anomaly` (after-hours detection), `per_type_entropy` (operation type distribution)
- **KEEP** 3 features as-is: F1 `modification_ratio`, F6 `peak_burst_velocity`, F10 `burst_mod_purity`
- **BREAKING**: Feature vector shrinks from 14 to 12 dimensions. `RansomwareFeatureVector`, `WeightedEuclideanScorer`, `ZScoreExplainer`, `IntermittentEncryptionBenchmark` all change shape. Threshold must be recalibrated.

## Capabilities

### New Capabilities
- `directory-coverage-depth`: Ratio of unique directories with modifications to total directories, plus average depth uniformity of modified file paths
- `temporal-uniformity`: 1 − CV of operation counts in sequential 5-minute bins within a round — detects sustained regular activity
- `rename-correlation`: Count of correlated (added, deleted) record pairs where paths are similar — reconstructs renames from snapdiff
- `wall-clock-anomaly`: Z-score of operation count at current hour vs historical same-hour baseline — detects after-hours attacks
- `per-type-entropy`: Shannon entropy of operation type distribution {added, modified, deleted} — replaces extension-based diversity measures

### Modified Capabilities
- `ransomware-feature-extraction`: Feature vector changes from 14→12 dimensions; 3 features kept, 4 fixed, 7 removed, 5 added; extraction logic restructured
- `statistical-anomaly-detector`: Weight array and threshold recalibrated for 12-feature set; baseline now includes per-hour operation counts for wall-clock anomaly
- `file-type-concentration`: Removed (replaced by `per-type-entropy`)
- `size-change-kurtosis`: Removed (fundamentally broken without `old_size`)
- `inter-op-time-cv`: Compute CV within burst window only (same 300s window as peak_burst_velocity/burst_mod_purity), not across entire round
- `burst-mod-purity`: No logic change; weight adjusted from 2.5→3.0
- `adversarial-attack-variants`: Benchmark updated for 12-feature vector; expected detection improvement on B1/B2/B3 at p50/p70

## Impact

- **Core files**: `RansomwareFeatureExtractor.java`, `RansomwareFeatureVector.java`, `BurstDataFile.java`, `WeightedEuclideanScorer.java`, `ZScoreExplainer.java`, `BaselineStatistics.java`, `RansomwareDetector.java`
- **Generator/benchmark**: `IntermittentEncryptionBenchmark.java` must update feature dimension and weight array
- **Threshold**: Must recalibrate anomaly threshold (~16.50) for new feature set
- **Baseline**: `BaselineStatistics` must accumulate per-hour operation counts (24 bins) for wall-clock anomaly
- **Backward incompatible**: Any serialized feature vectors or saved baselines from the 14-feature system will not load
