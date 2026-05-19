## 1. Remove Signature-Trivial Cases from AttackGenerator.java

- [x] 1.1 Remove `generateMassEncryption` method (`.lockbit` → signature match)
- [x] 1.2 Remove `generateRansomNoteDrop` method (ransom note filenames → signature match)
- [x] 1.3 Remove `generateExtensionPreservingMass` method (structural duplicate of conti)
- [x] 1.4 Remove `generateMassiveModifiedFlood` method (scaled-up lockbit duplicate)
- [x] 1.5 Remove `generateAfterHoursLockBit`, `generateAfterHoursConti`, `generateAfterHoursMassEncrypt` methods (exact wrappers)

## 2. Remove Signature-Trivial Cases from IntermittentEncryptionBenchmark.java

- [x] 2.1 Remove `mass_encryption` and `ransom_note_drop` from `ATTACK_TYPES` array; remove `extension_preserving_mass` from `ATTACK_TYPES`
- [x] 2.2 Remove corresponding `dispatchAttack` switch cases for the removed types
- [x] 2.3 Remove A1/A2/A3/A5/A7/A11 from `variantDefs` array (`.enc`/`.crypt` → signature match)
- [x] 2.4 Remove A4/A6/A8/A10 from `variantDefs` array (no-ext twins of removed signature-trivial variants)
- [x] 2.5 Remove corresponding `dispatchVariant` switch cases for removed A-series variants
- [x] 2.6 Remove entire Phase 2.6 after-hours block (`afterHoursDefs`, generation loop, print statement)
- [x] 2.7 Remove C1 from `highVolumeDefs` array and its `dispatchHighVolume` case
- [x] 2.8 Remove `dispatchAdversarial` cases for `after_hours_lockbit`, `after_hours_conti`, `after_hours_mass_encrypt`

## 3. Add D-Series Combo-Feature Attack Generators

- [x] 3.1 Implement `generateRyukLateral` in AttackGenerator.java — two-phase: slow HV targeting + fast burst
- [x] 3.2 Implement `generateDarkSideStaged` in AttackGenerator.java — three-phase: add copies → modify originals → delete copies
- [x] 3.3 Implement `generateLockBit3Adaptive` in AttackGenerator.java — alternating fast/slow bursts with varying purity
- [x] 3.4 Implement `generateBlackCatVariable` in AttackGenerator.java — mixed full/intermittent encryption at off-hours
- [x] 3.5 Implement `generateRoyalSelective` in AttackGenerator.java — partial corruption with rename on 15% of files
- [x] 3.6 Implement `generatePlayIntermittentNoExt` in AttackGenerator.java — 128KB block encryption of business files
- [x] 3.7 Implement `generateMedusaMultiStage` in AttackGenerator.java — disruption → escalating encryption stages
- [x] 3.8 Implement `generateAkiraVpnGradual` in AttackGenerator.java — slow-to-fast escalation at off-hours

## 4. Register D-Series in IntermittentEncryptionBenchmark.java

- [x] 4.1 Add `dispatchCombo` method routing D-series method names to AttackGenerator methods
- [x] 4.2 Add Phase 2.8 block with `comboDefs` array defining 8 D-series types with descriptions
- [x] 4.3 Add Phase 2.8 generation loop iterating comboDefs × PADDING_LEVELS

## 5. Build and Verify

- [x] 5.1 Run `mvn clean package -q` to verify zero compilation errors
- [x] 5.2 Run the full benchmark and verify: all phases complete, no exceptions, build succeeds
- [x] 5.3 Verify attack detection rate ≥ 99% on the full suite (target: 100%) — achieved 102/102 (100%) at velWt=8.0
- [x] 5.4 Verify vanilla normal false positive rate = 0% — achieved 0/24 (0.0%)
- [x] 5.5 If detection rate drops below 99%, analyze missed cases and adjust D-series parameters — not needed, 100% detection

## 6. Update Documentation

- [x] 6.1 Update README.md benchmark section: remove references to deleted cases, add D-series descriptions with feature combos, update total counts
