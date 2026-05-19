## MODIFIED Requirements

### Requirement: Single-pass feature extraction
The RansomwareFeatureExtractor SHALL support two extraction modes:

1. **Streaming mode** (new): `extractFromFile(Path filePath)` SHALL parse and process the snapdiff file in a single streaming pass using `StreamingSnapdiffParser`. During this pass, it SHALL accumulate features 0-6, 8-10, 12 as streaming counters/accumulators, and write `(epochSeconds, opType)` pairs to a temp file for burst computation (features 7 and 11). After the streaming pass, it SHALL sort the temp file and compute burst features via the sliding window algorithm.

2. **In-memory mode** (existing, deprecated): `extract(SnapdiffFile)` SHALL remain available for backward compatibility, marked `@Deprecated`. It SHALL produce numerically identical results to the streaming mode.

The streaming mode SHALL NOT load the full list of `SnapdiffRecord` objects into memory simultaneously.

#### Scenario: Streaming extraction produces identical results to in-memory extraction
- **WHEN** `extractFromFile(Path)` and `extract(SnapdiffFile)` are called on the same snapdiff data
- **THEN** the resulting `RansomwareFeatureVector` arrays SHALL be element-by-element equal (within floating-point tolerance of 1e-9)

#### Scenario: Streaming extraction handles empty file
- **WHEN** `extractFromFile()` is called on a snapdiff file with an empty diffs array
- **THEN** the returned `RansomwareFeatureVector` SHALL have all 13 features set to 0.0

#### Scenario: Streaming extraction handles large file without OOM
- **WHEN** `extractFromFile()` is called on a snapdiff file with 10 million records
- **THEN** heap usage during extraction SHALL remain bounded (not proportional to record count) and no `OutOfMemoryError` SHALL occur

#### Scenario: All 13 features extracted from non-empty snapdiff
- **WHEN** `extractFromFile()` is called with a snapdiff file containing diff records
- **THEN** the returned `RansomwareFeatureVector` SHALL have 13 feature values with indices 0-12 computed per their definitions

### Requirement: Per-timestamp operation tracking during extraction
The extractor SHALL write timestamped operation records to a temporary file during the streaming pass (not accumulate them in a `List`). Each record SHALL include the operation type and timestamp epoch seconds. This file is used to compute both `peak_burst_velocity` and `burst_mod_purity` after the streaming pass completes.

#### Scenario: Multiple operations at the same second
- **WHEN** 100 modifications and 50 deletes all occur at the same epoch second
- **THEN** the extractor SHALL write 150 separate lines to the temp file, not collapse them into 1 unique timestamp
