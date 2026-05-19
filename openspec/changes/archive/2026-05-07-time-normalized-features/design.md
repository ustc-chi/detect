## Context

The detector extracts 13 features from NetApp snapdiff files (snapshot differences). Currently all features are computed as raw values regardless of the time interval between the two snapshots being compared. In production, snapshot intervals vary (daily, every 2 days, weekly). This causes absolute-valued features to be incomparable across different intervals, breaking the baseline calibration.

**Current feature sensitivity analysis:**

| # | Feature | Type | Interval-sensitive? | Why |
|---|---------|------|---------------------|-----|
| 0 | `total_operations` | Absolute count | **YES** | ~7× more ops in 7-day vs 1-day snapdiff |
| 1 | `modification_ratio` | Ratio | No | modified/total — self-normalizing |
| 2 | `deletion_ratio` | Ratio | No | deleted/total — self-normalizing |
| 3 | `bytes_removed` | Absolute (log-scaled sum) | **YES** | Sum of deleted bytes scales with interval |
| 4 | `directory_spread` | Absolute (unique count) | **YES** | More unique paths over longer intervals |
| 5 | `extension_diversity` | Absolute (unique count) | **YES** | More unique extensions over longer intervals |
| 6 | `suspicious_extension_ratio` | Ratio | No | suspicious/total — self-normalizing |
| 7 | `peak_burst_velocity` | Velocity (ops/hr) | No | Already per-unit-time from 5-min window |
| 8 | `avg_modified_size` | Average | No | Per-file average — self-normalizing |
| 9 | `size_std_dev` | Statistic | No | Standard deviation of log-sizes |
| 10 | `high_value_ext_ratio` | Ratio | No | high-value/total — self-normalizing |
| 11 | `burst_mod_purity` | Ratio | No | mods/total in burst window |
| 12 | `file_type_concentration` | Ratio | No | max-ext/total-modified |

**4 of 13 features need normalization.** The other 9 are already interval-invariant by construction.

## Goals / Non-Goals

**Goals:**
- Normalize features 0, 3, 4, 5 to per-day rates, making the detector invariant to snapshot interval duration
- Accept a `daysBetweenSnapshots` parameter with a sensible default (2.0) at every entry point (constructor, extract methods, CLI)
- Maintain backward compatibility: existing 2-day baselines produce approximately the same feature values with the default parameter
- Preserve the 13-feature vector shape and all scoring/threshold infrastructure unchanged

**Non-Goals:**
- Auto-detecting the interval from the snapdiff data (the JSON format contains no snapshot timestamps at the container level)
- Changing any weights, thresholds, or the scoring formula
- Normalizing ratio/velocity/statistic features that are already interval-invariant
- Adding interval metadata to the snapdiff JSON format

## Decisions

### D1: Divide-by-days normalization (chosen)

**Approach**: Divide the raw absolute value by `daysBetweenSnapshots` before storing in the feature vector.

| Feature | Current | Normalized |
|---------|---------|------------|
| #0 total_operations | `count(records)` | `count(records) / days` |
| #3 bytes_removed | `log1p(sum_deleted_bytes)` | `log1p(sum_deleted_bytes / days)` |
| #4 directory_spread | `\|unique_grandparent_paths\|` | `\|unique_grandparent_paths\| / days` |
| #5 extension_diversity | `\|unique_extensions\|` | `\|unique_extensions\| / days` |

**Alternatives considered:**
- **Rate-feature approach**: Multiply all features by `2.0 / days` at the vector level (global scaling). Rejected because ratio features are already dimensionless and should not be scaled.
- **Z-score normalization handles it**: Rely on the fact that median/MAD adapts to the baseline. Rejected because mixing baselines with different intervals (e.g., 1-day and 7-day snapdiffs in the same baseline set) produces inconsistent statistics.
- **Add interval as a 14th feature**: Let the scorer learn the interval relationship. Rejected — unnecessary complexity, and with only ~24 baseline samples, the scorer cannot reliably learn a non-linear interval dependency.

### D2: Parameter placement — constructor + methods

`daysBetweenSnapshots` is accepted at:
1. `RansomwareFeatureExtractor` constructor (stored as instance field, default 2.0)
2. `extract(SnapdiffFile)` and `extractFromFile(Path)` use the stored value
3. `RansomwareDetector` passes it through to its internal `RansomwareFeatureExtractor`
4. CLI `--days-between` flag (default 2.0)

**Rationale**: Constructor-level default means existing test code that creates `new RansomwareFeatureExtractor(null)` continues to work with the 2.0 default. Only callers that need a different interval pass the parameter.

### D3: Default value of 2.0 days

The current test generator produces snapdiffs with 2-12 hour spans (time跨度) but the operations simulate what a "typical" NetApp snapshot interval would capture. The default 2.0 days was chosen because it matches the most common real-world NetApp snapshot schedule. For the existing test suite, the default means test-generated features remain numerically close to their current values.

### D4: Feature #3 normalization order — divide before log

For `bytes_removed`: `log1p(sum_deleted_bytes / days)` instead of `log1p(sum_deleted_bytes) / days`.

**Rationale**: `log1p(x/d)` preserves the compression property (large values are dampened) while correctly scaling the input. Dividing after the log would over-compress: `log1p(1e12) / 7 ≈ 39.5 / 7 ≈ 5.6`, while `log1p(1e12/7) ≈ log1p(1.4e11) ≈ 32.8`. The divide-before-log approach produces a value that is a per-day rate in log-space.

## Risks / Trade-offs

- **[Baseline recalculation needed]** Existing baselines calibrated without normalization will produce different feature values after this change. → Mitigation: Default of 2.0 days produces approximately the same values for baselines that were genuinely 2-day intervals. For other intervals, recalibration is required and expected.

- **[Division by very small days]** If `daysBetweenSnapshots` is set to a very small value (e.g., 0.01 = ~15 minutes), normalized features explode. → Mitigation: Clamp `daysBetweenSnapshots` to a minimum of 0.25 (6 hours) at the constructor level.

- **[Integer-day assumption]** The parameter is a `double`, allowing fractional days (e.g., 0.5 for 12-hour snapshots). This is intentional — not all snapshot schedules align on whole days.

- **[Unique counts saturate]** Features 4 and 5 are unique counts. In theory, `unique_directories / days` is not perfectly linear because unique items saturate. In practice, for the typical range (1-7 days), the relationship is close enough to linear that simple division is adequate. For very long intervals (>30 days), saturation effects become noticeable, but such intervals are uncommon for ransomware detection.
