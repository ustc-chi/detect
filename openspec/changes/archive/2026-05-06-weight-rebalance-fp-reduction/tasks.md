## 1. Weight Updates

- [x] 1.1 Update `WeightedEuclideanScorer.DEFAULT_WEIGHTS` — change index 0 from 1.0→2.0, index 8 from 1.0→1.5, index 9 from 4.0→1.5
- [x] 1.2 Update `IntermittentEncryptionBenchmark.BASE_WEIGHTS` — same 3 changes to match

## 2. Threshold Percentile Update

- [x] 2.1 Update default `--threshold-percentile` in `RansomwareDetectorCli.java` from 90.0 to 97.0
- [x] 2.2 Verify `AnomalyThreshold` constructor accepts the new percentile value correctly

## 3. Validation

- [x] 3.1 Run `mvn clean test` — all 21 existing tests must pass (test arrays unchanged, only default weights shift)
- [x] 3.2 Run `IntermittentEncryptionBenchmark` with updated weights — verify 72/72 detection
- [x] 3.3 Verify 0/24 false positives on normal rounds
- [x] 3.4 If any attacks missed or FPs remain, iterate on weight adjustments and re-run benchmark

## 4. Documentation

- [x] 4.1 Update README.md weights table with new values
- [x] 4.2 Update README.md threshold documentation to reflect 97% default
- [x] 4.3 Update README.md benchmark results table with new scores
