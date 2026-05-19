package com.anomalydetection.detector;

import com.anomalydetection.features.RansomwareFeatureVector;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BaselineStatistics {
    private final double[] median;
    private final double[] mad;
    private final Map<Integer, List<Double>> hourlyOps;
    private Map<Integer, double[]> hourlyStats;

    private static final int FEATURE_COUNT = RansomwareFeatureVector.FEATURE_COUNT;
    private static final double EPSILON = 0.001;
    private static final double MAD_SCALE = 1.4826;

    public BaselineStatistics(List<RansomwareFeatureVector> vectors) {
        if (vectors != null) {
            for (RansomwareFeatureVector v : vectors) {
                if (v == null) continue;
                try {
                    v.get(FEATURE_COUNT - 1);
                } catch (IndexOutOfBoundsException e) {
                    throw new IllegalArgumentException(
                        "expected " + FEATURE_COUNT + " dimensions, got vector with different dimension");
                }
            }
        }

        median = new double[FEATURE_COUNT];
        mad = new double[FEATURE_COUNT];
        hourlyOps = new HashMap<>();
        hourlyStats = new HashMap<>();

        int n = vectors == null ? 0 : vectors.size();
        if (n == 0) {
            for (int i = 0; i < FEATURE_COUNT; i++) {
                median[i] = 0.0;
                mad[i] = Math.sqrt(EPSILON);
            }
            return;
        }

        for (int i = 0; i < FEATURE_COUNT; i++) {
            List<Double> vals = new ArrayList<>(n);
            for (RansomwareFeatureVector v : vectors) {
                vals.add(v.get(i));
            }
            Collections.sort(vals);
            median[i] = median(vals);

            List<Double> absDevs = new ArrayList<>(n);
            for (double v : vals) {
                absDevs.add(Math.abs(v - median[i]));
            }
            Collections.sort(absDevs);
            double rawMad = median(absDevs);
            mad[i] = rawMad * MAD_SCALE;
            if (mad[i] < EPSILON) {
                mad[i] = Math.sqrt(EPSILON);
            }
        }
    }

    public void addHourlyObservation(int hour, double opsCount) {
        hourlyOps.computeIfAbsent(hour, k -> new ArrayList<>()).add(opsCount);
    }

    public void computeHourlyStats() {
        hourlyStats = new HashMap<>();
        for (Map.Entry<Integer, List<Double>> entry : hourlyOps.entrySet()) {
            int hour = entry.getKey();
            List<Double> vals = new ArrayList<>(entry.getValue());
            if (vals.isEmpty()) continue;
            Collections.sort(vals);
            double med = median(vals);
            List<Double> absDevs = new ArrayList<>(vals.size());
            for (double v : vals) {
                absDevs.add(Math.abs(v - med));
            }
            Collections.sort(absDevs);
            double rawMad = median(absDevs);
            double scaledMad = rawMad * MAD_SCALE;
            if (scaledMad < EPSILON) scaledMad = Math.sqrt(EPSILON);
            hourlyStats.put(hour, new double[]{med, scaledMad});
        }
    }

    public double[] getHourlyStats(int hour) {
        return hourlyStats.get(hour);
    }

    private double median(List<Double> sorted) {
        int n = sorted.size();
        if (n % 2 == 1) {
            return sorted.get(n / 2);
        }
        return (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }

    public double[] getMedian() { return median; }
    public double[] getMad() { return mad; }
    public double getMedian(int i) { return median[i]; }
    public double getMad(int i) { return mad[i]; }

    @Deprecated
    public double[] getMean() { return median; }
    @Deprecated
    public double[] getStd() { return mad; }
    @Deprecated
    public double getMean(int i) { return median[i]; }
    @Deprecated
    public double getStd(int i) { return mad[i]; }
}
