## Context

The existing 12 attack methods and 12 intermittent variants test standard ransomware behavior. However, sophisticated ransomware operators use evasion techniques specifically designed to defeat statistical anomaly detectors. The current detector relies on peak_burst_velocity (5-min window), modification_ratio, size_std_dev, burst_mod_purity, and other features — each of which can be targeted by adversarial strategies.

Current feature sensitivity:
- `peak_burst_velocity`: Defeated by spreading operations over time
- `burst_mod_purity`: Defeated by mixing operation types
- `modification_ratio`: Defeated by interleaving adds/deletes
- `size_std_dev`: Defeated by randomizing size changes
- `total_operations`: Defeated by encrypting few but high-value files
- `extension_diversity` / `high_value_ext_ratio`: Defeated by renaming files

## Goals / Non-Goals

**Goals:**
- Add 8 adversarial attack generator methods that each target a specific detector weakness
- Integrate all 24 adversarial cases (8 × 3 padding levels) into the benchmark
- Validate that the current detector catches all adversarial variants
- If any variant evades detection, identify which feature(s) need strengthening

**Non-Goals:**
- Changing weights or detector logic (that's a separate change if needed)
- Adding new features to the 13-feature vector
- Testing legitimate bulk operations (FP testing)

## Decisions

### Decision 1: Adversarial variant design philosophy

Each variant is designed to defeat **at least one** detector feature while still being a realistic ransomware attack. The key insight is: even if one feature is defeated, the remaining features should still trigger detection. This validates the multi-feature defense-in-depth approach.

### Decision 2: Variant implementation details

| Variant | Ops count | Time span | Key evasion mechanic |
|---------|-----------|-----------|---------------------|
| B1 backup_disguise | 3000-5000 | 2-4 hours | Uniform slow pace mimicking backup |
| B2 slow_drip_encrypt | ~3600 | 6 hours (50 ops / 5 min) | No dense 5-min window |
| B3 random_jitter_burst | 2000-3000 | 90-300s + jitter | Burst diluted by gaps |
| B4 mixed_operation_mask | 2000-3000 | 60-180s | 80% modify, 10% add, 10% delete |
| B5 size_mimic_normal | 2000-3000 | 60-180s | Random size delta per file |
| B6 selective_high_value | 200-500 | 30-120s | Only .docx/.xlsx/.pdf/.db/.sql |
| B7 multi_family_combo | 3000-5000 | 60-180s | 50% uniform + 50% append |
| B8 rename_and_encrypt | 2000-3000 | 60-180s | Random filenames destroy ext info |

### Decision 3: Integration into benchmark

Add a new Phase 2.5 in `IntermittentEncryptionBenchmark` between the existing intermittent variants and the velocity weight scan. The 24 adversarial cases are appended to `attackTestCases` and counted in the total. No changes to Phase 1 (60-round schedule) or Phase 3 (velocity scan).

### Decision 4: Method signatures

All 8 new methods follow the existing pattern: `public List<DiffEntry> generateXxx(Instant attackTime, double paddingRatio)`. This allows the benchmark's `dispatchAttack` switch to route to them identically.

## Risks / Trade-offs

- **[Risk]** Some adversarial variants may evade detection, especially at p70 padding → **Mitigation**: If detection fails, the benchmark report identifies which variants are missed and their scores, enabling targeted weight/feature adjustments
- **[Risk]** `slow_drip_encrypt` (6 hours, 50 ops/5min) may not trigger burst features at all → **Mitigation**: `total_operations` (weight 2.0), `modification_ratio`, and `directory_spread` should compensate since the total operation count is still elevated
- **[Trade-off]** `selective_high_value` targets few files — may have low total_operations signal. This tests whether `high_value_ext_ratio` (weight 2.5) alone is sufficient.
