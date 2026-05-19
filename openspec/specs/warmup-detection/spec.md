## ADDED Requirements

### Requirement: WarmupDetector heuristic classification
The `WarmupDetector` SHALL classify a `RansomwareFeatureVector` as anomalous when ≥2 of the following 5 heuristic rules match:
- `modification_ratio > 0.85`
- `peak_burst_velocity > 5000`
- `temporal_uniformity > 0.7`
- `burst_mod_purity > 0.90`
- `rename_correlation > 0.5`

The `classify(RansomwareFeatureVector)` method SHALL return `true` for anomalous and `false` for normal.

#### Scenario: Ransomware-like vector triggers anomaly
- **WHEN** a feature vector has `modification_ratio=0.95`, `peak_burst_velocity=12000`, and all other features within normal range
- **THEN** `classify()` SHALL return `true` (2 rules match)

#### Scenario: Normal vector does not trigger
- **WHEN** a feature vector has `modification_ratio=0.4`, `peak_burst_velocity=200`, `temporal_uniformity=0.3`, `burst_mod_purity=0.5`, `rename_correlation=0.0`
- **THEN** `classify()` SHALL return `false` (0 rules match)

#### Scenario: Exactly 1 rule match is not anomalous
- **WHEN** a feature vector has `modification_ratio=0.90` but all other features below thresholds
- **THEN** `classify()` SHALL return `false` (1 rule match, below 2-rule threshold)

#### Scenario: Edge case at threshold boundaries
- **WHEN** a feature vector has `modification_ratio=0.85` exactly
- **THEN** that rule SHALL NOT match (strictly greater than)

### Requirement: Warmup period determination
The system SHALL be in warmup mode when the baseline accumulator contains fewer than 5 vectors. Once 5 or more clean vectors are accumulated, warmup mode SHALL end and statistical detection SHALL be used exclusively.

#### Scenario: Cold start enters warmup mode
- **WHEN** RansomwareDetector is constructed with no baseline vectors
- **THEN** the detector SHALL be in warmup mode

#### Scenario: Warmup ends after 5 clean vectors
- **WHEN** 5 non-anomalous vectors have been added to the baseline accumulator
- **THEN** the detector SHALL exit warmup mode and use statistical detection

#### Scenario: Anomalous warmup rounds do not count toward baseline
- **WHEN** WarmupDetector.classify() returns true for a round during warmup
- **THEN** that vector SHALL NOT be added to the baseline accumulator

### Requirement: WarmupDetector logging
The WarmupDetector SHALL log at WARN level when warmup period exceeds 10 rounds without accumulating 5 clean baseline vectors.

#### Scenario: Extended warmup warning
- **WHEN** warmup mode persists for more than 10 rounds
- **THEN** a WARNING log SHALL be emitted indicating the system has not accumulated sufficient baseline data
