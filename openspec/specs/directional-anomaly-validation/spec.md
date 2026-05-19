## ADDED Requirements

### Requirement: Directional anomaly validation
After the symmetric scorer flags a round as anomalous (score > threshold), the system SHALL compute a direction ratio to determine whether the anomaly is predominantly caused by below-baseline (anti-ransomware) deviations. The direction ratio SHALL be:

```
E_up   = Σᵢ wᵢ × max(0, zᵢ)²
E_down = Σᵢ wᵢ × max(0, -zᵢ)²
ratio  = E_down / (E_up + E_down + ε)
```

where `zᵢ` are the per-feature z-scores, `wᵢ` are the scorer weights, and `ε = 1e-10`.

When `ratio > directionThreshold`, the system SHALL reverse the anomaly verdict to NORMAL.

#### Scenario: Quiet day reversal
- **WHEN** a round produces score > threshold AND 85% of the weighted z² energy comes from below-baseline deviations (ratio = 0.85)
- **AND** directionThreshold is 0.75
- **THEN** the system SHALL reverse the verdict to NORMAL

#### Scenario: Ransomware confirmed
- **WHEN** a round produces score > threshold AND 90% of the weighted z² energy comes from above-baseline deviations (ratio = 0.10)
- **AND** directionThreshold is 0.75
- **THEN** the system SHALL confirm the ANOMALY verdict

#### Scenario: Borderline case stays anomalous
- **WHEN** a round produces score > threshold AND the ratio equals exactly 0.75
- **THEN** the system SHALL confirm the ANOMALY verdict (strict greater-than comparison)

#### Scenario: Score below threshold skips validation
- **WHEN** a round produces score ≤ threshold
- **THEN** the directional validation SHALL NOT execute, and the verdict is NORMAL as usual

### Requirement: DirectionalValidator class
A `DirectionalValidator` class SHALL encapsulate the direction ratio computation and reversal decision. It SHALL accept the scorer's weights array and a configurable threshold in its constructor.

#### Scenario: Construction with threshold
- **WHEN** DirectionalValidator is constructed with weights and threshold 0.75
- **THEN** subsequent calls to `validate(zScores)` SHALL use those weights and threshold

#### Scenario: Validation returns result with details
- **WHEN** `validate(zScores)` is called with an array of 12 z-scores
- **THEN** the return value SHALL contain: `reversed` (boolean), `ratio` (double), `E_up` (double), `E_down` (double), and `topDeviations` (list of feature names with z-scores and direction)

### Requirement: CLI parameter for direction threshold
The `RansomwareDetectorCli` SHALL accept a `--direction-threshold` parameter with a double value. The default SHALL be 0.75. A value of 0 SHALL disable directional validation entirely.

#### Scenario: CLI with custom direction threshold
- **WHEN** `RansomwareDetectorCli` is invoked with `--direction-threshold 0.80`
- **THEN** the DirectionalValidator SHALL use 0.80 as the reversal threshold

#### Scenario: CLI with direction threshold 0 disables validation
- **WHEN** `RansomwareDetectorCli` is invoked with `--direction-threshold 0`
- **THEN** directional validation SHALL NOT execute; all anomaly verdicts stand unchanged

#### Scenario: CLI with direction threshold out of range
- **WHEN** `RansomwareDetectorCli` is invoked with `--direction-threshold 1.5`
- **THEN** the CLI SHALL print an error message and exit with code 1

### Requirement: Reversal logging
When a verdict is reversed from ANOMALY to NORMAL by directional validation, the system SHALL log a WARNING containing: the original score, the direction ratio, and the top-5 feature deviations with their z-scores and direction (above/below baseline).

#### Scenario: Reversal produces log entry
- **WHEN** a round with score 8.5 and ratio 0.88 is reversed
- **THEN** a WARNING log SHALL be emitted containing "Directional validation reversed anomaly", the score 8.5, the ratio 0.88, and the top-5 features with z-scores

### Requirement: Reversed rounds excluded from self-learning
Rounds whose anomaly verdict is reversed by directional validation SHALL NOT be added to the self-learning window.

#### Scenario: Reversed round not added to baseline
- **WHEN** a round exceeds the score threshold but is reversed by directional validation
- **THEN** the round SHALL NOT be added to the self-learning window, and the baseline statistics SHALL NOT be recalculated

#### Scenario: Confirmed anomaly not added to self-learning
- **WHEN** a round exceeds the score threshold and is confirmed as ANOMALY by directional validation (or validation is disabled)
- **THEN** the round SHALL NOT be added to the self-learning window (existing behavior unchanged)

#### Scenario: Normal round still added to self-learning
- **WHEN** a round does NOT exceed the score threshold (NORMAL by score, no validation needed)
- **THEN** the round SHALL be added to the self-learning window (existing behavior unchanged)
