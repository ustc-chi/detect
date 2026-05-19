## ADDED Requirements

### Requirement: Normal rounds include rename operations
The `evolveNormalRound()` method SHALL include rename operations as 5–15% of total operations. Each rename SHALL be implemented as a delete of the old path followed by an add of the new path with a versioning suffix (`_v2`, `_backup`, `_old`, `_final`, or `_copy`). The original file extension SHALL be preserved in the renamed path.

#### Scenario: Rename operations present in normal round output
- **WHEN** `evolveNormalRound(dayStart)` is called
- **THEN** the resulting DiffEntry list SHALL contain at least 5% and at most 15% of operations that form rename pairs (a deleted entry followed by an added entry in the same directory with matching filename prefix ≥ 3 characters)

#### Scenario: Renamed files update filesystem state correctly
- **WHEN** a rename operation is processed during `evolveNormalRound()`
- **THEN** the old path SHALL be removed from the files map and the new path SHALL be added with the original extension and same user index preserved

### Requirement: Normal rounds include after-hours timestamps
The `evolveNormalRound()` method SHALL allocate 15–25% of operation timestamps to after-hours periods (hours 18–23 or hours 5–7 relative to dayStart's date). The remaining timestamps SHALL continue to be distributed within the normal hoursWindow.

#### Scenario: After-hours timestamps present in normal round
- **WHEN** `evolveNormalRound(dayStart)` is called with a business-hours dayStart
- **THEN** at least 15% and at most 25% of operation timestamps SHALL fall outside hours 8–17 (i.e., in hours 0–7 or 18–23 of the day)

#### Scenario: After-hours operations are legitimate normal activity
- **WHEN** an after-hours operation is generated
- **THEN** it SHALL have the same size change distribution as business-hours operations (±30% of original size)

### Requirement: Normal rounds exhibit activity volatility
The `evolveNormalRound()` method SHALL produce three tiers of activity level:
- Quiet day (20% probability): total operations reduced to 40% of the base level
- Normal day (60% probability): current activity level (2–10% of total files)
- Busy day (20% probability): total operations increased to 200% of the base level, with a concentrated mini-burst of 50–200 operations within a 5–15 minute window

#### Scenario: Activity level varies across multiple rounds
- **WHEN** `evolveNormalRound()` is called 10+ times with the same Random seed sequence
- **THEN** the total operation counts across rounds SHALL show a coefficient of variation ≥ 0.25 (not all rounds have similar counts)

#### Scenario: Busy days include a concentrated mini-burst
- **WHEN** a busy day round is generated
- **THEN** there SHALL exist at least one 15-minute window containing ≥ 50 operations (a mini-burst within the round)

### Requirement: Normal round rename suffixes are realistic
Rename suffixes SHALL be drawn from the set: `_v2`, `_v3`, `_new`, `_backup`, `_old`, `_final`, `_copy`, `_draft`. Each rename SHALL select a random suffix from this set.

#### Scenario: Suffixes match expected set
- **WHEN** rename operations are generated during `evolveNormalRound()`
- **THEN** all new filenames SHALL end with one of: `_v2`, `_v3`, `_new`, `_backup`, `_old`, `_final`, `_copy`, `_draft`, followed by the original extension
