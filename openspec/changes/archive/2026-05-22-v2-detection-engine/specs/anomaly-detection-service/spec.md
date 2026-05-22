## ADDED Requirements

### Requirement: Unified detection entry point
The `AnomalyDetectionService` SHALL provide a single public method `detect(String resourceId, FeatureVector14 vector)` that returns a `DetectionResult`.

#### Scenario: Basic detection flow
- **WHEN** `detect(resourceId, featureVector14)` is called
- **THEN** the service SHALL determine the phase (Warmup or Active) based on the resource's normal history count
- **AND** delegate to the appropriate detector (WarmupDetector or ActiveDetector)
- **AND** return a complete `DetectionResult`

### Requirement: Phase determination
The service SHALL determine the current phase by querying an external interface for the resource's historical detection results.

#### Scenario: Warmup phase
- **WHEN** the resource's accumulated normal vector count is less than `NORMAL_THRESHOLD` (default 10)
- **THEN** the service SHALL delegate to `WarmupDetector`
- **AND** the result's `phase` SHALL be `Phase.WARMUP`

#### Scenario: Active phase
- **WHEN** the resource's accumulated normal vector count is >= `NORMAL_THRESHOLD` (default 10)
- **THEN** the service SHALL delegate to `ActiveDetector`
- **AND** the result's `phase` SHALL be `Phase.ACTIVE`

### Requirement: Historical data interface
The service SHALL accept historical detection data from an external caller-provided mechanism.

#### Scenario: Historical data input
- **WHEN** `detect()` is called
- **THEN** the service SHALL receive all normal historical vectors for this resource (from external query, passed as parameter or fetched via callback)
- **AND** optionally receive all anomaly historical vectors for this resource

### Requirement: Threshold configuration
The `NORMAL_THRESHOLD` SHALL be configurable.

#### Scenario: Configurable threshold
- **WHEN** the service is constructed with a custom threshold
- **THEN** it SHALL use that threshold instead of the default (10)
- **AND** the default SHALL be 10

### Requirement: External data for ActiveDetector
The service SHALL obtain `BaselineStatsDTO` (median, mad, threshold, weights) from an external data source before delegating to `ActiveDetector`.

#### Scenario: Data retrieval
- **WHEN** the service determines Active phase
- **THEN** it SHALL retrieve `BaselineStatsDTO` for the given `resourceId` via a pluggable data provider interface
- **AND** pass the stats to `ActiveDetector`

### Requirement: Result persistence callback
After detection completes, the service SHALL notify a callback/persistence layer of the result.

#### Scenario: Callback after detection
- **WHEN** a detection completes (both Warmup and Active)
- **THEN** the `DetectionResult` SHALL be passed to a configurable `ResultHandler` for persistence
- **AND** the handler failure SHALL NOT affect the detection result returned to the caller
