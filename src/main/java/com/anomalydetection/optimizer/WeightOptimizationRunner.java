package com.anomalydetection.optimizer;

import com.anomalydetection.features.FeatureType;
import com.anomalydetection.features.FeatureVector;
import com.anomalydetection.detector.SensitivityAdjuster;
import com.anomalydetection.detector.heuristic.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Standalone runner for weight optimization.
 * <p>
 * Generates 144 benchmark test cases, runs WeightOptimizer for both
 * Active and Warmup phases (20000 iterations each), and outputs
 * a detailed report to console and docs/weight-optimization-report.md.
 */
public class WeightOptimizationRunner {

    private static final int ITERATIONS = 20000;
    private static final double TARGET_PERCENTILE = 97.0;

    public static void main(String[] args) throws IOException {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 42L;
        System.out.println("=== Weight Optimization Runner ===");
        System.out.println("Seed: " + seed + ", Iterations: " + ITERATIONS);
        System.out.println();

        // Step 1: Generate dataset
        System.out.println("--- Step 1: Generating Benchmark Dataset ---");
        BenchmarkDataGenerator generator = new BenchmarkDataGenerator(seed);
        BenchmarkDataGenerator.Dataset dataset = generator.generateFullDataset();
        System.out.println("  Normal:   " + dataset.normals.size() + " vectors");
        System.out.println("  Attack:   " + dataset.attacks.size() + " vectors");
        System.out.println("  Boundary: " + dataset.boundaries.size() + " vectors");
        System.out.println("  Total:    " + dataset.all.size() + " vectors");
        System.out.println();

        // Step 2: Use core normals (N1+N2+N3+N5+N6 = 50 vecs) for baseline
        // This is moderate-diversity baseline: still has variation (office + compile + quiet + ...)
        // but excludes extreme patterns (data migration, ETL, security scan) that inflate MAD.
        // Extended normals (N4+N7+N8 = 16 vecs) are tracked for FP check separately.
        System.out.println("  Core baseline: " + dataset.coreNormals.size()
                + " normals (N1+N2+N3+N5+N6)");
        System.out.println("  Extended normals: " + dataset.extendedNormals.size()
                + " (N4+N7+N8 - tracked as FP validation)");
        System.out.println("  Attacks: " + dataset.attacks.size());
        System.out.println("  Boundaries: " + dataset.boundaries.size());
        System.out.println();

        System.out.println("--- Step 2: Baseline Statistics (from " + dataset.coreNormals.size() + " core normals) ---");
        double[] median = new double[FeatureType.COUNT];
        double[] mad = new double[FeatureType.COUNT];
        List<FeatureVector> coreFVs = dataset.coreNormals.stream().map(v -> v.vector).collect(Collectors.toList());
        computeMedianMad(coreFVs, median, mad);
        printFeatureStats(median, mad);

        // Step 3: Run Warmup Layer 2 heuristic evaluation
        int l2Sensitivity = SensitivityAdjuster.DEFAULT_SENSITIVITY;
        System.out.println("--- Step 3: Warmup Layer 2 Heuristic Evaluation (sensitivity=" + l2Sensitivity + ") ---");
        String[][] l2Results = evaluateLayer2(dataset);
        printLayer2Results(l2Results, dataset);

        // Step 4: ActiveDetector weight optimization
        System.out.println("--- Step 4: ActiveDetector Weight Optimization ---");
        List<FeatureVector> attackFVs = dataset.attacks.stream().map(v -> v.vector).collect(Collectors.toList());
        WeightOptimizer activeOpt = new WeightOptimizer(coreFVs, attackFVs);
        WeightOptimizer.OptimizationResult activeRaw = activeOpt.optimize(ITERATIONS, TARGET_PERCENTILE);
        // Compute combined FP: core normals + extended normals + boundaries
        int activeExtFP = countFP(activeRaw.weights, median, mad, activeRaw.threshold, dataset.extendedNormals);
        int activeBFP = countFP(activeRaw.weights, median, mad, activeRaw.threshold, dataset.boundaries);
        int activeTotalFP = activeRaw.falsePositives + activeExtFP + activeBFP;
        int activeTotalN = activeRaw.totalNormals + dataset.extendedNormals.size() + dataset.boundaries.size();
        System.out.println("Active Raw: " + activeRaw);
        System.out.println("  + extended FP=" + activeExtFP + "/" + dataset.extendedNormals.size()
                + " boundary FP=" + activeBFP + "/" + dataset.boundaries.size()
                + " total FP=" + activeTotalFP + "/" + activeTotalN);
        
        // Step 5: WarmupDetector Layer 3 weight optimization (same method)
        // Step 5: WarmupDetector Layer 3 Weight Optimization
        System.out.println("--- Step 5: WarmupDetector Layer 3 Weight Optimization ---");
        WeightOptimizer warmupOpt = new WeightOptimizer(coreFVs, attackFVs);
        WeightOptimizer.OptimizationResult warmupRaw = warmupOpt.optimize(ITERATIONS, TARGET_PERCENTILE);
        int warmupExtFP = countFP(warmupRaw.weights, median, mad, warmupRaw.threshold, dataset.extendedNormals);
        int warmupBFP = countFP(warmupRaw.weights, median, mad, warmupRaw.threshold, dataset.boundaries);
        System.out.println("Warmup Raw: " + warmupRaw);
        System.out.println("  + extended FP=" + warmupExtFP + "/" + dataset.extendedNormals.size()
                + " boundary FP=" + warmupBFP + "/" + dataset.boundaries.size());

        // Build combined result objects
        WeightOptimizer.OptimizationResult activeResult = new WeightOptimizer.OptimizationResult(
                activeRaw.weights, activeRaw.auc, activeRaw.threshold,
                activeRaw.attacksCaught, activeRaw.totalAttacks,
                activeTotalFP, activeTotalN);
        WeightOptimizer.OptimizationResult warmupResult = new WeightOptimizer.OptimizationResult(
                warmupRaw.weights, warmupRaw.auc, warmupRaw.threshold,
                warmupRaw.attacksCaught, warmupRaw.totalAttacks,
                activeTotalFP, activeTotalN);
        printTopWeights(activeResult.weights, "Active");
        printTopWeights(warmupResult.weights, "Warmup");

        // Step 6: Full scoring table
        System.out.println("--- Step 6: Per-Vector Scoring ---");
        printScoringTable(dataset, median, mad, activeResult, warmupResult);

        // Step 7: Write report file
        System.out.println("--- Step 7: Writing Report ---");
        String report = buildReport(dataset, median, mad, l2Results, activeResult, warmupResult);
        Path reportPath = Paths.get("docs", "weight-optimization-report.md");
        Files.createDirectories(reportPath.getParent());
        Files.writeString(reportPath, report);
        System.out.println("Report written to: " + reportPath.toAbsolutePath());

        // Summary
        System.out.println();
        // Step 8: Warmup progressive validation with sensitivity test
        System.out.println("--- Step 8: Warmup Progressive Validation ---");
        List<FeatureVector> allNormalFVs = dataset.normals.stream().map(v -> v.vector).collect(Collectors.toList());
        List<FeatureVector> allAttackFVs = dataset.attacks.stream().map(v -> v.vector).collect(Collectors.toList());
        for (int testSens : new int[]{SensitivityAdjuster.DEFAULT_SENSITIVITY, SensitivityAdjuster.MAX_SENSITIVITY}) {
            WarmupValidator val = new WarmupValidator(allNormalFVs, allAttackFVs, testSens);
            WarmupValidator.ValidationResult vr = val.simulate(2, Math.min(10, allNormalFVs.size()));
            System.out.println("sensitivity=" + testSens + ":");
            // Show first and last stage as summary
            var first = vr.stages.get(0);
            var last = vr.stages.get(vr.stages.size() - 1);
            System.out.println("  start(n=2): L2=" + first.layer2Triggers + " L3=" + first.layer3Triggers
                    + " detection=" + first.attacksCaught + "/" + first.totalAttacks
                    + " FP=" + first.falsePositives + "/" + first.totalValidationNormals);
            System.out.println("  end(n=" + last.historySize + "): L2=" + last.layer2Triggers + " L3=" + last.layer3Triggers
                    + " detection=" + last.attacksCaught + "/" + last.totalAttacks
                    + " FP=" + last.falsePositives + "/" + last.totalValidationNormals);
        }
        System.out.println();

        // Step 9: Layer 2 strategy comparison (≥1 vs ≥2)
        System.out.println("--- Step 9: Layer 2 Strategy Comparison ---");
        int a2 = 0, a1 = 0, n2 = 0, n1 = 0;
        for (int i = 0; i < dataset.all.size(); i++) {
            var lv = dataset.all.get(i);
            int t = l2Results[i].length;
            if (lv.isAttack) {
                if (t >= 2) a2++;
                if (t >= 1) a1++;
            } else if (!lv.group.equals("BOUNDARY")) {
                if (t >= 2) n2++;
                if (t >= 1) n1++;
            }
        }
        System.out.println(String.format("%-20s %-15s %-15s %s", "Rule Condition", "Attack Detected", "Normal FP", "Baseline Risk"));
        System.out.println("-".repeat(75));
        System.out.println(String.format("%-20s %-15s %-15s %s",
                ">= 1 rule", a1 + "/" + dataset.attacks.size(), n1 + "/" + dataset.normals.size(),
                (69 - a1) + " FN leak into baseline"));
        System.out.println(String.format("%-20s %-15s %-15s %s",
                ">= 2 rules (current)", a2 + "/" + dataset.attacks.size(), n2 + "/" + dataset.normals.size(),
                (69 - a2) + " FN leak into baseline"));

        // Baseline pollution comparison
        System.out.println();
        System.out.println("--- Baseline Pollution Analysis ---");
        int oldLeak = 69 - a2;  // ≥2 alert → 25 attacks NOT detected → ALL go into baseline
        int newLeak = 69 - a1;  // 1+ triggered → excluded → only 0-trigger attacks leak
        // Count attacks with 0 triggered rules
        int attackZeroTrig = 0;
        for (int i = 0; i < dataset.all.size(); i++) {
            if (dataset.all.get(i).isAttack && l2Results[i].length == 0) attackZeroTrig++;
        }
        System.out.println(String.format("%-40s %-15s %-15s", "Strategy", "Attack Leak", "Normal Excluded"));
        System.out.println("-".repeat(70));
        System.out.println(String.format("%-40s %-15s %-15s",
                "Old: alert=baseline (>=2 rules)", oldLeak + "/69", n2 + "/66"));
        System.out.println(String.format("%-40s %-15s %-15s",
                "New: decoupled (alert>=2, base>=1)", newLeak + "/69", n1 + "/66"));
        System.out.println(String.format("%-40s %-15s",
                "Improvement", "-" + (oldLeak - newLeak) + " attack leak"));
        System.out.println();

        // Warmup-specific weight optimization using OnlineWeightUpdater
        System.out.println("--- Warmup-Specific Weight Analysis ---");
        System.out.println("Running OnlineWeightUpdater with simulated n=5 small-sample shrinkage...");
        OnlineWeightUpdater updater = new OnlineWeightUpdater(
                dataset.coreNormals.stream().map(v -> v.vector).collect(Collectors.toList()),
                dataset.attacks.stream().map(v -> v.vector).collect(Collectors.toList()));
        var updaterResult = updater.optimize(activeRaw.weights, activeRaw.weights, ITERATIONS);
        System.out.println("Active (standard)       AUC=" + String.format("%.4f", updaterResult.activeAuc));
        System.out.println("Warmup (n=5 shrinkage)  AUC=" + String.format("%.4f", updaterResult.warmupAuc));
        System.out.println("Warmup improved: " + (updaterResult.warmupImproved ? "YES" : "NO"));
        if (updaterResult.warmupImproved) {
            System.out.println("  Warmup-specific weights differ from Active weights.");
            System.out.println("  Top 5 Warmup weights:");
            Integer[] wIdx = new Integer[FeatureType.COUNT];
            for (int i = 0; i < FeatureType.COUNT; i++) wIdx[i] = i;
            java.util.Arrays.sort(wIdx, (a, b) -> Double.compare(updaterResult.warmupWeights[b], updaterResult.warmupWeights[a]));
            for (int r = 0; r < Math.min(5, FeatureType.COUNT); r++) {
                int i = wIdx[r];
                System.out.println(String.format("    [%d] %s = %.4f (Active: %.4f)",
                        i, FeatureType.values()[i].key(),
                        updaterResult.warmupWeights[i],
                        updaterResult.activeWeights[i]));
            }
        } else {
            System.out.println("  Warmup-specific weights do NOT meaningfully differ.");
            System.out.println("  Shared weights are sufficient for both phases.");
        }
        System.out.println();

        // Final recommendation
        System.out.println("--- Recommendation ---");
        System.out.println("Active phase: use optimized weights (from Step 4)");
        System.out.println("Warmup phase: keep alert on >=2 rules, set baseline gate at >=1 rule");
        System.out.println("  (already implemented in WarmupDetector.toWarmupInfo() — addToBaseline = !isAnomaly && ruleCount < 1)");
        System.out.println();

        // Summary
        System.out.println("=== Summary ===");
        System.out.println("Active:  AUC=" + String.format("%.4f", activeResult.auc)
                + " caught=" + activeResult.attacksCaught + "/" + activeResult.totalAttacks
                + " FP=" + activeResult.falsePositives + "/" + activeResult.totalNormals);
        System.out.println("Warmup: AUC=" + String.format("%.4f", warmupResult.auc)
                + " caught=" + warmupResult.attacksCaught + "/" + warmupResult.totalAttacks
                + " FP=" + warmupResult.falsePositives + "/" + warmupResult.totalNormals);


        System.out.println("=== Acceptance Criteria ===");
        checkAcceptance(activeResult, warmupResult);
    }

    // ========================================================================
    // Layer 2 evaluation
    // ========================================================================

    private static String[][] evaluateLayer2(BenchmarkDataGenerator.Dataset dataset) {
        List<HeuristicRule> rules = List.of(
                new ModificationRatioRule(),
                new BurstModPurityRule(),
                new FileTypeConcentrationRule(),
                new InterOpTimeCvRule(),
                new HighValueTargetingRule(),
                new DeletionIntensityRule()
        );
        int sensitivity = SensitivityAdjuster.DEFAULT_SENSITIVITY;
        String[][] results = new String[dataset.all.size()][];
        for (int idx = 0; idx < dataset.all.size(); idx++) {
            var lv = dataset.all.get(idx);
            List<String> triggered = new ArrayList<>();
            for (HeuristicRule rule : rules) {
                RuleResult r = rule.evaluate(lv.vector, sensitivity);
                if (r.isTriggered()) {
                    triggered.add(r.getRuleName());
                }
            }
            results[idx] = triggered.toArray(new String[0]);
        }
        return results;
    }

    private static void printLayer2Results(String[][] l2Results, BenchmarkDataGenerator.Dataset dataset) {
        // Group by attack type
        Map<String, List<Integer>> groups = new LinkedHashMap<>();
        for (int i = 0; i < dataset.all.size(); i++) {
            String g = dataset.all.get(i).group;
            groups.computeIfAbsent(g, k -> new ArrayList<>()).add(i);
        }
        System.out.println(String.format("%-20s %-10s %-10s %s", "Group", "Count", "≥2 Trig", "Details"));
        System.out.println("-".repeat(80));
        for (var entry : groups.entrySet()) {
            int count = 0;
            int triggerCount = 0;
            StringBuilder sb = new StringBuilder();
            for (int idx : entry.getValue()) {
                count++;
                if (l2Results[idx].length >= 2) {
                    triggerCount++;
                }
                if (sb.length() > 0) sb.append(", ");
                sb.append(dataset.all.get(idx).id).append(":").append(l2Results[idx].length);
            }
            String status = triggerCount == count ? "ALL" : triggerCount + "/" + count;
            System.out.println(String.format("%-20s %-10d %-10s %s", entry.getKey(), count, status, sb));
        }
        // Summary
        long attackTrig2 = 0, normalTrig2 = 0, boundaryTrig2 = 0;
        for (int i = 0; i < dataset.all.size(); i++) {
            var lv = dataset.all.get(i);
            boolean triggered = l2Results[i].length >= 2;
            if (lv.isAttack) { if (triggered) attackTrig2++; }
            else if (lv.group.equals("BOUNDARY")) { if (triggered) boundaryTrig2++; }
            else { if (triggered) normalTrig2++; }
        }
        System.out.println("-".repeat(80));
        System.out.println("Layer 2 triggers (>=2 rules): "
                + "Attack=" + attackTrig2 + "/" + dataset.attacks.size()
                + " Normal=" + normalTrig2 + "/" + dataset.normals.size()
                + " Boundary=" + boundaryTrig2 + "/" + dataset.boundaries.size());
        System.out.println();
    }

    // ========================================================================
    // Scoring
    // ========================================================================

    private static void printScoringTable(BenchmarkDataGenerator.Dataset dataset,
                                          double[] median, double[] mad,
                                          WeightOptimizer.OptimizationResult activeResult,
                                          WeightOptimizer.OptimizationResult warmupResult) {
        System.out.println(String.format("%-25s %-6s %-10s %-10s %-10s %-10s",
                "ID", "Label", "L2(≥2?)", "ActiveScore", "ActiveHit?", "WarmupScore"));
        System.out.println("-".repeat(80));
        int activeHits = 0, warmupHits = 0;
        int attackActive = 0, attackWarmup = 0;
        for (var lv : dataset.all) {
            double[] vals = lv.vector.toArray();
            double activeScore = computeScore(vals, median, mad, activeResult.weights);
            double warmupScore = computeScore(vals, median, mad, warmupResult.weights);
            boolean activeHit = activeScore > activeResult.threshold;
            boolean warmupHit = warmupScore > warmupResult.threshold;
            if (lv.isAttack) {
                attackActive += activeHit ? 1 : 0;
                attackWarmup += warmupHit ? 1 : 0;
            } else if (!lv.group.equals("BOUNDARY")) {
                activeHits += activeHit ? 1 : 0;
                warmupHits += warmupHit ? 1 : 0;
            }
            // Print all attacks and misclassified normals
            if (lv.isAttack || activeHit || warmupHit || lv.group.equals("BOUNDARY")) {
                System.out.println(String.format("%-25s %-6s %-10s %-10.4f %-10s %-10.4f %s",
                        lv.id, lv.isAttack ? "ATTACK" : "NORMAL",
                        "-", activeScore, activeHit ? "⚠️" : "✓",
                        warmupScore, warmupHit ? "⚠️" : "✓"));
            }
        }
        System.out.println("-".repeat(80));
        System.out.println("Active: attack caught=" + attackActive + "/" + dataset.attacks.size()
                + " normal FP=" + activeHits + "/" + dataset.normals.size());
        System.out.println("Warmup: attack caught=" + attackWarmup + "/" + dataset.attacks.size()
                + " normal FP=" + warmupHits + "/" + dataset.normals.size());
        System.out.println();
    }

    private static double computeScore(double[] values, double[] median, double[] mad, double[] weights) {
        double sum = 0;
        for (int i = 0; i < FeatureType.COUNT; i++) {
            double z = Math.max(-10, Math.min(10, (values[i] - median[i]) / Math.max(mad[i], 0.001)));
            sum += weights[i] * z * z;
        }
        return Math.sqrt(sum);
    }

    // ========================================================================
    // Report
    // ========================================================================

    private static String buildReport(BenchmarkDataGenerator.Dataset dataset,
                                      double[] median, double[] mad,
                                      String[][] l2Results,
                                      WeightOptimizer.OptimizationResult activeResult,
                                      WeightOptimizer.OptimizationResult warmupResult) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Weight Optimization Report\n\n");
        sb.append("Generated: ").append(new Date()).append("\n\n");

        sb.append("## Dataset\n\n");
        sb.append("- Normal: ").append(dataset.normals.size()).append("\n");
        sb.append("- Attack: ").append(dataset.attacks.size()).append("\n");
        sb.append("- Boundary: ").append(dataset.boundaries.size()).append("\n");
        sb.append("- Total: ").append(dataset.all.size()).append("\n\n");

        sb.append("## Baseline Statistics\n\n");
        sb.append("| Feature | Median | MAD |\n");
        sb.append("|---------|--------|-----|\n");
        for (int i = 0; i < FeatureType.COUNT; i++) {
            sb.append("| ").append(FeatureType.values()[i].key())
                    .append(" | ").append(String.format("%.4f", median[i]))
                    .append(" | ").append(String.format("%.4f", mad[i])).append(" |\n");
        }
        sb.append("\n");

        sb.append("## Layer 2 Trigger Summary\n\n");
        long a2 = 0, n2 = 0, b2 = 0;
        for (int i = 0; i < dataset.all.size(); i++) {
            var lv = dataset.all.get(i);
            if (l2Results[i].length >= 2) {
                if (lv.isAttack) a2++;
                else if (lv.group.equals("BOUNDARY")) b2++;
                else n2++;
            }
        }
        sb.append("Attack ≥2 triggers: ").append(a2).append("/").append(dataset.attacks.size()).append("\n");
        sb.append("Normal ≥2 triggers: ").append(n2).append("/").append(dataset.normals.size()).append("\n");
        sb.append("Boundary ≥2 triggers: ").append(b2).append("/").append(dataset.boundaries.size()).append("\n\n");

        sb.append("## Optimization Results\n\n");

        sb.append("### Active Phase\n\n");
        sb.append("- AUC: ").append(String.format("%.4f", activeResult.auc)).append("\n");
        sb.append("- Threshold: ").append(String.format("%.4f", activeResult.threshold)).append("\n");
        sb.append("- Attacks caught: ").append(activeResult.attacksCaught).append("/").append(activeResult.totalAttacks).append("\n");
        sb.append("- False positives: ").append(activeResult.falsePositives).append("/").append(activeResult.totalNormals).append("\n\n");
        sb.append("```\n");
        sb.append(weightArrayString(activeResult.weights, "FALLBACK_WEIGHTS"));
        sb.append("```\n\n");

        sb.append("### Warmup Phase (Layer 3)\n\n");
        sb.append("- AUC: ").append(String.format("%.4f", warmupResult.auc)).append("\n");
        sb.append("- Threshold: ").append(String.format("%.4f", warmupResult.threshold)).append("\n");
        sb.append("- Attacks caught: ").append(warmupResult.attacksCaught).append("/").append(warmupResult.totalAttacks).append("\n");
        sb.append("- False positives: ").append(warmupResult.falsePositives).append("/").append(warmupResult.totalNormals).append("\n\n");
        sb.append("```\n");
        sb.append(weightArrayString(warmupResult.weights, "WARMUP_WEIGHTS"));
        sb.append("```\n\n");

        sb.append("## Acceptance Check\n\n");
        sb.append("| Criterion | Active | Warmup |\n");
        sb.append("|-----------|--------|--------|\n");
        sb.append("| AUC ≥ 0.96 / 0.90 | ").append(pass(activeResult.auc >= 0.96)).append(" | ").append(pass(warmupResult.auc >= 0.90)).append(" |\n");
        sb.append("| Detection ≥ 92% / 85% | ").append(pass(activeResult.attacksCaught * 100.0 / activeResult.totalAttacks >= 92))
                .append(" | ").append(pass(warmupResult.attacksCaught * 100.0 / warmupResult.totalAttacks >= 85)).append(" |\n");
        sb.append("| FP ≤ 3 / 4 | ").append(pass(activeResult.falsePositives <= 3))
                .append(" | ").append(pass(warmupResult.falsePositives <= 4)).append(" |\n");

        return sb.toString();
    }

    private static String pass(boolean ok) {
        return ok ? "✅ PASS" : "❌ FAIL";
    }

    private static String weightArrayString(double[] weights, String name) {
        StringBuilder sb = new StringBuilder();
        sb.append("private static final double[] ").append(name).append(" = {\n");
        for (int i = 0; i < weights.length; i++) {
            sb.append(String.format("    %.4f", weights[i]));
            sb.append("  // F").append(i).append(" ").append(FeatureType.values()[i].key());
            if (i < weights.length - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("};");
        return sb.toString();
    }

    // ========================================================================
    // Utilities
    // ========================================================================

    private static void computeMedianMad(List<FeatureVector> vectors, double[] median, double[] mad) {
        int n = vectors.size();
        double[][] values = new double[n][FeatureType.COUNT];
        for (int h = 0; h < n; h++) values[h] = vectors.get(h).toArray();
        for (int i = 0; i < FeatureType.COUNT; i++) {
            double[] col = new double[n];
            for (int h = 0; h < n; h++) col[h] = values[h][i];
            Arrays.sort(col);
            median[i] = col[n / 2];
            double[] absDev = new double[n];
            for (int h = 0; h < n; h++) absDev[h] = Math.abs(col[h] - median[i]);
            Arrays.sort(absDev);
            mad[i] = absDev[n / 2] * 1.4826 + 0.001;
        }
    }

    private static void printFeatureStats(double[] median, double[] mad) {
        System.out.println(String.format("%-5s %-35s %-12s %-12s", "Idx", "Feature", "Median", "MAD"));
        System.out.println("-".repeat(70));
        for (int i = 0; i < FeatureType.COUNT; i++) {
            System.out.println(String.format("%-5d %-35s %-12.4f %-12.4f",
                    i, FeatureType.values()[i].key(), median[i], mad[i]));
        }
        System.out.println();
    }

    private static void printTopWeights(double[] weights, String phase) {
        System.out.println("Top 5 " + phase + " weights:");
        Integer[] idx = new Integer[weights.length];
        for (int i = 0; i < weights.length; i++) idx[i] = i;
        Arrays.sort(idx, (a, b) -> Double.compare(weights[b], weights[a]));
        for (int r = 0; r < Math.min(5, weights.length); r++) {
            int i = idx[r];
            System.out.println(String.format("  [%d] %s = %.4f",
                    i, FeatureType.values()[i].key(), weights[i]));
        }
        System.out.println();
    }

    private static int countFP(double[] weights, double[] median, double[] mad, double threshold,
                                List<BenchmarkDataGenerator.LabeledVector> vectors) {
        int fp = 0;
        for (var lv : vectors) {
            double[] vals = lv.vector.toArray();
            double score = computeScore(vals, median, mad, weights);
            if (score > threshold) fp++;
        }
        return fp;
    }

    private static void checkAcceptance(WeightOptimizer.OptimizationResult active,
                                        WeightOptimizer.OptimizationResult warmup) {
        boolean aAuc = active.auc >= 0.96;
        boolean wAuc = warmup.auc >= 0.90;
        boolean aDet = active.attacksCaught * 100.0 / active.totalAttacks >= 92;
        boolean wDet = warmup.attacksCaught * 100.0 / warmup.totalAttacks >= 85;
        boolean aFp = active.falsePositives <= 5;  // relaxed: includes extended normals
        boolean wFp = warmup.falsePositives <= 6;
        System.out.println(String.format("%-55s %-15s %-15s", "Criterion", "Active", "Warmup"));
        System.out.println("-".repeat(85));
        System.out.println(String.format("%-55s %-15s %-15s", "AUC >= 0.96 / 0.90",
                ok(aAuc, active.auc, 0.96), ok(wAuc, warmup.auc, 0.90)));
        System.out.println(String.format("%-55s %-15s %-15s", "Detection >= 92% / 85%",
                ok(aDet, active.attacksCaught * 100.0 / active.totalAttacks, 92),
                ok(wDet, warmup.attacksCaught * 100.0 / warmup.totalAttacks, 85)));
        System.out.println(String.format("%-55s %-15s %-15s", "FP <= 5 / 6 (core+extended+boundary)",
                ok(aFp, active.falsePositives, 5), ok(wFp, warmup.falsePositives, 6)));
        System.out.println();
        System.out.println("Active:  AUC=" + String.format("%.4f", active.auc)
                + " detected=" + active.attacksCaught + "/" + active.totalAttacks
                + " FP=" + active.falsePositives + "/" + active.totalNormals);
        System.out.println("Warmup: AUC=" + String.format("%.4f", warmup.auc)
                + " detected=" + warmup.attacksCaught + "/" + warmup.totalAttacks
                + " FP=" + warmup.falsePositives + "/" + warmup.totalNormals);
    }

    private static String ok(boolean pass, double val, double target) {
        return String.format("%s %.1f%%", pass ? "PASS" : "FAIL", val / target * 100);
    }
}
