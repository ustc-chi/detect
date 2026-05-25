package com.anomalydetection.detector.v2;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Active-phase detector using weighted Euclidean distance scoring
 * with directional validation for quiet-day reversal.
 * <p>
 * Weights are passed as a separate parameter (queried from database by the service layer).
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
     * @param vector        the feature vector to evaluate
     * @param stats         baseline statistics (median, MAD, threshold) — NO weights
     * @param resourceId    resource identifier
     * @param weights       feature weights (from database, not from BaselineStatsDTO)
     * @return complete detection result
     */
    public DetectionResult detect(FeatureVector vector, BaselineStatsDTO stats,
                                   String resourceId, double[] weights) {
        double[] median = stats.getMedian();
        double[] mad = stats.getMad();
        double threshold = stats.getThreshold();

        if (weights == null || weights.length != FeatureVector.FEATURE_COUNT) {
            throw new IllegalArgumentException("weights must have " + FeatureVector.FEATURE_COUNT + " elements");
        }

        // 1. Compute z-scores and per-dimension contributions
        double[] zScores = new double[FeatureVector.FEATURE_COUNT];
        double[] contributions = new double[FeatureVector.FEATURE_COUNT];
        double sumWeightedZ2 = 0;

        for (int i = 0; i < FeatureVector.FEATURE_COUNT; i++) {
            double z = (vector.get(i) - median[i]) / Math.max(mad[i], 0.001);
            z = Math.max(-Z_CAP, Math.min(Z_CAP, z));
            zScores[i] = z;
            contributions[i] = weights[i] * z * z;
            sumWeightedZ2 += contributions[i];
        }

        double score = Math.sqrt(sumWeightedZ2);
        boolean isAnomaly = score > threshold;

        // 2. Build dimension reports
        List<DimensionReport> dimensions = buildDimensionReports(vector, zScores, contributions, weights);

        // 3. Top deviations — sorted by |contribution| (not |zScore|)
        List<DimensionReport> topDeviations = computeTopDeviations(dimensions);

        // 4. Directional validation
        DirectionValidation dirValidation = DirectionValidation.notReversed();
        if (isAnomaly && directionThreshold > 0) {
            DirectionalValidatorV2 validator = new DirectionalValidatorV2(weights, directionThreshold);
            DirectionalValidatorV2.ValidationResult vr = validator.validate(zScores);
            if (vr.reversed) {
                isAnomaly = false;
                dirValidation = new DirectionValidation(true, vr.ratio, vr.eUp, vr.eDown);
                LOG.warning("Directional validation reversed anomaly: score=" + String.format("%.4f", score));
            }
        }

        return new DetectionResult(resourceId, java.time.Instant.now(), Phase.ACTIVE,
                score, threshold, isAnomaly, dimensions, topDeviations,
                dirValidation, null, null);
    }

    private List<DimensionReport> buildDimensionReports(FeatureVector vector, double[] zScores,
                                                         double[] contributions, double[] weights) {
        List<DimensionReport> reports = new ArrayList<>(FeatureVector.FEATURE_COUNT);
        for (int i = 0; i < FeatureVector.FEATURE_COUNT; i++) {
            reports.add(new DimensionReport(i, FeatureVector.FEATURE_NAMES[i], vector.get(i),
                    zScores[i], contributions[i], weights[i],
                    FeatureVector.FEATURE_DESCRIPTIONS[i], FeatureVector.FEATURE_UNITS[i],
                    vector.getSupplementary(i)));
        }
        return reports;
    }

    /**
     * Returns top-K dimensions sorted by |contribution| descending.
     * contribution = w_i × z_i² represents the actual impact on the final score.
     */
    private List<DimensionReport> computeTopDeviations(List<DimensionReport> dimensions) {
        List<DimensionReport> sorted = new ArrayList<>(dimensions);
        sorted.sort((a, b) -> Double.compare(Math.abs(b.getContribution()), Math.abs(a.getContribution())));
        int limit = Math.min(TOP_DEVIATIONS_COUNT, sorted.size());
        return sorted.subList(0, limit);
    }
}
