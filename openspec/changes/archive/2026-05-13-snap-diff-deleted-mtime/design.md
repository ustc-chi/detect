## Context

The snap diff test data generator (`FilesystemState`, `AttackGenerator`) currently assigns random ISO-8601 timestamps to ALL records including deletions. In real NetApp snap diff output, deleted files have no meaningful change time — the deletion event has no associated timestamp. Downstream, the `RansomwareFeatureExtractor` already filters `Instant.EPOCH` timestamps out of time-based features (burst velocity, temporal uniformity, wall clock anomaly). The system has no concept of directory-level metadata or POSIX mtime propagation.

## Goals / Non-Goals

**Goals:**
- Make deleted records in generated test data use `Instant.MAX` as a sentinel `change_time`, accurately modeling real NetApp behavior where deletions carry no timestamp.
- Generate parent directory "modified" entries whenever files are added or deleted in a directory, following POSIX mtime semantics (directory mtime updates when its contents change).
- Ensure the parser and feature extractor correctly handle the `Instant.MAX` sentinel alongside the existing `Instant.EPOCH` sentinel.
- Maintain existing benchmark detection accuracy (105/105 attacks, 0/24 vanilla normal false positives).

**Non-Goals:**
- Changing the `SnapdiffRecord` data model (no new fields or types).
- Adding directory records as first-class entities in `FileInfo` — directories are only materialized as diff entries, not tracked in the filesystem state.
- Modifying the scoring formula or feature weights.
- Handling directory entries in the streaming parser path differently.

## Decisions

### D1: Sentinel value for deleted record change_time

**Decision**: Use `Instant.MAX` (`+1000000000-12-31T23:59:59.999999999Z`) as the sentinel.

**Rationale**: The existing codebase already uses `Instant.EPOCH` as a sentinel for "no valid timestamp." Using `Instant.MAX` for deletions is semantically appropriate ("infinite time" = "time is irrelevant for this deletion") and is a distinct value that won't collide with `Instant.EPOCH`. It's parseable by `Instant.parse()` in the `SnapdiffRecord` constructor, so no parser changes are needed for deserialization.

**Alternatives considered**:
- Empty string / null `change_time`: Would serialize as `"change_time": ""` or `"change_time": null`. This works but provides no semantic distinction at the parser level — both map to `Instant.EPOCH`, losing the "this was intentionally deleted" signal.
- Custom string like `"infinity"`: Would require parser changes to handle a non-ISO-8601 value. More explicit but adds parsing complexity.
- `Instant.EPOCH`: Already used for "missing/invalid." Reusing it for deletions conflates two different semantics (parse error vs. intentional deletion).

### D2: Parent directory mtime entries

**Decision**: After generating all file-level diff entries for a round, collect unique parent directories from added and deleted records, then emit one "modified" entry per directory with `change_time` set to the maximum timestamp among its child operations.

**Rationale**: POSIX specifies that a directory's mtime is updated when entries are added or removed from it. By emitting directory-level "modified" records, the generated test data accurately reflects real filesystem behavior. Using the max child timestamp ensures the directory mtime reflects the last change.

**Alternatives considered**:
- One directory entry per child operation: Would bloat the diff with thousands of near-duplicate directory entries.
- Per-directory entries with min child timestamp: Less semantically correct — mtime reflects the last modification, not the first.

### D3: Where to add directory mtime logic

**Decision**: Add a utility method `generateDirectoryMtimeEntries(List<DiffEntry> fileDiffs)` called at the end of each `evolve*` and `generate*` method in both `FilesystemState` and `AttackGenerator`.

**Rationale**: Centralizing the directory mtime generation in one utility avoids scattering POSIX logic across 20+ methods. Each method returns its file diffs, then the utility post-processes them to append directory entries.

**Alternatives considered**:
- Inline in each method: Error-prone, lots of duplication.
- In `SnapdiffOutput` serialization: Too late — feature extraction may happen before serialization.
- As a separate post-processing step in the test runner: Requires changing the test runner flow.

### D4: Feature extractor handling of Instant.MAX

**Decision**: Extend the existing `Instant.EPOCH` check to also exclude `Instant.MAX`. Change the condition from `!time.equals(Instant.EPOCH)` to `!time.equals(Instant.EPOCH) && !time.equals(Instant.MAX)`.

**Rationale**: `Instant.MAX` represents "no meaningful timestamp." Deleted records should be excluded from time-based features (burst velocity, temporal uniformity, inter-op CV, wall clock anomaly) just like EPOCH-timestamped records. This is a minimal, safe change that doesn't affect the feature computation logic.

## Risks / Trade-offs

- **Benchmark score shifts**: Since deleted records currently contribute random timestamps to burst/temporal features, removing them will change feature values for rounds that contain deletions. → Mitigation: Run benchmarks after implementation to verify detection accuracy remains at 100%.
- **Increased record count**: Adding directory "modified" entries increases total operations per round. This affects `total_operations_normalized` and `per_type_entropy`. → Mitigation: Directory entries are relatively few (unique directories) compared to file operations (thousands), so the impact is small.
- **`Instant.MAX` serializability**: `Instant.MAX.toString()` produces `+1000000000-12-31T23:59:59.999999999Z`. Verify that `Instant.parse()` round-trips this correctly in the target Java version. → Mitigation: Java 8+ supports this round-trip. Already verified in Java's `Instant` specification.
