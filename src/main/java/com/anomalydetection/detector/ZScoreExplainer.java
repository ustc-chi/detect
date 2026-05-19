package com.anomalydetection.detector;

import com.anomalydetection.features.RansomwareFeatureVector;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.AbstractMap;

/**
 * Computes z-scores for a given vector against the baseline statistics.
 */
public class ZScoreExplainer {
    private final double[] mean;
    private final double[] std;
    private static final int N = RansomwareFeatureVector.FEATURE_COUNT;

    public ZScoreExplainer(BaselineStatistics stats) {
        this.mean = stats.getMean();
        this.std = stats.getStd();
    }

    public Map<String, Double> computeZScores(RansomwareFeatureVector vector) {
        Map<String, Double> map = new HashMap<>();
        for (int i = 0; i < N; i++) {
            double s = std[i];
            double z = 0.0;
            if (s > 0) {
                z = (vector.get(i) - mean[i]) / s;
            }
            String name = null;
            try {
                String[] names = com.anomalydetection.features.RansomwareFeatureVector.FEATURE_NAMES;
                if (names != null && i < names.length) {
                    name = names[i];
                }
            } catch (Throwable t) {
                // If for some reason names are unavailable, fallback to generic names
            }
            if (name == null) {
                name = "feature_" + i;
            }
            map.put(name, z);
        }
        return map;
    }

    public List<Map.Entry<String, Double>> topDeviations(RansomwareFeatureVector vector, int n) {
        Map<String, Double> z = computeZScores(vector);
        List<Map.Entry<String, Double>> list = new ArrayList<>(z.entrySet());
        final class AbsComparator implements java.util.Comparator<Map.Entry<String, Double>> {
            public int compare(Map.Entry<String, Double> a, Map.Entry<String, Double> b) {
                double ad = Math.abs(a.getValue());
                double bd = Math.abs(b.getValue());
                return Double.compare(bd, ad);
            }
        }
        Collections.sort(list, new AbsComparator());
        if (n <= 0 || n > list.size()) {
            return list;
        }
        return list.subList(0, n);
    }
}
