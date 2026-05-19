# ADDED Requirements: ransomware-feature-extraction

### Requirement: 9 ransomware-specific features extraction
The system SHALL extract the following 9 features from each Snapdiff record batch for analysis:
- total_operations
- modification_ratio
- deletion_ratio
- bytes_removed
- user_spread
- extension_diversity
- suspicious_extension_ratio
- change_velocity
- avg_modified_size

#### Scenario: Basic batch with multiple records
- WHEN a batch contains 10 records with various sizes and operations
- THEN the system SHALL compute all 9 features for the batch without errors
