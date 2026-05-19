## Context

The ransomware detection system processes NetApp snapdiff JSON files to extract 13 statistical features. The current pipeline:

1. `SnapdiffParser.parse(Path)` uses Jackson `ObjectMapper.readValue()` to load the **entire** JSON file into a `SnapdiffFile` containing a `List<SnapdiffRecord>`.
2. `RansomwareFeatureExtractor.extract(SnapdiffFile)` iterates the in-memory list, accumulating counters, sets, and a `List<OpRecord>` of all `(epochSeconds, opType)` pairs.
3. After iteration, it **sorts `List<OpRecord>` in memory** to compute `peak_burst_velocity` (feature 7) and `burst_mod_purity` (feature 11) via a sliding window over 5-minute intervals.
4. `RansomwareSignatureDetector.scan(List<SnapdiffRecord>)` also requires the full list for signature pre-checks.
5. `RansomwareDetectorCli` wires this together: parse → extract → detect.

**Key constraint from user**: Records in snapdiff files are **not sorted by time**. No assumption of ordering should be made.

**Current memory bottleneck**: A snapdiff file with N records consumes O(N * record_size) heap. With millions of records, this causes OOM. The sort step for burst features adds another O(N * log N) memory pressure.

## Goals / Non-Goals

**Goals:**
- Eliminate OOM risk by streaming JSON parsing: only one `SnapdiffRecord` in memory at a time.
- Feature extraction for 11 of 13 features (indices 0-6, 8-10, 12) works as pure streaming accumulators — no structural change needed.
- Burst features (7, 11) use file-based external sort instead of in-memory sort.
- Signature detection works in streaming mode.
- Backward compatibility: existing `extract(SnapdiffFile)` API preserved (deprecated) for tests and small files.
- **No change to the 13 feature definitions, formulas, or output values.** Results must be numerically identical to the current implementation.

**Non-Goals:**
- Not changing the detection algorithm, scoring, threshold, or weights.
- Not optimizing JSON parsing speed — memory is the constraint, not CPU.
- Not changing the test data generator or benchmark infrastructure.
- Not implementing parallel file processing (single-threaded streaming is sufficient).

## Decisions

### Decision 1: Streaming Parser via Jackson `JsonParser` (not `ObjectMapper`)

**Choice**: Use Jackson's streaming `JsonParser` to read the `"diffs"` array one record at a time.

**Rationale**: `JsonParser` is already available (Jackson is a dependency). It provides token-by-token parsing with O(1) memory per record. No new dependencies needed.

**Alternative considered**: GSON streaming or manual character-by-character parsing — rejected because Jackson is already used throughout and its streaming API is well-tested.

**Implementation**: New class `StreamingSnapdiffParser` with method `parse(Path, Consumer<SnapdiffRecord>)` that opens a `BufferedInputStream`, creates a `JsonFactory.createParser()`, advances to the `"diffs"` array, and deserializes each element using `objectMapper.readValue(jsonParser, SnapdiffRecord.class)`.

### Decision 2: Callback-based extraction interface

**Choice**: New method `extractFromFile(Path, SuspiciousExtensions)` that internally creates a streaming parser and processes records via callback.

**Rationale**: The consumer pattern (`Consumer<SnapdiffRecord>`) lets the extractor process each record immediately during parsing without buffering. The method handles the full lifecycle: open file → stream records → accumulate features → write temp file for burst data → compute burst features → clean up.

**Alternative considered**: `Iterator<SnapdiffRecord>` — rejected because it requires the caller to manage the iteration lifecycle and error handling, which is error-prone and complicates the temp file cleanup.

### Decision 3: File-based external sort for burst features

**Choice**: During streaming pass, write each `(epochSeconds, opType)` pair as a line to a temp file (format: `<epochSeconds>\t<opType>`). After streaming completes, sort the temp file externally (read all lines, sort in memory — the temp file is much smaller than the original since it only stores 2 fields per record, not the full JSON object). Then compute the sliding window in a single pass over the sorted temp file.

**Rationale**: The temp file is ~20 bytes per record (`long` + `String` + delimiter) vs ~200-500 bytes per full JSON record. For a 10M-record file (5GB JSON), the temp file would be ~200MB — sortable in memory on any modern system. This is the simplest approach that solves the memory problem without requiring a true external merge sort.

**Alternative considered**: True external merge sort with chunked writes and k-way merge — rejected as over-engineering since the `(epochSeconds, opType)` pair is so much smaller than the full record that sorting the reduced dataset in memory is practical even for very large files.

**Fallback**: If the temp file itself is too large, a future iteration can implement true external sort. The interface is designed to allow this without API changes.

### Decision 4: Streaming signature detection

**Choice**: Add `scanStream(Path, SuspiciousExtensions)` method to `RansomwareSignatureDetector` that streams records and checks signatures on-the-fly. Returns `SignatureResult` without needing the full record list.

**Rationale**: Signature detection only needs to check path strings against known patterns — purely stateless per-record. No accumulation needed beyond tracking matches. This means the CLI can do signature pre-check in the same streaming pass as feature extraction.

**Implementation**: The streaming extraction method will accept an optional `SignatureResult.Builder` that collects signature matches during the same pass.

### Decision 5: Single-pass extraction with deferred burst computation

**Choice**: The extraction pipeline will be:
1. **Pass 1 (streaming)**: Read JSON file record-by-record. Accumulate all counters, sets, and maps for features 0-6, 8-10, 12. Write `(epochSeconds, opType)` pairs to temp file. Simultaneously check signatures.
2. **Pass 2 (temp file)**: Sort the temp file by `epochSeconds`. Compute `peak_burst_velocity` (feature 7) and `burst_mod_purity` (feature 11) using the sliding window algorithm.
3. **Assembly**: Combine all feature values into `RansomwareFeatureVector`.

**Rationale**: Only 2 of 13 features need sorted data. Rather than complicating all features, isolate the burst computation into its own phase.

## Risks / Trade-offs

- **[Temp file disk usage]** → For extremely large files, the temp file could consume significant disk. Mitigation: temp file is ~4% of original file size (only 2 fields per record). Cleanup is guaranteed via try-with-resources. Document disk requirements in CLI help.
- **[Backward compatibility risk]** → Existing code references `extract(SnapdiffFile)`. Mitigation: Retain old method, mark `@Deprecated`, delegate to streaming internally by writing a temp SnapdiffFile for small inputs.
- **[Numerical equivalence]** → Streaming vs batch must produce identical feature values. Mitigation: Use the same accumulation logic. Test by running both paths on the same input and comparing vectors element-by-element.
- **[Test impact]** → Existing tests use `makeFile(SnapdiffRecord...)` which constructs `SnapdiffFile` in memory. Mitigation: Keep these tests. Add new tests that use file-based extraction with temp JSON files.
