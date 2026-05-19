## MODIFIED Requirements

### Requirement: Weighted Euclidean scorer with 14 features
The WeightedEuclideanScorer SHALL operate on 14-feature vectors. The N constant SHALL be 14. The DEFAULT_WEIGHTS array SHALL contain 14 elements with rebalanced weights.

Default weights SHALL be:
```
2.0,  // 0: total_operations (unchanged)
3.0,  // 1: modification_ratio (unchanged)
0.5,  // 2: deletion_intensity (consolidated from old F2+F3)
1.5,  // 3: directory_spread (shifted, unchanged)
0.8,  // 4: extension_diversity (shifted, unchanged)
10.0, // 5: suspicious_extension_ratio (shifted, unchanged)
3.5,  // 6: peak_burst_velocity (shifted, REDUCED from 5.0)
1.5,  // 7: avg_modified_size (shifted, unchanged)
1.0,  // 8: size_std_dev (shifted, REDUCED from 1.5)
2.5,  // 9: high_value_ext_ratio (shifted, unchanged)
3.0,  // 10: burst_mod_purity (shifted, unchanged)
2.0,  // 11: file_type_concentration (shifted, unchanged)
2.0,  // 12: size_change_kurtosis (NEW)
2.5   // 13: inter_op_time_cv (NEW)
```

#### Scenario: Scorer with default weights handles 14 features
- **WHEN** WeightedEuclideanScorer is constructed with BaselineStatistics (default constructor)
- **THEN** the scorer SHALL use 14 default weights, N SHALL be 14, and scoring a 14-feature vector SHALL produce a valid distance

#### Scenario: Scorer rejects wrong-dimension weights
- **WHEN** WeightedEuclideanScorer is constructed with a weights array of length != 14
- **THEN** the scorer SHALL fall back to DEFAULT_WEIGHTS

### Requirement: Baseline statistics with 14 features
The BaselineStatistics SHALL compute median and MAD for 14 features. The feature dimensionality is determined by the input vectors.

#### Scenario: Baseline from 14-feature vectors
- **WHEN** BaselineStatistics is constructed with a list of 14-feature RansomwareFeatureVectors
- **THEN** getMedian() and getMad() SHALL each return 14-element arrays

### Requirement: CLI accepts 14 comma-separated weights
The `--weights` parameter of `RansomwareDetectorCli` SHALL accept exactly 14 comma-separated decimal values. Providing fewer or more SHALL produce an error message and exit with code 1.

#### Scenario: CLI with 14 custom weights
- **WHEN** `RansomwareDetectorCli` is invoked with `--weights 1.0,1.0,0.5,1.0,0.5,5.0,2.0,1.0,0.5,1.0,2.0,1.0,1.5,2.0`
- **THEN** the scorer SHALL use those 14 values as feature weights

#### Scenario: CLI with wrong number of weights
- **WHEN** `RansomwareDetectorCli` is invoked with `--weights 1.0,2.0,3.0` (only 3 values)
- **THEN** the CLI SHALL print an error and exit with code 1
