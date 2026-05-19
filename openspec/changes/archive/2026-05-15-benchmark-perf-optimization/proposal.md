## Why

The IntermittentEncryptionBenchmark takes 20+ minutes to run, making iteration painfully slow. The primary causes are: regenerating 151 snapdiff JSON files from scratch every run (including a 300K-file simulated filesystem), pretty-printing all output with INDENT_OUTPUT, redundant JSON serialization-deserialization round-trips, deep-copying a 300K-element map 204 times for snapshot/restore, and a streaming parser that parses the same file 3 times per detection while writing every record to a temp file on disk. Target: under 5 minutes with identical detection results.

## What Changes

- **New `BenchmarkDataGenerator`**: Standalone tool that pre-generates all 151 snapdiff JSON files to a persistent `benchmark-data/` folder. Run once, commit the data. Benchmark loads from disk instead of regenerating.
- **Refactored `IntermittentEncryptionBenchmark`**: Loads pre-generated JSON files instead of creating FilesystemState + AttackGenerator from scratch. Preserves all detection logic and output format unchanged.
- **Optimized `StreamingSnapdiffParser`**: Shared static `ObjectMapper` and `JsonFactory` (thread-safe, avoids per-call allocation). 64KB buffer instead of default 8KB.
- **New `InMemoryBurstAccumulator`**: Replaces `BurstDataFile` temp-file I/O with in-memory `ArrayList<OpEntry>` accumulation. Identical burst feature computation, zero disk I/O.
- **Eliminated triple-parse in `RansomwareDetector`**: Single-pass streaming with fan-out to both signature detector and feature extractor, instead of parsing the same file 3 times.
- **Optimized `FilesystemState` snapshot/restore**: Shallow `LinkedHashMap` copy with immutable `FileInfo` objects instead of 300K deep copies.
- **Disabled `INDENT_OUTPUT`** in benchmark: Compact JSON serialization removes 2-3x size overhead from pretty-printing.
- **Timing instrumentation**: Per-phase timing in benchmark output for ongoing performance monitoring.

## Capabilities

### New Capabilities
- `benchmark-data-cache`: Pre-generated snapdiff test data cache with manifest, loaded by the benchmark instead of generated at runtime
- `in-memory-burst-accumulation`: In-memory replacement for BurstDataFile temp-file I/O, performing identical burst feature computation without disk writes

### Modified Capabilities
- `streaming-snapdiff-parser`: Optimize with shared ObjectMapper/JsonFactory and 64KB buffer
- `ransomware-test-generator`: Benchmark refactored to load pre-generated data; FilesystemState snapshot/restore optimized with shallow copy-on-write

## Impact

- **Files Modified**: `IntermittentEncryptionBenchmark.java`, `StreamingSnapdiffParser.java`, `RansomwareDetector.java`, `RansomwareSignatureDetector.java`, `RansomwareFeatureExtractor.java`, `FilesystemState.java`, `AttackGenerator.java`
- **New Files**: `BenchmarkDataGenerator.java`, `InMemoryBurstAccumulator.java`, `benchmark-data/` directory (~265 MB of JSON files + manifest)
- **No API Changes**: All public method signatures preserved. New methods added but none removed.
- **No New Dependencies**: Uses only existing Jackson and JDK libraries
- **Detection Accuracy Preserved**: 102/102 attack detection, 0/24 vanilla normal false positives — verified by full benchmark run
