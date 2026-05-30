package com.anomalydetection.detector;

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

    public static DirectionValidation notReversed() {
        return new DirectionValidation(false, 0.0, 0.0, 0.0);
    }

    public boolean isReversed() { return reversed; }
    public double getRatio() { return ratio; }
    public double getEUp() { return eUp; }
    public double getEDown() { return eDown; }
}
