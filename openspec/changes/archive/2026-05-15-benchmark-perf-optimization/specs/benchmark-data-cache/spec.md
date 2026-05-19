## ADDED Requirements

### Requirement: BenchmarkDataGenerator produces persistent snapdiff test data
A new standalone class `BenchmarkDataGenerator` SHALL generate all 151 snapdiff JSON files to a `benchmark-data/` directory using the same generation logic as `IntermittentEncryptionBenchmark` (seed=42, same FilesystemState, same attack dispatch methods). Files SHALL be written with compact JSON (INDENT_OUTPUT disabled).

#### Scenario: Generator produces all expected files
- **WHEN** `BenchmarkDataGenerator.main()` is executed
- **THEN** 151 JSON files SHALL exist in `benchmark-data/` and `benchmark-data/attacks/` with the same naming convention as the benchmark

#### Scenario: Generated data is deterministic
- **WHEN** BenchmarkDataGenerator is run twice on the same codebase
- **THEN** the output files SHALL be byte-identical between runs

#### Scenario: Compact JSON output
- **WHEN** a generated JSON file is inspected
- **THEN** it SHALL NOT contain pretty-printing whitespace (no indentation, no extra newlines)

### Requirement: MANIFEST.json index of pre-generated test data
The BenchmarkDataGenerator SHALL produce a `benchmark-data/MANIFEST.json` file listing all generated files with metadata: file path, category (baseline/attack/irregular/variant), phase, attack type (if applicable), and padding level (if applicable).

#### Scenario: Manifest is valid JSON with complete entries
- **WHEN** MANIFEST.json is parsed
- **THEN** it SHALL contain an entry for every generated file with non-null category and phase fields

#### Scenario: Manifest enables direct file lookup
- **WHEN** the benchmark needs to load test case "ORIG_lockbit_fast_mode_p20"
- **THEN** the manifest or file naming convention SHALL resolve to the correct file path without directory scanning

### Requirement: Benchmark loads from pre-generated data
When `IntermittentEncryptionBenchmark.main()` is executed, it SHALL load snapdiff files from `benchmark-data/` instead of generating them. If `benchmark-data/` does not exist, it SHALL print an error message instructing the user to run BenchmarkDataGenerator first and exit.

#### Scenario: Benchmark runs from pre-generated data
- **WHEN** IntermittentEncryptionBenchmark is executed with `benchmark-data/` present
- **THEN** it SHALL load all snapdiff files from disk, extract features, and produce identical detection results to the generation-based approach

#### Scenario: Missing benchmark-data produces clear error
- **WHEN** IntermittentEncryptionBenchmark is executed without `benchmark-data/`
- **THEN** it SHALL print "benchmark-data/ not found. Run BenchmarkDataGenerator first." and exit with an error
