## ADDED Requirements

### Requirement: BaselineDataProvider accepts int sensitivity
The `BaselineDataProvider` interface SHALL provide a method `getBaselineStats(String resourceId, int sensitivity)` where sensitivity is in [1, 10] and 10 = most sensitive.

#### Scenario: Active phase passes int sensitivity
- **WHEN** `AnomalyDetectionService` is in Active phase with activeSensitivity=X
- **THEN** it SHALL call `getBaselineStats(resourceId, X)` where X is an int in [1, 10]

#### Scenario: Implementation returns baseline stats
- **WHEN** `getBaselineStats(resourceId, 7)` is called
- **THEN** it SHALL return a `BaselineStatsDTO` or null (if not available)

### Requirement: ExternalBaselineProvider implements int version
The `ExternalBaselineProvider` placeholder SHALL implement the int version.

#### Scenario: Default implementation returns null
- **WHEN** `getBaselineStats(resourceId, 5)` is called
- **THEN** it SHALL log a warning and return null (same behavior as before)

## REMOVED Requirements

### Requirement: BaselineDataProvider interface with double sensitivity
**Reason**: Replaced by int sensitivity parameter (range 1-10)
**Migration**: Implementations must replace `getBaselineStats(String, double)` with `getBaselineStats(String, int)`
