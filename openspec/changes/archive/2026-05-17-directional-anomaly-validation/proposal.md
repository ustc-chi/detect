## Why

The symmetric z² scoring treats above-baseline and below-baseline deviations equally. While ransomware overwhelmingly pushes feature indicators *above* baseline (higher burst velocity, higher modification ratio, higher temporal uniformity, etc.), an extremely quiet day (holiday, maintenance window, system outage) can push many features *below* baseline, generating a high anomaly score that is not a security threat. A post-prediction directional validation layer can distinguish "this is anomalous because it's quiet" from "this is anomalous because it looks like ransomware" — reducing false positives from non-threat outliers without modifying the core scorer.

## What Changes

- Add a **directional validation phase** after the symmetric anomaly verdict: when a round exceeds the anomaly threshold, compute the ratio of below-baseline energy to total energy, and reverse the verdict to NORMAL if the anomaly is overwhelmingly driven by anti-ransomware (below-baseline) deviations.
- Add CLI parameter `--direction-threshold` (default 0.75) controlling the reversal threshold.
- Log reversal details (original score, direction ratio, top-5 z-scores with direction) when a verdict is reversed.
- Reversed rounds SHALL NOT enter the self-learning window (they are statistically unusual but not ransomware; including them would pollute the baseline).

## Capabilities

### New Capabilities
- `directional-anomaly-validation`: Post-prediction validation that reverses anomaly verdicts when the anomaly is predominantly caused by below-baseline feature deviations rather than ransomware-indicative above-baseline deviations.

### Modified Capabilities
- `statistical-anomaly-detector`: The anomaly detection pipeline now includes an optional directional validation phase after scoring. The `RansomwareDetector` must call the validator before finalizing the verdict, and must exclude direction-reversed rounds from self-learning.

## Impact

- **`RansomwareDetector.java`**: Post-score validation logic, self-learning exclusion for reversed verdicts, logging on reversal.
- **`WeightedEuclideanScorer.java`**: Unchanged — the scorer remains symmetric.
- **`RansomwareDetectorCli.java`**: New `--direction-threshold` parameter.
- **`DirectionalValidator.java`**: New class computing E_up, E_down, ratio, and reversal decision.
- **Benchmark**: New "extremely quiet day" test cases to validate the reversal behavior.
