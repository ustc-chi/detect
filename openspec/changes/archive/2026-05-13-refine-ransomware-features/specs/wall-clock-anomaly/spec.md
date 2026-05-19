## ADDED Requirements

### Requirement: Wall clock anomaly feature extraction
The system SHALL compute a `wall_clock_anomaly` feature representing the z-score of the current round's operation count relative to historical activity at the same hour of day.

The feature SHALL be computed as:
1. Extract the hour-of-day (0–23) from the earliest `changeTime` in the round
2. Retrieve the per-hour baseline statistics: `median_h` and `mad_h` for hour `h`
3. If no baseline exists for hour `h` (learning period), return 0.0
4. Compute `z = (total_operations - median_h) / (mad_h × 1.4826)`
5. Clamp `z` to [-10.0, 10.0]
6. Return `z`

The feature SHALL NOT be divided by `daysBetweenSnapshots` — the z-score is already normalized.

#### Scenario: After-hours burst attack at 3 AM
- **WHEN** 3000 operations occur at 3 AM and the historical baseline for hour 3 shows median=50 ops, MAD=20
- **THEN** `wall_clock_anomaly` SHALL be approximately `(3000 - 50) / (20 × 1.4826)` ≈ 99.3, clamped to 10.0

#### Scenario: Normal business hours activity
- **WHEN** 500 operations occur at 2 PM and the historical baseline for hour 14 shows median=450, MAD=150
- **THEN** `wall_clock_anomaly` SHALL be approximately `(500 - 450) / (150 × 1.4826)` ≈ 0.22

#### Scenario: No baseline for this hour (learning period)
- **WHEN** the self-learning window has not yet accumulated data for hour 5
- **THEN** `wall_clock_anomaly` SHALL be 0.0

#### Scenario: All operations have EPOCH timestamps
- **WHEN** no records have valid `changeTime` (all are `Instant.EPOCH`)
- **THEN** `wall_clock_anomaly` SHALL be 0.0

### Requirement: Per-hour baseline accumulation
The `BaselineStatistics` SHALL maintain per-hour operation count statistics. For each round processed during the self-learning window, the total operation count SHALL be added to the bin corresponding to the round's dominant hour.

#### Scenario: Baseline accumulates per-hour data
- **WHEN** 10 rounds are processed during self-learning, with rounds at hours [9, 10, 14, 9, 10, 14, 15, 9, 10, 14]
- **THEN** the baseline SHALL have median and MAD computed for hours 9, 10, 14, and 15

### Requirement: Wall clock anomaly feature weight
The default weight for `wall_clock_anomaly` SHALL be 1.5 in the WeightedEuclideanScorer DEFAULT_WEIGHTS array.

#### Scenario: Weight configuration includes wall_clock_anomaly
- **WHEN** WeightedEuclideanScorer is constructed with default weights
- **THEN** the weight array SHALL contain 12 elements with the weight for wall_clock_anomaly equal to 1.5
