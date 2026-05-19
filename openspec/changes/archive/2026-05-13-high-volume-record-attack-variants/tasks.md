## 1. Attack Generator Methods

- [x] 1.1 Add `generateMassiveModifiedFlood(Instant, double)` to AttackGenerator — 50K–70K "modified" records, +2-4% size, 300–600s burst, all 10 users
- [x] 1.2 Add `generateMassiveAddFlood(Instant, double)` to AttackGenerator — 30K–50K "added" records with randomized filenames, 300–600s burst
- [x] 1.3 Add `generateMassiveDeleteFlood(Instant, double)` to AttackGenerator — 20K–40K "deleted" records, 180–300s burst, remove from FilesystemState
- [x] 1.4 Add `generateBalancedHighVolumeMix(Instant, double)` to AttackGenerator — 60K–80K total records (40% mod / 30% add / 30% del), 600–1200s window
- [x] 1.5 Add `generateMultiWaveEscalation(Instant, double)` to AttackGenerator — 3 waves (10K–15K, 20K–30K, 40K–50K modified), 300s per wave, 1800s gaps
- [x] 1.6 Add `generateAddedHeavyEncryption(Instant, double)` to AttackGenerator — 40K–60K "added" + 40K–60K "deleted" pairs, 600–900s window
- [x] 1.7 Add `generateDeleteHeavyDestruction(Instant, double)` to AttackGenerator — 30K–50K "deleted" + 5K–10K shrunk "modified" (-20% to -50%), 300–600s burst
- [x] 1.8 Add `generateBaselineMimickingVolume(Instant, double)` to AttackGenerator — 30K–40K total (50% mod / 25% add / 25% del), spread over 6–8h

## 2. Benchmark Integration

- [x] 2.1 Add `dispatchHighVolume()` method to IntermittentEncryptionBenchmark — switch dispatch for all 8 C-variant method names
- [x] 2.2 Add Phase 2.7 block in main() — iterate C1–C8 variant definitions × PADDING_LEVELS, generate JSON files, add to attackTestCases list with appropriate labels
- [x] 2.3 Update total test case count prints — ensure Phase 2.7 output shows correct cumulative count (129 total)
- [x] 2.4 Verify Phase 3 (weight scan) automatically includes new high-volume cases — no code change needed, just confirm attackTestCases list feeds into scan loop

## 3. Verification

- [x] 3.1 Run `mvn compile` to verify no compilation errors
- [x] 3.2 Run `mvn test` to verify existing tests still pass
- [x] 3.3 Run IntermittentEncryptionBenchmark.main() and verify all 129 attack cases are generated and scored (24 new C-variants detected)
