package com.anomalydetection.generator;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

public class FilesystemState {
    private final Map<String, FileInfo> files;
    private final Random random;
    private final int totalFiles;

    public FilesystemState(long seed, int totalFiles) {
        this.files = new LinkedHashMap<>();
        this.random = new Random(seed);
        this.totalFiles = totalFiles;
    }

    public void initialize() {
        String[] dirs = {
                "projects", "docs", "mail", "logs", "configs",
                "media", "backup", "archive", "temp", "data"
        };
        String[] exts = {
                "docx","xlsx","pptx","pdf","txt","log","conf","sh","py","java",
                "cpp","h","md","json","xml","sql","jpg","png","gif","mp3",
                "mp4","wav","zip","tar","gz","db","yaml","csv"
        };

        int users = 10;
        for (int u = 1; u <= users; u++) {
            for (String dir : dirs) {
                for (int i = 0; i < 3000; i++) {
                    String ext = exts[random.nextInt(exts.length)];
                    String path = String.format("/vol/share/user%d/%s/file_%04d.%s", u, dir, i, ext);
                    long size = randomSize(random);
                    FileInfo info = new FileInfo(path, ext, size, u);
                    files.put(path, info);
                }
            }
        }
    }

    private long randomSize(Random rnd) {
        double r = rnd.nextDouble();
        if (r < 0.70) {
            return 1024L * (1L + rnd.nextInt(100));
        } else if (r < 0.90) {
            long base = 1024L * 100L;
            long range = 1024L * 1024L * 10L;
            return base + (rnd.nextInt(100) * (range / 100));
        } else if (r < 0.99) {
            long min = 10L * 1024L * 1024L;
            long max = 100L * 1024L * 1024L;
            return min + (long) (rnd.nextDouble() * (max - min));
        } else {
            long min = 100L * 1024L * 1024L;
            long max = 1024L * 1024L * 1024L;
            return min + (long) (rnd.nextDouble() * (max - min));
        }
    }

    public List<DiffEntry> evolveNormalRound(Instant dayStart) {
        double activityLevel = 0.02 + random.nextDouble() * 0.08;
        int total = Math.max(1000, (int) (files.size() * activityLevel));

        double tierRoll = random.nextDouble();
        boolean isQuietDay = false;
        boolean isBusyDay = false;
        if (tierRoll < 0.15) {
            total = (int) (total * 0.6);
            isQuietDay = true;
        } else if (tierRoll >= 0.85) {
            total = (int) (total * 1.5);
            isBusyDay = true;
        }

        double modFraction = 0.35 + random.nextDouble() * 0.30;
        double addFraction = 0.15 + random.nextDouble() * 0.20;
        int modifyCount = Math.max(1, (int) Math.round(total * modFraction));
        int addCount = Math.max(1, (int) Math.round(total * addFraction));
        int deleteCount = Math.max(0, total - modifyCount - addCount);

        double renameFraction = 0.05 + random.nextDouble() * 0.10;
        int renameCount = Math.max(0, (int) Math.round(total * renameFraction));
        modifyCount = Math.max(1, modifyCount - renameCount);

        int[] allUsers = {1,2,3,4,5,6,7,8,9,10};
        shuffleArray(allUsers, random);
        int activeUsers = 3 + random.nextInt(8);
        Set<Integer> activeUserSet = new HashSet<>();
        for (int i = 0; i < activeUsers; i++) activeUserSet.add(allUsers[i]);

        int hoursWindow = 2 + random.nextInt(11);
        if (isQuietDay) {
            hoursWindow = (int) (hoursWindow * 1.5);
        }

        List<FileInfo> candidateFiles = new ArrayList<>();
        for (FileInfo fi : files.values()) {
            if (activeUserSet.contains(fi.userIndex)) candidateFiles.add(fi);
        }

        List<DiffEntry> diffs = new ArrayList<>();
        double afterHoursProb = 0.08 + random.nextDouble() * 0.10;
        String[] renameSuffixes = {"_v2", "_v3", "_new", "_backup", "_old", "_final", "_copy", "_draft"};

        for (int i = 0; i < modifyCount && !candidateFiles.isEmpty(); i++) {
            int idx = randomIndex(candidateFiles.size());
            FileInfo info = candidateFiles.get(idx);

            double delta = (random.nextDouble() * 0.6) - 0.3;
            long newSize = (long) (info.size * (1.0 + delta));
            if (newSize < 1024L) newSize = 1024L;
            FileInfo updated = new FileInfo(info.path, info.extension, newSize, info.userIndex);
            files.put(info.path, updated);
            candidateFiles.set(idx, updated);

            String changeTime;
            if (random.nextDouble() < afterHoursProb) {
                changeTime = generateAfterHoursTimestamp(dayStart);
            } else {
                changeTime = instantToISO(dayStart.plusSeconds(random.nextInt(hoursWindow * 3600)));
            }
            diffs.add(new DiffEntry(updated.path, "modified", String.valueOf(newSize), changeTime));
        }

        for (int i = 0; i < renameCount && !candidateFiles.isEmpty(); i++) {
            int idx = randomIndex(candidateFiles.size());
            FileInfo fi = candidateFiles.get(idx);

            String suffix = renameSuffixes[random.nextInt(renameSuffixes.length)];
            String newPath = fi.path.replace("." + fi.extension, suffix + "." + fi.extension);

            if (files.containsKey(newPath)) continue;

            long newSize = fi.size;
            if (random.nextDouble() < 0.5) {
                double sizeDelta = (random.nextDouble() * 0.10) - 0.05;
                newSize = (long) (fi.size * (1.0 + sizeDelta));
                if (newSize < 1024L) newSize = 1024L;
            }

            files.remove(fi.path);
            FileInfo renamed = new FileInfo(newPath, fi.extension, newSize, fi.userIndex);
            files.put(newPath, renamed);
            candidateFiles.remove(idx);

            String changeTime;
            if (random.nextDouble() < afterHoursProb) {
                changeTime = generateAfterHoursTimestamp(dayStart);
            } else {
                changeTime = instantToISO(dayStart.plusSeconds(random.nextInt(hoursWindow * 3600)));
            }

            diffs.add(new DiffEntry(fi.path, "deleted", String.valueOf(fi.size), Instant.MAX.toString()));
            diffs.add(new DiffEntry(newPath, "added", String.valueOf(newSize), changeTime));
        }

        List<FileInfo> toDelete = new ArrayList<>(candidateFiles);
        for (int i = 0; i < deleteCount && !toDelete.isEmpty(); i++) {
            int idx = randomIndex(toDelete.size());
            FileInfo info = toDelete.remove(idx);
            files.remove(info.path);
            diffs.add(new DiffEntry(info.path, "deleted", String.valueOf(info.size), Instant.MAX.toString()));
        }

        for (int i = 0; i < addCount; i++) {
            int user = allUsers[random.nextInt(activeUsers)];
            String dir = randomDirName();
            String ext = randomExt();
            String path = String.format("/vol/share/user%d/%s/file_added_%d.%s", user, dir, i + random.nextInt(100000), ext);
            long size = randomSize(random);
            FileInfo info = new FileInfo(path, ext, size, user);
            files.put(path, info);

            String changeTime;
            if (random.nextDouble() < afterHoursProb) {
                changeTime = generateAfterHoursTimestamp(dayStart);
            } else {
                changeTime = instantToISO(dayStart.plusSeconds(random.nextInt(hoursWindow * 3600)));
            }
            diffs.add(new DiffEntry(path, "added", String.valueOf(size), changeTime));
        }

        if (isBusyDay) {
            int burstOps = 30 + random.nextInt(71);
            int burstStartSec = random.nextInt(2 * 3600);
            int burstDur = 300 + random.nextInt(301);

            List<FileInfo> burstCandidates = new ArrayList<>();
            for (FileInfo fi : files.values()) {
                if (activeUserSet.contains(fi.userIndex)) burstCandidates.add(fi);
            }

            for (int i = 0; i < burstOps && !burstCandidates.isEmpty(); i++) {
                int idx = random.nextInt(burstCandidates.size());
                FileInfo fi = burstCandidates.get(idx);

                double delta = (random.nextDouble() * 0.6) - 0.3;
                long newSize = (long) (fi.size * (1.0 + delta));
                if (newSize < 1024L) newSize = 1024L;
                FileInfo updated = new FileInfo(fi.path, fi.extension, newSize, fi.userIndex);
                files.put(fi.path, updated);
                burstCandidates.set(idx, updated);

                int offset = burstStartSec + random.nextInt(burstDur);
                diffs.add(new DiffEntry(updated.path, "modified", String.valueOf(newSize),
                        instantToISO(dayStart.plusSeconds(offset))));
            }
        }

        diffs.addAll(generateDirectoryMtimeEntries(diffs));
        return diffs;
    }

    private String generateAfterHoursTimestamp(Instant dayStart) {
        int hour;
        if (random.nextBoolean()) {
            hour = 18 + random.nextInt(6);
        } else {
            hour = 5 + random.nextInt(3);
        }
        int minute = random.nextInt(60);
        int second = random.nextInt(60);
        Instant ts = dayStart.atZone(ZoneOffset.UTC)
                .withHour(hour).withMinute(minute).withSecond(second)
                .toInstant();
        return instantToISO(ts);
    }

    private int randomIndex(int bound) {
        return random.nextInt(Math.max(1, bound));
    }

    private void shuffleArray(int[] arr, Random rnd) {
        for (int i = arr.length - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int tmp = arr[i]; arr[i] = arr[j]; arr[j] = tmp;
        }
    }

    /**
     * Generate an "extremely quiet day" — 10-20% of normal operation count,
     * spread over 2-4 hours, no bursts. Mixed operation types.
     * Many features should drop BELOW baseline (total_ops, burst_velocity, etc.)
     */
    public List<DiffEntry> evolveExtremelyQuietDay(Instant dayStart, Random externalRng) {
        int totalOps = 500 + externalRng.nextInt(1000);

        double modFraction = 0.35 + externalRng.nextDouble() * 0.30;
        double addFraction = 0.15 + externalRng.nextDouble() * 0.20;
        int modifyCount = Math.max(1, (int) Math.round(totalOps * modFraction));
        int addCount = Math.max(1, (int) Math.round(totalOps * addFraction));
        int deleteCount = Math.max(0, totalOps - modifyCount - addCount);

        int[] allUsers = {1,2,3,4,5,6,7,8,9,10};
        Set<Integer> activeUserSet = new HashSet<>();
        for (int u : allUsers) activeUserSet.add(u);

        int hoursWindow = 2 + externalRng.nextInt(3);

        List<FileInfo> candidateFiles = new ArrayList<>();
        for (FileInfo fi : files.values()) {
            if (activeUserSet.contains(fi.userIndex)) candidateFiles.add(fi);
        }

        List<DiffEntry> diffs = new ArrayList<>();

        for (int i = 0; i < modifyCount && !candidateFiles.isEmpty(); i++) {
            int idx = externalRng.nextInt(candidateFiles.size());
            FileInfo info = candidateFiles.get(idx);

            double delta = (externalRng.nextDouble() * 0.6) - 0.3;
            long newSize = (long) (info.size * (1.0 + delta));
            if (newSize < 1024L) newSize = 1024L;
            FileInfo updated = new FileInfo(info.path, info.extension, newSize, info.userIndex);
            files.put(info.path, updated);
            candidateFiles.set(idx, updated);

            String changeTime = instantToISO(dayStart.plusSeconds(externalRng.nextInt(hoursWindow * 3600)));
            diffs.add(new DiffEntry(updated.path, "modified", String.valueOf(newSize), changeTime));
        }

        List<FileInfo> toDelete = new ArrayList<>(candidateFiles);
        for (int i = 0; i < deleteCount && !toDelete.isEmpty(); i++) {
            int idx = externalRng.nextInt(toDelete.size());
            FileInfo info = toDelete.remove(idx);
            files.remove(info.path);
            diffs.add(new DiffEntry(info.path, "deleted", String.valueOf(info.size), Instant.MAX.toString()));
        }

        String[] dirs = {"projects","docs","mail","logs","configs","media","backup","archive","temp","data"};
        String[] exts = {"docx","xlsx","pptx","pdf","txt","log","conf","sh","py","java",
                "cpp","h","md","json","xml","sql","jpg","png","gif","mp3",
                "mp4","wav","zip","tar","gz","db","yaml","csv"};
        for (int i = 0; i < addCount; i++) {
            int user = 1 + externalRng.nextInt(10);
            String dir = dirs[externalRng.nextInt(dirs.length)];
            String ext = exts[externalRng.nextInt(exts.length)];
            String path = String.format("/vol/share/user%d/%s/quiet_add_%d.%s", user, dir, externalRng.nextInt(100000), ext);
            long size = 1024L * (1L + externalRng.nextInt(100));
            FileInfo info = new FileInfo(path, ext, size, user);
            files.put(path, info);
            String changeTime = instantToISO(dayStart.plusSeconds(externalRng.nextInt(hoursWindow * 3600)));
            diffs.add(new DiffEntry(path, "added", String.valueOf(size), changeTime));
        }

        diffs.addAll(generateDirectoryMtimeEntries(diffs));
        return diffs;
    }

    public List<DiffEntry> evolveIrregularNormalRound(Instant dayStart, String pattern, Random externalRng) {
        switch (pattern) {
            case "batch_compile": return evolveBatchCompile(dayStart);
            case "log_rotation": return evolveLogRotation(dayStart);
            case "backup_surge": return evolveBackupSurge(dayStart);
            case "mass_rename": return evolveMassRename(dayStart, externalRng);
            case "db_checkpoint": return evolveDbCheckpoint(dayStart);
            case "after_hours_burst": return evolveAfterHoursBurst(dayStart);
            case "migration_wave": return evolveMigrationWave(dayStart);
            case "cleanup_purge": return evolveCleanupPurge(dayStart);
            default: return evolveNormalRound(dayStart);
        }
    }

    public List<DiffEntry> evolveBatchCompile(Instant dayStart) {
        int targetUser = 1 + random.nextInt(10);
        long tBase = dayStart.getEpochSecond();
        int burstStart = random.nextInt(3600);
        int burstDur = 120 + random.nextInt(180);
        List<DiffEntry> diffs = new ArrayList<>();
        List<FileInfo> userFiles = new ArrayList<>();
        for (FileInfo fi : files.values()) {
            if (fi.userIndex == targetUser && (fi.extension.equals("java") || fi.extension.equals("cpp") || fi.extension.equals("h"))) {
                userFiles.add(fi);
            }
        }
        for (int i = 0; i < userFiles.size() && diffs.size() < 3000; i++) {
            FileInfo fi = userFiles.get(i);
            long delta = (long) (fi.size * (0.01 + random.nextDouble() * 0.04));
            long newSize = fi.size + delta;
            FileInfo updated = new FileInfo(fi.path, fi.extension, newSize, fi.userIndex);
            files.put(fi.path, updated);
            int offset = burstStart + random.nextInt(burstDur);
            diffs.add(new DiffEntry(updated.path, "modified", String.valueOf(updated.size),
                    instantToISO(dayStart.plusSeconds(offset))));
        }
        int extraOps = 500 + random.nextInt(1500);
        for (int i = 0; i < extraOps; i++) {
            int user = 1 + random.nextInt(10);
            String dir = randomDirName();
            String ext = randomExt();
            String path = String.format("/vol/share/user%d/%s/compile_out_%d.%s", user, dir, random.nextInt(100000), ext);
            long size = randomSize(random);
            files.put(path, new FileInfo(path, ext, size, user));
            diffs.add(new DiffEntry(path, "added", String.valueOf(size),
                    instantToISO(dayStart.plusSeconds(random.nextInt(8 * 3600)))));
        }
        diffs.addAll(generateDirectoryMtimeEntries(diffs));
        return diffs;
    }

    public List<DiffEntry> evolveLogRotation(Instant dayStart) {
        List<DiffEntry> diffs = new ArrayList<>();
        long tBase = dayStart.getEpochSecond();
        int rotationWindow = 60 + random.nextInt(120);
        int rotationStart = random.nextInt(7200);
        List<FileInfo> logFiles = new ArrayList<>();
        for (FileInfo fi : files.values()) {
            if (fi.extension.equals("log") || fi.extension.equals("gz")) {
                logFiles.add(fi);
            }
        }
        int delCount = Math.min(logFiles.size(), 200 + random.nextInt(300));
        Collections.shuffle(logFiles, random);
        for (int i = 0; i < delCount; i++) {
            FileInfo fi = logFiles.get(i);
            files.remove(fi.path);
            diffs.add(new DiffEntry(fi.path, "deleted", String.valueOf(fi.size), Instant.MAX.toString()));
        }
        int newLogCount = 300 + random.nextInt(700);
        for (int i = 0; i < newLogCount; i++) {
            int user = 1 + random.nextInt(10);
            String path = String.format("/vol/share/user%d/logs/app_%d.log", user, random.nextInt(100));
            long size = 1024L * (1 + random.nextInt(51200));
            files.put(path, new FileInfo(path, "log", size, user));
            diffs.add(new DiffEntry(path, "added", String.valueOf(size),
                    instantToISO(dayStart.plusSeconds(rotationStart + random.nextInt(rotationWindow)))));
        }
        diffs.addAll(generateDirectoryMtimeEntries(diffs));
        return diffs;
    }

    public List<DiffEntry> evolveBackupSurge(Instant dayStart) {
        List<DiffEntry> diffs = new ArrayList<>();
        int surgeStart = 2 * 3600 + random.nextInt(3600);
        int surgeDur = 1800 + random.nextInt(3600);
        int numUsers = 1 + random.nextInt(3);
        Set<Integer> backupUsers = new HashSet<>();
        for (int i = 0; i < numUsers; i++) backupUsers.add(1 + random.nextInt(10));
        String[] backupExts = {".bak", ".backup"};
        int backupCount = 500 + random.nextInt(2000);
        List<FileInfo> allFiles = new ArrayList<>(files.values());
        for (int i = 0; i < backupCount && !allFiles.isEmpty(); i++) {
            int bIdx = random.nextInt(allFiles.size());
            FileInfo fi = allFiles.get(bIdx);
            if (!backupUsers.contains(fi.userIndex)) continue;
            double pct = 0.02 + random.nextDouble() * 0.04;
            long newSize = (long) (fi.size * (1.0 + pct));
            int offset = surgeStart + random.nextInt(surgeDur);
            diffs.add(new DiffEntry(fi.path, "modified", String.valueOf(newSize),
                    instantToISO(dayStart.plusSeconds(offset))));
            FileInfo updated = new FileInfo(fi.path, fi.extension, newSize, fi.userIndex);
            files.put(fi.path, updated);
            allFiles.set(bIdx, updated);
        }
        int normalOps = 1000 + random.nextInt(2000);
        List<String> paths = new ArrayList<>(files.keySet());
        for (int i = 0; i < normalOps && !paths.isEmpty(); i++) {
            String path = paths.get(random.nextInt(paths.size()));
            FileInfo fi = files.get(path);
            if (fi == null) continue;
            double delta = (random.nextDouble() * 0.6) - 0.3;
            long sz = (long) (fi.size * (1.0 + delta));
            if (sz < 1024L) sz = 1024L;
            diffs.add(new DiffEntry(path, "modified", String.valueOf(sz),
                    instantToISO(dayStart.plusSeconds(random.nextInt(10 * 3600)))));
        }
        diffs.addAll(generateDirectoryMtimeEntries(diffs));
        return diffs;
    }

    public List<DiffEntry> evolveMassRename(Instant dayStart, Random rng) {
        List<DiffEntry> diffs = new ArrayList<>();
        int renameWindow = 300 + random.nextInt(600);
        int renameStart = random.nextInt(4 * 3600);
        int targetUser = 1 + random.nextInt(10);
        List<FileInfo> userFiles = new ArrayList<>();
        for (FileInfo fi : files.values()) {
            if (fi.userIndex == targetUser) userFiles.add(fi);
        }
        String srcExt = randomExt();
        String dstExt;
        String[] renameSuffixes = {"_v2", "_new", "_backup", "_old", "_copy"};
        dstExt = srcExt;
        int renameCount = 300 + random.nextInt(1500);
        int done = 0;
        for (FileInfo fi : new ArrayList<>(userFiles)) {
            if (done >= renameCount) break;
            if (!fi.path.endsWith("." + srcExt)) continue;
            String suffix = renameSuffixes[rng.nextInt(renameSuffixes.length)];
            String newPath = fi.path.replace("." + srcExt, suffix + "." + dstExt);
            if (files.containsKey(newPath)) continue;
            FileInfo renamed = new FileInfo(newPath, dstExt, fi.size, fi.userIndex);
            files.remove(fi.path);
            files.put(newPath, renamed);
            int offset = renameStart + random.nextInt(renameWindow);
            diffs.add(new DiffEntry(newPath, "modified", String.valueOf(renamed.size),
                    instantToISO(dayStart.plusSeconds(offset))));
            done++;
        }
        int normalOps = 2000 + random.nextInt(3000);
        List<String> paths = new ArrayList<>(files.keySet());
        for (int i = 0; i < normalOps && !paths.isEmpty(); i++) {
            String path = paths.get(random.nextInt(paths.size()));
            FileInfo fi = files.get(path);
            if (fi == null) continue;
            double delta = (random.nextDouble() * 0.4) - 0.2;
            long sz = (long) (fi.size * (1.0 + delta));
            if (sz < 1024L) sz = 1024L;
            diffs.add(new DiffEntry(path, "modified", String.valueOf(sz),
                    instantToISO(dayStart.plusSeconds(random.nextInt(10 * 3600)))));
        }
        diffs.addAll(generateDirectoryMtimeEntries(diffs));
        return diffs;
    }

    public List<DiffEntry> evolveDbCheckpoint(Instant dayStart) {
        List<DiffEntry> diffs = new ArrayList<>();
        int checkpointStart = random.nextInt(7200);
        int checkpointDur = 60 + random.nextInt(300);
        String[] dbExts = {"db", "sql", "csv", "mdb", "accdb"};
        Set<String> dbExtSet = new HashSet<>(Arrays.asList(dbExts));
        List<FileInfo> dbFiles = new ArrayList<>();
        for (FileInfo fi : files.values()) {
            if (dbExtSet.contains(fi.extension)) dbFiles.add(fi);
        }
        int modCount = Math.min(dbFiles.size(), 200 + random.nextInt(800));
        Collections.shuffle(dbFiles, random);
        for (int i = 0; i < modCount; i++) {
            FileInfo fi = dbFiles.get(i);
            double pct = -0.05 + random.nextDouble() * 0.10;
            long newSize = (long) (fi.size * (1.0 + pct));
            if (newSize < 1024L) newSize = 1024L;
            FileInfo updated = new FileInfo(fi.path, fi.extension, newSize, fi.userIndex);
            files.put(fi.path, updated);
            int offset = checkpointStart + random.nextInt(checkpointDur);
            diffs.add(new DiffEntry(updated.path, "modified", String.valueOf(updated.size),
                    instantToISO(dayStart.plusSeconds(offset))));
        }
        int normalOps = 3000 + random.nextInt(5000);
        List<String> paths = new ArrayList<>(files.keySet());
        for (int i = 0; i < normalOps && !paths.isEmpty(); i++) {
            String path = paths.get(random.nextInt(paths.size()));
            FileInfo fi = files.get(path);
            if (fi == null) continue;
            double delta = (random.nextDouble() * 0.6) - 0.3;
            long sz = (long) (fi.size * (1.0 + delta));
            if (sz < 1024L) sz = 1024L;
            diffs.add(new DiffEntry(path, "modified", String.valueOf(sz),
                    instantToISO(dayStart.plusSeconds(random.nextInt(10 * 3600)))));
        }
        diffs.addAll(generateDirectoryMtimeEntries(diffs));
        return diffs;
    }

    public List<DiffEntry> evolveAfterHoursBurst(Instant dayStart) {
        List<DiffEntry> diffs = new ArrayList<>();
        int burstHour = 22 + random.nextInt(4);
        int burstStartSec = burstHour * 3600 + random.nextInt(1800);
        int burstDur = 300 + random.nextInt(600);
        int numUsers = 1 + random.nextInt(2);
        Set<Integer> burstUsers = new HashSet<>();
        for (int i = 0; i < numUsers; i++) burstUsers.add(1 + random.nextInt(10));
        int burstOps = 800 + random.nextInt(4000);
        List<FileInfo> userFiles = new ArrayList<>();
        for (FileInfo fi : files.values()) {
            if (burstUsers.contains(fi.userIndex)) userFiles.add(fi);
        }
        for (int i = 0; i < burstOps && !userFiles.isEmpty(); i++) {
            FileInfo fi = userFiles.get(random.nextInt(userFiles.size()));
            double delta = (random.nextDouble() * 0.3) - 0.15;
            long newSize = (long) (fi.size * (1.0 + delta));
            if (newSize < 1024L) newSize = 1024L;
            FileInfo updated = new FileInfo(fi.path, fi.extension, newSize, fi.userIndex);
            files.put(fi.path, updated);
            int offset = burstStartSec + random.nextInt(burstDur);
            diffs.add(new DiffEntry(updated.path, "modified", String.valueOf(updated.size),
                    instantToISO(dayStart.plusSeconds(offset))));
        }
        int normalOps = 2000 + random.nextInt(5000);
        List<String> paths = new ArrayList<>(files.keySet());
        for (int i = 0; i < normalOps && !paths.isEmpty(); i++) {
            String path = paths.get(random.nextInt(paths.size()));
            FileInfo fi = files.get(path);
            if (fi == null) continue;
            double delta = (random.nextDouble() * 0.5) - 0.25;
            long sz = (long) (fi.size * (1.0 + delta));
            if (sz < 1024L) sz = 1024L;
            int hour = 8 + random.nextInt(10);
            int sec = hour * 3600 + random.nextInt(3600);
            diffs.add(new DiffEntry(path, "modified", String.valueOf(sz),
                    instantToISO(dayStart.plusSeconds(sec))));
        }
        diffs.addAll(generateDirectoryMtimeEntries(diffs));
        return diffs;
    }

    public List<DiffEntry> evolveMigrationWave(Instant dayStart) {
        List<DiffEntry> diffs = new ArrayList<>();
        int srcUser = 1 + random.nextInt(10);
        int dstUser = 1 + random.nextInt(10);
        while (dstUser == srcUser) dstUser = 1 + random.nextInt(10);
        int waveStart = random.nextInt(3600);
        int waveDur = 2 * 3600 + random.nextInt(2 * 3600);
        List<FileInfo> srcFiles = new ArrayList<>();
        for (FileInfo fi : files.values()) {
            if (fi.userIndex == srcUser) srcFiles.add(fi);
        }
        int migrateCount = Math.min(srcFiles.size(), 1000 + random.nextInt(3000));
        Collections.shuffle(srcFiles, random);
        for (int i = 0; i < migrateCount; i++) {
            FileInfo fi = srcFiles.get(i);
            String newPath = fi.path.replace("/user" + srcUser + "/", "/user" + dstUser + "/");
            if (files.containsKey(newPath)) continue;
            FileInfo migrated = new FileInfo(newPath, fi.extension, fi.size, dstUser);
            files.remove(fi.path);
            files.put(newPath, migrated);
            int offset = waveStart + (int) ((double) i / migrateCount * waveDur);
            diffs.add(new DiffEntry(newPath, "added", String.valueOf(migrated.size),
                    instantToISO(dayStart.plusSeconds(offset))));
        }
        int delCount = 100 + random.nextInt(500);
        List<String> paths = new ArrayList<>(files.keySet());
        for (int i = 0; i < delCount && !paths.isEmpty(); i++) {
            String path = paths.get(random.nextInt(paths.size()));
            FileInfo fi = files.get(path);
            if (fi == null || fi.userIndex != srcUser) continue;
            files.remove(path);
            diffs.add(new DiffEntry(path, "deleted", String.valueOf(fi.size), Instant.MAX.toString()));
        }
        diffs.addAll(generateDirectoryMtimeEntries(diffs));
        return diffs;
    }

    public List<DiffEntry> evolveCleanupPurge(Instant dayStart) {
        List<DiffEntry> diffs = new ArrayList<>();
        int purgeStart = random.nextInt(3600);
        int purgeDur = 300 + random.nextInt(600);
        int numUsers = 2 + random.nextInt(5);
        Set<Integer> purgeUsers = new HashSet<>();
        for (int i = 0; i < numUsers; i++) purgeUsers.add(1 + random.nextInt(10));
        String[] purgeExts = {"tmp", "temp", "log", "bak", "cache"};
        Set<String> purgeExtSet = new HashSet<>(Arrays.asList(purgeExts));
        List<FileInfo> purgeFiles = new ArrayList<>();
        for (FileInfo fi : files.values()) {
            if (purgeUsers.contains(fi.userIndex) && purgeExtSet.contains(fi.extension)) {
                purgeFiles.add(fi);
            }
        }
        int delCount = Math.min(purgeFiles.size(), 500 + random.nextInt(2000));
        Collections.shuffle(purgeFiles, random);
        for (int i = 0; i < delCount; i++) {
            FileInfo fi = purgeFiles.get(i);
            files.remove(fi.path);
            int offset = purgeStart + random.nextInt(purgeDur);
            diffs.add(new DiffEntry(fi.path, "deleted", String.valueOf(fi.size), Instant.MAX.toString()));
        }
        int normalOps = 3000 + random.nextInt(5000);
        List<String> paths = new ArrayList<>(files.keySet());
        for (int i = 0; i < normalOps && !paths.isEmpty(); i++) {
            String path = paths.get(random.nextInt(paths.size()));
            FileInfo fi = files.get(path);
            if (fi == null) continue;
            double delta = (random.nextDouble() * 0.5) - 0.25;
            long sz = (long) (fi.size * (1.0 + delta));
            if (sz < 1024L) sz = 1024L;
            diffs.add(new DiffEntry(path, "modified", String.valueOf(sz),
                    instantToISO(dayStart.plusSeconds(random.nextInt(10 * 3600)))));
        }
        diffs.addAll(generateDirectoryMtimeEntries(diffs));
        return diffs;
    }

    /**
     * Generate POSIX-compliant directory mtime entries for parent directories
     * affected by file additions or deletions.
     *
     * Rules:
     * - Only "added" and "deleted" entries trigger directory entries (not "modified")
     * - One entry per unique parent directory
     * - If any deletion occurred in a directory, change_time = Instant.MAX.toString()
     * - If only additions, change_time = max child addition change_time
     * - size = "0" for all directory entries
     */
    public static List<DiffEntry> generateDirectoryMtimeEntries(List<DiffEntry> fileDiffs) {
        // Map: parentDir -> [hasDeletion, maxAdditionTime]
        Map<String, Object[]> dirState = new LinkedHashMap<>();

        for (DiffEntry entry : fileDiffs) {
            if (!"added".equals(entry.type) && !"deleted".equals(entry.type)) continue;
            if (entry.path == null) continue;

            int lastSlash = entry.path.lastIndexOf('/');
            String parentDir = lastSlash > 0 ? entry.path.substring(0, lastSlash + 1) : null;
            if (parentDir == null) continue;

            Object[] state = dirState.get(parentDir);
            if (state == null) {
                state = new Object[]{false, ""}; // [hasDeletion, maxAdditionTime]
                dirState.put(parentDir, state);
            }

            if ("deleted".equals(entry.type)) {
                state[0] = true;
            } else {
                // "added" - track max change_time
                String ct = entry.change_time;
                if (ct != null && ct.compareTo((String) state[1]) > 0) {
                    state[1] = ct;
                }
            }
        }

        List<DiffEntry> dirEntries = new ArrayList<>();
        for (Map.Entry<String, Object[]> e : dirState.entrySet()) {
            Object[] state = e.getValue();
            boolean hasDeletion = (Boolean) state[0];
            String changeTime = hasDeletion ? Instant.MAX.toString() : (String) state[1];
            dirEntries.add(new DiffEntry(e.getKey(), "modified", "0", changeTime));
        }
        return dirEntries;
    }

    private String randomDirName() {
        String[] dirs = {"projects","docs","mail","logs","configs","media","backup","archive","temp","data"};
        return dirs[random.nextInt(dirs.length)];
    }

    private String randomExt() {
        String[] exts = {
                "docx","xlsx","pptx","pdf","txt","log","conf","sh","py","java",
                "cpp","h","md","json","xml","sql","jpg","png","gif","mp3",
                "mp4","wav","zip","tar","gz","db","yaml","csv"
        };
        return exts[random.nextInt(exts.length)];
    }

    private String instantToISO(Instant instant) {
        return instant.toString();
    }

    public Collection<FileInfo> getAllFiles() { return files.values(); }
    public int getFileCount() { return files.size(); }
    public FileInfo getFile(String path) { return files.get(path); }
    public void removeFile(String path) { files.remove(path); }
    public void addFile(String path, FileInfo info) { files.put(path, info); }

    /**
     * Snapshot the current files map (shallow copy). Since FileInfo is immutable,
     * sharing references is safe. Used to isolate attack state changes from the
     * main state used for normal round generation.
     */
    public Map<String, FileInfo> snapshot() {
        return new LinkedHashMap<>(files);
    }

    /**
     * Restore files map from a snapshot (shallow copy). Random state is preserved.
     */
    public void restore(Map<String, FileInfo> snapshot) {
        this.files.clear();
        this.files.putAll(snapshot);
    }

    public Random getRandom() { return random; }

    public List<String> samplePaths(int count) {
        List<String> paths = new ArrayList<>(files.keySet());
        List<String> result = new ArrayList<>();
        for (int i = 0; i < count && !paths.isEmpty(); i++) {
            result.add(paths.get(random.nextInt(paths.size())));
        }
        return result;
    }

    public List<FileInfo> sampleFilesForUser(int user, int count) {
        List<FileInfo> userFiles = new ArrayList<>();
        for (FileInfo fi : files.values()) {
            if (fi.userIndex == user) userFiles.add(fi);
        }
        List<FileInfo> result = new ArrayList<>();
        for (int i = 0; i < count && !userFiles.isEmpty(); i++) {
            result.add(userFiles.get(random.nextInt(userFiles.size())));
        }
        return result;
    }

    public List<FileInfo> sampleFilesForUserExt(int user, String[] exts, int count) {
        List<FileInfo> matched = new ArrayList<>();
        for (FileInfo fi : files.values()) {
            if (fi.userIndex != user) continue;
            for (String ext : exts) {
                if (fi.path.endsWith(ext)) {
                    matched.add(fi);
                    break;
                }
            }
        }
        List<FileInfo> result = new ArrayList<>();
        for (int i = 0; i < count && !matched.isEmpty(); i++) {
            result.add(matched.get(random.nextInt(matched.size())));
        }
        return result;
    }

    public static class FileInfo {
        public final String path;
        public final String extension;
        public final long size;
        public final int userIndex;

        public FileInfo(String path, String extension, long size, int userIndex) {
            this.path = path;
            this.extension = extension;
            this.size = size;
            this.userIndex = userIndex;
        }
    }
}
