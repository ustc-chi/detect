## MODIFIED Requirements

### Requirement: Feature vector dimensionality
The feature vector SHALL contain exactly 14 features. The mapping from old 13-feature to new 14-feature indices is:

| New Idx | Feature | Source |
|---|---|---|
| 0 | total_operations | Unchanged (idx 0) |
| 1 | modification_ratio | Unchanged (idx 1) |
| 2 | deletion_intensity | Consolidated from old idx 2 (deletion_ratio) + idx 3 (bytes_removed) |
| 3 | directory_spread | Shifted from old idx 4 |
| 4 | extension_diversity | Shifted from old idx 5 |
| 5 | suspicious_extension_ratio | Shifted from old idx 6 |
| 6 | peak_burst_velocity | Shifted from old idx 7 |
| 7 | avg_modified_size | Shifted from old idx 8 |
| 8 | size_std_dev | Shifted from old idx 9 |
| 9 | high_value_ext_ratio | Shifted from old idx 10 |
| 10 | burst_mod_purity | Shifted from old idx 11 |
| 11 | file_type_concentration | Shifted from old idx 12 |
| 12 | size_change_kurtosis | New |
| 13 | inter_op_time_cv | New |

#### Scenario: Feature vector construction with 14 values
- **WHEN** a RansomwareFeatureVector is constructed with a double array
- **THEN** the array SHALL have length exactly 14; arrays of any other length SHALL throw IllegalArgumentException

#### Scenario: Feature names array
- **WHEN** RansomwareFeatureVector.FEATURE_NAMES is accessed
- **THEN** it SHALL return a 14-element array with names matching the table above

### Requirement: Deletion intensity feature extraction
The system SHALL compute a consolidated `deletion_intensity` feature (index 2) that replaces the previous `deletion_ratio` (old index 2) and `bytes_removed` (old index 3).

The feature SHALL be computed as:
`deletion_intensity = log1p(Σ size(type="deleted") / daysBetweenSnapshots) × (count(type="deleted") / total_operations)`

The value SHALL be 0.0 if `total_operations == 0` or there are no deleted files.

#### Scenario: Mass destructive attack
- **WHEN** 5000 files totaling 50GB are deleted in a snapdiff round with daysBetweenSnapshots=2.0
- **THEN** `deletion_intensity` SHALL be `log1p(25,000,000,000) × (5000 / total_ops)` — a large positive value

#### Scenario: No deletions
- **WHEN** a snapdiff round contains only modifications and additions
- **THEN** `deletion_intensity` SHALL be 0.0

#### Scenario: Normal file cleanup
- **WHEN** 50 small files totaling 5MB are deleted out of 10,000 total operations with daysBetweenSnapshots=2.0
- **THEN** `deletion_intensity` SHALL be `log1p(2,500,000) × 0.005` — a small positive value

### Requirement: Streaming and in-memory feature extraction
The RansomwareFeatureExtractor SHALL support two extraction modes for the new 14-feature vector:

1. **Streaming mode**: `extractFromFile(Path filePath)` SHALL compute all 14 features in a single streaming pass plus post-pass burst computation. The `size_change_kurtosis` SHALL be computed from the same `log1p(size)` values collected for `avg_modified_size` and `size_std_dev`. The `inter_op_time_cv` SHALL be computed from the same sorted burst temp file data used for `peak_burst_velocity` and `burst_mod_purity`.

2. **In-memory mode** (deprecated): `extract(SnapdiffFile)` SHALL produce numerically identical results to the streaming mode.

Both modes SHALL normalize features 0 and 2 by dividing by `daysBetweenSnapshots` before storing in the feature vector.

#### Scenario: Streaming extraction produces identical results to in-memory extraction
- **WHEN** `extractFromFile(Path)` and `extract(SnapdiffFile)` are called on the same snapdiff data with the same `daysBetweenSnapshots`
- **THEN** the resulting `RansomwareFeatureVector` arrays SHALL be element-by-element equal (within floating-point tolerance of 1e-9)
