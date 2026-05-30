package com.anomalydetection.precheck;

import java.util.List;

/**
 * Result of the pre-check signature scan.
 */
public class PreCheckResult {
    private final List<String> matchedExtensions;
    private final List<String> ransomNotePaths;

    public PreCheckResult(List<String> matchedExtensions, List<String> ransomNotePaths) {
        this.matchedExtensions = matchedExtensions;
        this.ransomNotePaths = ransomNotePaths;
    }

    public boolean isMatch() {
        return (!matchedExtensions.isEmpty()) || (!ransomNotePaths.isEmpty());
    }

    public List<String> getMatchedExtensions() { return matchedExtensions; }
    public List<String> getRansomNotePaths() { return ransomNotePaths; }
}
