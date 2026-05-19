package com.anomalydetection.detector;

import com.anomalydetection.features.RansomwareFeatureVector;

public class WeightedEuclideanScorer {
    private final double[] center;
    private final double[] scale;
    private final double[] weights;

    private static final int N = RansomwareFeatureVector.FEATURE_COUNT;
    private static final double Z_CAP = 10.0;

    public static final double[] DEFAULT_WEIGHTS = {
        2.0,  // 0: modification_ratio
        2.5,  // 1: total_operations_normalized
        5.0,  // 2: peak_burst_velocity
        3.0,  // 3: burst_mod_purity
        1.5,  // 4: high_value_ext_ratio
        2.0,  // 5: inter_op_time_cv_burst
        2.0,  // 6: high_value_file_coverage
        2.5,  // 7: directory_coverage_depth
        2.5,  // 8: temporal_uniformity
        3.0,  // 9: rename_correlation
        1.5,  // 10: wall_clock_anomaly
        2.0   // 11: per_type_entropy
    };

    public WeightedEuclideanScorer(BaselineStatistics stats) {
        this(stats, DEFAULT_WEIGHTS);
    }

    public WeightedEuclideanScorer(BaselineStatistics stats, double[] weights) {
        this.center = stats.getMedian();
        this.scale = stats.getMad();
        if (weights == null || weights.length != N) {
            this.weights = DEFAULT_WEIGHTS;
        } else {
            this.weights = weights;
        }
    }

    public double score(RansomwareFeatureVector vector) {
        double sum = 0.0;
        for (int i = 0; i < N; i++) {
            double z = (vector.get(i) - center[i]) / scale[i];
            z = Math.max(-Z_CAP, Math.min(Z_CAP, z));
            sum += weights[i] * z * z;
        }
        return Math.sqrt(sum);
    }

    public double[] getWeights() { return weights.clone(); }
    public double[] getCenter() { return center.clone(); }
    public double[] getScale() { return scale.clone(); }

    @Deprecated
    public double[] getMean() { return center; }
    @Deprecated
    public double[] getStd() { return scale; }
}
