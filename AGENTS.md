# Project Instructions

## Post-Archive Git Push

After completing `/opsx-archive` (archiving an openspec change), automatically commit and push all code changes to the git remote:

1. `git add -A`
2. `git commit -m "chore: archive <change-name>"`
3. `git push origin <current-branch>`

Do this without asking. The archive step is the final step — once the user archives, they expect it to be pushed.

## Benchmark Verification (MANDATORY)

Any change that modifies benchmark test cases (`IntermittentEncryptionBenchmark.java` or `AttackGenerator.java`) MUST run the full benchmark and verify results before marking work complete:

```bash
mvn clean package -q && java -cp target/rcf-snapdiff-anomaly-detector-1.0.jar com.anomalydetection.generator.IntermittentEncryptionBenchmark
```

**Verification criteria:**
- Build succeeds with zero errors
- Benchmark runs to completion (all phases)
- Attack detection rate ≥ 99% on the full suite
- Vanilla normal false positive rate = 0%
- No exceptions or runtime errors

If any criterion fails, the work is NOT complete. Fix issues and re-run.

## OpenSpec Delta Spec Auto-Sync

When running `/opsx-archive`, if delta specs exist, ALWAYS sync them to main specs (do NOT skip). Do not prompt for sync — just do it automatically.

## README ↔ benchmark.md ↔ normalRounds.md Sync

Whenever `README.md` is updated with benchmark-related content (test case descriptions, detection results, attack categories, feature definitions, weights, thresholds, or benchmark counts), `benchmark.md` AND `normalRounds.md` MUST also be updated to stay consistent. The three documents share overlapping information — keep them all in sync.
