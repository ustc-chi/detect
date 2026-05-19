## MODIFIED Requirements

### Requirement: Weighted Euclidean scorer with 12 features
The WeightedEuclideanScorer SHALL operate on 12-feature vectors. The N constant SHALL be 12. The DEFAULT_WEIGHTS array SHALL contain 12 elements with rebalanced weights.

Default weights SHALL be:
```
2.0,  // 0: modification_ratio (kept)
2.5,  // 1: total_operations_normalized (fixed — normalized by baseline)
5.0,  // 2: peak_burst_velocity (reduced from 10.0 — scheduled batch jobs also produce bursts)
3.0,  // 3: burst_mod_purity (kept, weight increased from 2.5)
1.5,  // 4: high_value_ext_ratio (fixed — EMA smoothed)
2.0,  // 5: inter_op_time_cv_burst (fixed — burst-window scoped)
2.0,  // 6: high_value_file_coverage (fixed — proper floor/cap)
2.5,  // 7: directory_coverage_depth (NEW)
2.5,  // 8: temporal_uniformity (NEW)
3.0,  // 9: rename_correlation (NEW)
1.5,  // 10: wall_clock_anomaly (NEW)
2.0   // 11: per_type_entropy (NEW)
```

#### Scenario: Scorer with default weights handles 12 features
- **WHEN** WeightedEuclideanScorer is constructed with BaselineStatistics (default constructor)
- **THEN** the scorer SHALL use 12 default weights, N SHALL be 12, and scoring a 12-feature vector SHALL produce a valid distance

#### Scenario: Scorer rejects wrong-dimension weights
- **WHEN** WeightedEuclideanScorer is constructed with a weights array of length != 12
- **THEN** the scorer SHALL fall back to DEFAULT_WEIGHTS

### Requirement: Baseline statistics with 12 features
The BaselineStatistics SHALL compute median and MAD for 12 features. The feature dimensionality is determined by the input vectors.

#### Scenario: Baseline from 12-feature vectors
- **WHEN** BaselineStatistics is constructed with a list of 12-feature RansomwareFeatureVectors
- **THEN** getMedian() and getMad() SHALL each return 12-element arrays

### Requirement: CLI accepts 12 comma-separated weights
The `--weights` parameter of `RansomwareDetectorCli` SHALL accept exactly 12 comma-separated decimal values. Providing fewer or more SHALL produce an error message and exit with code 1.

#### Scenario: CLI with 12 custom weights
- **WHEN** `RansomwareDetectorCli` is invoked with `--weights 2.0,2.5,5.0,3.0,1.5,2.0,2.0,2.5,2.5,3.0,1.5,2.0`
- **THEN** the scorer SHALL use those 12 values as feature weights

#### Scenario: CLI with wrong number of weights
- **WHEN** `RansomwareDetectorCli` is invoked with `--weights 1.0,2.0,3.0` (only 3 values)
- **THEN** the CLI SHALL print an error and exit with code 1

### Requirement: Baseline dimension mismatch detection
The BaselineStatistics SHALL reject feature vectors of incorrect dimensionality with a clear error message.

#### Scenario: Loading 14-feature baseline into 12-feature system
- **WHEN** BaselineStatistics encounters a feature vector with 14 dimensions when expecting 12
- **THEN** it SHALL throw an IllegalArgumentException with a message containing "expected 12 dimensions, got 14"
