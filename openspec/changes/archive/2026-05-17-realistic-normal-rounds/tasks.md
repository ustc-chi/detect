## 1. Enhance evolveNormalRound() with Renames

- [x] 1.1 Add rename operation generation to `evolveNormalRound()` in `FilesystemState.java`: allocate 5–15% of total operations as rename pairs (delete old + add new with versioning suffix from `_v2`, `_v3`, `_new`, `_backup`, `_old`, `_final`, `_copy`, `_draft`), preserving original extension and user index. Renames select files from the active user set, same as modify operations.
- [x] 1.2 Verify rename pairs produce correct DiffEntry records: one `deleted` entry (with `Instant.MAX` timestamp) and one `added` entry (with timestamp within the hoursWindow). Verify filesystem state is updated (old path removed, new path added).

## 2. Add After-Hours Timestamps

- [x] 2.1 Modify `evolveNormalRound()` to allocate 15–25% of operation timestamps to after-hours periods (hours 18–23 or 5–7 relative to dayStart). After-hours timestamps use the same size change distribution as business-hours operations.
- [x] 2.2 Verify that after-hours timestamps appear in generated rounds by inspecting the output DiffEntry list (check that some change_time values fall outside hours 8–17).

## 3. Add Activity Volatility

- [x] 3.1 Add three-tier activity level selection to `evolveNormalRound()`: quiet day (20% probability, 40% ops), normal day (60% probability, current behavior), busy day (20% probability, 200% ops with a concentrated mini-burst of 50–200 ops in 5–15 minutes).
- [x] 3.2 Verify that calling `evolveNormalRound()` 10+ times produces operation counts with coefficient of variation ≥ 0.25 (confirming natural variation).

## 4. Regenerate Benchmark Data

- [x] 4.1 Run `mvn clean package -q` to verify the project compiles with all changes
- [x] 4.2 Run `BenchmarkDataGenerator` to regenerate `benchmark-data/` directory with the enhanced normal rounds
- [x] 4.3 Run the full benchmark: `java -cp target/rcf-snapdiff-anomaly-detector-1.0.jar com.anomalydetection.generator.IntermittentEncryptionBenchmark` and verify: attack detection rate ≥ 99%, vanilla normal FP rate = 0%

## 5. Update Documentation

- [x] 5.1 Update `README.md` normal round description to document the new activity patterns (renames, after-hours, volatility) in the "Phase 1a/1c: Normal Rounds" section
- [x] 5.2 Update `benchmark.md` to stay consistent with README changes (per README ↔ benchmark.md sync requirement)

## Final Verification Wave

- [x] F1 Code quality review: verify no TODOs, stubs, hardcoded values, or scope creep in changed files
- [x] F2 Benchmark verification: full benchmark passes with ≥99% detection, 0% vanilla FP, no runtime errors
- [x] F3 Documentation sync: README and benchmark.md are consistent in their descriptions of normal round patterns
- [x] F4 Architecture review: changes are localized to `evolveNormalRound()` and `BenchmarkDataGenerator`; no changes to feature extraction, detection, or attack generation
