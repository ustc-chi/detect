## MODIFIED Requirements

### Requirement: Detector supports file-based detection
The `RansomwareDetector` SHALL support a `detectFromFile(Path filePath)` method that performs streaming feature extraction and signature scanning in a single pass over the file, then computes the statistical score. The existing `detect(RansomwareFeatureVector, List<SnapdiffRecord>)` method SHALL be preserved for backward compatibility.

The detector SHALL propagate its configured `daysBetweenSnapshots` value to the internal `RansomwareFeatureExtractor` so that all features are normalized consistently.

#### Scenario: File-based detection result matches in-memory detection
- **WHEN** `detectFromFile(Path)` and `detect(RansomwareFeatureVector, List<SnapdiffRecord>)` are called on the same snapdiff data with the same `daysBetweenSnapshots`
- **THEN** the `DetectionResult` objects SHALL have equal scores, thresholds, anomaly flags, and signature matches

#### Scenario: Detector propagates daysBetweenSnapshots to extractor
- **WHEN** a `RansomwareDetector` is constructed with a `RansomwareFeatureExtractor` configured with `daysBetweenSnapshots=5.0`
- **THEN** all subsequent calls to `detectFromFile()` SHALL produce feature vectors where features 0, 3, 4, 5 are divided by 5.0

## ADDED Requirements

### Requirement: CLI accepts days-between-snapshots parameter
The `RansomwareDetectorCli` SHALL accept a `--days-between` command-line option with a default value of 2.0. This value SHALL be passed to the `RansomwareFeatureExtractor` used for both baseline calibration and input detection.

#### Scenario: CLI default days-between is 2.0
- **WHEN** `RansomwareDetectorCli` is invoked without `--days-between`
- **THEN** the extractor SHALL use 2.0 as `daysBetweenSnapshots`

#### Scenario: CLI custom days-between
- **WHEN** `RansomwareDetectorCli` is invoked with `--days-between 7.0`
- **THEN** both baseline and input feature extraction SHALL use 7.0 as `daysBetweenSnapshots`

#### Scenario: WeightOptimizerCli accepts days-between
- **WHEN** `WeightOptimizerCli` is invoked with `--days-between 3.0`
- **THEN** feature extraction during weight optimization SHALL use 3.0 as `daysBetweenSnapshots`
