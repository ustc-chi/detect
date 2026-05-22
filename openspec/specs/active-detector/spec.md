# Active Detector

## Requirements

### Requirement: Weighted Euclidean distance scoring
The `ActiveDetector` SHALL compute an anomaly score using the weighted Euclidean distance formula.

#### Scenario: Score computation
- **WHEN** a `FeatureVector14` is evaluated in Active phase
- **THEN** for each dimension i: `z_i = (value_i - median_i) / mad_i`, clamped to [-10, +10]
- **AND** score = `sqrt(Σ w_i × z_i²)` over all 14 dimensions
- **AND** contribution per dimension = `w_i × z_i²`

#### Scenario: External data source for baseline
- **WHEN** computing the score
- **THEN** median[14], mad[14] SHALL come from an external API via `BaselineStatsDTO`
- **AND** weights[14] SHALL come from an external data source

### Requirement: Directional validation (quiet day reversal)
The `ActiveDetector` SHALL implement directional validation to prevent false positives caused by "quiet day" patterns.

#### Scenario: Direction reversal logic
- **WHEN** score > threshold
- **THEN** compute eUp = Σ w_i × z_i² for z_i > 0, eDown = Σ w_i × z_i² for z_i < 0
- **AND** ratio = eDown / (eUp + eDown + 1e-10)
- **WHEN** ratio > directionThreshold (default 0.75)
- **THEN** the result SHALL be reversed to NORMAL with `directionReversed = true`

### Requirement: BaselineStatsDTO
The system SHALL define a `BaselineStatsDTO` for receiving baseline data from external API.

#### Scenario: DTO structure
- **WHEN** a `BaselineStatsDTO` is received
- **THEN** it SHALL contain: `median` (double[14]), `mad` (double[14]), `threshold` (double), `weights` (double[14]), `resourceId` (String)
