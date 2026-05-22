# Detection Result

## Requirements

### Requirement: DetectionResult structure
The `DetectionResult` class SHALL encapsulate all information produced by a detection in either Warmup or Active phase.

#### Scenario: DetectionResult contains complete information
- **WHEN** a `DetectionResult` is created
- **THEN** it SHALL contain: `resourceId`, `detectionTime`, `phase`, `score`, `threshold`, `isAnomaly`, `dimensions` (14 DimensionReport entries), `topDeviations` (top 5 by |zScore|)

#### Scenario: Null safety
- **WHEN** a `DetectionResult` is constructed
- **THEN** all collection fields SHALL be non-null (empty list instead of null)

### Requirement: Direction validation info
The `DetectionResult` SHALL include direction validation information when applicable.

#### Scenario: Direction fields in result
- **WHEN** a detection is performed in Active phase
- **THEN** the result SHALL include: `directionReversed`, `directionRatio`, `eUp`, `eDown`

### Requirement: Warmup info
The `DetectionResult` SHALL include warmup-specific information when in Warmup phase.

#### Scenario: WarmupInfo structure
- **WHEN** in Warmup phase
- **THEN** the result SHALL contain `WarmupInfo` with: `matchingRuleCount`, `triggeredRules`, `confidence`, `addToBaseline`

### Requirement: DimensionReport detail
Each `DimensionReport` SHALL contain full analysis details.

#### Scenario: DimensionReport fields
- **WHEN** a `DimensionReport` is present
- **THEN** it SHALL contain: `index`, `name`, `value`, `zScore`, `contribution`, `weight`, `description`, `unit`, `isAnomalyDimension`, `supplementary`
