## 1. Feature Vector & Constants Update

- [x] 1.1 Update `RansomwareFeatureVector` to require exactly 14 elements (was 13), update `FEATURE_NAMES` array to 14 elements with new index mapping: `["total_operations", "modification_ratio", "deletion_intensity", "directory_spread", "extension_diversity", "suspicious_extension_ratio", "peak_burst_velocity", "avg_modified_size", "size_std_dev", "high_value_ext_ratio", "burst_mod_purity", "file_type_concentration", "size_change_kurtosis", "inter_op_time_cv"]`
- [x] 1.2 Update `WeightedEuclideanScorer.DEFAULT_WEIGHTS` to 14 elements with rebalanced values: `[2.0, 3.0, 0.5, 1.5, 0.8, 10.0, 3.5, 1.5, 1.0, 2.5, 3.0, 2.0, 2.0, 2.5]` and set N=14
- [x] 1.3 Update `BaselineStatistics` to work with 14-dimensional vectors (constructor should auto-detect dimensionality from input — verify it does)
- [x] 1.4 Update `ZScoreExplainer` to use FEATURE_NAMES from the updated `RansomwareFeatureVector`

## 2. Feature Extraction — Consolidated Deletion Intensity

- [x] 2.1 In `RansomwareFeatureExtractor`, replace the old F2 (`deletion_ratio`) and F3 (`bytes_removed`) computation with consolidated `deletion_intensity` at new index 2: `log1p(totalDeletedBytes / daysBetweenSnapshots) × (deletedCount / totalOps)`. Return 0.0 when `totalOps == 0` or `deletedCount == 0`
- [x] 2.2 Remove the old `bytes_removed` computation (was old index 3). The `bytes_removed` logic is now folded into `deletion_intensity`

## 3. Feature Extraction — Size Change Kurtosis

- [x] 3.1 In `RansomwareFeatureExtractor`, add `size_change_kurtosis` computation at index 12: collect `log1p(size_i)` values for all modified files with `size > 0`, compute excess kurtosis = `m4 / (σ² × σ²) - 3.0`. Return 0.0 when fewer than 4 modified files or variance < 1e-12
- [x] 3.2 Ensure the same `log1p(size)` values collected for `avg_modified_size` (idx 7) and `size_std_dev` (idx 8) are reused for kurtosis — single collection, three computations

## 4. Feature Extraction — Inter-Operation Time CV

- [x] 4.1 In `RansomwareFeatureExtractor`, add `inter_op_time_cv` computation at index 13: from the sorted timestamp data (already sorted for burst features), compute consecutive deltas, then `σ / μ`. Return 0.0 when fewer than 2 valid timestamps or `μ < 0.001`
- [x] 4.2 In streaming mode: compute `inter_op_time_cv` from the same sorted burst temp file data, after sort and before temp file deletion. No additional temp file or sort pass
- [x] 4.3 In in-memory mode: compute from the same sorted timestamps used for burst features

## 5. Index Re-mapping — Shift Existing Features

- [x] 5.1 Remap all features that shift index due to F2/F3 consolidation: `directory_spread` (4→3), `extension_diversity` (5→4), `suspicious_extension_ratio` (6→5), `peak_burst_velocity` (7→6), `avg_modified_size` (8→7), `size_std_dev` (9→8), `high_value_ext_ratio` (10→9), `burst_mod_purity` (11→10), `file_type_concentration` (12→11)
- [x] 5.2 Verify the final feature vector matches the 14-element index mapping from the design doc
- [x] 5.3 Update all references to feature indices in comments, debug output, and `ZScoreExplainer`

## 6. CLI Updates

- [x] 6.1 Update `RansomwareDetectorCli` to accept 14 comma-separated `--weights` values (was 13). Validate length and reject with error code 1 if wrong count
- [x] 6.2 Update `WeightOptimizerCli` to optimize over 14-dimensional weight space (was 13)
- [x] 6.3 Update CLI help text and weight table display to reflect new 14-feature layout

## 7. Attack Generator & Benchmark

- [x] 7.1 Verify `AttackGenerator` needs no changes — it produces `DiffEntry` lists, not feature vectors directly
- [x] 7.2 Update `IntermittentEncryptionBenchmark` to use 14-feature extraction and rebalanced weights. Add specific reporting for `slow_drip_encrypt` (B2) and `size_mimic_normal` (B5) detection with top contributing features
- [x] 7.3 Run full benchmark: 72 original+intermittent attacks + 24 adversarial variants + 24 normal rounds. Verify 0 false positives and >= 72/72 original detection maintained

## 8. Validation & Threshold Calibration

- [x] 8.1 Run baseline calibration with 14 features and rebalanced weights. Record new threshold value
- [x] 8.2 Verify safety margin: lowest attack score / threshold >= 1.1 (acceptable margin)
- [x] 8.3 If margin is insufficient, consider adjusting threshold percentile (97→98) or fine-tuning new feature weights
- [x] 8.4 Verify adversarial variant detection: `slow_drip_encrypt` and `size_mimic_normal` at all 3 padding levels SHALL be detected
