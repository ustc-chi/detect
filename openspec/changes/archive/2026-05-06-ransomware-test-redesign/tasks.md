## 1. Feature Vector Expansion (11 → 13 features)

- [x] 1.1 Update `RansomwareFeatureVector.java`: change FEATURE_COUNT from 11 to 13, add `burstModPurity` and `fileTypeConcentration` fields, update FEATURE_NAMES array, add constructor parameter, add getters, update get() switch, update toArray()
- [x] 1.2 Verify RansomwareFeatureVector compiles and all existing tests pass with the new 13-feature dimensionality

## 2. Feature Extractor Refactoring

- [x] 2.1 Refactor timestamp tracking in `RansomwareFeatureExtractor.java`: replace `Set<Instant> validTimes` with a `List<OpRecord>` inner class that stores both the operation type and timestamp. This enables both peak_burst_velocity and burst_mod_purity computation
- [x] 2.2 Implement `burst_mod_purity` computation: after identifying the peak 5-minute window (same sliding window as peak_burst_velocity), count modifications vs total ops in that window. Return mod_count / total_count (0.0 if window has <2 ops)
- [x] 2.3 Implement `file_type_concentration` computation: during the single-pass iteration, track a `Map<String, Integer>` of per-extension modification counts. Return max(count) / total_modified_count (0.0 if no modifications)
- [x] 2.4 Update the `extract()` return statement to build a 13-element double array including the two new feature values at indices 11 and 12
- [x] 2.5 Verify feature extractor produces correct 13-feature vectors for empty input, single-operation input, and multi-operation input with mixed types

## 3. Weight Rebalancing

- [x] 3.1 Update `WeightedEuclideanScorer.java`: change N from 11 to 13, replace DEFAULT_WEIGHTS array with the 13 rebalanced weights (1.0, 3.0, 0.5, 0.5, 1.5, 0.8, 10.0, 5.0, 1.0, 4.0, 2.5, 3.0, 2.0)
- [x] 3.2 Verify WeightedEuclideanScorer.score() correctly processes 13-feature vectors with the new weights

## 4. Attack Generator Rewrite

- [x] 4.1 Implement `generateLockBitFastMode(Instant)` — 4000-6000 files, +4KB fixed size, no ext change, 90-120s burst
- [x] 4.2 Implement `generateContiSizeTiered(Instant)` — file-size-based tiers (small +3-5%, medium +4-8KB, large +1-3%), no ext change, 300-600s window
- [x] 4.3 Implement `generateExtensionPreservingMass(Instant)` — 3000-5000 files across all users, +2-4%, no ext change, 180-300s burst
- [x] 4.4 Implement `generateDatabasePriority(Instant)` — phase 1: all .db/.sql/.mdb/.bak/.csv, phase 2: all .docx/.xlsx/.pdf, no ext change, 300s window
- [x] 4.5 Implement `generateSingleUserRapid(Instant)` — one random user's files, +2-5%, no ext change, 30-60s burst
- [x] 4.6 Implement `generateSlowDistributed(Instant)` — 2000 files, +2-4%, micro-bursts of ~100 ops in 10s windows with 60-90s gaps, 30-min total window
- [x] 4.7 Implement `generateCreepingShrink(Instant)` — 2500 files, -10% to -20% size, no ext change, 300s burst
- [x] 4.8 Implement `generateRevilRandomExt(Instant)` — 2000-3000 files, random 8-char lowercase alphanumeric extension, +2-5%, 180s burst
- [x] 4.9 Implement `generateClopCompanion(Instant)` — 2000 files modified in-place (+1-3%), 2000 companion .key files added, 300s window
- [x] 4.10 Implement `generateWannaCryStaged(Instant)` — 2000-3000 files, .WNCRY extension, +2-3%, 300s window
- [x] 4.11 Implement `generateMassEncryption(Instant)` — 4000-6000 files, .lockbit extension, +1-4%, 120-180s burst (positive control)
- [x] 4.12 Implement `generateRansomNoteDrop(Instant)` — 20-40 ransom notes with known patterns, padded to ~2000 ops (positive control)
- [x] 4.13 Remove all 11 old attack methods from AttackGenerator (generateMassEncryption, generateDestructiveEncryption, etc.) — they are replaced by the 12 new methods

## 5. Test Generator Update

- [x] 5.1 Update `RansomwareTestGenerator.java`: change attack round positions to {4, 7, 10, 13, 16, 19, 22, 25, 28, 31, 34, 37} and attack types to the 12 new patterns
- [x] 5.2 Update the switch/case dispatch in the generate() loop to map each new attack type name to its corresponding AttackGenerator method
- [x] 5.3 Verify the generate() method still does snapshot/restore for state isolation between attack rounds

## 6. Integration and Validation

- [x] 6.1 Run `mvn clean compile` to verify all source changes compile cleanly with zero errors
- [x] 6.2 Run `mvn test` to verify all existing tests pass (tests may need updating for 13-feature dimensionality)
- [x] 6.3 Generate test data with `RansomwareTestGenerator` (seed=42) and verify 40 JSON files are produced with correct attack round labels
- [x] 6.4 Run the detector against generated test data and record detection results for all 12 attack rounds — 12/24 total (original + intermittent), 0/28 false positives
- [x] 6.5 Update README.md: feature table (11→13), weight table, attack pattern table (12 new patterns), and detection results
