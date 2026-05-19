## Why

The snap diff test generator currently assigns random timestamps to deleted records, which doesn't reflect real NetApp snap diff behavior where deleted files have no meaningful change time. Additionally, the generated diffs lack POSIX-compliant parent directory mtime updates — when files are added or deleted, the parent directory's mtime should reflect the modification. These two fixes will make test data more realistic and improve the reliability of feature extraction under real-world conditions.

## What Changes

- Deleted records in generated snap diff output will use `Instant.MAX` (`+1000000000-12-31T23:59:59.999999999Z`) as the `change_time` sentinel instead of random timestamps, indicating no meaningful timestamp for deletions.
- When files are added or deleted in a directory, an additional "modified" diff entry for the parent directory will be generated with its mtime updated to the operation time, following POSIX semantics.
- The `SnapdiffRecord` parser will recognize `Instant.MAX` as a sentinel for "no valid timestamp" (analogous to the existing `Instant.EPOCH` sentinel), so downstream feature extraction excludes deleted records from time-based calculations.
- The `RansomwareFeatureExtractor` will treat `Instant.MAX` the same as `Instant.EPOCH` — records with this sentinel are excluded from burst/temporal/wall-clock features but still counted in operation totals and type ratios.

## Capabilities

### New Capabilities
- `deleted-sentinel-time`: Defines how deleted records use `Instant.MAX` as a sentinel `change_time` value across generator, parser, and feature extractor
- `posix-directory-mtime`: Generates parent directory "modified" entries when children are added or deleted, following POSIX mtime semantics

### Modified Capabilities

## Impact

- **Generator** (`FilesystemState.java`, `AttackGenerator.java`): All deleted record creation sites must emit `Instant.MAX.toString()` as `change_time`. All add/delete sites must emit parent directory modified entries.
- **Parser** (`SnapdiffRecord.java`): Constructor must map `Instant.MAX` to a recognized sentinel (stored as `Instant.MAX`, filtered downstream).
- **Feature Extractor** (`RansomwareFeatureExtractor.java`): Time-filtering logic must exclude `Instant.MAX` alongside `Instant.EPOCH` from time-based features.
- **Streaming path** (`BurstDataFile.java`, `StreamingSnapdiffParser.java`): No changes needed — they delegate to `SnapdiffRecord` which handles the sentinel.
- **Test data**: All generated JSON files will reflect the new behavior. Existing benchmark results may shift because deleted records no longer contribute random timestamps to burst/temporal features.
