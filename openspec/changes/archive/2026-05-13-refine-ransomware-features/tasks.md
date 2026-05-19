## 1. Feature Vector & Scorer Foundation

- [x] 1.1 Update `RansomwareFeatureVector` to 12 dimensions: change size check from 14→12, update `FEATURE_NAMES` array to the new 12-feature names, verify constructor validation
- [x] 1.2 Update `WeightedEuclideanScorer.DEFAULT_WEIGHTS` to 12 elements with new weights: `[2.0, 2.5, 5.0, 3.0, 1.5, 2.0, 2.0, 2.5, 2.5, 3.0, 1.5, 2.0]`
- [x] 1.3 Update `ZScoreExplainer` feature name array to match new 12-feature names
- [x] 1.4 Add dimension mismatch detection in `BaselineStatistics` — throw `IllegalArgumentException` with clear message ("expected 12 dimensions, got N") when encountering wrong-dimension vectors
- [x] 1.5 Update `RansomwareDetectorCli` `--weights` parsing to accept exactly 12 values (reject != 12)

## 2. Feature Extraction — Fix Existing Features

- [x] 2.1 Fix `total_operations` (idx 0→1): normalize by dividing by `daysBetweenSnapshots` and then by baseline median. During learning period, use raw `total_operations / daysBetweenSnapshots`
- [x] 2.2 Fix `high_value_ext_ratio` (idx 9→4): implement exponential moving average smoothing across rounds — store EMA state in `RansomwareFeatureExtractor`, alpha=0.3, initialize from first round's raw value
- [x] 2.3 Fix `inter_op_time_cv` → `inter_op_time_cv_burst` (idx 13→5): recompute CV using ONLY timestamps within the peak burst window (same 300s window from `peak_burst_velocity`), not the entire round
- [x] 2.4 Fix `high_value_file_coverage` (idx 14→6): add floor at 0.0, cap at 1.0, return 0.0 when undefined (no baseline HV modifications)
- [x] 2.5 Remove broken feature extraction code for: `deletion_intensity` (old idx 2), `directory_spread` (old idx 3→4), `extension_diversity` (old idx 4→5), `suspicious_extension_ratio` (old idx 5→6), `avg_modified_size` (old idx 7→8), `size_std_dev` (old idx 8→9), `file_type_concentration` (old idx 11→12), `size_change_kurtosis` (old idx 12)

## 3. Feature Extraction — New Features

- [x] 3.1 Implement `directory_coverage_depth` (idx 7): extract unique parent directories from modified files, compute `unique_dirs * (1.0 / (1.0 + std_dev(depths)))`, return 0.0 if no modified files
- [x] 3.2 Implement `temporal_uniformity` (idx 8): from sorted burst data, divide time span into 5-min bins, count ops per bin, compute `1.0 - (σ/μ)` of bin counts, return 0.0 if < 3 bins
- [x] 3.3 Implement `rename_correlation` (idx 9): match deleted+added records in same directory where filenames share prefix (≥3 chars), return `rename_count / total_operations`, 0.0 if no ops
- [x] 3.4 Implement `wall_clock_anomaly` (idx 10): extract hour from earliest `changeTime`, compute z-score vs per-hour baseline `(ops - median_h) / (mad_h * 1.4826)`, clamp ±10, return 0.0 if no baseline for this hour
- [x] 3.5 Implement `per_type_entropy` (idx 11): count ops per type {added, modified, deleted}, compute Shannon entropy `H = -Σ(p_i * log2(p_i))`, return 0.0 if N=0

## 4. Baseline Infrastructure

- [x] 4.1 Add per-hour baseline accumulation to `BaselineStatistics`: for each learning round, add `total_operations` to the bin for the round's dominant hour (extracted from earliest `changeTime`)
- [x] 4.2 Compute per-hour median and MAD from the accumulated bins — store as `Map<Integer, double[]>` (hour → [median, mad])
- [x] 4.3 Expose per-hour baseline via `BaselineStatistics.getHourlyStats(int hour)` returning `[median, mad]` or null if no data

## 5. Integration & Restructure

- [x] 5.1 Restructure `RansomwareFeatureExtractor.extract()` to produce 12 features in the new index order, removing old features and inserting new ones
- [x] 5.2 Restructure `RansomwareFeatureExtractor.extractFromFile()` (streaming mode) to compute all 12 features from streaming pass + burst temp file, ensuring new features are computed from the same sorted data
- [x] 5.3 Update `BurstDataFile` to expose the burst window boundaries (start/end timestamps) so `inter_op_time_cv_burst` and `temporal_uniformity` can use them
- [x] 5.4 Ensure streaming and in-memory modes produce numerically identical results (within 1e-9 tolerance) for all 12 features

## 6. Benchmark & Threshold

- [x] 6.1 Update `IntermittentEncryptionBenchmark` for 12-feature vectors: change weight array, update feature dimension, adjust result formatting for new feature names
- [x] 6.2 Run full benchmark (136 test cases) with new feature set and derive new anomaly threshold from ROC curve
- [x] 6.3 Verify B1_p50, B1_p70, B2_p50, B2_p70, B3_p50, B3_p70 are now detected (the 6 previous misses)
- [x] 6.4 Verify no regression on previously-detected cases (90/96 detected should remain ≥90/96 or improve)
