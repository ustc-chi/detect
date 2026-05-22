package com.anomalydetection.detector.v2;

import com.anomalydetection.detector.v2.heuristic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for each heuristic rule independently.
 */
class HeuristicRuleTest {

    private FeatureVector14 vector(double[] custom) {
        double[] v = new double[14];
        System.arraycopy(custom, 0, v, 0, Math.min(custom.length, 14));
        return new FeatureVector14(v);
    }

    @Test
    void testSuspiciousExtensionRule_TriggersOnPositive() {
        RuleResult r = new SuspiciousExtensionRule().evaluate(vector(new double[]{0,0,0,0,0,0.01}));
        assertTrue(r.isTriggered());
        assertEquals("SUSPICIOUS_EXTENSION", r.getRuleName());
    }

    @Test
    void testSuspiciousExtensionRule_DoesNotTriggerOnZero() {
        RuleResult r = new SuspiciousExtensionRule().evaluate(vector(new double[14]));
        assertFalse(r.isTriggered());
    }

    @Test
    void testModificationRatioRule_Triggers() {
        // index 0 = total_operations, index 1 = modification_ratio
        RuleResult r = new ModificationRatioRule().evaluate(vector(new double[]{100, 0.96}));
        assertTrue(r.isTriggered());
    }

    @Test
    void testModificationRatioRule_TooFewOps() {
        RuleResult r = new ModificationRatioRule().evaluate(vector(new double[]{30, 0.96}));
        assertFalse(r.isTriggered());
    }

    @Test
    void testBurstModPurityRule_Triggers() {
        // index 0 = total_ops, index 6 = peak_burst_velocity, index 10 = burst_mod_purity
        double[] v = new double[14];
        v[0] = 60;
        v[6] = 100;
        v[10] = 0.96;
        assertTrue(new BurstModPurityRule().evaluate(vector(v)).isTriggered());
    }

    @Test
    void testBurstModPurityRule_NotTriggered() {
        double[] v = new double[14];
        v[0] = 10;
        v[6] = 30;
        v[10] = 0.96;
        assertFalse(new BurstModPurityRule().evaluate(vector(v)).isTriggered());
    }

    @Test
    void testFileTypeConcentrationRule_Triggers() {
        double[] v = new double[14];
        v[0] = 150;
        v[11] = 0.95;
        assertTrue(new FileTypeConcentrationRule().evaluate(vector(v)).isTriggered());
    }

    @Test
    void testInterOpTimeCvRule_Triggers() {
        double[] v = new double[14];
        v[0] = 100;
        v[13] = 0.04;
        assertTrue(new InterOpTimeCvRule().evaluate(vector(v)).isTriggered());
    }

    @Test
    void testHighValueTargetingRule_Triggers() {
        double[] v = new double[14];
        v[0] = 150;
        v[9] = 0.85;
        assertTrue(new HighValueTargetingRule().evaluate(vector(v)).isTriggered());
    }

    @Test
    void testDeletionIntensityRule_Triggers() {
        double[] v = new double[14];
        v[2] = 6.0;
        assertTrue(new DeletionIntensityRule().evaluate(vector(v)).isTriggered());
    }

    @Test
    void testAllRulesHaveUniqueNames() {
        HeuristicRule[] rules = {
            new SuspiciousExtensionRule(),
            new ModificationRatioRule(),
            new BurstModPurityRule(),
            new FileTypeConcentrationRule(),
            new InterOpTimeCvRule(),
            new HighValueTargetingRule(),
            new DeletionIntensityRule()
        };
        java.util.Set<String> names = new java.util.HashSet<>();
        for (HeuristicRule r : rules) {
            assertTrue(names.add(r.getRuleName()), "Duplicate rule name: " + r.getRuleName());
        }
    }
}
