## 1. Project Setup

- [ ] 1.1 Create Maven project structure with `pom.xml`
- [ ] 1.2 Add dependency: `software.amazon.randomcutforest:randomcutforest-core:4.4.0`
- [ ] 1.3 Add dependencies: Jackson (JSON parsing), JUnit 5, AssertJ
- [ ] 1.4 Create package structure: `com.anomalydetection.{model,parser,features,detector,generator,cli}`
- [ ] 1.5 Verify Maven build compiles successfully

## 2. Snapdiff Data Model

- [ ] 2.1 Create `SnapdiffRecord` class (path, type, size, changeTime)
- [ ] 2.2 Create `SnapdiffFile` class (diffs list, summary object)
- [ ] 2.3 Create `FeatureVector` class with 17 normalized fields
- [ ] 2.4 Add JSON deserialization using Jackson with optional field handling
- [ ] 2.5 Write unit tests for parsing valid and minimal snapdiff JSON

## 3. Feature Extraction Engine

- [ ] 3.1 Implement count features: files_added, files_removed, files_modified, dirs_added, dirs_removed, symlinks_changed
- [ ] 3.2 Implement size features: bytes_added, bytes_removed, bytes_modified_delta, bytes_growth_rate
- [ ] 3.3 Implement metadata features: permissions_changed, ownership_changed, timestamps_changed, xattrs_changed
- [ ] 3.4 Implement ratio features: modification_ratio, churn_rate, metadata_change_ratio
- [ ] 3.5 Implement normalization: log1p for sizes, clip ratios to [0,1], handle zero division
- [ ] 3.6 Write unit tests for each feature with known inputs/outputs
- [ ] 3.7 Write integration test for full feature extraction pipeline

## 4. RCF Two-Phase Detector

### 4.1 Phase 1 — Baseline Building

- [ ] 4.1.1 Create `BaselineBuilder` class that processes historical snapdiffs
- [ ] 4.1.2 Implement forest initialization with configurable dimensions, numTrees, sampleSize, shingleSize
- [ ] 4.1.3 Implement shingle buffer (rolling window of last N feature vectors)
- [ ] 4.1.4 Insert all baseline snapdiffs into forest WITHOUT scoring or alerting
- [ ] 4.1.5 Compute and store per-feature mean and standard deviation from baseline vectors
- [ ] 4.1.6 Compute anomaly scores for all baseline points
- [ ] 4.1.7 Calibrate threshold at configurable percentile (default: 99th) of baseline scores
- [ ] 4.1.8 Enforce minimum baseline snapshots (default: 100)

### 4.2 Phase 2 — Streaming Detection

- [ ] 4.2.1 Create `StreamingDetector` class that processes new snapdiffs
- [ ] 4.2.2 Accept mature forest and baseline stats from Phase 1
- [ ] 4.2.3 Implement point insertion with FIFO forget when exceeding tree size (adaptation)
- [ ] 4.2.4 Implement anomaly score computation for each new incoming point
- [ ] 4.2.5 Implement anomaly flagging when score exceeds Phase 1 threshold
- [ ] 4.2.6 Write unit tests for Phase 1 baseline building
- [ ] 4.2.7 Write unit tests for Phase 2 streaming detection
- [ ] 4.2.8 Write integration test for full two-phase pipeline

## 5. Explanation Generator

- [ ] 5.1 Accept baseline mean/std statistics from Phase 1
- [ ] 5.2 Implement z-score computation: z = (actual - mean) / std
- [ ] 5.3 Implement top-N feature ranking by absolute z-score
- [ ] 5.4 Create `AnomalyExplanation` class with snapshot, score, threshold, deviations
- [ ] 5.5 Write unit tests for explanation generation with known deviations

## 6. Result File Writer

- [ ] 6.1 Implement `ResultWriter` class for structured text output
- [ ] 6.2 Write header line with column names
- [ ] 6.3 Write anomaly records: timestamp, filename, score, threshold, top features
- [ ] 6.4 Support `--include-normal` flag to write non-anomalous snapshots too
- [ ] 6.5 Write unit tests for result file formatting

## 7. CLI Interface

- [ ] 7.1 Add CLI argument parsing with picocli or Commons CLI
- [ ] 7.2 Implement `--baseline-dir` (required for Phase 1): directory containing historical snapdiffs
- [ ] 7.3 Implement `--input-dir` (required for Phase 2): directory containing new snapdiffs to detect
- [ ] 7.4 Implement `--output-file` (required): path for anomaly results
- [ ] 7.5 Implement `--num-trees` (default: 100)
- [ ] 7.6 Implement `--tree-size` (default: 256)
- [ ] 7.7 Implement `--shingle-size` (default: 4)
- [ ] 7.8 Implement `--threshold-percentile` (default: 99.0)
- [ ] 7.9 Implement `--min-baseline-snapshots` (default: 100)
- [ ] 7.10 Implement `--include-normal` (flag, default: false)
- [ ] 7.11 Implement `--help` showing all options with descriptions
- [ ] 7.12 Write unit tests for CLI argument parsing

## 8. Synthetic Test Data Generator

- [ ] 8.1 Create `SnapdiffGenerator` class for synthetic data generation
- [ ] 8.2 Generate 50+ normal snapshots with realistic file operations for Phase 1 baseline
- [ ] 8.3 Generate anomaly scenarios for Phase 2: mass deletion, unusual file types, permission sweeps, size spikes, rapid churn, metadata storms
- [ ] 8.4 Ensure ~10% anomaly rate among Phase 2 snapshots
- [ ] 8.5 Generate files with various suffixes: .txt, .log, .conf, .exe, .sh, .py, .java, etc.
- [ ] 8.6 Generate tens of thousands of records per snapshot
- [ ] 8.7 Write generator to designated output directory
- [ ] 8.8 Write unit tests verifying generator produces valid JSON

## 9. Integration Tests

- [ ] 9.1 Create end-to-end test: generate baseline + detection snapshots, run two-phase pipeline, verify anomalies detected
- [ ] 9.2 Test edge case: empty baseline directory
- [ ] 9.3 Test edge case: baseline directory with fewer than min-baseline-snapshots
- [ ] 9.4 Test edge case: empty input directory (Phase 2)
- [ ] 9.5 Test edge case: corrupted JSON file in Phase 2
- [ ] 9.6 Test edge case: all identical baseline snapshots
- [ ] 9.7 Test edge case: extremely large snapdiff (100k+ records)
- [ ] 9.8 Verify result file contains expected anomaly explanations

## 10. Hyperparameter Tuning

- [ ] 10.1 Run test suite with num_trees ∈ {50, 100, 200}
- [ ] 10.2 Run test suite with shingle_size ∈ {1, 2, 4, 8}
- [ ] 10.3 Run test suite with threshold_percentile ∈ {95, 99, 99.5}
- [ ] 10.4 Measure detection rate (recall) and false positive rate for each config
- [ ] 10.5 Select optimal hyperparameters based on test results
- [ ] 10.6 Document chosen hyperparameters and rationale

## 11. Documentation

- [ ] 11.1 Write README.md with build instructions (`mvn package`)
- [ ] 11.2 Document two-phase execution with examples
- [ ] 11.3 Document CLI usage with all options
- [ ] 11.4 Document snapdiff JSON format expected
- [ ] 11.5 Document feature vector composition
- [ ] 11.6 Document hyperparameter recommendations
- [ ] 11.7 Document output file format

## 12. Build Verification

- [ ] 12.1 Verify `mvn clean compile` succeeds
- [ ] 12.2 Verify `mvn test` passes all test cases
- [ ] 12.3 Verify `mvn package` produces executable JAR
- [ ] 12.4 Run manual end-to-end test with generated data
- [ ] 12.5 Review test coverage report (target: >80%)
