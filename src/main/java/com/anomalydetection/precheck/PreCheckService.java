package com.anomalydetection.precheck;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Pre-check service that scans a snapdiff JSON file for known ransomware signatures
 * (suspicious file extensions and ransom note file names).
 * <p>
 * This runs as the first step in the detection pipeline, <em>before</em> any
 * statistical or heuristic detection. Uses streaming JSON parsing to handle
 * large files without loading the entire document into memory.
 */
public class PreCheckService {

    private static final Set<String> SUSPICIOUS_EXTENSIONS = Set.of(
            ".locked", ".encrypted", ".crypt", ".enc", ".locky", ".wallet",
            ".onion", ".ezz", ".exx", ".zzz", ".xyz", ".aaa", ".abc",
            ".ccc", ".vvv", ".ttt", ".micro", ".encryptedRSA", ".crinf",
            ".r16m", ".dxxx", ".7zipp", ".axx", ".mzzz", ".hblen",
            ".hblr", ".Vault", ".LOL!", ".WNCRY", ".wcry", ".wnry",
            ".odid", ".id", ".id_[", ".R5A", ".KEYZZ", ".FREEDOM",
            ".zepto", ".cerber", ".legion", ".lambda", ".python",
            ".java", ".jse", ".js", ".vbs", ".ps1", ".scr", ".exe"
    );

    private static final String[] RANSOM_NOTE_PATTERNS = {
            "README_UNLOCK", "HOW_TO_DECRYPT", "READ_ME_UNLOCK",
            "DECRYPT_INSTRUCTIONS", "DECRYPT_YOUR_FILES", "RECOVER_FILES",
            "YOUR_FILES_ARE_ENCRYPTED", "OPEN_ME", "_DECRYPT_", "_READ_ME_",
            "HOW_TO_GET_BACK", "RESTORE_FILES"
    };

    private final JsonFactory jsonFactory;

    public PreCheckService() {
        this.jsonFactory = new JsonFactory();
    }

    /**
     * Scan a snapdiff JSON file for signature matches using streaming parser.
     *
     * @param filePath path to the snapdiff JSON file
     * @return pre-check result
     * @throws IOException if file cannot be read or parsed
     */
    public PreCheckResult check(Path filePath) throws IOException {
        List<String> matchedExtensions = new ArrayList<>();
        List<String> matchedNotePaths = new ArrayList<>();

        try (InputStream in = Files.newInputStream(filePath);
             JsonParser parser = jsonFactory.createParser(in)) {

            // Expect the file to be a JSON array
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new IOException("Expected JSON array at root of snapdiff file");
            }

            // Stream through each array element
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                if (parser.currentToken() == JsonToken.START_OBJECT) {
                    scanRecord(parser, matchedExtensions, matchedNotePaths);
                }
            }
        }

        return new PreCheckResult(matchedExtensions, matchedNotePaths);
    }

    /**
     * Scan a single JSON record from the streaming parser.
     */
    private void scanRecord(JsonParser parser, List<String> matchedExtensions,
                            List<String> matchedNotePaths) throws IOException {
        String path = null;

        // Read fields from the current object
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (parser.currentToken() == JsonToken.FIELD_NAME) {
                String fieldName = parser.currentName();
                parser.nextToken(); // advance to value

                if ("path".equals(fieldName) || "filePath".equals(fieldName)) {
                    path = parser.getValueAsString();
                } else {
                    parser.skipChildren();
                }
            }
        }

        if (path == null || path.isEmpty()) return;

        // Check file extension
        int dotIdx = path.lastIndexOf('.');
        if (dotIdx >= 0 && dotIdx < path.length() - 1) {
            String ext = path.substring(dotIdx).toLowerCase();
            if (SUSPICIOUS_EXTENSIONS.contains(ext)) {
                matchedExtensions.add(path);
                return; // one match per file is enough
            }
        }

        // Check ransom note patterns
        int slashIdx = path.lastIndexOf('/');
        if (slashIdx < 0) slashIdx = path.lastIndexOf('\\');
        String filename = slashIdx >= 0 ? path.substring(slashIdx + 1) : path;
        String upperFilename = filename.toUpperCase();
        for (String pattern : RANSOM_NOTE_PATTERNS) {
            if (upperFilename.contains(pattern)) {
                matchedNotePaths.add(path);
                break;
            }
        }
    }
}
