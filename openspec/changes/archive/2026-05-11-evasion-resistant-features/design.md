## Context

The RCF system detects ransomware from NetApp snapdiff data using 13 statistical features scored via weighted Euclidean distance with median/MAD robust normalization. It currently achieves 72/72 detection with 0/24 false positives across 12 attack patterns and 12 intermittent variants at 3 padding levels each.

However, 8 adversarial variants (B1-B8) expose weaknesses: `slow_drip_encrypt` (B2) distributes ~50 ops per 5-minute interval to stay below the fixed 300-second burst window, and `size_mimic_normal` (B5) randomizes per-file size changes between -10% and +10% to make the average and standard deviation appear normal. Additionally, Features 2 (`deletion_ratio`) and 3 (`bytes_removed`) have high overlap — both measure deletion activity from count and volume perspectives, wasting a dimension.

The feature extraction pipeline supports both streaming mode (temp file for burst data) and in-memory mode. The streaming mode writes `(epochSeconds, opType)` pairs to a temp file for post-pass burst computation. Any new features must integrate with this streaming architecture.

## Goals / Non-Goals

**Goals:**
- Add `size_change_kurtosis` (Feature 13) to detect uniform encryption size-change distributions vs. randomized adversarial changes
- Add `inter_op_time_cv` (Feature 14) to detect automated timing regularity that evades fixed-window burst detection
- Consolidate F2 (`deletion_ratio`) and F3 (`bytes_removed`) into a single `deletion_intensity` feature, reducing overlap
- Rebalance weights: reduce `peak_burst_velocity` (5.0→3.5) and `size_std_dev` (1.5→1.0) as new features share their detection load
- Maintain 0 false positives on the existing 24-round normal baseline
- Improve detection against `slow_drip_encrypt` and `size_mimic_normal` adversarial variants

**Non-Goals:**
- Entropy-based features (requires snapdiff content data not available in current pipeline)
- Path traversal order detection (moderate priority, deferred)
- Cross-round temporal correlation / trend analysis (moderate priority, deferred)
- Changing the scoring formula (weighted Euclidean distance) or normalization method (median/MAD)
- Changing the two-phase detection pipeline (signature pre-check → statistical scoring)

## Decisions

### Decision 1: Feature vector layout — compact reindexing vs. append-only

**Choice: Compact reindexing.** F2 and F3 merge into a single `deletion_intensity` at index 2. The old index 3 is removed. Indices 4-12 shift down by 1 to become 3-11. New `size_change_kurtosis` takes index 12. New `inter_op_time_cv` takes index 13. Total: 14 features (indices 0-13).

**Alternative considered:** Append-only (keep F2/F3, add new features at indices 13 and 14 for a 15-dimensional vector). Rejected because it preserves the F2/F3 overlap without benefit.

**Rationale:** Compact reindexing eliminates redundancy and keeps the vector small. The reindexing is a one-time migration cost (baseline regeneration is already required due to weight changes).

### Decision 2: New feature index mapping

```
OLD → NEW Mapping:
  0: total_operations           →  0: total_operations         (weight 2.0, unchanged)
  1: modification_ratio         →  1: modification_ratio       (weight 3.0, unchanged)
  2: deletion_ratio             ─┐
  3: bytes_removed              ─┴→  2: deletion_intensity     (weight 0.5, consolidated)
  4: directory_spread           →  3: directory_spread         (weight 1.5, unchanged)
  5: extension_diversity        →  4: extension_diversity      (weight 0.8, unchanged)
  6: suspicious_extension_ratio →  5: suspicious_extension_ratio (weight 10.0, unchanged)
  7: peak_burst_velocity        →  6: peak_burst_velocity      (weight 5.0→3.5, reduced)
  8: avg_modified_size          →  7: avg_modified_size        (weight 1.5, unchanged)
  9: size_std_dev               →  8: size_std_dev             (weight 1.5→1.0, reduced)
 10: high_value_ext_ratio       →  9: high_value_ext_ratio     (weight 2.5, unchanged)
 11: burst_mod_purity           → 10: burst_mod_purity         (weight 3.0, unchanged)
 12: file_type_concentration    → 11: file_type_concentration  (weight 2.0, unchanged)
  [NEW]                           12: size_change_kurtosis      (weight 2.0, new)
  [NEW]                           13: inter_op_time_cv          (weight 2.5, new)
```

### Decision 3: `size_change_kurtosis` computation approach

**Formula:** Excess kurtosis of per-file size change ratios for modified files with `size > 0`.

```
1. Collect change_ratios = [(new_size - old_size) / old_size] for each modified file with size > 0
   Note: In snapdiff, we only see the NEW size (post-modification). We need the original size.
   Since snapdiff records only contain the post-change size, we approximate:
   - For files with consistent +X% change: use (size - baseline_avg_size_for_ext) / baseline_avg_size_for_ext
   - Simpler approach: use size_i / mean(all_modified_sizes) - 1 as a proxy
   - CHOSEN: Compute kurtosis of log1p(size_i) values directly — this is what size_std_dev already uses,
     and kurtosis of the SIZE distribution (not change ratio) still captures uniformity.
     When encryption applies uniform +2-5%, the size distribution after encryption becomes more uniform
     (lower kurtosis) compared to the highly varied normal file sizes (higher kurtosis).
```

**Actual formula chosen:**
```
size_change_kurtosis = excess_kurtosis([log1p(size_i)]) for each modified file with size > 0
where excess_kurtosis = (Σ(x_i - x̄)⁴ / n) / (σ⁴) - 3.0
```

**Rationale:** Using `log1p(size)` instead of `(new - old) / old` because snapdiff records don't contain the original (pre-change) size — only the post-modification size. This is the same data used by Features 8 and 9 (`log1p(size_i)` values). The kurtosis of this distribution still captures the encryption uniformity signal: encryption produces a platykurtic (flat) distribution, while normal activity produces a leptokurtic (peaked with heavy tails) distribution.

**Default value:** 0.0 when fewer than 4 modified files with size > 0 (insufficient data for reliable kurtosis).

### Decision 4: `inter_op_time_cv` computation approach

**Formula:** Coefficient of variation of inter-operation time deltas.

```
1. Sort all records with valid change_time (not null, not EPOCH) by timestamp
2. Compute consecutive deltas: deltas[i] = sorted[i+1] - sorted[i] (in seconds)
3. μ = mean(deltas), σ = std_dev(deltas)
4. cv = σ / μ
```

**Integration with streaming mode:** The existing temp file for burst computation already stores `(epochSeconds, opType)` pairs. After sorting this temp file (already done for burst features), the inter_op_time_cv can be computed from the same sorted data in a single additional pass — no extra temp file needed.

**Default value:** 0.0 when fewer than 2 records with valid timestamps.

**Weight rationale:** 2.5 — moderate-high. This feature provides unique temporal signal that no other feature captures, directly countering the most dangerous evasion technique (slow_drip_encrypt).

### Decision 5: `deletion_intensity` consolidation formula

**Formula:** `log1p(total_deleted_bytes / daysBetweenSnapshots) × deletion_ratio`

```
deletion_intensity = log1p(Σ size(type="deleted") / daysBetweenSnapshots) × (count(type="deleted") / total_operations)
```

**Rationale:** Multiplying volume (`log1p(bytes)`) by proportion (`ratio`) captures both the scale and intensity of deletion. A few large deletions (high bytes, low count) and many small deletions (low bytes, high count) both produce moderate values. Only mass destructive attacks (high bytes AND high count) produce extreme values. This preserves the signal from both original features in one dimension.

**Weight:** 0.5 (unchanged — deletion attacks are a niche category).

### Decision 6: Weight rebalancing strategy

| Feature | Old Weight | New Weight | Rationale |
|---|---|---|---|
| peak_burst_velocity | 5.0 | 3.5 | `inter_op_time_cv` shares temporal detection load; avoid over-weighting temporal features collectively |
| size_std_dev | 1.5 | 1.0 | `size_change_kurtosis` captures distribution shape more precisely; size_std_dev becomes redundant for the uniformity signal |
| size_change_kurtosis | — | 2.0 | New feature; moderate weight — complements but doesn't replace size_std_dev |
| inter_op_time_cv | — | 2.5 | New feature; moderate-high weight — unique temporal signal against evasion |

All other weights remain unchanged. Total weight budget increases slightly (from 37.3 to ~39.8), which is acceptable since the threshold re-calibration absorbs the change.

## Risks / Trade-offs

**[Risk] Breaking change requires baseline regeneration** → All existing baselines become invalid when the feature vector changes. Mitigation: Document the breaking change clearly in CLI help and README. The `--weights` parameter changes from 13 to 14 values.

**[Risk] Kurtosis is sensitive to sample size** → With fewer than ~20 modified files, kurtosis is unreliable. Mitigation: Return 0.0 (neutral) when fewer than 4 modified files with size > 0, effectively excluding this feature from scoring for small samples.

**[Risk] inter_op_time_cv assumes timestamp availability** → If many records have null or EPOCH timestamps, the CV becomes unreliable. Mitigation: Return 0.0 when fewer than 2 records with valid timestamps. This matches the existing behavior for burst features.

**[Risk] Weight rebalancing may shift detection margins** → Reducing peak_burst_velocity from 5.0 to 3.5 could lower scores for burst-heavy attacks. Mitigation: The threshold re-calibrates automatically (97th percentile of baseline self-scores). Must run full benchmark (72 attacks + 24 normal + 24 adversarial variants) to verify margins.

**[Risk] Consolidated deletion_intensity changes wiper detection dynamics** → The combined formula may not perfectly replicate the individual signals of F2 and F3. Mitigation: Deletion attacks are already well-detected by other features (total_operations, directory_spread). The weight stays low (0.5). Verify with creeping_shrink and wiper-style tests.

**[Trade-off] Using log1p(size) kurtosis instead of true size change ratios** → We can't compute true `(new-old)/old` because snapdiff doesn't contain the original size. The log1p(size) kurtosis is a proxy that still captures the uniformity signal but is less direct. Accepted because it works with available data.
