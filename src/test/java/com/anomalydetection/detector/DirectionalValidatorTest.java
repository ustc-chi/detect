package com.anomalydetection.detector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

public class DirectionalValidatorTest {

    private static final double[] DEFAULT_WEIGHTS = {
        2.0, 2.5, 5.0, 3.0, 1.5, 2.0, 2.0, 2.5, 2.5, 3.0, 1.5, 2.0
    };
    private static final double THRESHOLD = 0.75;

    private DirectionalValidator validator;

    @BeforeEach
    void setUp() {
        validator = new DirectionalValidator(DEFAULT_WEIGHTS.clone(), THRESHOLD);
    }

    @Test
    void quietDayReversed() {
        double[] zScores = new double[12];
        for (int i = 0; i < 12; i++) {
            zScores[i] = -2.0;
        }

        DirectionalValidator.ValidationResult result = validator.validate(zScores);

        assertThat(result.reversed).isTrue();
        assertThat(result.eUp).isCloseTo(0.0, within(1e-9));
        assertThat(result.eDown).isGreaterThan(0.0);
        assertThat(result.ratio).isGreaterThan(THRESHOLD);
        assertThat(result.topDeviations).hasSize(5);
        for (DirectionalValidator.FeatureDeviation dev : result.topDeviations) {
            assertThat(dev.direction()).isEqualTo("BELOW");
        }
    }

    @Test
    void ransomwareConfirmed() {
        double[] zScores = new double[12];
        for (int i = 0; i < 12; i++) {
            zScores[i] = 3.0;
        }

        DirectionalValidator.ValidationResult result = validator.validate(zScores);

        assertThat(result.reversed).isFalse();
        assertThat(result.eDown).isCloseTo(0.0, within(1e-9));
        assertThat(result.eUp).isGreaterThan(0.0);
        assertThat(result.ratio).isLessThan(0.25);
        assertThat(result.topDeviations).hasSize(5);
        for (DirectionalValidator.FeatureDeviation dev : result.topDeviations) {
            assertThat(dev.direction()).isEqualTo("ABOVE");
        }
    }

    @Test
    void borderlineStaysAnomalous() {
        // E_up = w[0] * 1^2 = 2.0
        // E_down = w[1] * z^2 = 2.5 * 2.4 = 6.0
        // ratio = 6.0 / (2.0 + 6.0 + 1e-10) = 0.75 exactly
        // strict GT → reversed = false
        double[] zScores = new double[12];
        zScores[0] = 1.0;
        zScores[1] = -Math.sqrt(2.4);

        DirectionalValidator.ValidationResult result = validator.validate(zScores);

        assertThat(result.reversed).isFalse();
        assertThat(result.ratio).isCloseTo(0.75, within(1e-6));
    }

    @Test
    void disabledValidation() {
        DirectionalValidator disabled = new DirectionalValidator(DEFAULT_WEIGHTS.clone(), 0.0);

        double[] zScores = new double[12];
        for (int i = 0; i < 12; i++) {
            zScores[i] = -5.0;
        }

        DirectionalValidator.ValidationResult result = disabled.validate(zScores);

        assertThat(result.reversed).isFalse();
    }

    @Test
    void zeroZScores() {
        double[] zScores = new double[12];

        DirectionalValidator.ValidationResult result = validator.validate(zScores);

        assertThat(result.reversed).isFalse();
        assertThat(result.ratio).isCloseTo(0.0, within(1e-6));
        assertThat(result.eUp).isCloseTo(0.0, within(1e-9));
        assertThat(result.eDown).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void singleDominantFeature() {
        double[] zScores = new double[12];
        zScores[2] = 8.0;
        for (int i = 0; i < 12; i++) {
            if (i != 2) zScores[i] = 0.1;
        }

        DirectionalValidator.ValidationResult result = validator.validate(zScores);

        assertThat(result.reversed).isFalse();
        assertThat(result.ratio).isLessThan(0.5);
        assertThat(result.topDeviations).hasSize(5);
        assertThat(result.topDeviations.get(0).name()).isEqualTo("peak_burst_velocity");
        assertThat(result.topDeviations.get(0).zScore()).isCloseTo(8.0, within(1e-9));
        assertThat(result.topDeviations.get(0).direction()).isEqualTo("ABOVE");
    }
}
