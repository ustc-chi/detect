## MODIFIED Requirements

### Requirement: Unified detection entry point with dual sensitivity
The `AnomalyDetectionService` SHALL provide a public method `detect(FeatureVector vector, String resourceId, int warmupSensitivity, int activeSensitivity)` that returns a `DetectionResult`. Warmup sensitivity and active sensitivity SHALL be independent integers in [1, 10], where 10 = most sensitive. A convenience overload `detect(FeatureVector vector, String resourceId)` SHALL use configured defaults.

#### Scenario: Basic warmup detection flow
- **WHEN** `detect(vector, resourceId, 7, 8)` is called
- **THEN** the service SHALL determine the phase (Warmup or Active) based on the resource's normal history count
- **AND** in Warmup phase, SHALL pass sensitivity=7 to WarmupDetector
- **AND** in Active phase, SHALL pass sensitivity=8 to BaselineDataProvider

#### Scenario: Warmup phase with sensitivity
- **WHEN** normal count < NORMAL_THRESHOLD and `detect(vector, resourceId, 3, 5)` is called
- **THEN** delegate to `WarmupDetector` with sensitivity=3
- **AND** result phase = `WARMUP`

#### Scenario: Active phase with independent sensitivity
- **WHEN** normal count >= NORMAL_THRESHOLD and `detect(vector, resourceId, 3, 9)` is called
- **THEN** delegate to `BaselineDataProvider.getBaselineStats(resourceId, 9)` with activeSensitivity=9
- **AND** result phase = `ACTIVE`

#### Scenario: Convenience overload uses defaults
- **WHEN** `detect(vector, resourceId)` is called
- **THEN** the service SHALL use `SensitivityConfig.getDefaultWarmupSensitivity()` for warmup
- **AND** use `SensitivityConfig.getDefaultActiveSensitivity()` for active

### Requirement: Phase determination (unchanged)
The service SHALL determine the current phase by comparing normal vector count against a threshold.

#### Scenario: Warmup phase routing (unchanged)
- **WHEN** normal count < NORMAL_THRESHOLD (default 10)
- **THEN** delegate to `WarmupDetector`, result phase = `WARMUP`

#### Scenario: Active phase routing (unchanged)
- **WHEN** normal count >= NORMAL_THRESHOLD (default 10)
- **THEN** delegate to `ActiveDetector`, result phase = `ACTIVE`

## REMOVED Requirements

### Requirement: Single double sensitivity parameter
**Reason**: Replaced by dual int sensitivity parameters (warmupSensitivity + activeSensitivity, range 1-10)
**Migration**: Callers must replace `detect(vector, resourceId, 0.7)` with `detect(vector, resourceId, 7, 7)` or use the no-sensitivity overload.
