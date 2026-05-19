## ADDED Requirements

### Requirement: Deleted records use Instant.MAX as change_time sentinel
The generator SHALL set the `change_time` field of all deleted `DiffEntry` records to `Instant.MAX.toString()` (`+1000000000-12-31T23:59:59.999999999Z`) instead of a random timestamp. This applies to all generation methods in `FilesystemState` (evolveNormalRound, evolveLogRotation, evolveCleanupPurge, evolveMigrationWave, and all other evolve* methods) and `AttackGenerator` (all generate* methods and padding generation).

#### Scenario: Normal round deleted records
- **WHEN** `FilesystemState.evolveNormalRound()` generates deleted diff entries
- **THEN** every deleted `DiffEntry` SHALL have `change_time` equal to `Instant.MAX.toString()`

#### Scenario: Attack pattern deleted records
- **WHEN** any `AttackGenerator.generate*()` method creates deleted diff entries (e.g., B4 mixed operations, normal padding)
- **THEN** every deleted `DiffEntry` SHALL have `change_time` equal to `Instant.MAX.toString()`

#### Scenario: Irregular normal round deleted records
- **WHEN** any `FilesystemState.evolve*()` irregular method (logRotation, cleanupPurge, migrationWave, etc.) creates deleted diff entries
- **THEN** every deleted `DiffEntry` SHALL have `change_time` equal to `Instant.MAX.toString()`

### Requirement: Parser preserves Instant.MAX as valid timestamp
The `SnapdiffRecord` constructor SHALL parse `Instant.MAX` ISO-8601 string without error, storing it as `Instant.MAX` in the `changeTime` field. No special casing or fallback to `Instant.EPOCH` SHALL occur for this value.

#### Scenario: Parse record with Instant.MAX change_time
- **WHEN** a JSON record has `"change_time": "+1000000000-12-31T23:59:59.999999999Z"`
- **THEN** `SnapdiffRecord.getChangeTime()` SHALL return `Instant.MAX`

#### Scenario: Parse record with null change_time unchanged
- **WHEN** a JSON record has `"change_time": null` or missing `change_time`
- **THEN** `SnapdiffRecord.getChangeTime()` SHALL return `Instant.EPOCH` (existing behavior unchanged)

### Requirement: Feature extractor excludes Instant.MAX from time-based features
The `RansomwareFeatureExtractor` SHALL exclude records with `changeTime == Instant.MAX` from all time-based feature calculations, identical to how `Instant.EPOCH` is currently handled. This includes burst velocity (feature 2), burst mod purity (feature 3), inter-op time CV (feature 5), temporal uniformity (feature 8), and wall clock anomaly (feature 10). Deleted records with `Instant.MAX` SHALL still be counted in totalOps, deletedCount, per_type_entropy, and rename_correlation.

#### Scenario: Deleted record excluded from burst calculation
- **WHEN** a deleted record has `changeTime == Instant.MAX`
- **THEN** the record SHALL NOT be included in `opRecords` for burst velocity, temporal uniformity, or inter-op CV calculation
- **AND** the record SHALL still be counted in `totalOps` and `deletedCount`

#### Scenario: Deleted record excluded from wall clock anomaly
- **WHEN** `computeWallClockAnomaly()` iterates records and encounters a record with `changeTime == Instant.MAX`
- **THEN** that record SHALL be skipped when determining the earliest timestamp

#### Scenario: Deleted record included in rename correlation
- **WHEN** a deleted record has `changeTime == Instant.MAX`
- **THEN** the record SHALL still appear in `deletedRecords` and be available for rename correlation matching
