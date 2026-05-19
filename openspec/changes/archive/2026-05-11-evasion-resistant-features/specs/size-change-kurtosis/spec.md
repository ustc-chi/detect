## ADDED Requirements

### Requirement: Size change kurtosis feature extraction
The system SHALL compute a `size_change_kurtosis` feature (index 12 in the new 14-feature vector) representing the excess kurtosis of the `log1p(size)` distribution of modified files with `size > 0`.

The feature SHALL be computed as:
1. Collect `values = [log1p(size_i)]` for each modified record where `size > 0`
2. Compute mean `x̄ = Σ(values) / n`
3. Compute 4th central moment `m4 = Σ((x_i - x̄)⁴) / n`
4. Compute variance `σ² = Σ((x_i - x̄)²) / n`
5. If `σ² < 1e-12`, return 0.0 (no variation — neutral)
6. Return `m4 / (σ² × σ²) - 3.0` (excess kurtosis)

The value SHALL be 0.0 if there are fewer than 4 modified files with `size > 0`.

The feature SHALL NOT be divided by `daysBetweenSnapshots` — it is a distribution-shape metric independent of time normalization.

#### Scenario: Encryption produces low kurtosis (platykurtic distribution)
- **WHEN** 3000 modified files all receive a uniform +3% size increase (e.g., encryption)
- **THEN** `size_change_kurtosis` SHALL be negative (platykurtic, flatter than normal distribution)

#### Scenario: Normal activity produces higher kurtosis (leptokurtic)
- **WHEN** 3000 modified files include a mix of small text edits, large database updates, and occasional huge file changes
- **THEN** `size_change_kurtosis` SHALL be positive or near-zero (leptokurtic or mesokurtic)

#### Scenario: Adversarial randomization produces near-zero kurtosis
- **WHEN** modified files have random size changes uniformly distributed between -10% and +10%
- **THEN** `size_change_kurtosis` SHALL be approximately -1.2 (uniform distribution excess kurtosis)

#### Scenario: Too few modified files
- **WHEN** a snapdiff round contains only 2 modified files with `size > 0`
- **THEN** `size_change_kurtosis` SHALL be 0.0

#### Scenario: All modified files have identical size
- **WHEN** all modified files with `size > 0` have the same `log1p(size)` value (zero variance)
- **THEN** `size_change_kurtosis` SHALL be 0.0

### Requirement: Size change kurtosis feature weight
The default weight for `size_change_kurtosis` SHALL be 2.0 in the WeightedEuclideanScorer DEFAULT_WEIGHTS array.

#### Scenario: Weight configuration includes size_change_kurtosis
- **WHEN** WeightedEuclideanScorer is constructed with default weights
- **THEN** the weight array SHALL contain 14 elements with index 12 equal to 2.0
