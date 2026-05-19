## 1. Pre-generation Tool & Quick Wins (Wave 1 — Parallel)

- [ ] 1.1 Create `BenchmarkDataGenerator.java` — standalone main class that extracts generation logic from IntermittentEncryptionBenchmark (lines 44-272), generates all 151 snapdiff JSON files to `benchmark-data/` with compact JSON (INDENT_OUTPUT disabled), and produces a `MANIFEST.json` index. Must use identical seed=42, FilesystemState, and dispatch methods for determinism.
- [ ] 1.2 Run BenchmarkDataGenerator and commit `benchmark-data/` directory (~265 MB) to git. Verify 151+ JSON files exist and MANIFEST.json is valid.
- [ ] 1.3 Optimize `StreamingSnapdiffParser.java` — make ObjectMapper and JsonFactory static final shared instances, increase BufferedInputStream buffer to 64KB (65536 bytes). Keep parse() method signature and streaming behavior unchanged.
- [ ] 1.4 Create `InMemoryBurstAccumulator.java` — new class with same API as BurstDataFile (create/write/computeBurstFeatures/close) but accumulates OpEntry records in ArrayList instead of temp file. Copy computeBurstFeatures() algorithm verbatim from BurstDataFile.
- [ ] 1.5 Disable INDENT_OUTPUT in IntermittentEncryptionBenchmark line 52 and add timing skeleton (System.nanoTime at start/end and per-phase).

## 2. Pipeline Integration (Wave 2 — After Wave 1)

- [ ] 2.1 Eliminate triple-parse in `RansomwareDetector.detectFromFile()` — refactor to parse file once with StreamingSnapdiffParser, collect records into ArrayList, feed to signature detector (add checkRecord method) and feature extractor (add extractFromRecords method). Keep existing scanStream/extractFromFile methods for backward compatibility.
- [ ] 2.2 Replace BurstDataFile with InMemoryBurstAccumulator in `RansomwareFeatureExtractor.extractFromFile()`. Verify BurstFeatures output is numerically identical.
- [ ] 2.3 Refactor `IntermittentEncryptionBenchmark.java` to load from `benchmark-data/` instead of generating. Remove FilesystemState/AttackGenerator usage and temp directory. Keep Phase 3-4 detection logic unchanged. Add clear error if benchmark-data/ missing.
- [ ] 2.4 Optimize `FilesystemState.snapshot()/restore()` — make FileInfo fields final (immutable), use shallow LinkedHashMap copy. Audit all mutation sites in AttackGenerator and IntermittentEncryptionBenchmark gen* methods to create new FileInfo objects instead of mutating fields.

## 3. Verification & Instrumentation (Wave 3 — After Wave 2)

- [ ] 3.1 Add comprehensive per-phase timing to IntermittentEncryptionBenchmark (baseline load, attack load, irregular load, variant load, pre-cache, weight scan, detailed results) and to BenchmarkDataGenerator. Print timing summary table at end.
- [ ] 3.2 Run full benchmark verification: `mvn clean package -q && java -cp target/rcf-snapdiff-anomaly-detector-1.0.jar com.anomalydetection.generator.IntermittentEncryptionBenchmark`. Verify: build succeeds, all phases complete, 102/102 attacks detected, 0/24 vanilla normal FP, total runtime under 5 minutes, no exceptions.
