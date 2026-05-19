## ADDED Requirements

### Requirement: Temporal uniformity feature extraction
The system SHALL compute a `temporal_uniformity` feature representing the regularity of operation counts across sequential 5-minute bins within the round.

The feature SHALL be computed as:
1. Sort all records by `changeTime` ascending
2. Determine the time span: `t_last - t_first` (in seconds)
3. Divide the span into non-overlapping 5-minute (300-second) bins starting from `t_first`
4. Count operations in each bin: `counts[i]` for bin i
5. Compute `μ = mean(counts)` and `σ = std_dev(counts)`
6. If `μ < 0.001` or fewer than 3 bins have data, return 0.0
7. Return `1.0 - (σ / μ)` — high value (near 1.0) means suspiciously regular activity

The feature SHALL NOT be divided by `daysBetweenSnapshots`.

#### Scenario: Slow drip attack B2 (perfect regularity)
- **WHEN** 3600 operations are distributed as exactly 50 ops per 5-minute bin across 72 bins (6 hours)
- **THEN** `temporal_uniformity` SHALL be approximately `1.0 - (0.0 / 50.0)` = 1.0

#### Scenario: Normal activity (irregular bursts)
- **WHEN** operations cluster in morning and afternoon bursts with a lunch gap, producing bin counts like [120, 80, 5, 2, 0, 95, 110]
- **THEN** `temporal_uniformity` SHALL be negative or near 0.0 (high CV)

#### Scenario: Too few bins for meaningful computation
- **WHEN** all operations occur within a single 5-minute window (only 1 bin)
- **THEN** `temporal_uniformity` SHALL be 0.0

#### Scenario: Backup disguise B1 (sustained but slightly irregular)
- **WHEN** 4000 operations are spread over 2 hours with per-bin counts varying between 150–170
- **THEN** `temporal_uniformity` SHALL be approximately `1.0 - (8.2 / 160)` ≈ 0.95

### Requirement: Temporal uniformity computed from burst data
The `temporal_uniformity` SHALL be computed from the same sorted timestamp data used for burst features. No additional sorting pass SHALL be required.

#### Scenario: Streaming mode computes temporal_uniformity from burst temp file
- **WHEN** streaming extraction completes the sort of the burst temp file
- **THEN** `temporal_uniformity` SHALL be computed from the same sorted data

### Requirement: Temporal uniformity feature weight
The default weight for `temporal_uniformity` SHALL be 2.5 in the WeightedEuclideanScorer DEFAULT_WEIGHTS array.

#### Scenario: Weight configuration includes temporal_uniformity
- **WHEN** WeightedEuclideanScorer is constructed with default weights
- **THEN** the weight array SHALL contain 12 elements with the weight for temporal_uniformity equal to 2.5
