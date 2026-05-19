## Context

The benchmark suite currently has **129 attack test cases** across 7 phases in `IntermittentEncryptionBenchmark.java`. The attack generators live in `AttackGenerator.java` (31 methods) and inline generators in the benchmark class itself (for A-series variants). Detection is a two-phase pipeline: Phase 1 (`RansomwareSignatureDetector`) checks for suspicious extensions (`.enc`, `.crypt`, `.lockbit`, etc.) and ransom note filenames; Phase 2 does weighted Euclidean distance scoring on 12 behavioral features. The current suite achieves 100% detection, but 24 cases never reach Phase 2 — they're caught instantly by signature matching. Most remaining cases are caught by 1-2 dominant features.

The project is a Java/Maven codebase. Benchmark execution is a standalone `main()` method (not JUnit) that generates synthetic data, scores it, and reports results. All changes are confined to the `generator` package — no detector, feature, or scoring changes.

## Goals / Non-Goals

**Goals:**
- Remove 24 signature-trivial test cases (caught by Phase 1 signature pre-check, never reaching statistical scoring)
- Remove 27 structurally redundant test cases (exact duplicates, no-ext/with-ext twins, scaled-up variants)
- Add 8 new D-series attack types (24 cases at 3 padding levels) referencing real ransomware patterns
- Each D-series case should trigger 3+ features at moderate elevation — no single feature should carry >50% of the weighted z-score contribution
- Maintain 100% detection rate on the full test suite after changes
- Maintain 0% false positive rate on vanilla normal rounds

**Non-Goals:**
- No changes to the detection engine, feature extraction, scoring, or threshold logic
- No changes to the 12 existing features or their weights
- No changes to unit test files (only the benchmark generator)
- No optimization of existing attack patterns — only removal and addition
- No changes to padding strategy or baseline generation
- No changes to the signature pre-check (suspicious extensions list, ransom note patterns)

## Decisions

### Decision 1: Remove signature-trivial cases

**Choice**: Remove all cases where `RansomwareSignatureDetector` would instantly flag the attack — specifically cases using `.enc`, `.crypt`, `.lockbit` extensions or ransom note filenames. Verified against `SuspiciousExtensions.java` (50 extensions) and `RANSOM_NOTE_PATTERNS` (12 patterns).

**Rationale**: These 24 cases are "free wins" — they never exercise the statistical scoring engine. The benchmark's purpose is to test behavioral anomaly detection, not signature matching (which has its own unit tests in `RansomwareSignatureDetectorTest.java`). Keeping them inflates the detection rate artificially.

**Alternatives considered**:
- Keep them but exclude from statistical scoring results → Adds complexity to result reporting
- Move them to a separate "signature test" phase → Over-engineering; signature detection is already tested by unit tests

### Decision 2: Remove structural duplicates

**Choice**: Remove cases where (a) the attack generator is identical except for timestamp shift, (b) the only difference is presence/absence of extension suffix, or (c) the pattern is a scaled-up version of an existing case with the same detection profile.

**Rationale**: These 27 cases inflate the count without testing new detection pathways. After also removing signature-trivial cases, the A-series no-ext variants (A4, A6, A8, A10) would have no surviving with-ext pair for A5, A7, A11 — so they are also removed, leaving only A9 (.tmp, not suspicious) and A12.

**Alternatives considered**:
- Keep all cases and just add new ones → Would make the suite larger without gaining diversity
- Merge pairs into parameterized single cases → Adds complexity for no detection-value gain

### Decision 3: D-series design — "feature combo" approach

**Choice**: Each D-series attack deliberately suppresses the signals that make existing attacks easy to detect (no single dominant feature). Instead, 3+ features contribute moderately to the score. The attack parameters are calibrated so that:
- No single z-score exceeds 3× the next-highest
- At least 3 features have z-scores above the baseline MAD threshold
- The combined weighted Euclidean distance still exceeds the anomaly threshold

**Rationale**: Real sophisticated ransomware (Ryuk, BlackCat, Royal, Akira) doesn't trigger one overwhelming signal — it produces a pattern of moderate anomalies across multiple behavioral dimensions. The current suite doesn't test this scenario.

**Alternatives considered**:
- Generate adversarial cases via optimization (minimize max z-score while staying above threshold) → Too complex, results would be fragile and not reference real attack patterns
- Simply lower attack intensity across the board → Would make cases trivially detectable by the same single feature, just at lower magnitude

### Decision 4: D-series implementation location

**Choice**: Add all D-series generators as methods in `AttackGenerator.java`, following the existing B-series and C-series patterns. Register them in a new "Phase 2.8" block in `IntermittentEncryptionBenchmark.java` with a new `dispatchCombo()` method.

**Rationale**: Maintains consistency with existing code organization. The A-series uses inline generators in the benchmark class because they're simpler (parameterized versions of the same pattern), but the D-series are complex enough to warrant their own methods.

**Alternatives considered**:
- Keep D-series inline in the benchmark class → Would bloat the already-long benchmark class further
- Create a separate `ComboAttackGenerator.java` → Over-engineering for 8 methods; the existing `AttackGenerator` is the natural home

### Decision 5: Which variants survive from A-series

**Choice**: Only A9 (every_Nth + .tmp) and A12 (strip encrypt, no ext) survive. A5/A7/A11 are removed as signature-trivial (`.crypt`/`.enc` are in the suspicious extensions list). A4/A6/A8/A10 are removed as structural duplicates of A5/A7/A9/A11.

**Rationale**: `.tmp` is NOT in `SuspiciousExtensions.java` and A12 uses no extension at all — both genuinely test statistical detection. A9's every-Nth pattern and A12's strip-encrypt pattern are unique attack strategies not covered elsewhere.

## Risks / Trade-offs

**[Risk] D-series cases might not achieve the "combo" goal** → The weighted Euclidean distance naturally weights `peak_burst_velocity` heavily (weight 5.0). Even with suppressed burst signals, this feature may still dominate. **Mitigation**: Calibrate D-series parameters to explicitly reduce burst velocity (use wider time windows, add timing jitter) and verify via z-score analysis in Phase 4 output. If a single feature still dominates, adjust parameters.

**[Risk] Removing AH1-AH3 loses wall_clock_anomaly coverage** → The after-hours variants are the only cases that specifically test off-hours detection. **Mitigation**: D4 (BlackCat) and D8 (Akira) both include off-hours timing, providing replacement coverage for `wall_clock_anomaly` in a combo context rather than as a standalone signal.

**[Risk] Removing C1 loses extreme-volume coverage** → C1 tested 50K-70K operations in a burst. **Mitigation**: C2 (30K-50K adds), C4 (60K-80K mixed), and C5 (escalating waves) already cover extreme volumes with more diverse patterns. C1 was a pure-modification subset of what ORIG_lockbit already tests.

**[Risk] Large removal count (51 cases) reduces test diversity** → Removing 40% of cases could leave gaps. **Mitigation**: Every removed case was either signature-trivial (caught before statistical scoring) or structurally identical to a surviving case. The 8 new D-series cases add genuinely new detection pathways (combo-feature) not previously tested. Net result: fewer cases but higher diversity per case.

**[Risk] Benchmark results change, making historical comparison difficult** → The detection rate and specific scores will change. **Mitigation**: Document the before/after in README.md. The removal is intentional to focus the suite on diverse detection pathways.
