## ADDED Requirements

### Requirement: Inter-operation time CV feature extraction
The system SHALL compute an `inter_op_time_cv` feature (index 13 in the new 14-feature vector) representing the coefficient of variation of inter-operation time deltas.

The feature SHALL be computed as:
1. Sort all records with valid `change_time` (not null, not `Instant.EPOCH`) by timestamp ascending
2. Compute consecutive time deltas: `deltas[i] = sorted[i+1].epochSeconds - sorted[i].epochSeconds`
3. Compute `μ = mean(deltas)` and `σ = std_dev(deltas)`
4. If `μ < 0.001`, return 0.0 (no meaningful time spread)
5. Return `σ / μ`

The value SHALL be 0.0 if there are fewer than 2 records with valid timestamps.

The feature SHALL NOT be divided by `daysBetweenSnapshots` — it is a distribution-shape metric independent of time normalization.

#### Scenario: Automated regular timing (slow_drip_encrypt)
- **WHEN** operations occur at exactly 5-minute intervals (regular automated cadence)
- **THEN** `inter_op_time_cv` SHALL be approximately 0.0 (zero variation in deltas)

#### Scenario: Human irregular timing (normal activity)
- **WHEN** operations are clustered around working hours with natural gaps (meetings, breaks, lunch)
- **THEN** `inter_op_time_cv` SHALL be >= 0.5 (high variation in deltas)

#### Scenario: Burst encryption with some spread
- **WHEN** 3000 operations occur in a 90-second burst plus 7000 normal operations spread over 8 hours
- **THEN** `inter_op_time_cv` SHALL be high (mix of near-zero deltas in burst and large deltas between normal ops)

#### Scenario: Fewer than 2 valid timestamps
- **WHEN** a snapdiff round contains only 1 record with a valid `change_time`
- **THEN** `inter_op_time_cv` SHALL be 0.0

### Requirement: Inter-op time CV computed from sorted burst data
The `inter_op_time_cv` SHALL be computed from the same sorted timestamp data used for burst features (peak_burst_velocity and burst_mod_purity). No additional temp file or sorting pass SHALL be required. After the existing sort of the burst temp file, the inter-operation deltas SHALL be computed in a single pass over the sorted timestamps.

#### Scenario: Streaming mode computes inter_op_time_cv from burst temp file
- **WHEN** streaming extraction completes the sort of the burst temp file
- **THEN** `inter_op_time_cv` SHALL be computed from the same sorted data before temp file deletion

#### Scenario: In-memory mode computes inter_op_time_cv from sorted timestamps
- **WHEN** in-memory extraction computes burst features from sorted timestamps
- **THEN** `inter_op_time_cv` SHALL be computed from the same sorted data in the same pass

### Requirement: Inter-op time CV feature weight
The default weight for `inter_op_time_cv` SHALL be 2.5 in the WeightedEuclideanScorer DEFAULT_WEIGHTS array.

#### Scenario: Weight configuration includes inter_op_time_cv
- **WHEN** WeightedEuclideanScorer is constructed with default weights
- **THEN** the weight array SHALL contain 14 elements with index 13 equal to 2.5
