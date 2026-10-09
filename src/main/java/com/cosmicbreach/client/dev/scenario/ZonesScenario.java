package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.dev.DevCamera;
import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.world.AetheriaSpots;
import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.gen.AetheriaTerrain;
import com.cosmicbreach.world.gen.BreachShape;
import com.cosmicbreach.world.gen.DeepSpans;
import com.cosmicbreach.world.gen.DeepSurvey;
import com.cosmicbreach.world.gen.DeepZones;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

/**
 * The layer 3 zones (Aetheria 1.2) in the game. For each zone (the Spans, the Lichen Gardens, the Hanging Wood, the
 * Shattered Field):
 *
 * <ol>
 *   <li>finds a pillar well inside a region of that zone (from the terrain model), puts the player there flying and a
 *       camera at a view that shows the zone's shape, and saves one screenshot ({@code zone_<name>.png}, HUD hidden,
 *       noon, clouds off);</li>
 *   <li>measures walkable coverage from the generated chunks around it: of the columns whose biome is the zone's, the
 *       share with a solid block at Y 24 to 142 under two blocks a player fits through (the model's rule,
 *       {@link DeepSurvey}, but read from real blocks, so features count);</li>
 *   <li>times the generation of a fresh 6 by 6 chunk area elsewhere in the same zone.</li>
 * </ol>
 * Passes when every zone was found, drawn and measured, and each zone's biome covered most of its sample.
 */
public final class ZonesScenario implements Scenario {
    private static final String[] NAMES = {"spans", "lichen_gardens", "hanging_wood", "shattered_field"};
    private static final List<ResourceKey<Biome>> BIOMES = List.of(AetheriaWorld.RIFT_ABYSS, AetheriaWorld.LICHEN_GARDENS,
            AetheriaWorld.HANGING_WOOD, AetheriaWorld.SHATTERED_FIELD);
    private static final int SAMPLE_CHUNKS = 9;
    private static final int GEN_CHUNKS = 6;
    /** Screenshots per zone, each from a different pillar. */
    private static final int VIEWS = 3;
    /** Blocks counted in each zone's sample (per chunk in the report): what the zone's features put there. */
    @SuppressWarnings("unchecked")
    private static final java.util.function.Supplier<net.minecraft.world.level.block.Block>[] CENSUS = new java.util.function.Supplier[] {
            com.cosmicbreach.world.feature.ZoneBlocks.GIANT_UMBRAL_CAP, com.cosmicbreach.registry.ModBlocks.MAGENTA_NEON_LICHEN,
            com.cosmicbreach.registry.ModBlocks.TEAL_NEON_LICHEN, com.cosmicbreach.registry.ModBlocks.ECLIPSIUM_ORE,
            com.cosmicbreach.registry.ModBlocks.RIFT_GLASS, () -> net.minecraft.world.level.block.Blocks.CHAIN,
            com.cosmicbreach.provision.ProvisionRegistry.UMBRAL_CAP};
    private static final String[] CENSUS_NAMES = {"giant cap", "magenta lichen", "teal lichen", "eclipsium", "rift glass", "chain", "small cap"};

    @Override
    public int timeBudgetSeconds() {
        return 1100;
    }

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        DevCamera[] camera = {null};
        List<String> report = new ArrayList<>();
        steps.command("gamemode creative")
                .command("gamerule doDaylightCycle false")
                .command("time set noon")
                .run("clouds off, render distance 10, HUD hidden", () -> {
                    mc.options.cloudStatus().set(CloudStatus.OFF);
                    mc.options.renderDistance().set(10);
                    mc.options.hideGui = true;
                })
                .command("execute in " + AetheriaWorld.LEVEL.location() + " run tp @s 700 120 700")
                .run("fly", () -> {
                    mc.player.getAbilities().flying = true;
                    mc.player.onUpdateAbilities();
                })
                .waitUntil("in Aetheria", 600, () -> mc.level != null && mc.level.dimension() == AetheriaWorld.LEVEL)
                .run("fly in Aetheria", () -> {
                    mc.player.getAbilities().flying = true;
                    mc.player.onUpdateAbilities();
                });
        for (int zone = 0; zone < DeepZones.COUNT; zone++) {
            int z = zone;
            Vec3[] view = new Vec3[2];
            Vec3[] first = new Vec3[1];
            BlockPos[] far = {null};
            long[] started = {0};
            for (int shot = 0; shot < VIEWS; shot++) {
                int k = shot;
                String label = "zone_" + NAMES[z] + (k == 0 ? "" : "_" + (k + 1));
                steps.run("find view " + (k + 1) + " in zone " + NAMES[z], () -> server(s -> {
                            AetheriaTerrain t = AetheriaSpots.terrain(aetheria(s));
                            Vec3[] v = view(t, z, k);
                            if (v == null) {
                                throw new Steps.Failure("no " + NAMES[z] + " pillar found for view " + (k + 1));
                            }
                            view[0] = v[0];
                            view[1] = v[1];
                            if (k == 0) {
                                first[0] = v[0];
                                far[0] = farSpot(t, z, v[0]);
                            }
                            // the player waits 30 blocks behind the camera, out of the frame (the camera sees the
                            // player's own model if it stands where the camera is)
                            Vec3 back = v[0].subtract(v[1]).normalize().scale(30);
                            Vec3 at = v[0].add(back.x, 0, back.z);
                            ServerPlayer p = player(s);
                            p.teleportTo(aetheria(s), at.x, v[0].y, at.z, java.util.Set.of(), 0f, 0f);
                            p.getAbilities().flying = true;
                            p.onUpdateAbilities();
                            p.setDeltaMovement(Vec3.ZERO);
                            return null;
                        }, 60))
                        .waitUntil("the view is drawn", 1600, settled(mc, 1500))
                        .run("camera on the view", () -> {
                            camera[0] = DevCamera.create(mc.level);
                            camera[0].place(view[0], view[1]);
                            camera[0].use();
                        })
                        .waitTicks(100)
                        .screenshot(label)
                        .run("camera off", () -> camera[0].remove());
            }
            steps
                    .run("measure " + NAMES[z], () -> report.add(server(s -> coverage(aetheria(s), BlockPos.containing(first[0]), z), 120)))
                    .log(NAMES[z] + " coverage", () -> report.get(report.size() - 1))
                    .run("force-load a fresh area of " + NAMES[z], () -> server(s -> {
                        started[0] = System.nanoTime();
                        forceArea(aetheria(s), far[0], true);
                        return null;
                    }, 10))
                    .waitUntil(NAMES[z] + " area generated", 6000, () -> server(s -> generated(aetheria(s), far[0]), 5))
                    .run("time " + NAMES[z], () -> {
                        double ms = (System.nanoTime() - started[0]) / 1e6;
                        report.add(String.format(Locale.ROOT, "%s generation: %d chunks in %.0f ms (%.1f ms per chunk) at %s", NAMES[z],
                                GEN_CHUNKS * GEN_CHUNKS, ms, ms / (GEN_CHUNKS * GEN_CHUNKS), far[0].toShortString()));
                    })
                    .log(NAMES[z] + " timing", () -> report.get(report.size() - 1))
                    .run("unforce " + NAMES[z], () -> server(s -> {
                        forceArea(aetheria(s), far[0], false);
                        return null;
                    }, 10))
                    .waitTicks(20);
        }
        steps.log("zones summary", () -> String.join("; ", report));
    }

    /** A view of zone {@code z}: an eye and a point it looks at, at a pillar well inside one of the zone's regions. */
    static Vec3[] view(AetheriaTerrain t, int z, int skip) {
        List<Vec3> taken = new ArrayList<>();
        for (int ring = 7; ring < 60; ring++) {
            for (int i = -ring; i <= ring; i++) {
                for (int[] c : new int[][] {{i, -ring}, {i, ring}, {-ring, i}, {ring, i}}) {
                    DeepSpans.Pillar p = t.deep.pillar(c[0], c[1]);
                    if (!p.exists || p.zone != z || p.clearAxis >= 0 || nearClearing(t, p) || !inside(t, p.cx, p.cz, z)) {
                        continue;
                    }
                    Vec3[] v = viewAt(t, p, z, skip);
                    if (v == null || taken.stream().anyMatch(e -> e.distanceTo(v[0]) < 300)) {
                        continue;
                    }
                    if (taken.size() == skip) {
                        return v;
                    }
                    taken.add(v[0]);
                }
            }
        }
        return null;
    }

    private static boolean inside(AetheriaTerrain t, double x, double z, int zone) {
        if (Math.hypot(x, z) < DeepZones.HOME_RADIUS + 150) {
            return false;
        }
        for (int a = 0; a < 360; a += 45) {
            double r = Math.toRadians(a);
            if (t.zones.zoneAt(x + Math.cos(r) * 90, z + Math.sin(r) * 90) != zone) {
                return false;
            }
        }
        return t.zones.zoneAt(x, z) == zone;
    }

    /** True if a clearing (where the boss arena may stand) is among the 3 by 3 cells around: views keep clear of it. */
    private static boolean nearClearing(AetheriaTerrain t, DeepSpans.Pillar p) {
        for (int di = -1; di <= 1; di++) {
            for (int dj = -1; dj <= 1; dj++) {
                if (t.deep.pillar(p.ci + di, p.cj + dj).clearAxis >= 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /** View {@code k} of a zone (each zone's three views look at it three ways). */
    private static Vec3[] viewAt(AetheriaTerrain t, DeepSpans.Pillar p, int z, int k) {
        switch (z) {
            case DeepZones.GARDENS -> {
                // standing on the landmass, looking across it
                // above the landmass, looking down across its hills
                double y = t.deep.gardenTop(p.cx, p.cz) + 19; // over the tallest caps
                double a = Math.toRadians((p.ci * 73 + p.cj * 151) % 360);
                return new Vec3[] {new Vec3(p.cx, y, p.cz),
                        new Vec3(p.cx + Math.cos(a) * 60, t.deep.gardenTop(p.cx + Math.cos(a) * 60, p.cz + Math.sin(a) * 60) - 6,
                                p.cz + Math.sin(a) * 60)};
            }
            case DeepZones.HANGING -> {
                double a = Math.toRadians((p.ci * 73 + p.cj * 151) % 360);
                if (k == 1) {
                    // under a mass, looking up at its tip
                    double low = p.top - p.capThick - 16;
                    return new Vec3[] {new Vec3(p.cx + Math.cos(a) * 14, low, p.cz + Math.sin(a) * 14), new Vec3(p.cx, p.top - p.capThick * 0.4, p.cz)};
                }
                if (k == 2) {
                    // well back, to show the wood fading into its fog
                    double y = p.top - p.capThick * 0.5;
                    return new Vec3[] {new Vec3(p.cx - Math.cos(a) * (p.platformR + 110), y + 8, p.cz - Math.sin(a) * (p.platformR + 110)),
                            new Vec3(p.cx, y - 4, p.cz)};
                }
                // inside the wood: among the masses and curtains, looking along a gap
                double y = p.top - p.capThick * 0.6;
                return new Vec3[] {new Vec3(p.cx - Math.cos(a) * (p.platformR + 30), y, p.cz - Math.sin(a) * (p.platformR + 30)),
                        new Vec3(p.cx + Math.cos(a) * 40, y + 6, p.cz + Math.sin(a) * 40)};
            }
            case DeepZones.SHATTERED -> {
                DeepSpans.Chunk best = null;
                for (DeepSpans.Chunk c : p.satellites()) {
                    double d = Math.hypot(c.x - p.cx, c.z - p.cz);
                    if (d > 20 && (best == null || c.r > best.r)) {
                        best = c;
                    }
                }
                if (best == null) {
                    return null;
                }
                if (k == 1) {
                    // below a chunk, looking up at its glowing underside
                    double low = best.top - best.depth - 12;
                    return new Vec3[] {new Vec3(best.x + 9, low, best.z + 6), new Vec3(best.x, best.top - best.depth * 0.5, best.z)};
                }
                if (k == 2) {
                    // flying between the chunks, at their own height
                    double mx = (best.x + p.cx) / 2;
                    double mz = (best.z + p.cz) / 2;
                    double y = (best.top + p.top) / 2 - 3;
                    return new Vec3[] {new Vec3(mx - (best.z - p.cz) * 0.6, y, mz + (best.x - p.cx) * 0.6), new Vec3(mx, y - 2, mz)};
                }
                // in the air off the main chunk's rim, looking across the gaps toward a far chunk
                double dx = best.x - p.cx;
                double dz = best.z - p.cz;
                double len = Math.hypot(dx, dz);
                double back = p.platformR + 10;
                return new Vec3[] {new Vec3(p.cx - dx / len * back, p.top + 4, p.cz - dz / len * back), new Vec3(best.x, best.top - 4, best.z)};
            }
            default -> {
                // from the side of a span, a little above it: the span, its two pillars and the void around them
                for (DeepSpans.Span sp : p.spans()) {
                    double[] m = sp.at(0.5);
                    double[] a = sp.at(0.0);
                    double[] b = sp.at(1.0);
                    double dx = b[0] - a[0];
                    double dz = b[2] - a[2];
                    double len = Math.hypot(dx, dz);
                    if (len < 25) {
                        continue;
                    }
                    double nx = -dz / len;
                    double nz = dx / len;
                    if (sp.breakHalf <= 0) {
                        continue; // show the broken ones
                    }
                    if (k == 1) {
                        // a little above deck height, looking along a broken span toward its break
                        double[] from = sp.at(Math.max(0.0, sp.breakT - 0.35));
                        double[] to = sp.at(Math.min(1.0, sp.breakT + 0.3));
                        return new Vec3[] {new Vec3(from[0], from[1] + 5, from[2]), new Vec3(to[0], to[1] - 2, to[2])};
                    }
                    double off = len * 0.75 + 10;
                    return new Vec3[] {new Vec3(m[0] + nx * off - dx / len * 10, Math.min(m[1] + 14, 140), m[2] + nz * off - dz / len * 10),
                            new Vec3(m[0], m[1] - 4, m[2])};
                }
                return null;
            }
        }
    }

    /** The centre of a fresh area of zone {@code z}, well away from where the player is. */
    static BlockPos farSpot(AetheriaTerrain t, int z, Vec3 from) {
        for (int k = 0; k < 4000; k++) {
            double a = k * 2.399963;
            double r = 1400 + k * 3.0;
            double x = from.x + Math.cos(a) * r;
            double zz = from.z + Math.sin(a) * r;
            if (inside(t, x, zz, z) && Math.hypot(x, zz) > BreachShape.DEEP_RADIUS + 400) {
                return BlockPos.containing(x, 100, zz);
            }
        }
        return BlockPos.containing(from.x + 2000, 100, from.z);
    }

    /** Walkable coverage of the zone's columns in the chunks around {@code at}, read from the world. Server thread. */
    static String coverage(ServerLevel level, BlockPos at, int zone) {
        ResourceKey<Biome> want = BIOMES.get(zone);
        int cx0 = (at.getX() >> 4) - SAMPLE_CHUNKS / 2;
        int cz0 = (at.getZ() >> 4) - SAMPLE_CHUNKS / 2;
        long columns = 0;
        long walkable = 0;
        long other = 0;
        long[] census = new long[CENSUS.length];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < SAMPLE_CHUNKS; i++) {
            for (int j = 0; j < SAMPLE_CHUNKS; j++) {
                LevelChunk chunk = level.getChunk(cx0 + i, cz0 + j);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        int bx = ((cx0 + i) << 4) + x;
                        int bz = ((cz0 + j) << 4) + z;
                        if (!level.getBiome(pos.set(bx, 100, bz)).is(want)) {
                            other++;
                            continue;
                        }
                        columns++;
                        boolean head1 = true;
                        boolean head2 = true;
                        boolean counted = false;
                        for (int y = DeepSpans.CEIL_Y; y >= 1; y--) {
                            BlockState s = chunk.getBlockState(pos.set(x, y, z));
                            for (int k = 0; k < CENSUS.length; k++) {
                                if (s.is(CENSUS[k].get())) {
                                    census[k]++;
                                }
                            }
                            boolean solid = s.blocksMotion();
                            if (!counted && y >= DeepSurvey.MIN_Y && solid && head1 && head2) {
                                walkable++;
                                counted = true;
                            }
                            head2 = head1;
                            head1 = !solid;
                        }
                    }
                }
            }
        }
        if (columns < (columns + other) / 2) {
            throw new Steps.Failure(NAMES[zone] + ": only " + columns + " of " + (columns + other) + " sampled columns are in the zone's biome");
        }
        double chunks = columns / 256.0;
        StringBuilder blocks = new StringBuilder();
        for (int k = 0; k < CENSUS.length; k++) {
            blocks.append(String.format(Locale.ROOT, "%s%s %.1f", k == 0 ? "" : ", ", CENSUS_NAMES[k], census[k] / chunks));
        }
        return String.format(Locale.ROOT, "%s walkable coverage %.1f%% over %d columns (%d others skipped); per chunk: %s", NAMES[zone],
                100.0 * walkable / columns, columns, other, blocks);
    }

    private static void forceArea(ServerLevel level, BlockPos centre, boolean on) {
        int cx0 = (centre.getX() >> 4) - GEN_CHUNKS / 2;
        int cz0 = (centre.getZ() >> 4) - GEN_CHUNKS / 2;
        for (int i = 0; i < GEN_CHUNKS; i++) {
            for (int j = 0; j < GEN_CHUNKS; j++) {
                level.setChunkForced(cx0 + i, cz0 + j, on);
            }
        }
    }

    private static boolean generated(ServerLevel level, BlockPos centre) {
        int cx0 = (centre.getX() >> 4) - GEN_CHUNKS / 2;
        int cz0 = (centre.getZ() >> 4) - GEN_CHUNKS / 2;
        for (int i = 0; i < GEN_CHUNKS; i++) {
            for (int j = 0; j < GEN_CHUNKS; j++) {
                if (level.getChunkSource().getChunkNow(cx0 + i, cz0 + j) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    private static BooleanSupplier settled(Minecraft mc, int maxTicks) {
        int[] state = {0, 0, -1};
        return () -> {
            state[0]++;
            boolean ready = chunksAround(mc, 4);
            int rendered = mc.levelRenderer.countRenderedSections();
            if (ready && mc.levelRenderer.hasRenderedAllSections() && rendered == state[2]) {
                state[1]++;
            } else {
                state[1] = 0;
            }
            state[2] = rendered;
            return state[1] >= 40 || (state[0] >= maxTicks && ready);
        };
    }

    private static boolean chunksAround(Minecraft mc, int radius) {
        if (mc.screen != null || mc.level == null || mc.player == null) {
            return false;
        }
        int cx = mc.player.getBlockX() >> 4;
        int cz = mc.player.getBlockZ() >> 4;
        for (int dx = -radius; dx <= radius; dx += radius) {
            for (int dz = -radius; dz <= radius; dz += radius) {
                if (!mc.level.getChunkSource().hasChunk(cx + dx, cz + dz)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static ServerLevel aetheria(MinecraftServer server) {
        return server.getLevel(AetheriaWorld.LEVEL);
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().get(0);
    }

    private static <T> T server(Function<MinecraftServer, T> call, int timeoutSeconds) {
        MinecraftServer s = Minecraft.getInstance().getSingleplayerServer();
        if (s == null) {
            throw new Steps.Failure("no integrated server");
        }
        try {
            return s.submit(() -> call.apply(s)).get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof Steps.Failure f) {
                throw f;
            }
            throw new Steps.Failure("server call failed: " + cause);
        }
    }
}
