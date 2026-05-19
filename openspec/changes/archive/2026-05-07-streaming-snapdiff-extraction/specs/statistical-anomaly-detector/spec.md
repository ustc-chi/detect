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

### Requirement: Streaming-compatible signature detection
The `RansomwareSignatureDetector` SHALL support a streaming scan method `scanStream(Path filePath, SuspiciousExtensions suspiciousExtensions)` that processes the snapdiff file using `StreamingSnapdiffParser` and checks each record's path against suspicious extension and ransom note patterns on-the-fly. The existing `scan(List<SnapdiffRecord>)` method SHALL be preserved for backward compatibility.

#### Scenario: Streaming scan detects suspicious extensions
- **WHEN** `scanStream()` processes a snapdiff file containing records with paths ending in `.locked` or `.encrypted`
- **THEN** the returned `SignatureResult.matched()` SHALL be true and `getMatchedExtensions()` SHALL list those paths

#### Scenario: Streaming scan detects ransom notes
- **WHEN** `scanStream()` processes a snapdiff file containing a record with filename containing `HOW_TO_DECRYPT`
- **THEN** the returned `SignatureResult.matched()` SHALL be true and `getMatchedNotePaths()` SHALL list that path

#### Scenario: Streaming scan produces identical results to in-memory scan
- **WHEN** `scanStream(Path)` and `scan(List<SnapdiffRecord>)` are called on the same snapdiff data
- **THEN** the returned `SignatureResult` objects SHALL have identical matched extensions and matched note paths

### Requirement: Detector supports file-based detection
The `RansomwareDetector` SHALL support a `detectFromFile(Path filePath)` method that performs streaming feature extraction and signature scanning in a single pass over the file, then computes the statistical score. The existing `detect(RansomwareFeatureVector, List<SnapdiffRecord>)` method SHALL be preserved for backward compatibility.

#### Scenario: File-based detection result matches in-memory detection
- **WHEN** `detectFromFile(Path)` and `detect(RansomwareFeatureVector, List<SnapdiffRecord>)` are called on the same snapdiff data
- **THEN** the `DetectionResult` objects SHALL have equal scores, thresholds, anomaly flags, and signature matches
