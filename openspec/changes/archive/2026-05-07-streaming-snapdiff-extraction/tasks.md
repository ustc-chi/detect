## 1. Streaming Parser

- [x] 1.1 Create `StreamingSnapdiffParser` class in `com.anomalydetection.parser` with method `parse(Path filePath, Consumer<SnapdiffRecord> consumer)` using Jackson `JsonParser` streaming API. Configure `ObjectMapper` with `JavaTimeModule` and `FAIL_ON_UNKNOWN_PROPERTIES=false`. Open `BufferedInputStream`, create `JsonFactory.createParser()`, advance to `"diffs"` array, deserialize each element via `objectMapper.readValue(jsonParser, SnapdiffRecord.class)`, pass to consumer. Ensure `InputStream` and `JsonParser` are closed in finally/try-with-resources.
- [x] 1.2 Add diff type validation in `StreamingSnapdiffParser`: throw `IllegalArgumentException` for records with `type` not in `{added, modified, deleted}`. Match existing `SnapdiffParser` validation behavior.
- [x] 1.3 Mark existing `SnapdiffParser.parse(Path)` as `@Deprecated` with Javadoc noting `StreamingSnapdiffParser` as replacement for large files. No behavioral change to existing method.
- [x] 1.4 Add unit tests for `StreamingSnapdiffParser`: test valid file (consumer invoked N times), empty diffs array (0 invocations), invalid diff type (throws), resource cleanup on success and error.

## 2. File-Based Burst Computation

- [x] 2.1 Create `BurstDataFile` utility class in `com.anomalydetection.features` that manages a temp file for `(epochSeconds, opType)` pairs. Implement: `create()` using `Files.createTempFile()`, `write(long epochSeconds, String opType)` writing tab-separated line, `sortAndRead()` reading all lines, parsing, sorting by epochSeconds ascending, and returning sorted list. Implement `Closeable` to delete temp file in `close()`.
- [x] 2.2 Implement sliding window computation in `BurstDataFile` (or a companion class): given the sorted list of `(epochSeconds, opType)` pairs, compute `peak_burst_velocity` (max ops in any 300-second window / hours) and `burst_mod_purity` (modified count / total ops in densest window). Use the same two-pointer algorithm from current `RansomwareFeatureExtractor`.
- [x] 2.3 Add unit tests for `BurstDataFile`: test write+sort with unsorted input, duplicate timestamps preserved, fewer than 2 records returns (0.0, 0.0), cleanup on close, cleanup on exception.

## 3. Streaming Feature Extraction

- [x] 3.1 Add `extractFromFile(Path filePath)` method to `RansomwareFeatureExtractor`. Internally: create `StreamingSnapdiffParser`, create `BurstDataFile`, stream records through a callback that accumulates features 0-6, 8-10, 12 as counters/sets/maps AND writes `(epochSeconds, opType)` to `BurstDataFile`. After streaming, call `BurstDataFile.sortAndRead()` to compute features 7 and 11. Assemble all 13 features into `RansomwareFeatureVector`. Close `BurstDataFile` in finally block.
- [x] 3.2 Implement the streaming accumulator callback: for each `SnapdiffRecord`, update `totalOps`, `modifiedCount`, `deletedCount`, `sumModifiedSizes`, `bytesDeletedTotal`, `modifiedLogSizes` (for std dev), `perExtModified` (for file_type_concentration), `directories` set, `extensionsFound` set, `highValueExtCount`. These are the same accumulations as the current in-memory loop but done per-record in the callback.
- [x] 3.3 Ensure `extractFromFile()` produces numerically identical results to `extract(SnapdiffFile)`. Write a test that creates a temp JSON file from `SnapdiffRecord` objects, calls both methods, and asserts element-by-element equality (tolerance 1e-9) on the resulting vectors.
- [x] 3.4 Add test for streaming extraction on empty file (all 13 features = 0.0).
- [x] 3.5 Add test for streaming extraction with large synthetic file (e.g., 100K records) verifying no OOM and correct feature values.

## 4. Streaming Signature Detection

- [x] 4.1 Add `scanStream(Path filePath, SuspiciousExtensions suspiciousExtensions)` method to `RansomwareSignatureDetector`. Use `StreamingSnapdiffParser` to process records one-by-one, checking path against suspicious extensions and ransom note patterns on-the-fly. Return `SignatureResult` without buffering records.
- [x] 4.2 Add tests for `scanStream()`: test suspicious extension detection, ransom note detection, clean file returns no matches, results identical to `scan(List<SnapdiffRecord>)` on same data.

## 5. Detector Integration

- [x] 5.1 Add `detectFromFile(Path filePath)` method to `RansomwareDetector` that combines streaming extraction + streaming signature scan in a single file read pass. First do streaming signature check (fast pre-check), if matched return immediately; otherwise do streaming feature extraction and statistical scoring.
- [x] 5.2 Add test verifying `detectFromFile()` produces same `DetectionResult` (score, threshold, anomaly flag, signature match) as `detect(vector, records)` on identical input data.

## 6. CLI Integration

- [x] 6.1 Update `RansomwareDetectorCli` to use `extractFromFile(Path)` and `detectFromFile(Path)` for both baseline and input processing. Remove direct calls to `SnapdiffParser.parse()` and `extractor.extract(SnapdiffFile)`.
- [x] 6.2 Update `WeightOptimizerCli` similarly to use streaming extraction if it processes large files.
- [x] 6.3 Run full test suite (`mvn test`) to verify no regressions. All existing tests must pass since the in-memory API is preserved (deprecated).
- [x] 6.4 Run existing benchmark (`IntermittentEncryptionBenchmark`) to verify streaming path produces identical detection results to the in-memory path.
