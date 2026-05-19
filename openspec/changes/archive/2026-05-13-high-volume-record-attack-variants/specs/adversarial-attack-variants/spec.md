## MODIFIED Requirements

### Requirement: Adversarial variants in benchmark with 12 features
The IntermittentEncryptionBenchmark SHALL test all 8 adversarial variants at 3 padding levels (20%, 50%, 70%), producing 24 adversarial test cases, using the new 12-feature scoring system with rebalanced weights. Additionally, the benchmark SHALL include 24 high-volume record attack variants (C1–C8) at 3 padding levels as Phase 2.7, for a total of 129 attack test cases (36 ORIG + 36 intermittent + 24 adversarial + 9 after-hours + 24 high-volume).

The benchmark SHALL report detection results with emphasis on:
- B1 (backup_disguise) at p50 and p70
- B2 (slow_drip_encrypt) at p50 and p70
- B3 (random_jitter_burst) at p50 and p70
- All C1–C8 high-volume variants at all padding levels

#### Scenario: Benchmark runs with 12-feature scoring
- **WHEN** IntermittentEncryptionBenchmark.main() is executed
- **THEN** it SHALL test 129 attack cases using 12-feature vectors and rebalanced weights

#### Scenario: B1 backup disguise now detected at p50
- **WHEN** the benchmark tests backup_disguise (B1) at p50
- **THEN** it SHALL be flagged as an anomaly (temporal_uniformity + directory_coverage_depth provide new detection signals)

#### Scenario: B2 slow drip now detected at p50
- **WHEN** the benchmark tests slow_drip_encrypt (B2) at p50
- **THEN** it SHALL be flagged as an anomaly (temporal_uniformity provides the primary detection signal)

#### Scenario: B3 random jitter now detected at p50
- **WHEN** the benchmark tests random_jitter_burst (B3) at p50
- **THEN** it SHALL be flagged as an anomaly (directory_coverage_depth + rename_correlation provide new detection signals)

#### Scenario: Adversarial results reported with feature contributions
- **WHEN** benchmark results are printed
- **THEN** adversarial variant results SHALL be labeled with variant name (B1-B8), padding level, AND the top contributing features for each detection

#### Scenario: High-volume C1–C8 variants included in benchmark
- **WHEN** the benchmark Phase 2.7 runs
- **THEN** 24 high-volume test cases (8 variants × 3 padding levels) SHALL be generated, scored, and included in the total detection summary

#### Scenario: High-volume results reported separately
- **WHEN** benchmark detailed results are printed
- **THEN** high-volume variant results SHALL be labeled with variant name (C1-C8), padding level, record count, AND the top contributing features for each detection
