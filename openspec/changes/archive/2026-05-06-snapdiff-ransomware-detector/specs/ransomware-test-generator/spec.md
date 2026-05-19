# ADDED Requirements: ransomware-test-generator

### Requirement: Normal baseline generation
The system SHALL generate 7 rounds of normal snapdiff files representing realistic daily filesystem activity:
- Each round SHALL contain 10,000–50,000 diff records
- Total simulated filesystem size: ~200,000–500,000 files
- Proximate change ratio: ~5% of filesystem changed per round
- Changes distributed across 5–10 user directories
- Mix of operation types: modifications (~40-60%), additions (~20-35%), deletions (~15-25%)
- Time span per round: 8–12 hours of activity, ~24 hours between rounds
- Realistic file sizes: small files (1KB–100KB) predominating, occasional large files (1MB–100MB)
- File type diversity: at least 20 distinct extensions across categories (documents, code, images, configs, logs, databases, media, archives)
- Cross-round consistency: file paths persist and evolve across rounds (added in round N, modified in round N+2, etc.)

#### Scenario: Generate 7 rounds of normal baseline data
- WHEN the generator is invoked for baseline generation with a seed
- THEN 7 snapdiff JSON files are produced
- AND each file contains 10,000–50,000 diff records
- AND the change ratio per round is approximately 5%
- AND all files are saved to the configured output directory
- AND each file is valid JSON parseable by SnapdiffParser

#### Scenario: Cross-round consistency
- WHEN round 3 adds file /vol/share/user2/docs/report.docx
- THEN round 5 or later may modify that same file
- AND round 7 may delete that file
- AND path consistency is maintained across the 7-round sequence

### Requirement: Ransomware attack simulation
The system SHALL generate at least 2 snapdiff rounds simulating different ransomware attack patterns after the normal baseline:

#### Scenario: Mass encryption attack (LockBit-style)
- WHEN generating attack pattern A
- THEN 8,000–20,000 files are modified with high modification_ratio (> 0.80)
- AND files are renamed with suspicious extensions (.locked, .encrypted, .crypt)
- AND ransom notes (README_LOCKED.txt, HOW_TO_DECRYPT.txt) appear in multiple user directories
- AND change_velocity exceeds 500 ops/hour
- AND all user directories (1–10) are affected
- AND changes span all file extension types
- AND the time span is compressed to 1–2 hours
- AND the output is saved as a single snapdiff JSON file

#### Scenario: Destructive wiper attack (Ryuk-style)
- WHEN generating attack pattern B
- THEN 5,000–15,000 files are deleted with high deletion_ratio (> 0.60)
- AND bytes_removed reflects GB-scale data destruction
- AND change_velocity exceeds 1000 ops/hour
- AND all user directories are affected
- AND a ransom note appears in the root shared directory
- AND the time span is compressed to 30–60 minutes
- AND the output is saved as a single snapdiff JSON file

### Requirement: Test output persistence
The system SHALL save all generated snapdiff files to disk for manual review:
- Baseline files: `test-baseline/round_001.json` through `test-baseline/round_007.json`
- Attack files: `test-attack/round_008_lockbit_style.json`, `test-attack/round_009_ryuk_style.json`
- All files SHALL be valid JSON parseable by SnapdiffParser
- Files SHALL be human-readable (pretty-printed JSON)

### Requirement: Deterministic generation
The generator SHALL produce identical output for a given seed value, enabling reproducible test runs.
