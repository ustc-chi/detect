## ADDED Requirements

### Requirement: Eight adversarial attack generator methods
AttackGenerator SHALL implement 8 new methods for adversarial ransomware evasion techniques. Each method SHALL accept `(Instant attackTime, double paddingRatio)` and return `List<DiffEntry>`.

Methods:
1. `generateBackupDisguise` — 3000-5000 ops spread over 2-4 hours with uniform +2-4% size increase, mimicking scheduled backup timing
2. `generateSlowDripEncrypt` — ~50 ops every 5 minutes over 6 hours (~3600 total), keeping density below burst threshold in any 5-min window
3. `generateRandomJitterBurst` — 2000-3000 ops with 1-15s random jitter between operations and occasional 30-60s pauses
4. `generateMixedOperationMask` — 2000-3000 ops with 80% modified, 10% added, 10% deleted to keep modification_ratio below 0.9
5. `generateSizeMimicNormal` — 2000-3000 modified ops with per-file random size change (-10% to +10%) instead of uniform increase
6. `generateSelectiveHighValue` — 200-500 ops targeting only high-value extensions (.docx, .xlsx, .pdf, .db, .sql)
7. `generateMultiFamilyCombo` — 3000-5000 ops mixing two encryption strategies: 50% files get +2-3% uniform, 50% get +4-8KB append
8. `generateRenameAndEncrypt` — 2000-3000 ops that rename files to random names (destroying extension info) then encrypt with +1-4% size change

#### Scenario: Each method produces valid diff entries with padding
- **WHEN** any adversarial method is called with a valid attackTime and paddingRatio
- **THEN** it SHALL return a non-empty list of DiffEntry objects, with padding ops mixed in according to paddingRatio, and total ops >= MIN_TOTAL_OPS (5000)

#### Scenario: All methods use consistent signature
- **WHEN** any adversarial method is called
- **THEN** it SHALL accept exactly `(Instant, double)` parameters and return `List<DiffEntry>`, matching the existing 12 attack method signatures

### Requirement: Adversarial variants in benchmark
The IntermittentEncryptionBenchmark SHALL test all 8 adversarial variants at 3 padding levels (20%, 50%, 70%), producing 24 additional test cases.

#### Scenario: Benchmark runs adversarial variants
- **WHEN** IntermittentEncryptionBenchmark.main() is executed
- **THEN** it SHALL test 96 total attack cases (36 original + 36 intermittent + 24 adversarial)

#### Scenario: Adversarial results reported separately
- **WHEN** benchmark results are printed
- **THEN** adversarial variant results SHALL be labeled with variant name (B1-B8) and padding level
