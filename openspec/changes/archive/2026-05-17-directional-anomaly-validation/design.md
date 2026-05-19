## Context

The current anomaly detection pipeline uses a symmetric weighted Euclidean distance (`score = √(Σ w_i × z_i²)`) where `z_i` is the per-feature z-score relative to baseline median/MAD. Because z² squares both positive and negative deviations, a round with many features far *below* baseline (e.g., an extremely quiet day) can produce a high anomaly score despite having no ransomware indicators. The core scorer should remain unchanged (it correctly measures "distance from normal"), but a post-prediction validation layer can distinguish the *direction* of the deviation.

## Goals / Non-Goals

**Goals:**
- Reverse anomaly verdicts that are predominantly caused by below-baseline deviations (anti-ransomware direction)
- Preserve all existing detection capability — no ransomware attack should be reversed
- Make the direction threshold configurable via CLI (`--direction-threshold`, default 0.75)
- Log reversal events with enough detail for operator investigation (score, ratio, top-5 z-scores)
- Prevent reversed rounds from polluting the self-learning baseline

**Non-Goals:**
- Changing the core `WeightedEuclideanScorer` scoring formula (it stays symmetric)
- Per-feature directional encoding (e.g., treating inter_op_time_cv differently from modification_ratio) — future consideration
- Handling the 16/16 irregular-normal false positives (batch_compile, db_checkpoint, etc.) — these are above-baseline and would not be affected by direction validation
- Replacing threshold tuning with direction tuning — these are complementary mechanisms

## Decisions

### Decision 1: Post-prediction validation vs. per-feature directional scoring

**Chosen**: Post-prediction validation layer.

**Alternatives considered**:
- **Per-feature directional scoring**: Replace `z²` with `max(0, direction_i × z)²` where `direction_i` is +1 for most features and -1 for inter_op_time_cv and per_type_entropy. More principled but more invasive — changes the core scorer, changes all threshold calibrations, requires re-running the full benchmark to verify no regressions.
- **Hybrid scoring**: Use symmetric scoring but with a damping factor for below-baseline deviations. Adds a tunable parameter to the scorer itself.

**Rationale**: Post-prediction validation is the least invasive change. The scorer stays untouched, existing thresholds stay valid, and the validation only activates for rounds that already exceed the threshold. If the validation proves insufficient, per-feature directional scoring can be layered on top later without conflict.

### Decision 2: Energy ratio as the direction metric

**Chosen**: `ratio = E_down / (E_up + E_down + ε)` where `E_up = Σ w_i × max(0, z_i)²` and `E_down = Σ w_i × max(0, -z_i)²`.

**Alternatives considered**:
- **Simple count**: "How many of the top-5 deviations are below baseline?" — too coarse, ignores magnitude.
- **Weighted z-sum**: `Σ w_i × z_i` — a single massive positive z (from burst velocity) would dominate even if everything else is negative, making the sum always positive for borderline cases.

**Rationale**: The energy ratio naturally accounts for both the weight and magnitude of each feature's deviation. It produces values in [0, 1] making the threshold intuitive: 0.75 means "75% of the anomaly energy is anti-ransomware direction."

### Decision 3: Default threshold = 0.75

**Chosen**: 0.75 (conservative).

**Rationale**: At 0.75, three times more energy must be below-baseline than above. No known ransomware variant — including quiet targeted attacks like B6 (selective_high_value) or D6 (Play intermittent) — produces a ratio above ~0.40. Meanwhile, a genuine quiet day produces ratio ≈ 0.85-0.95. The 0.75 default provides a wide safety margin on both sides.

### Decision 4: New class `DirectionalValidator` vs. embedding in `RansomwareDetector`

**Chosen**: Separate class `DirectionalValidator`.

**Rationale**: Single responsibility. The validator needs the scorer's weights and the z-scores, but doesn't need the detector's self-learning logic or threshold. A separate class is easier to unit test in isolation.

### Decision 5: Reversed rounds excluded from self-learning

**Chosen**: Do not add reversed rounds to the self-learning window.

**Rationale**: A reversed round is still statistically unusual (it exceeded the score threshold). Adding it to the baseline would pull medians toward an unusual state, potentially creating blind spots. The self-learning window should only absorb genuinely normal behavior.

### Decision 6: ε value

**Chosen**: ε = 1e-10 (negligible).

**Rationale**: The denominator is `E_up + E_down`, which equals `score²`. If `score² = 0`, the round wouldn't have exceeded the threshold in the first place, so the division never actually occurs at zero. The ε is purely defensive for the floating-point edge case.

## Risks / Trade-offs

- **[Risk] A novel attack that looks like a quiet day could be reversed** → Mitigated by the conservative 0.75 threshold. Any attack with meaningful ransomware indicators produces E_up >> E_down. The only scenario where this fails is an attack that simultaneously depresses ALL features below baseline while still being destructive — there is no known ransomware family that operates this way (ransomware inherently creates file activity).

- **[Risk] The validation may not help with any current benchmark false positives** → Acknowledged. The 16/16 irregular-normal FPs are all above-baseline. The validation specifically targets a production scenario (extremely quiet days) not yet represented in the benchmark. Adding "extremely quiet day" test cases to the benchmark is included in the tasks.

- **[Risk] The CLI parameter adds tuning surface** → Mitigated by a safe default (0.75) and documentation that the parameter only affects rounds already flagged as anomalous.
