## Why

The current 72-case benchmark tests standard ransomware patterns but doesn't cover adversarial evasion techniques — deliberate strategies to defeat the statistical detector. Real ransomware operators actively evade detection by mimicking normal activity, timing attacks to blend with backups, or randomizing behavior to avoid burst detection. We need test cases that validate the detector against these adversarial strategies.

## What Changes

Add 8 new adversarial attack variants to `AttackGenerator.java` and the benchmark:

| # | Variant | Evasion Strategy | Size Change | Extension |
|---|---------|-----------------|-------------|-----------|
| B1 | **backup_disguise** | Mimics scheduled backup: spread over 2-4 hours with uniform +2-4% size increase across all file types. Defeats burst-based features. | +2-4% | None |
| B2 | **slow_drip_encrypt** | 50 ops every 5 minutes over 6 hours (total ~3600 ops). Defeats peak_burst_velocity by keeping density low in any 5-min window. | +1-3% | None |
| B3 | **random_jitter_burst** | Fast encryption but with random 1-15s jitter between ops and occasional 30-60s pauses. Defeats burst_mod_purity by diluting the window. | +2-5% | None |
| B4 | **mixed_operation_mask** | Encrypts files but interleaves real adds/deletes (10% each) among modifications. Defeats modification_ratio by keeping it below 0.9. | +1-3% | None |
| B5 | **size_mimic_normal** | Encrypts files but applies random size changes (-10% to +10%) instead of uniform increase. Defeats size_std_dev by maintaining high variance. | -10% to +10% | None |
| B6 | **selective_high_value** | Only encrypts high-value extensions (.docx, .xlsx, .pdf, .db, .sql) — small count but targeted. Defeats total_operations and directory_spread. | +2-5% | None |
| B7 | **multi_family_combo** | Combines two strategies: 50% files get +2-3% uniform (LockBit-style), 50% get +4-8KB append (fast-mode style). Tests detector against mixed signals. | Mixed | None |
| B8 | **rename_and_encrypt** | Renames files to random names before encrypting (destroys extension information), then encrypts. Defeats extension_diversity and high_value_ext_ratio. | +1-4% | Randomized |

These are tested at 3 padding levels (20%/50%/70%) each, adding 24 new adversarial test cases to the benchmark.

## Capabilities

### New Capabilities

- `adversarial-attack-variants`: 8 new attack generator methods and benchmark integration for adversarial evasion techniques

### Modified Capabilities

- `ransomware-test-generator`: extend round schedule to include adversarial variants in the test cycle
- `statistical-anomaly-detector`: benchmark must validate detection of adversarial variants (spec requirement, not code change)

## Impact

- `AttackGenerator.java` — 8 new methods
- `IntermittentEncryptionBenchmark.java` — 24 new test cases in Phase 2
- `RansomwareTestGenerator.java` — extended round schedule
- `README.md` — updated benchmark results
