## 1. Setup & Cleanup

- [x] 1.1 Remove RCF dependency from pom.xml (software.amazon.randomcutforest:randomcutforest-core)
- [x] 1.2 Remove legacy RCF-based classes: StreamingDetector, BaselineBuilder, FeatureStatistics, ExplanationGenerator, ResultWriter, AnomalyResult, AnomalyExplanation, FeatureDeviation
- [x] 1.3 Remove legacy 17-feature extractor: FeatureExtractor, FeatureVector
- [x] 1.4 Remove legacy anomaly types: AnomalyType (MASS_DELETION, UNUSUAL_FILE_TYPES, PERMISSION_SWEEP, SIZE_SPIKE, RAPID_CHURN, METADATA_STORM, SUDDEN_GROWTH)
- [x] 1.5 Remove legacy generator classes: SnapdiffGenerator, SnapdiffEntry (generator), SnapdiffFile (generator), SnapdiffSummary, FileTemplate
- [x] 1.6 Remove legacy CLI: AnomalyDetectorCli and its inner SnapdiffRecord class
- [x] 1.7 Keep reusable models: com.anomalydetection.model.SnapdiffRecord, SnapdiffFile, com.anomalydetection.parser.SnapdiffParser
- [x] 1.8 Remove all legacy tests that test deleted classes
- [x] 1.9 Verify build compiles cleanly after removal

## 2. Ransomware Feature Extraction

- [x] 2.1 Create RansomwareFeatureVector class with 9 features: total_operations, modification_ratio, deletion_ratio, bytes_removed, user_spread, extension_diversity, suspicious_extension_ratio, change_velocity, avg_modified_size
- [x] 2.2 Create SuspiciousExtensions config loader — loads the curated ransomware extension list, configurable via external file path
- [x] 2.3 Create RansomwareFeatureExtractor — single-pass extraction from SnapdiffFile to RansomwareFeatureVector
- [x] 2.4 Implement feature calculations:
  - [x] 2.4.1 total_operations: count all records
  - [x] 2.4.2 modification_ratio: modified / total, handle div-by-zero
  - [x] 2.4.3 deletion_ratio: deleted / total, handle div-by-zero
  - [x] 2.4.4 bytes_removed: log1p(sum of sizes where type == "deleted")
  - [x] 2.4.5 user_spread: extract userN from paths matching /vol/share/userN/, count unique
  - [x] 2.4.6 extension_diversity: extract extensions from paths, count unique
  - [x] 2.4.7 suspicious_extension_ratio: count matches against SuspiciousExtensions / total files with extensions
  - [x] 2.4.8 change_velocity: total_ops / timespan_hours, floor at 1 minute for timespan < 1 min
  - [x] 2.4.9 avg_modified_size: log1p(avg size of records where type == "modified"), 0.0 if none
- [x] 2.5 Handle all edge cases: empty snapdiff (zero vector), single record, no modified files, paths without extensions, changeTime = EPOCH

## 3. Statistical Anomaly Detector

- [x] 3.1 Create BaselineStatistics class: mean[9], std[9] computed from a list of RansomwareFeatureVectors
- [x] 3.2 Create WeightedEuclideanScorer: z-score normalized weighted Euclidean distance with balanced default weights for ransomware detection
- [x] 3.3 Default weights: modification_ratio=1.0, suspicious_extension_ratio=1.0, deletion_ratio=0.8, change_velocity=0.8, user_spread=0.6, bytes_removed=0.5, total_operations=0.4, extension_diversity=0.3, avg_modified_size=0.2
- [x] 3.4 Create ZScoreExplainer: compute per-feature z-scores and identify which features deviate most
- [x] 3.5 Create AnomalyThreshold: percentile-based threshold calibration from baseline distance scores
- [x] 3.6 Create RansomwareDetector: orchestrates BaselineStatistics, WeightedEuclideanScorer, ZScoreExplainer, and AnomalyThreshold
- [x] 3.7 Implement self-updating baseline: add new round, drop oldest if window > cap, recompute statistics

## 4. Test Data Generator (New)

- [x] 4.1 Create FilesystemState class: tracks simulated filesystem across user directories for cross-round consistency
- [x] 4.2 Create realistic file path generator: produce paths with 20+ extensions across categories, distributed across /vol/share/user1–user10/
- [x] 4.3 Create realistic size generator: power-law distribution (mostly small files, occasional large, rare very large)
- [x] 4.4 Create realistic timestamp generator: human-paced changes spread across 8–12 hour workday
- [x] 4.5 Implement normal round generation: evolve FilesystemState, produce 10K–50K changes with ~5% change ratio, mix of adds/modifies/deletes
- [x] 4.6 Implement cross-round consistency: files added in round N can be modified/deleted in later rounds
- [x] 4.7 Implement MASS_ENCRYPTION attack generator (LockBit-style): 8K–20K modifications, suspicious extensions, ransom notes, all users, 1–2 hour timespan
- [x] 4.8 Implement DESTRUCTIVE_WIPER attack generator (Ryuk-style): 5K–15K deletions, high bytes_removed, all users, 30–60 min timespan
- [x] 4.9 Create RansomwareTestGenerator CLI: generate full test dataset (7 normal + 2 attack rounds) and save to output directory
- [x] 4.10 Save all output as pretty-printed JSON: test-baseline/round_001.json through round_007.json, test-attack/round_008_lockbit_style.json, test-attack/round_009_ryuk_style.json

## 5. Test Suite

- [x] 5.1 Unit tests for RansomwareFeatureExtractor: test each of the 9 features in isolation with known inputs
- [x] 5.2 Unit tests for edge cases: empty snapdiff, single record, no modified files, no extensions, EPOCH timestamps
- [x] 5.3 Unit tests for BaselineStatistics: verify mean, std computation with known vectors
- [x] 5.4 Unit tests for WeightedEuclideanScorer: verify distance computation with known baseline and test vector
- [x] 5.5 Unit tests for ZScoreExplainer: verify z-score computation and feature ranking
- [x] 5.6 Unit tests for self-updating baseline: add new vector, verify window management and recomputation
- [x] 5.7 Integration test: baseline build and anomaly detection with synthetic data
- [x] 5.8 Unit tests for RansomwareDetector: verify detection and self-update flow

## 6. CLI & Integration

- [x] 6.1 Create new RansomwareDetectorCli: replaces AnomalyDetectorCli with statistical approach
- [x] 6.2 CLI options: --baseline-dir, --input-dir, --output-file, --window-size (default 20), --threshold-percentile (default 99.0), --suspicious-extensions-file, --weights (comma-separated 9 values), --include-normal
- [x] 6.3 Wire feature extraction → baseline → detection → explanation → output
- [x] 6.4 Update pom.xml mainClass and shade plugin configuration

## 7. Cleanup & Verification

- [x] 7.1 Remove all unused imports and dead code
- [x] 7.2 Compilation clean (mvn clean compile passes)
- [x] 7.3 Run mvn clean test — all 21 tests pass
- [x] 7.4 Run mvn package — fat JAR builds successfully
- [x] 7.5 Generate test data files for user review in test-output/
