## MODIFIED Requirements

### Requirement: Weighted Euclidean scorer with 13 features
The WeightedEuclideanScorer SHALL operate on 13-feature vectors. The N constant SHALL be 13. The DEFAULT_WEIGHTS array SHALL contain 13 elements with rebalanced weights optimized for false positive reduction.

Default weights SHALL be:
```
2.0,  // 0: total_operations (increased from 1.0)
3.0,  // 1: modification_ratio
0.5,  // 2: deletion_ratio
0.5,  // 3: bytes_removed
1.5,  // 4: directory_spread
0.8,  // 5: extension_diversity
10.0, // 6: suspicious_extension_ratio
5.0,  // 7: peak_burst_velocity
1.5,  // 8: avg_modified_size (increased from 1.0)
1.5,  // 9: size_std_dev (reduced from 4.0)
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

### Requirement: Default threshold percentile at 97%
The default threshold percentile for anomaly detection SHALL be 97% (increased from 90%). This reduces false positives by tightening the decision boundary while maintaining detection of all attack patterns.

#### Scenario: Default threshold uses 97th percentile
- **WHEN** AnomalyThreshold is constructed with baseline vectors and no explicit percentile
- **THEN** the threshold SHALL be computed at the 97th percentile of baseline self-scores

#### Scenario: Custom percentile overrides default
- **WHEN** AnomalyThreshold is constructed with an explicit percentile value
- **THEN** the threshold SHALL be computed at that percentile, not the default 97%

### Requirement: Zero false positives on benchmark
The detection system with updated weights and 97th percentile threshold SHALL achieve 0/24 false positives on the standard 60-round benchmark while maintaining 72/72 attack detection across all padding levels.

#### Scenario: No false positives on normal rounds
- **WHEN** the 24 normal rounds from the 60-round benchmark are scored
- **THEN** all 24 rounds SHALL score below the anomaly threshold (0 FPs)

#### Scenario: All attacks still detected
- **WHEN** all 72 attack test cases (36 original + 36 intermittent) are scored
- **THEN** all 72 SHALL be flagged as anomalies
