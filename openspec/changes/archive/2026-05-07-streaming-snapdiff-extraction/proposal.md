## Why

Real-world snapdiff files can contain millions of records, far exceeding available heap memory. The current pipeline loads the entire JSON file into a `SnapdiffFile` object (via `ObjectMapper.readValue`) and then iterates the in-memory `List<SnapdiffRecord>`, which causes OOM for large files. Additionally, the feature extractor assumes records can be sorted in memory for burst-window calculations — but snapdiff records are **not sorted by time** in the file, so this sorting step itself requires holding all records in memory.

## What Changes

- Replace `SnapdiffParser.parse(Path)` with a streaming JSON parser (Jackson `JsonParser`) that reads one `SnapdiffRecord` at a time from the `"diffs"` array, never materializing the full list.
- Refactor `RansomwareFeatureExtractor` to accept a streaming callback (`Path`-based or `Iterator<SnapdiffRecord>`-based) instead of requiring a fully-loaded `SnapdiffFile`. Most features (indices 0–6, 8–10, 12) are accumulators that work naturally in a single streaming pass.
- For burst features (indices 7: `peak_burst_velocity`, 11: `burst_mod_purity`), write `(epochSeconds, opType)` pairs to a temporary file during streaming, then externally sort that file and compute the sliding window in a second pass — avoiding in-memory sort of all records.
- Refactor `RansomwareSignatureDetector.scan()` to work in streaming mode (callback per record) instead of requiring the full `List<SnapdiffRecord>`.
- Update `RansomwareDetectorCli` to use the new streaming pipeline end-to-end.
- **BREAKING**: `RansomwareFeatureExtractor.extract(SnapdiffFile)` will be replaced by `extractFromFile(Path)`. The old in-memory method will be retained for backward compatibility (tests, small files) but marked deprecated.

## Capabilities

### New Capabilities
- `streaming-snapdiff-parser`: Streaming JSON parser for snapdiff files that reads records one-at-a-time via Jackson `JsonParser`, yielding an `Iterator<SnapdiffRecord>` or callback-based interface. Never loads the full file into memory.
- `file-based-burst-computation`: File-based external sort and sliding-window computation for `peak_burst_velocity` and `burst_mod_purity`. Writes `(epochSeconds, opType)` pairs to a temp file during streaming pass, sorts externally, then computes burst metrics in a second pass.

### Modified Capabilities
- `ransomware-feature-extraction`: Feature extraction API changes from requiring `SnapdiffFile` (fully in-memory) to supporting `Path`-based streaming extraction. Most features remain single-pass accumulators; burst features deferred to file-based computation. The requirement "single iteration over diff records" is updated to "single streaming pass over the JSON file" with a second pass over the temp file for burst features only.
- `statistical-anomaly-detector`: `RansomwareDetector.detect()` and `RansomwareSignatureDetector.scan()` signatures updated to support streaming-mode invocation.

## Impact

- **Code**: `SnapdiffParser`, `RansomwareFeatureExtractor`, `RansomwareSignatureDetector`, `RansomwareDetector`, `RansomwareDetectorCli`, `WeightOptimizerCli`
- **API**: New `extractFromFile(Path)` method; old `extract(SnapdiffFile)` deprecated but preserved
- **Dependencies**: No new external dependencies (Jackson `JsonParser` already available)
- **Temp files**: Burst computation creates a small temp file (2 fields per record: `long` + `String`), cleaned up after use
- **Tests**: Existing tests continue to work via deprecated in-memory path; new tests needed for streaming extraction
