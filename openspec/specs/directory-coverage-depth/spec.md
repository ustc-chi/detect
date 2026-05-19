## ADDED Requirements

### Requirement: Directory coverage depth feature extraction
The system SHALL compute a `directory_coverage_depth` feature representing the breadth and uniformity of directory targeting in modified files.

The feature SHALL be computed as:
1. Extract the parent directory path from each modified file's `path` field (everything up to the last `/`)
2. Count unique parent directories among modified files: `unique_dirs`
3. Compute the mean directory depth of modified files: `mean_depth = Σ(depth(path_i)) / count`, where depth is the number of `/` separators in the path
4. If there are no modified files, return 0.0
5. Return `unique_dirs * (1.0 / (1.0 + std_dev(depths)))` — high when many directories are hit AND depths are uniform

The feature SHALL NOT be divided by `daysBetweenSnapshots`.

#### Scenario: Ransomware breadth-first traversal (B1 backup disguise)
- **WHEN** 3000 modifications are distributed across 80 unique directories with uniform depth (all at depth 3)
- **THEN** `directory_coverage_depth` SHALL be approximately `80 * (1.0 / (1.0 + 0.0))` = 80.0

#### Scenario: Normal user editing (depth-first)
- **WHEN** 150 modifications are concentrated in 3 directories with varying depths (depths 2, 3, 4)
- **THEN** `directory_coverage_depth` SHALL be approximately `3 * (1.0 / (1.0 + ~0.8))` ≈ 1.7

#### Scenario: No modified files
- **WHEN** a snapdiff round contains only adds and deletes
- **THEN** `directory_coverage_depth` SHALL be 0.0

### Requirement: Directory coverage depth feature weight
The default weight for `directory_coverage_depth` SHALL be 2.5 in the WeightedEuclideanScorer DEFAULT_WEIGHTS array.

#### Scenario: Weight configuration includes directory_coverage_depth
- **WHEN** WeightedEuclideanScorer is constructed with default weights
- **THEN** the weight array SHALL contain 12 elements with the weight for directory_coverage_depth equal to 2.5
