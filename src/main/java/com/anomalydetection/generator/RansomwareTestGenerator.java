package com.anomalydetection.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RansomwareTestGenerator {
    private final long seed;

    public RansomwareTestGenerator(long seed) { this.seed = seed; }

    public void generate(Path outputDir) throws Exception {
        Path roundsDir = outputDir.resolve("rounds");
        Files.createDirectories(roundsDir);

        FilesystemState state = new FilesystemState(seed, 300_000);
        state.initialize();

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        Instant baseTime = Instant.parse("2026-04-22T08:00:00Z");

        String[] attackTypes = {
            "lockbit_fast_mode", "conti_size_tiered",
            "database_priority", "single_user_rapid", "slow_distributed",
            "creeping_shrink", "revil_random_ext", "clop_companion",
            "wannacry_staged"
        };
        double[] paddingLevels = {0.20, 0.50, 0.70};

        // 60 rounds: 12 groups of (attack_p20, attack_p50, attack_p70) + 2 normal
        // Group k (0-indexed) starts at round 5k+2, with normals at 5k+1 and 5k+5
        // Round 1 is normal, rounds 57,58,59 are last attacks, round 60 is normal

        AttackGenerator attackGen = new AttackGenerator(state, seed + 1000);

        for (int round = 1; round <= 60; round++) {
            Instant dayStart = baseTime.plusSeconds((round - 1) * 86400L);

            int posInCycle = (round - 2) % 5;
            boolean isFirstOfGroup = (round >= 2) && (posInCycle == 0 || posInCycle == 1 || posInCycle == 2)
                    && ((round - 2) / 5 < attackTypes.length);

            int groupIdx = (round - 2) / 5;
            boolean isAttack = round >= 2 && groupIdx < attackTypes.length
                    && posInCycle >= 0 && posInCycle <= 2;

            List<DiffEntry> diffs;
            Map<String, FilesystemState.FileInfo> snapshot = null;
            if (isAttack) {
                snapshot = state.snapshot();
            }

            if (isAttack) {
                String type = attackTypes[groupIdx];
                double paddingRatio = paddingLevels[posInCycle];
                String levelTag = "p" + ((int)(paddingRatio * 100));
                diffs = dispatchAttack(attackGen, type, dayStart, paddingRatio);
                SnapdiffOutput output = buildOutput(diffs);
                String fileName = String.format("round_%03d_%s_%s.json", round, type, levelTag);
                mapper.writeValue(roundsDir.resolve(fileName).toFile(), output);
            } else {
                diffs = state.evolveNormalRound(dayStart);
                SnapdiffOutput output = buildOutput(diffs);
                String fileName = String.format("round_%03d.json", round);
                mapper.writeValue(roundsDir.resolve(fileName).toFile(), output);
            }

            if (snapshot != null) {
                state.restore(snapshot);
            }
        }
    }

    private List<DiffEntry> dispatchAttack(AttackGenerator gen, String type,
            Instant attackTime, double paddingRatio) {
        switch (type) {
            case "lockbit_fast_mode": return gen.generateLockBitFastMode(attackTime, paddingRatio);
            case "conti_size_tiered": return gen.generateContiSizeTiered(attackTime, paddingRatio);
            case "database_priority": return gen.generateDatabasePriority(attackTime, paddingRatio);
            case "single_user_rapid": return gen.generateSingleUserRapid(attackTime, paddingRatio);
            case "slow_distributed": return gen.generateSlowDistributed(attackTime, paddingRatio);
            case "creeping_shrink": return gen.generateCreepingShrink(attackTime, paddingRatio);
            case "revil_random_ext": return gen.generateRevilRandomExt(attackTime, paddingRatio);
            case "clop_companion": return gen.generateClopCompanion(attackTime, paddingRatio);
            case "wannacry_staged": return gen.generateWannaCryStaged(attackTime, paddingRatio);
            default: throw new IllegalArgumentException("Unknown attack type: " + type);
        }
    }

    private SnapdiffOutput buildOutput(List<DiffEntry> diffs) {
        int added = 0, modified = 0, deleted = 0;
        for (DiffEntry d : diffs) {
            if ("added".equals(d.type)) added++;
            else if ("modified".equals(d.type)) modified++;
            else if ("deleted".equals(d.type)) deleted++;
        }
        return new SnapdiffOutput(diffs, new SummaryOutput(added, modified, deleted));
    }

    public static void main(String[] args) throws Exception {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 42L;
        Path outputDir = args.length > 1 ? Path.of(args[1]) : Path.of("test-output");
        new RansomwareTestGenerator(seed).generate(outputDir);
        System.out.println("Test data generated in: " + outputDir);
    }
}
