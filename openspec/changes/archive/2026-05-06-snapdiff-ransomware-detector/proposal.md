## Why

Ransomware detection is critical for early warning and automated response in Snapdiff-driven workflows. This change introduces a GOAL-ORIENTED, autonomous detector that leverages statistical anomaly detection on 9 ransomware-specific features derived from snapdiff records. It replaces the previous RC-based detector stack with a lightweight, self-updating, unsupervised approach that requires no external ML libraries and adapts baseline behavior over time.

## What Changes

- Replace ALL existing RCF-based classes and old 17-feature extractor with a focused ransomware-detection pipeline built around 9 robust features derived from snapdiff data.
- Implement a self-updating baseline using a moving window of 7-20 rounds (recomputed daily) and Mahalanobis distance-based scoring with per-feature z-scores for explanation.
- Introduce 3 new capabilities as first-class specs:
  - ransomware-feature-extraction: Extract 9 ransomware-specific features from snapdiff data.
  - statistical-anomaly-detector: Detect anomalies via Mahalanobis distance and z-scores with a self-updating baseline.
  - ransomware-test-generator: Generate synthetic data for normal and ransomware scenarios to validate the detector.
- Update configuration to track a configurable list of suspicious extensions used to inform feature 7 (extension_diversity and suspicious_extension_ratio).

- Remove dependency on RCF from pom.xml and preserve reusable models: SnapdiffParser, SnapdiffRecord, SnapdiffFile models remain.

## Test Cases

### Test Cases

The test generator SHALL produce realistic synthetic snapdiff datasets that exercise the full detection pipeline. All generated test snapdiff files MUST be saved to disk for manual review.

### Phase 1: Normal Baseline Generation
- Generate 7 rounds of normal snapdiff files
- Each round SHALL contain tens of thousands of records (10,000–50,000 per round)
- The snapdiff SHALL represent a filesystem of approximately 200,000–500,000 files
- Each round SHALL reflect a proximate change ratio of ~5% (i.e., ~5% of the total filesystem changed between snapshots)
- Normal behaviors MUST comprehensively cover:
  - **File edits**: Users modifying documents, code, configs (predominantly "modified" type)
  - **File creation**: New documents, logs, temporary files, downloaded files ("added" type)
  - **File deletion**: Cleanup of temp files, log rotation, old file removal ("deleted" type)
  - **Directory operations**: New project directories, reorganized folder structures
  - **Mixed file types**: Documents (.docx, .xlsx, .pptx, .pdf), code (.java, .py, .js, .sh), images (.jpg, .png, .gif), configs (.conf, .yaml, .json, .xml), logs (.log), databases (.sql, .db), media (.mp3, .mp4, .wav), archives (.zip, .tar, .gz)
  - **Multi-user activity**: Changes distributed across 5–10 user directories (/vol/share/user1 through /vol/share/user10), each user active in their own subtree
  - **Time distribution**: Changes spread across the full day (8–12 hour span), reflecting human-paced work patterns
  - **Realistic size distribution**: Small files (1KB–100KB) predominating, with occasional large files (1MB–100MB), and rare very large files (100MB–1GB)
- The 7 rounds MUST be internally consistent: files added in round 1 can be modified in round 3, deleted in round 5; file paths should persist across rounds to simulate a real evolving filesystem
- Each round's timestamps SHOULD be spaced ~24 hours apart
- All 7 files saved as snapdiff JSON: `test-baseline/round_001.json` through `test-baseline/round_007.json`

### Phase 2: Ransomware Attack Simulation
After the 7 normal rounds, generate at least 2 additional rounds simulating ransomware attacks using DIFFERENT attack patterns:

**Round 8 — Attack Pattern A: Mass Encryption (LockBit-style)**
- 8,000–20,000 files modified (encrypted) rapidly across ALL user directories
- modification_ratio > 0.80 (vast majority of operations are modifications)
- Files renamed with suspicious extensions (.locked, .encrypted, .crypt)
- Ransom notes dropped (README_LOCKED.txt, HOW_TO_DECRYPT.txt) in multiple user directories
- change_velocity orders of magnitude higher than baseline (> 500 ops/hour)
- user_spread = all users (1–10) affected
- Files modified across all extension types (documents, images, code, databases)
- Size of encrypted files slightly larger than originals (encryption overhead)
- Time compressed into 1–2 hours (machine-speed execution)
- Saved as `test-attack/round_008_lockbit_style.json`

**Round 9 — Attack Pattern B: Wiper/Destructive (Ryuk-style)**
- 5,000–15,000 files deleted rapidly
- deletion_ratio > 0.60 (mostly deletions rather than modifications)
- A smaller number of files modified (partial encryption before wiper kills the process)
- Ransom note dropped in root shared directory
- change_velocity extremely high (> 1000 ops/hour)
- user_spread = all users affected
- bytes_removed very high (GB-scale data destroyed)
- Time compressed into 30–60 minutes
- Saved as `test-attack/round_009_ryuk_style.json`

### Phase 3: Validation Assertions
The test harness SHALL verify:
- All 7 baseline rounds produce feature vectors within normal ranges
- Round 8 (LockBit) is flagged as ANOMALY with high confidence
  - modification_ratio z-score > 3.0
  - user_spread z-score > 3.0
  - change_velocity z-score > 3.0
  - suspicious_extension_ratio z-score > 3.0
- Round 9 (Ryuk) is flagged as ANOMALY with high confidence
  - deletion_ratio z-score > 3.0
  - bytes_removed z-score > 3.0
  - change_velocity z-score > 3.0
- Explanation output identifies the correct deviating features for each attack pattern
- Baseline self-update after processing round 8 produces updated statistics
- All generated JSON files are valid and parseable by SnapdiffParser

## Capabilities

### New Capabilities
- `ransomware-feature-extraction`: Extract the 9 ransomware-specific features from snapdiff data.
- `statistical-anomaly-detector`: Mahalanobis-based anomaly scoring with self-updating baseline.
- `ransomware-test-generator`: Generate synthetic datasets for normal and ransomware scenarios.

### Modified Capabilities
- None specified at this time.

## Impact

- Architectural overhaul of the detector pipeline (single-pass feature extraction, moving baseline, Mahalanobis scoring).
- Removal of RCF dependency and legacy detectors; consolidation around snapdiff-centric models.
- New configurables for suspicious file extensions and per-feature thresholds for explainability.
