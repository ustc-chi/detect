package com.anomalydetection.detector.v2;

/**
 * Direction validation result for quiet-day reversal detection.
 * <p>
 * When an anomaly score is primarily driven by negative z-scores (values below baseline),
 * it may indicate a "quiet day" rather than an actual attack. Directional validation
 * separates the energy into upward (eUp) and downward (eDown) components, and
 * if eDown dominates beyond the threshold ratio, the anomaly is reversed to normal.
 */
public final class DirectionValidation {

    private final boolean reversed;
    private final double ratio;
    private final double eUp;
    private final double eDown;

    public DirectionValidation(boolean reversed, double ratio, double eUp, double eDown) {
        this.reversed = reversed;
        this.ratio = ratio;
        this.eUp = eUp;
        this.eDown = eDown;
    }

    /** Default non-reversed instance. */
    public static DirectionValidation notReversed() {
        return new DirectionValidation(false, 0.0, 0.0, 0.0);
    }

    public boolean isReversed() { return reversed; }
    public double getRatio() { return ratio; }
    public double getEUp() { return eUp; }
    public double getEDown() { return eDown; }
}
