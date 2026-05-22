# Anomaly Detection Service

## Requirements

### Requirement: Unified detection entry point
The `AnomalyDetectionService` SHALL provide a single public method `detect(String resourceId, FeatureVector14 vector)` that returns a `DetectionResult`.

#### Scenario: Basic detection flow
- **WHEN** `detect(resourceId, featureVector14)` is called
- **THEN** the service SHALL determine the phase (Warmup or Active) based on the resource's normal history count
- **AND** delegate to the appropriate detector (WarmupDetector or ActiveDetector)

### Requirement: Phase determination
The service SHALL determine the current phase by comparing normal vector count against a threshold.

#### Scenario: Warmup phase
- **WHEN** normal count < NORMAL_THRESHOLD (default 10)
- **THEN** delegate to `WarmupDetector`, result phase = `WARMUP`

#### Scenario: Active phase
- **WHEN** normal count >= NORMAL_THRESHOLD (default 10)
- **THEN** delegate to `ActiveDetector`, result phase = `ACTIVE`

### Requirement: Threshold configuration
The `NORMAL_THRESHOLD` SHALL be configurable (default 10).

### Requirement: Result persistence callback
After detection completes, the service SHALL pass the result to a configurable `ResultHandler`.

#### Scenario: Callback after detection
- **WHEN** a detection completes
- **THEN** the result SHALL be passed to a `ResultHandler` for persistence
- **AND** handler failure SHALL NOT affect the returned result
