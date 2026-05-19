## Why

The current 13-feature detection system achieves 72/72 detection with 0 false positives against known attack patterns, but adversarial variants expose two critical gaps: (1) `slow_drip_encrypt` evades the fixed 5-minute burst window by distributing ~50 ops per 5-minute interval, and (2) `size_mimic_normal` evades avg_modified_size and size_std_dev by randomizing per-file size changes between -10% and +10%. Additionally, Features 2 (deletion_ratio) and 3 (bytes_removed) have high overlap — both measure deletion activity from different angles — wasting a dimension that could serve a more discriminative signal.

## What Changes

- **Add `size_change_kurtosis`** (new Feature 13): Kurtosis (4th standardized moment) of per-file size change ratios for modified files. Encryption produces a tight, uniform size-change distribution (low kurtosis), while adversarial randomization and normal activity produce broader distributions. Directly counters `size_mimic_normal` and `multi_family_combo`.
- **Add `inter_op_time_cv`** (new Feature 14): Coefficient of variation (σ/μ) of inter-operation time gaps. Automated ransomware produces regular timing patterns (low CV) even when throttled; human activity is irregular with natural clusters and gaps (high CV). Directly counters `slow_drip_encrypt`.
- **Consolidate F2+F3 into `deletion_intensity`**: Merge `deletion_ratio` (idx 2) and `bytes_removed` (idx 3) into a single feature `deletion_intensity = log1p(bytes_removed / daysBetweenSnapshots) × deletion_ratio`. This eliminates high overlap, freeing a dimension. **BREAKING**: Feature vector changes from 13 → 14 dimensions (drop 2, add 2 new, net +1).
- **Rebalance weights**: Reduce `peak_burst_velocity` from 5.0 to 3.5 (inter_op_time_cv shares temporal detection load). Reduce `size_std_dev` from 1.5 to 1.0 (size_change_kurtosis captures distribution shape more precisely). Adjust other weights as needed after benchmarking.
- **Re-calibrate threshold**: The 97th percentile threshold must be re-computed after feature changes and weight rebalancing. Target: maintain 0 false positives while closing adversarial gaps.

## Capabilities

### New Capabilities
- `size-change-kurtosis`: Kurtosis feature extraction for per-file size change ratios, capturing distribution shape (flat/peaked) to distinguish uniform encryption from randomized adversarial size changes.
- `inter-op-time-cv`: Coefficient of variation feature extraction for inter-operation time gaps, detecting automated regularity in timing patterns that distinguishes ransomware from human activity.

### Modified Capabilities
- `ransomware-feature-extraction`: Feature vector expands from 13 to 14 dimensions. F2 and F3 consolidate into `deletion_intensity` at index 2. Indices 3 shifts to `size_change_kurtosis`. New `inter_op_time_cv` at index 13. Updated extraction logic, FEATURE_NAMES array, and daysBetweenSnapshots normalization.
- `statistical-anomaly-detector`: DEFAULT_WEIGHTS array expands to 14 elements with new weights for consolidated deletion_intensity, size_change_kurtosis, and inter_op_time_cv. Rebalanced weights for peak_burst_velocity (5.0→3.5) and size_std_dev (1.5→1.0). N constant changes from 13 to 14.
- `burst-mod-purity`: Burst window computation unchanged, but burst_mod_purity feature index may shift due to F2/F3 consolidation.
- `file-type-concentration`: Feature index may shift due to F2/F3 consolidation. Computation logic unchanged.
- `adversarial-attack-variants`: Benchmark must include the new features in scoring and report whether slow_drip_encrypt and size_mimic_normal are now detected.
- `ransomware-test-generator`: No structural changes, but benchmark re-calibration needed for new feature vector dimensionality.

## Impact

- **Breaking change**: Feature vector dimensionality changes from 13 to 14. All downstream consumers (scorer, threshold, explainer, CLI weight parameter, optimizer) must be updated.
- **Weight parameter format**: CLI `--weights` parameter changes from 13 comma-separated values to 14.
- **Baseline recalibration**: All existing baselines become invalid. Users must regenerate baselines with the new feature extractor.
- **Code files affected**: `RansomwareFeatureExtractor.java`, `RansomwareFeatureVector.java`, `WeightedEuclideanScorer.java`, `BaselineStatistics.java`, `ZScoreExplainer.java`, `RansomwareDetectorCli.java`, `WeightOptimizerCli.java`, `IntermittentEncryptionBenchmark.java`, `AttackGenerator.java`.
- **Performance**: Two new features require additional per-file tracking during extraction (size change ratios and inter-operation time deltas). Memory impact is bounded — proportional to number of modified files, not total records. Streaming mode must be updated accordingly.
