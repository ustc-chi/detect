package com.anomalydetection.generator;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * Standalone data generator that pre-generates all snapdiff test data files
 * for benchmark runs. Produces a benchmark-data/ directory with ~151 JSON files
 * and a MANIFEST.json index.
 *
 * Uses IDENTICAL generation logic to IntermittentEncryptionBenchmark:
 * same SEED=42, same FilesystemState(42, 300_000), same timestamps.
 */
public class BenchmarkDataGenerator {

    private static final int NORMAL_BASELINE_ROUNDS = 10;
    private static final long SEED = 42L;
    private static final int MIN_TOTAL_OPS = 5000;

    static final double[] PADDING_LEVELS = {0.50, 0.70};

    static final String[] ATTACK_TYPES = {
        "lockbit_fast_mode", "conti_size_tiered",
        "database_priority", "single_user_rapid", "slow_distributed",
        "creeping_shrink", "revil_random_ext", "clop_companion",
        "wannacry_staged"
    };

    static final int[] BUSINESS_HOURS = {9, 10, 14, 15};

    static final String[] IRREGULAR_NORMAL_PATTERNS = {
        "batch_compile", "log_rotation", "backup_surge", "mass_rename",
        "db_checkpoint", "after_hours_burst", "migration_wave", "cleanup_purge"
    };

    // Manifest entry
    static class ManifestEntry {
        public String file;
        public String category;
        public String phase;
        public String attackType;
        public String paddingLevel;

        ManifestEntry(String file, String category, String phase, String attackType, String paddingLevel) {
            this.file = file;
            this.category = category;
            this.phase = phase;
            this.attackType = attackType;
            this.paddingLevel = paddingLevel;
        }
    }

    public static void main(String[] args) throws Exception {
        long startTime = System.currentTimeMillis();

        Path outputDir = Path.of("benchmark-data");
        Path attackDir = outputDir.resolve("attacks");
        Files.createDirectories(attackDir);

        FilesystemState state = new FilesystemState(SEED, 300_000);
        state.initialize();
        // Compact JSON — no INDENT_OUTPUT
        ObjectMapper mapper = new ObjectMapper();
        Instant baseTime = Instant.parse("2026-04-22T08:00:00Z");
        AttackGenerator attackGen = new AttackGenerator(state, SEED + 1000);
        Random rng = new Random(SEED + 3000);

        List<ManifestEntry> manifest = new ArrayList<>();

        // Phase 1a: Baseline normal rounds
        int totalAttacks = ATTACK_TYPES.length * PADDING_LEVELS.length;
        int totalNormals = 38 - totalAttacks;

        System.out.printf("=== Phase 1a: %d baseline normal rounds ===%n", NORMAL_BASELINE_ROUNDS);
        int round = 1;
        for (int i = 0; i < NORMAL_BASELINE_ROUNDS; i++, round++) {
            Instant dayStart = baseTime.plusSeconds((long) (round - 1) * 86400);
            int hourOffset = BUSINESS_HOURS[i % BUSINESS_HOURS.length] - 8;
            dayStart = dayStart.plusSeconds(hourOffset * 3600L);

            List<DiffEntry> diffs = state.evolveNormalRound(dayStart);
            String filename = String.format("round_%03d.json", round);
            mapper.writeValue(outputDir.resolve(filename).toFile(), buildOutput(diffs));
            manifest.add(new ManifestEntry(filename, "baseline", "1a", null, null));
        }
        System.out.printf("  Generated %d baseline files.%n", NORMAL_BASELINE_ROUNDS);

        // Phase 1b: Attack rounds
        System.out.printf("=== Phase 1b: %d attack rounds ===%n", totalAttacks);
        for (String type : ATTACK_TYPES) {
            for (double paddingRatio : PADDING_LEVELS) {
                Instant dayStart = baseTime.plusSeconds((long) (round - 1) * 86400);
                String levelTag = "p" + ((int) (paddingRatio * 100));

                Map<String, FilesystemState.FileInfo> snapshot = state.snapshot();
                List<DiffEntry> diffs = dispatchAttack(attackGen, type, dayStart, paddingRatio);
                state.restore(snapshot);

                String filename = String.format("round_%03d_%s_%s.json", round, type, levelTag);
                mapper.writeValue(outputDir.resolve(filename).toFile(), buildOutput(diffs));
                manifest.add(new ManifestEntry(filename, "attack", "1b", type, levelTag));
                round++;
            }
        }
        System.out.printf("  Generated %d attack files.%n", totalAttacks);

        // Phase 1c: Additional normal rounds
        int additionalNormals = totalNormals - NORMAL_BASELINE_ROUNDS;
        int phase1cEndRound = NORMAL_BASELINE_ROUNDS + totalAttacks + additionalNormals;
        System.out.printf("=== Phase 1c: %d additional normal rounds ===%n", additionalNormals);
        for (int i = 0; round <= phase1cEndRound; i++, round++) {
            Instant dayStart = baseTime.plusSeconds((long) (round - 1) * 86400);
            int hourOffset = BUSINESS_HOURS[i % BUSINESS_HOURS.length] - 8;
            dayStart = dayStart.plusSeconds(hourOffset * 3600L);

            List<DiffEntry> diffs = state.evolveNormalRound(dayStart);
            String filename = String.format("round_%03d.json", round);
            mapper.writeValue(outputDir.resolve(filename).toFile(), buildOutput(diffs));
            manifest.add(new ManifestEntry(filename, "baseline", "1c", null, null));
        }
        System.out.printf("  Generated %d additional normal files.%n", additionalNormals);

        // Phase 1.5: Irregular normal rounds
        System.out.printf("=== Phase 1.5: %d irregular normal rounds ===%n",
            IRREGULAR_NORMAL_PATTERNS.length * 2);
        Random irregularRng = new Random(SEED + 7777);
        int irregularCount = 0;
        for (String pattern : IRREGULAR_NORMAL_PATTERNS) {
            for (int repeat = 0; repeat < 2; repeat++) {
                Instant dayStart = baseTime.plusSeconds((long) (70 + irregularCount) * 86400);
                List<DiffEntry> diffs = state.evolveIrregularNormalRound(dayStart, pattern, irregularRng);
                String filename = String.format("irregular_%03d_%s_%d.json", irregularCount, pattern, repeat);
                mapper.writeValue(outputDir.resolve(filename).toFile(), buildOutput(diffs));
                manifest.add(new ManifestEntry(filename, "irregular", "1.5", null, null));
                irregularCount++;
            }
        }
        System.out.printf("  Generated %d irregular files.%n", irregularCount);

        // Phase 1.6: Extremely quiet day rounds
        int quietDayCount = 4;
        System.out.printf("=== Phase 1.6: %d extremely quiet day rounds ===%n", quietDayCount);
        Random quietRng = new Random(SEED + 9999);
        for (int i = 0; i < quietDayCount; i++) {
            Instant dayStart = baseTime.plusSeconds((long) (90 + i) * 86400);
            List<DiffEntry> diffs = state.evolveExtremelyQuietDay(dayStart, quietRng);
            String filename = String.format("quiet_day_%03d.json", i);
            mapper.writeValue(outputDir.resolve(filename).toFile(), buildOutput(diffs));
            manifest.add(new ManifestEntry(filename, "quiet_day", "1.6", null, null));
        }
        System.out.printf("  Generated %d quiet-day files.%n", quietDayCount);

        // Phase 2: Intermittent/partial encryption variants (2 × 3 = 6)
        System.out.println("=== Phase 2: Intermittent/partial encryption variants ===");
        String[][] variantDefs = {
            {"A9", "partial_every_Nth_with_tmp",    "Encrypt every 5th, 20% .tmp, 30s burst"},
            {"A12", "partial_strip_encrypt",        "Stripe encrypt 4KB blocks, no ext, 30s burst"},
        };

        for (String[] def : variantDefs) {
            String baseId = def[0];
            String method = def[1];

            for (double paddingRatio : PADDING_LEVELS) {
                String levelTag = "p" + ((int) (paddingRatio * 100));
                String id = baseId + "_" + levelTag;
                Map<String, FilesystemState.FileInfo> snapshot = state.snapshot();
                Instant attackTime = baseTime.plusSeconds(50L * 86400);

                List<DiffEntry> diffs = dispatchVariant(state, rng, method, attackTime, paddingRatio);
                state.restore(snapshot);

                String filename = "attacks/" + id + ".json";
                mapper.writeValue(outputDir.resolve(filename).toFile(), buildOutput(diffs));
                manifest.add(new ManifestEntry(filename, "variant", "2", method, levelTag));
            }
        }
        System.out.println("  Generated 6 intermittent variant files.");

        // Phase 2.5: Adversarial evasion variants (8 × 3 = 24)
        System.out.println("=== Phase 2.5: Adversarial evasion variants ===");
        String[][] adversarialDefs = {
            {"B1", "backup_disguise",             "Backup disguise"},
            {"B2", "slow_drip_encrypt",           "Slow drip"},
            {"B3", "random_jitter_burst",         "Random jitter"},
            {"B4", "mixed_operation_mask",        "Mixed ops"},
            {"B5", "size_mimic_normal",           "Size mimic"},
            {"B6", "selective_high_value",        "Selective HV"},
            {"B7", "multi_family_combo",          "Multi-family"},
            {"B8", "rename_and_encrypt",          "Rename+encrypt"},
        };

        for (String[] def : adversarialDefs) {
            String baseId = def[0];
            String method = def[1];

            for (double paddingRatio : PADDING_LEVELS) {
                String levelTag = "p" + ((int) (paddingRatio * 100));
                String id = baseId + "_" + levelTag;
                Map<String, FilesystemState.FileInfo> snapshot = state.snapshot();
                Instant attackTime = baseTime.plusSeconds(60L * 86400);

                List<DiffEntry> diffs = dispatchAdversarial(attackGen, method, attackTime, paddingRatio);
                state.restore(snapshot);

                String filename = "attacks/" + id + ".json";
                mapper.writeValue(outputDir.resolve(filename).toFile(), buildOutput(diffs));
                manifest.add(new ManifestEntry(filename, "attack", "2.5", method, levelTag));
            }
        }
        System.out.println("  Generated 24 adversarial variant files.");

        // Phase 2.7: High-volume record attack variants (7 × 3 = 21)
        System.out.println("=== Phase 2.7: High-volume record attack variants ===");
        String[][] highVolumeDefs = {
            {"C2", "massive_add_flood",            "Massive Add"},
            {"C3", "massive_delete_flood",         "Massive Delete"},
            {"C4", "balanced_high_volume_mix",     "Balanced Mix"},
            {"C5", "multi_wave_escalation",        "Multi-Wave"},
            {"C6", "added_heavy_encryption",       "Added-Heavy"},
            {"C7", "delete_heavy_destruction",     "Delete-Heavy"},
            {"C8", "baseline_mimicking_volume",    "Baseline-Mimicking"},
        };

        for (String[] def : highVolumeDefs) {
            String baseId = def[0];
            String method = def[1];

            for (double paddingRatio : PADDING_LEVELS) {
                String levelTag = "p" + ((int) (paddingRatio * 100));
                String id = baseId + "_" + levelTag;
                Map<String, FilesystemState.FileInfo> snapshot = state.snapshot();
                Instant attackTime = baseTime.plusSeconds(70L * 86400);

                List<DiffEntry> diffs = dispatchHighVolume(attackGen, method, attackTime, paddingRatio);
                state.restore(snapshot);

                String filename = "attacks/" + id + ".json";
                mapper.writeValue(outputDir.resolve(filename).toFile(), buildOutput(diffs));
                manifest.add(new ManifestEntry(filename, "attack", "2.7", method, levelTag));
            }
        }
        System.out.println("  Generated 21 high-volume variant files.");

        // Phase 2.8: Combo-feature attack variants (8 × 3 = 24)
        System.out.println("=== Phase 2.8: Combo-feature attack variants ===");
        String[][] comboDefs = {
            {"D1", "ryuk_lateral",             "Ryuk lateral"},
            {"D2", "darkside_staged",          "DarkSide staged"},
            {"D3", "lockbit3_adaptive",        "LockBit3 adaptive"},
            {"D4", "blackcat_variable",        "BlackCat variable"},
            {"D5", "royal_selective",          "Royal selective"},
            {"D6", "play_intermittent_noext",  "Play intermittent"},
            {"D7", "medusa_multi_stage",       "Medusa multi-stage"},
            {"D8", "akira_vpn_gradual",        "Akira VPN"},
        };

        for (String[] def : comboDefs) {
            String baseId = def[0];
            String method = def[1];

            for (double paddingRatio : PADDING_LEVELS) {
                String levelTag = "p" + ((int) (paddingRatio * 100));
                String id = baseId + "_" + levelTag;
                Map<String, FilesystemState.FileInfo> snapshot = state.snapshot();
                Instant attackTime = baseTime.plusSeconds(80L * 86400);

                List<DiffEntry> diffs = dispatchCombo(attackGen, method, attackTime, paddingRatio);
                state.restore(snapshot);

                String filename = "attacks/" + id + ".json";
                mapper.writeValue(outputDir.resolve(filename).toFile(), buildOutput(diffs));
                manifest.add(new ManifestEntry(filename, "attack", "2.8", method, levelTag));
            }
        }
        System.out.println("  Generated 24 combo-feature variant files.");

        // Write MANIFEST.json
        mapper.writeValue(outputDir.resolve("MANIFEST.json").toFile(), manifest);

        long elapsed = System.currentTimeMillis() - startTime;
        System.out.printf("%n=== Done: %d files generated in %d ms (%.1f s) ===%n",
            manifest.size(), elapsed, elapsed / 1000.0);
        System.out.printf("Output directory: %s%n", outputDir.toAbsolutePath());
    }

    // --- Dispatch methods (identical to IntermittentEncryptionBenchmark) ---

    private static List<DiffEntry> dispatchAttack(AttackGenerator gen, String type,
            Instant attackTime, double paddingRatio) {
        switch (type) {
            case "lockbit_fast_mode": return gen.generateLockBitFastMode(attackTime, paddingRatio);
            case "conti_size_tiered": return gen.generateContiSizeTiered(attackTime, paddingRatio);
            case "database_priority": return gen.generateDatabasePriority(attackTime, paddingRatio);
            case "single_user_rapid": return gen.generateSingleUserRapid(attackTime, paddingRatio);
            case "slow_distributed": return gen.generateSlowDistributed(attackTime, paddingRatio);
            case "creeping_shrink": return gen.generateCreepingShrink(attackTime, paddingRatio);
            case "revil_random_ext": return gen.generateRevilRandomExt(attackTime, paddingRatio);
            case "clop_companion": return gen.generateClopCompanion(attackTime, paddingRatio);
            case "wannacry_staged": return gen.generateWannaCryStaged(attackTime, paddingRatio);
            default: throw new IllegalArgumentException("Unknown attack type: " + type);
        }
    }

    private static List<DiffEntry> dispatchAdversarial(AttackGenerator gen, String method,
            Instant attackTime, double paddingRatio) {
        switch (method) {
            case "backup_disguise": return gen.generateBackupDisguise(attackTime, paddingRatio);
            case "slow_drip_encrypt": return gen.generateSlowDripEncrypt(attackTime, paddingRatio);
            case "random_jitter_burst": return gen.generateRandomJitterBurst(attackTime, paddingRatio);
            case "mixed_operation_mask": return gen.generateMixedOperationMask(attackTime, paddingRatio);
            case "size_mimic_normal": return gen.generateSizeMimicNormal(attackTime, paddingRatio);
            case "selective_high_value": return gen.generateSelectiveHighValue(attackTime, paddingRatio);
            case "multi_family_combo": return gen.generateMultiFamilyCombo(attackTime, paddingRatio);
            case "rename_and_encrypt": return gen.generateRenameAndEncrypt(attackTime, paddingRatio);
            default: throw new IllegalArgumentException("Unknown adversarial type: " + method);
        }
    }

    private static List<DiffEntry> dispatchHighVolume(AttackGenerator gen, String method,
            Instant attackTime, double paddingRatio) {
        switch (method) {
            case "massive_add_flood": return gen.generateMassiveAddFlood(attackTime, paddingRatio);
            case "massive_delete_flood": return gen.generateMassiveDeleteFlood(attackTime, paddingRatio);
            case "balanced_high_volume_mix": return gen.generateBalancedHighVolumeMix(attackTime, paddingRatio);
            case "multi_wave_escalation": return gen.generateMultiWaveEscalation(attackTime, paddingRatio);
            case "added_heavy_encryption": return gen.generateAddedHeavyEncryption(attackTime, paddingRatio);
            case "delete_heavy_destruction": return gen.generateDeleteHeavyDestruction(attackTime, paddingRatio);
            case "baseline_mimicking_volume": return gen.generateBaselineMimickingVolume(attackTime, paddingRatio);
            default: throw new IllegalArgumentException("Unknown high-volume type: " + method);
        }
    }

    private static List<DiffEntry> dispatchCombo(AttackGenerator gen, String method,
            Instant attackTime, double paddingRatio) {
        switch (method) {
            case "ryuk_lateral": return gen.generateRyukLateral(attackTime, paddingRatio);
            case "darkside_staged": return gen.generateDarkSideStaged(attackTime, paddingRatio);
            case "lockbit3_adaptive": return gen.generateLockBit3Adaptive(attackTime, paddingRatio);
            case "blackcat_variable": return gen.generateBlackCatVariable(attackTime, paddingRatio);
            case "royal_selective": return gen.generateRoyalSelective(attackTime, paddingRatio);
            case "play_intermittent_noext": return gen.generatePlayIntermittentNoExt(attackTime, paddingRatio);
            case "medusa_multi_stage": return gen.generateMedusaMultiStage(attackTime, paddingRatio);
            case "akira_vpn_gradual": return gen.generateAkiraVpnGradual(attackTime, paddingRatio);
            default: throw new IllegalArgumentException("Unknown combo type: " + method);
        }
    }

    private static List<DiffEntry> dispatchVariant(FilesystemState state, Random rng,
            String method, Instant attackTime, double paddingRatio) {
        switch (method) {
            case "partial_every_Nth_with_tmp":
                return genEveryNth(state, rng, attackTime, 5, 0.02, 0.05, 0.20, ".tmp", paddingRatio);
            case "partial_strip_encrypt":
                return genStripEncrypt(state, rng, attackTime, 2000, paddingRatio);
            default: return new ArrayList<>();
        }
    }

    // --- Helper methods (identical to IntermittentEncryptionBenchmark) ---

    private static int computePadding(int attackOps, double paddingRatio) {
        int padFromRatio = (int) (attackOps * paddingRatio / (1.0 - paddingRatio));
        int totalWithRatio = attackOps + padFromRatio;
        if (totalWithRatio < MIN_TOTAL_OPS) {
            return Math.max(0, MIN_TOTAL_OPS - attackOps);
        }
        return padFromRatio;
    }

    private static List<DiffEntry> genIntermittent(FilesystemState state, Random rng,
            Instant baseTime, int targetOps, double minSizePct, double maxSizePct,
            double extChangeProb, String extSuffix, double paddingRatio) {
        List<FilesystemState.FileInfo> targets = new ArrayList<>(state.getAllFiles());
        int count = Math.min(targetOps, targets.size());
        int attackWindowSecs = 30 + rng.nextInt(270);
        List<DiffEntry> attackDiffs = new ArrayList<>();

        for (int i = 0; i < count && !targets.isEmpty(); i++) {
            FilesystemState.FileInfo fi = targets.get(rng.nextInt(targets.size()));
            if (state.getFile(fi.path) == null) continue;
            double pct = minSizePct + rng.nextDouble() * (maxSizePct - minSizePct);
            long newSize = (long) (fi.size * (1.0 + pct));
            if (newSize < 1024L) newSize = 1024L;
            String ct = baseTime.plusSeconds(rng.nextInt(attackWindowSecs)).toString();
            if (extSuffix != null && rng.nextDouble() < extChangeProb) {
                String newPath = fi.path + extSuffix;
                state.removeFile(fi.path);
                FilesystemState.FileInfo updated = new FilesystemState.FileInfo(newPath, fi.extension, newSize, fi.userIndex);
                state.addFile(newPath, updated);
                attackDiffs.add(new DiffEntry(newPath, "modified", String.valueOf(newSize), ct));
            } else {
                FilesystemState.FileInfo updated = new FilesystemState.FileInfo(fi.path, fi.extension, newSize, fi.userIndex);
                state.addFile(fi.path, updated);
                attackDiffs.add(new DiffEntry(fi.path, "modified", String.valueOf(newSize), ct));
            }
        }

        int padTarget = computePadding(attackDiffs.size(), paddingRatio);
        List<DiffEntry> diffs = new ArrayList<>(genNormalPadding(state, rng, padTarget, baseTime));
        diffs.addAll(attackDiffs);
        return diffs;
    }

    private static List<DiffEntry> genHeaderAppend(FilesystemState state, Random rng,
            Instant baseTime, int targetOps, int minAppend, int maxAppend,
            double extChangeProb, String extSuffix, double paddingRatio) {
        List<FilesystemState.FileInfo> targets = new ArrayList<>(state.getAllFiles());
        int count = Math.min(targetOps, targets.size());
        int attackWindowSecs = 30 + rng.nextInt(270);
        List<DiffEntry> attackDiffs = new ArrayList<>();

        for (int i = 0; i < count && !targets.isEmpty(); i++) {
            int idx = rng.nextInt(targets.size());
            FilesystemState.FileInfo fi = targets.get(idx);
            long append = minAppend + rng.nextInt(maxAppend - minAppend);
            long newSize = fi.size + append;
            String ct = baseTime.plusSeconds(rng.nextInt(attackWindowSecs)).toString();
            if (extSuffix != null && rng.nextDouble() < extChangeProb) {
                String newPath = fi.path + extSuffix;
                state.removeFile(fi.path);
                FilesystemState.FileInfo updated = new FilesystemState.FileInfo(newPath, fi.extension, newSize, fi.userIndex);
                state.addFile(newPath, updated);
                attackDiffs.add(new DiffEntry(newPath, "modified", String.valueOf(newSize), ct));
            } else {
                FilesystemState.FileInfo updated = new FilesystemState.FileInfo(fi.path, fi.extension, newSize, fi.userIndex);
                state.addFile(fi.path, updated);
                attackDiffs.add(new DiffEntry(fi.path, "modified", String.valueOf(newSize), ct));
            }
        }

        int padTarget = computePadding(attackDiffs.size(), paddingRatio);
        List<DiffEntry> diffs = new ArrayList<>(genNormalPadding(state, rng, padTarget, baseTime));
        diffs.addAll(attackDiffs);
        return diffs;
    }

    private static List<DiffEntry> genEveryNth(FilesystemState state, Random rng,
            Instant baseTime, int nth, double minSizePct, double maxSizePct,
            double extChangeProb, String extSuffix, double paddingRatio) {
        List<FilesystemState.FileInfo> all = new ArrayList<>(state.getAllFiles());
        Collections.shuffle(all, rng);
        int attackWindowSecs = 30 + rng.nextInt(270);
        List<DiffEntry> attackDiffs = new ArrayList<>();
        int count = 0;

        for (int i = 0; i < all.size() && count < 2000; i++) {
            if ((i + 1) % nth != 0) continue;
            FilesystemState.FileInfo fi = all.get(i);
            if (state.getFile(fi.path) == null) continue;
            double pct = minSizePct + rng.nextDouble() * (maxSizePct - minSizePct);
            long newSize = (long) (fi.size * (1.0 + pct));
            if (newSize < 1024L) newSize = 1024L;
            count++;
            String ct = baseTime.plusSeconds(rng.nextInt(attackWindowSecs)).toString();
            if (extSuffix != null && rng.nextDouble() < extChangeProb) {
                String newPath = fi.path + extSuffix;
                state.removeFile(fi.path);
                FilesystemState.FileInfo updated = new FilesystemState.FileInfo(newPath, fi.extension, newSize, fi.userIndex);
                state.addFile(newPath, updated);
                attackDiffs.add(new DiffEntry(newPath, "modified", String.valueOf(newSize), ct));
            } else {
                FilesystemState.FileInfo updated = new FilesystemState.FileInfo(fi.path, fi.extension, newSize, fi.userIndex);
                state.addFile(fi.path, updated);
                attackDiffs.add(new DiffEntry(fi.path, "modified", String.valueOf(newSize), ct));
            }
        }

        int padTarget = computePadding(attackDiffs.size(), paddingRatio);
        List<DiffEntry> diffs = new ArrayList<>(genNormalPadding(state, rng, padTarget, baseTime));
        diffs.addAll(attackDiffs);
        return diffs;
    }

    private static List<DiffEntry> genSingleUser(FilesystemState state, Random rng,
            Instant baseTime, int targetOps, double minSizePct, double maxSizePct,
            double extChangeProb, String extSuffix, double paddingRatio) {
        int user = 1 + rng.nextInt(10);
        int attackWindowSecs = 30 + rng.nextInt(270);
        List<FilesystemState.FileInfo> targets = new ArrayList<>();
        for (FilesystemState.FileInfo fi : state.getAllFiles()) {
            if (fi.userIndex == user) targets.add(fi);
        }
        int count = Math.min(targetOps, targets.size());
        List<DiffEntry> attackDiffs = new ArrayList<>();

        for (int i = 0; i < count && !targets.isEmpty(); i++) {
            FilesystemState.FileInfo fi = targets.get(rng.nextInt(targets.size()));
            if (state.getFile(fi.path) == null) continue;
            double pct = minSizePct + rng.nextDouble() * (maxSizePct - minSizePct);
            long newSize = (long) (fi.size * (1.0 + pct));
            if (newSize < 1024L) newSize = 1024L;
            String ct = baseTime.plusSeconds(rng.nextInt(attackWindowSecs)).toString();
            if (extSuffix != null && rng.nextDouble() < extChangeProb) {
                String newPath = fi.path + extSuffix;
                state.removeFile(fi.path);
                FilesystemState.FileInfo updated = new FilesystemState.FileInfo(newPath, fi.extension, newSize, fi.userIndex);
                state.addFile(newPath, updated);
                attackDiffs.add(new DiffEntry(newPath, "modified", String.valueOf(newSize), ct));
            } else {
                FilesystemState.FileInfo updated = new FilesystemState.FileInfo(fi.path, fi.extension, newSize, fi.userIndex);
                state.addFile(fi.path, updated);
                attackDiffs.add(new DiffEntry(fi.path, "modified", String.valueOf(newSize), ct));
            }
        }

        int padTarget = computePadding(attackDiffs.size(), paddingRatio);
        List<DiffEntry> diffs = new ArrayList<>(genNormalPadding(state, rng, padTarget, baseTime));
        diffs.addAll(attackDiffs);
        return diffs;
    }

    private static List<DiffEntry> genStripEncrypt(FilesystemState state, Random rng,
            Instant baseTime, int targetOps, double paddingRatio) {
        List<FilesystemState.FileInfo> targets = new ArrayList<>(state.getAllFiles());
        int count = Math.min(targetOps, targets.size());
        int attackWindowSecs = 30 + rng.nextInt(270);
        List<DiffEntry> attackDiffs = new ArrayList<>();

        for (int i = 0; i < count && !targets.isEmpty(); i++) {
            FilesystemState.FileInfo fi = targets.get(rng.nextInt(targets.size()));
            if (state.getFile(fi.path) == null) continue;
            long stripeDelta = (fi.size / 4096) * (rng.nextInt(16) + 8);
            long newSize = fi.size + stripeDelta;
            String ct = baseTime.plusSeconds(rng.nextInt(attackWindowSecs)).toString();
            FilesystemState.FileInfo updated = new FilesystemState.FileInfo(fi.path, fi.extension, newSize, fi.userIndex);
            state.addFile(fi.path, updated);
            attackDiffs.add(new DiffEntry(fi.path, "modified", String.valueOf(newSize), ct));
        }

        int padTarget = computePadding(attackDiffs.size(), paddingRatio);
        List<DiffEntry> diffs = new ArrayList<>(genNormalPadding(state, rng, padTarget, baseTime));
        diffs.addAll(attackDiffs);
        return diffs;
    }

    private static List<DiffEntry> genNormalPadding(FilesystemState state, Random rng,
            int count, Instant baseTime) {
        List<DiffEntry> padding = new ArrayList<>();
        List<String> paths = new ArrayList<>(state.getAllFiles().stream().map(fi -> fi.path).toList());
        String[] dirs = {"projects","docs","mail","logs","configs","media","backup","archive","temp","data"};
        String[] exts = {"docx","xlsx","pptx","pdf","txt","log","conf","sh","py","java",
                "cpp","h","md","json","xml","sql","jpg","png","gif","mp3","mp4","wav","zip","tar","gz","db","yaml","csv"};

        int modCount = (int) (count * (0.35 + rng.nextDouble() * 0.25));
        int addCount = (int) (count * (0.15 + rng.nextDouble() * 0.15));
        int delCount = count - modCount - addCount;
        int hoursWindow = 6 + rng.nextInt(7);

        for (int i = 0; i < modCount && !paths.isEmpty(); i++) {
            String path = paths.get(rng.nextInt(paths.size()));
            FilesystemState.FileInfo fi = state.getFile(path);
            if (fi == null) continue;
            double delta = (rng.nextDouble() * 0.6) - 0.3;
            long newSize = (long) (fi.size * (1.0 + delta));
            if (newSize < 1024L) newSize = 1024L;
            FilesystemState.FileInfo updated = new FilesystemState.FileInfo(fi.path, fi.extension, newSize, fi.userIndex);
            state.addFile(fi.path, updated);
            String ct = baseTime.plusSeconds(rng.nextInt(hoursWindow * 3600)).toString();
            padding.add(new DiffEntry(path, "modified", String.valueOf(newSize), ct));
        }
        for (int i = 0; i < addCount; i++) {
            int user = 1 + rng.nextInt(10);
            String dir = dirs[rng.nextInt(dirs.length)];
            String ext = exts[rng.nextInt(exts.length)];
            String path = String.format("/vol/share/user%d/%s/file_pad_%d.%s", user, dir, rng.nextInt(100000), ext);
            long size = 1024L * (1L + rng.nextInt(100));
            state.addFile(path, new FilesystemState.FileInfo(path, ext, size, user));
            String ct = baseTime.plusSeconds(rng.nextInt(hoursWindow * 3600)).toString();
            padding.add(new DiffEntry(path, "added", String.valueOf(size), ct));
        }
        for (int i = 0; i < delCount && !paths.isEmpty(); i++) {
            String path = paths.get(rng.nextInt(paths.size()));
            FilesystemState.FileInfo fi = state.getFile(path);
            if (fi == null) continue;
            state.removeFile(path);
            String ct = baseTime.plusSeconds(rng.nextInt(hoursWindow * 3600)).toString();
            padding.add(new DiffEntry(path, "deleted", String.valueOf(fi.size), ct));
        }
        return padding;
    }

    private static SnapdiffOutput buildOutput(List<DiffEntry> diffs) {
        int added = 0, modified = 0, deleted = 0;
        for (DiffEntry d : diffs) {
            if ("added".equals(d.type)) added++;
            else if ("modified".equals(d.type)) modified++;
            else if ("deleted".equals(d.type)) deleted++;
        }
        return new SnapdiffOutput(diffs, new SummaryOutput(added, modified, deleted));
    }
}
