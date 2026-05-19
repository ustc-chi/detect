## Why

The benchmark test suite (`IntermittentEncryptionBenchmark`) has **129 attack test cases** across 7 phases, but suffers from three problems:

1. **Signature-trivial cases**: 24 cases use suspicious extensions (`.enc`, `.crypt`, `.lockbit`) or ransom note filenames that are **immediately caught by Phase 1 signature pre-check** (`RansomwareSignatureDetector`). These never reach the statistical scoring engine — they're "free wins" that inflate detection rates without testing behavioral detection.

2. **Structural duplicates**: 27 cases are near-identical pairs (no-ext vs with-ext variants like A4 vs A5), exact timestamp-shifted copies (AH1-AH3 wrapping identical ORIG generators), or scaled-up versions of existing patterns (C1 vs ORIG_lockbit).

3. **Single-feature dominance**: Most remaining cases are detected by one overwhelmingly dominant feature (typically `peak_burst_velocity`). The suite lacks cases where detection requires a **combination of moderately-elevated features** — the realistic scenario for sophisticated modern ransomware (Ryuk, BlackCat, Royal, Akira) that deliberately avoids triggering any single feature strongly.

## What Changes

### Removals — Signature-trivial cases (24 cases)

These cases are caught by `RansomwareSignatureDetector` before reaching statistical scoring. They test signature matching, not behavioral anomaly detection:

- **ORIG_mass_encryption** (3 cases): `.lockbit` extension → instant signature match
- **ORIG_ransom_note_drop** (3 cases): ransom note filenames (`README_UNLOCK`, `HOW_TO_DECRYPT`, etc.) → instant signature match
- **A1/A2/A3** (9 cases): 10/25/50% of files get `.enc` extension → instant signature match
- **A5** (3 cases): 15% of files get `.crypt` extension → instant signature match
- **A7** (3 cases): 10% of files get `.enc` extension → instant signature match
- **A11** (3 cases): 30% of files get `.enc` extension → instant signature match

Note: `.WNCRY` (ORIG_wannacry_staged), `.key` (ORIG_clop_companion), `.tmp` (A9), and random 8-char extensions (ORIG_revil_random_ext) are **NOT** in the suspicious extensions list — these survive and properly test statistical detection.

### Removals — Structural duplicates (27 cases)

- **Phase 2.6 AH1/AH2/AH3** (9 cases): Exact wrappers over `ORIG_lockbit`, `ORIG_conti`, `ORIG_mass_encryption` with only timestamp shifted to 3 AM. (AH3 wraps the already-removed mass_encryption.)
- **Phase 2 no-ext variants A4/A6/A8/A10** (12 cases): Identical to A5/A7/A9/A11 except without extension suffix. Keep the harder variant. (A5/A7/A11 are already removed above as signature-trivial, so only A9 survives from each pair.)
- **Phase 2.7 C1 massive_modified_flood** (3 cases): Scaled-up `ORIG_lockbit` — same 100% modification burst profile.
- **Phase 1 ORIG_extension_preserving_mass** (3 cases): Near-identical to `ORIG_conti_size_tiered` — both are all-user modification floods.

### Surviving test cases after removals

| Phase | Category | Before | After | Types kept |
|-------|----------|--------|-------|------------|
| Phase 1 | ORIG | 36 | 27 | lockbit_fast, conti_tiered, database_priority, single_user_rapid, slow_distributed, creeping_shrink, revil_random_ext, clop_companion, wannacry_staged |
| Phase 2 | Intermittent (A) | 36 | 6 | A9 (every_Nth + .tmp), A12 (strip encrypt) |
| Phase 2.5 | Adversarial (B) | 24 | 24 | All B1-B8 kept |
| Phase 2.6 | After-hours (AH) | 9 | 0 | All removed (duplicates) |
| Phase 2.7 | High-volume (C) | 24 | 21 | C2-C8 kept |
| **Total** | | **129** | **78** | |

### Additions — Combo-feature D-series (24 cases → 8 types × 3 padding)

New D-series test cases referencing real ransomware families, each designed so that **no single feature dominates detection** — detection requires 3+ features in combination:

- **D1 (Ryuk lateral):** Targeted high-value directory encryption with two-speed approach. Combo: `directory_coverage_depth` + `high_value_ext_ratio` + `burst_mod_purity`
- **D2 (DarkSide staged):** Three-phase exfiltrate→encrypt→cleanup with mixed op types. Combo: `per_type_entropy` + `temporal_uniformity` + `total_operations_normalized`
- **D3 (LockBit 3.0 adaptive):** Variable-speed bursts with alternating purity. Combo: `inter_op_time_cv_burst` + `burst_mod_purity` + `directory_coverage_depth`
- **D4 (BlackCat cross-platform):** Mixed full/intermittent encryption with off-hours timing. Combo: `peak_burst_velocity` + `per_type_entropy` + `wall_clock_anomaly`
- **D5 (Royal selective corruption):** Partial corruption leaving some files as "proof". Combo: `burst_mod_purity` + `high_value_file_coverage` + `rename_correlation`
- **D6 (Play intermittent no-ext):** Alternating block encryption targeting business files. Combo: `modification_ratio` + `inter_op_time_cv_burst` + `high_value_ext_ratio`
- **D7 (Medusa multi-stage):** Config disruption → escalating encryption. Combo: `total_operations_normalized` + `temporal_uniformity` + `per_type_entropy`
- **D8 (Akira VPN exploitation):** Gradual encryption escalation at off-hours. Combo: `wall_clock_anomaly` + `directory_coverage_depth` + `inter_op_time_cv_burst`

### Final suite: 78 + 24 = **102 attack test cases**

## Capabilities

### New Capabilities
- `combo-feature-attacks`: New D-series attack generators (D1-D8) that produce detection via feature combinations rather than single dominant features

### Modified Capabilities
<!-- No existing spec changes — purely test case content -->

## Impact

- **`AttackGenerator.java`**: Add 8 new methods (D1-D8), remove `generateMassEncryption`, `generateRansomNoteDrop`, `generateAfterHoursLockBit/Conti/MassEncrypt`, `generateMassiveModifiedFlood`, `generateExtensionPreservingMass`
- **`IntermittentEncryptionBenchmark.java`**: Remove signature-trivial and duplicate case definitions, add Phase 2.8 for D-series, update dispatch methods, update `ATTACK_TYPES` and `variantDefs` arrays
- **`README.md`**: Update benchmark documentation to reflect removed/added cases, note signature-trivial removal rationale
- **No changes** to detection engine, features, scoring, or signature pre-check logic
