## Context

The anomaly detector computes a threshold from baseline self-scores using the 97th percentile. With N=24 baseline rounds, `ceil(0.97 × 24) - 1 = 23` always selects the **maximum** score. This means a single contaminated baseline round (backup surge at score ~28, batch compile at ~31) becomes the threshold, causing the detector to miss real attacks. The normalization layer (median + MAD in `BaselineStatistics`) is already robust, but `AnomalyThreshold` has no outlier protection.

Current `AnomalyThreshold` constructor:
```java
List<Double> distances = new ArrayList<>();
for (RansomwareFeatureVector v : baselineVectors) {
    distances.add(scorer.score(v));
}
Collections.sort(distances);
int idx = (int) Math.ceil((percentile / 100.0) * distances.size()) - 1;
this.threshold = distances.get(idx);
```

## Goals / Non-Goals

**Goals:**
- Filter outlier self-scores from the baseline distribution before percentile selection
- Cap the threshold at a reasonable multiple of the median baseline self-score
- Provide diagnostic logging when outlier baseline rounds are detected
- Maintain 100% detection on all 96 attack cases and 0 FP on 24 vanilla normal cases
- Be backward-compatible: clean baselines produce the same threshold as before

**Non-Goals:**
- Changing the scoring formula (WeightedEuclideanScorer) or normalization (median + MAD)
- Changing the self-learning window behavior (threshold is still fixed after calibration)
- Detecting which specific feature caused an outlier baseline round
- Auto-removing contaminated files from the baseline directory

## Decisions

### Decision 1: IQR-based outlier filtering before percentile selection

Use the Interquartile Range (IQR) method to identify and exclude outlier self-scores from the baseline distribution before computing the percentile threshold.

**Algorithm**:
1. Compute all N baseline self-scores → sorted `distances`
2. Compute Q1 (25th percentile) and Q3 (75th percentile) of `distances`
3. Compute IQR = Q3 - Q1
4. Compute upper fence = Q3 + k × IQR (default k = 2.5)
5. Filter `distances` to include only scores ≤ upper fence
6. Compute percentile on the filtered set using the same P value

**Why IQR over alternatives**:
- **Z-score filtering**: Would require computing mean/std of the self-scores — but self-scores aren't normally distributed (they're distances from centroid), making z-scores unreliable
- **Trimmed percentile (remove top/bottom k%)**: Removes a fixed proportion regardless of whether outliers exist. With 24 rounds and 5% trim, that's 1.2 rounds removed always, even on clean data. IQR adapts — it removes nothing on clean data, and more on contaminated data
- **Median Absolute Deviation (MAD) filtering**: Would work but is redundant since we already use MAD for feature normalization. IQR is the standard box-plot outlier method and more intuitive to configure

### Decision 2: Maximum threshold cap at 3× median self-score

After IQR filtering and percentile selection, cap the threshold at 3× the median self-score. This is a safety net for edge cases where IQR filtering doesn't catch slow contamination (e.g., 3-4 moderately anomalous rounds that don't exceed the fence individually but collectively shift the percentile).

**Why 3× median**:
- Current system: median self-score ≈ 4.5, threshold ≈ 8.74 → ratio ≈ 1.94×
- Lowest attack score (B2_p70) = 15.72 → 15.72 / 4.5 ≈ 3.5× median
- Cap at 3× means threshold ≈ 13.5, which still catches all attacks (lowest = 15.72)
- Provides ~1.16× margin even at the cap
- If attacks evolve to score lower than 3× median, this cap would need tightening — but that's a fundamental detection capability issue, not a threshold issue

### Decision 3: Configurable IQR multiplier via CLI

Add `--threshold-iqr-multiplier` (default 2.5) to allow operators to tune aggressiveness. Standard box-plot outlier rules:
- k = 1.5 → aggressive filtering (may remove legitimate high-normal scores)
- k = 2.5 → moderate (default, catches clear outliers)
- k = 3.0 → permissive (only extreme outliers)
- k = 0 → disabled (backward compatible with current behavior)

### Decision 4: Diagnostic logging, not exception throwing

When outlier rounds are detected, log a WARNING with the count and scores of filtered rounds. Do NOT throw an exception or abort — the operator may have intentionally included those rounds. The IQR filtering handles the statistical correction; the logging provides visibility.

## Risks / Trade-offs

- **[Over-filtering]** → If IQR multiplier is too aggressive (k=1.5), legitimate high-normal baseline rounds could be filtered out, making the threshold too low and increasing false positives. **Mitigation**: Default k=2.5 is conservative. Benchmark validates 0 FP on vanilla normal.

- **[Under-filtering]** → If contaminated rounds cluster just below the fence, IQR won't catch them. **Mitigation**: The 3× median cap provides a hard safety net.

- **[Small baseline]** → With N < 5 baseline rounds, Q1/Q3/IQR estimates are unreliable. **Mitigation**: Skip IQR filtering when N < 5, fall back to raw percentile (same as current behavior). The cap still applies.

- **[Threshold shift on clean data]** → Could IQR filtering inadvertently change the threshold on clean baselines? **Mitigation**: On clean data with k=2.5, the upper fence should be well above the 97th percentile score, so no rounds get filtered. Benchmark validates this.
