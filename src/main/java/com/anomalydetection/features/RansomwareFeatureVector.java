package com.anomalydetection.features;

import java.util.Arrays;

/**
 * Immutable 12-feature ransomware feature vector.
 * Features are exposed as doubles in the following order:
 * 0: modification_ratio
 * 1: total_operations_normalized
 * 2: peak_burst_velocity
 * 3: burst_mod_purity
 * 4: high_value_ext_ratio
 * 5: inter_op_time_cv_burst
 * 6: high_value_file_coverage
 * 7: directory_coverage_depth
 * 8: temporal_uniformity
 * 9: rename_correlation
 * 10: wall_clock_anomaly
 * 11: per_type_entropy
 */
public final class RansomwareFeatureVector {
    public static final String[] FEATURE_NAMES = {
        "modification_ratio",
        "total_operations_normalized",
        "peak_burst_velocity",
        "burst_mod_purity",
        "high_value_ext_ratio",
        "inter_op_time_cv_burst",
        "high_value_file_coverage",
        "directory_coverage_depth",
        "temporal_uniformity",
        "rename_correlation",
        "wall_clock_anomaly",
        "per_type_entropy"
    };
    public static final int FEATURE_COUNT = 12;

    private final double modificationRatio;
    private final double totalOperationsNormalized;
    private final double peakBurstVelocity;
    private final double burstModPurity;
    private final double highValueExtRatio;
    private final double interOpTimeCvBurst;
    private final double highValueFileCoverage;
    private final double directoryCoverageDepth;
    private final double temporalUniformity;
    private final double renameCorrelation;
    private final double wallClockAnomaly;
    private final double perTypeEntropy;

    public RansomwareFeatureVector(
        double modificationRatio,
        double totalOperationsNormalized,
        double peakBurstVelocity,
        double burstModPurity,
        double highValueExtRatio,
        double interOpTimeCvBurst,
        double highValueFileCoverage,
        double directoryCoverageDepth,
        double temporalUniformity,
        double renameCorrelation,
        double wallClockAnomaly,
        double perTypeEntropy
    ) {
        this.modificationRatio = modificationRatio;
        this.totalOperationsNormalized = totalOperationsNormalized;
        this.peakBurstVelocity = peakBurstVelocity;
        this.burstModPurity = burstModPurity;
        this.highValueExtRatio = highValueExtRatio;
        this.interOpTimeCvBurst = interOpTimeCvBurst;
        this.highValueFileCoverage = highValueFileCoverage;
        this.directoryCoverageDepth = directoryCoverageDepth;
        this.temporalUniformity = temporalUniformity;
        this.renameCorrelation = renameCorrelation;
        this.wallClockAnomaly = wallClockAnomaly;
        this.perTypeEntropy = perTypeEntropy;
    }

    public RansomwareFeatureVector(double[] values) {
        if (values == null || values.length != FEATURE_COUNT) {
            throw new IllegalArgumentException("values must be an array of length " + FEATURE_COUNT);
        }
        this.modificationRatio = values[0];
        this.totalOperationsNormalized = values[1];
        this.peakBurstVelocity = values[2];
        this.burstModPurity = values[3];
        this.highValueExtRatio = values[4];
        this.interOpTimeCvBurst = values[5];
        this.highValueFileCoverage = values[6];
        this.directoryCoverageDepth = values[7];
        this.temporalUniformity = values[8];
        this.renameCorrelation = values[9];
        this.wallClockAnomaly = values[10];
        this.perTypeEntropy = values[11];
    }

    public double[] toArray() {
        return new double[] {
            modificationRatio,
            totalOperationsNormalized,
            peakBurstVelocity,
            burstModPurity,
            highValueExtRatio,
            interOpTimeCvBurst,
            highValueFileCoverage,
            directoryCoverageDepth,
            temporalUniformity,
            renameCorrelation,
            wallClockAnomaly,
            perTypeEntropy
        };
    }

    public double get(int index) {
        switch (index) {
            case 0: return modificationRatio;
            case 1: return totalOperationsNormalized;
            case 2: return peakBurstVelocity;
            case 3: return burstModPurity;
            case 4: return highValueExtRatio;
            case 5: return interOpTimeCvBurst;
            case 6: return highValueFileCoverage;
            case 7: return directoryCoverageDepth;
            case 8: return temporalUniformity;
            case 9: return renameCorrelation;
            case 10: return wallClockAnomaly;
            case 11: return perTypeEntropy;
            default: throw new IndexOutOfBoundsException("index=" + index);
        }
    }

    public double getModificationRatio() { return modificationRatio; }
    public double getTotalOperationsNormalized() { return totalOperationsNormalized; }
    public double getPeakBurstVelocity() { return peakBurstVelocity; }
    public double getBurstModPurity() { return burstModPurity; }
    public double getHighValueExtRatio() { return highValueExtRatio; }
    public double getInterOpTimeCvBurst() { return interOpTimeCvBurst; }
    public double getHighValueFileCoverage() { return highValueFileCoverage; }
    public double getDirectoryCoverageDepth() { return directoryCoverageDepth; }
    public double getTemporalUniformity() { return temporalUniformity; }
    public double getRenameCorrelation() { return renameCorrelation; }
    public double getWallClockAnomaly() { return wallClockAnomaly; }
    public double getPerTypeEntropy() { return perTypeEntropy; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("RansomwareFeatureVector{");
        for (int i = 0; i < FEATURE_COUNT; i++) {
            if (i > 0) sb.append(", ");
            sb.append(FEATURE_NAMES[i]).append("=").append(toArray()[i]);
        }
        sb.append("}");
        return sb.toString();
    }
}
