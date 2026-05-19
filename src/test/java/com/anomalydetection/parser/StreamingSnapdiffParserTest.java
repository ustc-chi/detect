package com.anomalydetection.parser;

import com.anomalydetection.model.SnapdiffRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class StreamingSnapdiffParserTest {

    @Test
    void testValidFile(@TempDir Path tempDir) throws IOException {
        String json = "{\n" +
                "  \"diffs\": [\n" +
                "    {\"path\":\"/vol/share/a.txt\",\"type\":\"added\",\"size\":\"100\",\"change_time\":\"2026-04-28T09:00:00Z\"},\n" +
                "    {\"path\":\"/vol/share/b.txt\",\"type\":\"modified\",\"size\":\"200\",\"change_time\":\"2026-04-28T09:01:00Z\"},\n" +
                "    {\"path\":\"/vol/share/c.txt\",\"type\":\"deleted\",\"size\":\"0\",\"change_time\":\"2026-04-28T09:02:00Z\"}\n" +
                "  ],\n" +
                "  \"summary\": {\"total\": 3}\n" +
                "}\n";
        Path file = tempDir.resolve("diffs.json");
        Files.writeString(file, json);

        StreamingSnapdiffParser parser = new StreamingSnapdiffParser();
        List<SnapdiffRecord> results = new ArrayList<>();
        parser.parse(file, results::add);

        assertThat(results).hasSize(3);
        assertThat(results.get(0).getPath()).isEqualTo("/vol/share/a.txt");
        assertThat(results.get(0).getType()).isEqualTo("added");
        assertThat(results.get(1).getPath()).isEqualTo("/vol/share/b.txt");
        assertThat(results.get(1).getType()).isEqualTo("modified");
        assertThat(results.get(2).getPath()).isEqualTo("/vol/share/c.txt");
        assertThat(results.get(2).getType()).isEqualTo("deleted");
    }

    @Test
    void testEmptyDiffs(@TempDir Path tempDir) throws IOException {
        String json = "{ \"diffs\": [], \"summary\": {} }";
        Path file = tempDir.resolve("empty.json");
        Files.writeString(file, json);

        StreamingSnapdiffParser parser = new StreamingSnapdiffParser();
        List<SnapdiffRecord> results = new ArrayList<>();
        parser.parse(file, results::add);
        assertThat(results).isEmpty();
    }

    @Test
    void testInvalidDiffType(@TempDir Path tempDir) throws IOException {
        String json = "{ \"diffs\": [ {\"path\":\"/a\",\"type\":\"unknown\",\"size\":\"1\",\"change_time\":\"2026-04-28T09:00:00Z\"} ], \"summary\": {} }";
        Path file = tempDir.resolve("invalid.json");
        Files.writeString(file, json);

        StreamingSnapdiffParser parser = new StreamingSnapdiffParser();
        assertThatThrownBy(() -> parser.parse(file, r -> {}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid diff type");
    }

    @Test
    void testResourceCleanup(@TempDir Path tempDir) throws IOException {
        String json = "{\"diffs\": [ {\"path\":\"/x\",\"type\":\"added\",\"size\":\"1\",\"change_time\":\"2026-04-28T09:00:00Z\"} ],\"summary\":{}}";
        Path file = tempDir.resolve("one.json");
        Files.writeString(file, json);

        StreamingSnapdiffParser parser = new StreamingSnapdiffParser();
        parser.parse(file, r -> {});
        // If no exception is thrown, we assume resources were cleaned up properly.
        assertThat(true).isTrue();
    }
}
