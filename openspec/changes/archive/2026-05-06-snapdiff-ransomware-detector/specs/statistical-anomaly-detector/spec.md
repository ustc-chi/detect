# ADDED Requirements: statistical-anomaly-detector

### Requirement: Mahalanobis-based anomaly scoring with self-updating baseline
The system SHALL compute an anomaly score using the Mahalanobis distance on the 9-feature vector and maintain a self-updating baseline over a moving window of 7-20 rounds, recomputing daily.

#### Scenario: Baseline update on new data
- WHEN the system processes a new batch and the window exceeds 20 rounds
- THEN the oldest round is dropped and the baseline is recomputed

#### Scenario: Anomaly is detected
- WHEN the Mahalanobis distance crosses the percentile threshold derived from the baseline distribution
- THEN the system SHALL flag an anomaly and provide per-feature z-scores for explanation
