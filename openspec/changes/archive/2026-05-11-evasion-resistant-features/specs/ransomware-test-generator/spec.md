## MODIFIED Requirements

### Requirement: Test round schedule with 14-feature extraction
The RansomwareTestGenerator SHALL produce test rounds using the 14-feature RansomwareFeatureExtractor with the new feature indices and consolidated deletion_intensity. The number of rounds and attack schedule remain unchanged (40 rounds: 28 normal + 12 attacks).

#### Scenario: 40 rounds generated with 14-feature extraction
- **WHEN** RansomwareTestGenerator.generate() is called with seed=42
- **THEN** 40 JSON files SHALL be produced, with feature vectors containing 14 elements per round

#### Scenario: Normal rounds score below threshold with new weights
- **WHEN** the 28 normal rounds are scored with the new 14-feature scorer and rebalanced weights
- **THEN** all 28 SHALL score below the anomaly threshold (0 false positives)
