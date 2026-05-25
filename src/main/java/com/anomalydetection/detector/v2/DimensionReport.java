package com.anomalydetection.detector.v2;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class DimensionReport {
    private final int index;
    private final String name;
    private final double value;
    private final double zScore;
    private final double contribution;
    private final double weight;
    private final String description;
    private final String unit;
    private final boolean isAnomalyDimension;
    private final Map<String, Object> supplementary;
    private static final double ANOMALY_Z_THRESHOLD = 2.0;

    public DimensionReport(int index, String name, double value, double zScore,
                           double contribution, double weight, String description,
                           String unit, Map<String, Object> supplementary) {
        this.index = index;
        this.name = Objects.requireNonNull(name);
        this.value = value;
        this.zScore = zScore;
        this.contribution = contribution;
        this.weight = weight;
        this.description = Objects.requireNonNull(description);
        this.unit = Objects.requireNonNull(unit);
        this.isAnomalyDimension = Math.abs(zScore) > ANOMALY_Z_THRESHOLD;
        this.supplementary = (supplementary == null || supplementary.isEmpty())
                ? Collections.emptyMap() : new HashMap<>(supplementary);
    }

    public int getIndex() { return index; }
    public String getName() { return name; }
    public double getValue() { return value; }
    public double getZScore() { return zScore; }
    public double getContribution() { return contribution; }
    public double getWeight() { return weight; }
    public String getDescription() { return description; }
    public String getUnit() { return unit; }
    public boolean isAnomalyDimension() { return isAnomalyDimension; }
    public Map<String, Object> getSupplementary() { return Collections.unmodifiableMap(supplementary); }
}
