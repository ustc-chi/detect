package com.anomalydetection.generator;

import com.anomalydetection.detector.*;
import com.anomalydetection.features.RansomwareFeatureExtractor;
import com.anomalydetection.features.RansomwareFeatureVector;
import com.anomalydetection.parser.StreamingSnapdiffParser;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

public class IntermittentEncryptionBenchmark {

    private static final int NORMAL_BASELINE_ROUNDS = 10;
    private static final long SEED = 42L;
    private static final int MIN_TOTAL_OPS = 5000;

    static final double[] BASE_WEIGHTS = {
        2.0, 2.5, 5.0, 3.0, 1.5, 2.0, 2.0, 2.5, 2.5, 3.0, 1.5, 2.0
    };

    static final double[] VELOCITY_WEIGHT_CANDIDATES = {0.5, 2.0, 5.0, 8.0, 10.0, 15.0};
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

    // Reverse lookup: attackType -> {baseId, description}
    private static final Map<String, String[]> ATTACK_ID_MAP = new HashMap<>();
    static {
        // Variants (category "variant")
        ATTACK_ID_MAP.put("partial_every_Nth_with_tmp", new String[]{"A9", "Encrypt every 5th, 20% .tmp, 30s burst"});
        ATTACK_ID_MAP.put("partial_strip_encrypt", new String[]{"A12", "Stripe encrypt 4KB blocks, no ext, 30s burst"});
        // Adversarial (phase "2.5")
        ATTACK_ID_MAP.put("backup_disguise", new String[]{"B1", "Backup disguise: 3-5K ops over 2-4h, uniform +2-4%"});
        ATTACK_ID_MAP.put("slow_drip_encrypt", new String[]{"B2", "Slow drip: 50 ops/5min over 6h, +1-3%"});
        ATTACK_ID_MAP.put("random_jitter_burst", new String[]{"B3", "Random jitter: 2-3K ops, 1-15s jitter + pauses, +2-5%"});
        ATTACK_ID_MAP.put("mixed_operation_mask", new String[]{"B4", "Mixed ops: 80% mod 10% add 10% del, +1-3%"});
        ATTACK_ID_MAP.put("size_mimic_normal", new String[]{"B5", "Size mimic: random -10% to +10% per file"});
        ATTACK_ID_MAP.put("selective_high_value", new String[]{"B6", "Selective HV: only .docx/.xlsx/.pdf/.db/.sql, 200-500 ops"});
        ATTACK_ID_MAP.put("multi_family_combo", new String[]{"B7", "Multi-family: 50% uniform + 50% append"});
        ATTACK_ID_MAP.put("rename_and_encrypt", new String[]{"B8", "Rename+encrypt: random filenames, +1-4%"});
        // High-volume (phase "2.7")
        ATTACK_ID_MAP.put("massive_add_flood", new String[]{"C2", "Massive Add: 30K-50K added files, randomized names"});
        ATTACK_ID_MAP.put("massive_delete_flood", new String[]{"C3", "Massive Delete: 20K-40K deleted, 3-5min burst"});
        ATTACK_ID_MAP.put("balanced_high_volume_mix", new String[]{"C4", "Balanced Mix: 60K-80K total (40/30/30 mod/add/del)"});
        ATTACK_ID_MAP.put("multi_wave_escalation", new String[]{"C5", "Multi-Wave: 3 waves 10K->20K->40K modified"});
        ATTACK_ID_MAP.put("added_heavy_encryption", new String[]{"C6", "Added-Heavy: 40K-60K added + 40K-60K deleted pairs"});
        ATTACK_ID_MAP.put("delete_heavy_destruction", new String[]{"C7", "Delete-Heavy: 30K-50K deleted + 5K-10K shrunk"});
        ATTACK_ID_MAP.put("baseline_mimicking_volume", new String[]{"C8", "Baseline-Mimicking: 30K-40K over 6-8h, -10% to +10%"});
        // Combo (phase "2.8")
        ATTACK_ID_MAP.put("ryuk_lateral", new String[]{"D1", "Ryuk lateral: slow HV targeting + fast burst"});
        ATTACK_ID_MAP.put("darkside_staged", new String[]{"D2", "DarkSide staged: add copies \u2192 modify \u2192 delete copies"});
        ATTACK_ID_MAP.put("lockbit3_adaptive", new String[]{"D3", "LockBit3 adaptive: alternating fast/slow bursts"});
        ATTACK_ID_MAP.put("blackcat_variable", new String[]{"D4", "BlackCat variable: mixed full/intermittent, off-hours"});
        ATTACK_ID_MAP.put("royal_selective", new String[]{"D5", "Royal selective: partial corruption + 15% rename"});
        ATTACK_ID_MAP.put("play_intermittent_noext", new String[]{"D6", "Play intermittent: 128KB block, business files"});
        ATTACK_ID_MAP.put("medusa_multi_stage", new String[]{"D7", "Medusa multi-stage: disruption \u2192 escalating encryption"});
        ATTACK_ID_MAP.put("akira_vpn_gradual", new String[]{"D8", "Akira VPN: slow-to-fast escalation, off-hours"});
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        long benchmarkStartMs = System.currentTimeMillis();

        String dataDir = System.getProperty("benchmark.data.dir", "benchmark-data");
        Path dataPath = Path.of(dataDir);
        Path manifestPath = dataPath.resolve("MANIFEST.json");

        if (!Files.exists(manifestPath)) {
            System.out.println("ERROR: " + manifestPath + " not found.");
            System.out.println("Run BenchmarkDataGenerator first to generate benchmark data.");
            System.out.println("  mvn compile -q && java -cp target/rcf-snapdiff-anomaly-detector-1.0.jar com.anomalydetection.generator.BenchmarkDataGenerator");
            System.exit(1);
        }

        ObjectMapper mapper = new ObjectMapper();
        RansomwareFeatureExtractor extractor = new RansomwareFeatureExtractor(null, 2.0);

        List<Map<String, Object>> manifest = mapper.readValue(manifestPath.toFile(), List.class);

        List<RansomwareFeatureVector> baselineVectors = new ArrayList<>();
        List<RansomwareFeatureVector> normalVectors = new ArrayList<>();
        List<RansomwareFeatureVector> irregularVectors = new ArrayList<>();
        List<String> irregularLabels = new ArrayList<>();
        List<RansomwareFeatureVector> quietDayVectors = new ArrayList<>();
        List<String> quietDayLabels = new ArrayList<>();
        List<String[]> attackTestCases = new ArrayList<>();
        List<RansomwareFeatureVector> attackVectors = new ArrayList<>();
        List<Integer> baselineHours = new ArrayList<>();
        List<Integer> baselineOpsCounts = new ArrayList<>();

        int baselineCount = 0;
        long totalLoadMs = 0;
        int totalFiles = 0;
        int slowFileCount = 0;

        System.out.println("=== Loading pre-generated benchmark data from " + dataDir + "/ ===");

        for (Map<String, Object> entry : manifest) {
            String file = (String) entry.get("file");
            String category = (String) entry.get("category");
            String phase = (String) entry.get("phase");
            String attackType = (String) entry.get("attackType");
            String paddingLevel = (String) entry.get("paddingLevel");
            Path filePath = dataPath.resolve(file);

            long loadStartMs = System.currentTimeMillis();
            RansomwareFeatureVector v = extractor.extractFromFile(filePath);
            long loadMs = System.currentTimeMillis() - loadStartMs;
            totalLoadMs += loadMs;
            totalFiles++;

            if (loadMs > 1000) {
                System.out.printf("  Slow file: %s (%d ms)%n", file, loadMs);
                slowFileCount++;
            }

            switch (category) {
                case "baseline":
                    baselineCount++;
                    if (baselineCount <= NORMAL_BASELINE_ROUNDS) {
                        baselineVectors.add(v);
                        baselineHours.add(extractDominantHourStreaming(filePath));
                        baselineOpsCounts.add(countRecordsStreaming(filePath));
                    }
                    normalVectors.add(v);
                    break;

                case "irregular":
                    irregularVectors.add(v);
                    String fname = Path.of(file).getFileName().toString().replace(".json", "");
                    irregularLabels.add(fname.replaceFirst("^irregular_\\d+_", ""));
                    break;

                case "quiet_day":
                    quietDayVectors.add(v);
                    quietDayLabels.add(Path.of(file).getFileName().toString().replace(".json", ""));
                    break;

                case "attack":
                case "variant":
                    String id;
                    String desc;
                    if ("1b".equals(phase)) {
                        id = "ORIG_" + attackType + "_" + paddingLevel;
                        desc = "";
                    } else {
                        String[] idInfo = ATTACK_ID_MAP.get(attackType);
                        if (idInfo == null) throw new RuntimeException("Unknown attackType: " + attackType);
                        id = idInfo[0] + "_" + paddingLevel;
                        desc = idInfo[1] + " " + paddingLevel;
                    }
                    attackTestCases.add(new String[]{id, "", desc});
                    attackVectors.add(v);
                    break;
            }
        }

        System.out.printf("Loaded %d files in %.1f s (avg %.1f ms/file, %d slow)%n%n",
            totalFiles, totalLoadMs / 1000.0, (double) totalLoadMs / totalFiles, slowFileCount);

        System.out.printf("Baseline: %d vectors, Normal: %d vectors, Attacks: %d test cases%n%n",
            baselineVectors.size(), normalVectors.size(), attackTestCases.size());

        System.out.printf("Pre-cached %d attack vectors.%n%n", attackVectors.size());

        // Phase 0: Warmup Detection (Cold Start)
        long phase0StartMs = System.currentTimeMillis();
        System.out.println("=== Phase 0: Warmup Detection (Cold Start) ===");
        double[] warmupWeights = WeightedEuclideanScorer.DEFAULT_WEIGHTS;
        RansomwareDetector warmupDetector = new RansomwareDetector(warmupWeights);

        int phase0Pass = 0;
        int phase0Fail = 0;

        // 3a: Feed 2 clean normal vectors
        System.out.println("\n--- Step 1: Feed 2 clean normal vectors ---");
        for (int i = 0; i < 2 && i < baselineVectors.size(); i++) {
            DetectionResult r = warmupDetector.detect(baselineVectors.get(i));
            System.out.printf("  Round %d: score=%.2f, threshold=%.2f, anomaly=%b, warmup=%b, baseline=%d%n",
                i+1, r.getScore(), r.getThreshold(), r.isAnomaly(),
                warmupDetector.isInWarmupMode(), warmupDetector.getBaselineCount());
        }

        // 3b: Feed 1 attack vector
        System.out.println("\n--- Step 2: Feed 1 attack vector (should be detected by heuristics) ---");
        if (!attackVectors.isEmpty()) {
            DetectionResult r = warmupDetector.detect(attackVectors.get(0));
            System.out.printf("  Attack round: score=%.2f, threshold=%.2f, anomaly=%b, warmup=%b, baseline=%d%n",
                r.getScore(), r.getThreshold(), r.isAnomaly(),
                warmupDetector.isInWarmupMode(), warmupDetector.getBaselineCount());
            if (r.isAnomaly() && r.getScore() >= 2.0 && warmupDetector.isInWarmupMode()) {
                System.out.println("  ✓ Warmup heuristic correctly detected ransomware");
                phase0Pass++;
            } else {
                System.out.printf("  ✗ Warmup heuristic FAILED to detect ransomware (anomaly=%b, score=%.2f, warmup=%b)%n",
                    r.isAnomaly(), r.getScore(), warmupDetector.isInWarmupMode());
                phase0Fail++;
            }
        }

        // 3c: Feed 3 more clean normal vectors (total 5 clean)
        System.out.println("\n--- Step 3: Feed 3 more clean normal vectors (transition to statistical) ---");
        for (int i = 2; i < 5 && i < baselineVectors.size(); i++) {
            DetectionResult r = warmupDetector.detect(baselineVectors.get(i));
            System.out.printf("  Round %d: score=%.2f, threshold=%.2f, anomaly=%b, warmup=%b, baseline=%d%n",
                i+1, r.getScore(), r.getThreshold(), r.isAnomaly(),
                warmupDetector.isInWarmupMode(), warmupDetector.getBaselineCount());
        }

        // 3d: Verify transition
        if (!warmupDetector.isInWarmupMode()) {
            System.out.println("  ✓ Warmup ended, transitioned to statistical mode");
            phase0Pass++;
        } else {
            System.out.println("  ✗ Warmup did NOT end after 5 clean vectors");
            phase0Fail++;
        }

        // 3e: Feed 1 more attack via statistical detection
        System.out.println("\n--- Step 4: Feed attack via statistical detection ---");
        if (attackVectors.size() > 1) {
            DetectionResult r = warmupDetector.detect(attackVectors.get(1));
            System.out.printf("  Statistical attack: score=%.2f, threshold=%.2f, anomaly=%b, warmup=%b%n",
                r.getScore(), r.getThreshold(), r.isAnomaly(), warmupDetector.isInWarmupMode());
            if (r.isAnomaly()) {
                System.out.println("  ✓ Post-warmup statistical detection works");
                phase0Pass++;
            } else {
                System.out.println("  ✗ Post-warmup statistical detection FAILED");
                phase0Fail++;
            }
        }

        long phase0Ms = System.currentTimeMillis() - phase0StartMs;
        System.out.printf("%n--- Phase 0 Summary: %d passed, %d failed ---%n", phase0Pass, phase0Fail);
        System.out.printf("Phase 0 completed in %.1f s%n%n", phase0Ms / 1000.0);

        // Phase 3: Velocity weight scan (using cached vectors)
        long phase3StartMs = System.currentTimeMillis();
        System.out.println("=== Phase 3: Testing velocity weight candidates ===\n");
        System.out.printf("%-8s | %-5s %-6s %-5s | %-60s%n",
            "VelWt", "Attks", "Caught", "FPs", "Missed attacks");
        System.out.println("-".repeat(120));

        double bestVelW = VELOCITY_WEIGHT_CANDIDATES[0];
        int bestCaught = -1;
        int bestFps = Integer.MAX_VALUE;

        for (double velWeight : VELOCITY_WEIGHT_CANDIDATES) {
            double[] w = BASE_WEIGHTS.clone();
            w[2] = velWeight;

            BaselineStatistics stats = new BaselineStatistics(baselineVectors);
            for (int i = 0; i < baselineVectors.size(); i++) {
                int hour = BUSINESS_HOURS[i % BUSINESS_HOURS.length];
                stats.addHourlyObservation(hour, baselineVectors.get(i).getTotalOperationsNormalized() * 2.0);
            }
            stats.computeHourlyStats();

            WeightedEuclideanScorer scorer = new WeightedEuclideanScorer(stats, w);
            AnomalyThreshold threshold = new AnomalyThreshold(baselineVectors, scorer, 97.0, 0.0);
            RansomwareDetector detector = new RansomwareDetector(stats, threshold, w, extractor);

            int caught = 0;
            List<String> missed = new ArrayList<>();

            for (int j = 0; j < attackTestCases.size(); j++) {
                String tcId = attackTestCases.get(j)[0];
                RansomwareFeatureVector v = attackVectors.get(j);
                DetectionResult result = detector.detect(v);
                if (result.isAnomaly()) {
                    caught++;
                } else {
                    missed.add(tcId + "(" + String.format("%.1f", result.getScore()) + ")");
                }
            }

            int fps = 0;
            for (RansomwareFeatureVector nv : normalVectors) {
                if (detector.detect(nv).isAnomaly()) fps++;
            }

            String missedStr = missed.isEmpty() ? "-" : String.join(", ", missed);
            System.out.printf("%-8.2f | %-5d %-6d %-5d | %s%n",
                velWeight, attackTestCases.size(), caught, fps, missedStr);

            if (caught > bestCaught || (caught == bestCaught && fps < bestFps)) {
                bestCaught = caught;
                bestFps = fps;
                bestVelW = velWeight;
            }
        }

        long phase3Ms = System.currentTimeMillis() - phase3StartMs;
        System.out.printf("Phase 3 completed in %.1f s%n", phase3Ms / 1000.0);

        // Phase 4: Detailed results with best velocity weight (using cached vectors)
        long phase4StartMs = System.currentTimeMillis();
        System.out.println("\n=== Phase 4: Detailed results with BEST velocity weight ===\n");

        double[] bestW = BASE_WEIGHTS.clone();
        bestW[2] = bestVelW;
        BaselineStatistics stats = new BaselineStatistics(baselineVectors);
        for (int i = 0; i < baselineVectors.size(); i++) {
            stats.addHourlyObservation(baselineHours.get(i), baselineOpsCounts.get(i));
        }
        stats.computeHourlyStats();
        WeightedEuclideanScorer scorer = new WeightedEuclideanScorer(stats, bestW);
        AnomalyThreshold threshold = new AnomalyThreshold(baselineVectors, scorer, 97.0, 0.0);
        RansomwareDetector detector = new RansomwareDetector(stats, threshold, bestW, extractor);

        System.out.printf("Velocity weight: %.2f, Threshold: %.4f%n%n", bestVelW, threshold.getThreshold());
        System.out.printf("%-30s %8s %8s %6s %s%n",
            "Attack", "Score", "Thresh", "Detect", "Top Deviation");
        System.out.println("-".repeat(100));

        for (int j = 0; j < attackTestCases.size(); j++) {
            String[] tc = attackTestCases.get(j);
            String tDesc = tc[2].isEmpty() ? tc[0] : tc[2];
            RansomwareFeatureVector v = attackVectors.get(j);
            DetectionResult result = detector.detect(v);

            String topDev = result.getTopDeviations().isEmpty() ? "-"
                : result.getTopDeviations().get(0).getKey() + "="
                + String.format("%.2f", result.getTopDeviations().get(0).getValue());

            System.out.printf("%-30s %8.2f %8.2f %6s %s%n",
                tDesc, result.getScore(), result.getThreshold(),
                result.isAnomaly() ? "YES" : "NO", topDev);
        }

        // Summary by padding level (using cached vectors)
        System.out.println("\n--- Detection by padding level ---");
        System.out.printf("%-8s %-6s %-6s %-8s %-8s%n",
            "Level", "Total", "Caught", "Rate", "FPs");
        System.out.println("-".repeat(50));

        for (double paddingRatio : PADDING_LEVELS) {
            String levelTag = "p" + ((int) (paddingRatio * 100));
            int total = 0;
            int caught = 0;
            for (int j = 0; j < attackTestCases.size(); j++) {
                if (!attackTestCases.get(j)[0].endsWith("_" + levelTag)) continue;
                total++;
                if (detector.detect(attackVectors.get(j)).isAnomaly()) caught++;
            }
            System.out.printf("%-8s %-6d %-6d %-7.1f%%", levelTag, total, caught, 100.0 * caught / total);
            if (levelTag.equals("p20")) {
                int fps = 0;
                for (RansomwareFeatureVector nv : normalVectors) {
                    if (detector.detect(nv).isAnomaly()) fps++;
                }
                System.out.printf(" %-8d", fps);
            }
            System.out.println();
        }

        System.out.println("\n--- Vanilla normal FP check (baseline vectors only) ---");
        int vanillaFps = 0;
        for (int i = 0; i < baselineVectors.size(); i++) {
            DetectionResult r = detector.detect(baselineVectors.get(i));
            if (r.isAnomaly()) {
                vanillaFps++;
                String topDev = r.getTopDeviations().isEmpty() ? ""
                    : " top=" + r.getTopDeviations().stream()
                        .limit(3)
                        .map(e -> e.getKey() + "=" + String.format("%.1f", e.getValue()))
                        .reduce((a, b) -> a + ", " + b).orElse("");
                System.out.printf("  FP: baseline round %d, score=%.2f > threshold=%.2f%s%n",
                    i + 1, r.getScore(), r.getThreshold(), topDev);
            }
        }
        System.out.printf("Vanilla FPs: %d/%d (%.1f%%)%n%n", vanillaFps, baselineVectors.size(),
            100.0 * vanillaFps / baselineVectors.size());

        System.out.println("--- Irregular normal FP check ---");
        int irregularFps = 0;
        for (int i = 0; i < irregularVectors.size(); i++) {
            DetectionResult r = detector.detect(irregularVectors.get(i));
            boolean fp = r.isAnomaly();
            String topDev = "";
            if (fp && !r.getTopDeviations().isEmpty()) {
                topDev = " top=" + r.getTopDeviations().stream()
                    .limit(3)
                    .map(e -> e.getKey() + "=" + String.format("%.1f", e.getValue()))
                    .reduce((a, b) -> a + ", " + b).orElse("");
            }
            System.out.printf("  %-35s score=%7.2f > %-7.2f %s%s%n",
                irregularLabels.get(i), r.getScore(), r.getThreshold(),
                fp ? "<< FP" : "OK", topDev);
            if (fp) irregularFps++;
        }
        System.out.printf("Irregular FPs: %d/%d (%.1f%%)%n", irregularFps, irregularVectors.size(),
            100.0 * irregularFps / irregularVectors.size());

        System.out.printf("%n=== FP Summary ===%n");
        System.out.printf("  Vanilla normal:   %d/%d (%.1f%%)%n", vanillaFps, baselineVectors.size(),
            100.0 * vanillaFps / baselineVectors.size());
        System.out.printf("  Irregular normal: %d/%d (%.1f%%)%n", irregularFps, irregularVectors.size(),
            100.0 * irregularFps / irregularVectors.size());
        System.out.printf("  Combined:         %d/%d (%.1f%%)%n", vanillaFps + irregularFps,
            baselineVectors.size() + irregularVectors.size(),
            100.0 * (vanillaFps + irregularFps) / (baselineVectors.size() + irregularVectors.size()));

        // Quiet-day directional validation test
        System.out.printf("%n--- Quiet-day directional validation test ---%n");
        if (!quietDayVectors.isEmpty()) {
            RansomwareDetector detectorWithValidation = new RansomwareDetector(
                stats, threshold, bestW, extractor, 0.75);

            System.out.printf("%-15s %8s %8s %8s %-8s %-8s %s%n",
                "QuietDay", "Score", "Thresh", "DirRatio", "NoValid", "WValid", "TopBelow");
            System.out.println("-".repeat(90));

            int reversedCount = 0;
            for (int i = 0; i < quietDayVectors.size(); i++) {
                RansomwareFeatureVector qv = quietDayVectors.get(i);

                DetectionResult rNoValidation = detector.detect(qv);
                boolean flaggedAsAnomaly = rNoValidation.isAnomaly();

                double[] zScores = computeZScores(stats, qv);
                DirectionalValidator dval = new DirectionalValidator(bestW, 0.75);
                DirectionalValidator.ValidationResult vr = dval.validate(zScores);

                DetectionResult rWithValidation = detectorWithValidation.detect(qv);
                boolean reversedToNormal = !rWithValidation.isAnomaly();

                String topBelow = "-";
                if (!vr.topDeviations.isEmpty()) {
                    topBelow = vr.topDeviations.stream()
                        .filter(d -> "BELOW".equals(d.direction()))
                        .limit(3)
                        .map(d -> d.name() + "=" + String.format("%.1f", d.zScore()))
                        .reduce((a, b) -> a + ", " + b).orElse("-");
                }

                System.out.printf("%-15s %8.2f %8.2f %8.3f %-8s %-8s %s%n",
                    quietDayLabels.get(i),
                    rNoValidation.getScore(),
                    rNoValidation.getThreshold(),
                    vr.ratio,
                    flaggedAsAnomaly ? "ANOMALY" : "NORMAL",
                    reversedToNormal ? "NORMAL" : "ANOMALY",
                    topBelow);

                if (flaggedAsAnomaly && reversedToNormal) reversedCount++;
            }
            System.out.printf("Quiet days reversed: %d/%d%n", reversedCount, quietDayVectors.size());
        } else {
            System.out.println("  No quiet-day vectors found in manifest.");
        }

        long phase4Ms = System.currentTimeMillis() - phase4StartMs;
        long totalMs = System.currentTimeMillis() - benchmarkStartMs;
        System.out.printf("%n=== Pipeline Timing Summary ===%n");
        System.out.printf("  Data loading:      %.1f s (%d files, avg %.1f ms/file)%n",
            totalLoadMs / 1000.0, totalFiles, (double) totalLoadMs / totalFiles);
        System.out.printf("  Phase 0 (warmup):  %.1f s%n", phase0Ms / 1000.0);
        System.out.printf("  Phase 3 (scan):    %.1f s%n", phase3Ms / 1000.0);
        System.out.printf("  Phase 4 (detailed): %.1f s%n", phase4Ms / 1000.0);
        System.out.printf("%n=== Total benchmark time: %.1f s ===%n", totalMs / 1000.0);
    }

    /**
     * Extract the hour-of-day of the earliest non-EPOCH change_time using streaming parser.
     * Only used for the first 24 baseline files (small files, double-parse is acceptable).
     */
    private static int extractDominantHourStreaming(Path filePath) throws IOException {
        Instant[] earliest = {null};
        new StreamingSnapdiffParser().parse(filePath, rec -> {
            Instant time = rec.getChangeTime();
            if (time != null && !time.equals(Instant.EPOCH)) {
                if (earliest[0] == null || time.isBefore(earliest[0])) {
                    earliest[0] = time;
                }
            }
        });
        if (earliest[0] == null) return -1;
        return earliest[0].atOffset(ZoneOffset.UTC).getHour();
    }

    /**
     * Count total records in a snapdiff file using streaming parser.
     * Only used for the first 24 baseline files (small files, double-parse is acceptable).
     */
    private static int countRecordsStreaming(Path filePath) throws IOException {
        int[] count = {0};
        new StreamingSnapdiffParser().parse(filePath, rec -> count[0]++);
        return count[0];
    }

    private static double[] computeZScores(BaselineStatistics stats, RansomwareFeatureVector v) {
        double[] median = stats.getMedian();
        double[] mad = stats.getMad();
        double[] z = new double[RansomwareFeatureVector.FEATURE_COUNT];
        for (int i = 0; i < z.length; i++) {
            z[i] = (v.get(i) - median[i]) / mad[i];
            z[i] = Math.max(-10.0, Math.min(10.0, z[i]));
        }
        return z;
    }
}
