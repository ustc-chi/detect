## Why

The current benchmark's 105 attack cases all operate within or near the baseline record-count envelope (4,700–25,600 ops/round). No variant explicitly tests scenarios where the attacker produces an abnormally high number of total, modified, added, or deleted records — far exceeding the baseline's maximum. This is a critical detection gap: real ransomware families (e.g., LockBit 4.0, BlackCat, Play) often encrypt 50K–200K files in a single snapshot window, producing record volumes 3–10× the normal baseline maximum. Without high-volume attack test cases, we cannot verify that the detector reliably flags operations when `total_operations_normalized` deviates 3–10σ above baseline, or that `per_type_entropy`, `modification_ratio`, and related features remain discriminative at extreme record volumes.

## What Changes

- Add 8 new high-volume attack variant generators (C1–C8) to `AttackGenerator`, each producing 30K–80K+ records per round
- Add 24 new attack test cases (8 variants × 3 padding levels) to `IntermittentEncryptionBenchmark` as Phase 2.7
- New variants cover: massive modified flood, massive add flood, massive delete flood, balanced high-volume mix, multi-wave escalation, added-heavy encryption, delete-heavy destruction, and baseline-mimicking volume
- Each variant is designed to push one or more record-type counters (total, modified, added, deleted) significantly above baseline maximum
- All new cases integrate into the existing Phase 3 (weight scan) and Phase 4 (detailed results) benchmark flow

## Capabilities

### New Capabilities
- `high-volume-record-attacks`: 8 new attack generator methods (C1–C8) producing 30K–80K+ DiffEntry records, each targeting a specific record-type dimension (total, modified, added, deleted) to exceed baseline maximums

### Modified Capabilities
- `adversarial-attack-variants`: Benchmark SHALL include the new Phase 2.7 (24 high-volume cases) alongside existing Phase 1–2.5 test cases, reporting detection results for all 129 total attack cases
- `redesigned-attack-rounds`: AttackGenerator SHALL expose 8 new high-volume generator methods with the same signature pattern as existing methods (Instant attackTime, double paddingRatio)

## Impact

- `src/main/java/com/anomalydetection/generator/AttackGenerator.java`: 8 new public methods
- `src/main/java/com/anomalydetection/generator/IntermittentEncryptionBenchmark.java`: Phase 2.7 block, updated total counts
- No changes to detection logic, feature extraction, or scoring — only test infrastructure
- Benchmark execution time may increase due to larger record volumes (30K–80K vs current 500–6000)
- No API or dependency changes
