## ADDED Requirements

### Requirement: Rename correlation feature extraction
The system SHALL compute a `rename_correlation` feature representing the count of files that were renamed during the round, reconstructed from correlated (deleted + added) record pairs.

The feature SHALL be computed as:
1. Collect all records with `type = "deleted"` into a set `D`
2. Collect all records with `type = "added"` into a set `A`
3. For each added record `a ∈ A`:
   a. Extract the filename (last path component) without extension: `name_a`
   b. For each deleted record `d ∈ D` where `d` is in the same parent directory as `a`:
      - Extract the filename without extension: `name_d`
      - If `name_a` is a prefix of `name_d` OR `name_d` is a prefix of `name_a` (length ≥ 3), increment `rename_count` and remove `d` from `D` (one-to-one matching)
4. If `total_operations > 0`, return `rename_count / total_operations`
5. If `total_operations == 0`, return 0.0

The feature SHALL NOT be divided by `daysBetweenSnapshots`.

#### Scenario: REvil mass rename with random extensions
- **WHEN** 2500 files are renamed (deleted as `.docx` + added as `.docx.abc12345`) in the same directory
- **THEN** `rename_correlation` SHALL be approximately `2500 / 5000` = 0.5 (5000 total ops = 2500 deletes + 2500 adds)

#### Scenario: Normal activity with no renames
- **WHEN** a snapdiff round contains 200 modifications, 50 adds, and 30 deletes with unrelated paths
- **THEN** `rename_correlation` SHALL be approximately 0.0

#### Scenario: B8 rename and encrypt (filename randomized)
- **WHEN** 2500 files are renamed to `file_[8-random-chars]` (deleted + added with same directory, original filename prefix matches)
- **THEN** `rename_correlation` SHALL be approximately `2500 / 5000` = 0.5

#### Scenario: Cl0p companion files (no renames, just additions)
- **WHEN** 2000 files are modified and 2000 new `.key` files are added alongside them
- **THEN** `rename_correlation` SHALL be approximately 0.0 (adds are new files, not correlated with deletes)

### Requirement: Rename correlation feature weight
The default weight for `rename_correlation` SHALL be 3.0 in the WeightedEuclideanScorer DEFAULT_WEIGHTS array.

#### Scenario: Weight configuration includes rename_correlation
- **WHEN** WeightedEuclideanScorer is constructed with default weights
- **THEN** the weight array SHALL contain 12 elements with the weight for rename_correlation equal to 3.0
