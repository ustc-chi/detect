## MODIFIED Requirements

### Requirement: Streaming JSON parser for snapdiff files
The `StreamingSnapdiffParser` SHALL provide a method `parse(Path filePath, Consumer<SnapdiffRecord> consumer)` that reads a snapdiff JSON file using Jackson's `JsonParser` streaming API. It SHALL advance to the `"diffs"` array and deserialize each element as a `SnapdiffRecord` via `objectMapper.readValue(jsonParser, SnapdiffRecord.class)`, passing each record to the consumer callback. Only one `SnapdiffRecord` SHALL be in memory at a time.

The `ObjectMapper` and `JsonFactory` SHALL be shared static final instances (created once, reused across all parse calls). The `BufferedInputStream` SHALL use a 64KB buffer (65536 bytes) instead of the default 8KB.

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

#### Scenario: Shared ObjectMapper is reused across calls
- **WHEN** `parse()` is called multiple times on the same StreamingSnapdiffParser instance or different instances
- **THEN** the same static `ObjectMapper` and `JsonFactory` SHALL be used for all calls

#### Scenario: Buffer size is 64KB
- **WHEN** `parse()` opens a file
- **THEN** the BufferedInputStream SHALL be constructed with a buffer size of 65536 bytes
