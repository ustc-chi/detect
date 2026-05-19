## 1. Deleted Record Sentinel Time

- [x] 1.1 In `FilesystemState.java`, change `evolveNormalRound()` (line ~111): replace the random `changeTime` for deleted entries with `java.time.Instant.MAX.toString()`
- [x] 1.2 In `FilesystemState.java`, change `evolveLogRotation()` (line ~205): replace random `changeTime` for deleted entries with `Instant.MAX.toString()`
- [x] 1.3 In `FilesystemState.java`, change `evolveCleanupPurge()` (line ~434): replace random `changeTime` for deleted entries with `Instant.MAX.toString()`
- [x] 1.4 In `FilesystemState.java`, change `evolveMigrationWave()` (line ~407): replace random `changeTime` for deleted entries with `Instant.MAX.toString()`
- [x] 1.5 In `FilesystemState.java`, change `evolveBatchCompile()` (line ~172): replace random `changeTime` for deleted entries with `Instant.MAX.toString()` if any deletions are generated
- [x] 1.6 In `AttackGenerator.java`, update `generateNormalPadding()`: replace random `changeTime` for deleted entries with `Instant.MAX.toString()`
- [x] 1.7 In `AttackGenerator.java`, scan all `generate*()` methods for any deleted entries and update them to use `Instant.MAX.toString()`
- [x] 1.8 Verify `SnapdiffRecord.java` constructor correctly parses `Instant.MAX` ISO-8601 string via `Instant.parse()` without modification

## 2. POSIX Directory mtime Entries

- [x] 2.1 Add a static utility method `generateDirectoryMtimeEntries(List<DiffEntry> fileDiffs)` in `FilesystemState.java` that: (a) extracts parent directory paths from "added" and "deleted" entries, (b) groups by unique parent, (c) determines change_time per directory (max child time for additions-only, `Instant.MAX` if any deletion present), (d) returns list of "modified" DiffEntry with size="0"
- [x] 2.2 Update `FilesystemState.evolveNormalRound()` to call the utility and append directory entries to the result
- [x] 2.3 Update `FilesystemState.evolveLogRotation()` to call the utility and append directory entries
- [x] 2.4 Update `FilesystemState.evolveCleanupPurge()` to call the utility and append directory entries
- [x] 2.5 Update `FilesystemState.evolveMigrationWave()` to call the utility and append directory entries
- [x] 2.6 Update `FilesystemState.evolveBackupSurge()` to call the utility and append directory entries
- [x] 2.7 Update `FilesystemState.evolveMassRename()` to call the utility and append directory entries
- [x] 2.8 Update `FilesystemState.evolveDbCheckpoint()` to call the utility and append directory entries
- [x] 2.9 Update `FilesystemState.evolveAfterHoursBurst()` to call the utility and append directory entries
- [x] 2.10 Update `FilesystemState.evolveBatchCompile()` to call the utility and append directory entries
- [x] 2.11 Update all `AttackGenerator.generate*()` methods to call the utility and append directory entries

## 3. Feature Extractor Instant.MAX Handling

- [x] 3.1 In `RansomwareFeatureExtractor.java`, update the `changeTime` validity check in `extract()` method (line ~106): change `!time.equals(Instant.EPOCH)` to `!time.equals(Instant.EPOCH) && !time.equals(Instant.MAX)` so that Instant.MAX records are excluded from `opRecords` and `epochSec` is set to `-1L`
- [x] 3.2 In `RansomwareFeatureExtractor.java`, update `computeWallClockAnomaly()` (line ~60): add `&& !time.equals(Instant.MAX)` to the existing `!time.equals(Instant.EPOCH)` check
- [x] 3.3 Verify that `BurstDataFile.java` and `StreamingSnapdiffParser.java` correctly handle Instant.MAX without changes (they delegate to SnapdiffRecord which handles parsing)

## 4. Verification

- [x] 4.1 Run `mvn compile` to ensure all changes compile without errors
- [x] 4.2 Run `mvn test` to verify existing tests pass
- [x] 4.3 Run `IntermittentEncryptionBenchmark` to verify detection accuracy remains at 100% with no new false positives on vanilla normal rounds
