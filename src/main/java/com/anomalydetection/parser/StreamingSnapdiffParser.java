package com.anomalydetection.parser;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.anomalydetection.model.SnapdiffFile;
import com.anomalydetection.model.SnapdiffRecord;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Streaming parser for Snapdiff records. This reads the "diffs" array from the JSON
 * file and streams each {@link SnapdiffRecord} to the provided consumer without loading
 * the entire list into memory.
 */
public class StreamingSnapdiffParser {

    // Shared ObjectMapper — thread-safe for read operations, avoids per-instance allocation
    private static final ObjectMapper SHARED_MAPPER = new ObjectMapper();
    static {
        SHARED_MAPPER.registerModule(new JavaTimeModule());
        SHARED_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    // Shared JsonFactory — thread-safe, avoids per-parse() allocation
    // INTERN_FIELD_NAMES=false eliminates String.intern() global lock for repeated field names
    private static final JsonFactory SHARED_FACTORY = JsonFactory.builder()
        .disable(JsonFactory.Feature.INTERN_FIELD_NAMES)
        .build();

    public StreamingSnapdiffParser() {
        // Empty constructor kept for backward compatibility
    }

    /**
     * Parse the given file and stream each SnapdiffRecord to the consumer.
     * The expected JSON structure is: {"diffs": [ ... ], "summary": { ... }}
     */
    public void parse(Path filePath, Consumer<SnapdiffRecord> consumer) throws IOException {
        try (InputStream in = Files.newInputStream(filePath);
             BufferedInputStream bis = new BufferedInputStream(in, 262144)) {

            JsonParser jsonParser = SHARED_FACTORY.createParser(bis);
            if (jsonParser.nextToken() != JsonToken.START_OBJECT) {
                throw new IOException("Expected start of JSON object");
            }

            while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = jsonParser.getCurrentName();
                if ("diffs".equals(fieldName)) {
                    jsonParser.nextToken(); // value token
                    if (jsonParser.getCurrentToken() != JsonToken.START_ARRAY) {
                        continue;
                    }
                    jsonParser.nextToken(); // advance to first element inside array

                    MappingIterator<SnapdiffRecord> iter = SHARED_MAPPER.readValues(jsonParser, SnapdiffRecord.class);
                    while (iter.hasNextValue()) {
                        SnapdiffRecord rec = iter.nextValue();
                        String t = rec.getType();
                        if (!"added".equals(t) && !"modified".equals(t) && !"deleted".equals(t)) {
                            throw new IllegalArgumentException("Invalid diff type: " + t);
                        }
                        consumer.accept(rec);
                    }
                    iter.close();
                } else {
                    jsonParser.nextToken();
                    jsonParser.skipChildren();
                }
            }
        }
    }

    /**
     * Parse the given file and return a SnapdiffFile with all records collected.
     * For large files, prefer using {@link #parse(Path, Consumer)} to stream records
     * without collecting them all in memory.
     */
    public SnapdiffFile parseToSnapdiffFile(Path filePath) throws IOException {
        List<SnapdiffRecord> records = new ArrayList<>();
        parse(filePath, records::add);
        return new SnapdiffFile(records, null);
    }
}
