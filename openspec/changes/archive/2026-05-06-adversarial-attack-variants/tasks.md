## 1. Attack Generator Methods

- [x] 1.1 Implement `generateBackupDisguise(Instant, double)` — 3000-5000 ops over 2-4 hours, uniform +2-4%
- [x] 1.2 Implement `generateSlowDripEncrypt(Instant, double)` — ~50 ops/5min over 6 hours
- [x] 1.3 Implement `generateRandomJitterBurst(Instant, double)` — 2000-3000 ops with 1-15s jitter + 30-60s pauses
- [x] 1.4 Implement `generateMixedOperationMask(Instant, double)` — 80% modify, 10% add, 10% delete
- [x] 1.5 Implement `generateSizeMimicNormal(Instant, double)` — random -10% to +10% per file
- [x] 1.6 Implement `generateSelectiveHighValue(Instant, double)` — only .docx/.xlsx/.pdf/.db/.sql, 200-500 ops
- [x] 1.7 Implement `generateMultiFamilyCombo(Instant, double)` — 50% uniform + 50% append
- [x] 1.8 Implement `generateRenameAndEncrypt(Instant, double)` — random filenames + encrypt

## 2. Benchmark Integration

- [x] 2.1 Add 8 new attack type strings to `ATTACK_TYPES` array (or create `ADVERSARIAL_TYPES` array)
- [x] 2.2 Add dispatch cases in `dispatchAttack` switch for B1-B8
- [x] 2.3 Generate 24 adversarial test cases (8 variants × 3 padding levels) in benchmark Phase 2
- [x] 2.4 Add adversarial variant definitions to variantDefs or equivalent structure

## 3. Validation

- [x] 3.1 Run `mvn clean test` — all existing 21 tests must pass
- [x] 3.2 Run benchmark — verify all 96 attack cases (72 existing + 24 adversarial) are tested
- [x] 3.3 Record detection results for adversarial variants — identify any that evade detection
- [x] 3.4 Report which features catch each adversarial variant (top deviation analysis)

## 4. Documentation

- [x] 4.1 Update README.md with adversarial variant descriptions and detection results
