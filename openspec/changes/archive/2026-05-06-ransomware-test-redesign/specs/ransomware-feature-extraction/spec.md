## MODIFIED Requirements

### Requirement: Feature vector dimensionality
The feature vector SHALL contain exactly 13 features (expanded from 11). Features at indices 0-10 retain their existing definitions. Index 11 is `burst_mod_purity`. Index 12 is `file_type_concentration`.

#### Scenario: Feature vector construction with 13 values
- **WHEN** a RansomwareFeatureVector is constructed with a double array
- **THEN** the array SHALL have length exactly 13; arrays of any other length SHALL throw IllegalArgumentException

#### Scenario: Feature names array
- **WHEN** RansomwareFeatureVector.FEATURE_NAMES is accessed
- **THEN** it SHALL return a 13-element array with "burst_mod_purity" at index 11 and "file_type_concentration" at index 12

### Requirement: Single-pass feature extraction
The RansomwareFeatureExtractor SHALL extract all 13 features from a SnapdiffFile in a single iteration over the diff records. The extractor SHALL track per-timestamp operation types (as a List of typed timestamp records, not a Set of unique Instants) to enable both `peak_burst_velocity` and `burst_mod_purity` computation.

#### Scenario: All 13 features extracted from non-empty snapdiff
- **WHEN** extract() is called with a SnapdiffFile containing diff records
- **THEN** the returned RansomwareFeatureVector SHALL have 13 feature values, with indices 0-10 computed as before, index 11 = burst_mod_purity, index 12 = file_type_concentration

#### Scenario: Empty snapdiff input
- **WHEN** extract() is called with null or empty SnapdiffFile
- **THEN** the returned RansomwareFeatureVector SHALL have all 13 features set to 0.0

## ADDED Requirements

### Requirement: Per-timestamp operation tracking during extraction
The extractor SHALL maintain a List of timestamped operation records (not a Set<Instant>) during the single-pass iteration. Each record SHALL include the operation type and timestamp. This list is used to compute both peak_burst_velocity and burst_mod_purity.

#### Scenario: Multiple operations at the same second
- **WHEN** 100 modifications and 50 deletes all occur at the same epoch second
- **THEN** the extractor SHALL track 150 separate timestamped operation records, not collapse them into 1 unique timestamp
