## MODIFIED Requirements

### Requirement: Weighted Euclidean scorer with 13 features
The WeightedEuclideanScorer SHALL operate on 13-feature vectors (expanded from 11). The N constant SHALL be 13. The DEFAULT_WEIGHTS array SHALL contain 13 elements with rebalanced weights.

Default weights SHALL be:
```
1.0,  // 0: total_operations
3.0,  // 1: modification_ratio
0.5,  // 2: deletion_ratio
0.5,  // 3: bytes_removed
1.5,  // 4: directory_spread
0.8,  // 5: extension_diversity
10.0, // 6: suspicious_extension_ratio
5.0,  // 7: peak_burst_velocity (reduced from 10.0 to avoid over-reliance on burst)
1.0,  // 8: avg_modified_size
4.0,  // 9: size_std_dev (highest non-burst weight — uniform size changes)
2.5,  // 10: high_value_ext_ratio
3.0,  // 11: burst_mod_purity
2.0   // 12: file_type_concentration
```

#### Scenario: Scorer with default weights handles 13 features
- **WHEN** WeightedEuclideanScorer is constructed with BaselineStatistics (default constructor)
- **THEN** the scorer SHALL use 13 default weights, N SHALL be 13, and scoring a 13-feature vector SHALL produce a valid distance

#### Scenario: Scorer rejects wrong-dimension weights
- **WHEN** WeightedEuclideanScorer is constructed with a weights array of length != 13
- **THEN** the scorer SHALL fall back to DEFAULT_WEIGHTS

### Requirement: Baseline statistics with 13 features
The BaselineStatistics SHALL compute median and MAD for 13 features (expanded from 11). The feature dimensionality is determined by the input vectors.

#### Scenario: Baseline from 13-feature vectors
- **WHEN** BaselineStatistics is constructed with a list of 13-feature RansomwareFeatureVectors
- **THEN** getMedian() and getMad() SHALL each return 13-element arrays
