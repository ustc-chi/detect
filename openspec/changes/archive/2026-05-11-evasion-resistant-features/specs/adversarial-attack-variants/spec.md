## MODIFIED Requirements

### Requirement: Adversarial variants in benchmark
The IntermittentEncryptionBenchmark SHALL test all 8 adversarial variants at 3 padding levels (20%, 50%, 70%), producing 24 adversarial test cases, using the new 14-feature scoring system with rebalanced weights.

The benchmark SHALL report detection results for `slow_drip_encrypt` (B2) and `size_mimic_normal` (B5) specifically, verifying that these previously evasive variants are now detected.

#### Scenario: Benchmark runs with 14-feature scoring
- **WHEN** IntermittentEncryptionBenchmark.main() is executed
- **THEN** it SHALL test 96 total attack cases (36 original + 36 intermittent + 24 adversarial) using 14-feature vectors and rebalanced weights

#### Scenario: slow_drip_encrypt now detected
- **WHEN** the benchmark tests slow_drip_encrypt (B2) at all 3 padding levels
- **THEN** all 3 SHALL be flagged as anomalies (inter_op_time_cv provides the new detection signal)

#### Scenario: size_mimic_normal now detected
- **WHEN** the benchmark tests size_mimic_normal (B5) at all 3 padding levels
- **THEN** all 3 SHALL be flagged as anomalies (size_change_kurtosis provides the new detection signal)

#### Scenario: Adversarial results reported with feature contributions
- **WHEN** benchmark results are printed
- **THEN** adversarial variant results SHALL be labeled with variant name (B1-B8), padding level, AND the top contributing features for each detection
