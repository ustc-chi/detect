## ADDED Requirements

### Requirement: Streaming JSON parser for snapdiff files
The `StreamingSnapdiffParser` SHALL provide a method `parse(Path filePath, Consumer<SnapdiffRecord> consumer)` that reads a snapdiff JSON file using Jackson's `JsonParser` streaming API. It SHALL advance to the `"diffs"` array and deserialize each element as a `SnapdiffRecord` via `objectMapper.readValue(jsonParser, SnapdiffRecord.class)`, passing each record to the consumer callback. Only one `SnapdiffRecord` SHALL be in memory at a time.

#### Scenario: Streaming parse of valid snapdiff file
- **WHEN** `parse()` is called with a path to a valid snapdiff JSON containing N diff records
- **THEN** the consumer SHALL be invoked exactly N times, each time with a valid `SnapdiffRecord`

#### Scenario: Streaming parse of empty diffs array
- **WHEN** `parse()` is called with a snapdiff JSON whose `"diffs"` array is empty
- **THEN** the consumer SHALL not be invoked, and no exception SHALL be thrown

#### Scenario: Streaming parse validates diff types
- **WHEN** a record in the diffs array has a `type` field that is not `"added"`, `"modified"`, or `"deleted"`
- **THEN** the parser SHALL throw `IllegalArgumentException` with a message containing the invalid type

#### Scenario: Streaming parse resource cleanup on success
- **WHEN** `parse()` completes successfully
- **THEN** the underlying `InputStream` and `JsonParser` SHALL be closed

#### Scenario: Streaming parse resource cleanup on error
- **WHEN** `parse()` encounters an error (malformed JSON, invalid record)
- **THEN** the underlying `InputStream` and `JsonParser` SHALL be closed before the exception propagates

### Requirement: Backward-compatible in-memory parser preserved
The existing `SnapdiffParser.parse(Path)` method SHALL remain available and unchanged. It SHALL be marked `@Deprecated` with a Javadoc note directing users to `StreamingSnapdiffParser` for large files.

#### Scenario: Old parser still works
- **WHEN** `SnapdiffParser.parse(Path)` is called on a valid snapdiff file
- **THEN** it SHALL return a `SnapdiffFile` with all diffs loaded, identical to current behavior
