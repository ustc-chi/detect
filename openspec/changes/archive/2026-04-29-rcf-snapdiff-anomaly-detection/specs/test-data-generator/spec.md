## ADDED Requirements

### Requirement: Synthetic snapdiff generation
The system SHALL generate realistic synthetic snapdiff files for testing.

#### Scenario: Generate normal snapshots
- **WHEN** the test generator runs
- **THEN** it produces 50+ snapdiff files simulating normal filesystem activity: occasional file additions, modifications, and deletions with realistic sizes

#### Scenario: Generate anomaly snapshots
- **WHEN** the test generator creates anomaly scenarios
- **THEN** it injects: mass deletion events (1000+ files), unusual file type patterns (all .exe in a Linux system), permission sweep changes, size spikes (single file >100GB), rapid churn (all files replaced), metadata-only storms

#### Scenario: Mixed scenario generation
- **WHEN** generating a test dataset
- **THEN** approximately 10% of snapshots contain anomalies while 90% are normal

### Requirement: Test coverage
The system SHALL include comprehensive tests covering all components.

#### Scenario: Unit test feature extraction
- **WHEN** running unit tests
- **THEN** all 17 features are tested individually with known inputs and expected outputs

#### Scenario: Unit test RCF integration
- **WHEN** running unit tests
- **THEN** the RCF wrapper is tested with synthetic data, verifying scores increase for anomalous inputs

#### Scenario: Integration test full pipeline
- **WHEN** running integration tests
- **THEN** the system processes a directory of generated snapdiffs and produces expected anomaly reports

#### Scenario: CLI argument testing
- **WHEN** running tests with various CLI arguments
- **THEN** the system correctly parses and applies all tunable parameters

### Requirement: Hyperparameter tuning tests
The system SHALL include tests that validate hyperparameter choices.

#### Scenario: Test different tree counts
- **WHEN** running with num_trees in {50, 100, 200}
- **THEN** the system achieves >90% detection rate on synthetic anomalies with <5% false positive rate

#### Scenario: Test different shingle sizes
- **WHEN** running with shingle_size in {1, 2, 4, 8}
- **THEN** the system identifies the optimal balance between detection latency and false positive rate

#### Scenario: Test threshold percentiles
- **WHEN** running with threshold in {95, 99, 99.5}
- **THEN** the system shows the trade-off between recall and precision

### Requirement: Edge case testing
The system SHALL handle edge cases gracefully.

#### Scenario: Empty input directory
- **WHEN** the input directory contains no snapdiff files
- **THEN** the system exits with an informative error message

#### Scenario: Single snapshot
- **WHEN** only one snapdiff file exists
- **THEN** the system completes warm-up but reports insufficient data for detection

#### Scenario: Corrupted JSON
- **WHEN** a snapdiff file contains invalid JSON
- **THEN** the system logs the error, skips the file, and continues processing

#### Scenario: All identical snapshots
- **WHEN** all snapdiffs are identical (no changes)
- **THEN** the system handles this without division by zero and correctly identifies any deviation as anomalous

#### Scenario: Extremely large snapdiff
- **WHEN** a snapdiff contains 100,000+ records
- **THEN** the system processes it without memory errors
