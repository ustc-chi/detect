## MODIFIED Requirements

### Requirement: File type concentration feature extraction
The system SHALL compute a `file_type_concentration` feature (index 11 in the new 14-feature vector) representing the maximum fraction of modifications targeting any single file extension.

The feature SHALL be computed as: `max(per_extension_modification_count) / total_modified_count`, where per_extension_modification_count is the count of modified files grouped by their file extension.

The value SHALL be 0.0 if there are no modified files.

#### Scenario: Targeted attack on database files
- **WHEN** 2000 modifications occur, of which 1500 target .db files and 500 target other extensions
- **THEN** `file_type_concentration` SHALL be 0.75

#### Scenario: No modified files in round
- **WHEN** a snapdiff round contains only adds and deletes, no modifications
- **THEN** `file_type_concentration` SHALL be 0.0

### Requirement: File type concentration feature weight
The default weight for `file_type_concentration` SHALL be 2.0 in the WeightedEuclideanScorer DEFAULT_WEIGHTS array.

#### Scenario: Weight configuration includes file_type_concentration
- **WHEN** WeightedEuclideanScorer is constructed with default weights
- **THEN** the weight array SHALL contain 14 elements with index 11 equal to 2.0
