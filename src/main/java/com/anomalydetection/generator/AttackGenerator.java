package com.anomalydetection.generator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AttackGenerator {
    private final FilesystemState state;
    private final Random random;

    private static final int MIN_TOTAL_OPS = 5000;

    public AttackGenerator(FilesystemState state, long seed) {
        this.state = state;
        this.random = new Random(seed);
    }

    /**
     * Compute padding count from attack ops and desired padding ratio.
     * paddingRatio = fraction of total ops that should be normal padding.
     * Ensures minimum total of MIN_TOTAL_OPS.
     */
    private int computePadding(int attackOps, double paddingRatio) {
        int padFromRatio = (int) (attackOps * paddingRatio / (1.0 - paddingRatio));
        int totalWithRatio = attackOps + padFromRatio;
        if (totalWithRatio < MIN_TOTAL_OPS) {
            return Math.max(0, MIN_TOTAL_OPS - attackOps);
        }
        return padFromRatio;
    }

    // 1) LockBit Fast Mode: 4KB per file, 90-120s burst, no ext change
    public List<DiffEntry> generateLockBitFastMode(Instant attackTime, double paddingRatio) {
        int targetMods = 4000 + random.nextInt(2001);
        int padTarget = computePadding(targetMods, paddingRatio);
        List<DiffEntry> diffs = new ArrayList<>(generateNormalPadding(padTarget, attackTime));

        List<FilesystemState.FileInfo> all = new ArrayList<>(state.getAllFiles());
        int limit = Math.min(targetMods, all.size());
        int burstWindow = 90 + random.nextInt(31);
        for (int i = 0; i < limit; i++) {
            int idx = random.nextInt(all.size());
            FilesystemState.FileInfo fi = all.get(idx);
            if (state.getFile(fi.path) == null) continue;
            long added = 4096L + random.nextInt(4096);
            long newSize = fi.size + added;
            FilesystemState.FileInfo updated = new FilesystemState.FileInfo(fi.path, fi.extension, newSize, fi.userIndex);
            state.addFile(fi.path, updated);
            all.set(idx, updated);
            String changeTime = attackTime.plusSeconds(random.nextInt(burstWindow)).toString();
            diffs.add(new DiffEntry(updated.path, "modified", String.valueOf(newSize), changeTime));
        }
        diffs.addAll(FilesystemState.generateDirectoryMtimeEntries(diffs));
        return diffs;
    }

    // 2) Conti Size-Tiered: small full, medium header, large partial, no ext change
    public List<DiffEntry> generateContiSizeTiered(Instant attackTime, double paddingRatio) {
        int targetMods = 3000 + random.nextInt(1001);
        int padTarget = computePadding(targetMods, paddingRatio);
        List<DiffEntry> diffs = new ArrayList<>(generateNormalPadding(padTarget, attackTime));

        List<FilesystemState.FileInfo> all = new ArrayList<>(state.getAllFiles());
        int limit = Math.min(targetMods, all.size());
        int burstWindow = 300 + random.nextInt(301);
        for (int i = 0; i < limit; i++) {
            int idx = random.nextInt(all.size());
            FilesystemState.FileInfo fi = all.get(idx);
            if (state.getFile(fi.path) == null) continue;
            long newSize;
            if (fi.size < 1048576L) {
                newSize = (long) (fi.size * (1.03 + random.nextDouble() * 0.02));
            } else if (fi.size < 5242880L) {
                newSize = fi.size + 4096L + random.nextInt(4096);
            } else {
                newSize = (long) (fi.size * (1.01 + random.nextDouble() * 0.02));
            }
            if (newSize < 1024L) newSize = 1024L;
            FilesystemState.FileInfo updated = new FilesystemState.FileInfo(fi.path, fi.extension, newSize, fi.userIndex);
            state.addFile(fi.path, updated);
            all.set(idx, updated);
            String changeTime = attackTime.plusSeconds(random.nextInt(burstWindow)).toString();
            diffs.add(new DiffEntry(updated.path, "modified", String.valueOf(newSize), changeTime));
        }
        diffs.addAll(FilesystemState.generateDirectoryMtimeEntries(diffs));
        return diffs;
    }

    // 4) Database-Priority: .db/.sql/.mdb/.bak/.csv first, then .docx/.xlsx/.pdf, no ext change
    public List<DiffEntry> generateDatabasePriority(Instant attackTime, double paddingRatio) {
        String[] dbExts = {".db", ".sql", ".mdb", ".bak", ".csv"};
        String[] docExts = {".docx", ".xlsx", ".pdf"};
        int maxTarget = 2000 + random.nextInt(1001);
        int burstWindow = 300;
        int count = 0;
        List<DiffEntry> attackDiffs = new ArrayList<>();

        List<FilesystemState.FileInfo> dbFiles = new ArrayList<>();
        for (FilesystemState.FileInfo fi : state.getAllFiles()) {
            for (String ext : dbExts) {
                if (fi.path.endsWith(ext)) { dbFiles.add(fi); break; }
            }
        }
        for (int i = 0; i < dbFiles.size() && count < maxTarget; i++) {
            int idx = random.nextInt(dbFiles.size());
            FilesystemState.FileInfo fi = dbFiles.get(idx);
            if (state.getFile(fi.path) == null || fi.size <= 0) continue;
            long newSize = (long) (fi.size * (1.03 + random.nextDouble() * 0.02));
            if (newSize < 1024L) newSize = 1024L;
            FilesystemState.FileInfo updated = new FilesystemState.FileInfo(fi.path, fi.extension, newSize, fi.userIndex);
            state.addFile(fi.path, updated);
            dbFiles.set(idx, updated);
            String changeTime = attackTime.plusSeconds(random.nextInt(burstWindow)).toString();
            attackDiffs.add(new DiffEntry(updated.path, "modified", String.valueOf(newSize), changeTime));
            count++;
        }

        List<FilesystemState.FileInfo> docFiles = new ArrayList<>();
        for (FilesystemState.FileInfo fi : state.getAllFiles()) {
            for (String ext : docExts) {
                if (fi.path.endsWith(ext)) { docFiles.add(fi); break; }
            }
        }
        for (int i = 0; i < docFiles.size() && count < maxTarget; i++) {
            int idx = random.nextInt(docFiles.size());
            FilesystemState.FileInfo fi = docFiles.get(idx);
            if (state.getFile(fi.path) == null || fi.size <= 0) continue;
            long newSize = (long) (fi.size * (1.03 + random.nextDouble() * 0.02));
            if (newSize < 1024L) newSize = 1024L;
            FilesystemState.FileInfo updated = new FilesystemState.FileInfo(fi.path, fi.extension, newSize, fi.userIndex);
            state.addFile(fi.path, updated);
            docFiles.set(idx, updated);
            String changeTime = attackTime.plusSeconds(random.nextInt(burstWindow)).toString();
            attackDiffs.add(new DiffEntry(updated.path, "modified", String.valueOf(newSize), changeTime));
            count++;
        }

        int padTarget = computePadding(count, paddingRatio);
        List<DiffEntry> diffs = new ArrayList<>(generateNormalPadding(padTarget, attackTime));
        diffs.addAll(attackDiffs);
        diffs.addAll(FilesystemState.generateDirectoryMtimeEntries(diffs));
        return diffs;
    }

    // 5) Single-User Rapid: one user's files, 30-60s extreme burst
    public List<DiffEntry> generateSingleUserRapid(Instant attackTime, double paddingRatio) {
        int user = 1 + random.nextInt(10);
        List<FilesystemState.FileInfo> userFiles = new ArrayList<>();
        for (FilesystemState.FileInfo fi : state.getAllFiles()) {
            if (fi.userIndex == user) userFiles.add(fi);
        }
        int burstWindow = 30 + random.nextInt(31);
        List<DiffEntry> attackDiffs = new ArrayList<>();
        for (FilesystemState.FileInfo fi : userFiles) {
            if (state.getFile(fi.path) == null) continue;
            long newSize = (long) (fi.size * (1.02 + random.nextDouble() * 0.03));
            if (newSize < 1024L) newSize = 1024L;
            FilesystemState.FileInfo updated = new FilesystemState.FileInfo(fi.path, fi.extension, newSize, fi.userIndex);
            state.addFile(fi.path, updated);
            String changeTime = attackTime.plusSeconds(random.nextInt(burstWindow)).toString();
            attackDiffs.add(new DiffEntry(updated.path, "modified", String.valueOf(newSize), changeTime));
        }

        int padTarget = computePadding(attackDiffs.size(), paddingRatio);
        List<DiffEntry> diffs = new ArrayList<>(generateNormalPadding(padTarget, attackTime));
        diffs.addAll(attackDiffs);
        diffs.addAll(FilesystemState.generateDirectoryMtimeEntries(diffs));
        return diffs;
    }

    // 6) Slow Distributed: micro-bursts of ~100 ops in 10s windows with 60-90s gaps
    public List<DiffEntry> generateSlowDistributed(Instant attackTime, double paddingRatio) {
        int totalAttackOps = 2000;
        List<FilesystemState.FileInfo> all = new ArrayList<>(state.getAllFiles());
        int opsPerGroup = 100;
        int numGroups = totalAttackOps / opsPerGroup;
        long t = 0;
        List<DiffEntry> attackDiffs = new ArrayList<>();

        for (int g = 0; g < numGroups && attackDiffs.size() < totalAttackOps; g++) {
            long groupStart = t;
            for (int j = 0; j < opsPerGroup && attackDiffs.size() < totalAttackOps; j++) {
                int idx = random.nextInt(all.size());
                FilesystemState.FileInfo fi = all.get(idx);
                if (state.getFile(fi.path) == null) continue;
                long newSize = (long) (fi.size * (1.02 + random.nextDouble() * 0.02));
                if (newSize < 1024L) newSize = 1024L;
                FilesystemState.FileInfo updated = new FilesystemState.FileInfo(fi.path, fi.extension, newSize, fi.userIndex);
                state.addFile(fi.path, updated);
                all.set(idx, updated);
                long offset = groupStart + random.nextInt(10);
                String changeTime = attackTime.plusSeconds(offset).toString();
                attackDiffs.add(new DiffEntry(updated.path, "modified", String.valueOf(newSize), changeTime));
            }
            t += 60 + random.nextInt(31);
        }

        int padTarget = computePadding(attackDiffs.size(), paddingRatio);
        List<DiffEntry> diffs = new ArrayList<>(generateNormalPadding(padTarget, attackTime));
        diffs.addAll(attackDiffs);
        diffs.addAll(FilesystemState.generateDirectoryMtimeEntries(diffs));
        return diffs;
    }

    // 7) Creeping Shrink: -10% to -20%, no ext change
    public List<DiffEntry> generateCreepingShrink(Instant attackTime, double paddingRatio) {
        int targetOps = 2500;
        List<FilesystemState.FileInfo> all = new ArrayList<>(state.getAllFiles());
        int limit = Math.min(targetOps, all.size());
        int burstWindow = 300;
        List<DiffEntry> attackDiffs = new ArrayList<>();

        for (int i = 0; i < limit; i++) {
            int idx = random.nextInt(all.size());
            FilesystemState.FileInfo fi = all.get(idx);
            if (state.getFile(fi.path) == null) continue;
            double factor = 0.80 + random.nextDouble() * 0.10;
            long newSize = (long) (fi.size * factor);
            if (newSize < 512L) newSize = 512L;
            FilesystemState.FileInfo updated = new FilesystemState.FileInfo(fi.path, fi.extension, newSize, fi.userIndex);
            state.addFile(fi.path, updated);
            all.set(idx, updated);
            String changeTime = attackTime.plusSeconds(random.nextInt(burstWindow)).toString();
            attackDiffs.add(new DiffEntry(updated.path, "modified", String.valueOf(newSize), changeTime));
        }

        int padTarget = computePadding(attackDiffs.size(), paddingRatio);
        List<DiffEntry> diffs = new ArrayList<>(generateNormalPadding(padTarget, attackTime));
        diffs.addAll(attackDiffs);
        diffs.addAll(FilesystemState.generateDirectoryMtimeEntries(diffs));
        return diffs;
    }

    // 8) REvil Random Extension: random 8-char lowercase alphanumeric ext
    public List<DiffEntry> generateRevilRandomExt(Instant attackTime, double paddingRatio) {
        int targetMods = 2000 + random.nextInt(1001);
        List<FilesystemState.FileInfo> all = new ArrayList<>(state.getAllFiles());
        int limit = Math.min(targetMods, all.size());
        int burstWindow = 180;
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        List<DiffEntry> attackDiffs = new ArrayList<>();

        for (int i = 0; i < limit; i++) {
            int idx = random.nextInt(all.size());
            FilesystemState.FileInfo fi = all.remove(idx);
            String oldPath = fi.path;
            StringBuilder ext = new StringBuilder(".");
            for (int c = 0; c < 8; c++) ext.append(chars.charAt(random.nextInt(chars.length())));
            String newPath = oldPath + ext;
            long newSize = (long) (fi.size * (1.02 + random.nextDouble() * 0.03));
            if (newSize < 1024L) newSize = 1024L;
            FilesystemState.FileInfo renamed = new FilesystemState.FileInfo(newPath, fi.extension, newSize, fi.userIndex);
            state.removeFile(oldPath);
            state.addFile(newPath, renamed);
            String changeTime = attackTime.plusSeconds(random.nextInt(burstWindow)).toString();
            attackDiffs.add(new DiffEntry(newPath, "modified", String.valueOf(newSize), changeTime));
        }

        int padTarget = computePadding(attackDiffs.size(), paddingRatio);
        List<DiffEntry> diffs = new ArrayList<>(generateNormalPadding(padTarget, attackTime));
        diffs.addAll(attackDiffs);
        diffs.addAll(FilesystemState.generateDirectoryMtimeEntries(diffs));
        return diffs;
    }

    // 9) Cl0p Companion: in-place mod + .key companion files
    public List<DiffEntry> generateClopCompanion(Instant attackTime, double paddingRatio) {
        int targetMods = 2000;
        List<FilesystemState.FileInfo> all = new ArrayList<>(state.getAllFiles());
        int limit = Math.min(targetMods, all.size());
        int burstWindow = 300;
        List<DiffEntry> attackDiffs = new ArrayList<>();

        for (int i = 0; i < limit; i++) {
            int idx = random.nextInt(all.size());
            FilesystemState.FileInfo fi = all.get(idx);
            if (state.getFile(fi.path) == null) continue;
            long newSize = (long) (fi.size * (1.01 + random.nextDouble() * 0.02));
            if (newSize < 1024L) newSize = 1024L;
            FilesystemState.FileInfo updated = new FilesystemState.FileInfo(fi.path, fi.extension, newSize, fi.userIndex);
            state.addFile(fi.path, updated);
            all.set(idx, updated);
            String changeTime = attackTime.plusSeconds(random.nextInt(burstWindow)).toString();
            attackDiffs.add(new DiffEntry(updated.path, "modified", String.valueOf(newSize), changeTime));

            String keyPath = fi.path + ".key";
            long keySize = 200L + random.nextInt(300);
            FilesystemState.FileInfo keyInfo = new FilesystemState.FileInfo(keyPath, "key", keySize, fi.userIndex);
            state.addFile(keyPath, keyInfo);
            attackDiffs.add(new DiffEntry(keyPath, "added", String.valueOf(keySize), changeTime));
        }

        int padTarget = computePadding(attackDiffs.size(), paddingRatio);
        List<DiffEntry> diffs = new ArrayList<>(generateNormalPadding(padTarget, attackTime));
        diffs.addAll(attackDiffs);
        diffs.addAll(FilesystemState.generateDirectoryMtimeEntries(diffs));
        return diffs;
    }

    // 10) WannaCry Staged: rename to .WNCRY
    public List<DiffEntry> generateWannaCryStaged(Instant attackTime, double paddingRatio) {
        int targetMods = 2000 + random.nextInt(1001);
        List<FilesystemState.FileInfo> all = new ArrayList<>(state.getAllFiles());
        int limit = Math.min(targetMods, all.size());
        int burstWindow = 300;
        List<DiffEntry> attackDiffs = new ArrayList<>();

        for (int i = 0; i < limit; i++) {
            int idx = random.nextInt(all.size());
            FilesystemState.FileInfo fi = all.remove(idx);
            String oldPath = fi.path;
            String newPath = oldPath + ".WNCRY";
            long newSize = (long) (fi.size * (1.02 + random.nextDouble() * 0.01));
            if (newSize < 1024L) newSize = 1024L;
            FilesystemState.FileInfo renamed = new FilesystemState.FileInfo(newPath, fi.extension, newSize, fi.userIndex);
            state.removeFile(oldPath);
            state.addFile(newPath, renamed);
            String changeTime = attackTime.plusSeconds(random.nextInt(burstWindow)).toString();
            attackDiffs.add(new DiffEntry(newPath, "modified", String.valueOf(newSize), changeTime));
        }

        int padTarget = computePadding(attackDiffs.size(), paddingRatio);
        List<DiffEntry> diffs = new ArrayList<>(generateNormalPadding(padTarget, attackTime));
        diffs.addAll(attackDiffs);
        diffs.addAll(FilesystemState.generateDirectoryMtimeEntries(diffs));
        return diffs;
    }


    // B1) Backup Disguise: spread over 2-4 hours, uniform +2-4%, no ext change
    // Evasion: defeats peak_burst_velocity by mimicking backup timing
    public List<DiffEntry> generateBackupDisguise(Instant attackTime, double paddingRatio) {
        int targetMods = 3000 + random.nextInt(2001);
        List<FilesystemState.FileInfo> all = new ArrayList<>(state.getAllFiles());
        int limit = Math.min(targetMods, all.size());
        int totalSeconds = 7200 + random.nextInt(7201);
        List<DiffEntry> attackDiffs = new ArrayList<>();

        for (int i = 0; i < limit; i++) {
            int idx = random.nextInt(all.size());
            FilesystemState.FileInfo fi = all.get(idx);
            if (state.getFile(fi.path) == null) continue;
            long newSize = (long) (fi.size * (1.02 + random.nextDouble() * 0.02));
            if (newSize < 1024L) newSize = 1024L;
            FilesystemState.FileInfo updated = new FilesystemState.FileInfo(fi.path, fi.extension, newSize, fi.userIndex);
            state.addFile(fi.path, updated);
            all.set(idx, updated);
            String changeTime = attackTime.plusSeconds(random.nextInt(totalSeconds)).toString();
            attackDiffs.add(new DiffEntry(updated.path, "modified", String.valueOf(newSize), changeTime));
        }

        int padTarget = computePadding(attackDiffs.size(), paddingRatio);
        List<DiffEntry> diffs = new ArrayList<>(generateNormalPadding(padTarget, attackTime));
        diffs.addAll(attackDiffs);
        diffs.addAll(FilesystemState.generateDirectoryMtimeEntries(diffs));
        return diffs;
    }

    // B2) Slow Drip Encrypt: ~50 ops / 5min over 6 hours, +1-3%
    // Evasion: never produces a dense 5-min window for burst detection
    public List<DiffEntry> generateSlowDripEncrypt(Instant attackTime, double paddingRatio) {
        int totalOps = 3600;
        int opsPerBatch = 50;
        int batchIntervalSec = 300;
        int numBatches = totalOps / opsPerBatch;
        List<FilesystemState.FileInfo> all = new ArrayList<>(state.getAllFiles());
        List<DiffEntry> attackDiffs = new ArrayList<>();
        int count = 0;

        for (int b = 0; b < numBatches && count < totalOps; b++) {
            long batchStart = (long) b * batchIntervalSec;
            for (int j = 0; j < opsPerBatch && count < totalOps; j++) {
                int idx = random.nextInt(all.size());
                FilesystemState.FileInfo fi = all.get(idx);
                if (state.getFile(fi.path) == null) continue;
                long newSize = (long) (fi.size * (1.01 + random.nextDouble() * 0.02));
                if (newSize < 1024L) newSize = 1024L;
                FilesystemState.FileInfo updated = new FilesystemState.FileInfo(fi.path, fi.extension, newSize, fi.userIndex);
                state.addFile(fi.path, updated);
                all.set(idx, updated);
                long offset = batchStart + random.nextInt(batchIntervalSec);
                String changeTime = attackTime.plusSeconds(offset).toString();
                attackDiffs.add(new DiffEntry(updated.path, "modified", String.valueOf(newSize), changeTime));
                count++;
            }
        }

        int padTarget = computePadding(attackDiffs.size(), paddingRatio);
        List<DiffEntry> diffs = new ArrayList<>(generateNormalPadding(padTarget, attackTime));
        diffs.addAll(attackDiffs);
        diffs.addAll(FilesystemState.generateDirectoryMtimeEntries(diffs));
        return diffs;
    }

    // B3) Random Jitter Burst: 1-15s jitter with 10% chance of 30-60s pause, +2-5%
    // Evasion: random timing dilutes burst_mod_purity
    public List<DiffEntry> generateRandomJitterBurst(Instant attackTime, double paddingRatio) {
        int targetMods = 2000 + random.nextInt(1001);
        List<FilesystemState.FileInfo> all = new ArrayList<>(state.getAllFiles());
        int limit = Math.min(targetMods, all.size());
        List<DiffEntry> attackDiffs = new ArrayList<>();
        long t = 0;

        for (int i = 0; i < limit; i++) {
            int idx = random.nextInt(all.size());
            FilesystemState.FileInfo fi = all.get(idx);
            if (state.getFile(fi.path) == null) continue;
            long newSize = (long) (fi.size * (1.02 + random.nextDouble() * 0.03));
            if (newSize < 1024L) newSize = 1024L;
            FilesystemState.FileInfo updated = new FilesystemState.FileInfo(fi.path, fi.extension, newSize, fi.userIndex);
            state.addFile(fi.path, updated);
            all.set(idx, updated);
            t += (random.nextDouble() < 0.10) ? (30 + random.nextInt(31)) : (1 + random.nextInt(15));
            String changeTime = attackTime.plusSeconds(t).toString();
            attackDiffs.add(new DiffEntry(updated.path, "modified", String.valueOf(newSize), changeTime));
        }

        int padTarget = computePadding(attackDiffs.size(), paddingRatio);
        List<DiffEntry> diffs = new ArrayList<>(generateNormalPadding(padTarget, attackTime));
        diffs.addAll(attackDiffs);
        diffs.addAll(FilesystemState.generateDirectoryMtimeEntries(diffs));
        return diffs;
    }

    // B4) Mixed Operation Mask: 80% modify, 10% add, 10% delete, +1-3%
    // Evasion: defeats modification_ratio by keeping it at ~0.8 instead of ~1.0
    public List<DiffEntry> generateMixedOperationMask(Instant attackTime, double paddingRatio) {
        int totalOps = 2000 + random.nextInt(1001);
        List<FilesystemState.FileInfo> all = new ArrayList<>(state.getAllFiles());
        int burstWindow = 180 + random.nextInt(121);
        List<DiffEntry> attackDiffs = new ArrayList<>();
        String[] dirs = {"projects","docs","mail","logs","configs","media","backup","archive","temp","data"};
        String[] exts = {"docx","xlsx","pptx","pdf","txt","log","conf","sh","py","java",
                "cpp","h","md","json","xml","sql","jpg","png","gif","mp3","mp4","wav","zip","tar","gz","db","yaml","csv"};

        for (int i = 0; i < totalOps; i++) {
            double roll = random.nextDouble();
            if (roll < 0.80) {
                int idx = random.nextInt(all.size());
                FilesystemState.FileInfo fi = all.get(idx);
                if (state.getFile(fi.path) == null) continue;
                long newSize = (long) (fi.size * (1.01 + random.nextDouble() * 0.02));
                if (newSize < 1024L) newSize = 1024L;
                FilesystemState.FileInfo updated = new FilesystemState.FileInfo(fi.path, fi.extension, newSize, fi.userIndex);
                state.addFile(fi.path, updated);
                all.set(idx, updated);
                String changeTime = attackTime.plusSeconds(random.nextInt(burstWindow)).toString();
                attackDiffs.add(new DiffEntry(updated.path, "modified", String.valueOf(newSize), changeTime));
            } else if (roll < 0.90) {
                int user = 1 + random.nextInt(10);
                String dir = dirs[random.nextInt(dirs.length)];
                String ext = exts[random.nextInt(exts.length)];
                String path = String.format("/vol/share/user%d/%s/file_b4_%d.%s", user, dir, random.nextInt(100000), ext);
                long size = 1024L * (1L + random.nextInt(100));
                FilesystemState.FileInfo info = new FilesystemState.FileInfo(path, ext, size, user);
                state.addFile(path, info);
                String changeTime = attackTime.plusSeconds(random.nextInt(burstWindow)).toString();
                attackDiffs.add(new DiffEntry(path, "added", String.valueOf(size), changeTime));
            } else {
                if (all.isEmpty()) continue;
                int idx = random.nextInt(all.size());
                FilesystemState.FileInfo fi = all.get(idx);
                if (state.getFile(fi.path) == null) continue;
                state.removeFile(fi.path);
                attackDiffs.add(new DiffEntry(fi.path, "deleted", String.valueOf(fi.size), Instant.MAX.toString()));
            }
        }

        int padTarget = computePadding(attackDiffs.size(), paddingRatio);
        List<DiffEntry> diffs = new ArrayList<>(generateNormalPadding(padTarget, attackTime));
        diffs.addAll(attackDiffs);
        diffs.addAll(FilesystemState.generateDirectoryMtimeEntries(diffs));
        return diffs;
    }

    // B5) Size Mimic Normal: random -10% to +10% per file, maintains high variance
    // Evasion: defeats size_std_dev by keeping variance similar to normal activity
    public List<DiffEntry> generateSizeMimicNormal(Instant attackTime, double paddingRatio) {
        int targetMods = 2000 + random.nextInt(1001);
        List<FilesystemState.FileInfo> all = new ArrayList<>(state.getAllFiles());
        int limit = Math.min(targetMods, all.size());
        int burstWindow = 180 + random.nextInt(121);
        List<DiffEntry> attackDiffs = new ArrayList<>();

        for (int i = 0; i < limit; i++) {
            int idx = random.nextInt(all.size());
            FilesystemState.FileInfo fi = all.get(idx);
            if (state.getFile(fi.path) == null) continue;
            double delta = (random.nextDouble() * 0.20) - 0.10;
            long newSize = (long) (fi.size * (1.0 + delta));
            if (newSize < 1024L) newSize = 1024L;
            FilesystemState.FileInfo updated = new FilesystemState.FileInfo(fi.path, fi.extension, newSize, fi.userIndex);
            state.addFile(fi.path, updated);
            all.set(idx, updated);
            String changeTime = attackTime.plusSeconds(random.nextInt(burstWindow)).toString();
            attackDiffs.add(new DiffEntry(updated.path, "modified", String.valueOf(newSize), changeTime));
        }

        int padTarget = computePadding(attackDiffs.size(), paddingRatio);
        List<DiffEntry> diffs = new ArrayList<>(generateNormalPadding(padTarget, attackTime));
        diffs.addAll(attackDiffs);
        diffs.addAll(FilesystemState.generateDirectoryMtimeEntries(diffs));
        return diffs;
    }

    // B6) Selective High Value: only .docx/.xlsx/.pdf/.db/.sql, 200-500 ops
    // Evasion: defeats total_operations and directory_spread with low count
    public List<DiffEntry> generateSelectiveHighValue(Instant attackTime, double paddingRatio) {
        String[] hvExts = {".docx", ".xlsx", ".pdf", ".db", ".sql"};
        int targetMods = 200 + random.nextInt(301);
        int burstWindow = 30 + random.nextInt(91);
        List<FilesystemState.FileInfo> targets = new ArrayList<>();
        for (FilesystemState.FileInfo fi : state.getAllFiles()) {
            for (String ext : hvExts) {
                if (fi.path.endsWith(ext)) { targets.add(fi); break; }
            }
        }
        int limit = Math.min(targetMods, targets.size());
        List<DiffEntry> attackDiffs = new ArrayList<>();

        for (int i = 0; i < limit; i++) {
            int idx = random.nextInt(targets.size());
            FilesystemState.FileInfo fi = targets.get(idx);
            if (state.getFile(fi.path) == null) continue;
            long newSize = (long) (fi.size * (1.02 + random.nextDouble() * 0.03));
            if (newSize < 1024L) newSize = 1024L;
            FilesystemState.FileInfo updated = new FilesystemState.FileInfo(fi.path, fi.extension, newSize, fi.userIndex);
            state.addFile(fi.path, updated);
            targets.set(idx, updated);
            String changeTime = attackTime.plusSeconds(random.nextInt(burstWindow)).toString();
            attackDiffs.add(new DiffEntry(updated.path, "modified", String.valueOf(newSize), changeTime));
        }

        int padTarget = computePadding(attackDiffs.size(), paddingRatio);
        List<DiffEntry> diffs = new ArrayList<>(generateNormalPadding(padTarget, attackTime));
        diffs.addAll(attackDiffs);
        diffs.addAll(FilesystemState.generateDirectoryMtimeEntries(diffs));
        return diffs;
    }

    // B7) Multi-Family Combo: 50% uniform +2-3%, 50% append +4-8KB
    // Evasion: mixed size signals confuse size_std_dev and avg_modified_size
    public List<DiffEntry> generateMultiFamilyCombo(Instant attackTime, double paddingRatio) {
        int targetMods = 3000 + random.nextInt(2001);
        List<FilesystemState.FileInfo> all = new ArrayList<>(state.getAllFiles());
        int limit = Math.min(targetMods, all.size());
        int burstWindow = 180 + random.nextInt(121);
        List<DiffEntry> attackDiffs = new ArrayList<>();

        for (int i = 0; i < limit; i++) {
            int idx = random.nextInt(all.size());
            FilesystemState.FileInfo fi = all.get(idx);
            if (state.getFile(fi.path) == null) continue;
            long newSize;
            if (random.nextDouble() < 0.50) {
                newSize = (long) (fi.size * (1.02 + random.nextDouble() * 0.01));
            } else {
                newSize = fi.size + 4096L + random.nextInt(4096);
            }
            if (newSize < 1024L) newSize = 1024L;
            FilesystemState.FileInfo updated = new FilesystemState.FileInfo(fi.path, fi.extension, newSize, fi.userIndex);
            state.addFile(fi.path, updated);
            all.set(idx, updated);
            String changeTime = attackTime.plusSeconds(random.nextInt(burstWindow)).toString();
            attackDiffs.add(new DiffEntry(updated.path, "modified", String.valueOf(newSize), changeTime));
        }

        int padTarget = computePadding(attackDiffs.size(), paddingRatio);
        List<DiffEntry> diffs = new ArrayList<>(generateNormalPadding(padTarget, attackTime));
        diffs.addAll(attackDiffs);
        diffs.addAll(FilesystemState.generateDirectoryMtimeEntries(diffs));
        return diffs;
    }

    // B8) Rename and Encrypt: random filenames destroy extension info, +1-4%
    // Evasion: defeats extension_diversity and high_value_ext_ratio
    public List<DiffEntry> generateRenameAndEncrypt(Instant attackTime, double paddingRatio) {
        int targetMods = 2000 + random.nextInt(1001);
        List<FilesystemState.FileInfo> all = new ArrayList<>(state.getAllFiles());
        int limit = Math.min(targetMods, all.size());
        int burstWindow = 180 + random.nextInt(121);
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        List<DiffEntry> attackDiffs = new ArrayList<>();

        for (int i = 0; i < limit; i++) {
            int idx = random.nextInt(all.size());
            FilesystemState.FileInfo fi = all.remove(idx);
            String oldPath = fi.path;
            StringBuilder newName = new StringBuilder("file_");
            for (int c = 0; c < 8; c++) newName.append(chars.charAt(random.nextInt(chars.length())));
            int lastSlash = oldPath.lastIndexOf('/');
            String newPath = lastSlash >= 0 ? oldPath.substring(0, lastSlash + 1) + newName : newName.toString();
            long newSize = (long) (fi.size * (1.01 + random.nextDouble() * 0.03));
            if (newSize < 1024L) newSize = 1024L;
            FilesystemState.FileInfo renamed = new FilesystemState.FileInfo(newPath, fi.extension, newSize, fi.userIndex);
            state.removeFile(oldPath);
            state.addFile(newPath, renamed);
            String changeTime = attackTime.plusSeconds(random.nextInt(burstWindow)).toString();
            attackDiffs.add(new DiffEntry(newPath, "modified", String.valueOf(newSize), changeTime));
        }

        int padTarget = computePadding(attackDiffs.size(), paddingRatio);
        List<DiffEntry> diffs = new ArrayList<>(generateNormalPadding(padTarget, attackTime));
        diffs.addAll(attackDiffs);
        diffs.addAll(FilesystemState.generateDirectoryMtimeEntries(diffs));
        return diffs;
    }

    // C2) Massive Add Flood: 30K-50K added files with randomized names, 5-10min burst
    public List<DiffEntry> generateMassiveAddFlood(Instant attackTime, double paddingRatio) {
        int targetAdds = 15000 + random.nextInt(10001);
        int burstWindow = 300 + random.nextInt(301);
        String[] dirs = {"projects","docs","mail","logs","configs","media","backup","archive","temp","data"};
        String[] exts = {"docx","xlsx","pptx","pdf","txt","log","conf","sh","py","java",
                "cpp","h","md","json","xml","sql","jpg","png","gif","mp3","mp4","wav","zip","tar","gz","db","yaml","csv"};
        List<DiffEntry> attackDiffs = new ArrayList<>();

        for (int i = 0; i < targetAdds; i++) {
            int user = 1 + random.nextInt(10);
            String dir = dirs[random.nextInt(dirs.length)];
            String ext = exts[random.nextInt(exts.length)];
            String path = String.format("/vol/share/user%d/%s/file_c2_%d.%s", user, dir, random.nextInt(1000000), ext);
            long size = 1024L + random.nextInt(102400);
            FilesystemState.FileInfo info = new FilesystemState.FileInfo(path, ext, size, user);
            state.addFile(path, info);
            String changeTime = attackTime.plusSeconds(random.nextInt(burstWindow)).toString();
            attackDiffs.add(new DiffEntry(path, "added", String.valueOf(size), changeTime));
        }

        int padTarget = computePadding(attackDiffs.size(), paddingRatio);
        List<DiffEntry> diffs = new ArrayList<>(generateNormalPadding(padTarget, attackTime));
        diffs.addAll(attackDiffs);
        diffs.addAll(FilesystemState.generateDirectoryMtimeEntries(diffs));
        return diffs;
    }

    // C3) Massive Delete Flood: 20K-40K deleted records, 3-5min burst, all users
    public List<DiffEntry> generateMassiveDeleteFlood(Instant attackTime, double paddingRatio) {
        int targetDeletes = 10000 + random.nextInt(10001);
        int burstWindow = 180 + random.nextInt(121);
        List<String> paths = new ArrayList<>(state.getAllFiles().stream().map(fi -> fi.path).toList());
        List<DiffEntry> attackDiffs = new ArrayList<>();

        for (int i = 0; i < targetDeletes && !paths.isEmpty(); i++) {
            String path = paths.get(random.nextInt(paths.size()));
            FilesystemState.FileInfo fi = state.getFile(path);
            if (fi == null) continue;
            state.removeFile(path);
            attackDiffs.add(new DiffEntry(path, "deleted", String.valueOf(fi.size), Instant.MAX.toString()));
        }

        int padTarget = computePadding(attackDiffs.size(), paddingRatio);
        List<DiffEntry> diffs = new ArrayList<>(generateNormalPadding(padTarget, attackTime));
        diffs.addAll(attackDiffs);
        diffs.addAll(FilesystemState.generateDirectoryMtimeEntries(diffs));
        return diffs;
    }

    // C4) Balanced High-Volume Mix: 60K-80K total (40% mod / 30% add / 30% del), 10-20min window
    public List<DiffEntry> generateBalancedHighVolumeMix(Instant attackTime, double paddingRatio) {
        int totalOps = 30000 + random.nextInt(10001);
        List<FilesystemState.FileInfo> all = new ArrayList<>(state.getAllFiles());
        List<String> paths = new ArrayList<>(state.getAllFiles().stream().map(fi -> fi.path).toList());
        int burstWindow = 600 + random.nextInt(601);
        String[] dirs = {"projects","docs","mail","logs","configs","media","backup","archive","temp","data"};
        String[] exts = {"docx","xlsx","pptx","pdf","txt","log","conf","sh","py","java",
                "cpp","h","md","json","xml","sql","jpg","png","gif","mp3","mp4","wav","zip","tar","gz","db","yaml","csv"};
        List<DiffEntry> attackDiffs = new ArrayList<>();

        int modTarget = (int) (totalOps * 0.40);
        int addTarget = (int) (totalOps * 0.30);
        int delTarget = totalOps - modTarget - addTarget;

        for (int i = 0; i < modTarget; i++) {
            int idx = random.nextInt(all.size());
            FilesystemState.FileInfo fi = all.get(idx);
            if (state.getFile(fi.path) == null) continue;
            long newSize = (long) (fi.size * (1.01 + random.nextDouble() * 0.02));
            if (newSize < 1024L) newSize = 1024L;
            FilesystemState.FileInfo updated = new FilesystemState.FileInfo(fi.path, fi.extension, newSize, fi.userIndex);
            state.addFile(fi.path, updated);
            all.set(idx, updated);
            String changeTime = attackTime.plusSeconds(random.nextInt(burstWindow)).toString();
            attackDiffs.add(new DiffEntry(updated.path, "modified", String.valueOf(newSize), changeTime));
        }

        for (int i = 0; i < addTarget; i++) {
            int user = 1 + random.nextInt(10);
            String dir = dirs[random.nextInt(dirs.length)];
            String ext = exts[random.nextInt(exts.length)];
            String path = String.format("/vol/share/user%d/%s/file_c4_%d.%s", user, dir, random.nextInt(1000000), ext);
            long size = 1024L * (1L + random.nextInt(100));
            FilesystemState.FileInfo info = new FilesystemState.FileInfo(path, ext, size, user);
            state.addFile(path, info);
            String changeTime = attackTime.plusSeconds(random.nextInt(burstWindow)).toString();
            attackDiffs.add(new DiffEntry(path, "added", String.valueOf(size), changeTime));
        }

        for (int i = 0; i < delTarget && !paths.isEmpty(); i++) {
            String path = paths.get(random.nextInt(paths.size()));
            FilesystemState.FileInfo fi = state.getFile(path);
            if (fi == null) continue;
            state.removeFile(path);
            attackDiffs.add(new DiffEntry(path, "deleted", String.valueOf(fi.size), Instant.MAX.toString()));
        }

        int padTarget = computePadding(attackDiffs.size(), paddingRatio);
        List<DiffEntry> diffs = new ArrayList<>(generateNormalPadding(padTarget, attackTime));
        diffs.addAll(attackDiffs);
        diffs.addAll(FilesystemState.generateDirectoryMtimeEntries(diffs));
        return diffs;
    }

    // C5) Multi-Wave Escalation: 3 waves of escalating modified records (10K→20K→40K), 30min gaps
    public List<DiffEntry> generateMultiWaveEscalation(Instant attackTime, double paddingRatio) {
        int[] waveSizes = {
            5000 + random.nextInt(2501),
            10000 + random.nextInt(5001),
            20000 + random.nextInt(5001)
        };
        List<FilesystemState.FileInfo> all = new ArrayList<>(state.getAllFiles());
        List<DiffEntry> attackDiffs = new ArrayList<>();
        long waveStart = 0;

        for (int w = 0; w < waveSizes.length; w++) {
            int burstWindow = 300;
            for (int i = 0; i < waveSizes[w]; i++) {
                int idx = random.nextInt(all.size());
                FilesystemState.FileInfo fi = all.get(idx);
                if (state.getFile(fi.path) == null) continue;
                long newSize = (long) (fi.size * (1.02 + random.nextDouble() * 0.02));
                if (newSize < 1024L) newSize = 1024L;
                FilesystemState.FileInfo updated = new FilesystemState.FileInfo(fi.path, fi.extension, newSize, fi.userIndex);
                state.addFile(fi.path, updated);
                all.set(idx, updated);
                long offset = waveStart + random.nextInt(burstWindow);
                String changeTime = attackTime.plusSeconds(offset).toString();
                attackDiffs.add(new DiffEntry(updated.path, "modified", String.valueOf(newSize), changeTime));
            }
            waveStart += burstWindow + 1800;
        }

        int padTarget = computePadding(attackDiffs.size(), paddingRatio);
        List<DiffEntry> diffs = new ArrayList<>(generateNormalPadding(padTarget, attackTime));
        diffs.addAll(attackDiffs);
        diffs.addAll(FilesystemState.generateDirectoryMtimeEntries(diffs));
        return diffs;
    }

    // C6) Added-Heavy Encryption: 40K-60K added + 40K-60K deleted pairs, 10-15min window
    public List<DiffEntry> generateAddedHeavyEncryption(Instant attackTime, double paddingRatio) {
        int targetPairs = 20000 + random.nextInt(10001);
        int burstWindow = 600 + random.nextInt(301);
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        List<FilesystemState.FileInfo> all = new ArrayList<>(state.getAllFiles());
        List<DiffEntry> attackDiffs = new ArrayList<>();

        for (int i = 0; i < targetPairs && !all.isEmpty(); i++) {
            int idx = random.nextInt(all.size());
            FilesystemState.FileInfo fi = all.remove(idx);
            String oldPath = fi.path;
            state.removeFile(oldPath);
            attackDiffs.add(new DiffEntry(oldPath, "deleted", String.valueOf(fi.size), Instant.MAX.toString()));
            StringBuilder newName = new StringBuilder("enc_");
            for (int c = 0; c < 8; c++) newName.append(chars.charAt(random.nextInt(chars.length())));
            int lastSlash = oldPath.lastIndexOf('/');
            String newPath = lastSlash >= 0 ? oldPath.substring(0, lastSlash + 1) + newName : newName.toString();
            long newSize = (long) (fi.size * (1.02 + random.nextDouble() * 0.03));
            if (newSize < 1024L) newSize = 1024L;
            FilesystemState.FileInfo encrypted = new FilesystemState.FileInfo(newPath, fi.extension, newSize, fi.userIndex);
            state.addFile(newPath, encrypted);
            String changeTime = attackTime.plusSeconds(random.nextInt(burstWindow)).toString();
            attackDiffs.add(new DiffEntry(newPath, "added", String.valueOf(newSize), changeTime));
        }

        int padTarget = computePadding(attackDiffs.size(), paddingRatio);
        List<DiffEntry> diffs = new ArrayList<>(generateNormalPadding(padTarget, attackTime));
        diffs.addAll(attackDiffs);
        diffs.addAll(FilesystemState.generateDirectoryMtimeEntries(diffs));
        return diffs;
    }

    // C7) Delete-Heavy Destruction: 30K-50K deleted + 5K-10K shrunk modified, 5-10min burst
    public List<DiffEntry> generateDeleteHeavyDestruction(Instant attackTime, double paddingRatio) {
        int targetDeletes = 15000 + random.nextInt(10001);
        int targetMods = 2500 + random.nextInt(2501);
        int burstWindow = 300 + random.nextInt(301);
        List<String> paths = new ArrayList<>(state.getAllFiles().stream().map(fi -> fi.path).toList());
        List<FilesystemState.FileInfo> all = new ArrayList<>(state.getAllFiles());
        List<DiffEntry> attackDiffs = new ArrayList<>();

        for (int i = 0; i < targetDeletes && !paths.isEmpty(); i++) {
            String path = paths.get(random.nextInt(paths.size()));
            FilesystemState.FileInfo fi = state.getFile(path);
            if (fi == null) continue;
            state.removeFile(path);
            attackDiffs.add(new DiffEntry(path, "deleted", String.valueOf(fi.size), Instant.MAX.toString()));
        }

        for (int i = 0; i < targetMods; i++) {
            int idx = random.nextInt(all.size());
            FilesystemState.FileInfo fi = all.get(idx);
            if (state.getFile(fi.path) == null) continue;
            double factor = 0.50 + random.nextDouble() * 0.30;
            long newSize = (long) (fi.size * factor);
            if (newSize < 512L) newSize = 512L;
            FilesystemState.FileInfo updated = new FilesystemState.FileInfo(fi.path, fi.extension, newSize, fi.userIndex);
            state.addFile(fi.path, updated);
            all.set(idx, updated);
            String changeTime = attackTime.plusSeconds(random.nextInt(burstWindow)).toString();
            attackDiffs.add(new DiffEntry(updated.path, "modified", String.valueOf(newSize), changeTime));
        }

        int padTarget = computePadding(attackDiffs.size(), paddingRatio);
        List<DiffEntry> diffs = new ArrayList<>(generateNormalPadding(padTarget, attackTime));
        diffs.addAll(attackDiffs);
        diffs.addAll(FilesystemState.generateDirectoryMtimeEntries(diffs));
        return diffs;
    }

    // C8) Baseline-Mimicking Volume: 30K-40K total (50/25/25), spread over 6-8 hours
    public List<DiffEntry> generateBaselineMimickingVolume(Instant attackTime, double paddingRatio) {
        int totalOps = 15000 + random.nextInt(5001);
        int totalSeconds = 21600 + random.nextInt(7201);
        List<FilesystemState.FileInfo> all = new ArrayList<>(state.getAllFiles());
        List<String> paths = new ArrayList<>(state.getAllFiles().stream().map(fi -> fi.path).toList());
        String[] dirs = {"projects","docs","mail","logs","configs","media","backup","archive","temp","data"};
        String[] exts = {"docx","xlsx","pptx","pdf","txt","log","conf","sh","py","java",
                "cpp","h","md","json","xml","sql","jpg","png","gif","mp3","mp4","wav","zip","tar","gz","db","yaml","csv"};
        List<DiffEntry> attackDiffs = new ArrayList<>();

        int modTarget = (int) (totalOps * 0.50);
        int addTarget = (int) (totalOps * 0.25);
        int delTarget = totalOps - modTarget - addTarget;

        for (int i = 0; i < modTarget; i++) {
            int idx = random.nextInt(all.size());
            FilesystemState.FileInfo fi = all.get(idx);
            if (state.getFile(fi.path) == null) continue;
            double delta = (random.nextDouble() * 0.20) - 0.10;
            long newSize = (long) (fi.size * (1.0 + delta));
            if (newSize < 1024L) newSize = 1024L;
            FilesystemState.FileInfo updated = new FilesystemState.FileInfo(fi.path, fi.extension, newSize, fi.userIndex);
            state.addFile(fi.path, updated);
            all.set(idx, updated);
            String changeTime = attackTime.plusSeconds(random.nextInt(totalSeconds)).toString();
            attackDiffs.add(new DiffEntry(updated.path, "modified", String.valueOf(newSize), changeTime));
        }

        for (int i = 0; i < addTarget; i++) {
            int user = 1 + random.nextInt(10);
            String dir = dirs[random.nextInt(dirs.length)];
            String ext = exts[random.nextInt(exts.length)];
            String path = String.format("/vol/share/user%d/%s/file_c8_%d.%s", user, dir, random.nextInt(1000000), ext);
            long size = 1024L * (1L + random.nextInt(100));
            FilesystemState.FileInfo info = new FilesystemState.FileInfo(path, ext, size, user);
            state.addFile(path, info);
            String changeTime = attackTime.plusSeconds(random.nextInt(totalSeconds)).toString();
            attackDiffs.add(new DiffEntry(path, "added", String.valueOf(size), changeTime));
        }

        for (int i = 0; i < delTarget && !paths.isEmpty(); i++) {
            String path = paths.get(random.nextInt(paths.size()));
            FilesystemState.FileInfo fi = state.getFile(path);
            if (fi == null) continue;
            state.removeFile(path);
            String changeTime = attackTime.plusSeconds(random.nextInt(totalSeconds)).toString();
            attackDiffs.add(new DiffEntry(path, "deleted", String.valueOf(fi.size), changeTime));
        }

        int padTarget = computePadding(attackDiffs.size(), paddingRatio);
        List<DiffEntry> diffs = new ArrayList<>(generateNormalPadding(padTarget, attackTime));
        diffs.addAll(attackDiffs);
        diffs.addAll(FilesystemState.generateDirectoryMtimeEntries(diffs));
        return diffs;
    }

    // D1) Ryuk Lateral: two-phase — slow HV targeting then fast burst across all users
    public List<DiffEntry> generateRyukLateral(Instant attackTime, double paddingRatio) {
        List<DiffEntry> attackDiffs = new ArrayList<>();
        String[] hvExts = {".docx", ".xlsx", ".pdf", ".db", ".sql"};
        List<FilesystemState.FileInfo> hvFiles = new ArrayList<>();
        for (FilesystemState.FileInfo fi : state.getAllFiles()) {
            for (String ext : hvExts) {
                if (fi.path.endsWith(ext)) { hvFiles.add(fi); break; }
            }
        }

        int phase1Target = 500 + random.nextInt(501);
        int[] users = new int[5 + random.nextInt(4)];
        for (int i = 0; i < users.length; i++) users[i] = 1 + random.nextInt(10);
        int phase1Window = 600 + random.nextInt(1201);
        for (int i = 0; i < phase1Target && !hvFiles.isEmpty(); i++) {
            int idx = random.nextInt(hvFiles.size());
            FilesystemState.FileInfo fi = hvFiles.get(idx);
            if (state.getFile(fi.path) == null) continue;
            long newSize = (long) (fi.size * (1.02 + random.nextDouble() * 0.02));
            if (newSize < 1024L) newSize = 1024L;
            FilesystemState.FileInfo updated = new FilesystemState.FileInfo(fi.path, fi.extension, newSize, fi.userIndex);
            state.addFile(fi.path, updated);
            hvFiles.set(idx, updated);
            String ct = attackTime.plusSeconds(random.nextInt(phase1Window)).toString();
            attackDiffs.add(new DiffEntry(updated.path, "modified", String.valueOf(newSize), ct));
        }

        long phase2Start = phase1Window + 60 + random.nextInt(120);
        int phase2Target = 1500 + random.nextInt(1501);
        int phase2Window = 60 + random.nextInt(121);
        List<FilesystemState.FileInfo> all = new ArrayList<>(state.getAllFiles());
        for (int i = 0; i < phase2Target && !all.isEmpty(); i++) {
            int idx = random.nextInt(all.size());
            FilesystemState.FileInfo fi = all.get(idx);
            if (state.getFile(fi.path) == null) continue;
            long newSize = (long) (fi.size * (1.02 + random.nextDouble() * 0.02));
            if (newSize < 1024L) newSize = 1024L;
            FilesystemState.FileInfo updated = new FilesystemState.FileInfo(fi.path, fi.extension, newSize, fi.userIndex);
            state.addFile(fi.path, updated);
            all.set(idx, updated);
            String ct = attackTime.plusSeconds(phase2Start + random.nextInt(phase2Window)).toString();
            attackDiffs.add(new DiffEntry(updated.path, "modified", String.valueOf(newSize), ct));
        }

        int padTarget = computePadding(attackDiffs.size(), paddingRatio);
        List<DiffEntry> diffs = new ArrayList<>(generateNormalPadding(padTarget, attackTime));
        diffs.addAll(attackDiffs);
        diffs.addAll(FilesystemState.generateDirectoryMtimeEntries(diffs));
        return diffs;
    }

    // D2) DarkSide Staged: three-phase add copies → modify originals → delete copies
    public List<DiffEntry> generateDarkSideStaged(Instant attackTime, double paddingRatio) {
        List<FilesystemState.FileInfo> all = new ArrayList<>(state.getAllFiles());
        List<DiffEntry> attackDiffs = new ArrayList<>();
        List<String> copyPaths = new ArrayList<>();

        int phase1Count = 500 + random.nextInt(1001);
        int phase1Window = 300 + random.nextInt(301);
        for (int i = 0; i < phase1Count && !all.isEmpty(); i++) {
            int idx = random.nextInt(all.size());
            FilesystemState.FileInfo fi = all.get(idx);
            if (state.getFile(fi.path) == null) continue;
            String copyPath = fi.path + ".enc_copy";
            long copySize = (long) (fi.size * (1.02 + random.nextDouble() * 0.02));
            if (copySize < 1024L) copySize = 1024L;
            FilesystemState.FileInfo copyInfo = new FilesystemState.FileInfo(copyPath, "enc_copy", copySize, fi.userIndex);
            state.addFile(copyPath, copyInfo);
            copyPaths.add(copyPath);
            String ct = attackTime.plusSeconds(random.nextInt(phase1Window)).toString();
            attackDiffs.add(new DiffEntry(copyPath, "added", String.valueOf(copySize), ct));
        }

        long phase2Start = phase1Window + 60 + random.nextInt(121);
        int phase2Count = 1500 + random.nextInt(1501);
        int phase2Window = 180 + random.nextInt(301);
        for (int i = 0; i < phase2Count && !all.isEmpty(); i++) {
            int idx = random.nextInt(all.size());
            FilesystemState.FileInfo fi = all.get(idx);
            if (state.getFile(fi.path) == null) continue;
            long newSize = (long) (fi.size * (1.02 + random.nextDouble() * 0.02));
            if (newSize < 1024L) newSize = 1024L;
            FilesystemState.FileInfo updated = new FilesystemState.FileInfo(fi.path, fi.extension, newSize, fi.userIndex);
            state.addFile(fi.path, updated);
            all.set(idx, updated);
            String ct = attackTime.plusSeconds(phase2Start + random.nextInt(phase2Window)).toString();
            attackDiffs.add(new DiffEntry(updated.path, "modified", String.valueOf(newSize), ct));
        }

        long phase3Start = phase2Start + phase2Window + 60 + random.nextInt(121);
        int phase3Window = 120 + random.nextInt(181);
        for (int i = 0; i < copyPaths.size(); i++) {
            String cp = copyPaths.get(i);
            FilesystemState.FileInfo fi = state.getFile(cp);
            if (fi == null) continue;
            state.removeFile(cp);
            String ct = attackTime.plusSeconds(phase3Start + random.nextInt(phase3Window)).toString();
            attackDiffs.add(new DiffEntry(cp, "deleted", String.valueOf(fi.size), ct));
        }

        int padTarget = computePadding(attackDiffs.size(), paddingRatio);
        List<DiffEntry> diffs = new ArrayList<>(generateNormalPadding(padTarget, attackTime));
        diffs.addAll(attackDiffs);
        diffs.addAll(FilesystemState.generateDirectoryMtimeEntries(diffs));
        return diffs;
    }

    // D3) LockBit3 Adaptive: alternating fast/slow bursts with varying purity
    public List<DiffEntry> generateLockBit3Adaptive(Instant attackTime, double paddingRatio) {
        List<FilesystemState.FileInfo> all = new ArrayList<>(state.getAllFiles());
        List<DiffEntry> attackDiffs = new ArrayList<>();
        String[] dirs = {"projects","docs","mail","logs","configs","media","backup","archive","temp","data"};
        String[] exts = {"docx","xlsx","pptx","pdf","txt","log","conf","sh","py","java",
                "cpp","h","md","json","xml","sql","jpg","png","gif","mp3","mp4","wav","zip","tar","gz","db","yaml","csv"};
        int totalTarget = 2000 + random.nextInt(2001);
        int numBursts = 5 + random.nextInt(4);
        long t = 0;

        for (int b = 0; b < numBursts && attackDiffs.size() < totalTarget; b++) {
            boolean isFast = b % 2 == 0;
            int burstOps = isFast ? (400 + random.nextInt(201)) : (200 + random.nextInt(101));
            int burstWindow = isFast ? (30 + random.nextInt(31)) : (180 + random.nextInt(121));
            double modPurity = isFast ? 0.90 : 0.80;
            double sizeMin = isFast ? 0.03 : 0.02;
            double sizeMax = isFast ? 0.05 : 0.03;

            for (int j = 0; j < burstOps && attackDiffs.size() < totalTarget; j++) {
                if (random.nextDouble() < modPurity) {
                    int idx = random.nextInt(all.size());
                    FilesystemState.FileInfo fi = all.get(idx);
                    if (state.getFile(fi.path) == null) continue;
                    long newSize = (long) (fi.size * (1.0 + sizeMin + random.nextDouble() * (sizeMax - sizeMin)));
                    if (newSize < 1024L) newSize = 1024L;
                    FilesystemState.FileInfo updated = new FilesystemState.FileInfo(fi.path, fi.extension, newSize, fi.userIndex);
                    state.addFile(fi.path, updated);
                    all.set(idx, updated);
                    String ct = attackTime.plusSeconds(t + random.nextInt(burstWindow)).toString();
                    attackDiffs.add(new DiffEntry(updated.path, "modified", String.valueOf(newSize), ct));
                } else {
                    int user = 1 + random.nextInt(10);
                    String dir = dirs[random.nextInt(dirs.length)];
                    String ext = exts[random.nextInt(exts.length)];
                    String path = String.format("/vol/share/user%d/%s/file_d3_%d.%s", user, dir, random.nextInt(100000), ext);
                    long size = 1024L * (1L + random.nextInt(100));
                    FilesystemState.FileInfo info = new FilesystemState.FileInfo(path, ext, size, user);
                    state.addFile(path, info);
                    String ct = attackTime.plusSeconds(t + random.nextInt(burstWindow)).toString();
                    attackDiffs.add(new DiffEntry(path, "added", String.valueOf(size), ct));
                }
            }
            t += burstWindow + 30 + random.nextInt(61);
        }

        int padTarget = computePadding(attackDiffs.size(), paddingRatio);
        List<DiffEntry> diffs = new ArrayList<>(generateNormalPadding(padTarget, attackTime));
        diffs.addAll(attackDiffs);
        diffs.addAll(FilesystemState.generateDirectoryMtimeEntries(diffs));
        return diffs;
    }

    // D4) BlackCat Variable: mixed full/intermittent encryption at off-hours
    public List<DiffEntry> generateBlackCatVariable(Instant attackTime, double paddingRatio) {
        attackTime = attackTime.atZone(java.time.ZoneOffset.UTC).withHour(2 + random.nextInt(3)).withMinute(0).withSecond(0).toInstant();
        List<FilesystemState.FileInfo> all = new ArrayList<>(state.getAllFiles());
        int totalTarget = 2000 + random.nextInt(2001);
        int totalWindow = 900 + random.nextInt(1801);
        List<DiffEntry> attackDiffs = new ArrayList<>();

        for (int i = 0; i < totalTarget && !all.isEmpty(); i++) {
            int idx = random.nextInt(all.size());
            FilesystemState.FileInfo fi = all.get(idx);
            if (state.getFile(fi.path) == null) continue;
            boolean fullEncrypt = random.nextDouble() < 0.60;
            double pct = fullEncrypt ? (0.03 + random.nextDouble() * 0.02) : (0.01 + random.nextDouble() * 0.01);
            long newSize = (long) (fi.size * (1.0 + pct));
            if (newSize < 1024L) newSize = 1024L;
            FilesystemState.FileInfo updated = new FilesystemState.FileInfo(fi.path, fi.extension, newSize, fi.userIndex);
            state.addFile(fi.path, updated);
            all.set(idx, updated);
            String ct = attackTime.plusSeconds(random.nextInt(totalWindow)).toString();
            attackDiffs.add(new DiffEntry(updated.path, "modified", String.valueOf(newSize), ct));
        }

        int padTarget = computePadding(attackDiffs.size(), paddingRatio);
        List<DiffEntry> diffs = new ArrayList<>(generateNormalPadding(padTarget, attackTime));
        diffs.addAll(attackDiffs);
        diffs.addAll(FilesystemState.generateDirectoryMtimeEntries(diffs));
        return diffs;
    }

    // D5) Royal Selective: partial corruption + 15% rename
    public List<DiffEntry> generateRoyalSelective(Instant attackTime, double paddingRatio) {
        List<FilesystemState.FileInfo> all = new ArrayList<>(state.getAllFiles());
        int totalTarget = 1500 + random.nextInt(1501);
        int burstWindow = 120 + random.nextInt(181);
        List<DiffEntry> attackDiffs = new ArrayList<>();
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";

        for (int i = 0; i < totalTarget && !all.isEmpty(); i++) {
            int idx = random.nextInt(all.size());
            FilesystemState.FileInfo fi = all.get(idx);
            if (state.getFile(fi.path) == null) continue;

            double roll = random.nextDouble();
            long newSize;
            if (roll < 0.70) {
                newSize = (long) (fi.size * (1.03 + random.nextDouble() * 0.02));
            } else {
                newSize = (long) (fi.size * (1.0 + (random.nextDouble() * 0.10) - 0.05));
            }
            if (newSize < 1024L) newSize = 1024L;
            String ct = attackTime.plusSeconds(random.nextInt(burstWindow)).toString();

            if (roll < 0.70 && random.nextDouble() < 0.15) {
                String oldPath = fi.path;
                state.removeFile(oldPath);
                StringBuilder newName = new StringBuilder("file_");
                for (int c = 0; c < 8; c++) newName.append(chars.charAt(random.nextInt(chars.length())));
                int lastSlash = oldPath.lastIndexOf('/');
                String newPath = lastSlash >= 0 ? oldPath.substring(0, lastSlash + 1) + newName : newName.toString();
                FilesystemState.FileInfo renamed = new FilesystemState.FileInfo(newPath, fi.extension, newSize, fi.userIndex);
                state.addFile(newPath, renamed);
                all.set(idx, renamed);
                attackDiffs.add(new DiffEntry(newPath, "modified", String.valueOf(newSize), ct));
            } else {
                FilesystemState.FileInfo updated = new FilesystemState.FileInfo(fi.path, fi.extension, newSize, fi.userIndex);
                state.addFile(fi.path, updated);
                all.set(idx, updated);
                attackDiffs.add(new DiffEntry(updated.path, "modified", String.valueOf(newSize), ct));
            }
        }

        int padTarget = computePadding(attackDiffs.size(), paddingRatio);
        List<DiffEntry> diffs = new ArrayList<>(generateNormalPadding(padTarget, attackTime));
        diffs.addAll(attackDiffs);
        diffs.addAll(FilesystemState.generateDirectoryMtimeEntries(diffs));
        return diffs;
    }

    // D6) Play Intermittent NoExt: 128KB block encryption of business files
    public List<DiffEntry> generatePlayIntermittentNoExt(Instant attackTime, double paddingRatio) {
        String[] bizExts = {".docx", ".xlsx", ".pdf", ".db", ".sql", ".csv", ".pptx"};
        List<FilesystemState.FileInfo> targets = new ArrayList<>();
        for (FilesystemState.FileInfo fi : state.getAllFiles()) {
            for (String ext : bizExts) {
                if (fi.path.endsWith(ext)) { targets.add(fi); break; }
            }
        }
        int totalTarget = 1000 + random.nextInt(1001);
        int limit = Math.min(totalTarget, targets.size());
        int totalWindow = 600 + random.nextInt(601);
        List<DiffEntry> attackDiffs = new ArrayList<>();
        long[] timestamps = new long[limit];

        for (int i = 0; i < limit; i++) {
            int idx = random.nextInt(targets.size());
            FilesystemState.FileInfo fi = targets.get(idx);
            if (state.getFile(fi.path) == null) continue;
            long stripeDelta = (fi.size / 131072L) * (8L + random.nextInt(17));
            if (stripeDelta < 8L) stripeDelta = 8L;
            long newSize = fi.size + stripeDelta;
            FilesystemState.FileInfo updated = new FilesystemState.FileInfo(fi.path, fi.extension, newSize, fi.userIndex);
            state.addFile(fi.path, updated);
            targets.set(idx, updated);
            timestamps[i] = (long) (i * (200 + random.nextInt(401)));
            if (timestamps[i] > totalWindow) timestamps[i] = totalWindow;
            String ct = attackTime.plusSeconds(timestamps[i]).toString();
            attackDiffs.add(new DiffEntry(updated.path, "modified", String.valueOf(newSize), ct));
        }

        int padTarget = computePadding(attackDiffs.size(), paddingRatio);
        List<DiffEntry> diffs = new ArrayList<>(generateNormalPadding(padTarget, attackTime));
        diffs.addAll(attackDiffs);
        diffs.addAll(FilesystemState.generateDirectoryMtimeEntries(diffs));
        return diffs;
    }

    // D7) Medusa Multi-Stage: disruption → variable-speed encryption → final burst
    public List<DiffEntry> generateMedusaMultiStage(Instant attackTime, double paddingRatio) {
        List<FilesystemState.FileInfo> all = new ArrayList<>(state.getAllFiles());
        List<String> paths = new ArrayList<>(state.getAllFiles().stream().map(fi -> fi.path).toList());
        String[] dirs = {"projects","docs","mail","logs","configs","media","backup","archive","temp","data"};
        String[] exts = {"docx","xlsx","pptx","pdf","txt","log","conf","sh","py","java",
                "cpp","h","md","json","xml","sql","jpg","png","gif","mp3","mp4","wav","zip","tar","gz","db","yaml","csv"};
        List<DiffEntry> attackDiffs = new ArrayList<>();

        int s1Count = 100 + random.nextInt(201);
        int s1Window = 300 + random.nextInt(601);
        for (int i = 0; i < s1Count; i++) {
            double roll = random.nextDouble();
            if (roll < 0.30) {
                int idx = random.nextInt(all.size());
                FilesystemState.FileInfo fi = all.get(idx);
                if (state.getFile(fi.path) == null) continue;
                long newSize = (long) (fi.size * (1.02 + random.nextDouble() * 0.02));
                if (newSize < 1024L) newSize = 1024L;
                FilesystemState.FileInfo updated = new FilesystemState.FileInfo(fi.path, fi.extension, newSize, fi.userIndex);
                state.addFile(fi.path, updated);
                all.set(idx, updated);
                String ct = attackTime.plusSeconds(random.nextInt(s1Window)).toString();
                attackDiffs.add(new DiffEntry(updated.path, "modified", String.valueOf(newSize), ct));
            } else if (roll < 0.70) {
                int user = 1 + random.nextInt(10);
                String dir = dirs[random.nextInt(dirs.length)];
                String ext = exts[random.nextInt(exts.length)];
                String path = String.format("/vol/share/user%d/%s/file_d7_%d.%s", user, dir, random.nextInt(100000), ext);
                long size = 1024L * (1L + random.nextInt(100));
                FilesystemState.FileInfo info = new FilesystemState.FileInfo(path, ext, size, user);
                state.addFile(path, info);
                String ct = attackTime.plusSeconds(random.nextInt(s1Window)).toString();
                attackDiffs.add(new DiffEntry(path, "added", String.valueOf(size), ct));
            } else {
                if (paths.isEmpty()) continue;
                String path = paths.get(random.nextInt(paths.size()));
                FilesystemState.FileInfo fi = state.getFile(path);
                if (fi == null) continue;
                state.removeFile(path);
                String ct = attackTime.plusSeconds(random.nextInt(s1Window)).toString();
                attackDiffs.add(new DiffEntry(path, "deleted", String.valueOf(fi.size), ct));
            }
        }

        long s2Start = s1Window + 120 + random.nextInt(181);
        int s2Target = 2000 + random.nextInt(2001);
        int s2Window = 480 + random.nextInt(421);
        for (int i = 0; i < s2Target && !all.isEmpty(); i++) {
            int idx = random.nextInt(all.size());
            FilesystemState.FileInfo fi = all.get(idx);
            if (state.getFile(fi.path) == null) continue;
            long newSize = (long) (fi.size * (1.02 + random.nextDouble() * 0.02));
            if (newSize < 1024L) newSize = 1024L;
            FilesystemState.FileInfo updated = new FilesystemState.FileInfo(fi.path, fi.extension, newSize, fi.userIndex);
            state.addFile(fi.path, updated);
            all.set(idx, updated);
            String ct = attackTime.plusSeconds(s2Start + random.nextInt(s2Window)).toString();
            attackDiffs.add(new DiffEntry(updated.path, "modified", String.valueOf(newSize), ct));
        }

        long s3Start = s2Start + s2Window + 120 + random.nextInt(181);
        int s3Target = 500 + random.nextInt(501);
        int s3Window = 180 + random.nextInt(121);
        for (int i = 0; i < s3Target && !all.isEmpty(); i++) {
            int idx = random.nextInt(all.size());
            FilesystemState.FileInfo fi = all.get(idx);
            if (state.getFile(fi.path) == null) continue;
            long newSize = (long) (fi.size * (1.03 + random.nextDouble() * 0.02));
            if (newSize < 1024L) newSize = 1024L;
            FilesystemState.FileInfo updated = new FilesystemState.FileInfo(fi.path, fi.extension, newSize, fi.userIndex);
            state.addFile(fi.path, updated);
            all.set(idx, updated);
            String ct = attackTime.plusSeconds(s3Start + random.nextInt(s3Window)).toString();
            attackDiffs.add(new DiffEntry(updated.path, "modified", String.valueOf(newSize), ct));
        }

        int padTarget = computePadding(attackDiffs.size(), paddingRatio);
        List<DiffEntry> diffs = new ArrayList<>(generateNormalPadding(padTarget, attackTime));
        diffs.addAll(attackDiffs);
        diffs.addAll(FilesystemState.generateDirectoryMtimeEntries(diffs));
        return diffs;
    }

    // D8) Akira VPN Gradual: slow-to-fast escalation at off-hours
    public List<DiffEntry> generateAkiraVpnGradual(Instant attackTime, double paddingRatio) {
        int hour = 23 + random.nextInt(4);
        if (hour >= 24) hour -= 24;
        attackTime = attackTime.atZone(java.time.ZoneOffset.UTC).withHour(hour).withMinute(0).withSecond(0).toInstant();
        List<FilesystemState.FileInfo> all = new ArrayList<>(state.getAllFiles());
        int[] targetUsers = new int[3 + random.nextInt(3)];
        for (int i = 0; i < targetUsers.length; i++) targetUsers[i] = 1 + random.nextInt(10);
        List<FilesystemState.FileInfo> userFiles = new ArrayList<>();
        for (FilesystemState.FileInfo fi : all) {
            for (int u : targetUsers) {
                if (fi.userIndex == u) { userFiles.add(fi); break; }
            }
        }
        if (userFiles.isEmpty()) userFiles = all;

        int totalTarget = 1500 + random.nextInt(1501);
        int totalWindow = 2700 + random.nextInt(2701);
        int zone1End = 1200;
        int zone2End = 2400;
        int zone1Count = 100 + random.nextInt(101);
        int zone2Count = 500 + random.nextInt(501);
        List<DiffEntry> attackDiffs = new ArrayList<>();

        for (int i = 0; i < totalTarget && !userFiles.isEmpty(); i++) {
            int idx = random.nextInt(userFiles.size());
            FilesystemState.FileInfo fi = userFiles.get(idx);
            if (state.getFile(fi.path) == null) continue;
            double pct;
            if (random.nextDouble() < 0.20) {
                pct = (random.nextDouble() * 0.10) - 0.05;
            } else {
                pct = 0.01 + random.nextDouble() * 0.02;
            }
            long newSize = (long) (fi.size * (1.0 + pct));
            if (newSize < 1024L) newSize = 1024L;
            FilesystemState.FileInfo updated = new FilesystemState.FileInfo(fi.path, fi.extension, newSize, fi.userIndex);
            state.addFile(fi.path, updated);
            userFiles.set(idx, updated);

            int ts;
            if (i < zone1Count) {
                ts = random.nextInt(zone1End);
            } else if (i < zone1Count + zone2Count) {
                ts = zone1End + random.nextInt(zone2End - zone1End);
            } else {
                ts = zone2End + random.nextInt(totalWindow - zone2End);
            }
            String ct = attackTime.plusSeconds(ts).toString();
            attackDiffs.add(new DiffEntry(updated.path, "modified", String.valueOf(newSize), ct));
        }

        int padTarget = computePadding(attackDiffs.size(), paddingRatio);
        List<DiffEntry> diffs = new ArrayList<>(generateNormalPadding(padTarget, attackTime));
        diffs.addAll(attackDiffs);
        diffs.addAll(FilesystemState.generateDirectoryMtimeEntries(diffs));
        return diffs;
    }

    private List<DiffEntry> generateNormalPadding(int count, Instant baseTime) {
        List<DiffEntry> padding = new ArrayList<>();
        List<String> paths = new ArrayList<>(state.getAllFiles().stream().map(fi -> fi.path).toList());
        String[] dirs = {"projects","docs","mail","logs","configs","media","backup","archive","temp","data"};
        String[] exts = {"docx","xlsx","pptx","pdf","txt","log","conf","sh","py","java",
                "cpp","h","md","json","xml","sql","jpg","png","gif","mp3","mp4","wav","zip","tar","gz","db","yaml","csv"};

        int modCount = (int) (count * (0.35 + java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 0.25));
        int addCount = (int) (count * (0.15 + java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 0.15));
        int delCount = count - modCount - addCount;
        int hoursWindow = 6 + random.nextInt(7);

        for (int i = 0; i < modCount && !paths.isEmpty(); i++) {
            String path = paths.get(random.nextInt(paths.size()));
            FilesystemState.FileInfo fi = state.getFile(path);
            if (fi == null) continue;
            double delta = (random.nextDouble() * 0.6) - 0.3;
            long newSize = (long) (fi.size * (1.0 + delta));
            if (newSize < 1024L) newSize = 1024L;
            FilesystemState.FileInfo updated = new FilesystemState.FileInfo(fi.path, fi.extension, newSize, fi.userIndex);
            state.addFile(path, updated);
            String changeTime = baseTime.plusSeconds(random.nextInt(hoursWindow * 3600)).toString();
            padding.add(new DiffEntry(path, "modified", String.valueOf(newSize), changeTime));
        }

        for (int i = 0; i < addCount; i++) {
            int user = 1 + random.nextInt(10);
            String dir = dirs[random.nextInt(dirs.length)];
            String ext = exts[random.nextInt(exts.length)];
            String path = String.format("/vol/share/user%d/%s/file_pad_%d.%s", user, dir, random.nextInt(100000), ext);
            long size = 1024L * (1L + random.nextInt(100));
            FilesystemState.FileInfo info = new FilesystemState.FileInfo(path, ext, size, user);
            state.addFile(path, info);
            String changeTime = baseTime.plusSeconds(random.nextInt(hoursWindow * 3600)).toString();
            padding.add(new DiffEntry(path, "added", String.valueOf(size), changeTime));
        }

        for (int i = 0; i < delCount && !paths.isEmpty(); i++) {
            String path = paths.get(random.nextInt(paths.size()));
            FilesystemState.FileInfo fi = state.getFile(path);
            if (fi == null) continue;
            state.removeFile(path);
            padding.add(new DiffEntry(path, "deleted", String.valueOf(fi.size), Instant.MAX.toString()));
        }

        padding.addAll(FilesystemState.generateDirectoryMtimeEntries(padding));
        return padding;
    }
}
