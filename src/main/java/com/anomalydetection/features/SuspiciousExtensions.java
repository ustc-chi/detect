package com.anomalydetection.features;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Curated list of suspicious ransomware extensions. Provides optional loading
 * from a config file (one extension per line, comments starting with '#').
 */
public class SuspiciousExtensions {
    private static final Set<String> DEFAULT_EXTENSIONS = Set.of(
        ".encrypted", ".locked", ".crypt", ".crypto", ".enc",
        ".cerber", ".locky", ".zepto", ".odin", ".thor",
        ".aesir", ".sage", ".globe", ".purge", ".osiris",
        ".cryptolocker", ".cry", ".dharma", ".phobos",
        ".sodinokibi", ".ryuk", ".conti", ".revil",
        ".blackcat", ".alphv", ".lockbit", ".hive",
        ".play", ".blackbyte", ".foop", ".help",
        ".decrypt", ".trun", ".lockedbydoctor",
        ".dqn", ".adobe", ".spora", ".globeimposter",
        ".shade", ".bart", ".globe2", ".crypton",
        ".xtbl", ".cryptowall", ".ceber", ".zerber",
        ".odcodc", ".craft", ".lesli", ".marl"
    );

    private final Set<String> extensions;

    // Constructor with default list
    public SuspiciousExtensions() {
        this.extensions = new HashSet<>(DEFAULT_EXTENSIONS);
    }

    // Constructor loading from file (one extension per line, lines starting with # are comments)
    public SuspiciousExtensions(Path configFile) {
        Set<String> loaded = new HashSet<>();
        if (configFile != null) {
            try {
                List<String> lines = Files.readAllLines(configFile);
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty()) continue;
                    if (trimmed.startsWith("#")) continue;
                    loaded.add(trimmed.toLowerCase());
                }
            } catch (IOException e) {
                // If config cannot be read, fall back to default list
                loaded = new HashSet<>(DEFAULT_EXTENSIONS);
            }
        }
        this.extensions = loaded.isEmpty() ? new HashSet<>(DEFAULT_EXTENSIONS) : loaded;
    }

    // Check if extension is suspicious
    public boolean isSuspicious(String extension) {
        if (extension == null) return false;
        return extensions.contains(extension.toLowerCase());
    }

    // Get all extensions
    public Set<String> getExtensions() {
        return Collections.unmodifiableSet(extensions);
    }
}
