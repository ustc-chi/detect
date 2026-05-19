
package com.anomalydetection.cli;

import com.anomalydetection.detector.*;
import com.anomalydetection.features.*;
import com.anomalydetection.model.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class WeightOptimizerCli {
    public static void main(String[] args) throws Exception {
        String baselineDir = args.length > 0 ? args[0] : "test-output/baseline";
        String allDir = args.length > 1 ? args[1] : "test-output/all";
        int iterations = args.length > 2 ? Integer.parseInt(args[2]) : 5000;

        Set<String> attackPrefixes = Set.of("005","010","015","018","022","025","028","031","034","037","040");
        
        RansomwareFeatureExtractor extractor = new RansomwareFeatureExtractor(null, 2.0);

        List<RansomwareFeatureVector> normalVecs = new ArrayList<>();
        List<RansomwareFeatureVector> attackVecs = new ArrayList<>();

        for (File f : new File(allDir).listFiles()) {
            String name = f.getName();
            String prefix = name.replace("round_","").split("_")[0];
            RansomwareFeatureVector vec = extractor.extractFromFile(f.toPath());
            if (attackPrefixes.contains(prefix)) {
                attackVecs.add(vec);
            } else {
                normalVecs.add(vec);
            }
        }
        
        System.out.println("Normal: " + normalVecs.size() + " Attack: " + attackVecs.size());
        
        WeightOptimizer optimizer = new WeightOptimizer(normalVecs, attackVecs);
        WeightOptimizer.OptimizationResult result = optimizer.optimize(iterations);
        System.out.println(result);
        
        // Print top-5 weights
        double[] w = result.weights;
        String[] names = RansomwareFeatureVector.FEATURE_NAMES;
        List<int[]> indexed = new ArrayList<>();
        for (int i = 0; i < w.length; i++) indexed.add(new int[]{i, (int)(w[i]*10000)});
        indexed.sort((a,b) -> b[1] - a[1]);
        System.out.println("\nTop weights:");
        for (int[] pair : indexed) {
            System.out.printf("  %s = %.4f%n", names[pair[0]], w[pair[0]]);
        }
        
        // Print per-attack scores
        BaselineStatistics stats = new BaselineStatistics(normalVecs);
        WeightedEuclideanScorer scorer = new WeightedEuclideanScorer(stats, result.weights);
        System.out.println("\nPer-attack scores:");
        for (File f : new File(allDir).listFiles()) {
            String name = f.getName();
            String prefix = name.replace("round_","").split("_")[0];
            if (!attackPrefixes.contains(prefix)) continue;
            RansomwareFeatureVector vec = extractor.extractFromFile(f.toPath());
            double score = scorer.score(vec);
            System.out.printf("  %-50s score=%.4f caught=%b%n", name, score, score > result.threshold);
        }
    }
}
