package com.anomalydetection.detector.v2;

import com.anomalydetection.features.SuspiciousExtensions;
import com.anomalydetection.model.SnapdiffRecord;
import com.anomalydetection.parser.StreamingSnapdiffParser;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Pre-check service that scans a snapdiff JSON file for known ransomware signatures
 * (suspicious file extensions and ransom note file names).
 * <p>
 * This runs as the first step in the detection pipeline, <em>before</em> any
 * statistical or heuristic detection. If a signature match is found, the detection
 * short-circuits and returns an immediate anomaly.
 */
public class PreCheckService {

    private static final String[] RANSOM_NOTE_PATTERNS = {
        "README_UNLOCK", "HOW_TO_DECRYPT", "READ_ME_UNLOCK",
        "DECRYPT_INSTRUCTIONS", "DECRYPT_YOUR_FILES", "RECOVER_FILES",
        "YOUR_FILES_ARE_ENCRYPTED", "OPEN_ME", "_DECRYPT_", "_READ_ME_",
        "HOW_TO_GET_BACK", "RESTORE_FILES"
    };

    private final SuspiciousExtensions suspiciousExtensions;

    public PreCheckService() {
        this.suspiciousExtensions = new SuspiciousExtensions();
    }

    /**
     * Scan a snapdiff JSON file for signature matches.
     *
     * @param filePath path to the snapdiff JSON file
     * @return pre-check result
     * @throws IOException if file cannot be read or parsed
     */
    public PreCheckResult check(Path filePath) throws IOException {
        StreamingSnapdiffParser parser = new StreamingSnapdiffParser();
        List<String> matchedExtensions = new ArrayList<>();
        List<String> matchedNotePaths = new ArrayList<>();

        parser.parse(filePath, record -> {
            String path = record.getPath();
            if (path == null) return;

            // Check file extension
            int dotIdx = path.lastIndexOf('.');
            if (dotIdx >= 0 && dotIdx < path.length() - 1) {
                String ext = path.substring(dotIdx).toLowerCase();
                if (suspiciousExtensions.isSuspicious(ext)) {
                    matchedExtensions.add(path);
                }
            }

            // Check ransom note patterns
            int slashIdx = path.lastIndexOf('/');
            String filename = slashIdx >= 0 ? path.substring(slashIdx + 1) : path;
            String upperFilename = filename.toUpperCase();
            for (String pattern : RANSOM_NOTE_PATTERNS) {
                if (upperFilename.contains(pattern)) {
                    matchedNotePaths.add(path);
                    break;
                }
            }
        });

        return new PreCheckResult(matchedExtensions, matchedNotePaths);
    }

    /**
     * Result of a pre-check scan.
     */
    public static class PreCheckResult {
        private final List<String> matchedExtensions;
        private final List<String> matchedNotePaths;

        public PreCheckResult(List<String> matchedExtensions, List<String> matchedNotePaths) {
            this.matchedExtensions = matchedExtensions;
            this.matchedNotePaths = matchedNotePaths;
        }

        /** Returns true if any signature match was found. */
        public boolean matched() {
            return !matchedExtensions.isEmpty() || !matchedNotePaths.isEmpty();
        }

        public List<String> getMatchedExtensions() { return matchedExtensions; }
        public List<String> getMatchedNotePaths() { return matchedNotePaths; }

        /** Human-readable description of what was matched. */
        public String describe() {
            StringBuilder sb = new StringBuilder();
            if (!matchedExtensions.isEmpty()) {
                sb.append("Suspicious extensions: ").append(matchedExtensions.size()).append(" files (e.g., ")
                  .append(matchedExtensions.get(0)).append(")");
            }
            if (!matchedNotePaths.isEmpty()) {
                if (sb.length() > 0) sb.append("; ");
                sb.append("Ransom notes: ").append(matchedNotePaths.size()).append(" files (e.g., ")
                  .append(matchedNotePaths.get(0)).append(")");
            }
            return sb.toString();
        }
    }
}
