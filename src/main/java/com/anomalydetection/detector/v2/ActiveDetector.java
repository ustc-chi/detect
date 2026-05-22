package com.anomalydetection.detector.v2;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Active-phase detector using weighted Euclidean distance scoring
 * with directional validation for quiet-day reversal.
 * <p>
 * Relies on pre-computed baseline statistics (median, MAD, threshold, weights)
 * provided via {@link BaselineStatsDTO}.
 */
public class ActiveDetector {

    private static final Logger LOG = Logger.getLogger(ActiveDetector.class.getName());

    private static final double Z_CAP = 10.0;
    private static final int TOP_DEVIATIONS_COUNT = 5;
    private static final double DEFAULT_DIRECTION_THRESHOLD = 0.75;

    private final double directionThreshold;

    public ActiveDetector() {
        this.directionThreshold = DEFAULT_DIRECTION_THRESHOLD;
    }

    public ActiveDetector(double directionThreshold) {
        this.directionThreshold = directionThreshold;
    }

    /**
     * Detect anomaly in active phase using pre-computed baseline stats.
     *
     * @param vector         the feature vector to evaluate
     * @param baselineStats  pre-computed baseline statistics (median, MAD, threshold, weights)
     * @param resourceId     resource identifier
     * @return complete detection result
     */
    public DetectionResult detect(FeatureVector14 vector,
                                   BaselineStatsDTO baselineStats,
                                   String resourceId) {
        double[] median = baselineStats.getMedian();
        double[] mad = baselineStats.getMad();
        double[] weights = baselineStats.getWeights();
        double threshold = baselineStats.getThreshold();

        // 1. Compute z-scores and per-dimension contributions
        double[] zScores = new double[FeatureVector14.FEATURE_COUNT];
        double[] contributions = new double[FeatureVector14.FEATURE_COUNT];
        double sumWeightedZ2 = 0;

        for (int i = 0; i < FeatureVector14.FEATURE_COUNT; i++) {
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

        // 3. Top deviations
        List<DimensionReport> topDeviations = computeTopDeviations(dimensions);

        // 4. Directional validation
        DirectionValidation dirValidation = DirectionValidation.notReversed();
        if (isAnomaly && directionThreshold > 0) {
            DirectionalValidatorV2 validator = new DirectionalValidatorV2(weights, directionThreshold);
            DirectionalValidatorV2.ValidationResult vr = validator.validate(zScores);
            if (vr.reversed) {
                isAnomaly = false;
                dirValidation = new DirectionValidation(true, vr.ratio, vr.eUp, vr.eDown);
                LOG.warning(String.format(
                        "Directional validation reversed anomaly: score=%.4f, ratio=%.4f",
                        score, vr.ratio));
            }
        }

        return new DetectionResult(
                resourceId,
                java.time.Instant.now(),
                Phase.ACTIVE,
                score,
                threshold,
                isAnomaly,
                dimensions,
                topDeviations,
                dirValidation,
                null,   // signatureMatch
                null    // warmupInfo
        );
    }

    private List<DimensionReport> buildDimensionReports(
            FeatureVector14 vector, double[] zScores, double[] contributions, double[] weights) {
        List<DimensionReport> reports = new ArrayList<>(FeatureVector14.FEATURE_COUNT);
        for (int i = 0; i < FeatureVector14.FEATURE_COUNT; i++) {
            reports.add(new DimensionReport(
                    i,
                    FeatureVector14.FEATURE_NAMES[i],
                    vector.get(i),
                    zScores[i],
                    contributions[i],
                    weights[i],
                    FeatureVector14.FEATURE_DESCRIPTIONS[i],
                    FeatureVector14.FEATURE_UNITS[i],
                    vector.getSupplementary(i)
            ));
        }
        return reports;
    }

    private List<DimensionReport> computeTopDeviations(List<DimensionReport> dimensions) {
        List<DimensionReport> sorted = new ArrayList<>(dimensions);
        sorted.sort((a, b) -> Double.compare(Math.abs(b.getZScore()), Math.abs(a.getZScore())));
        int limit = Math.min(TOP_DEVIATIONS_COUNT, sorted.size());
        return sorted.subList(0, limit);
    }
}
