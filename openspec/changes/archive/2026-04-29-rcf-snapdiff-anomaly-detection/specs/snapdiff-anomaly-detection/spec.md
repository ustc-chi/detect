## ADDED Requirements

### Requirement: Snapdiff file parsing
The system SHALL parse snapdiff JSON files containing filesystem change records.

#### Scenario: Parse valid snapdiff file
- **WHEN** the system reads a snapdiff JSON file with `diffs` array and `summary` object
- **THEN** it extracts all diff entries including path, type, size, and change_time

#### Scenario: Handle missing optional fields
- **WHEN** a diff entry lacks `size` or `change_time` fields
- **THEN** the system uses default values (size=0, change_time=epoch) and continues processing

#### Scenario: Handle empty snapdiff
- **WHEN** a snapdiff file has an empty `diffs` array
- **THEN** the system produces a feature vector with all zero values

### Requirement: Feature vector extraction
The system SHALL convert parsed snapdiff data into a 17-dimensional numerical feature vector.

#### Scenario: Extract features from typical snapdiff
- **WHEN** a snapdiff contains added, modified, and deleted files with various sizes
- **THEN** the system computes: files_added, files_removed, files_modified, dirs_added, dirs_removed, symlinks_changed, bytes_added, bytes_removed, bytes_modified_delta, bytes_growth_rate, permissions_changed, ownership_changed, timestamps_changed, xattrs_changed, modification_ratio, churn_rate, metadata_change_ratio

#### Scenario: Normalize features
- **WHEN** feature values span multiple orders of magnitude (e.g., bytes in GB vs counts in single digits)
- **THEN** the system applies log-transformation to size features and computes ratios for relative metrics

### Requirement: Two-phase execution
The system SHALL operate in two distinct phases: Phase 1 (baseline building) and Phase 2 (anomaly detection).

#### Scenario: Phase 1 builds baseline
- **WHEN** the system runs with `--baseline-dir` containing historical snapdiff files
- **THEN** it processes all files in lexicographic order, inserting each into the RCF forest WITHOUT generating alerts, and computes per-feature statistics (mean, std)

#### Scenario: Phase 2 detects anomalies
- **WHEN** the system runs with `--input-dir` containing new snapdiff files AND a completed baseline
- **THEN** it scores each new snapdiff against the mature RCF forest and flags anomalies when scores exceed the threshold

#### Scenario: Detect obvious anomaly
- **WHEN** a snapdiff in Phase 2 shows 1000x normal file deletion rate
- **THEN** the RCF anomaly score exceeds the threshold and the system flags it as anomalous

### Requirement: CLI interface
The system SHALL expose all tunable parameters as command-line arguments.

#### Scenario: Run with custom hyperparameters
- **WHEN** the user provides `--num-trees 200 --tree-size 512 --shingle-size 8 --threshold 95`
- **THEN** the system uses these values instead of defaults

#### Scenario: Specify baseline and detection directories
- **WHEN** the user provides `--baseline-dir /path/to/baseline --input-dir /path/to/new --output-file /path/to/results.txt`
- **THEN** the system builds baseline from baseline-dir, then detects anomalies in input-dir, writing results to the output file

#### Scenario: Show help
- **WHEN** the user runs with `--help`
- **THEN** the system displays all available CLI options with descriptions and defaults

### Requirement: Anomaly explanation generation
The system SHALL produce human-readable explanations when anomalies are detected.

#### Scenario: Explain anomaly by feature deviation
- **WHEN** an anomaly is detected with score above threshold
- **THEN** the system identifies the top 3 deviating features by z-score and writes them to the result file

#### Scenario: Include context in explanation
- **WHEN** writing an anomaly explanation
- **THEN** the system includes: snapshot identifier, anomaly score, threshold, top deviating features with actual vs expected values
