## ADDED Requirements

### Requirement: Generator supports configurable snapshot interval
The `RansomwareTestGenerator` SHALL accept a `daysBetweenSnapshots` parameter (default 2.0) that is passed to the `RansomwareFeatureExtractor` when computing feature vectors for generated snapdiffs. This enables generating baselines and test data at different interval configurations.

#### Scenario: Generator uses default interval
- **WHEN** `RansomwareTestGenerator` is constructed without specifying an interval
- **THEN** feature extraction during generation SHALL use 2.0 days

#### Scenario: Generator uses custom interval
- **WHEN** `RansomwareTestGenerator` is constructed with `daysBetweenSnapshots=5.0`
- **THEN** feature extraction during generation SHALL use 5.0 days

### Requirement: Interval-sensitivity validation test
The test suite SHALL include a test that verifies feature invariance across different snapshot intervals. This test SHALL generate identical operations spread over different time spans, extract features with matching `daysBetweenSnapshots`, and confirm that normalized features (0, 3, 4, 5) produce approximately equal values.

#### Scenario: Same operations, different intervals, normalized features match
- **WHEN** 5000 operations are generated once and features are extracted with `daysBetweenSnapshots=1.0` and `daysBetweenSnapshots=7.0`
- **THEN** features 0, 3, 4, 5 SHALL differ by less than 1e-6 between the two extractions (after normalization), and ratio features (1, 2, 6, 10, 11, 12) SHALL be exactly equal
