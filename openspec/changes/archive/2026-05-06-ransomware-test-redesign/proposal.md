## Why

The current test suite and feature/weight configuration have three critical weaknesses revealed by analysis against the ransomware encryption research document:

1. **Over-reliance on signature detection**: 8/11 current attacks (73%) are caught by signature pre-check (`.enc`, `.locked`, `.encrypted`, `.crypt`, ransom note names). The statistical detector is barely exercised, and all 3 "stealthy" attacks (no obvious indicators) are **missed** (scores 2.8, 4.2, 6.8 against threshold ≈7.0).

2. **Critically underweighted features**: `size_std_dev` (weight 0.06) and `modification_ratio` (weight 0.22) are the two strongest statistical signals for extension-free encryption — encryption produces uniformly consistent size changes across files and near-1.0 modification ratios. Their low weights make statistical detection of stealthy attacks nearly impossible.

3. **Unrealistic attack modeling**: Current attacks spread operations over 3600s+. Real ransomware operates in 30-300 second bursts (LockBit <2 min, Conti <15 min, Ryuk <10 min). No attacks model Conti's size-tiered strategy, LockBit's 4KB fast mode, BlackCat's SmartPattern, extension-preserving encryption, or Cl0p's companion files — all well-documented behaviors from the research.

## What Changes

- **Add 2 new features** to the 11-feature vector (total 13):
  - `burst_mod_purity` (index 11): In the peak 5-minute burst window, fraction of operations that are modifications. Ransomware bursts: 0.85-1.0. Normal bursts: 0.3-0.5. Weight: 5.0.
  - `file_type_concentration` (index 12): Maximum fraction of modifications targeting any single file extension. Ransomware targeting .docx: 0.5-0.8. Normal: 0.1-0.3. Weight: 2.0.

- **Rebalance feature weights** based on ransomware filesystem behaviors:
  - `modification_ratio`: 0.22 → **2.5** (encryption pushes ratio to 0.8-0.95)
  - `size_std_dev`: 0.06 → **3.0** (encryption produces LOW variance — uniformly consistent changes)
  - `high_value_ext_ratio`: 0.41 → **2.0** (database/document targeting signal)
  - `extension_diversity`: 0.27 → **0.8** (extension introduction/removal signal)
  - `directory_spread`: 1.01 → **1.5** (cross-directory coverage)
  - All other weights remain similar

- **Redesign all 12 attack rounds** to match documented ransomware filesystem manipulation behaviors, with attack operations concentrated in realistic burst windows (30-300 seconds):

  **Group A — No extension changes, no ransom notes (10 rounds):**
  1. LockBit Fast Mode — 4KB per file, 90-120s burst, no ext change
  2. Conti Size-Tiered — small/medium/large file tiers, no ext change
  3. Extension-Preserving Mass Encryption — +2-4% across all files, no ext change
  4. Database-Priority Targeted — databases first then documents, no ext change
  5. Single-User Rapid Encryption — one user's files, 30-60s burst, no ext change
  6. Slow Distributed Encryption — micro-bursts of ~100 ops with gaps, hardest
  7. Creeping Shrink Encryption — -10% to -20% partial encryption, no ext change
  8. Random Extension (REvil-style) — .a7x9k2m4, not in suspicious list
  9. Companion Key Files (Cl0p-style) — .key companions alongside originals
  10. Staged Encryption (WannaCry-style) — temp file staging, .WNCRY not in suspicious list

  **Group B — With obvious indicators (2 rounds, positive controls):**
  11. Mass Encryption with .lockbit extension
  12. Ransom Note Drop

- **BREAKING**: Feature vector expands from 11 to 13 dimensions. All downstream consumers (scorer, detector, threshold, baseline statistics, weight optimizer, CLI) must handle 13 features.

## Capabilities

### New Capabilities
- `burst-mod-purity`: New feature measuring modification purity within the peak burst window — what fraction of burst operations are modifications vs adds/deletes
- `file-type-concentration`: New feature measuring maximum per-extension modification concentration — detects targeted attacks on specific file types
- `redesigned-attack-rounds`: Complete replacement of all 12 attack round implementations matching documented ransomware filesystem manipulation behaviors

### Modified Capabilities
- `ransomware-feature-extraction`: Feature vector expands from 11 to 13 dimensions; extractor must compute 2 new features alongside existing 11
- `statistical-anomaly-detector`: WeightedEuclideanScorer, BaselineStatistics, AnomalyThreshold, and CLI must handle 13-feature vectors; default weights array expanded and rebalanced
- `ransomware-test-generator`: RansomwareTestGenerator and AttackGenerator completely rewritten with 12 new attack patterns and realistic burst timing

## Impact

- **RansomwareFeatureVector**: FEATURE_COUNT 11→13, new fields + getters + array indices
- **RansomwareFeatureExtractor**: Must compute `burst_mod_purity` and `file_type_concentration` during single-pass extraction; requires tracking per-timestamp operation type counts
- **WeightedEuclideanScorer**: N=11→13, DEFAULT_WEIGHTS array expanded with 2 new weights
- **BaselineStatistics**: Handles 13-feature vectors (generic, minimal change)
- **AnomalyThreshold**: Handles 13-feature vectors (generic, minimal change)
- **RansomwareDetector**: Passes 13-feature vectors (generic, minimal change)
- **AttackGenerator**: Complete rewrite — 12 new attack methods replacing all 11 existing ones
- **RansomwareTestGenerator**: Updated round schedule (12 attacks at new round positions), attack type dispatch
- **FilesystemState**: May need size-tier file querying methods for Conti-style attacks
- **CLI / WeightOptimizerCli**: Updated weight parameter descriptions and defaults
- **README**: Updated feature table, weight table, attack pattern table, and detection results
