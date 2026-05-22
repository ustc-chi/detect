package com.anomalydetection.detector.v2;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Analysis report for a single feature dimension within a detection result.
 * <p>
 * Contains the original value, z-score, contribution to the final score,
 * weight, description, unit, and optional supplementary data.
 */
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

    /**
     * Full constructor for dimension report.
     *
     * @param index         dimension index (0-13)
     * @param name          feature name
     * @param value         original feature value
     * @param zScore        z-score relative to baseline (0 if not computed, e.g., warmup)
     * @param contribution  w_i × z_i² contribution to Euclidean score
     * @param weight        feature weight used in scoring
     * @param description   human-readable feature description
     * @param unit          measurement unit
     * @param supplementary optional extra data per dimension
     */
    public DimensionReport(
            int index,
            String name,
            double value,
            double zScore,
            double contribution,
            double weight,
            String description,
            String unit,
            Map<String, Object> supplementary) {
        this.index = index;
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.value = value;
        this.zScore = zScore;
        this.contribution = contribution;
        this.weight = weight;
        this.description = Objects.requireNonNull(description, "description must not be null");
        this.unit = Objects.requireNonNull(unit, "unit must not be null");
        this.isAnomalyDimension = Math.abs(zScore) > ANOMALY_Z_THRESHOLD;
        if (supplementary == null || supplementary.isEmpty()) {
            this.supplementary = Collections.emptyMap();
        } else {
            this.supplementary = new HashMap<>(supplementary);
        }
    }

    // --- Getters ---

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

    @Override
    public String toString() {
        return String.format("%s[%d]: value=%.4f, z=%.4f, contrib=%.4f, weight=%.4f",
                name, index, value, zScore, contribution, weight);
    }
}
