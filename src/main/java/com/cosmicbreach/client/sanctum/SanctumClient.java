package com.cosmicbreach.client.sanctum;

import com.cosmicbreach.client.fx.FxRenderTypes;
import com.cosmicbreach.client.fx.WorldFx;
import com.cosmicbreach.client.sky.SkyState;
import com.cosmicbreach.client.sky.SkyWeather;
import com.cosmicbreach.client.weather.WeatherClient;
import com.cosmicbreach.structure.sanctum.SanctumLayout;
import com.cosmicbreach.structure.sanctum.SanctumPasses;
import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.Layer;
import com.cosmicbreach.world.VesperClock;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * The Breach Sanctum on the client: its own pass through the Gate's veil ({@link SanctumPasses}); the sky's flicker
 * when a Heart on the throne meets silence (dips of the Eclipse Surge's dimming, laid over the weather's own); and
 * the last embers of Solenne: a warm glow hanging over the arena and a faint column of light under it, drawn without
 * fog while the camera is in the Deep, so the Sanctum reads as the destination from any platform round the Breach
 * (terrain in front still hides it; within 36 blocks the arena's own embers take over).
 */
public final class SanctumClient {
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new ByteBufferBuilder(1 << 14));
    /**
     * The glow hangs this far over the throne: above the halls' roofs, and high enough to clear a platform's own rim
     * when seen from across the Breach (from a platform's top the arena itself lies below the horizon).
     */
    private static final double GLOW_UP = 40.0;
    private static final double COLUMN_TOP = Layer.DEEP.bandMaxY - 12;
    private static long flickerStart = -1;
    private static int flickerTicks;
    private static float flicker;
    private static int glowFrames;
    private static int flickerPeaks;
    /** While true the embers' glow is not drawn (the Heliarch fights under its own eclipse). */
    public static java.util.function.BooleanSupplier hideEmbers = () -> false;

    private SanctumClient() {
    }

    public static void register(IEventBus modBus, IEventBus game) {
        game.addListener(EventPriority.LOWEST, false, ClientTickEvent.Post.class, event -> tick());
        game.addListener(RenderLevelStageEvent.class, SanctumClient::render);
        game.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> reset());
    }

    public static void onPass(boolean pass) {
        SanctumPasses.setClient(pass);
    }

    public static void onFlicker(int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            flickerStart = mc.level.getGameTime();
            flickerTicks = Math.max(1, ticks);
        }
    }

    /** The flicker's dimming now, 0 to 1 (for checks). */
    public static float flicker() {
        return flicker;
    }

    /** Ticks in which the flicker dimmed the sky past half (for checks). */
    public static int flickerPeaks() {
        return flickerPeaks;
    }

    /** Frames the embers' glow was drawn (for checks). */
    public static int glowFrames() {
        return glowFrames;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || flickerStart < 0) {
            return;
        }
        long age = mc.level.getGameTime() - flickerStart;
        if (age > flickerTicks || age < 0) {
            flickerStart = -1;
            flicker = 0f;
            return;
        }
        // a dying star's stutter: irregular dips, deepest in the middle of the silence
        double envelope = Math.sin(Math.PI * age / flickerTicks);
        double stutter = Math.floorMod(age * 7, 11) < 4 ? 1.0 : 0.3;
        flicker = (float) Math.min(1.0, 0.95 * envelope * stutter);
        if (flicker > 0.5f) {
            flickerPeaks++;
        }
        float weather = WeatherClient.hooks()[2] * (float) SkyState.frame().layers[2];
        SkyWeather.setSkyDim(Math.max(flicker, weather));
    }

    private static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.dimension() != AetheriaWorld.LEVEL) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        if (cam.y >= Layer.DEEP.bandMaxY || hideEmbers.getAsBoolean()) {
            return;
        }
        Vec3 heart = new Vec3(0.5, SanctumLayout.ARENA_Y + GLOW_UP, 0.5);
        Vec3 rel = heart.subtract(cam);
        double far = rel.length();
        if (far < 36.0) {
            return;
        }
        float fade = (float) Math.min(1.0, (far - 36.0) / 40.0);
        // keep it inside the far plane at short render distances, at the same apparent size
        double limit = mc.gameRenderer.getDepthFar() * 0.8;
        double scale = far > limit ? limit / far : 1.0;
        rel = rel.scale(scale);
        double dist = far * scale;
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long time = mc.level.getGameTime();
        float beat = (float) Math.exp(-(VesperClock.tickInBeat(time) + partialTick) / VesperClock.PULSE_DECAY);
        float pulse = 0.8f + 0.2f * beat;
        float fogStart = RenderSystem.getShaderFogStart();
        float fogEnd = RenderSystem.getShaderFogEnd();
        RenderSystem.setShaderFogStart(1.0e7f);
        RenderSystem.setShaderFogEnd(2.0e7f);
        try {
            VertexConsumer glow = BUFFERS.getBuffer(FxRenderTypes.additive(FxRenderTypes.GLOW));
            float halo = (float) Math.max(12.0, dist * 0.1);
            float core = (float) Math.max(3.0, dist * 0.024);
            WorldFx.billboard(glow, camera, rel, halo, 1.0f, 0.45f, 0.12f, 0.6f * fade * pulse);
            WorldFx.billboard(glow, camera, rel, halo * 0.45f, 1.0f, 0.7f, 0.3f, 0.8f * fade * pulse);
            WorldFx.billboard(glow, camera, rel, core, 1.0f, 0.93f, 0.7f, fade);
            VertexConsumer beam = BUFFERS.getBuffer(FxRenderTypes.additive(FxRenderTypes.BEAM));
            Vec3 bottom = new Vec3(0.5, SanctumLayout.ARENA_Y - 20, 0.5).subtract(cam).scale(scale);
            Vec3 top = new Vec3(0.5, COLUMN_TOP, 0.5).subtract(cam).scale(scale);
            column(beam, bottom, top, (float) Math.max(1.2, dist * 0.012), 1.0f, 0.6f, 0.25f, 0.5f * fade * pulse);
            BUFFERS.endBatch();
            glowFrames++;
        } finally {
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
            RenderSystem.defaultBlendFunc();
        }
    }

    /** A vertical strip from {@code a} up to {@code b} (camera-relative), turned to face the camera, brightest level with the glow, fading at both ends. */
    private static void column(VertexConsumer out, Vec3 a, Vec3 b, float half, float r, float g, float bl, float alpha) {
        Vec3 mid = a.add(b).scale(0.5);
        Vec3 side = new Vec3(-mid.z, 0, mid.x);
        if (side.lengthSqr() < 1e-8) {
            return;
        }
        side = side.normalize().scale(half);
        Vec3 m = a.add(b.subtract(a).scale(0.65));
        WorldFx.vertex(out, a.subtract(side), 0f, 0f, r, g, bl, 0f);
        WorldFx.vertex(out, m.subtract(side), 0f, 0.5f, r, g, bl, alpha);
        WorldFx.vertex(out, m.add(side), 1f, 0.5f, r, g, bl, alpha);
        WorldFx.vertex(out, a.add(side), 1f, 0f, r, g, bl, 0f);
        WorldFx.vertex(out, m.subtract(side), 0f, 0.5f, r, g, bl, alpha);
        WorldFx.vertex(out, b.subtract(side), 0f, 1f, r, g, bl, 0f);
        WorldFx.vertex(out, b.add(side), 1f, 1f, r, g, bl, 0f);
        WorldFx.vertex(out, m.add(side), 1f, 0.5f, r, g, bl, alpha);
    }

    private static void reset() {
        SanctumPasses.setClient(false);
        flickerStart = -1;
        flicker = 0f;
    }
}
