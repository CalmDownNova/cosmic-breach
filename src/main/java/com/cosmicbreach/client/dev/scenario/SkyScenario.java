package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.client.sky.AetheriaFog;
import com.cosmicbreach.client.sky.AetheriaMusic;
import com.cosmicbreach.client.sky.AetheriaSkyEffects;
import com.cosmicbreach.client.sky.AetheriaSkyRenderer;
import com.cosmicbreach.client.sky.ShaderPacks;
import com.cosmicbreach.client.sky.SkyFrame;
import com.cosmicbreach.client.sky.SkyState;
import com.cosmicbreach.client.sky.SkyStats;
import com.cosmicbreach.world.AetheriaAudio;
import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.Layer;
import com.cosmicbreach.world.VesperClock;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.Musics;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.biome.Biome;

/**
 * Aetheria's sky, fog and sound (W3a), in the game:
 *
 * <ol>
 *   <li>Views: in each layer at noon, sunset and night (the eclipse), looking up, towards Thalassa on the
 *       western horizon and to the north (Vesper, the clusters, the aurora); over a Reach island's edge and
 *       down the Breach from its rim; straight down from Y 352 to the Deep with our fog and vertical reach
 *       and with vanilla's, measured at the centre of the view; clouds on in the options and still absent.</li>
 *   <li>Checks: our effects are registered with no cloud height and skip vanilla's clouds; the fog's
 *       colour and distances per layer and its vertical factor; the 3 s cross-fade after a teleport; the
 *       Iris fallback (with a pretend shader pack our sky steps aside); each biome's ambient bed and track,
 *       creative music replaced by Aetheria's, the Arrival cue sent once; the Vesper clock's beat.</li>
 *   <li>Cost: the sky's CPU and GPU time over 400 frames.</li>
 * </ol>
 * Parts run alone as {@code sky-views}, {@code sky-checks}, {@code sky-cost}.
 */
public final class SkyScenario implements Scenario {
    public enum Part { VIEWS, CHECKS, COST }

    private static final int SETTLE_TICKS = 40;
    private static final int VIEW_X = 620;
    private static final int VIEW_Z = -380;
    /** Thalassa's heading in the Reach: a little south of west. */
    private static final float WEST = 87f;

    private final EnumSet<Part> parts;

    public SkyScenario() {
        this.parts = EnumSet.allOf(Part.class);
    }

    public SkyScenario(Part part) {
        this.parts = EnumSet.of(part);
    }

    @Override
    public int timeBudgetSeconds() {
        return 1200;
    }

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        steps.command("gamerule doDaylightCycle false")
                .command("time set 6000")
                .run("clouds on (fancy) and render distance 12: Aetheria must hide them anyway", () -> {
                    mc.options.cloudStatus().set(CloudStatus.FANCY);
                    mc.options.renderDistance().set(12);
                    mc.options.hideGui = true;
                })
                .command("execute in " + AetheriaWorld.LEVEL.location() + " run tp @s " + VIEW_X + " 400 " + VIEW_Z)
                .run("fly", () -> {
                    mc.player.getAbilities().flying = true;
                    mc.player.onUpdateAbilities();
                })
                .waitUntil("in Aetheria", 600, () -> inAetheria(mc))
                .command("cosmicbreach debug goto reach")
                .waitUntil("the island is drawn", 1600, settled(mc, 1500))
                .log("where", () -> where(mc));

        if (parts.contains(Part.CHECKS)) {
            checks(steps, mc);
        }
        if (parts.contains(Part.VIEWS)) {
            views(steps, mc);
        }
        if (parts.contains(Part.COST)) {
            cost(steps, mc);
        }
        steps.run("show the HUD", () -> mc.options.hideGui = false);
    }

    // ------------------------------------------------------------------ views

    private static void views(Steps steps, Minecraft mc) {
        com.cosmicbreach.client.dev.DevCamera[] camera = {null};
        double[] ground = {0};
        steps.run("face Thalassa", () -> look(mc, WEST, -4));
        times(steps, mc, "reach");

        // an island's edge: looking down the cliff to the Drift's asteroids and the Deep far below
        time(steps, mc, 6000);
        steps.run("to a Shattered Spires rim, looking down its edge", () -> edge(mc))
                .waitUntil("the view is drawn", 1200, settled(mc, 1100))
                .log("where", () -> where(mc))
                .run("a camera leaning out past the edge, looking down", () -> {
                    float yaw = mc.player.getYRot() * net.minecraft.util.Mth.DEG_TO_RAD;
                    double fx = -net.minecraft.util.Mth.sin(yaw);
                    double fz = net.minecraft.util.Mth.cos(yaw);
                    net.minecraft.world.phys.Vec3 eye = mc.player.getEyePosition().add(fx * 3.0, 1.0, fz * 3.0);
                    camera[0] = com.cosmicbreach.client.dev.DevCamera.create(mc.level);
                    camera[0].place(eye, eye.add(fx * 10, -16, fz * 10));
                    camera[0].use();
                })
                .waitTicks(30)
                .screenshot("reach_edge_down_noon");
        time(steps, mc, 18000);
        steps.screenshot("reach_edge_down_night")
                .run("back to the player's eyes", () -> camera[0].remove());

        // straight down from the Reach's height over open air to the Deep, with our fog and with vanilla's
        float[][] centre = {null, null};
        int[] deepTop = {0};
        time(steps, mc, 6000);
        steps.run("a camera at Y 352 over a column open down to the Deep", () -> {
                    int[] c = openColumn(mc);
                    deepTop[0] = c[1];
                    camera[0] = com.cosmicbreach.client.dev.DevCamera.create(mc.level);
                    net.minecraft.world.phys.Vec3 eye = new net.minecraft.world.phys.Vec3(c[0] + 0.5, 352, c[2] + 0.5);
                    camera[0].place(eye, eye.add(4, -200, 0));
                    camera[0].use();
                })
                .log("column", () -> "the column's first block is the Deep's rock at Y " + deepTop[0] + ", " + (352 - deepTop[0]) + " blocks below the camera")
                .waitTicks(40)
                .screenshot("lookdown_deep_our_fog")
                .run("measure the centre with our fog", () -> centre[0] = centreColour(mc))
                .run("vanilla's vertical reach and fog", () -> {
                    AetheriaFog.vanillaVerticalForTest = true;
                    mc.levelRenderer.needsUpdate();
                })
                .waitTicks(10)
                .screenshot("lookdown_deep_vanilla_fog")
                .run("measure the centre with vanilla's fog", () -> centre[1] = centreColour(mc))
                .run("ours again", () -> {
                    AetheriaFog.vanillaVerticalForTest = false;
                    mc.levelRenderer.needsUpdate();
                })
                .log("look-down", () -> String.format(Locale.ROOT,
                        "centre of the view straight down to the Deep: ours %s, vanilla's %s, fog colour %s",
                        hex(centre[0]), hex(centre[1]), hex(SkyState.frame().horizon)))
                .check("vanilla's way, the Deep is not there: fog colour or empty sky", () ->
                        distance(centre[1], SkyState.frame().horizon) < 0.06 || distance(centre[1], SkyState.frame().nadir) < 0.08)
                .check("ours, the Deep's rock shows (not the fog colour, not vanilla's picture)", () ->
                        distance(centre[0], SkyState.frame().horizon) > 0.25 && distance(centre[0], centre[1]) > 0.1)
                .run("drop the camera", () -> camera[0].remove())
                .command("cosmicbreach debug goto breach")
                .waitUntil("the Breach's rim is drawn", 1600, settled(mc, 1500))
                .log("where", () -> where(mc))
                .run("a camera on the rim, looking down into the Breach", () -> {
                    camera[0] = com.cosmicbreach.client.dev.DevCamera.create(mc.level);
                    breachCamera(mc, camera[0]);
                    camera[0].use();
                })
                .waitTicks(30)
                .screenshot("breach_down_noon");
        time(steps, mc, 18000);
        steps.screenshot("breach_down_night")
                .run("back to the player's eyes", () -> camera[0].remove());

        // the Drift, then the Deep (a few blocks above the rock, clear of trees and crystals)
        time(steps, mc, 6000);
        steps.command("execute in " + AetheriaWorld.LEVEL.location() + " run tp @s " + VIEW_X + " 250 " + VIEW_Z)
                .command("cosmicbreach debug goto drift")
                .waitUntil("the asteroid is drawn", 1600, settled(mc, 1500))
                .run("remember the ground", () -> ground[0] = mc.player.getY())
                .command("tp @s ~ ~12 ~")
                .waitUntil("lifted", 40, () -> mc.player.getY() > ground[0] + 9)
                .run("keep flying", () -> fly(mc))
                .waitTicks(5)
                .log("where", () -> where(mc));
        times(steps, mc, "drift");
        time(steps, mc, 6000);
        steps.command("execute in " + AetheriaWorld.LEVEL.location() + " run tp @s " + VIEW_X + " 120 " + VIEW_Z)
                .command("cosmicbreach debug goto deep")
                .waitUntil("the platform is drawn", 1600, settled(mc, 1500))
                .run("remember the ground", () -> ground[0] = mc.player.getY())
                .command("tp @s ~ ~4 ~")
                .waitUntil("lifted", 40, () -> mc.player.getY() > ground[0] + 3)
                .run("keep flying", () -> fly(mc))
                .waitTicks(5)
                .log("where", () -> where(mc));
        times(steps, mc, "deep");
    }

    /** Noon, sunset and night: up, west (Thalassa) and north (Vesper, clusters, aurora). */
    private static void times(Steps steps, Minecraft mc, String layer) {
        int[] times = {6000, 12300, 18000};
        String[] names = {"noon", "sunset", "night"};
        for (int i = 0; i < 3; i++) {
            String t = names[i];
            time(steps, mc, times[i]);
            steps.run("look up", () -> look(mc, 0, -62))
                    .waitTicks(2)
                    .screenshot(layer + "_" + t + "_up")
                    .run("look west, towards Thalassa", () -> look(mc, WEST, -4))
                    .waitTicks(2)
                    .screenshot(layer + "_" + t + "_west")
                    .run("look north", () -> look(mc, 180, -30))
                    .waitTicks(2)
                    .screenshot(layer + "_" + t + "_north");
        }
    }

    /** Sets the time and waits for the client to have it (the server sends the time once a second). */
    private static void time(Steps steps, Minecraft mc, int dayTime) {
        steps.command("time set " + dayTime)
                .waitUntil("the client's clock reads " + dayTime, 60, () -> Math.floorMod(mc.level.getDayTime(), 24000L) == dayTime)
                .waitTicks(2);
    }

    /** Puts the player on a Shattered Spires rim facing out, as the W2 views did. */
    private static void edge(Minecraft mc) {
        server(s -> {
            ServerPlayer p = player(s);
            var level = s.getLevel(AetheriaWorld.LEVEL);
            var spot = com.cosmicbreach.world.AetheriaSpots.edgeView(level, p.blockPosition(), false);
            if (spot.isEmpty()) {
                throw new Steps.Failure("no rim viewpoint found");
            }
            com.cosmicbreach.world.WorldCommands.teleport(p, level, spot.get());
            return null;
        }, 90);
    }

    /**
     * The camera on the rim, 3 blocks up, facing the Breach's centre and looking down 82 degrees: the chasm
     * below, the rim's underside, the Drift's rim and, far down at the bottom of the view, the Deep's (its
     * opening is wider).
     */
    private static void breachCamera(Minecraft mc, com.cosmicbreach.client.dev.DevCamera camera) {
        double x = mc.player.getX();
        double z = mc.player.getZ();
        double r = Math.hypot(x, z);
        double ix = -x / r;
        double iz = -z / r;
        net.minecraft.world.phys.Vec3 eye = new net.minecraft.world.phys.Vec3(x + ix * 1.5, mc.player.getEyeY() + 3, z + iz * 1.5);
        double down = Math.toRadians(82);
        camera.place(eye, eye.add(ix * Math.cos(down) * 20, -Math.sin(down) * 20, iz * Math.cos(down) * 20));
    }

    /** A column near the player with nothing in it above the Deep: {x, the Deep rock's top Y, z}. */
    private static int[] openColumn(Minecraft mc) {
        return server(s -> {
            ServerPlayer p = player(s);
            var level = s.getLevel(AetheriaWorld.LEVEL);
            int px = p.getBlockX();
            int pz = p.getBlockZ();
            for (int r = 30; r <= 140; r += 6) {
                for (int k = 0; k < 24; k++) {
                    double a = k * Math.PI * 2 / 24;
                    int x = px + (int) Math.round(Math.cos(a) * r);
                    int z = pz + (int) Math.round(Math.sin(a) * r);
                    var chunk = level.getChunk(x >> 4, z >> 4);
                    int top = chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
                    if (top > 20 && top < 140) {
                        return new int[] {x, top, z};
                    }
                }
            }
            throw new Steps.Failure("no column open down to the Deep near " + p.blockPosition().toShortString());
        }, 60);
    }

    /** Mean colour of the middle 32 by 32 pixels of the last frame (0..1). */
    private static float[] centreColour(Minecraft mc) {
        try (com.mojang.blaze3d.platform.NativeImage img = net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            int cx = img.getWidth() / 2;
            int cy = img.getHeight() / 2;
            double r = 0;
            double g = 0;
            double b = 0;
            for (int y = cy - 16; y < cy + 16; y++) {
                for (int x = cx - 16; x < cx + 16; x++) {
                    int abgr = img.getPixelRGBA(x, y);
                    r += abgr & 0xFF;
                    g += (abgr >> 8) & 0xFF;
                    b += (abgr >> 16) & 0xFF;
                }
            }
            return new float[] {(float) (r / 1024 / 255), (float) (g / 1024 / 255), (float) (b / 1024 / 255)};
        }
    }

    private static double distance(float[] a, float[] b) {
        return Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
    }

    private static String hex(float[] c) {
        return c == null ? "none" : String.format(Locale.ROOT, "#%02X%02X%02X", Math.round(c[0] * 255), Math.round(c[1] * 255), Math.round(c[2] * 255));
    }

    private static void fly(Minecraft mc) {
        mc.player.getAbilities().flying = true;
        mc.player.onUpdateAbilities();
    }

    // ------------------------------------------------------------------ checks

    private static void checks(Steps steps, Minecraft mc) {
        long[] before = {0};
        int[] cues = {0};
        int[] outside = {0};
        double[] mid = {0};
        steps.check("Aetheria's effects are ours, with no cloud height", () ->
                        mc.level.effects() instanceof AetheriaSkyEffects && Float.isNaN(mc.level.effects().getCloudHeight()))
                .check("the clouds hook skips vanilla's clouds (clouds are on in the options)", () ->
                        mc.options.getCloudsType() != CloudStatus.OFF
                                && mc.level.effects().renderClouds(mc.level, 0, 0f, new com.mojang.blaze3d.vertex.PoseStack(), 0, 192, 0,
                                new org.joml.Matrix4f(), new org.joml.Matrix4f()))
                .run("remember the sky's frame count", () -> before[0] = AetheriaSkyRenderer.frames())
                .waitTicks(10)
                .check("our sky is drawing", () -> AetheriaSkyRenderer.frames() > before[0] + 5)
                .log("frame", () -> describe(SkyState.frame()))
                .check("Reach fog: the horizon colour #F3E6C4 at noon", () -> near(SkyState.frame().horizon, 0xF3E6C4, 0.01f))
                .check("Reach fog: 55% to 100% of the render distance", () -> {
                    float rd = mc.options.getEffectiveRenderDistance() * 16f;
                    float[] fog = AetheriaFog.lastDistances();
                    return Math.abs(fog[0] - 0.55f * rd) < 1f && Math.abs(fog[1] - rd) < 1f;
                })
                .check("the fog's vertical factor is on (shape index above vanilla's 1)", () -> AetheriaFog.activeShapeIndex() > 1)
                .log("fog", () -> String.format(Locale.ROOT, "fog start %.1f end %.1f, shape index %d (vertical counts %.2f)",
                        AetheriaFog.lastDistances()[0], AetheriaFog.lastDistances()[1], AetheriaFog.activeShapeIndex(),
                        (AetheriaFog.activeShapeIndex() - 1) * 0.05))
                .check("the Arrival cue was sent once on entering", () -> AetheriaMusic.arrivalCues() == 1)
                .run("remember the cue count", () -> cues[0] = AetheriaMusic.arrivalCues())

                // the cross-fade: a teleport to the Drift fades sky and fog over about 3 s
                .command("execute in " + AetheriaWorld.LEVEL.location() + " run tp @s " + VIEW_X + " 240 " + VIEW_Z)
                .waitUntil("below Shear band A", 200, () -> mc.player.getY() < 300)
                .waitTicks(30)
                .run("halfway through the fade", () -> mid[0] = SkyState.frame().layers[1])
                .log("fade", () -> String.format(Locale.ROOT, "1.5 s after the teleport the Drift's weight is %.2f", mid[0]))
                .check("halfway, both layers show", () -> mid[0] > 0.2 && mid[0] < 0.8)
                .waitTicks(40)
                .check("after 3.5 s the Drift is all there is", () -> SkyState.frame().layers[1] > 0.999)
                .check("Drift fog: #A8D8E8 from 45%", () -> near(SkyState.frame().horizon, 0xA8D8E8, 0.01f)
                        && Math.abs(SkyState.frame().fogStart - 0.45f) < 0.001f)
                .command("execute in " + AetheriaWorld.LEVEL.location() + " run tp @s " + VIEW_X + " 100 " + VIEW_Z)
                .waitTicks(80)
                .check("Deep fog: #25123F from 25% to 80%", () -> near(SkyState.frame().horizon, 0x25123F, 0.01f)
                        && Math.abs(SkyState.frame().fogStart - 0.25f) < 0.001f && Math.abs(SkyState.frame().fogEnd - 0.8f) < 0.001f)

                // leaving and coming back does not replay the Arrival cue
                .command("execute in minecraft:overworld run tp @s 0 100 0")
                .waitUntil("in the Overworld", 400, () -> mc.level.dimension() == net.minecraft.world.level.Level.OVERWORLD)
                // the level can change in the packets handled just before this tick, with no frame drawn since: the
                // fog state is per frame, so wait for two whole frames drawn in the Overworld before reading it
                .run("mark the frames", () -> outside[0] = AetheriaFog.framesOutside())
                .waitUntil("two frames drawn in the Overworld", 200, () -> AetheriaFog.framesOutside() - outside[0] >= 2)
                .check("no Aetheria fog outside Aetheria", () -> AetheriaFog.activeShapeIndex() == 0)
                .command("execute in " + AetheriaWorld.LEVEL.location() + " run tp @s " + VIEW_X + " 400 " + VIEW_Z)
                .waitUntil("back in Aetheria", 600, () -> inAetheria(mc))
                .waitTicks(20)
                .check("the Arrival cue did not play again", () -> AetheriaMusic.arrivalCues() == cues[0])
                .check("the server remembers it", () -> server(s -> player(s).getData(AetheriaAudio.HEARD_ARRIVAL), 5))
                .command("cosmicbreach debug goto reach")
                .waitUntil("the island is drawn", 1600, settled(mc, 1500))

                // sound: beds and tracks per layer, creative music replaced
                .check("each biome loops its layer's bed and plays its layer's track", () -> soundsWired(mc))
                .check("every Aetheria sound event resolves to a sound file", () -> soundsResolve(mc))
                .check("in creative, Aetheria plays its own track rather than vanilla's", () -> {
                    Music chosen = net.neoforged.neoforge.client.ClientHooks.selectMusic(mc.getSituationalMusic(), null);
                    return chosen != null && chosen != Musics.CREATIVE && chosen.getEvent().is(AetheriaAudio.MUSIC_REACH.getKey());
                })

                // the shared clock
                .check("the Vesper clock: 12 ticks a beat, the pulse peaks on the beat", () ->
                        VesperClock.TICKS_PER_BEAT == 12 && VesperClock.isBeat(mc.level.getGameTime() - VesperClock.tickInBeat(mc.level.getGameTime()))
                                && VesperClock.pulse(1200, 0f) == 1f)

                // Iris: with a shader pack on, our sky and cloud hooks step aside
                .run("pretend a shader pack is on", () -> {
                    ShaderPacks.forced = true;
                    look(mc, WEST, -10);
                })
                .waitTicks(3)
                .run("remember the sky's frame count", () -> before[0] = AetheriaSkyRenderer.frames())
                .waitTicks(10)
                .check("under a shader pack our sky is not drawn", () -> AetheriaSkyRenderer.frames() == before[0])
                .check("and the clouds hook hands clouds back to the pack", () -> !mc.level.effects().renderClouds(mc.level, 0, 0f,
                        new com.mojang.blaze3d.vertex.PoseStack(), 0, 192, 0, new org.joml.Matrix4f(), new org.joml.Matrix4f()))
                .check("fog colours still apply", () -> near(SkyState.frame().horizon, 0xF3E6C4, 0.01f))
                .screenshot("iris_fallback_vanilla_sky")
                .run("shader pack off", () -> ShaderPacks.forced = null)
                .waitTicks(3)
                .check("our sky is back", () -> AetheriaSkyRenderer.frames() > before[0]);
    }

    private static boolean soundsWired(Minecraft mc) {
        String[][] expect = {{"shattered_spires", "reach"}, {"sunfield_terraces", "reach"}, {"drift_belt", "drift"}, {"rift_abyss", "deep"}};
        var biomes = mc.level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        for (String[] e : expect) {
            Biome biome = biomes.get(com.cosmicbreach.CosmicBreach.id(e[0]));
            if (biome == null) {
                throw new Steps.Failure("biome " + e[0] + " missing on the client");
            }
            Optional<Holder<SoundEvent>> bed = biome.getAmbientLoop();
            Optional<Music> music = biome.getBackgroundMusic();
            ResourceLocation wantBed = com.cosmicbreach.CosmicBreach.id("ambient/" + e[1]);
            ResourceLocation wantMusic = com.cosmicbreach.CosmicBreach.id("music/" + e[1]);
            if (bed.isEmpty() || !bed.get().value().getLocation().equals(wantBed)) {
                throw new Steps.Failure(e[0] + " loops " + bed.map(h -> h.value().getLocation().toString()).orElse("nothing") + ", expected " + wantBed);
            }
            if (music.isEmpty() || !music.get().getEvent().value().getLocation().equals(wantMusic)) {
                throw new Steps.Failure(e[0] + " plays " + music.map(m -> m.getEvent().value().getLocation().toString()).orElse("nothing")
                        + ", expected " + wantMusic);
            }
        }
        return true;
    }

    private static boolean soundsResolve(Minecraft mc) {
        for (var holder : AetheriaAudio.SOUNDS.getEntries()) {
            ResourceLocation id = holder.getId();
            var events = mc.getSoundManager().getSoundEvent(id);
            if (events == null) {
                throw new Steps.Failure("no sounds.json entry for " + id);
            }
            var sound = events.getSound(net.minecraft.util.RandomSource.create());
            ResourceLocation file = sound.getPath();
            if (mc.getResourceManager().getResource(file).isEmpty()) {
                throw new Steps.Failure(id + " points at a missing file " + file);
            }
            if (events.getSubtitle() == null) {
                throw new Steps.Failure(id + " has no subtitle");
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ cost

    private static void cost(Steps steps, Minecraft mc) {
        steps.command("execute in " + AetheriaWorld.LEVEL.location() + " run tp @s " + VIEW_X + " 400 " + VIEW_Z)
                .command("cosmicbreach debug goto reach")
                .waitUntil("the island is drawn", 1600, settled(mc, 1500));
        time(steps, mc, 12300);
        steps.command("time set 12300")
                .run("face the busiest sky: Thalassa, the eclipse, the aurora", () -> look(mc, WEST + 20, -18))
                .waitTicks(10)
                .run("start measuring", () -> {
                    SkyStats.reset();
                    SkyStats.enabled = true;
                })
                .waitUntil("400 frames measured", 2400, () -> SkyStats.samples() >= 400)
                .run("stop measuring", () -> SkyStats.enabled = false)
                .log("cost", SkyStats::report)
                .check("the sky costs under 0.5 ms of GPU time a frame (median)", () -> {
                    double gpu = SkyStats.gpuMedianMs();
                    return Double.isNaN(gpu) || gpu < 0.5;
                })
                .check("and under 0.5 ms of CPU time a frame (median)", () -> SkyStats.cpuMedianMs() < 0.5)
                .screenshot("cost_view");
    }

    // ------------------------------------------------------------------ helpers

    private static void look(Minecraft mc, float yaw, float pitch) {
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        mc.player.yRotO = yaw;
        mc.player.xRotO = pitch;
    }

    private static boolean near(float[] rgb, int hex, float tolerance) {
        return Math.abs(rgb[0] - ((hex >> 16) & 0xFF) / 255f) <= tolerance
                && Math.abs(rgb[1] - ((hex >> 8) & 0xFF) / 255f) <= tolerance
                && Math.abs(rgb[2] - (hex & 0xFF) / 255f) <= tolerance;
    }

    private static String describe(SkyFrame f) {
        return String.format(Locale.ROOT, "layers %.2f/%.2f/%.2f, daylight %.2f, sun %.2f/%.2f/%.2f visible %.2f eclipse %.2f, planet radius %.1f deg, "
                        + "stars %.2f, nebula %.2f/%.2f veil %.2f, aurora %.2f, Vesper core %.2f beam %.2f",
                f.layers[0], f.layers[1], f.layers[2], f.daylight, f.sunDir[0], f.sunDir[1], f.sunDir[2], f.sunVisible, f.eclipse,
                Math.toDegrees(f.planetRadius), f.stars[0], f.nebula[0], f.nebula[1], f.nebula[3], f.aurora, f.vesperCore, f.vesperBeam);
    }

    private static boolean inAetheria(Minecraft mc) {
        return mc.level != null && mc.level.dimension() == AetheriaWorld.LEVEL && mc.player != null;
    }

    private static String where(Minecraft mc) {
        return String.format(Locale.ROOT, "at %s in %s, layer %s, facing %.0f/%.0f", mc.player.blockPosition().toShortString(),
                mc.level.dimension().location(), Layer.at(mc.player.getY()), mc.player.getYRot(), mc.player.getXRot());
    }

    /** No screen, the chunks within 5 have arrived, and the section queue has stayed empty for a while. */
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
