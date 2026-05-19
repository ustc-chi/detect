## ADDED Requirements

### Requirement: Per-type entropy feature extraction
The system SHALL compute a `per_type_entropy` feature representing the Shannon entropy of the operation type distribution `{added, modified, deleted}` within the round.

The feature SHALL be computed as:
1. Count operations per type: `n_add = count(type="added")`, `n_mod = count(type="modified")`, `n_del = count(type="deleted")`
2. Compute total: `N = n_add + n_mod + n_del`
3. If `N == 0`, return 0.0
4. Compute Shannon entropy: `H = -Σ(p_i × log2(p_i))` where `p_i = count_i / N` for each type with `count_i > 0`
5. Return `H` (range: 0.0 to ~1.585 for 3 equally-distributed types)

The feature SHALL NOT be divided by `daysBetweenSnapshots`.

#### Scenario: Ransomware all-modification pattern
- **WHEN** a round contains 3000 modifications, 10 adds, and 0 deletes
- **THEN** `per_type_entropy` SHALL be approximately `-((3010/3010) × log2(3010/3010)) - ((10/3010) × log2(10/3010))` ≈ 0.035 (near-zero — almost all same type)

#### Scenario: Normal mixed activity
- **WHEN** a round contains 120 modifications, 80 adds, and 50 deletes
- **THEN** `per_type_entropy` SHALL be approximately 1.48 (high entropy — mixed types)

#### Scenario: Cl0p companion pattern (50% adds)
- **WHEN** a round contains 2000 modifications and 2000 adds (companion files)
- **THEN** `per_type_entropy` SHALL be approximately 1.0 (two dominant types)

#### Scenario: Empty round
- **WHEN** a snapdiff round contains 0 operations
- **THEN** `per_type_entropy` SHALL be 0.0

### Requirement: Per-type entropy feature weight
The default weight for `per_type_entropy` SHALL be 2.0 in the WeightedEuclideanScorer DEFAULT_WEIGHTS array.

#### Scenario: Weight configuration includes per_type_entropy
- **WHEN** WeightedEuclideanScorer is constructed with default weights
- **THEN** the weight array SHALL contain 12 elements with the weight for per_type_entropy equal to 2.0
