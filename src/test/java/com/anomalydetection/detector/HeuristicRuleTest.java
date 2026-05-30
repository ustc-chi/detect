package com.anomalydetection.detector;

import com.anomalydetection.detector.heuristic.*;
import com.anomalydetection.features.FeatureType;
import com.anomalydetection.features.FeatureVector;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeuristicRuleTest {

    private static FeatureVector createVector(double... values) {
        FeatureVector fv = new FeatureVector();
        for (int i = 0; i < values.length && i < FeatureType.COUNT; i++) {
            fv.set(FeatureType.values()[i], values[i]);
        }
        return fv;
    }

    @Test
    void testModificationRatioRule_Triggers() {
        // F0=modification_ratio=0.96, F3=total_operations_normalized=100
        FeatureVector fv = createVector(0.96, 0, 0, 100);
        assertTrue(new ModificationRatioRule().evaluate(fv).isTriggered());
    }

    @Test
    void testModificationRatioRule_TooFewOps() {
        // F0=modification_ratio=0.96, F3=total_operations_normalized=30 (below MIN_OPS=50)
        FeatureVector fv = createVector(0.96, 0, 0, 30);
        assertFalse(new ModificationRatioRule().evaluate(fv).isTriggered());
    }

    @Test
    void testBurstModPurityRule_Triggers() {
        // F4=peak_burst_velocity=200, F5=burst_mod_purity=0.96
        FeatureVector fv = createVector(0, 0, 0, 0, 200, 0.96);
        assertTrue(new BurstModPurityRule().evaluate(fv).isTriggered());
    }

    @Test
    void testHighValueTargetingRule_Triggers() {
        // F3=total_operations_normalized=200, F6=high_value_ext_ratio=0.9
        FeatureVector fv = createVector(0, 0, 0, 200, 0, 0, 0.9);
        assertTrue(new HighValueTargetingRule().evaluate(fv).isTriggered());
    }

    @Test
    void testInterOpTimeCvRule_Triggers() {
        // F3=total_operations_normalized=200, F7=inter_op_time_cv_burst=0.01
        FeatureVector fv = createVector(0, 0, 0, 200, 0, 0, 0, 0.01);
        assertTrue(new InterOpTimeCvRule().evaluate(fv).isTriggered());
    }

    @Test
    void testDeletionIntensityRule_Triggers() {
        // F1=deletion_ratio=0.8
        FeatureVector fv = createVector(0, 0.8);
        assertTrue(new DeletionIntensityRule().evaluate(fv).isTriggered());
    }

    @Test
    void testFileTypeConcentrationRule_Triggers() {
        // F3=total_operations_normalized=200, F13=per_type_entropy=0.1 (low = concentrated)
        FeatureVector fv = createVector(0, 0, 0, 200, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.1);
        assertTrue(new FileTypeConcentrationRule().evaluate(fv).isTriggered());
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
