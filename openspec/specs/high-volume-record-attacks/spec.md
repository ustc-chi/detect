## ADDED Requirements

### Requirement: C1 Massive Modified Flood attack generator
The system SHALL provide an attack generator method `generateMassiveModifiedFlood(Instant attackTime, double paddingRatio)` that simulates a high-volume encryption attack producing 50,000–70,000 DiffEntry records of type "modified" across all 10 users. Each modified file SHALL have size = original * (1.02 + random * 0.02), NO file extension change, and timestamps distributed within a 300–600 second burst window.

#### Scenario: C1 Massive Modified Flood generation
- **WHEN** generateMassiveModifiedFlood is called with an attack time and padding ratio
- **THEN** 50,000–70,000 DiffEntry records of type "modified" SHALL be produced, each with size = original * (1.02 + random * 0.02), NO file extension change, timestamps within 300–600 seconds of the attack time, targeting files across all 10 users, plus normal padding computed from the padding ratio

### Requirement: C2 Massive Add Flood attack generator
The system SHALL provide an attack generator method `generateMassiveAddFlood(Instant attackTime, double paddingRatio)` that creates 30,000–50,000 new encrypted file copies (DiffEntry type "added") with randomized names, leaving original files intact. Each new file SHALL have size = 1024 + random(1, 102400) and a randomized filename in an existing user directory. Timestamps within a 300–600 second burst window.

#### Scenario: C2 Massive Add Flood generation
- **WHEN** generateMassiveAddFlood is called with an attack time and padding ratio
- **THEN** 30,000–50,000 DiffEntry records of type "added" SHALL be produced with new file paths under existing user directories, randomized filenames, and timestamps within 300–600 seconds of the attack time, plus normal padding computed from the padding ratio

### Requirement: C3 Massive Delete Flood attack generator
The system SHALL provide an attack generator method `generateMassiveDeleteFlood(Instant attackTime, double paddingRatio)` that performs destructive file deletion producing 20,000–40,000 DiffEntry records of type "deleted" across all users in a 180–300 second burst window. Deleted files SHALL be removed from the FilesystemState.

#### Scenario: C3 Massive Delete Flood generation
- **WHEN** generateMassiveDeleteFlood is called with an attack time and padding ratio
- **THEN** 20,000–40,000 DiffEntry records of type "deleted" SHALL be produced, files SHALL be removed from FilesystemState, timestamps within 180–300 seconds of the attack time, targeting files across all 10 users, plus normal padding computed from the padding ratio

### Requirement: C4 Balanced High-Volume Mix attack generator
The system SHALL provide an attack generator method `generateBalancedHighVolumeMix(Instant attackTime, double paddingRatio)` that produces 60,000–80,000 total DiffEntry records with approximately 40% modified, 30% added, and 30% deleted types. Modified files SHALL have size = original * (1.01 + random * 0.02). Added files SHALL be new entries with randomized names. Deleted files SHALL be removed from FilesystemState. All timestamps within a 600–1200 second window.

#### Scenario: C4 Balanced High-Volume Mix generation
- **WHEN** generateBalancedHighVolumeMix is called with an attack time and padding ratio
- **THEN** 60,000–80,000 total DiffEntry records SHALL be produced with approximately 40% "modified", 30% "added", and 30% "deleted" types, timestamps within 600–1200 seconds of the attack time, plus normal padding computed from the padding ratio

### Requirement: C5 Multi-Wave Escalation attack generator
The system SHALL provide an attack generator method `generateMultiWaveEscalation(Instant attackTime, double paddingRatio)` that produces 3 attack waves with escalating volumes: Wave 1 = 10,000–15,000 modified records, Wave 2 = 20,000–30,000 modified records, Wave 3 = 40,000–50,000 modified records. Each wave SHALL be separated by 1800 seconds (30 minutes). Within each wave, timestamps SHALL be within a 300-second burst. All files modified with size = original * (1.02 + random * 0.02), NO extension change.

#### Scenario: C5 Multi-Wave Escalation generation
- **WHEN** generateMultiWaveEscalation is called with an attack time and padding ratio
- **THEN** 3 waves of DiffEntry "modified" records SHALL be produced (10K–15K, 20K–30K, 40K–50K), each wave's timestamps within a 300-second window, 1800-second gaps between waves, NO extension change, plus normal padding computed from the padding ratio

### Requirement: C6 Added-Heavy Encryption attack generator
The system SHALL provide an attack generator method `generateAddedHeavyEncryption(Instant attackTime, double paddingRatio)` that produces 40,000–60,000 DiffEntry records of type "added" representing new encrypted files with randomized filenames. For each added file, the corresponding original file SHALL be deleted (producing a paired "deleted" record). Total records SHALL be 80,000–120,000 (added + deleted pairs). Timestamps within a 600–900 second window.

#### Scenario: C6 Added-Heavy Encryption generation
- **WHEN** generateAddedHeavyEncryption is called with an attack time and padding ratio
- **THEN** 40,000–60,000 DiffEntry "added" records and 40,000–60,000 DiffEntry "deleted" records SHALL be produced, added files SHALL have randomized filenames in existing user directories, deleted files SHALL be the original counterparts, timestamps within 600–900 seconds of the attack time, plus normal padding computed from the padding ratio

### Requirement: C7 Delete-Heavy Destruction attack generator
The system SHALL provide an attack generator method `generateDeleteHeavyDestruction(Instant attackTime, double paddingRatio)` that produces 30,000–50,000 DiffEntry records of type "deleted" plus 5,000–10,000 DiffEntry records of type "modified" (with -20% to -50% size shrinkage on surviving files). Timestamps within a 300–600 second burst window. Simulates wiper malware that destroys most files and corrupts remaining ones.

#### Scenario: C7 Delete-Heavy Destruction generation
- **WHEN** generateDeleteHeavyDestruction is called with an attack time and padding ratio
- **THEN** 30,000–50,000 DiffEntry "deleted" records and 5,000–10,000 DiffEntry "modified" records SHALL be produced, modified files SHALL have size = original * (0.50 + random * 0.30), timestamps within 300–600 seconds of the attack time, plus normal padding computed from the padding ratio

### Requirement: C8 Baseline-Mimicking Volume attack generator
The system SHALL provide an attack generator method `generateBaselineMimickingVolume(Instant attackTime, double paddingRatio)` that produces 30,000–40,000 DiffEntry records with ~50% modified, ~25% added, ~25% deleted distribution, spread over a 6–8 hour window (21,600–28,800 seconds). Size changes SHALL be random -10% to +10%. This produces total_operations_normalized approximately 1.5–2× baseline maximum, making volume-based detection harder. The timing spread mimics a full business day of activity.

#### Scenario: C8 Baseline-Mimicking Volume generation
- **WHEN** generateBaselineMimickingVolume is called with an attack time and padding ratio
- **THEN** 30,000–40,000 total DiffEntry records SHALL be produced with approximately 50% "modified", 25% "added", 25% "deleted", timestamps spread over 21,600–28,800 seconds (6–8 hours) of the attack time, size changes random -10% to +10%, plus normal padding computed from the padding ratio
