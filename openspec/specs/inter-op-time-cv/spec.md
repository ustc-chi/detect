## MODIFIED Requirements

### Requirement: Inter-operation time CV feature extraction (burst-window scoped)
The system SHALL compute an `inter_op_time_cv_burst` feature representing the coefficient of variation of inter-operation time deltas WITHIN the peak burst window only (the same 300-second window identified by `peak_burst_velocity`).

The feature SHALL be computed as:
1. Identify the burst window start and end timestamps (from `peak_burst_velocity` computation)
2. Select all records within that burst window
3. Sort selected records by timestamp ascending
4. Compute consecutive time deltas: `deltas[i] = sorted[i+1].epochSeconds - sorted[i].epochSeconds`
5. Compute `μ = mean(deltas)` and `σ = std_dev(deltas)`
6. If `μ < 0.001`, return 0.0
7. Return `σ / μ`

The value SHALL be 0.0 if there are fewer than 2 records within the burst window.

The feature SHALL NOT be divided by `daysBetweenSnapshots`.

#### Scenario: Automated regular timing within burst
- **WHEN** 100 operations occur at regular 3-second intervals within a 300-second burst window
- **THEN** `inter_op_time_cv_burst` SHALL be approximately 0.0 (zero variation in deltas)

#### Scenario: Human irregular timing within burst
- **WHEN** operations within the burst window are clustered with natural gaps
- **THEN** `inter_op_time_cv_burst` SHALL be >= 0.5 (high variation in deltas)

#### Scenario: No identifiable burst window
- **WHEN** a snapdiff round contains fewer than 2 operations with valid timestamps in any burst window
- **THEN** `inter_op_time_cv_burst` SHALL be 0.0

### Requirement: Inter-op time CV computed from burst window data
The `inter_op_time_cv_burst` SHALL be computed from the same sorted timestamp data within the burst window used for `peak_burst_velocity` and `burst_mod_purity`. No additional sorting pass SHALL be required.

#### Scenario: Streaming mode computes inter_op_time_cv_burst from burst temp file
- **WHEN** streaming extraction identifies the burst window
- **THEN** `inter_op_time_cv_burst` SHALL be computed from timestamps within that window

### Requirement: Inter-op time CV burst feature weight
The default weight for `inter_op_time_cv_burst` SHALL be 2.0 in the WeightedEuclideanScorer DEFAULT_WEIGHTS array.

#### Scenario: Weight configuration includes inter_op_time_cv_burst
- **WHEN** WeightedEuclideanScorer is constructed with default weights
- **THEN** the weight array SHALL contain 12 elements with the weight for inter_op_time_cv_burst equal to 2.0
