package com.anomalydetection.detector;

import com.anomalydetection.features.SuspiciousExtensions;
import com.anomalydetection.model.SnapdiffRecord;
import com.anomalydetection.parser.StreamingSnapdiffParser;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class RansomwareSignatureDetector {

    private static final String[] RANSOM_NOTE_PATTERNS = {
        "README_UNLOCK", "HOW_TO_DECRYPT", "READ_ME_UNLOCK",
        "DECRYPT_INSTRUCTIONS", "DECRYPT_YOUR_FILES", "RECOVER_FILES",
        "YOUR_FILES_ARE_ENCRYPTED", "OPEN_ME", "_DECRYPT_", "_READ_ME_",
        "HOW_TO_GET_BACK", "RESTORE_FILES"
    };

    private final SuspiciousExtensions suspiciousExtensions;

    public RansomwareSignatureDetector() {
        this.suspiciousExtensions = new SuspiciousExtensions();
    }

    public RansomwareSignatureDetector(SuspiciousExtensions suspiciousExtensions) {
        this.suspiciousExtensions = suspiciousExtensions;
    }

    public SignatureResult scan(List<SnapdiffRecord> records) {
        List<String> matchedExtensions = new ArrayList<>();
        List<String> matchedNotePaths = new ArrayList<>();

        for (SnapdiffRecord rec : records) {
            String path = rec.getPath();
            if (path == null) continue;

            int dotIdx = path.lastIndexOf('.');
            if (dotIdx >= 0 && dotIdx < path.length() - 1) {
                String ext = path.substring(dotIdx).toLowerCase();
                if (suspiciousExtensions.isSuspicious(ext)) {
                    matchedExtensions.add(path);
                }
            }

            int slashIdx = path.lastIndexOf('/');
            String filename = slashIdx >= 0 ? path.substring(slashIdx + 1) : path;
            String upperFilename = filename.toUpperCase();
            for (String pattern : RANSOM_NOTE_PATTERNS) {
                if (upperFilename.contains(pattern)) {
                    matchedNotePaths.add(path);
                    break;
                }
            }
        }

        return new SignatureResult(matchedExtensions, matchedNotePaths);
    }

    // Streaming version of the scan operation. Processes a JSON file containing
    // a Snapdiff report and streams records to avoid loading all records into memory.
    public SignatureResult scanStream(Path filePath) throws IOException {
        StreamingSnapdiffParser streamingParser = new StreamingSnapdiffParser();
        List<String> matchedExtensions = new ArrayList<>();
        List<String> matchedNotePaths = new ArrayList<>();

        streamingParser.parse(filePath, rec -> {
            String path = rec.getPath();
            if (path == null) return;

            int dotIdx = path.lastIndexOf('.');
            if (dotIdx >= 0 && dotIdx < path.length() - 1) {
                String ext = path.substring(dotIdx).toLowerCase();
                if (suspiciousExtensions.isSuspicious(ext)) {
                    matchedExtensions.add(path);
                }
            }

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

        return new SignatureResult(matchedExtensions, matchedNotePaths);
    }

    public static class SignatureResult {
        private final List<String> matchedExtensions;
        private final List<String> matchedNotePaths;

        public SignatureResult(List<String> matchedExtensions, List<String> matchedNotePaths) {
            this.matchedExtensions = matchedExtensions;
            this.matchedNotePaths = matchedNotePaths;
        }

        public boolean matched() {
            return !matchedExtensions.isEmpty() || !matchedNotePaths.isEmpty();
        }

        public List<String> getMatchedExtensions() { return matchedExtensions; }
        public List<String> getMatchedNotePaths() { return matchedNotePaths; }

        public String describe() {
            if (!matched()) return null;
            StringBuilder sb = new StringBuilder();
            if (!matchedExtensions.isEmpty()) {
                sb.append("suspicious_ext:").append(matchedExtensions.size()).append(" files");
            }
            if (!matchedNotePaths.isEmpty()) {
                if (sb.length() > 0) sb.append("; ");
                sb.append("ransom_notes:").append(matchedNotePaths.size()).append(" files");
            }
            return sb.toString();
        }
    }
}
