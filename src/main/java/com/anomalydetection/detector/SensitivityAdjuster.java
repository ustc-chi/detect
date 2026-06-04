package com.anomalydetection.detector;

/**
 * Utility for mapping int sensitivity [1, 10] to a threshold multiplier.
 *
 * <p>Piecewise linear mapping with three anchor points:
 * <ul>
 *   <li>sensitivity = 1  → multiplier = 2.0 (least sensitive, thresholds doubled)</li>
 *   <li>sensitivity = 5  → multiplier = 0.95 (default / medium sensitivity)</li>
 *   <li>sensitivity = 10 → multiplier = 0.5 (most sensitive, thresholds halved)</li>
 * </ul>
 *
 * <p>The formula is:
 * <pre>
 *   [1, 5]:  multiplier = 2.0 - (s - 1) × 0.2625
 *   [5, 10]: multiplier = 0.95 - (s - 5) × 0.09
 * </pre>
 */
public final class SensitivityAdjuster {

    public static final int MIN_SENSITIVITY = 1;
    public static final int MAX_SENSITIVITY = 10;
    public static final int DEFAULT_SENSITIVITY = 5;

    private SensitivityAdjuster() {
        // utility class
    }

    /**
     * Returns the threshold multiplier for the given int sensitivity.
     *
     * @param sensitivity detection sensitivity in [1, 10], where 10 is most sensitive
     * @return threshold multiplier in [0.5, 2.0]
     * @throws IllegalArgumentException if sensitivity is outside [1, 10]
     */
    public static double getThresholdMultiplier(int sensitivity) {
        if (sensitivity < MIN_SENSITIVITY || sensitivity > MAX_SENSITIVITY) {
            throw new IllegalArgumentException(
                "sensitivity must be in [" + MIN_SENSITIVITY + ", " + MAX_SENSITIVITY + "], got: " + sensitivity);
        }
        if (sensitivity <= 5) {
            // [1, 5]: 2.0 → 0.95
            return 2.0 - (sensitivity - 1) * 0.2625;
        } else {
            // [5, 10]: 0.95 → 0.5
            return 0.95 - (sensitivity - 5) * 0.09;
        }
    }

    /**
     * Returns the default sensitivity value (5) — medium sensitivity.
     */
    public static int getDefaultSensitivity() {
        return DEFAULT_SENSITIVITY;
    }
}
