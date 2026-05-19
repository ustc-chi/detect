## Context

The IntermittentEncryptionBenchmark generates 151 snapdiff JSON files from a simulated 300K-file filesystem every run, then parses them all back to extract features and run detection. This takes 20+ minutes. The bottleneck analysis (3 parallel codebase investigations) identified:

- **~60% of time**: Data generation + JSON serialization (Phase 1-2.8) — creating FilesystemState, generating attacks, writing 151 JSON files with INDENT_OUTPUT
- **~20% of time**: JSON deserialization — parsing 151 files back via deprecated bulk `SnapdiffParser`
- **~15% of time**: FilesystemState snapshot/restore — 204 deep copies of a 300K-element LinkedHashMap (61M FileInfo objects GC'd)
- **~5% of time**: Detection phases (already fast, operates on cached vectors)

The randomness is deterministic (seed=42), so pre-generated data is byte-identical across runs.

## Goals / Non-Goals

**Goals:**
- Reduce benchmark runtime from 20+ minutes to under 5 minutes
- Maintain identical detection accuracy: 102/102 attack detection, 0/24 vanilla normal FP
- Pre-generate snapdiff test data once, commit to repo, load at benchmark runtime
- Eliminate redundant parsing (triple-parse in RansomwareDetector) and temp-file I/O (BurstDataFile)
- Optimize streaming parser with shared ObjectMapper and larger buffers
- Add per-phase timing instrumentation for ongoing monitoring

**Non-Goals:**
- Changing detection algorithm, weights, threshold calculation, or feature extraction logic
- Changing test case definitions (attack types, padding levels, counts)
- Optimizing the detection phases (already fast)
- Adding new test cases or attack types
- Changing CLI interface or production parsing behavior
- Parallelizing attack generation (unnecessary with pre-generation)

## Decisions

### Decision 1: Pre-generate files as JSON on disk (not in-memory only)

**Choice**: Persist 151 snapdiff JSON files to `benchmark-data/` directory with a MANIFEST.json index.

**Alternatives considered**:
- *In-memory only*: Skip JSON entirely, generate DiffEntry lists and convert directly to SnapdiffFile objects. Fastest possible but doesn't exercise the parser — we want to validate the full pipeline.
- *Compressed archive*: Store as .tar.gz to save disk. Rejected — adds decompression overhead and complexity for ~265 MB (acceptable repo size).

**Rationale**: User explicitly chose JSON persistence. It validates the real parsing pipeline while eliminating the expensive generation phase. The ~265 MB is acceptable for a git-tracked benchmark dataset.

### Decision 2: Single-pass fan-out instead of multi-consumer chaining

**Choice**: Parse the file once, collect all records into an ArrayList, then feed to both signature detector and feature extractor sequentially.

**Alternatives considered**:
- *Dual Consumer pattern*: Stream to two consumers simultaneously via a composite Consumer. Rejected — more complex, no measurable speed difference since both consumers need all records anyway.
- *Keep triple-parse*: Simplest but wasteful. Each parse re-opens the file, re-tokenizes the JSON, and re-creates all SnapdiffRecord objects.

**Rationale**: Collecting into an ArrayList then processing sequentially is simpler than dual-consumer, eliminates 2 out of 3 file opens + parses, and keeps code readable.

### Decision 3: InMemoryBurstAccumulator replaces BurstDataFile (not in-place modification)

**Choice**: Create a new class `InMemoryBurstAccumulator` with the same API as `BurstDataFile` (`create()`, `write()`, `computeBurstFeatures()`, `close()`), backed by an ArrayList instead of a temp file.

**Alternatives considered**:
- *Modify BurstDataFile in-place*: Add an in-memory mode. Rejected — the class has temp-file semantics baked in; adding modes makes it harder to understand.
- *Use the existing in-memory `extract(SnapdiffFile)` path*: The non-streaming path already does burst computation in memory. Rejected — we want to keep the streaming architecture, just remove the disk I/O.

**Rationale**: Drop-in replacement with identical API. The `computeBurstFeatures()` algorithm is copied verbatim — same ArrayList sort, same sliding window, same calculations. Zero behavioral difference, zero disk I/O.

### Decision 4: Shallow copy-on-write for FilesystemState

**Choice**: Make `FileInfo` immutable (final fields, no setters). Mutations create new `FileInfo` objects and update the map entry. `snapshot()` returns `new LinkedHashMap<>(files)` (shallow copy of references). `restore()` replaces `this.files` with a shallow copy of the snapshot.

**Alternatives considered**:
- *Deep copy (current)*: 300K new FileInfo objects per snapshot. Safe but slow (61M objects GC'd across 204 snapshots).
- *Copy-on-write wrapper*: Wrap the map in a COW structure. Rejected — over-engineered for a single-use data generator.
- *Skip snapshots entirely*: Generate attack diffs without mutating state. Rejected — requires significant AttackGenerator refactoring that changes data generation logic.

**Rationale**: Immutable FileInfo is a clean pattern. Since the benchmark no longer generates data at runtime (after T6), this optimization primarily benefits the one-time BenchmarkDataGenerator. But it's still a worthwhile improvement for anyone re-running data generation.

### Decision 5: Shared static ObjectMapper and JsonFactory in StreamingSnapdiffParser

**Choice**: `private static final ObjectMapper` and `private static final JsonFactory` initialized once in the class.

**Alternatives considered**:
- *Instance fields*: Create per constructor call (current). Wasteful — ObjectMapper and JsonFactory are thread-safe heavy objects with internal caches.
- *Dependency injection*: Accept ObjectMapper as constructor parameter. Good pattern but overkill here — the parser always needs the same config.

**Rationale**: Jackson docs explicitly state ObjectMapper is thread-safe for read operations. The parser always uses the same config (JavaTimeModule, no FAIL_ON_UNKNOWN_PROPERTIES). Sharing avoids per-call allocation of serializers, deserializers, and type caches.

## Risks / Trade-offs

**[Risk] Pre-generated data drift**: If attack generation logic changes, `benchmark-data/` must be regenerated and re-committed.
→ **Mitigation**: MANIFEST.json records the generator version/seed. BenchmarkDataGenerator can be re-run anytime. Add a comment in benchmark code: "If detection results change unexpectedly, regenerate data."

**[Risk] ~265 MB in git repo**: Large binary-ish files in version control.
→ **Mitigation**: JSON is text and compresses well with git. If it becomes a problem, git LFS is a fallback. The data only changes when attack generation logic changes.

**[Risk] Shallow copy correctness**: If any code path mutates FileInfo in-place after snapshot, both the snapshot and the live state will be corrupted.
→ **Mitigation**: Make FileInfo fields final. All mutation sites must create new objects. The AuditGenerator and gen* methods in the benchmark are the only mutation sites — audit them all.

**[Risk] Detection accuracy regression**: Any optimization that changes the data pipeline could subtly affect feature values and detection results.
→ **Mitigation**: Full benchmark verification run (T9) with explicit checks: 102/102 attacks, 0/24 FP. This is the gate — no commit without passing.
