## Why

The detector requires `BaselineStatistics` and `AnomalyThreshold` at construction time, which in turn need ≥5 baseline feature vectors for IQR-based threshold computation. When the system starts cold (no baseline directory, or fewer than 5 rounds), `AnomalyThreshold` returns `threshold = 0.0`, causing every round to be flagged as anomalous. There is no heuristic safety net for rounds 0–4, leaving a blind spot during the critical early window where ransomware often strikes first.

## What Changes

- Add a `WarmupDetector` class that applies heuristic rules (modification_ratio > 0.85, peak_burst_velocity > 5000, temporal_uniformity > 0.7, burst_mod_purity > 0.90, rename_correlation > 0.5) to classify rounds before statistical baseline is ready. Trigger: ≥2 rules → anomaly.
- Anomalous rounds during warmup are excluded from the baseline accumulator, preventing contamination.
- CLI: `--baseline-dir` becomes optional; CSV output gains `warmup` and `baseline_count` columns.
- Benchmark: Add Phase 0 testing cold-start → heuristic anomaly detection → exclusion → transition to statistical detection at round 5+.

## Capabilities

### New Capabilities
- `warmup-detection`: Heuristic-based anomaly detection for rounds 0–4 when statistical baseline is insufficient. Covers WarmupDetector rules, baseline accumulator gating, and cold-start CLI behavior.

### Modified Capabilities
- `statistical-anomaly-detector`: RansomwareDetector construction and processRound must support null/empty baseline (warmup mode), delegate to WarmupDetector for early rounds, and gate baseline accumulator against warmup-anomalous vectors.
- `ransomware-test-generator`: Benchmark gains Phase 0 cold-start scenario and warmup-related CSV output columns.

## Impact

- **Code**: New `WarmupDetector.java`; modifications to `RansomwareDetector.java`, `RansomwareDetectorCli.java`, `IntermittentEncryptionBenchmark.java`.
- **API**: `--baseline-dir` CLI flag becomes optional (backward-compatible). CSV format adds 2 columns.
- **Dependencies**: No new external dependencies.
- **Systems**: No infrastructure changes.
