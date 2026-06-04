## ADDED Requirements

### Requirement: SensitivityConfig provides default sensitivities
The system SHALL provide a `SensitivityConfig` class that holds default sensitivity values for Warmup and Active phases separately.

#### Scenario: Default values are both 7
- **WHEN** a `SensitivityConfig` is created with no-arg constructor
- **THEN** `getDefaultWarmupSensitivity()` SHALL return 7
- **AND** `getDefaultActiveSensitivity()` SHALL return 7

#### Scenario: Custom values can be injected
- **WHEN** a `SensitivityConfig` is created with `new SensitivityConfig(5, 9)`
- **THEN** `getDefaultWarmupSensitivity()` SHALL return 5
- **AND** `getDefaultActiveSensitivity()` SHALL return 9

#### Scenario: Invalid warmup sensitivity throws
- **WHEN** a `SensitivityConfig` is created with warmup sensitivity outside [1, 10]
- **THEN** it SHALL throw IllegalArgumentException

#### Scenario: Invalid active sensitivity throws
- **WHEN** a `SensitivityConfig` is created with active sensitivity outside [1, 10]
- **THEN** it SHALL throw IllegalArgumentException

#### Scenario: SensitivityConfig is immutable
- **WHEN** a `SensitivityConfig` is created
- **THEN** its fields SHALL be final and have no setters

### Requirement: AnomalyDetectionService uses SensitivityConfig
The `AnomalyDetectionService` SHALL accept a `SensitivityConfig` in its constructor and use its values when the `detect()` method is called without explicit sensitivity parameters.

#### Scenario: No-arg detect uses config defaults
- **WHEN** `detect(vector, resourceId)` is called (without sensitivity values)
- **THEN** the Warmup phase SHALL use `config.getDefaultWarmupSensitivity()`
- **AND** the Active phase SHALL use `config.getDefaultActiveSensitivity()`

#### Scenario: Explicit detect overrides config
- **WHEN** `detect(vector, resourceId, 10, 3)` is called
- **THEN** the Warmup phase SHALL use sensitivity 10
- **AND** the Active phase SHALL use sensitivity 3
- **AND** the config default SHALL be ignored
