## ADDED Requirements

### Requirement: Three-layer detection strategy
The `WarmupDetector` SHALL implement a three-layer detection pipeline: Layer 1 (deterministic rules), Layer 2 (strong heuristic rules), Layer 3 (dynamic statistical detection).

#### Scenario: Layer 1 triggers on suspicious extension
- **WHEN** `suspicious_extension_ratio > 0` (index 5)
- **THEN** the result SHALL be `ANOMALY`
- **AND** the vector SHALL NOT be added to baseline
- **AND** the triggered rule SHALL be `"SUSPICIOUS_EXTENSION"`

#### Scenario: Layer 2 heuristic rules
- **WHEN** `modification_ratio > 0.95 AND total_operations > 50`
- **THEN** Layer 2 SHALL trigger with rule `"EXTREME_MODIFICATION_RATIO"` and confidence 0.90

- **WHEN** `burst_mod_purity > 0.95 AND peak_burst_velocity > 50`
- **THEN** Layer 2 SHALL trigger with rule `"HIGH_BURST_PURITY"` and confidence 0.80

- **WHEN** `file_type_concentration > 0.90 AND total_operations > 100`
- **THEN** Layer 2 SHALL trigger with rule `"HIGH_FILE_TYPE_CONCENTRATION"` and confidence 0.80

- **WHEN** `inter_op_time_cv < 0.05 AND total_operations > 50`
- **THEN** Layer 2 SHALL trigger with rule `"ROBOTIC_TIMING_PATTERN"` and confidence 0.85

- **WHEN** `high_value_ext_ratio > 0.8 AND total_operations > 100`
- **THEN** Layer 2 SHALL trigger with rule `"HIGH_VALUE_TARGETING"` and confidence 0.75

- **WHEN** `deletion_intensity > 5.0`
- **THEN** Layer 2 SHALL trigger with rule `"HIGH_DELETION_INTENSITY"` and confidence 0.70

#### Scenario: Layer 3 dynamic statistical detection
- **WHEN** history normals count >= 2 AND both Layer 1 and Layer 2 do NOT trigger
- **THEN** Layer 3 SHALL compute a weighted Euclidean score using warmup-specific weights
- **AND** compare against a dynamic threshold based on history size
- **AND** if score > threshold, mark as `SUSPICIOUS` (not ANOMALY)

#### Scenario: Normal vector added to baseline
- **WHEN** no layer triggers an anomaly or suspicious result
- **THEN** the result SHALL be `NORMAL`
- **AND** the vector SHALL be eligible for baseline addition

### Requirement: HeuristicRule interface
The system SHALL define a `HeuristicRule` interface for pluggable heuristic rules. Each rule SHALL independently evaluate a `FeatureVector14` and return a result with triggered status, rule name, and confidence.

#### Scenario: Rule evaluates independently
- **WHEN** a `HeuristicRule` evaluates a `FeatureVector14`
- **THEN** it SHALL return a `RuleResult` containing: `triggered` (boolean), `ruleName` (String), `confidence` (double 0.0-1.0)

#### Scenario: New rule can be added without modifying WarmupDetector
- **WHEN** a new `HeuristicRule` implementation is created
- **THEN** it SHALL be usable by `WarmupDetector` through the interface without modifying the detector class

### Requirement: Warmup-specific weights
The `WarmupDetector` SHALL use a dedicated weight array for Layer 3 statistical detection. Weights that require stable baselines SHALL be set to 0.0 (disabled).

#### Scenario: Warmup weights are applied
- **WHEN** Layer 3 computes scores
- **THEN** weights SHALL be: F0=1.0, F1=5.0, F2=0.5, F3=0.5, F4=0.5, F5=15.0, F6=2.0, F7=0.0, F8=0.0, F9=1.5, F10=5.0, F11=3.0, F12=0.0, F13=3.0

### Requirement: Dynamic threshold calculation
Layer 3 SHALL use a dynamic threshold that varies based on the number of accumulated normal history samples.

#### Scenario: Threshold multiplier decreases with more samples
- **WHEN** history normals count is 2-3
- **THEN** the multiplier SHALL be 10.0
- **WHEN** history normals count is 4-5
- **THEN** the multiplier SHALL be 5.0
- **WHEN** history normals count is 6-7
- **THEN** the multiplier SHALL be 3.0
- **WHEN** history normals count is 8+
- **THEN** the multiplier SHALL be 2.0

#### Scenario: Threshold formula
- **WHEN** computing the dynamic threshold
- **THEN** threshold = max historical score × multiplier
- **AND** max historical score is computed by scoring all history normal vectors against a baseline built from the same history

### Requirement: WarmupResult classification
The `WarmupDetector` SHALL classify each detection into one of four statuses.

#### Scenario: WarmupResult statuses
- **WHEN** Layer 1 or Layer 2 triggers
- **THEN** status SHALL be `ANOMALY` with `addToBaseline = false`
- **WHEN** Layer 3 triggers
- **THEN** status SHALL be `SUSPICIOUS` with `addToBaseline = false`
- **WHEN** no layer triggers
- **THEN** status SHALL be `NORMAL` with `addToBaseline = true`
