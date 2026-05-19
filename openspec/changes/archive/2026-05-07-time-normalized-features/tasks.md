## 1. Core: Add daysBetweenSnapshots to RansomwareFeatureExtractor

- [ ] 1.1 Add `private final double daysBetweenSnapshots` field to `RansomwareFeatureExtractor` with default 2.0 and minimum clamp of 0.25
- [ ] 1.2 Update `RansomwareFeatureExtractor` constructor to accept `daysBetweenSnapshots` parameter (overloaded constructor with default preserved for backward compat)
- [ ] 1.3 In `extract(SnapdiffFile)`: normalize feature 0 (`total_operations / days`), feature 3 (`log1p(sumDeletedBytes / days)` instead of `log1p(sumDeletedBytes)`), feature 4 (`uniqueDirs / days`), feature 5 (`uniqueExts / days`)
- [ ] 1.4 In `extractFromFile(Path)`: apply identical normalization to features 0, 3, 4, 5 using the same formulas
- [ ] 1.5 Add getter `getDaysBetweenSnapshots()` to `RansomwareFeatureExtractor`

## 2. Detector: Propagate interval parameter

- [ ] 2.1 Update `RansomwareDetector` constructors to pass `daysBetweenSnapshots` to the internal `RansomwareFeatureExtractor`
- [ ] 2.2 Ensure `detectFromFile(Path)` uses the extractor's configured interval for feature normalization
- [ ] 2.3 Ensure `detect(RansomwareFeatureVector, List<SnapdiffRecord>)` is unaffected (it receives pre-computed vectors)

## 3. CLI: Add --days-between flag

- [ ] 3.1 Add `@Option(names={"--days-between"}, defaultValue="2.0")` to `RansomwareDetectorCli`
- [ ] 3.2 Pass `daysBetween` to `RansomwareFeatureExtractor` constructor in `RansomwareDetectorCli.call()`
- [ ] 3.3 Add same `--days-between` option to `WeightOptimizerCli` and pass to its extractor

## 4. Tests: Update existing tests and add interval-sensitivity test

- [ ] 4.1 Update `RansomwareFeatureExtractorTest`: verify all existing test expectations account for default 2.0 normalization (features 0, 3, 4, 5 expected values may change)
- [ ] 4.2 Add test in `RansomwareFeatureExtractorTest`: construct extractor with `daysBetweenSnapshots=1.0` and `daysBetweenSnapshots=7.0` on identical data, assert features 0, 3, 4, 5 match (normalized), and ratio features (1, 2, 6, 10, 11, 12) are exactly equal
- [ ] 4.3 Add test: construct extractor with `daysBetweenSnapshots=0.01`, verify it clamps to 0.25
- [ ] 4.4 Update `RansomwareDetectorTest` to use the updated constructor API
- [ ] 4.5 Update `BaselineStatisticsTest` to use the updated constructor API (if it creates extractors)
- [ ] 4.6 Update `WeightedEuclideanScorerTest` to use the updated constructor API
- [ ] 4.7 Update `ZScoreExplainerTest` to use the updated constructor API
- [ ] 4.8 Update `EndToEndTest` to use the updated constructor API
- [ ] 4.9 Run `mvn clean test` and verify all tests pass

## 5. Generator: Propagate interval

- [ ] 5.1 Update `RansomwareTestGenerator` to accept and pass `daysBetweenSnapshots` to its feature extractor
- [ ] 5.2 Update `IntermittentEncryptionBenchmark` to accept and pass `daysBetweenSnapshots` to its feature extractor
