## ADDED Requirements

### Requirement: Map int sensitivity to threshold multiplier
The `SensitivityAdjuster` SHALL provide a method that converts an int sensitivity value (range 1-10) to a threshold multiplier in [0.5, 2.0].

#### Scenario: Most sensitive (int=10) produces minimum multiplier
- **WHEN** `getThresholdMultiplier(10)` is called
- **THEN** it SHALL return 0.5 (thresholds halved = most sensitive)

#### Scenario: Least sensitive (int=1) produces maximum multiplier
- **WHEN** `getThresholdMultiplier(1)` is called
- **THEN** it SHALL return 2.0 (thresholds doubled = least sensitive)

#### Scenario: Default (int=7) produces multiplier ≈ 1.0
- **WHEN** `getThresholdMultiplier(7)` is called
- **THEN** it SHALL return 1.0 (neutral/balanced sensitivity)

#### Scenario: Mid-range (int=5) produces intermediate multiplier
- **WHEN** `getThresholdMultiplier(5)` is called
- **THEN** it SHALL return approximately 1.333

#### Scenario: Invalid value below 1 throws exception
- **WHEN** `getThresholdMultiplier(0)` is called
- **THEN** it SHALL throw IllegalArgumentException

#### Scenario: Invalid value above 10 throws exception
- **WHEN** `getThresholdMultiplier(11)` is called
- **THEN** it SHALL throw IllegalArgumentException

#### Scenario: Boundary int=1 is valid
- **WHEN** `getThresholdMultiplier(1)` is called
- **THEN** it SHALL NOT throw an exception

#### Scenario: Boundary int=10 is valid
- **WHEN** `getThresholdMultiplier(10)` is called
- **THEN** it SHALL NOT throw an exception

### Requirement: Int mapping uses two-step conversion
The int→multiplier mapping SHALL first convert int to effective double in [0.0, 1.0], then apply the existing multiplier formula.

#### Scenario: Conversion preserves 0-1 range behavior
- **WHEN** int sensitivity is mapped
- **THEN** the effective double SHALL equal `(sensitivity - 1) / 9.0`
- **AND** the multiplier SHALL equal `2.0 - effectiveDouble * 1.5`
