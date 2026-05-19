## MODIFIED Requirements

### Requirement: Test round schedule with 14-feature extraction
The RansomwareTestGenerator SHALL produce test rounds using the 14-feature RansomwareFeatureExtractor. The number of rounds and attack schedule remain unchanged (40 rounds: 28 normal + 12 attacks).

The `IntermittentEncryptionBenchmark` SHALL load pre-generated snapdiff test data from `benchmark-data/` instead of generating it at runtime. All Phase 3 and Phase 4 detection logic, output format, and test case structure SHALL remain unchanged.

The `FilesystemState.snapshot()` SHALL return a shallow copy of the file map (copying references, not FileInfo objects). `FileInfo` fields SHALL be immutable (final). Mutation operations SHALL create new `FileInfo` objects instead of modifying existing ones.

The `evolveNormalRound()` method SHALL include rename operations (5–15% of total), after-hours timestamps (15–25% of operations), and activity volatility (three tiers: quiet/normal/busy days). This produces more realistic baseline statistics for the `rename_correlation`, `wall_clock_anomaly`, `peak_burst_velocity`, and `temporal_uniformity` features.

#### Scenario: 40 rounds generated with 14-feature extraction
- **WHEN** RansomwareTestGenerator.generate() is called with seed=42
- **THEN** 40 JSON files SHALL be produced, with feature vectors containing 14 elements per round

#### Scenario: Normal rounds score below threshold with new weights
- **WHEN** the 28 normal rounds are scored with the new 14-feature scorer and rebalanced weights
- **THEN** all 28 SHALL score below the anomaly threshold (0 false positives)

#### Scenario: Benchmark loads pre-generated data and produces identical detection
- **WHEN** IntermittentEncryptionBenchmark is run with pre-generated benchmark-data/
- **THEN** detection results SHALL show ≥99% attack detection rate (67/68 or better) and 0% vanilla normal false positive rate

#### Scenario: Shallow snapshot preserves data integrity
- **WHEN** snapshot() is called, state is mutated, then restore() is called with the snapshot
- **THEN** the restored state SHALL be identical to the pre-snapshot state

#### Scenario: Benchmark completes in under 5 minutes
- **WHEN** IntermittentEncryptionBenchmark is run with pre-generated data
- **THEN** total runtime SHALL be under 5 minutes (300 seconds)
