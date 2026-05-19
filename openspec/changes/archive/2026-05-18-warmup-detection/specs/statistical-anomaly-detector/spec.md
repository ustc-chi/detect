## ADDED Requirements

### Requirement: Null-safe construction for warmup mode
The `RansomwareDetector` SHALL accept null `BaselineStatistics` and `AnomalyThreshold` parameters, entering warmup mode where it delegates to `WarmupDetector` for classification.

#### Scenario: Construction with null baseline
- **WHEN** RansomwareDetector is constructed with null BaselineStatistics and null AnomalyThreshold
- **THEN** the detector SHALL enter warmup mode and not throw an exception

#### Scenario: Construction with existing baseline skips warmup
- **WHEN** RansomwareDetector is constructed with a BaselineStatistics containing ≥5 vectors and a valid AnomalyThreshold
- **THEN** the detector SHALL use statistical detection immediately (no warmup)

### Requirement: processRound delegates to WarmupDetector during warmup
During warmup mode, `RansomwareDetector.processRound()` SHALL use `WarmupDetector.classify()` to produce a `DetectionResult` with `isAnomaly` set to the classification result. The score SHALL be set to the count of matching heuristic rules, and the threshold SHALL be set to 2.

#### Scenario: Warmup round classified as anomalous
- **WHEN** processRound is called during warmup and WarmupDetector.classify() returns true
- **THEN** DetectionResult.isAnomaly SHALL be true, score SHALL equal the number of matching rules (≥2), threshold SHALL be 2

#### Scenario: Warmup round classified as normal
- **WHEN** processRound is called during warmup and WarmupDetector.classify() returns false
- **THEN** DetectionResult.isAnomaly SHALL be false, and the vector SHALL be added to the baseline accumulator

## MODIFIED Requirements

### Requirement: CLI accepts 12 comma-separated weights
The `--baseline-dir` parameter of `RansomwareDetectorCli` SHALL be optional. When omitted, the CLI SHALL construct RansomwareDetector with null baseline (warmup mode). The `--weights` parameter SHALL accept exactly 12 comma-separated decimal values. Providing fewer or more SHALL produce an error message and exit with code 1.

The CSV output SHALL include two additional columns after existing columns: `warmup` (boolean, "true"/"false" indicating whether the round was processed during warmup mode) and `baseline_count` (integer, number of vectors in the baseline accumulator after processing this round).

#### Scenario: CLI with 12 custom weights
- **WHEN** `RansomwareDetectorCli` is invoked with `--weights 2.0,2.5,5.0,3.0,1.5,2.0,2.0,2.5,2.5,3.0,1.5,2.0`
- **THEN** the scorer SHALL use those 12 values as feature weights

#### Scenario: CLI with wrong number of weights
- **WHEN** `RansomwareDetectorCli` is invoked with `--weights 1.0,2.0,3.0` (only 3 values)
- **THEN** the CLI SHALL print an error and exit with code 1

#### Scenario: CLI without baseline-dir starts in warmup mode
- **WHEN** `RansomwareDetectorCli` is invoked without `--baseline-dir`
- **THEN** the detector SHALL start in warmup mode and use WarmupDetector for early rounds

#### Scenario: CSV output includes warmup columns
- **WHEN** a round is processed during warmup mode
- **THEN** the CSV row SHALL have `warmup=true` and `baseline_count` reflecting current accumulator size
