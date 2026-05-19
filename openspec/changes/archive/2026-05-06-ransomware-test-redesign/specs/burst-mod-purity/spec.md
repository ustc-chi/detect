## ADDED Requirements

### Requirement: Burst modification purity feature extraction
The system SHALL compute a `burst_mod_purity` feature (index 11) representing the fraction of modifications among all operations within the peak 5-minute burst window.

The feature SHALL be computed as: `modifications_in_burst / total_ops_in_burst`, where the burst window is the same 5-minute sliding window identified by `peak_burst_velocity`.

The value SHALL be 0.0 if there are fewer than 2 operations or no identifiable burst window.

#### Scenario: Pure modification burst during encryption
- **WHEN** 3000 file modifications occur within a 90-second window mixed with 7000 normal operations spread over 6 hours
- **THEN** `burst_mod_purity` SHALL be >= 0.85 (the burst window captures predominantly modifications)

#### Scenario: Mixed activity burst during normal operations
- **WHEN** a legitimate burst of 200 operations occurs with 100 modifications, 60 adds, and 40 deletes
- **THEN** `burst_mod_purity` SHALL be approximately 0.50

#### Scenario: Empty or single-operation round
- **WHEN** a snapdiff round contains 0 or 1 operations
- **THEN** `burst_mod_purity` SHALL be 0.0

### Requirement: Timestamp-to-operation tracking for burst analysis
The feature extractor SHALL track operations with their timestamps and types as structured records (not just a Set of unique timestamps) to enable both `peak_burst_velocity` and `burst_mod_purity` computation from a single pass.

Each tracked record SHALL include the operation type (added/modified/deleted) and the timestamp.

#### Scenario: Multiple operations at the same timestamp
- **WHEN** 50 modifications and 10 deletes all occur at the same timestamp
- **THEN** all 60 operations SHALL be individually tracked for burst window analysis (not collapsed into a single timestamp entry)

### Requirement: Burst mod purity feature weight
The default weight for `burst_mod_purity` SHALL be 3.0 in the WeightedEuclideanScorer DEFAULT_WEIGHTS array. This is moderate — it supports burst detection alongside `peak_burst_velocity` but does not dominate. Detection should work via burst OR non-burst signals.

#### Scenario: Weight configuration includes burst_mod_purity
- **WHEN** WeightedEuclideanScorer is constructed with default weights
- **THEN** the weight array SHALL contain 13 elements with index 11 equal to 3.0
