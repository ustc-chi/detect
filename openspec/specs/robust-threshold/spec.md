### Requirement: IQR-based outlier filtering in threshold calculation
The `AnomalyThreshold` constructor SHALL compute the Interquartile Range (IQR) of baseline self-scores and exclude scores exceeding Q3 + k × IQR before computing the percentile threshold, where k is the configurable IQR multiplier (default 2.5).

#### Scenario: Outlier baseline round filtered out
- **GIVEN** 24 baseline vectors where 23 produce self-scores in range [3.0, 8.74] and 1 produces a score of 42.5 (contaminated round)
- **WHEN** AnomalyThreshold is constructed with percentile=97.0 and iqrMultiplier=2.5
- **THEN** the outlier score (42.5) SHALL be excluded from the percentile calculation
- **AND** the threshold SHALL be computed from the remaining 23 scores at the 97th percentile

#### Scenario: Clean baseline produces same threshold as before
- **GIVEN** 24 baseline vectors where all self-scores are in a normal range [3.0, 8.74]
- **WHEN** AnomalyThreshold is constructed with percentile=97.0 and iqrMultiplier=2.5
- **THEN** no scores SHALL be filtered out
- **AND** the threshold SHALL be identical to what the unfiltered percentile would produce

#### Scenario: Small baseline skips IQR filtering
- **GIVEN** 4 baseline vectors (N < 5)
- **WHEN** AnomalyThreshold is constructed with percentile=97.0 and iqrMultiplier=2.5
- **THEN** IQR filtering SHALL be skipped
- **AND** the threshold SHALL be computed using raw percentile on all scores
- **AND** the 3× median cap SHALL still apply

### Requirement: Maximum threshold cap at 3× median self-score
After IQR filtering and percentile selection, the threshold SHALL be capped at 3 times the median of the filtered self-scores. If the percentile value exceeds 3× median, the cap value SHALL be used instead.

#### Scenario: Percentile exceeds median cap
- **GIVEN** filtered self-scores with median = 4.5 and 97th percentile = 18.0
- **WHEN** AnomalyThreshold is constructed
- **THEN** the threshold SHALL be set to min(18.0, 3 × 4.5) = 13.5

#### Scenario: Percentile below median cap
- **GIVEN** filtered self-scores with median = 4.5 and 97th percentile = 8.74
- **WHEN** AnomalyThreshold is constructed
- **THEN** the threshold SHALL be 8.74 (min(8.74, 13.5))

### Requirement: Configurable IQR multiplier
The IQR multiplier k SHALL be configurable. Default value SHALL be 2.5. Setting k to 0 SHALL disable IQR filtering entirely while preserving the median cap.

#### Scenario: IQR multiplier set to 0 disables filtering
- **GIVEN** 24 baseline vectors with outlier score 42.5
- **WHEN** AnomalyThreshold is constructed with iqrMultiplier=0.0
- **THEN** no IQR filtering SHALL occur
- **AND** the threshold SHALL be the raw 97th percentile (42.5)
- **AND** the median cap SHALL still apply (threshold = min(42.5, 3 × median))

#### Scenario: Aggressive IQR multiplier
- **GIVEN** baseline self-scores with a moderate outlier at the upper end
- **WHEN** AnomalyThreshold is constructed with iqrMultiplier=1.5
- **THEN** more scores SHALL be filtered than with the default k=2.5

### Requirement: Diagnostic logging on outlier detection
When baseline self-scores are filtered by IQR, the system SHALL log a WARNING message listing the count of filtered scores and their values.

#### Scenario: Outliers detected and logged
- **GIVEN** baseline vectors where IQR filtering removes 2 outlier scores (28.3, 42.5)
- **WHEN** AnomalyThreshold is constructed
- **THEN** a WARNING log SHALL be emitted containing "Filtered 2 outlier baseline scores"
- **AND** the log SHALL include the values [28.3, 42.5]

#### Scenario: No outliers detected
- **GIVEN** baseline vectors where no scores exceed the IQR fence
- **WHEN** AnomalyThreshold is constructed
- **THEN** no warning log SHALL be emitted

### Requirement: Backward compatibility with existing constructor
The existing `AnomalyThreshold(List, WeightedEuclideanScorer, double)` constructor SHALL continue to work with the same behavior on clean baselines. A new overloaded constructor SHALL accept the IQR multiplier parameter.

#### Scenario: Legacy constructor without IQR multiplier
- **WHEN** AnomalyThreshold is constructed via `AnomalyThreshold(baselineVectors, scorer, 97.0)` (3-arg constructor)
- **THEN** the default IQR multiplier of 2.5 SHALL be used
- **AND** the median cap of 3× SHALL be applied

#### Scenario: New constructor with IQR multiplier
- **WHEN** AnomalyThreshold is constructed via `AnomalyThreshold(baselineVectors, scorer, 97.0, 1.5)` (4-arg constructor)
- **THEN** the IQR multiplier SHALL be 1.5
