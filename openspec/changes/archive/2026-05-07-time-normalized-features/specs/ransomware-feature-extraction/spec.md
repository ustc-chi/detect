## MODIFIED Requirements

### Requirement: Streaming and in-memory feature extraction
The RansomwareFeatureExtractor SHALL support two extraction modes, both accepting a configurable snapshot interval:

1. **Streaming mode**: `extractFromFile(Path filePath)` SHALL parse and process the snapdiff file in a single streaming pass using `StreamingSnapdiffParser`. During this pass, it SHALL accumulate features 0-6, 8-10, 12 as streaming counters/accumulators, and write `(epochSeconds, opType)` pairs to a temp file for burst computation (features 7 and 11). After the streaming pass, it SHALL sort the temp file and compute burst features via the sliding window algorithm.

2. **In-memory mode** (existing, deprecated): `extract(SnapdiffFile)` SHALL remain available for backward compatibility, marked `@Deprecated`. It SHALL produce numerically identical results to the streaming mode.

Both modes SHALL normalize absolute-valued features (0, 3, 4, 5) by dividing by `daysBetweenSnapshots` before storing in the feature vector. The `daysBetweenSnapshots` value SHALL be provided at construction time with a default of 2.0.

The streaming mode SHALL NOT load the full list of `SnapdiffRecord` objects into memory simultaneously.

#### Scenario: Streaming extraction produces identical results to in-memory extraction
- **WHEN** `extractFromFile(Path)` and `extract(SnapdiffFile)` are called on the same snapdiff data with the same `daysBetweenSnapshots`
- **THEN** the resulting `RansomwareFeatureVector` arrays SHALL be element-by-element equal (within floating-point tolerance of 1e-9)

#### Scenario: Streaming extraction handles empty file
- **WHEN** `extractFromFile()` is called on a snapdiff file with an empty diffs array
- **THEN** the returned `RansomwareFeatureVector` SHALL have all 13 features set to 0.0

#### Scenario: Streaming extraction handles large file without OOM
- **WHEN** `extractFromFile()` is called on a snapdiff file with 10 million records
- **THEN** heap usage during extraction SHALL remain bounded (not proportional to record count) and no `OutOfMemoryError` SHALL occur

#### Scenario: All 13 features extracted from non-empty snapdiff
- **WHEN** `extractFromFile()` is called with a snapdiff file containing diff records
- **THEN** the returned `RansomwareFeatureVector` SHALL have 13 feature values with indices 0-12 computed per their definitions, with features 0, 3, 4, 5 divided by `daysBetweenSnapshots`

#### Scenario: Default daysBetweenSnapshots is 2.0
- **WHEN** a `RansomwareFeatureExtractor` is constructed without specifying `daysBetweenSnapshots`
- **THEN** the extractor SHALL use 2.0 as the interval value

#### Scenario: Custom daysBetweenSnapshots normalizes features
- **WHEN** `extractFromFile()` is called with `daysBetweenSnapshots=7.0` on a snapdiff with 14000 total operations, 350 unique directories, and 28 unique extensions
- **THEN** `total_operations` SHALL be 2000.0 (14000/7), `directory_spread` SHALL be 50.0 (350/7), and `extension_diversity` SHALL be 4.0 (28/7)

#### Scenario: Ratio features are not affected by daysBetweenSnapshots
- **WHEN** `extractFromFile()` is called with `daysBetweenSnapshots=7.0` on a snapdiff where 80% of operations are modifications
- **THEN** `modification_ratio` SHALL be 0.8 regardless of the interval value

#### Scenario: daysBetweenSnapshots clamped to minimum 0.25
- **WHEN** a `RansomwareFeatureExtractor` is constructed with `daysBetweenSnapshots=0.01`
- **THEN** the extractor SHALL clamp the value to 0.25 and use that for normalization

#### Scenario: bytes_removed normalization divides before log
- **WHEN** `extractFromFile()` is called with `daysBetweenSnapshots=4.0` on a snapdiff with 1,000,000 total deleted bytes
- **THEN** `bytes_removed` SHALL be `log1p(250000.0)` (divide raw sum by days, then apply log1p), NOT `log1p(1000000.0) / 4.0`

### Requirement: Per-timestamp operation tracking via temp file
The extractor SHALL write timestamped operation records to a temporary file during the streaming pass (not accumulate them in a `List`). Each record SHALL include the operation type and timestamp epoch seconds. This file is used to compute both `peak_burst_velocity` and `burst_mod_purity` after the streaming pass completes.

#### Scenario: Multiple operations at the same second
- **WHEN** 100 modifications and 50 deletes all occur at the same epoch second
- **THEN** the extractor SHALL write 150 separate lines to the temp file, not collapse them into 1 unique timestamp
