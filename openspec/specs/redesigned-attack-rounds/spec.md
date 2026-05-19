## ADDED Requirements

### Requirement: LockBit fast mode attack (4KB per file)
The system SHALL provide an attack generator method that simulates LockBit v2/v3 fast encryption: 4000-6000 files modified in-place with a fixed +4KB size increase per file, NO extension change, operations concentrated in a 90-120 second burst window.

#### Scenario: LockBit fast mode generation
- **WHEN** generateLockBitFastMode is called with an attack time
- **THEN** 4000-6000 DiffEntry records of type "modified" SHALL be produced, each with size = original_size + random(4096, 8192), NO file extension change, and timestamps within 90-120 seconds of the attack time

### Requirement: Conti size-tiered encryption attack
The system SHALL provide an attack generator method that simulates Conti's 3-tier encryption strategy: files <1MB get full encryption (+3-5% size), files 1-5MB get header-only (+4-8KB fixed), files >5MB get partial encryption (+1-3% size). NO extension change. Operations concentrated in a 5-10 minute window.

#### Scenario: Conti size-tiered generation
- **WHEN** generateContiSizeTiered is called with an attack time
- **THEN** 3000-4000 DiffEntry records of type "modified" SHALL be produced with size changes determined by the file's original size tier, NO file extension change, and timestamps within 300-600 seconds of the attack time

### Requirement: Extension-preserving mass encryption attack
The system SHALL provide an attack generator method that encrypts 3000-5000 files across all users with +2-4% consistent size change while preserving original file extensions. Operations concentrated in a 3-5 minute window.

#### Scenario: Extension-preserving mass encryption generation
- **WHEN** generateExtensionPreservingMass is called with an attack time
- **THEN** 3000-5000 DiffEntry records of type "modified" SHALL be produced, each with size = original * (1.02 + random * 0.02), original extension preserved, timestamps within 180-300 seconds of attack time, targeting files across all 10 users

### Requirement: Database-priority targeted encryption attack
The system SHALL provide an attack generator method that first targets ALL database files (.db, .sql, .mdb, .bak, .csv), then ALL document files (.docx, .xlsx, .pdf), with +3-5% size change, NO extension change, in a 5-minute window.

#### Scenario: Database-priority targeted encryption generation
- **WHEN** generateDatabasePriority is called with an attack time
- **THEN** DiffEntry records SHALL be produced targeting database extensions first exhaustively, then document extensions, 2000-3000 total modifications, NO extension change, timestamps within 300 seconds of attack time, and high_value_ext_ratio SHALL exceed 0.8

### Requirement: Single-user rapid encryption attack
The system SHALL provide an attack generator method that encrypts ALL files from ONE randomly selected user with +2-5% size change, NO extension change, in a 30-60 second burst.

#### Scenario: Single-user rapid encryption generation
- **WHEN** generateSingleUserRapid is called with an attack time
- **THEN** DiffEntry records SHALL be produced targeting files from exactly 1 user, 2500-3500 modifications, NO extension change, timestamps within 30-60 seconds of attack time, and directory_spread SHALL be low (limited to ~10 directories)

### Requirement: Slow distributed encryption attack (evasion)
The system SHALL provide an attack generator method that modifies 2000 files across all users with +2-4% size change, distributed as micro-bursts: 10-second bursts of ~100 operations with 60-90 second gaps between bursts, total attack window of ~30 minutes. NO extension change.

#### Scenario: Slow distributed encryption generation
- **WHEN** generateSlowDistributed is called with an attack time
- **THEN** 2000 DiffEntry records of type "modified" SHALL be produced with timestamps forming micro-bursts (groups of ~100 ops within 10-second windows, separated by 60-90 second gaps), NO extension change, targeting files across all users

### Requirement: Creeping shrink encryption attack
The system SHALL provide an attack generator method that partially encrypts 2500 files resulting in -10% to -20% size decrease (simulating partial encryption with content removal). NO extension change. 5-minute burst window.

#### Scenario: Creeping shrink encryption generation
- **WHEN** generateCreepingShrink is called with an attack time
- **THEN** 2500 DiffEntry records of type "modified" SHALL be produced with size = original * (0.80 + random * 0.10), NO extension change, timestamps within 300 seconds of attack time

### Requirement: Random extension attack (REvil-style)
The system SHALL provide an attack generator method that modifies 2000-3000 files with +2-5% size change and renames them with random 8-character lowercase alphanumeric extensions (e.g., .a7x9k2m4). Extensions SHALL NOT match any in the SuspiciousExtensions default list. 3-minute burst window.

#### Scenario: Random extension attack generation
- **WHEN** generateRevilRandomExt is called with an attack time
- **THEN** 2000-3000 DiffEntry records SHALL be produced, each file renamed with a unique 8-character random lowercase alphanumeric extension, the new extensions SHALL NOT be in SuspiciousExtensions.DEFAULT_EXTENSIONS, timestamps within 180 seconds of attack time

### Requirement: Companion key files attack (Cl0p-style)
The system SHALL provide an attack generator method that modifies 2000 files in-place with +1-3% size change (no rename), and creates a companion .key file (200-500 bytes) for each modified file. 5-minute burst window.

#### Scenario: Companion key files attack generation
- **WHEN** generateClopCompanion is called with an attack time
- **THEN** 2000 DiffEntry "modified" records + 2000 DiffEntry "added" records SHALL be produced, original filenames preserved, companion files named as "<original_path>.key", timestamps within 300 seconds of attack time

### Requirement: Staged encryption attack (WannaCry-style)
The system SHALL provide an attack generator method that simulates WannaCry's staging: files are read, encrypted content written to temp files (.WNCRYT), then renamed to final extension (.WNCRY). 2000-3000 files, 5-minute window. The .WNCRY extension SHALL NOT be in SuspiciousExtensions.

#### Scenario: Staged encryption attack generation
- **WHEN** generateWannaCryStaged is called with an attack time
- **THEN** 2000-3000 DiffEntry records SHALL be produced with files renamed to .WNCRY extension, size = original * (1.02 + random * 0.03), timestamps within 300 seconds of attack time, and .WNCRY SHALL NOT trigger signature detection

### Requirement: Mass encryption with .lockbit extension (positive control)
The system SHALL provide an attack generator method that encrypts 4000-6000 files with +1-4% size change and renames to .lockbit extension. 2-3 minute burst. This SHALL trigger signature pre-check.

#### Scenario: Mass encryption with .lockbit generation
- **WHEN** generateMassEncryption is called with an attack time
- **THEN** 4000-6000 DiffEntry records SHALL be produced with .lockbit extension, size = original * (1.01 + random * 0.03), timestamps within 120-180 seconds of attack time, and .lockbit SHALL be in SuspiciousExtensions

### Requirement: Ransom note drop (positive control)
The system SHALL provide an attack generator method that drops 20-40 ransom note files with names matching known ransom note patterns (README_UNLOCK, HOW_TO_DECRYPT, etc.) across user directories. Padded to ~2000 total ops.

#### Scenario: Ransom note drop generation
- **WHEN** generateRansomNoteDrop is called with an attack time
- **THEN** 20-40 DiffEntry records of type "added" SHALL be produced with filenames matching RANSOM_NOTE_PATTERNS, plus padding to ~2000 total operations, and the ransom note filenames SHALL trigger signature detection

## ADDED Requirements

### Requirement: C1–C8 High-Volume Record Attack Generators
The AttackGenerator SHALL expose 8 new public methods following the signature `(Instant attackTime, double paddingRatio) → List<DiffEntry>` for generating high-volume attack variants:
- generateMassiveModifiedFlood (C1): 50K–70K modified records
- generateMassiveAddFlood (C2): 30K–50K added records
- generateMassiveDeleteFlood (C3): 20K–40K deleted records
- generateBalancedHighVolumeMix (C4): 60K–80K total mixed records
- generateMultiWaveEscalation (C5): 3 waves of escalating modified records
- generateAddedHeavyEncryption (C6): 40K–60K added + 40K–60K deleted records
- generateDeleteHeavyDestruction (C7): 30K–50K deleted + 5K–10K shrunk modified records
- generateBaselineMimickingVolume (C8): 30K–40K total records spread over 6–8 hours

Each method SHALL use the existing computePadding() and generateNormalPadding() pattern for noise injection.

#### Scenario: All 8 high-volume generators produce correct record counts
- **WHEN** any C1–C8 generator is called with paddingRatio=0.20
- **THEN** the returned DiffEntry list SHALL contain at least the specified minimum attack records (before padding) for that variant

#### Scenario: High-volume generators integrate with existing padding pattern
- **WHEN** any C1–C8 generator is called with a valid paddingRatio
- **THEN** padding records SHALL be generated via the same generateNormalPadding() method used by existing attack generators

#### Scenario: High-volume generators handle FilesystemState correctly
- **WHEN** any C1–C8 generator modifies/deletes files
- **THEN** the FilesystemState SHALL reflect the changes (added files added, deleted files removed, modified files with updated sizes)
