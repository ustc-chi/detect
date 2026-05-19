package com.anomalydetection.features;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BurstDataFile implements Closeable {
    private final Path tempFile;
    private final BufferedWriter writer;
    private boolean closed = false;

    public static BurstDataFile create() throws IOException {
        Path tmp = Files.createTempFile("burstdata-", ".txt");
        BufferedWriter w = Files.newBufferedWriter(tmp);
        return new BurstDataFile(tmp, w);
    }

    private BurstDataFile(Path tempFile, BufferedWriter writer) {
        this.tempFile = tempFile;
        this.writer = writer;
    }

    public void write(long epochSeconds, String opType) throws IOException {
        if (closed) {
            throw new IOException("BurstDataFile is closed");
        }
        writer.write(Long.toString(epochSeconds));
        writer.write("\t");
        writer.write(opType);
        writer.write("\n");
    }

    public List<OpEntry> sortAndRead() throws IOException {
        writer.flush();
        List<OpEntry> entries = new ArrayList<>();
        List<String> lines = Files.readAllLines(tempFile);
        for (String line : lines) {
            if (line == null || line.trim().isEmpty()) continue;
            String[] parts = line.split("\\t", 2);
            long epoch = Long.parseLong(parts[0]);
            String opType = parts.length > 1 ? parts[1] : "";
            entries.add(new OpEntry(epoch, opType));
        }
        entries.sort(Comparator.comparingLong(OpEntry::epochSeconds));
        return entries;
    }

    public BurstFeatures computeBurstFeatures() throws IOException {
        List<OpEntry> sorted = sortAndRead();
        if (sorted.size() < 2) {
            return new BurstFeatures(0.0, 0.0, 0.0, 0L, 0L, 0.0);
        }

        long windowSecs = 300L;

        int tail = 0;
        int maxInWindow = 1;
        for (int head = 1; head < sorted.size(); head++) {
            while (sorted.get(head).epochSeconds() - sorted.get(tail).epochSeconds() > windowSecs) {
                tail++;
            }
            int count = head - tail + 1;
            if (count > maxInWindow) maxInWindow = count;
        }
        double peakBurstVelocity = maxInWindow / (windowSecs / 3600.0);

        tail = 0;
        int bestStart = 0, bestEnd = 0, bestCount = 0;
        for (int head = 0; head < sorted.size(); head++) {
            while (sorted.get(head).epochSeconds() - sorted.get(tail).epochSeconds() > windowSecs) {
                tail++;
            }
            int count = head - tail + 1;
            if (count > bestCount) {
                bestCount = count;
                bestStart = tail;
                bestEnd = head;
            }
        }
        int modsInWindow = 0;
        for (int i = bestStart; i <= bestEnd; i++) {
            if ("modified".equalsIgnoreCase(sorted.get(i).opType())) modsInWindow++;
        }
        double burstModPurity = bestCount >= 2 ? ((double) modsInWindow / bestCount) : 0.0;

        long burstWindowStart = sorted.get(bestStart).epochSeconds();
        long burstWindowEnd = sorted.get(bestEnd).epochSeconds();

        double interOpTimeCvBurst = 0.0;
        if (bestCount >= 2) {
            double[] burstDeltas = new double[bestEnd - bestStart];
            for (int i = bestStart; i < bestEnd; i++) {
                burstDeltas[i - bestStart] = sorted.get(i + 1).epochSeconds() - sorted.get(i).epochSeconds();
            }
            double burstDeltaMean = 0.0;
            for (double d : burstDeltas) burstDeltaMean += d;
            burstDeltaMean /= burstDeltas.length;
            if (burstDeltaMean >= 0.001) {
                double burstDeltaVar = 0.0;
                for (double d : burstDeltas) { double dd = d - burstDeltaMean; burstDeltaVar += dd * dd; }
                burstDeltaVar /= burstDeltas.length;
                interOpTimeCvBurst = Math.sqrt(burstDeltaVar) / burstDeltaMean;
            }
        }

        double temporalUniformity = 0.0;
        long spanSecs = sorted.get(sorted.size() - 1).epochSeconds() - sorted.get(0).epochSeconds();
        if (spanSecs > 0) {
            int numBins = (int) (spanSecs / windowSecs) + 1;
            if (numBins >= 3) {
                long baseEpoch = sorted.get(0).epochSeconds();
                double[] binCounts = new double[numBins];
                for (OpEntry entry : sorted) {
                    int bin = (int) ((entry.epochSeconds() - baseEpoch) / windowSecs);
                    if (bin >= 0 && bin < numBins) binCounts[bin]++;
                }
                double binMean = 0.0;
                for (double c : binCounts) binMean += c;
                binMean /= binCounts.length;
                if (binMean >= 0.001) {
                    double binVar = 0.0;
                    for (double c : binCounts) { double d = c - binMean; binVar += d * d; }
                    binVar /= binCounts.length;
                    double binStdDev = Math.sqrt(binVar);
                    temporalUniformity = 1.0 - (binStdDev / binMean);
                }
            }
        }

        return new BurstFeatures(peakBurstVelocity, burstModPurity, interOpTimeCvBurst,
                burstWindowStart, burstWindowEnd, temporalUniformity);
    }

    @Override
    public void close() throws IOException {
        if (closed) return;
        try {
            writer.flush();
        } finally {
            writer.close();
            Files.deleteIfExists(tempFile);
            closed = true;
        }
    }

    Path getTempFilePath() {
        return tempFile;
    }

    public static final class OpEntry {
        private final long epochSeconds;
        private final String opType;

        public OpEntry(long epochSeconds, String opType) {
            this.epochSeconds = epochSeconds;
            this.opType = opType;
        }

        public long epochSeconds() { return epochSeconds; }
        public String opType() { return opType; }
    }

    public static final class BurstFeatures {
        private final double peakBurstVelocity;
        private final double burstModPurity;
        private final double interOpTimeCvBurst;
        private final long burstWindowStart;
        private final long burstWindowEnd;
        private final double temporalUniformity;

        public BurstFeatures(double peakBurstVelocity, double burstModPurity, double interOpTimeCvBurst,
                             long burstWindowStart, long burstWindowEnd, double temporalUniformity) {
            this.peakBurstVelocity = peakBurstVelocity;
            this.burstModPurity = burstModPurity;
            this.interOpTimeCvBurst = interOpTimeCvBurst;
            this.burstWindowStart = burstWindowStart;
            this.burstWindowEnd = burstWindowEnd;
            this.temporalUniformity = temporalUniformity;
        }

        public double peakBurstVelocity() { return peakBurstVelocity; }
        public double burstModPurity() { return burstModPurity; }
        public double interOpTimeCvBurst() { return interOpTimeCvBurst; }
        public long burstWindowStart() { return burstWindowStart; }
        public long burstWindowEnd() { return burstWindowEnd; }
        public double temporalUniformity() { return temporalUniformity; }
    }
}
