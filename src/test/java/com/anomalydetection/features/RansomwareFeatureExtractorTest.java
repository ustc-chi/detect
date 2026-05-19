package com.anomalydetection.features;

import com.anomalydetection.model.SnapdiffFile;
import com.anomalydetection.model.SnapdiffRecord;
import com.anomalydetection.detector.BaselineStatistics;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;

public class RansomwareFeatureExtractorTest {

    private final RansomwareFeatureExtractor extractor = new RansomwareFeatureExtractor(null);

    private SnapdiffFile makeFile(SnapdiffRecord... records) {
        return new SnapdiffFile(Arrays.asList(records), Map.of());
    }

    private SnapdiffRecord rec(String path, String type, long size, Instant time) {
        return new SnapdiffRecord(path, type, String.valueOf(size), time != null ? time.toString() : null);
    }

    @Test
    void testTotalOperationsNormalized() {
        SnapdiffFile file = makeFile(
            rec("/vol/share/user1/a.txt", "added", 100, Instant.parse("2026-04-28T09:00:00Z")),
            rec("/vol/share/user1/b.txt", "modified", 200, Instant.parse("2026-04-28T10:00:00Z")),
            rec("/vol/share/user1/c.txt", "deleted", 50, Instant.parse("2026-04-28T11:00:00Z"))
        );
        RansomwareFeatureVector vec = extractor.extract(file);
        assertThat(vec.getTotalOperationsNormalized()).isCloseTo(1.5, within(1e-9));
    }

    @Test
    void testModificationRatio() {
        SnapdiffFile file = makeFile(
            rec("/vol/share/user1/a.txt", "modified", 100, Instant.parse("2026-04-28T09:00:00Z")),
            rec("/vol/share/user1/b.txt", "modified", 200, Instant.parse("2026-04-28T10:00:00Z")),
            rec("/vol/share/user1/c.txt", "added", 50, Instant.parse("2026-04-28T11:00:00Z"))
        );
        RansomwareFeatureVector vec = extractor.extract(file);
        assertThat(vec.getModificationRatio()).isCloseTo(2.0 / 3.0, within(1e-9));
    }

    @Test
    void testPeakBurstVelocity() {
        Instant t1 = Instant.parse("2026-04-28T09:00:00Z");
        Instant t2 = Instant.parse("2026-04-28T09:01:00Z");
        Instant t3 = Instant.parse("2026-04-28T09:02:00Z");
        SnapdiffFile file = makeFile(
            rec("/vol/share/user1/a.txt", "added", 100, t1),
            rec("/vol/share/user1/b.txt", "added", 200, t2),
            rec("/vol/share/user1/c.txt", "added", 300, t3)
        );
        RansomwareFeatureVector vec = extractor.extract(file);
        assertThat(vec.getPeakBurstVelocity()).isCloseTo(36.0, within(0.01));
    }

    @Test
    void testEmptySnapdiffProducesZeroVector() {
        SnapdiffFile empty = new SnapdiffFile(Collections.emptyList(), Map.of());
        RansomwareFeatureVector vec = extractor.extract(empty);
        double[] arr = vec.toArray();
        assertThat(arr).hasSize(RansomwareFeatureVector.FEATURE_COUNT);
        for (double v : arr) {
            assertThat(v).isCloseTo(0.0, within(1e-9));
        }
    }

    @Test
    void testSingleRecordVelocityIsZero() {
        SnapdiffFile file = makeFile(
            rec("/vol/share/user1/a.txt", "added", 100, Instant.parse("2026-04-28T09:00:00Z"))
        );
        RansomwareFeatureVector vec = extractor.extract(file);
        assertThat(vec.getPeakBurstVelocity()).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void testPerTypeEntropy() {
        SnapdiffFile file = makeFile(
            rec("/vol/share/user1/a.txt", "modified", 100, Instant.parse("2026-04-28T09:00:00Z")),
            rec("/vol/share/user1/b.txt", "modified", 200, Instant.parse("2026-04-28T10:00:00Z")),
            rec("/vol/share/user1/c.txt", "added", 50, Instant.parse("2026-04-28T11:00:00Z"))
        );
        RansomwareFeatureVector vec = extractor.extract(file);
        double expectedH = 0.0;
        expectedH -= (2.0 / 3.0) * Math.log(2.0 / 3.0) / Math.log(2);
        expectedH -= (1.0 / 3.0) * Math.log(1.0 / 3.0) / Math.log(2);
        assertThat(vec.getPerTypeEntropy()).isCloseTo(expectedH, within(1e-9));
    }

    @Test
    void testPerTypeEntropyAllSameType() {
        SnapdiffFile file = makeFile(
            rec("/vol/share/user1/a.txt", "modified", 100, Instant.parse("2026-04-28T09:00:00Z")),
            rec("/vol/share/user1/b.txt", "modified", 200, Instant.parse("2026-04-28T10:00:00Z"))
        );
        RansomwareFeatureVector vec = extractor.extract(file);
        assertThat(vec.getPerTypeEntropy()).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void testDirectoryCoverageDepth() {
        SnapdiffFile file = makeFile(
            rec("/vol/share/user1/docs/a.txt", "modified", 100, Instant.parse("2026-04-28T09:00:00Z")),
            rec("/vol/share/user1/docs/b.txt", "modified", 200, Instant.parse("2026-04-28T09:01:00Z")),
            rec("/vol/share/user3/projects/c.txt", "modified", 300, Instant.parse("2026-04-28T09:02:00Z"))
        );
        RansomwareFeatureVector vec = extractor.extract(file);
        assertThat(vec.getDirectoryCoverageDepth()).isGreaterThan(0.0);
    }

    @Test
    void testRenameCorrelation() {
        SnapdiffFile file = makeFile(
            rec("/vol/share/user1/docs/report.docx", "deleted", 1000, Instant.parse("2026-04-28T09:00:00Z")),
            rec("/vol/share/user1/docs/report.docx.abc12345", "added", 1050, Instant.parse("2026-04-28T09:00:01Z"))
        );
        RansomwareFeatureVector vec = extractor.extract(file);
        assertThat(vec.getRenameCorrelation()).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void testRenameCorrelationNoRenames() {
        SnapdiffFile file = makeFile(
            rec("/vol/share/user1/a.txt", "modified", 100, Instant.parse("2026-04-28T09:00:00Z")),
            rec("/vol/share/user1/b.txt", "modified", 200, Instant.parse("2026-04-28T09:01:00Z"))
        );
        RansomwareFeatureVector vec = extractor.extract(file);
        assertThat(vec.getRenameCorrelation()).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void testHighValueFileCoverageCappedAtOne() {
        SnapdiffFile file = makeFile(
            rec("/vol/share/user1/a.docx", "modified", 100, Instant.parse("2026-04-28T09:00:00Z")),
            rec("/vol/share/user1/b.xlsx", "modified", 200, Instant.parse("2026-04-28T09:01:00Z"))
        );
        RansomwareFeatureVector vec = extractor.extract(file);
        assertThat(vec.getHighValueFileCoverage()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void testWallClockAnomalyNoBaselineStats() {
        SnapdiffFile file = makeFile(
            rec("/vol/share/user1/a.txt", "modified", 100, Instant.parse("2026-04-28T09:00:00Z"))
        );
        RansomwareFeatureVector vec = extractor.extract(file);
        assertThat(vec.getWallClockAnomaly()).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void testWallClockAnomalyOffHoursBurst() {
        BaselineStatistics stats = new BaselineStatistics(Arrays.asList(
            new RansomwareFeatureVector(new double[12]),
            new RansomwareFeatureVector(new double[12])
        ));
        for (int i = 0; i < 10; i++) {
            stats.addHourlyObservation(3, 40 + i * 2);
        }
        stats.computeHourlyStats();

        RansomwareFeatureExtractor ext = new RansomwareFeatureExtractor(null);
        ext.setBaselineStats(stats);

        List<SnapdiffRecord> records = new ArrayList<>();
        for (int i = 0; i < 3000; i++) {
            records.add(rec("/vol/share/user1/f" + i + ".txt", "modified", 100,
                Instant.parse("2026-04-28T03:00:00Z").plusSeconds(i)));
        }
        SnapdiffFile file = new SnapdiffFile(records, Map.of());
        RansomwareFeatureVector vec = ext.extract(file);
        assertThat(vec.getWallClockAnomaly()).isCloseTo(10.0, within(0.1));
    }

    @Test
    void testWallClockAnomalyNormalHours() {
        BaselineStatistics stats = new BaselineStatistics(Arrays.asList(
            new RansomwareFeatureVector(new double[12]),
            new RansomwareFeatureVector(new double[12])
        ));
        for (int i = 0; i < 10; i++) {
            stats.addHourlyObservation(14, 400 + i * 10);
        }
        stats.computeHourlyStats();

        RansomwareFeatureExtractor ext = new RansomwareFeatureExtractor(null);
        ext.setBaselineStats(stats);

        List<SnapdiffRecord> records = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            records.add(rec("/vol/share/user1/f" + i + ".txt", "modified", 100,
                Instant.parse("2026-04-28T14:00:00Z").plusSeconds(i)));
        }
        SnapdiffFile file = new SnapdiffFile(records, Map.of());
        RansomwareFeatureVector vec = ext.extract(file);
        assertThat(vec.getWallClockAnomaly()).isCloseTo(1.21, within(0.5));
    }

    @Test
    void testWallClockAnomalyLearningPeriod() {
        BaselineStatistics stats = new BaselineStatistics(Arrays.asList(
            new RansomwareFeatureVector(new double[12]),
            new RansomwareFeatureVector(new double[12])
        ));
        stats.computeHourlyStats();

        RansomwareFeatureExtractor ext = new RansomwareFeatureExtractor(null);
        ext.setBaselineStats(stats);

        SnapdiffFile file = makeFile(
            rec("/vol/share/user1/a.txt", "modified", 100, Instant.parse("2026-04-28T05:00:00Z"))
        );
        RansomwareFeatureVector vec = ext.extract(file);
        assertThat(vec.getWallClockAnomaly()).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void testWallClockAnomalyAllEpochTimestamps() {
        BaselineStatistics stats = new BaselineStatistics(Arrays.asList(
            new RansomwareFeatureVector(new double[12]),
            new RansomwareFeatureVector(new double[12])
        ));
        for (int i = 0; i < 10; i++) {
            stats.addHourlyObservation(3, 50);
        }
        stats.computeHourlyStats();

        RansomwareFeatureExtractor ext = new RansomwareFeatureExtractor(null);
        ext.setBaselineStats(stats);

        SnapdiffFile file = makeFile(
            rec("/vol/share/user1/a.txt", "modified", 100, Instant.EPOCH)
        );
        RansomwareFeatureVector vec = ext.extract(file);
        assertThat(vec.getWallClockAnomaly()).isCloseTo(0.0, within(1e-9));
    }

    // Streaming tests
    private Path writeTempSnapdiff(SnapdiffFile file, Path tempDir) throws java.io.IOException {
        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, Object>> diffs = new ArrayList<>();
        for (SnapdiffRecord rec : file.getDiffs()) {
            Map<String, Object> m = new HashMap<>();
            m.put("path", rec.getPath());
            m.put("type", rec.getType());
            m.put("size", String.valueOf(rec.getSize()));
            m.put("change_time", rec.getChangeTime() != null ? rec.getChangeTime().toString() : null);
            diffs.add(m);
        }
        Map<String, Object> root = new HashMap<>();
        root.put("diffs", diffs);
        root.put("summary", Map.of());
        Path tmp = Files.createTempFile(tempDir, "snapdiff-streaming-", ".json");
        mapper.writeValue(tmp.toFile(), root);
        return tmp;
    }

    @Test
    void testStreamingEqualsInMemory(@TempDir Path tempDir) throws Exception {
        Instant t1 = Instant.parse("2026-04-28T09:00:00Z");
        Instant t2 = Instant.parse("2026-04-28T10:15:00Z");
        Instant t3 = Instant.parse("2026-04-28T11:30:00Z");

        SnapdiffFile memFile = makeFile(
            rec("/vol/share/user1/a.txt", "added", 100, t1),
            rec("/vol/share/user1/b.txt", "modified", 200, t2),
            rec("/vol/share/user2/c.txt", "deleted", 50, t3),
            rec("/vol/share/user3/d.md", "added", 60, t3)
        );
        RansomwareFeatureVector vMem = extractor.extract(memFile);

        Path streamingPath = writeTempSnapdiff(memFile, tempDir);
        RansomwareFeatureVector vStream = extractor.extractFromFile(streamingPath);

        double[] a = vMem.toArray();
        double[] b = vStream.toArray();
        assertThat(a).hasSameSizeAs(b);
        for (int i = 0; i < a.length; i++) {
            assertThat(a[i]).isCloseTo(b[i], within(1e-9));
        }
    }

    @Test
    void testStreamingEmptyFile(@TempDir Path tempDir) throws Exception {
        SnapdiffFile empty = new SnapdiffFile(Collections.emptyList(), Map.of());
        RansomwareFeatureVector vMem = extractor.extract(empty);
        Path p = writeTempSnapdiff(empty, tempDir);
        RansomwareFeatureVector vStream = extractor.extractFromFile(p);
        assertThat(vMem.toArray()).isEqualTo(vStream.toArray());
        double[] zero = new double[RansomwareFeatureVector.FEATURE_COUNT];
        Arrays.fill(zero, 0.0);
        assertThat(vStream.toArray()).isEqualTo(zero);
    }

    @Test
    void testStreamingLargeFile(@TempDir Path tempDir) throws Exception {
        List<SnapdiffRecord> recs = new ArrayList<>(100000);
        Instant base = Instant.parse("2026-04-28T09:00:00Z");
        for (int i = 0; i < 100000; i++) {
            String path = "/vol/share/user" + (i % 10) + "/file" + i + ".txt";
            String type = (i % 5 == 0) ? "modified" : "added";
            long size = 100 + (i % 1000);
            Instant t = base.plusSeconds(i * 1L);
            recs.add(rec(path, type, size, t));
        }
        SnapdiffFile large = new SnapdiffFile(recs, Map.of());
        RansomwareFeatureVector vMem = extractor.extract(large);
        Path p = writeTempSnapdiff(large, tempDir);
        RansomwareFeatureVector vStream = extractor.extractFromFile(p);
        assertThat(vStream.getTotalOperationsNormalized()).isCloseTo(50000.0, within(1e-9));
        assertThat(vMem.toArray()).hasSameSizeAs(vStream.toArray());
    }

    @Test
    void testTotalOpsNormalizedByDays() {
        SnapdiffFile file = makeFile(
            rec("/vol/share/user1/docs/a.txt", "deleted", 1000, Instant.parse("2026-04-28T09:00:00Z")),
            rec("/vol/share/user2/projects/b.txt", "added", 200, Instant.parse("2026-04-28T10:00:00Z")),
            rec("/vol/share/user3/data/c.py", "added", 300, Instant.parse("2026-04-28T11:00:00Z"))
        );

        RansomwareFeatureExtractor ext1 = new RansomwareFeatureExtractor(null, 1.0);
        RansomwareFeatureExtractor ext7 = new RansomwareFeatureExtractor(null, 7.0);

        double[] v1 = ext1.extract(file).toArray();
        double[] v7 = ext7.extract(file).toArray();

        assertThat(v1[1] / v7[1]).isCloseTo(7.0, within(1e-9));

        assertThat(v1[0]).isCloseTo(v7[0], within(1e-9));
    }

    @Test
    void testClampMinDays() {
        RansomwareFeatureExtractor ext = new RansomwareFeatureExtractor(null, 0.01);
        assertThat(ext.getDaysBetweenSnapshots()).isCloseTo(0.25, within(1e-9));
    }

    @Test
    void testDefaultDaysBetweenIsTwo() {
        RansomwareFeatureExtractor ext = new RansomwareFeatureExtractor(null);
        assertThat(ext.getDaysBetweenSnapshots()).isCloseTo(2.0, within(1e-9));
    }
}
