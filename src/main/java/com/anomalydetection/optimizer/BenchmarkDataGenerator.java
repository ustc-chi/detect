package com.anomalydetection.optimizer;

import com.anomalydetection.features.FeatureType;
import com.anomalydetection.features.FeatureVector;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates synthetic benchmark datasets for weight optimization.
 * <p>
 * Produces 66 normal + 70 attack + 8 boundary = 144 labeled FeatureVector instances,
 * with distributions matching the design document specifications.
 */
public class BenchmarkDataGenerator {

    private final Random rng;

    public BenchmarkDataGenerator(long seed) {
        this.rng = new Random(seed);
    }

    // ========================================================================
    // Public API
    // ========================================================================

    /** Full dataset: 66 normal + 70 attack + 8 boundary = 144 instances. */
    public Dataset generateFullDataset() {
        List<LabeledVector> all = new ArrayList<>();

        // Normals: 66 instances across 8 patterns
        all.addAll(generateNormal(NormalPattern.REGULAR_OFFICE, 20));
        all.addAll(generateNormal(NormalPattern.BATCH_COMPILE, 8));
        all.addAll(generateNormal(NormalPattern.LOG_ROTATION, 8));
        all.addAll(generateNormal(NormalPattern.DATA_MIGRATION, 8));
        all.addAll(generateNormal(NormalPattern.QUIET_DAY, 6));
        all.addAll(generateNormal(NormalPattern.FOCUSED_DIR, 6));
        all.addAll(generateNormal(NormalPattern.DATABASE_ETL, 6));
        all.addAll(generateNormal(NormalPattern.SECURITY_SCAN, 4));

        // Attacks: 70 instances across 14 types
        all.addAll(generateAttack("A1", AttackSpecs.A1_FAST_ENCRYPT));
        all.addAll(generateAttack("A2", AttackSpecs.A2_INTERMITTENT));
        all.addAll(generateAttack("A3", AttackSpecs.A3_HIGH_VALUE));
        all.addAll(generateAttack("A4", AttackSpecs.A4_DESTRUCTIVE));
        all.addAll(generateAttack("A5", AttackSpecs.A5_SLOW_DRIP));
        all.addAll(generateAttack("A6", AttackSpecs.A6_MIXED_MASK));
        all.addAll(generateAttack("A7", AttackSpecs.A7_HOURLY_SPREAD));
        all.addAll(generateAttack("A8", AttackSpecs.A8_ENCRYPT_RENAME));
        all.addAll(generateAttack("A9", AttackSpecs.A9_ENCRYPT_CLEANUP));
        all.addAll(generateAttack("A10", AttackSpecs.A10_TINY_FILE));
        all.addAll(generateAttack("A11", AttackSpecs.A11_TIME_AWARE));
        all.addAll(generateAttack("A12", AttackSpecs.A12_MULTI_STAGE));
        all.addAll(generateAttack("A13", AttackSpecs.A13_INPLACE));
        all.addAll(generateAttack("A14", AttackSpecs.A14_ARCHIVE));

        // Boundary cases: 8 instances
        all.addAll(generateBoundary());

        return new Dataset(all);
    }

    // ========================================================================
    // Normal pattern generation
    // ========================================================================

    public enum NormalPattern {
        REGULAR_OFFICE, BATCH_COMPILE, LOG_ROTATION, DATA_MIGRATION,
        QUIET_DAY, FOCUSED_DIR, DATABASE_ETL, SECURITY_SCAN
    }

    /** Generates {@code count} normal vectors from the given pattern. */
    public List<LabeledVector> generateNormal(NormalPattern pattern, int count) {
        double[] baseMedian = Normals.getMedian(pattern);
        double[] baseMad = Normals.getMad(pattern);
        List<LabeledVector> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            FeatureVector fv = new FeatureVector();
            for (int d = 0; d < FeatureType.COUNT; d++) {
                fv.set(FeatureType.values()[d], sampleTruncatedNormal(baseMedian[d], baseMad[d]));
            }
            result.add(new LabeledVector(fv, false, pattern.name(), String.format("%s_%02d", pattern.name(), i + 1)));
        }
        return result;
    }

    // ========================================================================
    // Attack pattern generation
    // ========================================================================

    /** Generates attack vectors from a spec (list of double[16] feature values). */
    public List<LabeledVector> generateAttack(String prefix, double[][] spec) {
        List<LabeledVector> result = new ArrayList<>(spec.length);
        for (int i = 0; i < spec.length; i++) {
            FeatureVector fv = new FeatureVector();
            for (int d = 0; d < FeatureType.COUNT && d < spec[i].length; d++) {
                fv.set(FeatureType.values()[d], spec[i][d]);
            }
            result.add(new LabeledVector(fv, true, prefix, String.format("%s-%d", prefix, i + 1)));
        }
        return result;
    }

    // ========================================================================
    // Boundary cases
    // ========================================================================

    private List<LabeledVector> generateBoundary() {
        double[] n1Med = Normals.getMedian(NormalPattern.REGULAR_OFFICE);
        List<LabeledVector> list = new ArrayList<>();

        // B1: F0 at boundary
        list.add(boundaryCase("B1", n1Med, new int[]{0}, new double[]{0.85}));
        // B2: F4 at boundary
        list.add(boundaryCase("B2", n1Med, new int[]{4}, new double[]{10000}));
        // B3: F0+F4 both high
        list.add(boundaryCase("B3", n1Med, new int[]{0, 4}, new double[]{0.75, 8000}));
        // B4: all zeros
        list.add(boundaryCase("B4", new double[FeatureType.COUNT], new int[]{}, new double[]{}));
        // B5: compile+hv
        list.add(boundaryCase("B5", n1Med, new int[]{0, 2, 3, 6}, new double[]{0.40, 0.50, 50000, 0.55}));
        // B6: only F15 high
        list.add(boundaryCase("B6", n1Med, new int[]{15}, new double[]{0.70}));
        // B7: only F10 high
        list.add(boundaryCase("B7", n1Med, new int[]{10}, new double[]{0.20}));
        // B8: A1-1 with F3 lowered
        double[] a1 = AttackSpecs.A1_FAST_ENCRYPT[0].clone();
        a1[3] = 20000;
        list.add(boundaryCase("B8", a1, new int[]{}, new double[]{}));

        return list;
    }

    private LabeledVector boundaryCase(String id, double[] base, int[] overrideIdx, double[] overrideVal) {
        FeatureVector fv = new FeatureVector();
        for (int d = 0; d < FeatureType.COUNT; d++) {
            fv.set(FeatureType.values()[d], base[d]);
        }
        for (int j = 0; j < overrideIdx.length; j++) {
            fv.set(FeatureType.values()[overrideIdx[j]], overrideVal[j]);
        }
        return new LabeledVector(fv, false, "BOUNDARY", id);
    }

    // ========================================================================
    // Sampling helpers
    // ========================================================================

    private double sampleTruncatedNormal(double mean, double mad) {
        double std = mad * 1.4826;  // convert MAD to approximate std
        double val = mean + rng.nextGaussian() * std;
        // Truncate to [mean - 3*mad, mean + 3*mad] to avoid outliers
        double lo = mean - 3 * mad;
        double hi = mean + 3 * mad;
        return Math.max(lo, Math.min(hi, val));
    }

    // ========================================================================
    // Normal distribution definitions (from design doc N1~N8)
    // ========================================================================

    private static class Normals {
        static double[] getMedian(NormalPattern p) {
            double[] base = N1_MEDIAN.clone();
            double[] offset = OFFSET_MEDIAN.getOrDefault(p, new double[FeatureType.COUNT]);
            for (int i = 0; i < FeatureType.COUNT; i++) base[i] += offset[i];
            return base;
        }
        static double[] getMad(NormalPattern p) {
            double[] base = N1_MAD.clone();
            double[] offset = OFFSET_MAD.getOrDefault(p, new double[FeatureType.COUNT]);
            for (int i = 0; i < FeatureType.COUNT; i++) base[i] = Math.max(base[i] + offset[i], 0.001);
            return base;
        }

        static final double[] N1_MEDIAN = {
            0.50, 0.15, 0.25, 12000, 3000, 0.60, 0.28, 1.20,
            40, 0.50, 0.03, 0.20, 3.50, 1.20, 1.50, 0.10
        };
        static final double[] N1_MAD = {
            0.10, 0.05, 0.08, 5000, 2000, 0.12, 0.06, 0.25,
            15, 0.12, 0.02, 0.06, 0.50, 0.15, 0.60, 0.08
        };

        static final Map<NormalPattern, double[]> OFFSET_MEDIAN = new HashMap<>();
        static final Map<NormalPattern, double[]> OFFSET_MAD = new HashMap<>();
        static {
            // N2: Batch compile
            OFFSET_MEDIAN.put(NormalPattern.BATCH_COMPILE, new double[]{
                -0.20, 0.075, 0.20, 25000, 1000, -0.05, 0.0, -0.10,
                10, 0.075, 0.0, -0.02, -0.30, -0.15, 1.0, 0.05
            });
            OFFSET_MAD.put(NormalPattern.BATCH_COMPILE, new double[]{
                0.05, 0.02, 0.05, 8000, 1500, 0.05, 0.0, 0.10,
                10, 0.05, 0.01, 0.03, 0.15, 0.10, 0.5, 0.05
            });
            // N3: Log rotation
            OFFSET_MEDIAN.put(NormalPattern.LOG_ROTATION, new double[]{
                -0.10, 0.075, 0.10, 3000, -500, -0.05, 0.0, -0.10,
                -5, 0.10, 0.07, 0.0, -0.20, 0.0, 0.0, 0.0
            });
            OFFSET_MAD.put(NormalPattern.LOG_ROTATION, new double[]{
                0.05, 0.02, 0.03, 3000, 1000, 0.05, 0.0, 0.10,
                8, 0.08, 0.03, 0.03, 0.20, 0.05, 0.0, 0.0
            });
            // N4: Data migration
            OFFSET_MEDIAN.put(NormalPattern.DATA_MIGRATION, new double[]{
                -0.20, 0.15, 0.15, 35000, 10000, -0.10, 0.0, -0.30,
                60, 0.20, 0.0, -0.05, -0.50, 0.0, 0.5, 0.0
            });
            OFFSET_MAD.put(NormalPattern.DATA_MIGRATION, new double[]{
                0.05, 0.04, 0.04, 15000, 5000, 0.05, 0.0, 0.10,
                30, 0.08, 0.01, 0.02, 0.20, 0.05, 0.5, 0.02
            });
            // N5: Quiet day
            OFFSET_MEDIAN.put(NormalPattern.QUIET_DAY, new double[]{
                0.0, 0.0, 0.0, -10000, -2500, 0.0, 0.0, 0.0,
                -30, 0.0, 0.0, 0.10, 0.30, 0.0, 0.0, 0.0
            });
            OFFSET_MAD.put(NormalPattern.QUIET_DAY, new double[]{
                0.05, 0.03, 0.04, 2000, 1000, 0.06, 0.03, 0.10,
                8, 0.06, 0.01, 0.04, 0.30, 0.08, 0.3, 0.04
            });
            // N6: Focused single directory
            OFFSET_MEDIAN.put(NormalPattern.FOCUSED_DIR, new double[]{
                0.10, 0.0, -0.05, -5000, -1000, 0.0, 0.0, -0.20,
                -35, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0
            });
            OFFSET_MAD.put(NormalPattern.FOCUSED_DIR, new double[]{
                0.05, 0.02, 0.03, 3000, 1000, 0.05, 0.0, 0.10,
                5, 0.06, 0.01, 0.03, 0.15, 0.05, 0.3, 0.03
            });
            // N7: Database ETL
            OFFSET_MEDIAN.put(NormalPattern.DATABASE_ETL, new double[]{
                -0.25, 0.20, 0.20, 40000, 5000, -0.05, 0.0, -0.20,
                20, 0.25, 0.0, -0.05, -0.40, -0.10, 1.5, -0.05
            });
            OFFSET_MAD.put(NormalPattern.DATABASE_ETL, new double[]{
                0.05, 0.04, 0.04, 15000, 3000, 0.05, 0.0, 0.10,
                15, 0.08, 0.01, 0.03, 0.20, 0.08, 0.8, 0.03
            });
            // N8: Security scan
            OFFSET_MEDIAN.put(NormalPattern.SECURITY_SCAN, new double[]{
                0.0, 0.05, 0.05, 8000, 8000, -0.05, 0.0, -0.30,
                -10, 0.15, 0.03, -0.02, -0.30, 0.0, 0.0, 0.0
            });
            OFFSET_MAD.put(NormalPattern.SECURITY_SCAN, new double[]{
                0.05, 0.03, 0.03, 5000, 4000, 0.05, 0.0, 0.10,
                10, 0.08, 0.02, 0.03, 0.20, 0.05, 0.3, 0.02
            });
        }
    }

    // ========================================================================
    // Attack specifications (exact 16-dim values from design doc)
    // ========================================================================

    static class AttackSpecs {
        // All specs: double[count][16], trailing entries default to 0
        static double[][] fill(double[][] specs) {
            for (double[] s : specs) {
                for (int i = 0; i < s.length; i++) {
                    // fill remaining with 0 (F3 in some specs etc.)
                }
            }
            return specs;
        }

        static final double[][] A1_FAST_ENCRYPT = {
            {0.95, 0.02, 0.03, 80000, 50000, 0.98, 0.28, 0.40, 5, 0.85, 0.0, 0.05, 4.50, 0.30, 6.0, 0.90},
            {0.92, 0.03, 0.05, 120000, 80000, 0.96, 0.30, 0.45, 8, 0.82, 0.0, 0.06, 4.40, 0.40, 4.5, 0.85},
            {0.88, 0.05, 0.07, 60000, 35000, 0.94, 0.28, 0.50, 6, 0.80, 0.0, 0.07, 4.30, 0.55, 5.0, 0.80},
            {0.96, 0.01, 0.03, 150000, 120000, 0.99, 0.30, 0.35, 4, 0.88, 0.0, 0.04, 4.60, 0.25, 8.0, 0.95},
            {0.90, 0.04, 0.06, 45000, 25000, 0.95, 0.28, 0.48, 7, 0.78, 0.0, 0.06, 4.35, 0.50, 3.5, 0.75},
        };

        static final double[][] A2_INTERMITTENT = {
            {0.85, 0.03, 0.12, 15000, 18000, 0.95, 0.28, 0.50, 15, 0.75, 0.0, 0.10, 3.80, 0.60, 4.0, 0.85},
            {0.82, 0.04, 0.14, 10000, 12000, 0.93, 0.28, 0.55, 12, 0.72, 0.0, 0.10, 3.90, 0.65, 3.5, 0.80},
            {0.88, 0.02, 0.10, 20000, 25000, 0.96, 0.28, 0.45, 18, 0.78, 0.0, 0.08, 3.70, 0.55, 4.5, 0.88},
            {0.80, 0.05, 0.15, 8000, 9000, 0.90, 0.28, 0.60, 10, 0.70, 0.0, 0.12, 4.00, 0.70, 3.0, 0.78},
            {0.86, 0.03, 0.11, 25000, 22000, 0.94, 0.28, 0.48, 20, 0.76, 0.0, 0.09, 3.85, 0.58, 5.0, 0.82},
        };

        static final double[][] A3_HIGH_VALUE = {
            {0.90, 0.05, 0.05, 5000, 8000, 0.80, 0.75, 1.10, 25, 0.50, 0.0, 0.25, 2.80, 1.00, 1.5, 0.0},
            {0.88, 0.05, 0.07, 8000, 12000, 0.82, 0.85, 1.00, 30, 0.48, 0.0, 0.22, 2.50, 0.95, 1.8, 0.0},
            {0.85, 0.08, 0.07, 3500, 6000, 0.78, 0.65, 1.20, 20, 0.52, 0.0, 0.28, 3.00, 1.05, 1.2, 0.0},
            {0.92, 0.03, 0.05, 10000, 15000, 0.85, 0.80, 0.95, 35, 0.45, 0.0, 0.20, 2.60, 0.90, 2.0, 0.0},
            {0.87, 0.06, 0.07, 6000, 10000, 0.80, 0.70, 1.15, 28, 0.50, 0.0, 0.24, 2.90, 1.00, 1.5, 0.0},
        };

        static final double[][] A4_DESTRUCTIVE = {
            {0.10, 0.55, 0.35, 80000, 5000, 0.30, 0.15, 0.90, 60, 0.30, 0.0, 0.12, 3.50, 0.95, 2.0, 0.60},
            {0.10, 0.65, 0.25, 60000, 4000, 0.25, 0.15, 0.85, 50, 0.35, 0.0, 0.15, 3.30, 0.85, 1.8, 0.50},
            {0.15, 0.45, 0.40, 50000, 6000, 0.35, 0.18, 0.95, 55, 0.32, 0.0, 0.13, 3.40, 1.05, 2.2, 0.55},
            {0.10, 0.70, 0.20, 100000, 8000, 0.20, 0.12, 0.80, 70, 0.38, 0.0, 0.18, 3.10, 0.75, 1.5, 0.45},
            {0.20, 0.50, 0.30, 35000, 3000, 0.35, 0.20, 1.00, 45, 0.28, 0.0, 0.10, 3.60, 1.00, 2.5, 0.50},
        };

        static final double[][] A5_SLOW_DRIP = {
            {0.88, 0.03, 0.09, 8000, 5000, 0.88, 0.28, 2.00, 20, 0.80, 0.0, 0.08, 4.30, 0.60, 2.0, 0.70},
            {0.85, 0.05, 0.10, 12000, 7000, 0.85, 0.28, 1.80, 25, 0.78, 0.0, 0.09, 4.20, 0.65, 2.2, 0.65},
            {0.90, 0.02, 0.08, 5000, 3500, 0.90, 0.28, 2.20, 15, 0.82, 0.0, 0.07, 4.40, 0.55, 1.8, 0.75},
            {0.82, 0.06, 0.12, 15000, 9000, 0.82, 0.28, 1.70, 30, 0.75, 0.0, 0.10, 4.10, 0.70, 2.5, 0.60},
            {0.92, 0.01, 0.07, 6000, 4000, 0.92, 0.28, 2.50, 18, 0.85, 0.0, 0.06, 4.50, 0.50, 1.5, 0.80},
        };

        static final double[][] A6_MIXED_MASK = {
            {0.68, 0.18, 0.14, 14000, 5000, 0.90, 0.28, 1.00, 35, 0.48, 0.0, 0.14, 3.40, 1.00, 5.5, 0.55},
            {0.72, 0.14, 0.14, 16000, 6000, 0.92, 0.30, 0.95, 38, 0.46, 0.0, 0.13, 3.35, 0.95, 6.0, 0.60},
            {0.65, 0.20, 0.15, 12000, 4000, 0.88, 0.28, 1.10, 30, 0.50, 0.0, 0.15, 3.50, 1.05, 5.0, 0.50},
            {0.75, 0.10, 0.15, 20000, 7000, 0.93, 0.32, 0.85, 42, 0.44, 0.0, 0.11, 3.25, 0.85, 7.0, 0.65},
            {0.66, 0.19, 0.15, 13000, 4500, 0.88, 0.28, 1.05, 32, 0.49, 0.0, 0.14, 3.45, 1.02, 5.2, 0.52},
        };

        static final double[][] A7_HOURLY_SPREAD = {
            {0.85, 0.03, 0.12, 15000, 12000, 0.90, 0.28, 0.40, 20, 0.85, 0.0, 0.05, 4.50, 0.55, 2.0, 0.70},
            {0.82, 0.04, 0.14, 12000, 10000, 0.88, 0.28, 0.45, 18, 0.82, 0.0, 0.06, 4.40, 0.60, 1.8, 0.65},
            {0.88, 0.02, 0.10, 18000, 15000, 0.92, 0.28, 0.35, 22, 0.88, 0.0, 0.04, 4.60, 0.50, 2.2, 0.75},
            {0.80, 0.05, 0.15, 10000, 8000, 0.85, 0.28, 0.50, 16, 0.80, 0.0, 0.07, 4.30, 0.65, 1.5, 0.60},
            {0.86, 0.03, 0.11, 16000, 13000, 0.91, 0.28, 0.38, 20, 0.86, 0.0, 0.05, 4.55, 0.52, 2.0, 0.72},
        };

        static final double[][] A8_ENCRYPT_RENAME = {
            {0.48, 0.05, 0.47, 18000, 7000, 0.62, 0.20, 1.05, 28, 0.54, 0.22, 0.14, 3.20, 0.78, 5.5, 0.88},
            {0.52, 0.04, 0.44, 20000, 8000, 0.64, 0.22, 0.95, 30, 0.50, 0.18, 0.15, 3.25, 0.82, 4.5, 0.84},
            {0.44, 0.05, 0.51, 14000, 5500, 0.60, 0.18, 1.15, 24, 0.56, 0.28, 0.13, 3.10, 0.72, 6.5, 0.92},
            {0.50, 0.04, 0.46, 22000, 9000, 0.63, 0.20, 1.00, 32, 0.52, 0.20, 0.14, 3.20, 0.80, 5.0, 0.86},
            {0.42, 0.05, 0.53, 12000, 4800, 0.58, 0.16, 1.25, 22, 0.58, 0.32, 0.12, 3.05, 0.68, 7.5, 0.94},
        };

        static final double[][] A9_ENCRYPT_CLEANUP = {
            {0.10, 0.40, 0.50, 80000, 15000, 0.50, 0.20, 0.90, 40, 0.60, 0.05, 0.12, 3.40, 1.05, 5.0, 0.85},
            {0.10, 0.35, 0.55, 60000, 12000, 0.48, 0.22, 0.95, 35, 0.58, 0.05, 0.13, 3.30, 0.95, 4.5, 0.80},
            {0.25, 0.30, 0.45, 50000, 10000, 0.55, 0.25, 1.00, 45, 0.55, 0.04, 0.14, 3.50, 1.10, 3.5, 0.75},
            {0.15, 0.50, 0.35, 100000, 20000, 0.45, 0.18, 0.85, 50, 0.62, 0.06, 0.10, 3.20, 0.90, 6.0, 0.88},
            {0.15, 0.25, 0.60, 35000, 8000, 0.55, 0.20, 1.05, 30, 0.58, 0.05, 0.15, 3.45, 0.85, 5.5, 0.90},
        };

        static final double[][] A10_TINY_FILE = {
            {0.90, 0.03, 0.07, 60000, 45000, 0.95, 0.28, 0.50, 10, 0.80, 0.0, 0.06, 4.30, 0.40, 5.0, 0.85},
            {0.88, 0.04, 0.08, 40000, 30000, 0.93, 0.28, 0.55, 8, 0.78, 0.0, 0.07, 4.20, 0.45, 4.5, 0.80},
            {0.93, 0.02, 0.05, 80000, 60000, 0.97, 0.28, 0.45, 12, 0.82, 0.0, 0.05, 4.40, 0.35, 5.5, 0.90},
            {0.85, 0.05, 0.10, 35000, 25000, 0.91, 0.28, 0.60, 7, 0.75, 0.0, 0.08, 4.10, 0.50, 4.0, 0.78},
            {0.95, 0.01, 0.04, 100000, 80000, 0.98, 0.28, 0.40, 15, 0.85, 0.0, 0.04, 4.50, 0.30, 6.0, 0.92},
        };

        static final double[][] A11_TIME_AWARE = {
            {0.85, 0.05, 0.10, 15000, 12000, 0.92, 0.28, 1.20, 35, 0.55, 0.0, 0.20, 3.00, 0.60, 1.5, 0.50},
            {0.88, 0.04, 0.08, 20000, 15000, 0.94, 0.28, 1.10, 40, 0.50, 0.0, 0.25, 2.80, 0.55, 1.8, 0.55},
            {0.82, 0.06, 0.12, 10000, 8000, 0.90, 0.28, 1.30, 30, 0.60, 0.0, 0.15, 3.20, 0.65, 1.2, 0.45},
            {0.90, 0.03, 0.07, 25000, 20000, 0.95, 0.28, 1.00, 45, 0.52, 0.0, 0.22, 2.90, 0.50, 2.0, 0.60},
            {0.83, 0.05, 0.12, 12000, 10000, 0.91, 0.28, 1.25, 32, 0.58, 0.0, 0.18, 3.10, 0.62, 1.4, 0.48},
        };

        static final double[][] A12_MULTI_STAGE = {
            // A12-1: recon
            {0.35, 0.10, 0.55, 5000, 2000, 0.50, 0.28, 1.20, 30, 0.45, 0.0, 0.20, 3.50, 1.20, 1.5, 0.00},
            // A12-2: infiltrate
            {0.50, 0.05, 0.45, 8000, 3000, 0.60, 0.28, 1.15, 25, 0.48, 0.0, 0.18, 3.40, 1.15, 1.2, 0.20},
            // A12-3: encrypt
            {0.90, 0.03, 0.07, 60000, 40000, 0.96, 0.28, 0.80, 15, 0.75, 0.0, 0.08, 4.20, 0.45, 5.0, 0.88},
            // A12-4: cleanup
            {0.15, 0.60, 0.25, 30000, 5000, 0.30, 0.20, 1.00, 40, 0.40, 0.0, 0.15, 3.30, 1.00, 2.0, 0.10},
        };

        static final double[][] A13_INPLACE = {
            {0.85, 0.08, 0.07, 15000, 6000, 0.92, 0.30, 1.10, 38, 0.48, 0.0, 0.20, 3.50, 0.50, 1.50, 0.00},
            {0.88, 0.06, 0.06, 20000, 9000, 0.94, 0.32, 1.30, 42, 0.45, 0.0, 0.22, 3.40, 0.42, 1.80, 0.00},
            {0.82, 0.10, 0.08, 10000, 4500, 0.90, 0.28, 1.00, 32, 0.50, 0.0, 0.18, 3.60, 0.58, 1.20, 0.00},
            {0.90, 0.05, 0.05, 28000, 14000, 0.96, 0.35, 1.60, 48, 0.42, 0.0, 0.25, 3.20, 0.38, 2.00, 0.00},
            {0.83, 0.09, 0.08, 12000, 5000, 0.91, 0.28, 1.10, 34, 0.49, 0.0, 0.20, 3.50, 0.55, 1.30, 0.00},
        };

        static final double[][] A14_ARCHIVE = {
            {0.88, 0.04, 0.08, 8000, 5000, 0.93, 0.65, 1.00, 10, 0.55, 0.0, 0.20, 3.20, 0.70, 2.0, 0.0},
            {0.90, 0.03, 0.07, 12000, 7000, 0.94, 0.75, 1.00, 15, 0.52, 0.0, 0.22, 3.00, 0.65, 2.5, 0.0},
            {0.85, 0.05, 0.10, 5000, 3500, 0.91, 0.55, 1.20, 8, 0.58, 0.0, 0.18, 3.40, 0.75, 1.8, 0.0},
            {0.92, 0.02, 0.06, 15000, 9000, 0.95, 0.80, 0.80, 18, 0.50, 0.0, 0.25, 2.90, 0.60, 3.0, 0.0},
            {0.86, 0.04, 0.10, 6000, 4000, 0.92, 0.60, 1.10, 12, 0.55, 0.0, 0.20, 3.30, 0.72, 2.2, 0.0},
        };
    }

    // ========================================================================
    // Data classes
    // ========================================================================

    /** A FeatureVector with ground-truth label and metadata. */
    public static class LabeledVector {
        public final FeatureVector vector;
        public final boolean isAttack;
        public final String group;   // e.g. "A1", "N1", "BOUNDARY"
        public final String id;      // e.g. "A1-1", "N1_REGULAR_OFFICE_01"

        public LabeledVector(FeatureVector vector, boolean isAttack, String group, String id) {
            this.vector = vector;
            this.isAttack = isAttack;
            this.group = group;
            this.id = id;
        }
    }

    /** Complete dataset with normals, attacks, and boundaries. */
    public static class Dataset {
        public final List<LabeledVector> all;
        public final List<LabeledVector> normals;
        public final List<LabeledVector> attacks;
        public final List<LabeledVector> boundaries;
        /** Core normals: N1 (office) + N2 (compile) + N3 (logrotate) + N5 (quiet) + N6 (focused) = stable baseline */
        public final List<LabeledVector> coreNormals;
        /** Extended normals: N4 (migration) + N7 (etl) + N8 (scan) = high-variance patterns kept separate */
        public final List<LabeledVector> extendedNormals;

        Dataset(List<LabeledVector> all) {
            this.all = Collections.unmodifiableList(all);
            this.normals = all.stream().filter(v -> !v.isAttack).filter(v -> !v.group.equals("BOUNDARY"))
                    .collect(Collectors.toUnmodifiableList());
            this.attacks = all.stream().filter(v -> v.isAttack)
                    .collect(Collectors.toUnmodifiableList());
            this.boundaries = all.stream().filter(v -> v.group.equals("BOUNDARY"))
                    .collect(Collectors.toUnmodifiableList());
            Set<String> core = Set.of("REGULAR_OFFICE", "BATCH_COMPILE", "LOG_ROTATION", "QUIET_DAY", "FOCUSED_DIR");
            this.coreNormals = normals.stream().filter(v -> core.contains(v.group))
                    .collect(Collectors.toUnmodifiableList());
            Set<String> ext = Set.of("DATA_MIGRATION", "DATABASE_ETL", "SECURITY_SCAN");
            this.extendedNormals = normals.stream().filter(v -> ext.contains(v.group))
                    .collect(Collectors.toUnmodifiableList());
        }
    }
}
