package com.anomalydetection.parser;

import com.anomalydetection.model.SnapdiffFile;
import com.anomalydetection.model.SnapdiffRecord;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * JSON parser for Snapdiff reports.
 *
 * @deprecated Use {@link StreamingSnapdiffParser} instead.
 */
@Deprecated
public class SnapdiffParser {
    private final ObjectMapper objectMapper;

    public SnapdiffParser() {
        objectMapper = new ObjectMapper();
        // support Java 8 time types if present in the model
        objectMapper.registerModule(new JavaTimeModule());
        // tolerate unknown properties for forward-compatibility
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * @deprecated Use {@link com.anomalydetection.parser.StreamingSnapdiffParser} for large files to avoid loading all records into memory.
     */
    @Deprecated
    public SnapdiffFile parse(Path filePath) throws IOException {
        SnapdiffFile file = objectMapper.readValue(filePath.toFile(), SnapdiffFile.class);
        // Basic validation: ensure all diff entries have a valid type
        if (file.getDiffs() != null) {
            for (SnapdiffRecord r : file.getDiffs()) {
                String t = r.getType();
                if (t == null || !(t.equals("added") || t.equals("modified") || t.equals("deleted"))) {
                    throw new IllegalArgumentException("Invalid diff type: " + t);
                }
            }
        }
        return file;
    }
}
