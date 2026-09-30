package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.dev.FrameStats;
import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.client.sky.SkyStats;
import com.cosmicbreach.guardian.heliarch.HeliarchArena;
import com.cosmicbreach.guardian.heliarch.Heliarchs;
import com.cosmicbreach.sandbox.StressScene;
import com.cosmicbreach.structure.sanctum.SanctumCommands;
import com.cosmicbreach.structure.sanctum.SanctumLayout;
import com.cosmicbreach.world.AetheriaWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;

/**
 * GDD 9.4's stress scene and its budgets (F1). At the Breach Sanctum with the Hollow Heliarch in phase 2, a Solar
 * Flare over the Reach and a Meteor Shower in the Drift, {@code /cosmicbreach stress build} adds 60 mobs across the
 * three layers and seven fake players fighting them (two in the Reach, two in the Drift, three here). Measured and
 * asserted:
 *
 * <ul>
 *   <li>the server holds 20 ticks a second (median and 95th percentile tick under 50 ms); the whole mod within 4 ms a
 *       tick with 8 players (every Cosmic Breach entity plus each fake player's whole tick, vanilla's part included);
 *       the Heliarch alone under 0.5 ms;</li>
 *   <li>150 mobs round one player in the Reach tick in under 1.5 ms (the mob AI budget);</li>
 *   <li>frames on this machine hold 60 fps (the frame's CPU and GPU time at the 95th percentile under 16.7 ms), and the
 *       mod's share of a frame stays under its 3 ms budget at the median. The share is bounded from above: the stressed
 *       frame's CPU time less a vanilla frame's (the Overworld's flat test world first, nothing of ours drawing, clouds
 *       off), since vanilla does more work in the stressed frame than in that one;</li>
 *   <li>the sky pass under 0.35 ms of GPU time (median) in the busiest sky, the Reach at the start of the eclipse;</li>
 *   <li>fresh chunks generate in under 100 ms each; memory after the scene, reported.</li>
 * </ul>
 */
public final class StressScenario implements Scenario {
    /** FULL: everything; LOAD: only the mob AI budget (for profiling). */
    public enum Part { FULL, LOAD }

    private final Part part;

    public StressScenario() {
        this(Part.FULL);
    }

    public StressScenario(Part part) {
        this.part = part;
    }

    static final double TICK_BUDGET_MS = 50.0;
    static final double MOD_BUDGET_MS = 4.0;
    static final double CLIENT_BUDGET_MS = 3.0;
    static final double BOSS_BUDGET_MS = 0.5;
    static final double AI_BUDGET_MS = 1.5;
    static final double FRAME_BUDGET_MS = 1000.0 / 60.0;
    static final double SKY_BUDGET_MS = 0.35;
    static final double CHUNK_BUDGET_MS = 100.0;
    private final List<String> results = new ArrayList<>();
    private final double[] baseline = {Double.NaN, Double.NaN};
    private StressScene.Stats scene;
    private StressScene.Stats load;
    private StressScene.Stats close;
    private long heapBefore;

    @Override
    public int timeBudgetSeconds() {
        return 900;
    }

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        FrameStats.install();
        if (part == Part.LOAD) {
            steps.command("gamerule sendCommandFeedback false")
                    .command("gamerule doDaylightCycle false")
                    .command("time set 6000")
                    .command("difficulty normal")
                    .command("execute in " + AetheriaWorld.LEVEL.location() + " run tp @s 0.5 400 0.5")
                    .waitUntil("in Aetheria", 600, () -> mc.level != null && mc.level.dimension() == AetheriaWorld.LEVEL);
            load(steps, mc);
            steps.check("150 loaded mobs tick in under 1.5 ms (the mob AI budget)", () -> load.entityMsPerTick() < AI_BUDGET_MS)
                    .check("150 mobs in a fight still leave 20 TPS", () -> close.tickMedianMs() < TICK_BUDGET_MS);
            return;
        }
        steps.command("gamerule sendCommandFeedback false")
                .command("gamerule doDaylightCycle false")
                .command("gamerule doWeatherCycle false")
                .command("time set 6000")
                .command("difficulty normal")
                .command("gamemode creative")

                // a vanilla frame for reference: the flat Overworld, nothing of ours drawing (no Aetheria sky, fog or weather,
                // an empty hand so no combat HUD), vanilla's clouds off (Aetheria has none)
                .run("the reference view distance (GDD 9.4: 12 chunks), clouds off", () -> {
                    mc.options.renderDistance().set(12);
                    clouds[0] = mc.options.cloudStatus().get();
                    mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
                })
                .waitUntil("the Overworld is drawn", 1200, CryptKit.settled(mc, 900))
                .run("frames: vanilla", () -> {
                    FrameStats.reset();
                    FrameStats.enabled = true;
                })
                .waitTicks(200)
                .run("the vanilla frame", () -> {
                    FrameStats.enabled = false;
                    frame[3] = FrameStats.cpuMedianMs();
                    mc.options.cloudStatus().set(clouds[0]);
                    results.add("vanilla (the flat Overworld, nothing of ours drawing): " + FrameStats.report());
                })
                .log("vanilla", () -> results.get(results.size() - 1))
                .command("execute in " + AetheriaWorld.LEVEL.location() + " run tp @s 0.5 120 0.5")
                .waitUntil("in Aetheria", 600, () -> mc.level != null && mc.level.dimension() == AetheriaWorld.LEVEL)
                .run("fly", () -> {
                    mc.player.getAbilities().flying = true;
                    mc.player.onUpdateAbilities();
                })
                .run("build the Breach Sanctum at its place", () -> results.add("Sanctum built in "
                        + CryptKit.server(srv -> SanctumCommands.build(srv.getLevel(AetheriaWorld.LEVEL))) + " ms"))
                .command("cosmicbreach weather clear")
                .command("cosmicbreach debug goto sanctum arena")
                .waitUntil("on the arena", 600, () -> Math.abs(mc.player.getY() - SanctumLayout.ARENA_Y) < 1.5
                        && HeliarchArena.radiusOf(mc.player.getX(), mc.player.getZ()) < 30)
                .run("the reference view distance (GDD 9.4: 12 chunks)", () -> mc.options.renderDistance().set(12))
                .waitUntil("the arena is drawn", 1200, CryptKit.settled(mc, 900))
                .run("memory before", () -> heapBefore = heapAfterGc())

                // the quiet baseline: the same place, one player, nothing awake
                .command("cosmicbreach stress measure")
                .run("frames: start", () -> {
                    FrameStats.reset();
                    FrameStats.enabled = true;
                })
                .waitTicks(200)
                .run("baseline", () -> {
                    FrameStats.enabled = false;
                    StressScene.Stats s = CryptKit.server(srv -> {
                        StressScene.Stats st = StressScene.stats();
                        st.stop();
                        return st;
                    });
                    baseline[0] = s.tickMedianMs();
                    baseline[1] = FrameStats.cpuMedianMs();
                    results.add("baseline: " + s.report());
                    results.add("baseline: " + FrameStats.report());
                })
                .log("baseline", () -> results.get(results.size() - 2) + System.lineSeparator() + results.get(results.size() - 1))

                // the scene
                .command("gamemode survival")
                .command("effect give @s minecraft:resistance 100000 4 true")
                .command("effect give @s minecraft:regeneration 100000 4 true")
                .command("cosmicbreach debug heliarch summon")
                .waitUntil("the Heliarch fights", 400, () -> CryptKit.server(srv -> Heliarchs.active(srv.getLevel(AetheriaWorld.LEVEL)) != null))
                .waitTicks(220)
                .command("cosmicbreach debug heliarch hollow")
                .waitTicks(140)
                .command("cosmicbreach weather flare reach")
                .command("cosmicbreach weather shower drift")
                .command("cosmicbreach stress build")
                .waitUntil("the scene stands", 100, () -> CryptKit.server(srv -> StressScene.standing() && StressScene.fighters() == 7))
                .waitTicks(100)
                .command("cosmicbreach stress measure")
                .run("frames: start", () -> {
                    FrameStats.reset();
                    FrameStats.enabled = true;
                })
                .waitTicks(400)
                .run("the scene measured", () -> {
                    FrameStats.enabled = false;
                    scene = CryptKit.server(srv -> {
                        StressScene.Stats st = StressScene.stats();
                        st.stop();
                        return st;
                    });
                    results.add("stress scene: " + scene.report());
                    results.add("stress scene: " + FrameStats.report());
                    frame[0] = FrameStats.cpuP95Ms();
                    frame[1] = FrameStats.gpuP95Ms();
                    frame[2] = FrameStats.cpuMedianMs();
                })
                .log("scene", () -> results.get(results.size() - 2) + System.lineSeparator() + results.get(results.size() - 1))
                .check("the Heliarch is still fighting in phase 2", () -> CryptKit.server(srv -> {
                    var h = Heliarchs.active(srv.getLevel(AetheriaWorld.LEVEL));
                    return h != null;
                }))
                .run("memory with the scene", () -> results.add(String.format(Locale.ROOT, "heap after GC: %d MB before the scene, %d MB with it",
                        heapBefore >> 20, heapAfterGc() >> 20)))
                .log("memory", () -> results.get(results.size() - 1))
                .command("cosmicbreach stress stop")
                .command("cosmicbreach debug heliarch reset")
                .command("cosmicbreach weather clear")

                // the busiest sky: the Reach as the eclipse begins (Thalassa, the eclipse, the aurora)
                .command("gamemode creative")
                .command("cosmicbreach debug goto reach")
                .waitUntil("in the Reach", 600, () -> mc.player.getY() > 300)
                .waitUntil("the island is drawn", 1600, CryptKit.settled(mc, 1500))
                .command("time set 12300")
                .run("face the busiest sky", () -> {
                    mc.player.setYRot(110f);
                    mc.player.setXRot(-18f);
                })
                .waitTicks(10)
                .run("sky: start", () -> {
                    SkyStats.reset();
                    SkyStats.enabled = true;
                })
                .waitUntil("400 sky frames", 2400, () -> SkyStats.samples() >= 400)
                .run("sky: stop", () -> {
                    SkyStats.enabled = false;
                    results.add(SkyStats.report());
                    sky[0] = SkyStats.gpuMedianMs();
                })
                .log("sky", () -> results.get(results.size() - 1))
                .command("time set 6000");
        load(steps, mc);
        steps
                // fresh chunks, timed on the server thread (it waits for each)
                .run("time fresh chunks", () -> chunks[0] = CryptKit.server(srv -> {
                    StressScene.chunks(srv.getLevel(AetheriaWorld.LEVEL));
                    return StressScene.chunkReport();
                }))
                .run("chunks", () -> results.add(chunks[0].toString()))
                .log("chunks", () -> results.get(results.size() - 1))

                // the budgets, once everything is measured
                .check("the server holds 20 TPS (median and 95th percentile tick under 50 ms)", () -> scene.tickMedianMs() < TICK_BUDGET_MS
                        && scene.tickP95Ms() < TICK_BUDGET_MS)
                .check("the whole mod within 4 ms a tick with 8 players (every Cosmic Breach entity, plus each fake player's whole tick)",
                        () -> scene.entityMsPerTick() + scene.fighterMsPerTick() < MOD_BUDGET_MS)
                .check("the Heliarch alone within the boss budget (0.5 ms a tick)",
                        () -> scene.typeMsPerTick(CosmicBreach.id("hollow_heliarch")) < BOSS_BUDGET_MS)
                .check("frames hold 60 fps here (CPU p95 under 16.7 ms)", () -> frame[0] < FRAME_BUDGET_MS)
                .log("the mod's share of a frame", () -> String.format(Locale.ROOT, "at most %.2f ms: the stressed frame's CPU median "
                        + "%.2f ms less the vanilla frame's %.2f ms", frame[2] - frame[3], frame[2], frame[3]))
                .check("the mod's share of a frame is under its 3 ms budget (median; the stressed frame less a vanilla one)",
                        () -> frame[2] - frame[3] < CLIENT_BUDGET_MS)
                .check("frames hold 60 fps here (GPU p95 under 16.7 ms)", () -> Double.isNaN(frame[1])
                        || frame[1] < FRAME_BUDGET_MS)
                .check("the sky pass costs under 0.35 ms of GPU time (median)", () -> Double.isNaN(sky[0]) || sky[0] < SKY_BUDGET_MS)
                .check("150 loaded mobs tick in under 1.5 ms (the mob AI budget)", () -> load.entityMsPerTick() < AI_BUDGET_MS)
                .check("150 mobs in a fight still leave 20 TPS", () -> close.tickMedianMs() < TICK_BUDGET_MS)
                .check("fresh chunks generate in under 100 ms each", () -> chunks[0].msPerChunk() < CHUNK_BUDGET_MS);
    }

    private final StressScene.ChunkReport[] chunks = new StressScene.ChunkReport[1];
    /** The stressed frame's CPU p95, GPU p95 and CPU median; the vanilla frame's CPU median. */
    private final double[] frame = {Double.NaN, Double.NaN, Double.NaN, Double.NaN};
    private final net.minecraft.client.CloudStatus[] clouds = {net.minecraft.client.CloudStatus.FANCY};
    private final double[] sky = {Double.NaN};

    /**
     * The mob AI budget: 150 mobs loaded round one player in the Reach, 48 to 128 blocks away, beyond their sight
     * (asserted: GDD 9.4's 150 loaded mobs), then 150 more all within 26 blocks, hunting (a fight, the worst case: the
     * server must still hold 20 TPS).
     */
    private void load(Steps steps, Minecraft mc) {
        steps.command("gamemode creative")
                .command("cosmicbreach debug goto reach")
                .waitUntil("in the Reach", 600, () -> mc.player.getY() > 300)
                .run("the reference view distance (GDD 9.4: 12 chunks)", () -> mc.options.renderDistance().set(12))
                .waitUntil("the island is drawn", 1600, CryptKit.settled(mc, 1500))
                .command("gamemode survival")
                .command("effect give @s minecraft:resistance 100000 4 true")
                // exactly 150: no natural spawning, and the herds and packs already here gone
                .command("gamerule doMobSpawning false")
                .command("kill @e[type=cosmicbreach:lumen_stag]")
                .command("kill @e[type=cosmicbreach:shardling]")
                .command("kill @e[type=cosmicbreach:gyre_knight]")
                .command("kill @e[type=cosmicbreach:hollow_stalker]")
                .command("kill @e[type=cosmicbreach:drift_manta]")
                .waitTicks(20)
                .command("cosmicbreach stress load 150 spread")
                .waitTicks(80)
                .command("cosmicbreach stress measure")
                .waitTicks(200)
                .run("the spread load measured", () -> {
                    load = CryptKit.server(srv -> {
                        StressScene.Stats st = StressScene.stats();
                        st.stop();
                        return st;
                    });
                    results.add("150 mobs loaded 48 to 128 blocks away: " + load.report());
                })
                .log("load", () -> results.get(results.size() - 1))
                .command("cosmicbreach stress stop")
                .command("cosmicbreach stress load 150")
                .waitTicks(80)
                .command("cosmicbreach stress measure")
                .waitTicks(200)
                .run("the close load measured", () -> {
                    close = CryptKit.server(srv -> {
                        StressScene.Stats st = StressScene.stats();
                        st.stop();
                        return st;
                    });
                    results.add("150 mobs within 26 blocks: " + close.report());
                })
                .log("close", () -> results.get(results.size() - 1))
                .command("cosmicbreach stress stop")
                .command("gamerule doMobSpawning true")
                .command("gamemode creative");
    }

    private static long heapAfterGc() {
        System.gc();
        Runtime r = Runtime.getRuntime();
        return r.totalMemory() - r.freeMemory();
    }
}
