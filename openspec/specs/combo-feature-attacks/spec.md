## ADDED Requirements

### Requirement: D-series combo-feature attack generators
The system SHALL provide 8 new attack generator methods in `AttackGenerator.java` (D1-D8), each implementing a real-world ransomware pattern designed to avoid triggering any single detection feature dominantly. Each generator SHALL accept `(Instant attackTime, double paddingRatio)` parameters consistent with existing generators and return `List<DiffEntry>`.

#### Scenario: D1 Ryuk lateral targeted encryption
- **WHEN** `generateRyukLateral(attackTime, paddingRatio)` is called
- **THEN** the generator SHALL produce a two-phase attack: Phase 1 produces 500-1000 modifications of high-value files (.docx/.xlsx/.pdf/.db/.sql) spread across 5-8 users over 10-30 minutes at moderate speed; Phase 2 produces 1500-3000 modifications across all file types in 1-3 minutes. The size change SHALL be +2-4% uniformly. No extension changes. The two phases SHALL produce moderately elevated `directory_coverage_depth`, `high_value_ext_ratio`, and `burst_mod_purity` with no single feature z-score exceeding 3x the next-highest.

#### Scenario: D2 DarkSide staged exfiltration-then-encrypt
- **WHEN** `generateDarkSideStaged(attackTime, paddingRatio)` is called
- **THEN** the generator SHALL produce a three-phase attack: Phase 1 adds 500-1500 encrypted copies (added ops) over 5-10 minutes; Phase 2 modifies 1500-3000 original files (+2-4%) over 3-8 minutes; Phase 3 deletes the copies from Phase 1 (deleted ops) over 2-5 minutes. Total attack ops SHALL be 3000-5000. Each phase SHALL have a distinct time window with 1-3 minute gaps. No extension changes. The result SHALL elevate `per_type_entropy` (mixed added/modified/deleted), `temporal_uniformity` (three distinct phases), and `total_operations_normalized` (high volume) without any single feature dominating.

#### Scenario: D3 LockBit3 adaptive speed encryption
- **WHEN** `generateLockBit3Adaptive(attackTime, paddingRatio)` is called
- **THEN** the generator SHALL produce 2000-4000 modifications across all users in 5-8 alternating fast/slow bursts: fast bursts of 400-600 ops in 30-60 seconds (90% modified + 10% padding-like adds/deletes for noise), slow bursts of 200-300 ops spread over 3-5 minutes (80% modified). Size change SHALL be +2-5% with fast bursts having larger changes (+3-5%) and slow bursts having smaller changes (+2-3%). No extension changes. The alternating pattern SHALL create variable `inter_op_time_cv_burst` (irregular timing between bursts), `burst_mod_purity` (varying 80-100% purity per burst), and `directory_coverage_depth` (broad but variable traversal).

#### Scenario: D4 BlackCat cross-platform variable encryption
- **WHEN** `generateBlackCatVariable(attackTime, paddingRatio)` is called
- **THEN** the generator SHALL produce a mixed-mode attack: 60% of files get full encryption (+3-5% size, modified) and 40% get partial/intermittent encryption (+1-2% size, modified). Operations SHALL span 4-8 users over 15-45 minutes with variable burst density (some 30s bursts, some 3-minute windows). Attack time SHALL be set to an off-hours timestamp (02:00-04:00 range). No extension changes. The mixed encryption depth and variable timing SHALL create moderate elevation in `peak_burst_velocity` (variable burst rates), `per_type_entropy` (not purely modified due to mixed sizes), and `wall_clock_anomaly` (off-hours execution).

#### Scenario: D5 Royal selective corruption
- **WHEN** `generateRoyalSelective(attackTime, paddingRatio)` is called
- **THEN** the generator SHALL produce 1500-3000 modifications across all users where: 70% of files get encrypted (modified, +3-5%), 20% get partial corruption (modified, -5% to +5% random), 10% are left untouched (not in diff list). Among encrypted files, 15% SHALL also have their path renamed (delete+add pattern in same directory). The attack SHALL run in a 2-5 minute burst. This SHALL produce moderate `burst_mod_purity` (not 100% due to partial corruption variance), `high_value_file_coverage` (targets but doesn't exclusively hit high-value files), and `rename_correlation` (15% renamed files create detectable but not overwhelming rename signal).

#### Scenario: D6 Play intermittent no-ext
- **WHEN** `generatePlayIntermittentNoExt(attackTime, paddingRatio)` is called
- **THEN** the generator SHALL produce 1000-2000 modifications of business-critical files (.docx/.xlsx/.pdf/.db/.sql/.csv/.pptx) using alternating 128KB block encryption. Each file's size change SHALL be calculated as `(fileSize / 131072) * random(8-24)` bytes — proportional to block count. Operations SHALL be spread over 10-20 minutes with inter-operation intervals of 200-600ms (automated but not bursty). No extension changes. This SHALL produce moderate `modification_ratio` (high but not extreme since the slow pacing allows padding to dilute), `inter_op_time_cv_burst` (consistent but not perfectly uniform timing), and `high_value_ext_ratio` (targets business files specifically).

#### Scenario: D7 Medusa multi-stage encryption
- **WHEN** `generateMedusaMultiStage(attackTime, paddingRatio)` is called
- **THEN** the generator SHALL produce a three-stage attack: Stage 1 generates 100-300 mixed operations (30% modified, 40% added, 30% deleted) simulating service disruption over 5-15 minutes; Stage 2 generates 2000-4000 modifications (+2-4%) over 8-15 minutes with variable speed (alternating fast 1-min bursts and slow 3-min windows); Stage 3 generates 500-1000 additional modifications (+3-5%) over 3-5 minutes. No extension changes. Stages SHALL have 2-5 minute gaps. This SHALL produce moderate elevation in `total_operations_normalized` (high total volume), `temporal_uniformity` (two encryption stages at different speeds), and `per_type_entropy` (mixed types in Stage 1).

#### Scenario: D8 Akira VPN-exploit gradual encryption
- **WHEN** `generateAkiraVpnGradual(attackTime, paddingRatio)` is called
- **THEN** the generator SHALL produce 1500-3000 modifications spread across 3-5 users over 45-90 minutes. The attack SHALL start slowly (100-200 ops in first 20 minutes), escalate to moderate speed (500-1000 ops in next 20 minutes), then peak (remaining ops in final 10-20 minutes). Attack time SHALL be set to off-hours (23:00-03:00). Size changes SHALL be +1-3% with some files having -5% to +5% random variation to mimic normal editing. No extension changes. The gradual escalation and off-hours timing SHALL produce moderate `wall_clock_anomaly`, `directory_coverage_depth` (broad but focused on 3-5 users), and `inter_op_time_cv_burst` (variable timing due to escalating speed).

### Requirement: Benchmark registration of D-series cases
The system SHALL register all D-series attack types in `IntermittentEncryptionBenchmark.java` as Phase 2.8, following the same pattern as Phase 2.5 (adversarial variants). Each D-series type SHALL be tested at all 3 padding levels (p20, p50, p70).

#### Scenario: D-series cases appear in Phase 2.8
- **WHEN** the benchmark runs
- **THEN** 8 D-series types x 3 padding levels = 24 new test cases SHALL be generated, added to `attackTestCases`, and included in Phase 3 weight scan and Phase 4 detailed results

#### Scenario: D-series dispatch routing
- **WHEN** `dispatchCombo(gen, method, attackTime, paddingRatio)` is called with method names matching D-series generators
- **THEN** the correct `AttackGenerator` method SHALL be invoked and the resulting diffs returned

### Requirement: Removal of signature-trivial test cases
The system SHALL remove test case definitions that are trivially caught by the Phase 1 signature pre-check (`RansomwareSignatureDetector`) and never exercise statistical scoring:

#### Scenario: ORIG signature-trivial removal
- **WHEN** the benchmark code is modified
- **THEN** `mass_encryption` SHALL be removed from `ATTACK_TYPES` array (`.lockbit` is in `SuspiciousExtensions`). `ransom_note_drop` SHALL be removed from `ATTACK_TYPES` array (ransom note filenames match `RANSOM_NOTE_PATTERNS`). The corresponding `dispatchAttack` cases, `generateMassEncryption` and `generateRansomNoteDrop` methods in `AttackGenerator.java` SHALL be removed.

#### Scenario: A-series signature-trivial removal
- **WHEN** the benchmark code is modified
- **THEN** the following entries SHALL be removed from `variantDefs`: A1 (`intermittent_10pct_enc_ext`), A2 (`intermittent_25pct_enc_ext`), A3 (`intermittent_50pct_enc_ext`), A5 (`partial_encrypt_header_with_crypt`), A7 (`intermittent_tiny_500ops_enc`), A11 (`intermittent_single_user_enc`). All use `.enc` or `.crypt` which are in `SuspiciousExtensions.java`. The corresponding `dispatchVariant` case entries SHALL be removed. Inline generator methods (`genIntermittent`, `genHeaderAppend`, `genSingleUser`) SHALL be kept since they may be used by surviving variants or are general-purpose.

### Requirement: Removal of redundant test cases
The system SHALL remove the following structurally redundant test case definitions from `IntermittentEncryptionBenchmark.java`:

#### Scenario: AH-series removal
- **WHEN** the benchmark code is modified
- **THEN** the Phase 2.6 after-hours block (AH1/AH2/AH3 definitions, `afterHoursDefs` array, the Phase 2.6 generation loop) SHALL be removed. The `dispatchAdversarial` entries for `after_hours_lockbit`, `after_hours_conti`, `after_hours_mass_encrypt` SHALL be removed. The `generateAfterHoursLockBit`, `generateAfterHoursConti`, `generateAfterHoursMassEncrypt` methods in `AttackGenerator.java` SHALL be removed.

#### Scenario: A-series no-ext variant removal
- **WHEN** the benchmark code is modified
- **THEN** the following entries SHALL be removed from `variantDefs`: A4 (`partial_encrypt_header_only`), A6 (`intermittent_tiny_500ops`), A8 (`partial_every_Nth_file`), A10 (`intermittent_single_user`). The corresponding `dispatchVariant` case entries SHALL be removed.

#### Scenario: C1 massive modified flood removal
- **WHEN** the benchmark code is modified
- **THEN** the C1 (`massive_modified_flood`) entry SHALL be removed from the `highVolumeDefs` array and its `dispatchHighVolume` case SHALL be removed. The `generateMassiveModifiedFlood` method in `AttackGenerator.java` SHALL be removed.

#### Scenario: ORIG extension_preserving_mass removal
- **WHEN** the benchmark code is modified
- **THEN** `extension_preserving_mass` SHALL be removed from the `ATTACK_TYPES` array. The `dispatchAttack` case for `extension_preserving_mass` SHALL be removed. The `generateExtensionPreservingMass` method in `AttackGenerator.java` SHALL be removed.

### Requirement: README documentation update
The README.md SHALL be updated to reflect the new test case inventory: removed cases with rationale (signature-trivial + structural duplicates), added D-series cases with their feature combos, and updated total counts.

#### Scenario: Updated benchmark overview
- **WHEN** the README is updated
- **THEN** the benchmark overview table SHALL show the corrected phase breakdown and total counts, the D-series SHALL be documented with feature combo descriptions, and the removal rationale SHALL be noted (signature-trivial vs structural duplicate)
