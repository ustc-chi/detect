package com.anomalydetection.precheck;

import com.anomalydetection.features.FeatureVector;

import java.util.ArrayList;
import java.util.List;

/**
 * Pre-check service that inspects a {@link FeatureVector} for known ransomware
 * signatures (suspicious file extensions and ransom note file names).
 * <p>
 * The signature scanning is performed by the feature-extractor module during
 * feature extraction; results are stored in {@link FeatureVector#getExtendInfo()}
 * under the key defined by {@link #PRECHECK_EXTENSIONS_KEY} and
 * {@link #PRECHECK_RANSOM_NOTES_KEY}.
 * <p>
 * This service runs as the first step in the detection pipeline, <em>before</em>
 * any statistical or heuristic detection.
 */
public class PreCheckService {

    /**
     * Key used in {@link FeatureVector#getExtendInfo()} to store matched
     * suspicious extension file paths. Align with the FeatureType key defined
     * in feature-extractor.
     */
    public static final String PRECHECK_EXTENSIONS_KEY = "precheck_suspicious_extensions";

    /**
     * Key used in {@link FeatureVector#getExtendInfo()} to store matched
     * ransom note file paths. Align with the FeatureType key defined in
     * feature-extractor.
     */
    public static final String PRECHECK_RANSOM_NOTES_KEY = "precheck_ransom_notes";

    /**
     * Inspect a FeatureVector for signature matches.
     *
     * @param vector the feature vector from feature-extractor
     * @return pre-check result
     */
    public PreCheckResult check(FeatureVector vector) {
        List<String> matchedExtensions = readExtendInfoList(vector, PRECHECK_EXTENSIONS_KEY);
        List<String> matchedNotePaths = readExtendInfoList(vector, PRECHECK_RANSOM_NOTES_KEY);
        return new PreCheckResult(matchedExtensions, matchedNotePaths);
    }

    /**
     * Reads a {@code String[]} from the FeatureVector's extendInfo by key
     * and converts it to a mutable list, returning an empty list if absent.
     */
    private static List<String> readExtendInfoList(FeatureVector vector, String key) {
        String[] raw = vector.getExtendInfo().get(key);
        if (raw == null || raw.length == 0) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>(raw.length);
        for (String s : raw) {
            if (s != null && !s.isEmpty()) {
                result.add(s);
            }
        }
        return result;
    }
}
