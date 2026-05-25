package com.anomalydetection.detector.v2.heuristic;

public final class RuleResult {
    private final boolean triggered;
    private final String ruleName;
    private final double confidence;

    public static final RuleResult NOT_TRIGGERED = new RuleResult(false, "", 0.0);

    public RuleResult(boolean triggered, String ruleName, double confidence) {
        this.triggered = triggered;
        this.ruleName = ruleName;
        this.confidence = confidence;
    }

    public static RuleResult triggered(String ruleName, double confidence) {
        return new RuleResult(true, ruleName, confidence);
    }
    public static RuleResult notTriggered() { return NOT_TRIGGERED; }

    public boolean isTriggered() { return triggered; }
    public String getRuleName() { return ruleName; }
    public double getConfidence() { return confidence; }
}
