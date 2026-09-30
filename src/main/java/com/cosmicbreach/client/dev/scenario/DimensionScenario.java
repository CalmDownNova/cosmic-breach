package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.registry.ModBlocks;
import com.cosmicbreach.world.AetheriaSpots;
import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.LayerAttunement;
import com.cosmicbreach.world.ShearBand;
import com.cosmicbreach.world.ShearBands;
import com.cosmicbreach.world.WorldCommands;
import com.cosmicbreach.world.gen.AetheriaTerrain;
import com.cosmicbreach.world.gen.DeepSpans;
import com.cosmicbreach.world.gen.DriftBelts;
import com.cosmicbreach.world.gen.SpireField;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * Aetheria, the dimension (W2), in the game:
 *
 * <ol>
 *   <li>Each layer in daylight: a Shattered Spires island (ground view, a view across the gaps, looking
 *       down an island's edge), a Sunfield island, the Breach from the Reach's rim looking down and from
 *       above its axis, a big asteroid in the Drift, a platform in the Deep. Clouds off, render distance 12.</li>
 *   <li>Jump height in the Drift (0.4x gravity, jump x1.3) against the Reach, through the real jump key.</li>
 *   <li>Falling off the Reach unattuned: caught by Shear band A, back on the last block stood on, 4 damage,
 *       the subtitle. Attuned to the Drift: through band A with Slow Falling; then band B (no Deep
 *       attunement) catches and returns the player too.</li>
 *   <li>A zombie dropped into band A is removed, with no drops.</li>
 *   <li>Generation time of a 12 by 12 chunk area in Aetheria against the Nether and the superflat
 *       Overworld, and the ores those Aetheria chunks hold.</li>
 * </ol>
 */
public final class DimensionScenario implements Scenario {
    private static final int SETTLE_TICKS = 40;
    private static final int GEN_SIZE = 12;

    /** Parts of the scenario; {@code dimension} runs them all, {@code dimension-<part>} one. */
    public enum Part { VIEWS, RULES, TIMING }

    private final java.util.EnumSet<Part> parts;

    public DimensionScenario() {
        this.parts = java.util.EnumSet.allOf(Part.class);
    }

    public DimensionScenario(Part part) {
        this.parts = java.util.EnumSet.of(part);
    }

    @Override
    public int timeBudgetSeconds() {
        return 1100;
    }

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        double[] y0 = {0};
        double[] peak = {0};
        double[] jumps = {0, 0};
        BlockPos[] stood = {null};
        BlockPos[] sky = {null};
        UUID[] zombie = {null};
        long[] started = {0};
        List<String> timings = new ArrayList<>();

        steps.command("gamerule doDaylightCycle false")
                .run("clouds off, render distance 12", () -> {
                    mc.options.cloudStatus().set(CloudStatus.OFF);
                    mc.options.renderDistance().set(12);
                })
                .run("reset the Shear counters", () -> server(s -> {
                    ShearBands.resetCounters();
                    return null;
                }, 5));

        if (parts.contains(Part.VIEWS)) {
            views(steps, mc);
        }
        if (parts.contains(Part.RULES)) {
            rules(steps, mc, y0, peak, jumps, stood, sky, zombie);
        }
        if (parts.contains(Part.TIMING)) {
            timing(steps, mc, started, timings);
        }
    }

    /** Where the views are taken: away from the Breach, so each layer shows its ordinary self. */
    private static final int VIEW_X = 620;
    private static final int VIEW_Z = -380;

    private static void views(Steps steps, Minecraft mc) {
        com.cosmicbreach.client.dev.DevCamera[] camera = {null};
        steps.run("hide the HUD", () -> mc.options.hideGui = true)
                .command("execute in " + AetheriaWorld.LEVEL.location() + " run tp @s " + VIEW_X + " 400 " + VIEW_Z)
                .run("fly", () -> {
                    mc.player.getAbilities().flying = true;
                    mc.player.onUpdateAbilities();
                })
                .waitUntil("in Aetheria", 600, () -> inAetheria(mc));

        // ---------------------------------------------------------------- the Reach
        steps.command("cosmicbreach debug goto reach")
                .waitUntil("the island is drawn", 1600, settled(mc, 1500))
                .log("where", () -> where(mc))
                .screenshot("reach_ground")
                .run("turn around", () -> mc.player.setYRot(mc.player.getYRot() + 180))
                .waitTicks(2)
                .screenshot("reach_ground_back")
                .run("to the tallest spire nearby, looking up at it", () -> spireView(mc))
                .waitUntil("the view is drawn", 1200, settled(mc, 1100))
                .log("where", () -> where(mc))
                .screenshot("reach_spire")
                .run("to a snapped spire, looking at its stump and fallen top", () -> snappedView(mc))
                .waitUntil("the view is drawn", 1200, settled(mc, 1100))
                .log("where", () -> where(mc))
                .screenshot("reach_snapped")
                .run("to a rim, looking across a gap", () -> view(mc, true))
                .waitUntil("the view is drawn", 1200, settled(mc, 1100))
                .log("where", () -> where(mc))
                .screenshot("reach_gap")
                .run("to a rim, looking down its edge", () -> view(mc, false))
                .waitUntil("the view is drawn", 1200, settled(mc, 1100))
                .log("where", () -> where(mc))
                .screenshot("reach_edge_down")
                .command("cosmicbreach debug goto sunfield")
                .waitUntil("the Sunfield is drawn", 1600, settled(mc, 1500))
                .log("where", () -> where(mc))
                .screenshot("sunfield_ground");

        // ---------------------------------------------------------------- the Breach
        steps.command("cosmicbreach debug goto breach")
                .waitUntil("the Breach's rim is drawn", 1600, settled(mc, 1500))
                .log("where", () -> where(mc))
                .run("look across the Breach", () -> mc.player.setXRot(38))
                .waitTicks(2)
                .screenshot("breach_from_rim")
                .run("look down into it", () -> mc.player.setXRot(70))
                .waitTicks(2)
                .screenshot("breach_rim_down")
                .run("a camera out over the Breach, facing the rim's cliff", () -> {
                    double x = mc.player.getX();
                    double z = mc.player.getZ();
                    double r = Math.hypot(x, z);
                    double k = Math.max(0, r - 50) / r;
                    camera[0] = com.cosmicbreach.client.dev.DevCamera.create(mc.level);
                    camera[0].place(new net.minecraft.world.phys.Vec3(x * k, mc.player.getY() - 2, z * k),
                            new net.minecraft.world.phys.Vec3(x, mc.player.getY() - 14, z));
                    camera[0].use();
                })
                .waitTicks(20)
                .screenshot("breach_cliff")
                .run("back to the player's eyes", () -> camera[0].remove());

        // ---------------------------------------------------------------- the Drift and the Deep
        steps.command("execute in " + AetheriaWorld.LEVEL.location() + " run tp @s " + VIEW_X + " 250 " + VIEW_Z)
                .command("cosmicbreach debug goto drift")
                .waitUntil("the asteroid is drawn", 1600, settled(mc, 1500))
                .log("where", () -> where(mc))
                .screenshot("drift_asteroid")
                .run("a camera above the rock, looking out over the belt", () -> {
                    lookAtAsteroids(mc);
                    float yaw = mc.player.getYRot() * Mth.DEG_TO_RAD;
                    float pitch = mc.player.getXRot() * Mth.DEG_TO_RAD;
                    net.minecraft.world.phys.Vec3 eye = mc.player.getEyePosition().add(0, 7, 0);
                    net.minecraft.world.phys.Vec3 dir = new net.minecraft.world.phys.Vec3(-Mth.sin(yaw) * Mth.cos(pitch), -Mth.sin(pitch),
                            Mth.cos(yaw) * Mth.cos(pitch));
                    camera[0] = com.cosmicbreach.client.dev.DevCamera.create(mc.level);
                    camera[0].place(eye, eye.add(dir.scale(100)).add(0, -10, 0));
                    camera[0].use();
                })
                .waitTicks(20)
                .log("facing", () -> where(mc))
                .screenshot("drift_belt")
                .run("back to the player's eyes", () -> camera[0].remove())
                .command("execute in " + AetheriaWorld.LEVEL.location() + " run tp @s " + VIEW_X + " 120 " + VIEW_Z)
                .command("cosmicbreach debug goto deep")
                .waitUntil("the platform is drawn", 1600, settled(mc, 1500))
                .log("where", () -> where(mc))
                .screenshot("deep_platform")
                .run("look at the nearest other platform", () -> lookAtPillars(mc))
                .waitTicks(2)
                .log("facing", () -> where(mc))
                .screenshot("deep_view")
                .run("to a platform's rim where a span leaves, looking along it", () -> spanView(mc))
                .waitUntil("the view is drawn", 1200, settled(mc, 1100))
                .log("where", () -> where(mc))
                .screenshot("deep_span")
                .run("show the HUD", () -> mc.options.hideGui = false);
    }

    /** Stands the player on the island 30 to 45 blocks from the tallest spire within 160 blocks, facing it. */
    private static void spireView(Minecraft mc) {
        server(s -> {
            ServerPlayer p = player(s);
            ServerLevel level = aetheria(s);
            AetheriaTerrain t = AetheriaSpots.terrain(level);
            List<SpireField.Spire> spires = new ArrayList<>();
            t.spires.spiresTouching(p.getBlockX() - 160, p.getBlockZ() - 160, p.getBlockX() + 160, p.getBlockZ() + 160, spires);
            spires.sort((a, b) -> Double.compare(b.height, a.height));
            for (SpireField.Spire spire : spires) {
                for (int k = 0; k < 12; k++) {
                    double a = k * Math.PI / 6;
                    double d = 30 + (k % 3) * 7;
                    BlockPos probe = BlockPos.containing(spire.bx + Math.cos(a) * d, spire.groundY + 12, spire.bz + Math.sin(a) * d);
                    Optional<BlockPos> feet = AetheriaSpots.settle(level, probe, 4, 30);
                    if (feet.isPresent()) {
                        BlockPos f = feet.get();
                        double dx = spire.bx - f.getX();
                        double dz = spire.bz - f.getZ();
                        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                        float pitch = (float) -Math.toDegrees(Math.atan2(spire.standing * 0.45, Math.hypot(dx, dz)));
                        WorldCommands.teleport(p, level, new AetheriaSpots.Spot(f, yaw, pitch));
                        return null;
                    }
                }
            }
            throw new Steps.Failure("no spire with ground around it near " + p.blockPosition().toShortString());
        }, 90);
    }

    /** Drops a stone item with no motion at {@code at} in Aetheria; returns its id. */
    private static UUID dropItem(BlockPos at) {
        return server(s -> {
            ServerLevel level = aetheria(s);
            ItemEntity item = new ItemEntity(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5,
                    new net.minecraft.world.item.ItemStack(ModBlocks.STARFALL_STONE.get()), 0, 0, 0);
            item.setPickUpDelay(32767);
            level.addFreshEntity(item);
            return item.getUUID();
        }, 5);
    }

    /** {Shardlings within 128 blocks, packs of two or more, the biggest pack}. Server thread. */
    private static int[] packs(ServerPlayer player) {
        List<com.cosmicbreach.entity.shardling.Shardling> near = player.serverLevel().getEntitiesOfClass(
                com.cosmicbreach.entity.shardling.Shardling.class, player.getBoundingBox().inflate(128));
        java.util.Map<Object, Integer> byPack = new java.util.IdentityHashMap<>();
        for (var shardling : near) {
            if (shardling.pack() != null) {
                byPack.merge(shardling.pack(), 1, Integer::sum);
            }
        }
        int packs = (int) byPack.values().stream().filter(n -> n >= 2).count();
        int biggest = byPack.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        return new int[] {near.size(), packs, biggest};
    }

    /** Stands the player 18 to 30 blocks from the nearest snapped spire, facing between its stump and its fallen top. */
    private static void snappedView(Minecraft mc) {
        server(s -> {
            ServerPlayer p = player(s);
            ServerLevel level = aetheria(s);
            AetheriaTerrain t = AetheriaSpots.terrain(level);
            List<SpireField.Spire> spires = new ArrayList<>();
            t.spires.spiresTouching(p.getBlockX() - 400, p.getBlockZ() - 400, p.getBlockX() + 400, p.getBlockZ() + 400, spires);
            spires.removeIf(sp -> !sp.snapped);
            spires.sort((a, b) -> Double.compare(Math.hypot(a.bx - p.getX(), a.bz - p.getZ()), Math.hypot(b.bx - p.getX(), b.bz - p.getZ())));
            for (SpireField.Spire spire : spires) {
                double mx = (spire.bx + spire.f1x) / 2;
                double mz = (spire.bz + spire.f1z) / 2;
                double ax = spire.f1x - spire.bx;
                double az = spire.f1z - spire.bz;
                double len = Math.hypot(ax, az);
                for (int side = -1; side <= 1; side += 2) {
                    for (double d = 18; d <= 34; d += 8) {
                        double x = mx - az / len * d * side;
                        double z = mz + ax / len * d * side;
                        Optional<BlockPos> feet = AetheriaSpots.settle(level, BlockPos.containing(x, spire.groundY + 10, z), 6, 30);
                        if (feet.isPresent()) {
                            BlockPos f = feet.get();
                            double dx = mx - f.getX();
                            double dz = mz - f.getZ();
                            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                            WorldCommands.teleport(p, level, new AetheriaSpots.Spot(f, yaw, -8f));
                            return null;
                        }
                    }
                }
            }
            throw new Steps.Failure("no snapped spire with ground beside it");
        }, 90);
    }

    /** Stands the player on a Deep platform where a span leaves it, facing along the span. */
    private static void spanView(Minecraft mc) {
        server(s -> {
            ServerPlayer p = player(s);
            ServerLevel level = aetheria(s);
            AetheriaTerrain t = AetheriaSpots.terrain(level);
            int ci = (int) Math.floor(p.getX() / DeepSpans.CELL);
            int cj = (int) Math.floor(p.getZ() / DeepSpans.CELL);
            for (int r = 0; r <= 4; r++) {
                for (int i = ci - r; i <= ci + r; i++) {
                    for (int j = cj - r; j <= cj + r; j++) {
                        DeepSpans.Pillar pl = t.deep.pillar(i, j);
                        if (!pl.exists || pl.spans().length == 0) {
                            continue;
                        }
                        for (DeepSpans.Pillar other : neighbours(t, pl)) {
                            double dx = other.cx - pl.cx;
                            double dz = other.cz - pl.cz;
                            double len = Math.hypot(dx, dz);
                            double x = pl.cx + dx / len * (pl.platformR - 5);
                            double z = pl.cz + dz / len * (pl.platformR - 5);
                            Optional<BlockPos> feet = AetheriaSpots.settle(level, BlockPos.containing(x, pl.top + 3, z), 4, 12);
                            if (feet.isPresent()) {
                                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                                WorldCommands.teleport(p, level, new AetheriaSpots.Spot(feet.get(), yaw, 8f));
                                return null;
                            }
                        }
                    }
                }
            }
            throw new Steps.Failure("no Deep span found");
        }, 90);
    }

    private static List<DeepSpans.Pillar> neighbours(AetheriaTerrain t, DeepSpans.Pillar pl) {
        List<DeepSpans.Pillar> out = new ArrayList<>();
        for (int di = -2; di <= 2; di++) {
            for (int dj = -2; dj <= 2; dj++) {
                DeepSpans.Pillar o = t.deep.pillar(pl.ci + di, pl.cj + dj);
                if (o != pl && o.exists && Math.hypot(o.cx - pl.cx, o.cz - pl.cz) - o.platformR - pl.platformR < 70) {
                    out.add(o);
                }
            }
        }
        out.sort((a, b) -> Double.compare(Math.hypot(a.cx - pl.cx, a.cz - pl.cz), Math.hypot(b.cx - pl.cx, b.cz - pl.cz)));
        return out;
    }

    /** Turns the player toward the thick of the belt: the size-weighted centre of the rocks 30 to 150 blocks away. */
    private static void lookAtAsteroids(Minecraft mc) {
        float[] facing = server(s -> {
            ServerPlayer p = player(s);
            AetheriaTerrain t = AetheriaSpots.terrain(aetheria(s));
            int ci = Math.floorDiv(p.getBlockX(), DriftBelts.CELL);
            int cj = Math.floorDiv(p.getBlockZ(), DriftBelts.CELL);
            double sx = 0;
            double sy = 0;
            double sz = 0;
            double weight = 0;
            for (int i = ci - 4; i <= ci + 4; i++) {
                for (int j = cj - 4; j <= cj + 4; j++) {
                    for (int k = 4; k <= 7; k++) {
                        DriftBelts.Asteroid a = t.drift.asteroid(i, j, k);
                        if (a == null) {
                            continue;
                        }
                        double dx = a.cx - p.getX();
                        double dy = a.cy - p.getEyeY();
                        double dz = a.cz - p.getZ();
                        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        if (dist < 30 || dist > 150) {
                            continue;
                        }
                        sx += dx / dist * a.r;
                        sy += dy / dist * a.r;
                        sz += dz / dist * a.r;
                        weight += a.r;
                    }
                }
            }
            if (weight == 0) {
                return new float[] {p.getYRot(), 0};
            }
            return new float[] {(float) Math.toDegrees(Math.atan2(-sx, sz)),
                    (float) Math.max(-20, Math.min(30, -Math.toDegrees(Math.atan2(sy, Math.hypot(sx, sz)))))};
        }, 30);
        mc.player.setYRot(facing[0]);
        mc.player.setXRot(facing[1]);
    }

    /** Turns the player toward the nearest other Deep platform. */
    private static void lookAtPillars(Minecraft mc) {
        float[] facing = server(s -> {
            ServerPlayer p = player(s);
            AetheriaTerrain t = AetheriaSpots.terrain(aetheria(s));
            int ci = (int) Math.floor(p.getX() / DeepSpans.CELL);
            int cj = (int) Math.floor(p.getZ() / DeepSpans.CELL);
            double best = Double.MAX_VALUE;
            float[] out = {p.getYRot(), 5};
            for (int i = ci - 2; i <= ci + 2; i++) {
                for (int j = cj - 2; j <= cj + 2; j++) {
                    DeepSpans.Pillar pl = t.deep.pillar(i, j);
                    double dx = pl.cx - p.getX();
                    double dz = pl.cz - p.getZ();
                    double d = Math.hypot(dx, dz);
                    if (!pl.exists || d < pl.platformR + 10 || d > best) {
                        continue;
                    }
                    best = d;
                    out[0] = (float) Math.toDegrees(Math.atan2(-dx, dz));
                    out[1] = 6;
                }
            }
            return out;
        }, 30);
        mc.player.setYRot(facing[0]);
        mc.player.setXRot(facing[1]);
    }

    private static void rules(Steps steps, Minecraft mc, double[] y0, double[] peak, double[] jumps, BlockPos[] stood,
            BlockPos[] sky, UUID[] zombie) {
        // ---------------------------------------------------------------- jump heights
        steps.command("gamerule naturalRegeneration false")
                .command("gamemode survival")
                .waitUntil("in survival", 40, () -> !mc.player.isCreative())
                .command("cosmicbreach debug goto reach")
                .waitUntil("standing on the Reach", 1200, () -> mc.player.onGround() && mc.player.getY() > 320 && settledNow(mc))
                .waitTicks(20)
                .run("mark", () -> {
                    y0[0] = mc.player.getY();
                    peak[0] = y0[0];
                })
                .press(mc.options.keyJump)
                .waitUntil("landed again", 60, () -> {
                    peak[0] = Math.max(peak[0], mc.player.getY());
                    return mc.player.getY() < peak[0] - 0.01 && mc.player.onGround();
                })
                .run("record the Reach jump", () -> jumps[0] = peak[0] - y0[0])
                .command("cosmicbreach debug goto drift")
                .waitUntil("standing on an asteroid", 1200, () -> mc.player.onGround() && mc.player.getY() < 300 && settledNow(mc))
                .waitTicks(20)
                .run("mark", () -> {
                    y0[0] = mc.player.getY();
                    peak[0] = y0[0];
                })
                .press(mc.options.keyJump)
                .waitUntil("landed again", 200, () -> {
                    peak[0] = Math.max(peak[0], mc.player.getY());
                    return mc.player.getY() < peak[0] - 0.01 && mc.player.onGround();
                })
                .run("record the Drift jump", () -> jumps[1] = peak[0] - y0[0])
                .log("jumps", () -> String.format(Locale.ROOT, "jump height: Reach %.2f blocks, Drift %.2f blocks (x%.2f)",
                        jumps[0], jumps[1], jumps[1] / jumps[0]))
                .check("a Reach jump is vanilla's 1.25 blocks", () -> Math.abs(jumps[0] - 1.25) < 0.1)
                .check("a Drift jump is at least 3 times a Reach jump", () -> jumps[1] > 3.0 * jumps[0] && jumps[1] < 6.0);

        // ---------------------------------------------------------------- Shear band A catches the unattuned
        steps.command("cosmicbreach debug goto reach")
                .waitUntil("standing on the Reach", 1200, () -> mc.player.onGround() && mc.player.getY() > 320 && settledNow(mc))
                .waitTicks(10)
                .run("heal and find open sky", () -> {
                    stood[0] = server(s -> {
                        ServerPlayer p = player(s);
                        p.setHealth(p.getMaxHealth());
                        return ShearBands.lastStood(p);
                    }, 5);
                    sky[0] = server(s -> AetheriaSpots.openSky(aetheria(s), player(s).blockPosition(), ShearBand.A.minY - 4, 400)
                            .orElseThrow(() -> new Steps.Failure("no open sky near the Reach spot")), 60);
                })
                .check("the server knows the block the player stood on", () -> stood[0] != null)
                .log("the fall", () -> "stood on " + stood[0].toShortString() + ", dropping over the gap at " + sky[0].toShortString())
                .run("step off into the gap", () -> server(s -> {
                    ServerPlayer p = player(s);
                    WorldCommands.teleport(p, aetheria(s), new AetheriaSpots.Spot(sky[0].atY(ShearBand.A.maxY + 10), p.getYRot(), 20f));
                    return null;
                }, 5))
                .waitUntil("caught by the band and put back", 200, () -> caught(mc) && mc.player.distanceToSqr(stood[0].getX() + 0.5, stood[0].getY() + 1, stood[0].getZ() + 0.5) < 9)
                .waitTicks(5)
                .screenshot("shear_caught")
                .log("after the catch", () -> String.format(Locale.ROOT, "health %.1f, at %s, subtitle '%s'",
                        mc.player.getHealth(), mc.player.blockPosition().toShortString(), subtitle(mc)))
                .check("4 damage", () -> Math.abs(mc.player.getHealth() - (mc.player.getMaxHealth() - 4)) < 0.01)
                .check("the subtitle says the song won't carry you yet", () -> subtitle(mc).contains("won't carry you yet"))
                .check("the server counted one catch", () -> server(s -> ShearBands.counters(), 5).startsWith("1 "));

        // ---------------------------------------------------------------- attuned: through band A, caught by band B
        steps.command("advancement grant @s only " + LayerAttunement.DRIFT)
                .waitUntil("attuned to the Drift", 40, () -> server(s -> LayerAttunement.has(player(s), com.cosmicbreach.world.Layer.DRIFT), 5))
                .run("heal", () -> server(s -> {
                    player(s).setHealth(player(s).getMaxHealth());
                    return null;
                }, 5))
                .run("step off into the gap again", () -> server(s -> {
                    ServerPlayer p = player(s);
                    WorldCommands.teleport(p, aetheria(s), new AetheriaSpots.Spot(sky[0].atY(ShearBand.A.maxY + 10), p.getYRot(), 20f));
                    return null;
                }, 5))
                .waitUntil("through band A, into the Drift", 400, () -> mc.player.getY() < ShearBand.A.minY - 2)
                .check("with Slow Falling", () -> mc.player.hasEffect(MobEffects.SLOW_FALLING))
                .check("not caught", () -> server(s -> ShearBands.counters(), 5).startsWith("1 1 "))
                .log("after band A", () -> String.format(Locale.ROOT, "at y %.1f with Slow Falling %s, health %.1f",
                        mc.player.getY(), mc.player.hasEffect(MobEffects.SLOW_FALLING), mc.player.getHealth()))
                .run("find open sky over band B", () -> sky[0] = server(s -> AetheriaSpots.openSky(aetheria(s), player(s).blockPosition(),
                        ShearBand.B.minY - 4, ShearBand.B.maxY + 20).orElseThrow(() -> new Steps.Failure("no open sky over band B")), 60))
                .run("drop toward band B", () -> server(s -> {
                    ServerPlayer p = player(s);
                    WorldCommands.teleport(p, aetheria(s), new AetheriaSpots.Spot(sky[0].atY(ShearBand.B.maxY + 8), p.getYRot(), 20f));
                    return null;
                }, 5))
                .waitUntil("band B caught the player (no Deep attunement) and put them back", 400,
                        () -> server(s -> ShearBands.counters(), 5).startsWith("2 ") && mc.player.getY() > 320)
                .log("after band B", () -> String.format(Locale.ROOT, "back at %s, health %.1f",
                        mc.player.blockPosition().toShortString(), mc.player.getHealth()));

        // ---------------------------------------------------------------- arrival grace
        steps.command("advancement revoke @s only " + LayerAttunement.DRIFT)
                .waitUntil("no longer attuned to the Drift", 40, () -> !server(s -> LayerAttunement.has(player(s), com.cosmicbreach.world.Layer.DRIFT), 5))
                .run("find open sky over band A", () -> sky[0] = server(s -> AetheriaSpots.openSky(aetheria(s), player(s).blockPosition(),
                        ShearBand.A.minY - 4, 400).orElseThrow(() -> new Steps.Failure("no open sky")), 60))
                .run("a fresh arrival (never stood here) drops into band A", () -> server(s -> {
                    ServerPlayer p = player(s);
                    p.setHealth(p.getMaxHealth());
                    ShearBands.forget(p);
                    WorldCommands.teleport(p, aetheria(s), new AetheriaSpots.Spot(sky[0].atY(ShearBand.A.maxY + 10), p.getYRot(), 20f));
                    return null;
                }, 5))
                .waitUntil("set down on an island", 300, () -> server(s -> ShearBands.counters(), 5).startsWith("3 ")
                        && mc.player.getY() > ShearBand.A.maxY && mc.player.onGround())
                .log("after the grace catch", () -> String.format(Locale.ROOT, "at %s, health %.1f",
                        mc.player.blockPosition().toShortString(), mc.player.getHealth()))
                .check("no damage for a fresh arrival", () -> mc.player.getHealth() >= mc.player.getMaxHealth() - 0.01);

        // ---------------------------------------------------------------- mobs are removed
        steps.run("find open sky and drop a zombie into band A", () -> {
                    sky[0] = server(s -> AetheriaSpots.openSky(aetheria(s), player(s).blockPosition(), ShearBand.A.minY - 4, 400)
                            .orElseThrow(() -> new Steps.Failure("no open sky")), 60);
                    zombie[0] = server(s -> {
                        ServerLevel level = aetheria(s);
                        Zombie z = EntityType.ZOMBIE.create(level);
                        z.moveTo(sky[0].getX() + 0.5, ShearBand.A.maxY + 6, sky[0].getZ() + 0.5, 0, 0);
                        z.setPersistenceRequired();
                        z.finalizeSpawn(level, level.getCurrentDifficultyAt(z.blockPosition()), MobSpawnType.COMMAND, null);
                        level.addFreshEntity(z);
                        return z.getUUID();
                    }, 5);
                })
                .waitUntil("the zombie is gone", 200, () -> server(s -> aetheria(s).getEntity(zombie[0]) == null, 5))
                .check("removed by the band", () -> server(s -> ShearBands.counters(), 5).endsWith(" 1"))
                .check("and it dropped nothing", () -> server(s -> aetheria(s).getEntitiesOfClass(ItemEntity.class,
                        new AABB(sky[0].getX() - 16, 250, sky[0].getZ() - 16, sky[0].getX() + 16, 340, sky[0].getZ() + 16)).isEmpty(), 5))
                .command("gamemode creative");

        // ---------------------------------------------------------------- items fall at 0.4x in the Drift
        double[] falls = {0, 0};
        UUID[] items = {null, null};
        steps.run("drop an item in open sky in the Reach and one in the Drift", () -> {
                    BlockPos reachSky = server(s -> AetheriaSpots.openSky(aetheria(s), player(s).blockPosition(), 330, 400)
                            .orElseThrow(() -> new Steps.Failure("no open sky")), 60);
                    BlockPos driftSky = server(s -> AetheriaSpots.openSky(aetheria(s), player(s).blockPosition(), 200, 260)
                            .orElseThrow(() -> new Steps.Failure("no open sky in the Drift")), 60);
                    items[0] = dropItem(reachSky.atY(395));
                    items[1] = dropItem(driftSky.atY(255));
                })
                .waitTicks(30)
                .run("measure the falls", () -> {
                    falls[0] = 395 - server(s -> aetheria(s).getEntity(items[0]).getY(), 5);
                    falls[1] = 255 - server(s -> aetheria(s).getEntity(items[1]).getY(), 5);
                })
                .log("item falls", () -> String.format(Locale.ROOT, "an item fell %.2f blocks in 30 ticks in the Reach, %.2f in the Drift (x%.2f)",
                        falls[0], falls[1], falls[1] / falls[0]))
                .check("items fall at 0.4x in the Drift", () -> Math.abs(falls[1] / falls[0] - 0.4) < 0.05)
                .run("clean up the items", () -> server(s -> {
                    for (UUID id : items) {
                        var e = aetheria(s).getEntity(id);
                        if (e != null) {
                            e.discard();
                        }
                    }
                    return null;
                }, 5));

        // ---------------------------------------------------------------- Starbloom grows on Glimmer Grass
        steps.run("plant Starbloom on Glimmer Grass and let it grow", () -> server(s -> {
                    ServerLevel level = aetheria(s);
                    BlockPos soil = player(s).blockPosition().offset(3, 5, 3);
                    level.setBlock(soil, ModBlocks.GLIMMER_GRASS.get().defaultBlockState(), 3);
                    level.setBlock(soil.above(2), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                    BlockState crop = ModBlocks.STARBLOOM_CROP.get().defaultBlockState();
                    if (!crop.canSurvive(level, soil.above())) {
                        throw new Steps.Failure("a Starbloom crop can't stand on Glimmer Grass");
                    }
                    level.setBlock(soil.above(), crop, 3);
                    net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(1);
                    for (int i = 0; i < 400; i++) {
                        BlockState now = level.getBlockState(soil.above());
                        if (now.is(ModBlocks.STARBLOOM_CROP.get())) {
                            now.randomTick(level, soil.above(), random);
                        }
                    }
                    BlockState grown = level.getBlockState(soil.above());
                    if (!grown.is(ModBlocks.STARBLOOM_CROP.get())
                            || grown.getValue(com.cosmicbreach.block.StarbloomCropBlock.AGE) != com.cosmicbreach.block.StarbloomCropBlock.MAX_AGE) {
                        throw new Steps.Failure("the crop did not grow up on Glimmer Grass: " + grown);
                    }
                    level.setBlock(soil.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                    level.setBlock(soil, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                    return null;
                }, 10))
                .log("a Starbloom crop grew to full age on Glimmer Grass");

        // ---------------------------------------------------------------- Shardling packs spawn naturally
        int[] seen = {0, 0, 0};
        steps.command("difficulty normal")
                .command("cosmicbreach debug goto reach")
                .waitUntil("on the Reach", 1200, () -> mc.player.onGround() && mc.player.getY() > 320 && settledNow(mc))
                .command("gamemode survival")
                .command("gamerule doMobSpawning true")
                .waitUntil("a natural Shardling pack appeared (two or more sharing one pack brain)", 2400, () -> {
                    int[] r = server(s -> packs(player(s)), 5);
                    System.arraycopy(r, 0, seen, 0, 3);
                    return r[1] >= 1;
                })
                .waitTicks(100)
                .run("count again", () -> System.arraycopy(server(s -> packs(player(s)), 5), 0, seen, 0, 3))
                .log("natural spawns", () -> seen[0] + " Shardlings within 128 blocks in " + seen[1]
                        + " pack(s), the biggest of " + seen[2])
                .command("gamerule doMobSpawning false")
                .command("kill @e[type=cosmicbreach:shardling]")
                .command("gamemode creative")
                .command("cosmicbreach debug locate")
                .waitForChat("Nearest safe deep ground", 400)
                .command("gamerule naturalRegeneration true");
    }

    private static void timing(Steps steps, Minecraft mc, long[] started, List<String> timings) {
        // ---------------------------------------------------------------- generation time
        steps.run("render distance back to 8", () -> mc.options.renderDistance().set(8))
                .waitTicks(100);
        generation(steps, "aetheria", AetheriaWorld.LEVEL, 3000, started, timings);
        generation(steps, "nether", Level.NETHER, 3000, started, timings);
        generation(steps, "overworld_flat", Level.OVERWORLD, 3000, started, timings);
        steps.log("generation summary", () -> String.join("; ", timings))
                .log("ores in the Aetheria area", () -> server(s -> ores(aetheria(s), 3000), 120));
    }

    /** Force-loads a GEN_SIZE x GEN_SIZE chunk area far from everything and times it to full chunks. */
    private static void generation(Steps steps, String name, ResourceKey<Level> dimension, int blockX, long[] started, List<String> timings) {
        int cx0 = blockX >> 4;
        int cz0 = blockX >> 4;
        steps.run("force-load " + GEN_SIZE + "x" + GEN_SIZE + " chunks in " + name, () -> server(s -> {
                    ServerLevel level = s.getLevel(dimension);
                    started[0] = System.nanoTime();
                    for (int i = 0; i < GEN_SIZE; i++) {
                        for (int j = 0; j < GEN_SIZE; j++) {
                            level.setChunkForced(cx0 + i, cz0 + j, true);
                        }
                    }
                    return null;
                }, 10))
                .waitUntil(name + " is generated", 6000, () -> server(s -> {
                    ServerLevel level = s.getLevel(dimension);
                    for (int i = 0; i < GEN_SIZE; i++) {
                        for (int j = 0; j < GEN_SIZE; j++) {
                            LevelChunk chunk = level.getChunkSource().getChunkNow(cx0 + i, cz0 + j);
                            if (chunk == null) {
                                return false;
                            }
                        }
                    }
                    return true;
                }, 5))
                .run("record " + name, () -> {
                    double ms = (System.nanoTime() - started[0]) / 1e6;
                    timings.add(String.format(Locale.ROOT, "%s %d chunks in %.0f ms (%.1f ms per chunk)", name, GEN_SIZE * GEN_SIZE, ms,
                            ms / (GEN_SIZE * GEN_SIZE)));
                })
                .log(name + " timing", () -> timings.get(timings.size() - 1))
                .run("unforce " + name, () -> server(s -> {
                    ServerLevel level = s.getLevel(dimension);
                    for (int i = 0; i < GEN_SIZE; i++) {
                        for (int j = 0; j < GEN_SIZE; j++) {
                            level.setChunkForced(cx0 + i, cz0 + j, false);
                        }
                    }
                    return null;
                }, 10))
                .waitTicks(40);
    }

    /** Ore blocks per chunk in the timed Aetheria area. Server thread. */
    private static String ores(ServerLevel level, int blockX) {
        Block[] ores = {ModBlocks.STARSTEEL_ORE.get(), ModBlocks.NEBULITE_ORE.get(), ModBlocks.ECLIPSIUM_ORE.get()};
        long[] counts = new long[3];
        long[] rock = new long[3];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int cx0 = blockX >> 4;
        for (int i = 0; i < GEN_SIZE; i++) {
            for (int j = 0; j < GEN_SIZE; j++) {
                LevelChunk chunk = level.getChunk(cx0 + i, cx0 + j); // the area is square: same start on both axes
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = 1; y < 400; y++) {
                            BlockState state = chunk.getBlockState(pos.set(x, y, z));
                            if (state.isAir()) {
                                continue;
                            }
                            int layer = y >= 300 ? 0 : (y >= 160 ? 1 : 2);
                            rock[layer]++;
                            if (state.is(ores[layer])) {
                                counts[layer]++;
                            }
                        }
                    }
                }
            }
        }
        double chunks = GEN_SIZE * GEN_SIZE;
        return String.format(Locale.ROOT, "per chunk: Starsteel %.1f (in %.0f Reach blocks), Nebulite %.1f (in %.0f Drift blocks), "
                        + "Eclipsium %.1f (in %.0f Deep blocks)",
                counts[0] / chunks, rock[0] / chunks, counts[1] / chunks, rock[1] / chunks, counts[2] / chunks, rock[2] / chunks);
    }

    // ------------------------------------------------------------------ helpers

    private static void view(Minecraft mc, boolean across) {
        server(s -> {
            ServerPlayer p = player(s);
            Optional<AetheriaSpots.Spot> spot = AetheriaSpots.edgeView(aetheria(s), p.blockPosition(), across);
            if (spot.isEmpty()) {
                throw new Steps.Failure("no rim viewpoint found");
            }
            WorldCommands.teleport(p, aetheria(s), spot.get());
            return null;
        }, 90);
    }

    private static boolean inAetheria(Minecraft mc) {
        return mc.level != null && mc.level.dimension() == AetheriaWorld.LEVEL && mc.player != null;
    }

    /**
     * Waits until no screen is up, the chunks within 5 of the player have arrived, and the section compile
     * queue has then stayed empty for a while; after {@code maxTicks} it passes once the chunks are there.
     */
    private static BooleanSupplier settled(Minecraft mc, int maxTicks) {
        int[] state = {0, 0, -1};
        return () -> {
            state[0]++;
            boolean ready = chunksAround(mc, 5);
            int rendered = mc.levelRenderer.countRenderedSections();
            if (ready && mc.levelRenderer.hasRenderedAllSections() && rendered == state[2]) {
                state[1]++;
            } else {
                state[1] = 0;
            }
            state[2] = rendered;
            return state[1] >= SETTLE_TICKS || (state[0] >= maxTicks && ready);
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

    private static boolean settledNow(Minecraft mc) {
        return chunksAround(mc, 2) && mc.levelRenderer.hasRenderedAllSections();
    }

    private static boolean caught(Minecraft mc) {
        return mc.player.getHealth() < mc.player.getMaxHealth() - 1 && mc.player.getY() > ShearBand.A.maxY;
    }

    private static String where(Minecraft mc) {
        return String.format(Locale.ROOT, "at %s in %s, biome %s, facing %.0f/%.0f", mc.player.blockPosition().toShortString(),
                mc.level.dimension().location(), mc.level.getBiome(mc.player.blockPosition()).unwrapKey().map(k -> k.location().toString()).orElse("?"),
                mc.player.getYRot(), mc.player.getXRot());
    }

    private static String subtitle(Minecraft mc) {
        try {
            Field field = Gui.class.getDeclaredField("subtitle");
            field.setAccessible(true);
            Component c = (Component) field.get(mc.gui);
            return c == null ? "" : c.getString();
        } catch (ReflectiveOperationException e) {
            return "(unreadable: " + e + ")";
        }
    }

    private static ServerLevel aetheria(MinecraftServer server) {
        return server.getLevel(AetheriaWorld.LEVEL);
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().get(0);
    }

    /** Runs {@code call} on the integrated server's thread and waits for it. */
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
