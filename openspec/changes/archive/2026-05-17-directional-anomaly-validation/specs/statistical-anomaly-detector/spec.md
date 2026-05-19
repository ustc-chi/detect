## MODIFIED Requirements

### Requirement: Anomaly detection pipeline with directional validation
The anomaly detection pipeline in `RansomwareDetector` SHALL include an optional directional validation phase between score computation and verdict finalization. The pipeline flow SHALL be:

1. Signature pre-check (unchanged)
2. Feature extraction (unchanged)
3. Symmetric scoring via WeightedEuclideanScorer (unchanged)
4. **Directional validation** (new, when score > threshold):
   - Compute z-scores for all features
   - Pass z-scores and scorer weights to DirectionalValidator
   - If validator reverses the verdict → NORMAL (with logging, no self-learning)
   - If validator confirms → ANOMALY (existing behavior)
5. Self-learning window update (unchanged for normal rounds, excluded for reversed rounds)

#### Scenario: Full pipeline with quiet day
- **WHEN** a quiet day round exceeds the score threshold AND directional validation reverses it
- **THEN** the final verdict is NORMAL, a WARNING is logged, and the round is excluded from self-learning

#### Scenario: Full pipeline with ransomware
- **WHEN** a ransomware round exceeds the score threshold AND directional validation confirms it
- **THEN** the final verdict is ANOMALY with top-5 deviation features recorded (existing behavior)
