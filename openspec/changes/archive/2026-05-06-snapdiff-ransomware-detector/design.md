## Context

This design documents how to implement the snapdiff-ransomware-detector change. It replaces the legacy RC-based detector with a lightweight, unsupervised, statistics-based detector that operates on 9 ransomware-specific features derived from snapdiff data. The detector is designed to be self-updating, uses a moving baseline, and provides explainable per-feature deviations in addition to an overall anomaly score.

## Goals / Non-Goals

- Goals:
- Detect ransomware-like changes in Snapdiff data using weighted Euclidean distance with a self-updating baseline.
- Provide explainable per-feature deviations to justify anomalies.
- Keep dependencies minimal (no ML libraries; pure Java/Java-friendly computations).
- Non-Goals:
- Not a supervised classifier; no labels or training data required.
- No external ML services or cloud dependencies.
- Do not expand beyond 9 ransomware-specific features unless explicitly changed by proposal/specs.

## Decisions

- Architecture: Single-pass through Snapdiff records to extract 9 features O(N).
- Baseline: Maintain mean[9] and std[9] for the rolling window of rounds (7-20, recomputed daily).
- Scoring: Use weighted Euclidean distance (z-score normalized, per-feature weighted) as the primary anomaly score; provide per-feature z-scores for explanation. Default weights are tuned for ransomware detection with modification_ratio and suspicious_extension_ratio weighted highest. Weights are configurable via CLI.
- Thresholding: Use percentile-based calibration on baseline scores to flag anomalies.
- Edge cases: Define behavior for empty data, single-record windows, and velocity calculations as described in the design decisions.
- Features: Implement the 9 ransomware-specific features described in the design decisions document.
- Extensions: Include a configurable list of suspicious extensions; update related feature calculations accordingly.
- Artifacts: Create 3 core specs for implementation: ransomware-feature-extraction, statistical-anomaly-detector, ransomware-test-generator.

## Risks / Trade-offs

- Risk: Sensitivity to window size could affect false positives/negatives. Mitigation: cap window, daily baseline recomputation, and percentile-based thresholding.
- Risk: Weighted Euclidean assumes feature independence. Mitigation: z-score normalization prevents scale bias; weights are tunable via CLI for domain-specific adjustment.
- Trade-off: No ML library usage reduces complexity and enables standalone Java deployment, at potential cost of missing multivariate correlations.

## Migration Plan

- Phase 1: Introduce 3 new specs and the 9-feature extractor in the codebase. Remove legacy RC components and old accelerators.
- Phase 2: Implement statistical-anomaly-detector with self-updating baseline and per-feature explanations.
- Phase 3: Integrate ransomware-test-generator for synthetic data validation.
- Phase 4: Run unit tests and end-to-end validation with synthetic datasets and Snapdiff samples.
- Phase 5: Deploy with fallback rollback in case of anomalous baseline recalibration.

## Open Questions

- None at this time; design decisions align with the proposal.
