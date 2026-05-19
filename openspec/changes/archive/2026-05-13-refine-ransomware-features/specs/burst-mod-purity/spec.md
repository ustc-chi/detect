## MODIFIED Requirements

### Requirement: Burst modification purity feature extraction
The system SHALL compute a `burst_mod_purity` feature representing the fraction of modifications among all operations within the peak 5-minute burst window.

The feature SHALL be computed as: `modifications_in_burst / total_ops_in_burst`, where the burst window is the same 5-minute sliding window identified by `peak_burst_velocity`.

The value SHALL be 0.0 if there are fewer than 2 operations or no identifiable burst window.

This requirement is UNCHANGED from the existing specification. The feature logic remains identical; only the feature index and weight have changed.

#### Scenario: Pure modification burst during encryption
- **WHEN** 3000 file modifications occur within a 90-second window mixed with 7000 normal operations spread over 6 hours
- **THEN** `burst_mod_purity` SHALL be >= 0.85

#### Scenario: Empty or single-operation round
- **WHEN** a snapdiff round contains 0 or 1 operations
- **THEN** `burst_mod_purity` SHALL be 0.0

### Requirement: Burst mod purity feature weight
The default weight for `burst_mod_purity` SHALL be 3.0 in the WeightedEuclideanScorer DEFAULT_WEIGHTS array (increased from 2.5).

#### Scenario: Weight configuration includes burst_mod_purity
- **WHEN** WeightedEuclideanScorer is constructed with default weights
- **THEN** the weight array SHALL contain 12 elements with the weight for burst_mod_purity equal to 3.0
