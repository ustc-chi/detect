package com.anomalydetection.detector;

import com.anomalydetection.features.FeatureDescription;
import com.anomalydetection.features.FeatureType;
import com.anomalydetection.features.FeatureVector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * Active-phase detector using weighted Euclidean distance scoring
 * with directional validation for quiet-day reversal.
 */
public class ActiveDetector {

    private static final Logger LOG = Logger.getLogger(ActiveDetector.class.getName());
    private static final double Z_CAP = 10.0;
    private static final int TOP_DEVIATIONS_COUNT = 5;
    private static final double DEFAULT_DIRECTION_THRESHOLD = 0.75;

    private final double directionThreshold;

    public ActiveDetector() { this.directionThreshold = DEFAULT_DIRECTION_THRESHOLD; }
    public ActiveDetector(double directionThreshold) { this.directionThreshold = directionThreshold; }

    /**
     * Detect anomaly in active phase.
     *
     * @param vector     the feature vector from feature-extractor
     * @param stats      baseline statistics (median, MAD, threshold)
     * @param resourceId resource identifier
     * @param weights    feature weights (from database, length = FeatureType.COUNT)
     * @return complete detection result
     */
    public DetectionResult detect(FeatureVector vector, BaselineStatsDTO stats,
                                   String resourceId, double[] weights) {
        double[] median = stats.getMedian();
        double[] mad = stats.getMad();
        double threshold = stats.getThreshold();

        if (weights == null || weights.length != FeatureType.COUNT) {
            throw new IllegalArgumentException("weights must have " + FeatureType.COUNT + " elements");
        }

        // 1. Compute z-scores and per-dimension contributions
        double[] values = vector.toArray();
        double[] zScores = new double[FeatureType.COUNT];
        double[] contributions = new double[FeatureType.COUNT];
        double sumWeightedZ2 = 0;

        for (int i = 0; i < FeatureType.COUNT; i++) {
            double z = (values[i] - median[i]) / Math.max(mad[i], 0.001);
            z = Math.max(-Z_CAP, Math.min(Z_CAP, z));
            zScores[i] = z;
            contributions[i] = weights[i] * z * z;
            sumWeightedZ2 += contributions[i];
        }

        double score = Math.sqrt(sumWeightedZ2);
        boolean isAnomaly = score > threshold;

        // 2. Build dimension reports
        List<DimensionReport> dimensions = buildDimensionReports(vector, values, zScores, contributions, weights);

        // 3. Top deviations — sorted by contribution descending
        List<DimensionReport> topDeviations = computeTopDeviations(dimensions);

        // 4. Directional validation
        DirectionalValidator validator = new DirectionalValidator(weights, directionThreshold);
        DirectionalValidator.ValidationResult vr = validator.validate(zScores);
        DirectionValidation dv = new DirectionValidation(vr.reversed, vr.ratio, vr.eUp, vr.eDown);

        if (vr.reversed) {
            LOG.fine("Resource " + resourceId + ": quiet-day reversal detected, flipping to normal");
            isAnomaly = false;
        }

        return DetectionResult.activeResult(resourceId, vector, score, threshold, isAnomaly,
                dimensions, topDeviations, dv);
    }

    // =====================================================================
    // Private helpers
    // =====================================================================

    private List<DimensionReport> buildDimensionReports(FeatureVector vector, double[] values,
                                                         double[] zScores, double[] contributions,
                                                         double[] weights) {
        List<DimensionReport> reports = new ArrayList<>(FeatureType.COUNT);
        for (int i = 0; i < FeatureType.COUNT; i++) {
            FeatureType ft = FeatureType.values()[i];
            String desc = buildFeatureDescription(vector, ft);
            reports.add(new DimensionReport(
                    i, ft.key(), values[i], zScores[i], contributions[i],
                    weights[i], desc, "", Collections.emptyMap()));
        }
        return reports;
    }

    private static String buildFeatureDescription(FeatureVector vector, FeatureType ft) {
        if (vector.getExtendInfo().containsKey(ft.key())) {
            FeatureDescription fd = vector.getDes(ft);
            return fd.cn() + " / " + fd.en();
        }
        return ft.desEN();
    }

    private List<DimensionReport> computeTopDeviations(List<DimensionReport> dimensions) {
        List<DimensionReport> sorted = new ArrayList<>(dimensions);
        sorted.sort((a, b) -> Double.compare(b.getContribution(), a.getContribution()));
        return sorted.subList(0, Math.min(TOP_DEVIATIONS_COUNT, sorted.size()));
    }
}
