## Context

The 13-feature detection system achieved 72/72 detection but with 2/24 false positives (8.3%). Analysis shows the FPs are driven by `size_std_dev` (weight 4.0) — the second-highest non-burst weight. On filesystems with regular automated maintenance (cron log rotation, database checkpoint writes, config file updates by agents), files get uniformly small size changes, producing a low `size_std_dev` that mimics encryption patterns.

Meanwhile `total_operations` at weight 1.0 is underweighted for its discriminative value. Ransomware's most universal signal is sheer operation volume: even with 70% padding, attack rounds still show elevated operation counts relative to baseline.

Current weight distribution:
- Burst features (7, 11): total weight 8.0
- Non-burst features (rest): total weight 11.5
- `size_std_dev` alone: 4.0 (35% of non-burst weight)

## Goals / Non-Goals

**Goals:**
- Reduce false positives from 2/24 (8.3%) to 0/24 (0%) while maintaining 72/72 attack detection
- Increase `total_operations` weight to reflect its discriminative power
- Reduce `size_std_dev` weight to prevent confusion with regular filesystem maintenance
- Raise default threshold percentile to 95% to tighten the decision boundary

**Non-Goals:**
- Adding or removing features from the 13-feature vector
- Changing the z-score normalization or MAD calculation
- Changing the weighted Euclidean distance formula itself
- Modifying the signature pre-check logic

## Decisions

### Decision 1: Weight rebalancing strategy

| Feature | Old Weight | New Weight | Rationale |
|---------|-----------|------------|-----------|
| total_operations (0) | 1.0 | 2.5 | Ransomware produces mass operations; this is the most universal signal across all 12 attack types |
| modification_ratio (1) | 3.0 | 3.0 | Unchanged — already well-weighted |
| size_std_dev (9) | 4.0 | 2.0 | Regular filesystem maintenance (log rotation, DB checkpoints) produces uniform size changes that mimic encryption |
| avg_modified_size (8) | 1.0 | 1.5 | Compensates for reduced size_std_dev — encryption shifts mean file size upward by 1-5% |
| All other features | — | unchanged | No evidence of issues with other weights |

**Alternative considered**: Reduce `size_std_dev` to 1.0 (half of current). Rejected — the uniform-size-change signal is still valuable for pure encryption attacks; 2.0 keeps it meaningful while reducing FP sensitivity.

### Decision 2: Default threshold percentile 90% → 95%

The 90th percentile threshold gives ≈7.7. The lowest attack score is 10.2 (slow_distributed p70). Raising to 95% increases the threshold but still leaves margin:
- If threshold rises to ~9.5 (reasonable for 95th percentile), margin is 10.2/9.5 ≈ 1.07× — tight but positive
- The weight changes themselves will shift all scores, so the exact threshold will recalibrate

**Combined effect**: Higher threshold + better weights should eliminate the 2 FPs while maintaining full detection. The benchmark must validate this.

### Decision 3: Synchronized weight arrays

Both `WeightedEuclideanScorer.DEFAULT_WEIGHTS` and `IntermittentEncryptionBenchmark.BASE_WEIGHTS` must be updated together. The benchmark serves as validation — if weights diverge, the benchmark tests wrong weights.

## Risks / Trade-offs

- **[Risk]** Raising threshold to 95% may miss low-signal attacks if weight rebalancing shifts scores unfavorably → **Mitigation**: Full 72-case benchmark must pass before accepting. If any attack is missed, iterate on weights.
- **[Risk]** Reduced `size_std_dev` weight may weaken detection of encryption attacks that DON'T change file extensions → **Mitigation**: `burst_mod_purity`, `peak_burst_velocity`, and `modification_ratio` provide orthogonal signals that cover these cases. Benchmark validates.
- **[Trade-off]** Higher threshold = fewer FPs but less sensitivity to novel attack patterns. This is acceptable — the 95th percentile is still standard for anomaly detection.
