## ADDED Requirements

### Requirement: DetectionResult structure
The `DetectionResult` class SHALL encapsulate all information produced by a detection in either Warmup or Active phase.

#### Scenario: DetectionResult contains complete information
- **WHEN** a `DetectionResult` is created
- **THEN** it SHALL contain:
  - `resourceId` (String)
  - `detectionTime` (Instant)
  - `phase` (Phase: WARMUP or ACTIVE)
  - `score` (double)
  - `threshold` (double)
  - `isAnomaly` (boolean)
  - `dimensions` (List of 14 DimensionReport entries)
  - `topDeviations` (List of DimensionReport, top 5 by |zScore|)

#### Scenario: Null safety
- **WHEN** a `DetectionResult` is constructed
- **THEN** all collection fields SHALL be non-null (empty list instead of null)

### Requirement: Direction validation info
The `DetectionResult` SHALL include direction validation information when applicable.

#### Scenario: Direction fields in result
- **WHEN** a detection is performed in Active phase
- **THEN** the result SHALL include: `directionReversed` (boolean), `directionRatio` (double), `eUp` (double), `eDown` (double)
- **WHEN** no direction validation was performed
- **THEN** `directionReversed` SHALL be `false`, ratio/eUp/eDown SHALL be 0

### Requirement: Warmup info
The `DetectionResult` SHALL include warmup-specific information when in Warmup phase.

#### Scenario: WarmupInfo structure
- **WHEN** a detection is performed in Warmup phase
- **THEN** the result SHALL contain a `WarmupInfo` with: `matchingRuleCount` (int), `triggeredRules` (List of String), `confidence` (double), `addToBaseline` (boolean)
- **WHEN** in Active phase
- **THEN** `WarmupInfo` SHALL be null

### Requirement: Signature match info
The `DetectionResult` SHALL include signature match information when a deterministic signature (suspicious extension, ransom note pattern) is detected.

#### Scenario: Signature match
- **WHEN** a signature match occurs (Layer 1 deterministic rule triggered)
- **THEN** the result SHALL include `signatureMatch` with a description of what was matched
- **AND** score SHALL be `Double.MAX_VALUE`
- **AND** `isAnomaly` SHALL be `true`

### Requirement: DimensionReport detail
Each `DimensionReport` in the `DetectionResult` SHALL contain full analysis details for that feature dimension.

#### Scenario: DimensionReport fields
- **WHEN** a `DimensionReport` is present in the result
- **THEN** it SHALL contain: `index`, `name`, `value`, `zScore`, `contribution` (w_i × z_i²), `weight`, `description`, `unit`, `isAnomalyDimension`, `supplementary` (Map)
- **AND** all numeric fields SHALL be 0 if not applicable in current phase (e.g., zScore in Warmup)
