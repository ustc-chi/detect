## Context

The ransomware detection system uses a two-phase pipeline: Phase 1 (signature-based: suspicious extensions + ransom note filenames) and Phase 2 (statistical anomaly detection on 14 features). The statistical phase computes features from `SnapdiffRecord` metadata (path, type, size, changeTime) per round, then uses weighted Euclidean distance from a robust baseline (median + MAD) to score anomalies.

Critical analysis revealed:
- 3 features (F7/F8/F12) are fundamentally broken — they measure file-size composition, not size changes, because `SnapdiffRecord` lacks `old_size`
- 4 features (F3/F4/F5/F11) produce near-zero signal for most attack patterns or are highly redundant
- 6 adversarial variants (B1/B2/B3 at p50/p70) evade detection because no existing feature targets sustained low-rate activity, systematic directory traversal, or batch regularity

The snapdiff schema is fixed (4 fields per record). We cannot add `old_size` or access file content. All improvements must work within metadata-only constraints.

## Goals / Non-Goals

**Goals:**
- Replace broken features (F7/F8/F12) with features that produce valid signal from available data
- Add features targeting the 6 detection misses (B1/B2/B3 at p50/p70)
- Reduce feature redundancy (F4/F11 measure the same signal)
- Fix computation issues in surviving features (F2 normalization, F13 burst-window scoping, F14 undefined handling)
- Maintain the existing two-phase detection architecture (signature → statistical)
- Keep the weighted Euclidean + robust z-score scoring approach

**Non-Goals:**
- Adding `old_size` to `SnapdiffRecord` or changing the snapdiff schema
- Implementing entropy-based or content-based detection (no file content access)
- Changing the detection pipeline architecture (no ML classifiers, no action-triggered windows)
- Modifying Phase 1 signature detection
- Per-user decomposition of the feature vector (each round produces one vector for all users)

## Decisions

### D1: Feature vector dimensionality: 14 → 12

**Decision**: Shrink from 14 to 12 features by removing 7 and adding 5.

**Rationale**: Removing 7 broken/redundant features eliminates noise from the distance calculation. The 3 broken size features (F7/F8/F12) contribute random z-scores that dilute genuine signals. The 4 weak features (F3/F4/F5/F11) either produce z≈0 for most attacks or duplicate other features' signals.

**Alternative considered**: Keep 14 slots but replace all 7 with new features. Rejected — 12 well-chosen features produce a cleaner distance metric than 14 features where some contribute noise. Fewer dimensions also reduce the baseline sample requirements.

### D2: New feature: `temporal_uniformity` (replaces inter_op_time_cv's role for slow attacks)

**Decision**: Compute `1 - CV` of operation counts in sequential 5-minute bins. High uniformity (≈1.0) indicates sustained regular activity like B2's 50-ops-every-5-minutes pattern.

**Rationale**: B2 (slow drip) evades `inter_op_time_cv` because padding dilutes the timestamp signal across the entire round. Binning operations into 5-minute windows and measuring batch-size regularity is structurally immune to padding — even with 70% normal ops mixed in, the attack's contribution maintains its regularity.

**Alternative considered**: Autocorrelation of operation timestamps. Rejected — more complex to compute, harder to interpret as a z-score, and provides similar signal.

### D3: New feature: `directory_coverage_depth`

**Decision**: Compute ratio of unique directories with modifications to total directories observed, plus mean directory depth of modified files.

**Rationale**: B1 (backup disguise) and B3 (random jitter) both modify files across many directories breadth-first. Normal user activity is depth-first (few directories, many files per directory). This structural property survives padding dilution.

**Alternative considered**: Shannon entropy of directory paths. Rejected — less interpretable and conflates directory count with naming diversity.

### D4: New feature: `rename_correlation`

**Decision**: Count correlated (added, deleted) record pairs where the added path shares a prefix or has low Levenshtein distance to the deleted path.

**Rationale**: The snapdiff format has no "rename" type — renames appear as (deleted + added). REvil, WannaCry, and B8 all rename files. This reconstructs rename events from the diff, producing a strong signal (2000+ renames per round) that is near-zero for normal activity.

**Alternative considered**: Track extension transitions by maintaining a baseline extension set. Rejected — requires cross-round state that the current per-round feature extractor doesn't support.

### D5: New feature: `wall_clock_anomaly`

**Decision**: Z-score of current hour's operation count vs historical same-hour baseline. Requires per-hour baseline bins (24 bins) accumulated during self-learning.

**Rationale**: After-hours attacks produce anomalous operation counts at unusual hours. The baseline already accumulates median + MAD — extending it to per-hour bins is a minimal change.

**Alternative considered**: Day-of-week + hour combined baseline (168 bins). Rejected — requires longer learning period and the signal gain is marginal for our attack patterns.

### D6: New feature: `per_type_entropy`

**Decision**: Shannon entropy of the operation type distribution `{added, modified, deleted}` within the round.

**Rationale**: Replaces F4 (extension diversity) and F11 (file type concentration), which both measure extension-based signals that produce zero output for 10/12 attack patterns. Operation type entropy has signal for ALL attacks: ransomware concentrates on modifications (low entropy), while normal activity has mixed types (high entropy).

**Alternative considered**: Keep one extension feature and add operation type as separate. Rejected — the extension features are the weakest in the set, and operation type entropy subsumes their signal while adding new signal.

### D7: Fix `inter_op_time_cv` — burst-window scoping

**Decision**: Compute CV only within the 300-second burst window (same window identified by `peak_burst_velocity`), not across the entire round.

**Rationale**: At p50/p70 padding, 50–70% of operations are normal and dominate the timestamp sequence. Within the burst window, the attack's timing pattern is concentrated and the CV signal survives.

### D8: Feature weight allocation

**Decision**: `peak_burst_velocity` reduced to 5.0 (from 10.0). New features get weights 1.5–3.0.

**Rationale**: A weight of 10.0 made the scorer overly sensitive to any burst — scheduled batch jobs (backup, indexing, sync) legitimately produce thousands of ops in 5-min windows. At 10.0, a single backup burst could single-handedly push the score past threshold. Reducing to 5.0 keeps burst velocity as the highest-weighted feature but allows other features (temporal_uniformity, directory_coverage_depth, rename_correlation) to contribute meaningfully. The combined signal from multiple moderate-weight features is more robust than reliance on a single dominant feature.

## Risks / Trade-offs

- **[Threshold recalibration]** → The anomaly threshold (~16.50) was tuned for the 14-feature set. Removing 7 features and adding 5 changes the distance landscape. The benchmark must be re-run and a new threshold derived from the ROC curve.
- **[Rename correlation false positives]** → Large-scale file reorganization (migration, archiving) could trigger rename correlation. Mitigation: the feature is one of 12 in the distance metric, so isolated triggers won't exceed threshold alone.
- **[Temporal uniformity at low ops]** → With very few operations per round, the 5-minute binning produces noisy CV. Mitigation: floor the feature at 0.0 when fewer than 3 bins have data.
- **[Wall-clock anomaly learning period]** → Requires at least 7 days of baseline to establish per-hour patterns. Mitigation: during learning period, the feature returns 0.0 (neutral, no contribution to distance).
- **[Baseline dimension mismatch]** → Any saved baselines from the 14-feature system are incompatible. Mitigation: add a dimension check in `BaselineStatistics` with a clear error message.
- **[No per-user feature]** → The analysis showed `user_operation_concentration` would be valuable, but it was excluded by request. B1 (uniform user spread) detection relies on the combination of temporal_uniformity + directory_coverage_depth instead.
