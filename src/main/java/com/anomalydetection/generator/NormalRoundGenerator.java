package com.anomalydetection.generator;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Generates 10 normal-round snapdiff JSON files using mutation axes,
 * matching the benchmark specification in benchmark.md.
 *
 * Each round simulates realistic normal user activity on a 300,000-file
 * network share, with varying activity levels, operation mixes, user
 * participation, and temporal patterns.
 *
 * Output format per benchmark.md:
 * <pre>
 * {
 *   "diffs": [
 *     {"path": "...", "type": "modified", "size": "56832", "change_time": "2026-04-22T10:30:00Z"},
 *     ...
 *   ],
 *   "summary": {"files_added": 1, "files_modified": 1, "files_deleted": 1}
 * }
 * </pre>
 */
public class NormalRoundGenerator {

    // ──────────────────────────────────────────────────────────────────
    // Filesystem constants (matching benchmark.md § FilesystemState)
    // ──────────────────────────────────────────────────────────────────

    private static final String BASE_PATH = "/vol/share";
    private static final String[] USERS = {
        "user1", "user2", "user3", "user4", "user5",
        "user6", "user7", "user8", "user9", "user10"
    };
    private static final String[] DIRS = {
        "projects", "docs", "mail", "logs", "configs",
        "media", "backup", "archive", "temp", "data"
    };
    private static final String[] EXTENSIONS = {
        ".docx", ".xlsx", ".pptx", ".pdf", ".txt", ".log", ".conf",
        ".sh", ".py", ".java", ".cpp", ".h", ".md", ".json", ".xml",
        ".sql", ".jpg", ".png", ".gif", ".mp3", ".mp4", ".wav",
        ".zip", ".tar", ".gz", ".db", ".yaml", ".csv"
    };
    private static final int FILES_PER_DIR = 3000;

    /** Realistic rename suffixes per spec (spec.md § Requirement 1). */
    private static final String[] RENAME_SUFFIXES = {
        "_v2", "_v3", "_new", "_backup", "_old", "_final", "_copy", "_draft"
    };

    /**
     * File size distribution (benchmark.md):
     *   70%   1 KB – 100 KB
     *   20%   100 KB – 10 MB
     *    9%   10 MB – 100 MB
     *    1%   100 MB – 1 GB
     */
    private static final long[][] SIZE_BANDS = {
        {1024L, 102_400L},
        {102_400L, 10_485_760L},
        {10_485_760L, 104_857_600L},
        {104_857_600L, 1_073_741_824L}
    };
    private static final double[] SIZE_CDF = {0.70, 0.90, 0.99, 1.00};

    private final Random rng;

    /** Per-(user,dir) counter for generating unique added-file paths. */
    private final Map<String, Integer> addedFileCounters;

    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ISO_INSTANT;

    public NormalRoundGenerator(long seed) {
        this.rng = new Random(seed);
        this.addedFileCounters = new HashMap<>();
    }

    // ──────────────────────────────────────────────────────────────────
    // Round configuration (mutation axes)
    // ──────────────────────────────────────────────────────────────────

    /** All mutation-axis parameters for one normal round. */
    public record RoundConfig(
        String label,
        int totalOperations,
        double modFraction,       // 0.35–0.65
        double renameFraction,    // 0.05–0.15
        double addFraction,       // 0.15–0.35
        int activeUserCount,      // 3–10
        double hoursWindow,       // 2–12
        double afterHoursRatio,   // 0.08–0.18
        String roundType,         // "quiet" | "normal" | "busy"
        int startHour             // 8–17
    ) {}

    /**
     * 10 diverse round configs sampling the full mutation-axes space.
     *
     * Each row varies a different combination of axes to ensure broad
     * coverage of the "normal behaviour" distribution.
     */
    private List<RoundConfig> generateRoundConfigs() {
        return List.of(
            // 1: Quiet day — low activity, 3 users, wide window
            new RoundConfig("quiet-office-day",
                2800, 0.40, 0.08, 0.30, 3, 10.0, 0.12, "quiet", 9),
            // 2: Normal morning — medium activity, balanced
            new RoundConfig("normal-office-morning",
                8500, 0.50, 0.10, 0.22, 5,  4.0, 0.10, "normal", 9),
            // 3: Busy afternoon — high activity, 8 users, burst
            new RoundConfig("busy-afternoon-burst",
                18000, 0.55, 0.12, 0.18, 8,  6.0, 0.15, "busy", 14),
            // 4: Heavy edit session — high modify ratio
            new RoundConfig("heavy-edit-session",
                12000, 0.62, 0.06, 0.20, 6,  5.0, 0.08, "normal", 10),
            // 5: Cleanup + rename — high rename, many deletes
            new RoundConfig("batch-cleanup-rename",
                6500, 0.38, 0.14, 0.28, 4,  3.0, 0.18, "normal", 15),
            // 6: All-hands — all 10 users active, balanced ops
            new RoundConfig("all-hands-active",
                22000, 0.48, 0.10, 0.25, 10, 8.0, 0.10, "normal", 9),
            // 7: Quiet night automation — mostly adds, high after-hours
            new RoundConfig("quiet-night-automation",
                3200, 0.45, 0.05, 0.35, 3, 12.0, 0.85, "quiet", 22),
            // 8: Busy morning surge — high adds, short window
            new RoundConfig("busy-morning-surge",
                20000, 0.52, 0.08, 0.30, 7,  3.5, 0.08, "busy", 8),
            // 9: Log rotation — high rename and adds
            new RoundConfig("log-rotation-maintenance",
                7800, 0.36, 0.15, 0.32, 5,  2.5, 0.15, "normal", 12),
            // 10: Data migration — high adds, many users
            new RoundConfig("data-migration",
                15000, 0.42, 0.07, 0.38, 6,  7.0, 0.12, "normal", 10)
        );
    }

    // ──────────────────────────────────────────────────────────────────
    // File path & size helpers
    // ──────────────────────────────────────────────────────────────────

    /** Random original path from the 300K file space. */
    private String origPath(int u, int d, int f) {
        return String.format("%s/%s/%s/file_%04d%s",
            BASE_PATH, USERS[u], DIRS[d], f, EXTENSIONS[rng.nextInt(EXTENSIONS.length)]);
    }

    /** Unique path for a newly added file (counters ensure uniqueness). */
    private String addedPath(int u, int d) {
        String key = u + "/" + d;
        int c = addedFileCounters.merge(key, 1, Integer::sum);
        return String.format("%s/%s/%s/file_added_%d%s",
            BASE_PATH, USERS[u], DIRS[d], c, EXTENSIONS[rng.nextInt(EXTENSIONS.length)]);
    }

    /** Rename target with a realistic versioning suffix. */
    private String renameOf(String orig) {
        int dot = orig.lastIndexOf('.');
        String base = dot > 0 ? orig.substring(0, dot) : orig;
        String ext  = dot > 0 ? orig.substring(dot) : "";
        return base + RENAME_SUFFIXES[rng.nextInt(RENAME_SUFFIXES.length)] + ext;
    }

    /** Random file size following the benchmark distribution. */
    private long randomSize() {
        double roll = rng.nextDouble();
        int b = 0;
        while (b < SIZE_CDF.length && roll >= SIZE_CDF[b]) b++;
        b = Math.min(b, SIZE_BANDS.length - 1);
        return SIZE_BANDS[b][0] + (long)(rng.nextDouble()
            * (SIZE_BANDS[b][1] - SIZE_BANDS[b][0]));
    }

    /** Modified file: -30% … +30% of original size. */
    private long modSize(long orig) {
        return Math.max(1024L, (long)(orig * (1.0 + (rng.nextDouble() * 0.6 - 0.3))));
    }

    /** Renamed file: ±5% or unchanged. */
    private long renameSize(long orig) {
        if (rng.nextBoolean()) {
            return Math.max(1024L, (long)(orig * (1.0 + (rng.nextDouble() * 0.1 - 0.05))));
        }
        return orig;
    }

    // ──────────────────────────────────────────────────────────────────
    // Timestamp helpers
    // ──────────────────────────────────────────────────────────────────

    /** Format an Instant as ISO-8601 UTC (e.g. 2026-04-22T10:30:00Z). */
    private static String fmt(Instant ts) {
        return ts.atZone(ZoneOffset.UTC).format(ISO_FMT);
    }

    /** Random timestamp within the round's window, honouring after-hours ratio. */
    private Instant randTS(RoundConfig cfg, Instant dayStart) {
        double hr;
        boolean after = rng.nextDouble() < cfg.afterHoursRatio();
        if (after) {
            hr = rng.nextBoolean() ? 18.0 + rng.nextDouble() * 6.0   // 18:00–23:59
                                   :  5.0 + rng.nextDouble() * 3.0;  // 05:00–07:59
        } else {
            hr = cfg.startHour() + rng.nextDouble() * cfg.hoursWindow();
        }
        return dayStart.plusSeconds((long)(hr * 3600));
    }

    // ──────────────────────────────────────────────────────────────────
    // Selection
    // ──────────────────────────────────────────────────────────────────

    /** Pick {@code count} distinct active user indices. */
    private int[] pickUsers(int count) {
        List<Integer> pool = new ArrayList<>(USERS.length);
        for (int i = 0; i < USERS.length; i++) pool.add(i);
        Collections.shuffle(pool, rng);
        return pool.subList(0, count).stream().mapToInt(Integer::intValue).toArray();
    }

    // ──────────────────────────────────────────────────────────────────
    // JSON writer (manual — avoids external Jackson dependency)
    // ──────────────────────────────────────────────────────────────────

    /** Wrap a string in JSON double-quotes. */
    private static String jsonStr(String raw) {
        return '"' + raw + '"';
    }

    // ──────────────────────────────────────────────────────────────────
    // Round generation
    // ──────────────────────────────────────────────────────────────────

    /** Generate all 10 rounds into {@code outputDir} and return the created files. */
    public List<File> generateAll(Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        List<RoundConfig> configs = generateRoundConfigs();
        List<File> files = new ArrayList<>();

        for (int i = 0; i < configs.size(); i++) {
            File out = outputDir.resolve(
                String.format("round%02d-%s.json", i + 1, configs.get(i).label())).toFile();
            generateRound(configs.get(i), out);
            files.add(out);
        }
        return files;
    }

    /** Generate one normal round file. */
    private void generateRound(RoundConfig cfg, File file) throws IOException {
        addedFileCounters.clear();
        Instant dayStart = Instant.parse("2026-04-01T00:00:00Z")
            .plusSeconds((long) rng.nextInt(30) * 86400L);

        // ── Derive operation counts from fractions ──────────────────
        int renames  = (int) Math.round(cfg.totalOperations() * cfg.renameFraction());
        int adds     = (int) Math.round(cfg.totalOperations() * cfg.addFraction());
        int mods     = (int) Math.round(cfg.totalOperations() * cfg.modFraction());
        int dels     = cfg.totalOperations() - renames * 2 - adds - mods;
        if (dels < 0) { dels = 0; adds = cfg.totalOperations() - renames * 2 - mods; }
        if (adds < 0) { adds = 0; }

        int[] users = pickUsers(cfg.activeUserCount());

        // ── Collect operations to write ─────────────────────────────
        // Each entry: {path, type, size, timestamp}
        List<String[]> ops = new ArrayList<>(cfg.totalOperations());

        // busy-day burst: 50-200 operations in a 5-15 minute window
        if ("busy".equals(cfg.roundType())) {
            int burstCount = 50 + rng.nextInt(151);
            Instant burstStart = randTS(cfg, dayStart);
            int burstWindowSec = 300 + rng.nextInt(601);
            int actualBurst = Math.min(burstCount, mods);
            for (int b = 0; b < actualBurst; b++) {
                int u = users[rng.nextInt(users.length)];
                int d = rng.nextInt(DIRS.length);
                int f = rng.nextInt(FILES_PER_DIR);
                long sz = modSize(randomSize());
                ops.add(new String[]{origPath(u, d, f), "modified",
                    String.valueOf(sz), fmt(burstStart.plusSeconds(
                        (long)(rng.nextDouble() * burstWindowSec)))});
            }
            mods -= actualBurst;
        }

        // Modified entries
        for (int m = 0; m < mods; m++) {
            int u = users[rng.nextInt(users.length)];
            int d = rng.nextInt(DIRS.length);
            int f = rng.nextInt(FILES_PER_DIR);
            long sz = modSize(randomSize());
            ops.add(new String[]{origPath(u, d, f), "modified",
                String.valueOf(sz), fmt(randTS(cfg, dayStart))});
        }

        // Deleted entries (standalone)
        for (int e = 0; e < dels; e++) {
            int u = users[rng.nextInt(users.length)];
            int d = rng.nextInt(DIRS.length);
            int f = rng.nextInt(FILES_PER_DIR);
            ops.add(new String[]{origPath(u, d, f), "deleted",
                String.valueOf(randomSize()), fmt(randTS(cfg, dayStart))});
        }

        // Rename pairs (delete old + add new)
        for (int r = 0; r < renames; r++) {
            int u = users[rng.nextInt(users.length)];
            int d = rng.nextInt(DIRS.length);
            int f = rng.nextInt(FILES_PER_DIR);
            String oldP = origPath(u, d, f);
            String newP = renameOf(oldP);
            long sz = randomSize();
            String ts = fmt(randTS(cfg, dayStart));
            ops.add(new String[]{oldP, "deleted", String.valueOf(sz), ts});
            ops.add(new String[]{newP, "added", String.valueOf(renameSize(sz)), ts});
        }

        // Standalone added entries
        for (int a = 0; a < adds; a++) {
            int u = users[rng.nextInt(users.length)];
            int d = rng.nextInt(DIRS.length);
            ops.add(new String[]{addedPath(u, d), "added",
                String.valueOf(randomSize()), fmt(randTS(cfg, dayStart))});
        }

        // ── Count summary and sort by change_time ───────────────────
        ops.sort(Comparator.comparing(o -> o[3]));  // sort by timestamp

        int sumAdded = 0, sumModified = 0, sumDeleted = 0;
        for (String[] op : ops) {
            switch (op[1]) {
                case "added"    -> sumAdded++;
                case "modified" -> sumModified++;
                case "deleted"  -> sumDeleted++;
            }
        }

        // ── Write JSON ──────────────────────────────────────────────
        try (PrintWriter w = new PrintWriter(file.toPath().toFile(), "UTF-8")) {
            w.println("{");
            w.println("  \"diffs\": [");

            for (int i = 0; i < ops.size(); i++) {
                String[] op = ops.get(i);
                w.print("    {");
                w.print("\"path\": " + jsonStr(op[0]) + ", ");
                w.print("\"type\": " + jsonStr(op[1]) + ", ");
                w.print("\"size\": " + jsonStr(op[2]) + ", ");
                w.print("\"change_time\": " + jsonStr(op[3]));
                w.print(i < ops.size() - 1 ? "}," : "}");
                w.println();
            }

            w.println("  ],");
            w.println("  \"summary\": {");
            w.println("    \"files_added\": " + sumAdded + ",");
            w.println("    \"files_modified\": " + sumModified + ",");
            w.println("    \"files_deleted\": " + sumDeleted);
            w.println("  }");
            w.println("}");
        }

        System.out.printf("  ✓ %s  (%d entries: +%d ~%d −%d)  [%s]%n",
            file.getName(), ops.size(), sumAdded, sumModified, sumDeleted, cfg.roundType());
    }

    // ──────────────────────────────────────────────────────────────────
    // Entry point
    // ──────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws IOException {
        Path outputDir;
        if (args.length > 0) {
            outputDir = Paths.get(args[0]);
        } else {
            // Default: normal-rounds/ under the current working directory
            outputDir = Paths.get(System.getProperty("user.dir"), "normal-rounds");
        }

        System.out.println("Generating 10 normal-round snapdiff JSON files ...");
        System.out.println("Output: " + outputDir.toAbsolutePath());
        System.out.println();

        NormalRoundGenerator gen = new NormalRoundGenerator(42L);
        List<File> files = gen.generateAll(outputDir);

        System.out.println();
        System.out.println("Done — " + files.size() + " files generated.");
    }
}
