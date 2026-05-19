## ADDED Requirements

### Requirement: In-memory accumulation of burst data records
A new class `InMemoryBurstAccumulator` SHALL provide the same API as `BurstDataFile` (`create()`, `write(long, String)`, `computeBurstFeatures()`, `close()`) but accumulate records in an `ArrayList` instead of writing to a temporary file. Each call to `write(epochSeconds, opType)` SHALL append an `OpEntry` to the list.

#### Scenario: Records accumulated in memory
- **WHEN** `write()` is called N times with valid (epochSeconds, opType) pairs
- **THEN** the internal list SHALL contain exactly N OpEntry records

#### Scenario: No temp file created
- **WHEN** InMemoryBurstAccumulator is instantiated and used
- **THEN** no temporary file SHALL be created on disk

### Requirement: Identical burst feature computation
`InMemoryBurstAccumulator.computeBurstFeatures()` SHALL produce the same `BurstFeatures` output as `BurstDataFile.computeBurstFeatures()` when given identical input records. The computation SHALL use the same algorithms: sort by epochSeconds ascending, then sliding window (300s) for peak_burst_velocity, burst_mod_purity, inter_op_time_cv_burst, and temporal_uniformity.

#### Scenario: Parity with BurstDataFile output
- **WHEN** identical (epochSeconds, opType) pairs are fed to both BurstDataFile and InMemoryBurstAccumulator
- **THEN** all 6 fields of BurstFeatures SHALL be numerically identical between the two implementations

#### Scenario: Fewer than 2 records returns zero features
- **WHEN** fewer than 2 records have been written
- **THEN** computeBurstFeatures() SHALL return BurstFeatures with all numeric fields as 0.0

### Requirement: Drop-in replacement for BurstDataFile in streaming path
`RansomwareFeatureExtractor.extractFromFile()` SHALL use `InMemoryBurstAccumulator` instead of `BurstDataFile`. The `BurstDataFile` class SHALL remain available but unused in the streaming extraction path.

#### Scenario: Streaming extraction uses no disk I/O for burst data
- **WHEN** extractFromFile() processes a snapdiff file via StreamingSnapdiffParser
- **THEN** burst data SHALL be accumulated in memory with zero temp-file creation
