## Why

The anomaly threshold is calculated using the 97th percentile of baseline self-scores. With N=24 baseline rounds and P=97, the percentile index is always `ceil(0.97 × 24) - 1 = 23` — the **maximum** self-score. If even a single contaminated baseline round (e.g., a backup surge, batch compile, or misclassified snapdiff) enters the baseline set, its outlier score becomes the threshold. A threshold inflated from ~8.74 to 28+ would cause the detector to miss adversarial attack variants (B2_p70 scores 15.72, B3_p70 scores 16.56). The normalization layer (median + MAD) is robust, but the threshold selection layer has zero outlier protection.

## What Changes

- Add IQR-based outlier filtering to `AnomalyThreshold` — baseline self-scores exceeding Q3 + k×IQR are excluded before percentile selection
- Add a configurable maximum threshold cap relative to the median baseline self-score (default: 3× median)
- Add baseline validation logging — warn when filtered-out rounds are detected, listing their scores
- Lower the effective percentile after filtering to compensate for removed outliers (use same P value on the filtered set)
- Add CLI parameter `--threshold-iqr-multiplier` (default 2.5) to control outlier filtering aggressiveness

## Capabilities

### New Capabilities
- `robust-threshold`: Outlier-resistant threshold calculation with IQR filtering, max-cap, and diagnostic logging

### Modified Capabilities
- `statistical-anomaly-detector`: AnomalyThreshold constructor signature gains optional IQR multiplier parameter; threshold calculation now filters outliers before percentile selection

## Impact

- `AnomalyThreshold.java` — core change: filter outliers from self-score distribution before percentile selection
- `RansomwareDetectorCli.java` — new `--threshold-iqr-multiplier` CLI parameter
- `BaselineStatistics.java` — no change (median/MAD already robust)
- `IntermittentEncryptionBenchmark.java` — verify all 136 test cases still pass with new threshold logic
- `README.md` — document new parameter and updated threshold calculation description
- **Not a breaking change**: existing behavior is preserved for clean baselines (IQR filtering only removes outliers, the percentile on clean data produces the same result)
