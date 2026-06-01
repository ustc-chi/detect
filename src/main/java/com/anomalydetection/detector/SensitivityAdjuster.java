package com.anomalydetection.detector;

/**
 * Utility for mapping sensitivity [0.0, 1.0] to a threshold multiplier.
 *
 * <p>Mapping formula: {@code multiplier = 2.0 - sensitivity * 1.5}
 * <ul>
 *   <li>sensitivity = 1.0 (most sensitive) → multiplier = 0.5 (thresholds halved)</li>
 *   <li>sensitivity = 0.7 (default)       → multiplier ≈ 0.95 (preserves current behavior)</li>
 *   <li>sensitivity = 0.0 (least sensitive)→ multiplier = 2.0 (thresholds doubled)</li>
 * </ul>
 */
public final class SensitivityAdjuster {

    private static final double MIN_SENSITIVITY = 0.0;
    private static final double MAX_SENSITIVITY = 1.0;
    private static final double DEFAULT_SENSITIVITY = 0.7;

    private SensitivityAdjuster() {
        // utility class
    }

    /**
     * Returns the threshold multiplier for the given sensitivity value.
     *
     * @param sensitivity detection sensitivity in [0.0, 1.0], where 1.0 is most sensitive
     * @return threshold multiplier in [0.5, 2.0]
     * @throws IllegalArgumentException if sensitivity is outside [0.0, 1.0]
     */
    public static double getThresholdMultiplier(double sensitivity) {
        if (sensitivity < MIN_SENSITIVITY || sensitivity > MAX_SENSITIVITY) {
            throw new IllegalArgumentException(
                "sensitivity must be in [" + MIN_SENSITIVITY + ", " + MAX_SENSITIVITY + "], got: " + sensitivity);
        }
        return 2.0 - sensitivity * 1.5;
    }

    /**
     * Returns the default sensitivity value (0.7) that preserves current detection behavior.
     */
    public static double getDefaultSensitivity() {
        return DEFAULT_SENSITIVITY;
    }
}
