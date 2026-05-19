## Context

The IntermittentEncryptionBenchmark currently tests 105 attack cases across 5 phases (ORIG, intermittent A1-A12, adversarial B1-B8, after-hours). All attack generators produce 500–6000 DiffEntry records per round, which falls within or near the baseline envelope of 4,700–25,600 ops/round. The `total_operations_normalized` feature (index 1) is weighted at 2.5 and measures ops/day — at baseline max (25,600 ops / 2 days = 12,800 ops/day), a 6000-op attack would only deviate ~0.5σ. Real ransomware families routinely encrypt 50K–200K files per snapshot window, producing 3–10× baseline maximum record counts.

The existing `AttackGenerator` class provides a well-established pattern: each attack method takes `(Instant attackTime, double paddingRatio)`, uses `computePadding()` for noise injection, and returns `List<DiffEntry>`. The `IntermittentEncryptionBenchmark` dispatch table pattern (`dispatchAttack`, `dispatchAdversarial`) makes adding new phases straightforward.

## Goals / Non-Goals

**Goals:**
- Add 8 high-volume attack variants (C1–C8) that produce 30K–80K+ records per round
- Each variant specifically targets one record-count dimension (total, modified, added, deleted) to exceed baseline maximum by 2–5×
- Integrate into benchmark as Phase 2.7 with 24 new test cases (8 × 3 padding levels)
- Verify the 12-feature detector still catches all high-volume variants despite potential dilution from padding

**Non-Goals:**
- Changing detection logic, feature extraction, or scoring weights
- Optimizing benchmark performance for larger record volumes
- Adding new features or modifying existing feature calculations
- Handling scenarios where baseline itself contains high-volume rounds

## Decisions

### 1. Variant Design: Record-Count Dimension Targeting

Each of the 8 variants targets a specific dimension where record counts exceed baseline:

| ID | Name | Records | Primary Dimension | Behavior |
|----|------|---------|-------------------|----------|
| C1 | Massive Modified Flood | 50K-70K modified | modified count | Encrypts files across all users in 5-10min, +2-4% size |
| C2 | Massive Add Flood | 30K-50K added | added count | Creates new encrypted copies, leaves originals intact |
| C3 | Massive Delete Flood | 20K-40K deleted | deleted count | Destructive wipe — deletes files in rapid burst |
| C4 | Balanced High-Volume Mix | 60K-80K total | total count | ~40% mod / 30% add / 30% delete, mimics enterprise migration |
| C5 | Multi-Wave Escalation | 3 waves × 10K-15K | total count | Starts near baseline, each wave 2× larger, 30min gaps |
| C6 | Added-Heavy Encryption | 40K-60K added | added count | New encrypted files with randomized names (delete originals) |
| C7 | Delete-Heavy Destruction | 30K-50K deleted | deleted count | Delete + shrink remaining, simulates wiper malware |
| C8 | Baseline-Mimicking Volume | 30K-40K total | total count | Exactly 1.5-2× baseline max, spread over 6-8h, mimics backup timing |

**Rationale**: Covering all four record-type dimensions (total, modified, added, deleted) ensures the detector is stress-tested for each feature's discriminative power at extreme volumes. C5 tests escalation detection. C8 tests volume that's only moderately above baseline but with timing designed to mimic normal patterns.

**Alternative considered**: Random combinations of high volumes — rejected because it doesn't systematically cover the feature space.

### 2. Padding Integration

All 8 variants use the existing `computePadding()` and `generateNormalPadding()` pattern. At p70 padding with 50K attack ops, total round size can reach 150K+ records. The padding ratio still works correctly: it's a proportion of total, so 70% padding means adding ~117K normal ops to the 50K attack ops.

**Rationale**: Consistency with existing attack pattern. The `computePadding()` function handles minimum-total-floor logic.

### 3. Dispatch Table Extension

Add a new `dispatchHighVolume()` method to `IntermittentEncryptionBenchmark` following the same pattern as `dispatchAdversarial()`. Add a Phase 2.7 block that iterates over the 8 variant definitions × 3 padding levels.

**Rationale**: Minimal code change, consistent with existing architecture.

### 4. FilesystemState Considerations

The simulated filesystem has 300K files. For variants that produce 50K+ modifications, we need to ensure the file pool is sufficient. Since `AttackGenerator` picks files randomly with replacement from `state.getAllFiles()`, and the same file can be modified multiple times (generating multiple DiffEntry records), the 300K pool is adequate.

**Rationale**: No filesystem scaling needed — random sampling with replacement handles high-volume naturally.

## Risks / Trade-offs

- **[Benchmark execution time]** → Generating 80K+ records per round with padding may slow benchmark significantly. Mitigation: acceptable since benchmark runs offline; no real-time requirement.
- **[Memory pressure]** → 150K+ DiffEntry objects per round may increase heap usage. Mitigation: Java GC handles short-lived objects well; temp files are deleted after scoring.
- **[Detection dilution from padding]** → At p70 with 50K attack ops, padding adds ~117K normal ops. The 12-feature system may struggle to distinguish attack signal within 3.3:1 noise ratio. Mitigation: this is exactly what we want to test — if the detector catches it, confidence increases; if not, we identify a real weakness.
- **[FilesystemState mutation]** → High-volume variants modify/destroy many files. Mitigation: existing `snapshot()/restore()` pattern in benchmark handles this correctly.
