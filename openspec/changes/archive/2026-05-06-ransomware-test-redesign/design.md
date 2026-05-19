## Context

The ransomware detection system uses 11 aggregated features with weighted Euclidean distance scoring (z-score normalized by median/MAD baseline) to detect ransomware activity in NetApp snapdiff data. A signature pre-check catches obvious indicators (.enc, .locked, ransom note names), while statistical scoring handles everything else.

The research document (`ransomware-encryption-comparison.md`) provides detailed filesystem manipulation behaviors for 7 major ransomware families (WannaCry, Ryuk, Conti, REvil, LockBit, BlackCat, Cl0p). Cross-referencing these behaviors against the current implementation reveals:

- Current attack rounds model generic behaviors but not family-specific filesystem strategies
- Attack operations are spread over 3600s+ windows; real ransomware operates in 30-300s bursts
- `size_std_dev` at weight 0.06 is the most critical misconfiguration — encryption's uniform size changes produce low variance, making this a primary detection signal that's essentially turned off
- 73% of detection relies on signature pre-check; the statistical layer has never been properly stress-tested

## Goals / Non-Goals

**Goals:**
- Add 2 high-impact features (`burst_mod_purity`, `file_type_concentration`) that directly capture documented ransomware filesystem behaviors
- Rebalance all 13 feature weights based on which features discriminate ransomware from normal activity
- Replace all 11 existing attack patterns with 12 new patterns directly modeled after specific ransomware family behaviors from the research document
- Ensure at least 10 of 12 attack rounds have NO obvious indicators (no suspicious extensions, no ransom notes) to properly stress-test statistical detection
- Concentrate attack operations in realistic burst windows (30-300 seconds) matching real ransomware timing

**Non-Goals:**
- Changing the detection algorithm (still weighted Euclidean distance with z-score normalization)
- Changing the signature pre-check mechanism (still extension + ransom note pattern matching)
- Adding per-user or per-directory baseline tracking
- Modifying the self-learning window mechanism (still FIFO 10 rounds)
- Changing the snapdiff data format or parser

## Decisions

### Decision 1: Add `burst_mod_purity` (index 11) — Modification Purity During Bursts

**Rationale**: The research shows that ransomware encryption bursts are almost purely modifications — 85-100% of burst operations are file modifications. Normal activity bursts (batch deployments, backups) have mixed operation types — 30-50% modifications, rest adds/deletes. This feature directly discriminates encryption bursts from legitimate bursts.

**Computation**: 
1. Identify the peak 5-minute window (same logic as `peak_burst_velocity`)
2. Count operations in that window by type
3. `burst_mod_purity = modifications_in_burst / total_ops_in_burst`

**Implementation**: The current `peak_burst_velocity` uses `Set<Instant>` which loses duplicate timestamps. We must refactor to track a `List<Instant>` with associated operation types to enable both features from a single pass.

**Weight**: 3.0 — Moderate. Combines with `peak_burst_velocity` (5.0) for burst characterization, but neither is dominant. Detection should work via burst patterns OR non-burst statistical signals, not burst-only.

**Alternative considered**: Track operation type distribution across the ENTIRE round (not just burst window). Rejected because normal rounds with mixed activity would dilute the signal — the burst window isolates the encryption spike.

### Decision 2: Add `file_type_concentration` (index 12) — Per-Extension Targeting Concentration

**Rationale**: The research documents that ransomware targets file types exhaustively — Conti gives 171 extensions FULL_ENCRYPT priority, LockBit's `FastSet` targets specific extensions. When ransomware targets .docx files, it modifies ALL of them, creating a very high concentration on that extension. Normal activity touches files of various types more evenly.

**Computation**:
1. For each modified file, extract extension
2. Count modifications per extension
3. `file_type_concentration = max(per_extension_count) / total_modified_count`
4. Returns 0.0 if no modified files

**Weight**: 2.0 — Moderate. Useful discriminator for targeted attacks but less powerful than burst/size features for mass encryption.

**Alternative considered**: Compute Shannon entropy of extension distribution. Rejected because max-concentration is simpler, more interpretable, and directly models the ransomware behavior of targeting ALL files of a specific type.

### Decision 3: Weight Rebalancing Strategy

**Approach**: Rebalance weights based on the discriminatory power of each feature against documented ransomware behaviors:

| # | Feature | Old | New | Rationale |
|---|---------|-----|-----|-----------|
| 0 | total_operations | 1.14 | 1.0 | Moderate signal; ransomware increases volume but so does normal activity |
| 1 | modification_ratio | 0.22 | **3.0** | Encryption rounds have mod_ratio ≈0.8-0.95 vs baseline ≈0.4. Primary non-burst statistical signal |
| 2 | deletion_ratio | 0.12 | 0.5 | Only relevant for wiper variants; moderate weight |
| 3 | bytes_removed | 0.16 | 0.5 | Complements deletion_ratio; moderate |
| 4 | directory_spread | 1.01 | 1.5 | Encryption hits many directories; moderate increase |
| 5 | extension_diversity | 0.27 | 0.8 | Extension introduction/removal is a signal; increase |
| 6 | suspicious_extension_ratio | 10.0 | 10.0 | Deterministic signal; keep |
| 7 | peak_burst_velocity | 10.0 | **5.0** | Reduced from 10.0 to avoid single-point-of-failure on burst detection. Normal batch jobs also produce bursts; overweighting causes false positives and misses throttled attacks |
| 8 | avg_modified_size | 1.09 | 1.0 | Moderate signal; similar |
| 9 | size_std_dev | 0.06 | **4.0** | **Most critical change**. Encryption produces LOW variance (uniform size changes). Highest-weight non-burst statistical signal — works even when attackers throttle speed |
| 10 | high_value_ext_ratio | 0.41 | **2.5** | Database/document targeting is a documented ransomware behavior |
| 11 | burst_mod_purity | — | **3.0** | New; burst characterization. Moderate weight — works with peak_burst_velocity but not dominant |
| 12 | file_type_concentration | — | **2.0** | New; targeted attack detection |

**Key insight on `size_std_dev`**: The z-score for this feature goes NEGATIVE during encryption (variance drops below baseline). The squared z-score in the Euclidean distance doesn't care about direction — it just needs sufficient weight to contribute to the total. At weight 0.06, even a large negative z-score contributes almost nothing. At 4.0, a z-score of -3.0 contributes 4.0 × 9.0 = 36.0 to the squared sum, which is decisive. This is the highest-weight non-burst feature because it captures the most fundamental encryption signal: uniform size changes across all files. It works regardless of whether the attacker operates in bursts or throttles speed.

**Key insight on `peak_burst_velocity` reduction (10.0 → 5.0)**: Over-relying on burst velocity creates two failure modes: (1) advanced ransomware that throttles operations becomes invisible, and (2) legitimate batch jobs (deployments, backups) trigger false positives. By reducing burst velocity weight and increasing non-burst statistical features (`size_std_dev` 4.0, `modification_ratio` 3.0, `high_value_ext_ratio` 2.5), the system detects via EITHER burst patterns OR statistical anomalies. Non-burst feature total weight: 4.0 + 3.0 + 2.5 + 2.0 = 11.5. Burst feature total weight: 5.0 + 3.0 = 8.0. This ensures statistical signals dominate, with burst features as supporting evidence.

### Decision 4: Attack Round Design — Realistic Burst Windows

**Current problem**: Attack operations are timestamped `attackTime.plusSeconds(random.nextInt(3600))` — spread over 1 hour. Real ransomware operates in concentrated bursts:
- LockBit: <2 minutes for full host
- Conti: <15 minutes with 32 parallel workers  
- Ryuk: <10 minutes
- BlackCat: <10 minutes with 4 workers

**New approach**: All attack operations are concentrated in realistic time windows:
- Fast attacks (LockBit, single-user): 30-120 seconds
- Standard attacks (Conti, mass encryption): 3-5 minutes
- Slow evasion attacks: 30 minutes with micro-bursts (10s bursts of ~100 ops, 60-90s gaps)

Normal padding operations remain spread over 6-13 hours as before. This creates a clear temporal separation: attack ops cluster tightly while normal ops are dispersed. The 5-minute sliding window for `peak_burst_velocity` will capture the attack burst even when padded with normal ops.

### Decision 5: Attack Round Schedule and Positive Controls

**Schedule**: 40 total rounds (28 normal + 12 attacks). Attack rounds at positions: 4, 7, 10, 13, 16, 19, 22, 25, 28, 31, 34, 37.

**Positive controls**: 2 rounds with obvious indicators (rounds 31 and 37) to verify signature pre-check still works after all changes. The other 10 rounds have NO extensions or ransom notes that would trigger current signatures.

**Alternative considered**: More positive controls (3-4 signature-caught rounds). Rejected because the user specifically wants to stress-test statistical detection, and signature detection is already proven. 2 positive controls is sufficient to verify no regression.

## Risks / Trade-offs

**[Risk] New features may increase false positives on legitimate bursts** → Mitigation: `burst_mod_purity` only activates when `peak_burst_velocity` is also high. A legitimate burst with high mod purity but normal velocity won't score high because both features must deviate simultaneously.

**[Risk] Weight rebalancing may shift the threshold** → Mitigation: The threshold is percentile-based from baseline self-scoring. After retraining with new weights, the threshold adapts automatically. Must regenerate baseline data with new features.

**[Risk] 13-feature expansion is a breaking change** → Mitigation: All downstream code uses `FEATURE_COUNT` constant and indexed access. Changes propagate uniformly. Old 11-feature baselines are incompatible and must be regenerated.

**[Risk] Slow distributed encryption (round 6) may still be undetectable** → This is intentional — it tests the limits of the system. The micro-burst pattern (100 ops in 10s, then 60-90s gap) represents the most sophisticated evasion. Accepting that some attacks are beyond aggregated-feature detection is honest.

**[Trade-off] Using max-concentration vs. entropy for file_type_concentration** → Max-concentration is simpler and directly models the "target all .docx files" behavior, but misses distributed targeting across 2-3 extensions. Acceptable tradeoff since `high_value_ext_ratio` already captures multi-type targeting.
