## MODIFIED Requirements

### Requirement: Feature vector dimensionality
The feature vector SHALL contain exactly 12 features. The mapping from old 14-feature to new 12-feature indices is:

| New Idx | Feature | Source | Status |
|---|---|---|---|
| 0 | modification_ratio | Unchanged (old idx 1) | KEPT |
| 1 | total_operations_normalized | Modified (old idx 0) | FIXED |
| 2 | peak_burst_velocity | Unchanged (old idx 6) | KEPT |
| 3 | burst_mod_purity | Unchanged (old idx 10) | KEPT |
| 4 | high_value_ext_ratio | Modified (old idx 9) | FIXED |
| 5 | inter_op_time_cv_burst | Modified (old idx 13) | FIXED |
| 6 | high_value_file_coverage | Modified (old idx 14) | FIXED |
| 7 | directory_coverage_depth | New | ADDED |
| 8 | temporal_uniformity | New | ADDED |
| 9 | rename_correlation | New | ADDED |
| 10 | wall_clock_anomaly | New | ADDED |
| 11 | per_type_entropy | New | ADDED |

#### Scenario: Feature vector construction with 12 values
- **WHEN** a RansomwareFeatureVector is constructed with a double array
- **THEN** the array SHALL have length exactly 12; arrays of any other length SHALL throw IllegalArgumentException

#### Scenario: Feature names array
- **WHEN** RansomwareFeatureVector.FEATURE_NAMES is accessed
- **THEN** it SHALL return a 12-element array with names matching the table above

### Requirement: Total operations normalization (index 1)
The `total_operations` feature (index 1) SHALL be normalized by dividing by `daysBetweenSnapshots` and then by the baseline median of normalized total operations. During learning period (no baseline), the raw `total_operations / daysBetweenSnapshots` value SHALL be used.

#### Scenario: Operations normalized by baseline
- **WHEN** a round has 5000 operations with daysBetweenSnapshots=1.0 and baseline median is 800 ops/day
- **THEN** the normalized value SHALL be `5000 / 800` = 6.25

#### Scenario: Learning period uses raw normalized value
- **WHEN** a round has 5000 operations with daysBetweenSnapshots=1.0 and no baseline exists
- **THEN** the value SHALL be `5000 / 1.0` = 5000.0

### Requirement: Streaming and in-memory feature extraction
The RansomwareFeatureExtractor SHALL support two extraction modes for the new 12-feature vector:

1. **Streaming mode**: `extractFromFile(Path filePath)` SHALL compute all 12 features in a single streaming pass plus post-pass burst computation. New features (`directory_coverage_depth`, `temporal_uniformity`, `rename_correlation`, `wall_clock_anomaly`, `per_type_entropy`) SHALL be computed during the streaming pass or from the burst temp file data.

2. **In-memory mode**: `extract(SnapdiffFile)` SHALL produce numerically identical results to the streaming mode.

#### Scenario: Streaming extraction produces identical results to in-memory extraction
- **WHEN** `extractFromFile(Path)` and `extract(SnapdiffFile)` are called on the same snapdiff data with the same `daysBetweenSnapshots`
- **THEN** the resulting `RansomwareFeatureVector` arrays SHALL be element-by-element equal (within floating-point tolerance of 1e-9)

## REMOVED Requirements

### Requirement: Deletion intensity feature extraction
**Reason**: The consolidated `deletion_intensity` feature (old idx 2) is removed along with the other broken/weak features. Its signal (mass deletion detection) is subsumed by `per_type_entropy` (operation type distribution) and `total_operations_normalized`.
**Migration**: No migration needed — the feature is removed from the vector.

### Requirement: Directory spread feature extraction
**Reason**: Replaced by `directory_coverage_depth` which adds depth uniformity and is more resistant to padding dilution.
**Migration**: Use `directory_coverage_depth` (new idx 7) instead.

### Requirement: Extension diversity feature extraction
**Reason**: Produced zero signal for 10/12 ORIG attacks (no extension changes). Redundant with `file_type_concentration`. Replaced by `per_type_entropy` which has signal for all attacks.
**Migration**: Use `per_type_entropy` (new idx 11) instead.

### Requirement: Suspicious extension ratio feature extraction
**Reason**: Vestigial in Phase 2 statistical scoring — suspicious extensions would have been caught by Phase 1 signature detection. The feature produces z≈0 in Phase 2 where statistical features apply.
**Migration**: Remains in Phase 1 signature detector only. Not part of statistical scoring.

### Requirement: Avg modified size feature extraction
**Reason**: Fundamentally broken — computes mean of `log1p(NEW_SIZE)` which measures file-size composition, not size change. Without `old_size` in `SnapdiffRecord`, the feature cannot measure encryption-related size deltas.
**Migration**: No replacement from metadata alone. If `old_size` becomes available in the snapdiff schema, a `size_delta_cv` feature would be the proper replacement.

### Requirement: Size std dev feature extraction
**Reason**: Same fundamental issue as `avg_modified_size` — variance of post-encryption file sizes is not a detection signal.
**Migration**: Same as avg_modified_size.

### Requirement: Size change kurtosis feature extraction
**Reason**: Same fundamental issue — kurtosis of post-encryption file sizes measures composition, not change.
**Migration**: Same as avg_modified_size.

### Requirement: File type concentration feature extraction
**Reason**: Redundant with `extension_diversity` (both measure extension distribution using different statistics). Both produce zero signal for attacks that don't change extensions. Replaced by `per_type_entropy`.
**Migration**: Use `per_type_entropy` (new idx 11) instead.
