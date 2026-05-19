## Why

Filesystem snapshot diffs (snapdiffs) capture changes between two points in time — file creations, deletions, modifications, and metadata changes. Detecting anomalous patterns in these diffs is critical for identifying security incidents (ransomware, unauthorized access), operational issues (runaway logs, misconfigurations), and data integrity problems. Current approaches rely on static thresholds that produce false positives or miss subtle anomalies. We need an unsupervised, streaming-capable anomaly detection system that learns "normal" filesystem behavior and flags deviations.

## What Changes

- **New CLI application** (`rcf-anomaly-detector`): A Java/Maven program that operates in two phases using Amazon's Random Cut Forest (RCF) library
- **Feature extraction engine**: Converts snapdiff records into numerical feature vectors (counts, sizes, metadata changes, ratios)
- **Phase 1 — Baseline building**: Ingests historical snapdiffs to build a mature RCF forest and compute per-feature statistics (mean, std) without generating alerts
- **Phase 2 — Streaming detection**: Processes newly produced snapdiffs, scoring each against the established baseline while adapting the forest via insert/forget semantics
- **Explanation generator**: When an anomaly is detected, produces human-readable explanation showing which features deviated and by how much, using Phase 1 statistics
- **Comprehensive test suite**: Generates synthetic snapshots for both phases with tens of thousands of records covering normal operations and various anomaly scenarios (mass deletions, permission changes, unusual file types, size spikes)
- **Tunable hyperparameters**: CLI exposes num_trees, tree_size, shingle_size, threshold_percentile, and min_baseline_snapshots
- **Result output**: Writes anomaly reports to a designated result file with timestamps, scores, and explanations

## Capabilities

### New Capabilities
- `snapdiff-anomaly-detection`: End-to-end anomaly detection pipeline for filesystem snapshot diffs using Random Cut Forest
- `snapdiff-feature-extraction`: Parsing and feature engineering from snapdiff format to numerical vectors
- `rcf-streaming-detector`: Streaming anomaly scoring with explanation generation and result reporting
- `test-data-generator`: Synthetic snapdiff generation for comprehensive testing and hyperparameter tuning

### Modified Capabilities
- (none — this is a greenfield capability)

## Impact

- **New dependency**: `software.amazon.randomcutforest:randomcutforest-core` (Apache 2.0, free for commercial use)
- **Build system**: Maven-based Java project
- **CLI tool**: New executable with tunable parameters
- **Output**: Anomaly result files for downstream alerting/integration
- **Testing**: Large-scale synthetic data generation for validation
