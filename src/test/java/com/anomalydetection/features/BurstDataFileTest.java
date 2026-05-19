package com.anomalydetection.features;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class BurstDataFileTest {

    @Test
    void testSortOrderUnsortedInput() throws IOException {
        BurstDataFile f = BurstDataFile.create();
        try {
            f.write(300L, "modified");
            f.write(100L, "deleted");
            f.write(200L, "modified");
            List<BurstDataFile.OpEntry> sorted = f.sortAndRead();
            assertThat(sorted).extracting(BurstDataFile.OpEntry::epochSeconds).containsExactly(100L, 200L, 300L);
        } finally {
            f.close();
        }
    }

    @Test
    void testDuplicateTimestampsArePreserved() throws IOException {
        BurstDataFile f = BurstDataFile.create();
        try {
            f.write(100L, "modified");
            f.write(100L, "deleted");
            f.write(100L, "modified");
            List<BurstDataFile.OpEntry> sorted = f.sortAndRead();
            assertThat(sorted).hasSize(3);
            long count100 = sorted.stream().filter(e -> e.epochSeconds() == 100L).count();
            assertThat(count100).isEqualTo(3);
        } finally {
            f.close();
        }
    }

    @Test
    void testFewRecordsReturnsZeroBurstFeatures() throws IOException {
        BurstDataFile f = BurstDataFile.create();
        try {
            f.write(1L, "modified");
            BurstDataFile.BurstFeatures bf = f.computeBurstFeatures();
            assertThat(bf.peakBurstVelocity()).isCloseTo(0.0, org.assertj.core.data.Offset.offset(0.0001));
            assertThat(bf.burstModPurity()).isCloseTo(0.0, org.assertj.core.data.Offset.offset(0.0001));
        } finally {
            f.close();
        }
    }

    @Test
    void testCleanupOnClose(@TempDir Path tempDir) throws IOException {
        BurstDataFile f = BurstDataFile.create();
        try {
            f.write(10L, "modified");
            Path p = f.getTempFilePath();
            assertThat(Files.exists(p)).isTrue();
        } finally {
            f.close();
        }
        Path p2 = f.getTempFilePath();
        assertThat(Files.notExists(p2)).isTrue();
    }

    @Test
    void testBurstComputationCorrectness() throws IOException {
        BurstDataFile f = BurstDataFile.create();
        try {
            for (int i = 0; i < 500; i++) {
                long t = i % 300L;
                String op = (i < 400) ? "modified" : "deleted";
                f.write(t, op);
            }
            BurstDataFile.BurstFeatures bf = f.computeBurstFeatures();
            assertThat(bf.peakBurstVelocity()).isCloseTo(6000.0, org.assertj.core.data.Offset.offset(0.0001));
            assertThat(bf.burstModPurity()).isCloseTo(0.8, org.assertj.core.data.Offset.offset(0.0001));
        } finally {
            f.close();
        }
    }
}
