## Context

The ransomware detection system currently has 12 features, but feature index 10 (`wall_clock_anomaly`) is a hard-coded placeholder returning 0.0 in both `extract()` and `extractFromFile()` methods of `RansomwareFeatureExtractor`. The `BaselineStatistics` class already has per-hour infrastructure (`hourlyOps` map, `addHourlyObservation()`, `computeHourlyStats()`, `getHourlyStats()`) that was added in a previous change but never wired into the detection pipeline. The existing spec at `openspec/specs/wall-clock-anomaly/spec.md` defines the expected behavior.

The challenge is that feature extraction currently happens without access to baseline statistics — the extractor is stateless except for the `hvExtEma` field. To compute a z-score against historical same-hour data, the extractor needs a reference to the `BaselineStatistics` instance.

## Goals / Non-Goals

**Goals:**
- Activate `wall_clock_anomaly` by implementing actual z-score computation in `RansomwareFeatureExtractor`
- Wire the per-hour baseline accumulation into the detection pipeline (baseline loading → hourly stats computation → feature extraction)
- Add test coverage that verifies the feature works for after-hours attacks and returns 0.0 during learning period
- Update benchmark data to generate rounds at varied hours so per-hour baselines have meaningful statistics

**Non-Goals:**
- Changing the 12-feature vector size or other feature definitions
- Implementing day-of-week or seasonal patterns (24 hourly bins only)
- Adding new CLI parameters (use existing `--days-between` infrastructure)
- Modifying Phase 1 signature detection

## Decisions

### D1: Pass BaselineStatistics to RansomwareFeatureExtractor

**Decision**: Add a `BaselineStatistics` field to `RansomwareFeatureExtractor` with a setter (or constructor parameter), and use it during feature extraction to look up hourly stats.

**Rationale**: The extractor needs `getHourlyStats(hour)` to compute the z-score. Adding it as an optional field (nullable, defaults to null) preserves backward compatibility for existing code that constructs the extractor without a baseline.

**Alternative considered**: Pass hourly stats as a method parameter to `extract()`. Rejected — this would break all existing call sites and the `RansomwareDetector` doesn't have the hour information until after extraction begins.

### D2: Extract dominant hour from earliest non-EPOCH changeTime

**Decision**: Use the earliest `changeTime` in the round (excluding EPOCH) to determine the hour-of-day. If no valid timestamps exist, return 0.0.

**Rationale**: The earliest timestamp is the most stable reference point for the round. Using the "dominant hour" (mode of all timestamps) adds complexity without meaningful benefit — most rounds are contained within a single hour anyway.

**Alternative considered**: Use the median timestamp. Rejected — adds sorting overhead for marginal gain.

### D3: Compute z-score using the same robust statistics as other features

**Decision**: `z = (totalOps - median_h) / (mad_h * 1.4826)`, clamped to [-10.0, 10.0].

**Rationale**: Consistent with the existing z-score computation in `WeightedEuclideanScorer`. The `1.4826` scale factor makes MAD consistent with standard deviation under normality. Clamping prevents extreme values from dominating the distance metric.

### D4: Generate baseline rounds at varied hours in benchmark

**Decision**: Modify the benchmark to generate baseline normal rounds across multiple hours (e.g., 09:00, 10:00, 14:00, 15:00 for business hours) and add after-hours attack scenarios at 03:00.

**Rationale**: The current benchmark generates all rounds at 08:00:00Z, so every hour's baseline would have the same median. Varying hours creates realistic per-hour differences that the feature can detect.

### D5: Add after-hours burst attack to benchmark

**Decision**: Add a new adversarial variant or modify an existing one to occur at 03:00 with high operation count.

**Rationale**: We need test cases that specifically exercise `wall_clock_anomaly`. A 3 AM burst with 3000 ops vs a baseline of 50 ops at that hour should produce a strong signal.

## Risks / Trade-offs

- **[Threshold shift]** → Adding an active feature (currently 0.0) will change the distance landscape for all test cases. The benchmark threshold (~8.74) was tuned with feature 10 disabled. After activation, threshold recalibration is required.
- **[Learning period coverage]** → If the baseline doesn't cover all 24 hours, off-hours attacks during uncovered hours will return 0.0 (no detection from this feature). Mitigation: the benchmark should ensure baseline covers common hours; in production, a 7-day warmup period is recommended.
- **[Extractor state dependency]** → Making the extractor depend on `BaselineStatistics` introduces a coupling that didn't exist before. Mitigation: the field is optional; if null, the feature returns 0.0 (backward compatible).
- **[Benchmark hour distribution]** → Normal rounds currently span 6-13 hours of activity within a single day-start timestamp. The dominant hour is determined by `dayStart`, not the actual operation timestamps. Mitigation: explicitly set `dayStart` to varied hours for baseline rounds.
