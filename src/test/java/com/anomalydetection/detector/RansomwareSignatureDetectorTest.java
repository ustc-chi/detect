package com.anomalydetection.detector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import com.anomalydetection.model.SnapdiffRecord;
import com.anomalydetection.model.SnapdiffFile;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

public class RansomwareSignatureDetectorTest {

    private static final String TEST_JSON_TEMPLATE =
        "{\"diffs\":[%s],\"summary\":{}}"; // placeholders for diffs

    private static String recJson(String path) {
        // Build a single diff object with the required fields used by SnapdiffRecord
        return String.format("{\"path\":\"%s\",\"type\":\"modified\",\"size\":\"100\",\"change_time\":\"2026-04-28T09:00:00Z\"}", path);
    }

    @Test
    public void testScanStreamDetectsSuspiciousExtension(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("test1.json");
        String json = "{\"diffs\":[" + recJson("/vol/share/a.locked") + "] ,\"summary\":{}}";
        Files.writeString(file, json);

        RansomwareSignatureDetector detector = new RansomwareSignatureDetector();
        RansomwareSignatureDetector.SignatureResult result = detector.scanStream(file);

        assertThat(result.matched()).isTrue();
        assertThat(result.getMatchedExtensions()).contains("/vol/share/a.locked");
    }

    @Test
    public void testScanStreamDetectsRansomNote(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("test2.json");
        String json = "{\"diffs\":[" + recJson("/vol/share/HOW_TO_DECRYPT_MY_FILES.txt") + "] ,\"summary\":{}}";
        Files.writeString(file, json);

        RansomwareSignatureDetector detector = new RansomwareSignatureDetector();
        RansomwareSignatureDetector.SignatureResult result = detector.scanStream(file);

        assertThat(result.matched()).isTrue();
        assertThat(result.getMatchedNotePaths()).contains("/vol/share/HOW_TO_DECRYPT_MY_FILES.txt");
    }

    @Test
    public void testScanStreamCleanFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("test3.json");
        String json = "{\"diffs\":[" + recJson("/vol/share/user1/docs/report.docx") + "] ,\"summary\":{}}";
        Files.writeString(file, json);

        RansomwareSignatureDetector detector = new RansomwareSignatureDetector();
        RansomwareSignatureDetector.SignatureResult result = detector.scanStream(file);

        assertThat(result.matched()).isFalse();
        assertThat(result.getMatchedExtensions()).isEmpty();
        assertThat(result.getMatchedNotePaths()).isEmpty();
    }

    @Test
    public void testScanStreamEqualsScan(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("test4.json");
        // Build 5 records: 2 suspicious extensions, 2 ransom notes, 1 clean
        List<String> paths = Arrays.asList(
            "/vol/share/a.locked",                 // ext
            "/vol/share/README_UNLOCK_ME.txt",       // ransom note
            "/vol/share/docs/report.docx",           // clean
            "/vol/share/another.crypt",              // ext
            "/vol/share/OPEN_ME.txt"                 // ransom note
        );
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < paths.size(); i++) {
            sb.append(recJson(paths.get(i)));
            if (i < paths.size() - 1) sb.append(",");
        }
        sb.append("]");
        String json = "{\"diffs\":" + sb.toString() + ",\"summary\":{}}";
        Files.writeString(file, json);

        // Streaming path
        RansomwareSignatureDetector detector = new RansomwareSignatureDetector();
        RansomwareSignatureDetector.SignatureResult resultStreaming = detector.scanStream(file);

        // In-memory parse
        ObjectMapper mapper = new ObjectMapper();
        SnapdiffFile sf = mapper.readValue(json, SnapdiffFile.class);
        List<SnapdiffRecord> records = sf.getDiffs();
        RansomwareSignatureDetector.SignatureResult resultInMemory = detector.scan(records);

        assertThat(resultStreaming.matched()).isEqualTo(resultInMemory.matched());
        assertThat(resultStreaming.getMatchedExtensions().size()).isEqualTo(resultInMemory.getMatchedExtensions().size());
        assertThat(resultStreaming.getMatchedNotePaths().size()).isEqualTo(resultInMemory.getMatchedNotePaths().size());
    }
}
