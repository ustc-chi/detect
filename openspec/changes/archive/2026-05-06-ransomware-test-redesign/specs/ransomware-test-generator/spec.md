## MODIFIED Requirements

### Requirement: Test round schedule with 12 attack rounds
The RansomwareTestGenerator SHALL produce 40 total rounds: 28 normal + 12 attacks. Attack rounds SHALL be at positions: 4, 7, 10, 13, 16, 19, 22, 25, 28, 31, 34, 37.

Attack types in order:
1. Round 4: lockbit_fast_mode
2. Round 7: conti_size_tiered
3. Round 10: extension_preserving_mass
4. Round 13: database_priority
5. Round 16: single_user_rapid
6. Round 19: slow_distributed
7. Round 22: creeping_shrink
8. Round 25: revil_random_ext
9. Round 28: clop_companion
10. Round 31: wannacry_staged
11. Round 34: mass_encryption (.lockbit)
12. Round 37: ransom_note_drop

#### Scenario: 40 rounds generated with 12 attacks at correct positions
- **WHEN** RansomwareTestGenerator.generate() is called with seed=42
- **THEN** 40 JSON files SHALL be produced, with attack rounds at positions 4, 7, 10, 13, 16, 19, 22, 25, 28, 31, 34, 37, each labeled with the corresponding attack type

#### Scenario: Filesystem state isolation between attack rounds
- **WHEN** each attack round is generated
- **THEN** the FilesystemState SHALL be snapshotted before the attack and restored after, ensuring no state leakage between rounds

### Requirement: Normal padding with dispersed timestamps
Normal padding operations SHALL have timestamps spread over 6-13 hour windows (as current), while attack operations SHALL be concentrated in realistic burst windows (30-600 seconds depending on attack type). This creates temporal separation between attack and normal activity.

#### Scenario: Attack ops in burst, padding ops dispersed
- **WHEN** an attack with 3000 attack ops and 7000 padding ops is generated
- **THEN** attack ops SHALL have timestamps within the attack's burst window (30-600s), and padding ops SHALL have timestamps spread over 6-13 hours
