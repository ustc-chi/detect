## ADDED Requirements

### Requirement: RCF model initialization
The system SHALL initialize a Random Cut Forest with configurable hyperparameters.

#### Scenario: Default initialization
- **WHEN** the system starts without custom parameters
- **THEN** it creates an RCF with: dimensions=17 (base features) × shingle_size, num_trees=100, sample_size=256, random_seed=42

#### Scenario: Custom hyperparameters
- **WHEN** the user specifies custom num_trees, tree_size, shingle_size
- **THEN** the system initializes RCF with those values

### Requirement: Phase 1 — Baseline building
The system SHALL ingest historical snapdiffs to build a mature RCF forest and compute baseline statistics.

#### Scenario: Insert baseline points
- **WHEN** a snapdiff from the baseline directory is processed in Phase 1
- **THEN** the system inserts its feature vector into the RCF forest without scoring or alerting

#### Scenario: Compute baseline statistics
- **WHEN** Phase 1 completes after processing all baseline snapdiffs
- **THEN** the system computes per-feature mean and standard deviation from all baseline feature vectors

#### Scenario: Calibrate threshold from baseline scores
- **WHEN** Phase 1 completes
- **THEN** the system computes anomaly scores for all baseline points and sets the threshold at the configured percentile (default: 99th)

### Requirement: Phase 2 — Streaming detection with adaptation
The system SHALL process new snapdiffs in Phase 2, scoring them against the mature baseline while allowing the forest to adapt.

#### Scenario: Score new point against baseline
- **WHEN** a new snapdiff is processed in Phase 2
- **THEN** the system computes its anomaly score using the mature RCF forest

#### Scenario: Adapt forest with insert/forget
- **WHEN** a new snapdiff is scored in Phase 2
- **THEN** the system inserts the new point into the forest AND removes the oldest point if the forest exceeds tree_size, maintaining a sliding window

#### Scenario: Shingled input
- **WHEN** shingle_size > 1
- **THEN** each point fed to RCF is a concatenation of the current and previous (shingle_size-1) feature vectors

#### Scenario: Normal point scoring
- **WHEN** a typical snapdiff is processed in Phase 2
- **THEN** the anomaly score is near the baseline (typically 0-10)

#### Scenario: Anomalous point scoring
- **WHEN** a snapdiff with extreme deviations is processed in Phase 2
- **THEN** the anomaly score spikes significantly above baseline (typically >20)

### Requirement: Threshold-based alerting
The system SHALL flag anomalies when scores exceed the threshold calibrated in Phase 1.

#### Scenario: Alert on threshold breach
- **WHEN** a Phase 2 point's anomaly score exceeds the Phase 1 threshold
- **THEN** the system writes an anomaly record to the result file

#### Scenario: No alert for normal points
- **WHEN** a Phase 2 point's anomaly score is below the threshold
- **THEN** the system does NOT write an anomaly alert (unless --include-normal is set)

### Requirement: Attribution and explanation
The system SHALL identify which features contributed most to an anomaly.

#### Scenario: Compute feature deviations
- **WHEN** an anomaly is detected in Phase 2
- **THEN** the system computes z-scores for each feature: z = (actual - mean) / std, using statistics computed during Phase 1 baseline building

#### Scenario: Rank feature contributions
- **WHEN** z-scores are computed
- **THEN** the system ranks features by absolute z-score and reports the top 3

#### Scenario: Include raw attribution
- **WHEN** writing anomaly explanation
- **THEN** the system includes RCF attribution vector if available from the library

### Requirement: Result file format
The system SHALL write anomaly results to a structured text file.

#### Scenario: Write anomaly record
- **WHEN** an anomaly is detected
- **THEN** the system appends to the result file: timestamp, snapshot filename, anomaly score, threshold, top deviating features with actual/expected values

#### Scenario: Include normal snapshots
- **WHEN** configured with `--include-normal`
- **THEN** the system also writes records for non-anomalous snapshots with their scores

#### Scenario: Result file header
- **WHEN** the result file is created
- **THEN** it includes a header line with column names
