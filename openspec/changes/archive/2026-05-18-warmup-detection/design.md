## Context

The detector has a hard dependency: `RansomwareDetector` constructor requires `BaselineStatistics` and `AnomalyThreshold`, which need ≥5 baseline vectors for IQR computation. When `AnomalyThreshold` receives an empty baseline, it returns `threshold = 0.0`, flagging everything as anomalous. There is no fallback path for cold-start scenarios.

Current flow: CLI requires `--baseline-dir`, loads all files, builds stats/threshold, then processes suspect rounds. If baseline-dir is empty or missing, the system either crashes or produces useless results.

## Goals / Non-Goals

**Goals:**
- Enable detection from round 0 via heuristic rules when statistical baseline is unavailable
- Prevent baseline contamination from anomalous rounds during warmup period
- Make `--baseline-dir` optional so the system can start cold
- Add warmup-awareness to benchmark (Phase 0)

**Non-Goals:**
- Machine-learning-based warmup detection
- Modifying the statistical scoring algorithm or weights
- Supporting warmup heuristic tuning via CLI flags (hardcoded thresholds for now)
- Changing the signature pre-check flow

## Decisions

### D1: WarmupDetector as a separate class (not mixed into RansomwareDetector)

**Choice**: Standalone `WarmupDetector` with a `classify(RansomwareFeatureVector) -> boolean` method.

**Rationale**: Separation of concerns — heuristic rules and statistical scoring are fundamentally different mechanisms. This keeps `RansomwareDetector` clean and makes WarmupDetector independently testable.

**Alternative considered**: Adding warmup logic directly to `RansomwareDetector.processRound()`. Rejected because it creates a god method and makes testing warmup rules harder.

### D2: Five heuristic rules with ≥2 trigger threshold

**Rules**:
- `modification_ratio > 0.85` — encryption is almost all modifications
- `peak_burst_velocity > 5000` — ops/hr threshold for automated speed
- `temporal_uniformity > 0.7` — automated tools produce uniform time distributions
- `burst_mod_purity > 0.90` — burst windows in encryption are nearly pure modifications
- `rename_correlation > 0.5` — encrypt-then-rename pattern

**Trigger**: ≥2 rules match → anomaly.

**Rationale**: Single-rule triggers would have high false positives (e.g., batch compile triggers modification_ratio + temporal_uniformity). The 2-rule threshold mirrors the statistical detector's multi-feature approach. These thresholds are intentionally conservative — the statistical detector is the primary mechanism.

**Alternative considered**: Weighted scoring similar to the statistical detector. Rejected because 5 binary rules with a count threshold is simpler, more interpretable, and sufficient for a warmup safety net.

### D3: Warmup period ends when baseline accumulates ≥5 vectors

**Choice**: WarmupDetector is consulted only when `baselineCount < 5`. Once 5 clean vectors are accumulated, statistical detection takes over entirely.

**Rationale**: `MIN_BASELINE_FOR_IQR = 5` in `AnomalyThreshold`. The warmup period naturally ends when the statistical detector has enough data.

### D4: Anomalous warmup rounds excluded from baseline accumulator

**Choice**: In `RansomwareDetector.processRound()`, during warmup, if `WarmupDetector.classify()` returns true, the vector is NOT added to the baseline accumulator.

**Rationale**: Including ransomware vectors in the baseline would poison median/MAD calculations, shifting the statistical model and making subsequent detection unreliable.

### D5: CLI changes are backward-compatible

**Choice**: `--baseline-dir` becomes optional. When omitted, the system starts in warmup mode. When provided with <5 files, warmup mode applies for early rounds.

**Rationale**: Existing scripts that pass `--baseline-dir` continue to work unchanged. CSV adds columns at the end, preserving column order for existing consumers.

## Risks / Trade-offs

- **[Heuristic false positives during warmup]** → Mitigated by conservative 2-rule threshold. Warmup period is short (≤5 rounds). False positive in warmup just means a normal round is flagged — acceptable for a security system.
- **[Heuristic false negatives during warmup]** → Mitigated by rules targeting the strongest ransomware signals. Slow/stealthy attacks during warmup might evade heuristics but will be caught once statistical baseline is established.
- **[Stuck in warmup if all early rounds are anomalous]** → If warmup never accumulates 5 clean vectors, system stays in warmup indefinitely. Acceptable — persistent anomalies warrant continued heuristic mode. Log a WARNING when warmup period exceeds 10 rounds.
