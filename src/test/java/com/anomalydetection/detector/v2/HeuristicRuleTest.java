package com.anomalydetection.detector.v2;

import com.anomalydetection.detector.v2.heuristic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeuristicRuleTest {

    @Test
    void testModificationRatioRule_Triggers() {
        double[] v = new double[14];
        v[0] = 100; v[1] = 0.96;
        assertTrue(new ModificationRatioRule().evaluate(new FeatureVector(v)).isTriggered());
    }

    @Test
    void testModificationRatioRule_TooFewOps() {
        double[] v = new double[14];
        v[0] = 30; v[1] = 0.96;
        assertFalse(new ModificationRatioRule().evaluate(new FeatureVector(v)).isTriggered());
    }

    @Test
    void testAllRulesHaveUniqueNames() {
        HeuristicRule[] rules = {
            new ModificationRatioRule(), new BurstModPurityRule(),
            new FileTypeConcentrationRule(), new InterOpTimeCvRule(),
            new HighValueTargetingRule(), new DeletionIntensityRule()
        };
        java.util.Set<String> names = new java.util.HashSet<>();
        for (HeuristicRule r : rules) {
            assertTrue(names.add(r.getRuleName()), "Duplicate rule name: " + r.getRuleName());
        }
    }
}
