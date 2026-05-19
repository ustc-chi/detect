## Context

The RCF benchmark generates normal baseline rounds via `FilesystemState.evolveNormalRound()`. These rounds simulate everyday NAS filesystem activity for 10 users across 300K files. The current implementation produces only modify/add/delete operations, timestamps exclusively during business hours, and relatively uniform activity levels (activityLevel = 2–10% of 300K files, with a 20% chance of dropping to 40% of that).

This sterility creates two problems:
1. **Over-sensitive features**: `rename_correlation` and `wall_clock_anomaly` have zero or near-zero values across all baseline rounds, making any non-zero signal appear anomalous. This contributes to the 100% false positive rate on irregular normals.
2. **Narrow baseline distributions**: `peak_burst_velocity` and `temporal_uniformity` have tight distributions because normal activity is too homogeneous. Real filesystems show natural variation.

The existing irregular normal rounds (Phase 1.5) handle edge cases like batch_compile and after_hours_burst, but the *regular* baseline rounds should also include mild versions of these patterns.

## Goals / Non-Goals

**Goals:**
- Add 5–15% rename operations to normal rounds (versioning renames: `_v2`, `_backup`, `_old`, `_final`, `_copy`)
- Add after-hours timestamps to ~15–25% of normal round operations (hours 18–23 or 5–8)
- Add natural volatility: occasional high-activity bursts, quiet days, and concentrated mini-bursts
- Maintain 0% false positive rate on vanilla normal rounds
- Maintain ≥99% attack detection rate (67/68 or better)
- Keep the change localized to `evolveNormalRound()` and related data regeneration

**Non-Goals:**
- Changing attack generation code
- Changing feature extraction or detection logic
- Changing weights or thresholds (these adapt via the baseline recalibration)
- Eliminating irregular normal false positives (that requires detection logic changes)
- Adding new attack test cases

## Decisions

### Decision 1: Rename via delete+add pairs (same as mass_rename irregular pattern)

Renames will be implemented as delete the old path + add a new path with a versioning suffix, consistent with how the `mass_rename` irregular pattern and real ransomware renames work. This means:
- Old path removed from state, new path added to state
- Two DiffEntry records: one `deleted` (with `Instant.MAX` timestamp) and one `added`
- This naturally feeds the `rename_correlation` feature with legitimate baseline signal

**Alternatives considered:**
- Single `modified` entry with path change: Would not exercise `rename_correlation` at all, defeating the purpose.

### Decision 2: After-hours activity mixed into business-hour rounds

Rather than creating separate "after-hours rounds", inject 15–25% of operations with timestamps outside business hours into the same round. This mirrors reality: a daily snapdiff captures both business-hours and off-hours activity.

**Implementation**: After computing the `hoursWindow` for the round, allocate a fraction (15–25%) of operations with timestamps shifted to evening (18–23h) or early morning (5–8h) relative to `dayStart`.

**Alternatives considered:**
- Separate after-hours-only rounds: Would create unrealistic round profiles (100% after-hours).
- Changing dayStart itself: Would move all operations to after-hours, not just a fraction.

### Decision 3: Three volatility tiers via probability

Activity level selection will use three tiers:
- **Quiet day** (20% probability): 40% of normal ops, spread over wider time window
- **Normal day** (60% probability): current behavior (unchanged)
- **Busy day** (20% probability): 200% of normal ops, with a concentrated mini-burst (50–200 ops in 5–15 minutes)

This creates a realistic trimodal distribution. The mini-burst on busy days ensures the baseline captures some burst velocity, broadening the `peak_burst_velocity` distribution.

**Alternatives considered:**
- Log-normal distribution for activity level: More realistic statistically but harder to reason about. Fixed tiers are simpler and achieve the goal.
- No volatility (keep current): Defeats the purpose.

### Decision 4: Minimal changes to evolveNormalRound()

All three enhancements (renames, after-hours, volatility) will be added directly to the existing `evolveNormalRound()` method. No new methods or classes needed. The method signature stays the same.

### Decision 5: Seed stability

The SEED=42 remains unchanged. Adding new code paths to `evolveNormalRound()` will change the sequence of `random.nextInt()` calls, so all normal round data will change. This is expected and acceptable — the benchmark data gets fully regenerated.

## Risks / Trade-offs

- **Risk: Baseline statistics shift significantly** → Mitigation: The benchmark re-calibrates thresholds from the new baseline. If detection rate drops below 99%, we adjust the proportions (e.g., reduce rename percentage or after-hours fraction).
- **Risk: False positive rate on vanilla normals increases** → Mitigation: The 0% FP rate is a hard requirement. If any vanilla normal triggers above threshold, we reduce volatility/renames and regenerate.
- **Risk: Threshold change causes B3_p70 to move further from detection** → Mitigation: B3_p70 is already the only miss at 8.44 vs threshold 8.77. The new baseline may actually help (broader distribution → potentially higher threshold tolerance). If it worsens, we accept the same miss rate.
- **Risk: Irregular normal FP rate changes** → Mitigation: Irregular normals already have 100% FP. This change shouldn't make them worse (may actually improve if the baseline becomes more representative).
- **Trade-off**: All benchmark data files must be regenerated. This is a one-time cost (~7s generation time).
