## ADDED Requirements

### Requirement: File-based temporary storage for burst computation
During the streaming pass over snapdiff records, the extractor SHALL write each `(epochSeconds, opType)` pair as a line to a temporary file (format: `<epochSeconds>\t<opType>\n`). The temp file SHALL be created via `Files.createTempFile()` and SHALL be deleted in a `finally` block or via try-with-resources, regardless of success or failure.

#### Scenario: Temp file created and populated during streaming
- **WHEN** streaming extraction processes N records with valid `change_time` values
- **THEN** the temp file SHALL contain exactly N lines, each with an epoch-seconds value and operation type separated by a tab character

#### Scenario: Records with null or EPOCH change_time are skipped
- **WHEN** a record has `change_time` equal to null or `Instant.EPOCH`
- **THEN** no line SHALL be written to the temp file for that record

#### Scenario: Temp file cleanup on success
- **WHEN** extraction completes successfully
- **THEN** the temp file SHALL be deleted from disk

#### Scenario: Temp file cleanup on failure
- **WHEN** extraction fails at any point after temp file creation
- **THEN** the temp file SHALL still be deleted from disk

### Requirement: External sort of burst data
After the streaming pass completes, the extractor SHALL read all lines from the temp file, parse them into `(epochSeconds, opType)` pairs, and sort them by `epochSeconds` ascending. Since records in the snapdiff file are NOT sorted by time, this sort step is mandatory.

#### Scenario: Unsorted input produces correctly sorted burst data
- **WHEN** the temp file contains pairs with timestamps in random order (e.g., 300, 100, 200)
- **THEN** the sorted result SHALL be in ascending order (100, 200, 300)

#### Scenario: Duplicate timestamps preserved
- **WHEN** multiple records share the same epoch second
- **THEN** all records with that timestamp SHALL appear consecutively in the sorted output, preserving their original opTypes

### Requirement: Sliding window computation over sorted burst data
The sorted burst data SHALL be used to compute both `peak_burst_velocity` (feature 7) and `burst_mod_purity` (feature 11) using the same sliding window algorithm as the current implementation: a 300-second (5-minute) window with two pointers advancing over the sorted sequence.

#### Scenario: Peak burst velocity computed from sorted temp file
- **WHEN** the sorted burst data contains 1000 records with a dense cluster of 500 within a 5-minute window
- **THEN** `peak_burst_velocity` SHALL equal `500 / (300.0 / 3600.0)` = 6000.0 ops/hr

#### Scenario: Burst mod purity computed from sorted temp file
- **WHEN** the densest 5-minute window contains 400 modified and 100 deleted operations
- **THEN** `burst_mod_purity` SHALL equal `400 / 500` = 0.8

#### Scenario: Fewer than 2 records with valid timestamps
- **WHEN** fewer than 2 records have valid `change_time` values
- **THEN** both `peak_burst_velocity` and `burst_mod_purity` SHALL be 0.0
