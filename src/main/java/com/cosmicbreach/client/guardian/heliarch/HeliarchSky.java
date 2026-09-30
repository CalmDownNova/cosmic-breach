package com.cosmicbreach.client.guardian.heliarch;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.fx.FxRenderTypes;
import com.cosmicbreach.client.sanctum.SanctumClient;
import com.cosmicbreach.client.sky.SkyFrame;
import com.cosmicbreach.client.sky.SkyState;
import com.cosmicbreach.client.sky.SkyWeather;
import com.cosmicbreach.client.weather.WeatherClient;
import com.cosmicbreach.guardian.heliarch.HeliarchArena;
import com.cosmicbreach.guardian.heliarch.HeliarchPose;
import com.cosmicbreach.guardian.heliarch.HollowHeliarch;
import com.cosmicbreach.guardian.heliarch.HollowHeliarch.State;
import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.VesperClock;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The Heliarch in the sky and over the Breach, on the client. Through the fight Solenne is eclipsed: the dome, the fog
 * and the sky's light dim (through {@link SkyWeather#setSkyDim}, never below the weather's own), and a black disc with a
 * burning rim covers the sun (a sky layer), coming on over the intro and lifting as the Heliarch dies. The last embers
 * of Solenne ({@link SanctumClient}) are hidden while it fights. After a world's first kill a seal of light hangs over
 * the Breach for good: a slow golden sigil over the arena, pulsing on Vesper's beat, seen from all round the Breach.
 */
public final class HeliarchSky {
    private static final ResourceLocation SEAL = CosmicBreach.id("textures/fx/heliarch_seal.png");
    private static final ResourceLocation CORONA = CosmicBreach.id("textures/fx/heliarch_corona.png");
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new ByteBufferBuilder(1 << 14));
    private static final float DISTANCE = 40f;
    /** The seal hangs this high over the arena's floor, and this wide. */
    public static final double SEAL_UP = 44.0;
    public static final double SEAL_RADIUS = 30.0;

    private static boolean sealed;
    private static long formingStart = -1;
    private static float eclipse;
    /** How far Solenne has been placed behind the throne (0 where the sky has it). */
    private static float place;
    private static float placeWant;
    private static int side = 1;
    private static boolean placed;
    /** Solenne's height over the horizon behind the throne, in degrees. */
    static final double SOLENNE_ELEVATION = 13.0;
    /** The sky's dimming at a full eclipse, and the lightmap's (the whole arena, not only the sky). */
    static final float SKY_DIM = 0.85f;
    static final float ARENA_DIM = 0.5f;
    /** The eclipsed Solenne behind the throne is this many times the sun's disc. */
    static final float DISC_SCALE = 1.6f;
    private static float flash;
    private static @Nullable VertexBuffer buffer;
    private static int sealFrames;
    private static int eclipseFrames;

    private HeliarchSky() {
    }

    static void register(IEventBus game) {
        SkyWeather.addLayer(HeliarchSky::skyLayer);
        game.addListener(RenderLevelStageEvent.class, HeliarchSky::render);
        SanctumClient.hideEmbers = () -> HeliarchFx.heliarch() != null;
        com.cosmicbreach.client.sky.AetheriaSkyEffects.fightDim = HeliarchSky::fightDim;
    }

    static void setSealed(boolean on, boolean forming) {
        sealed = on;
        Minecraft mc = Minecraft.getInstance();
        formingStart = forming && mc.level != null ? mc.level.getGameTime() : -1;
    }

    public static boolean sealed() {
        return sealed;
    }

    /** How eclipsed Solenne is now, 0 to 1 (for checks). */
    public static float eclipse() {
        return eclipse;
    }

    public static int sealFrames() {
        return sealFrames;
    }

    public static int eclipseFrames() {
        return eclipseFrames;
    }

    /** A white flash over the sky and the screen (Nova's detonation, the death). */
    static void flash(float amount) {
        flash = Math.max(flash, amount);
    }

    static float flashNow() {
        return flash;
    }

    static void clear() {
        sealed = false;
        formingStart = -1;
        eclipse = 0f;
        flash = 0f;
        place = 0f;
        placeWant = 0f;
        if (placed) {
            SkyWeather.setSolenneAt(0.0, 1.0, 0.0, 0f);
            placed = false;
        }
    }

    /** Each client tick: how eclipsed the sky should be, eased; handed to the sky above the weather's own dimming. */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        flash *= 0.85f;
        float want = 0f;
        HollowHeliarch h = HeliarchFx.heliarch();
        if (h != null && mc.level != null) {
            double s = mc.level.getGameTime() - h.stateStart();
            State st = h.state();
            // the eclipse closes over the intro's first seconds (the moment the fight begins), and holds
            want = switch (st) {
                case INTRO -> (float) HeliarchPose.smooth((s - 20) / 70.0);
                case REGENT, HOLLOWING, HOLLOW, COLLAPSE -> 1f;
                case DYING -> (float) (1.0 - HeliarchPose.smooth((s - 2) / 20.0));
            };
            // Solenne swings down behind the throne as seen from the entrance, low over the far rim
            placeWant = st == State.INTRO ? (float) HeliarchPose.smooth(s / 40.0) : 1f;
            side = h.side();
        } else {
            placeWant = 0f;
        }
        eclipse += (want - eclipse) * 0.1f;
        if (Math.abs(want - eclipse) < 1e-3f) {
            eclipse = want;
        }
        place += (placeWant - place) * (placeWant > place ? 0.25f : 0.04f);
        if (Math.abs(placeWant - place) < 1e-3f) {
            place = placeWant;
        }
        if (mc.level != null && mc.level.dimension() == AetheriaWorld.LEVEL) {
            if (eclipse > 0.001f) {
                float weather = WeatherClient.hooks()[2] * (float) SkyState.frame().layers[2];
                SkyWeather.setSkyDim(Math.max(SKY_DIM * eclipse, weather));
            }
            if (place > 0.001f || placed) {
                double e = Math.toRadians(SOLENNE_ELEVATION);
                placed = place > 0.001f;
                SkyWeather.setSolenneAt(0.0, Math.sin(e), -side * Math.cos(e), placed ? place : 0f);
            }
        }
    }

    /** How dark the lightmap goes for the fight now (everything lit but the boss, the telegraphs and what glows). */
    static double fightDim() {
        return ARENA_DIM * eclipse;
    }

    // ------------------------------------------------------------------ the sun eclipsed (a sky layer)

    private static void skyLayer(Matrix4f view, Matrix4f projection, Camera camera, float partialTick, SkyFrame frame, PoseStack scratch) {
        if (eclipse < 0.01f || frame.sunVisible < 0.01f) {
            return;
        }
        ShaderInstance shader = GameRenderer.getPositionTexColorShader();
        if (shader == null) {
            return;
        }
        if (buffer == null) {
            buffer = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        }
        Vector3f n = new Vector3f(frame.sunDir[0], frame.sunDir[1], frame.sunDir[2]).normalize();
        Vector3f e1 = new Vector3f(n).cross(Math.abs(n.y) < 0.99f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0)).normalize();
        Vector3f e2 = new Vector3f(n).cross(e1).normalize();
        Vector3f c = new Vector3f(n).mul(DISTANCE);
        // the disc keeps its size and fades (shrinking, it left a bright bead of Solenne at its edge)
        float disc = (float) (DISTANCE * Math.tan(frame.sunRadius * 1.1f * (1f + (DISC_SCALE - 1f) * place)));
        float cover = Math.min(1f, eclipse / 0.6f);
        // the moon of the Hollow over the sun: black, blended over it
        RenderSystem.setShaderTexture(0, FxRenderTypes.GLOW);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR);
        int segs = 40;
        for (int i = 0; i < segs; i++) {
            double a0 = Math.PI * 2 * i / segs;
            double a1 = Math.PI * 2 * (i + 1) / segs;
            vertex(b, c, 0.5f, 0.5f, 0.01f, 0.005f, 0.02f, cover);
            vertex(b, new Vector3f(c).add(new Vector3f(e1).mul((float) Math.cos(a0) * disc)).add(new Vector3f(e2).mul((float) Math.sin(a0) * disc)),
                    0.5f, 0.5f, 0.01f, 0.005f, 0.02f, cover);
            vertex(b, new Vector3f(c).add(new Vector3f(e1).mul((float) Math.cos(a1) * disc)).add(new Vector3f(e2).mul((float) Math.sin(a1) * disc)),
                    0.5f, 0.5f, 0.01f, 0.005f, 0.02f, cover);
        }
        draw(b, view, projection, shader);
        // its burning rim
        RenderSystem.setShaderTexture(0, CORONA);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        BufferBuilder r = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float half = disc / 0.62f;
        float glow = Math.min(1f, eclipse / 0.6f);
        quad(r, c, e1, e2, half, 1.0f, 0.62f, 0.25f, glow);
        quad(r, c, e1, e2, half * 1.6f, 0.7f, 0.3f, 1.0f, 0.4f * glow);
        draw(r, view, projection, shader);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        eclipseFrames++;
    }

    private static void vertex(BufferBuilder b, Vector3f p, float u, float v, float r, float g, float bl, float a) {
        b.addVertex(p.x, p.y, p.z).setUv(u, v).setColor(r, g, bl, a);
    }

    private static void quad(BufferBuilder b, Vector3f c, Vector3f e1, Vector3f e2, float half, float r, float g, float bl, float a) {
        float[][] corners = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        for (float[] k : corners) {
            Vector3f p = new Vector3f(c).add(new Vector3f(e1).mul(k[0] * half)).add(new Vector3f(e2).mul(k[1] * half));
            vertex(b, p, (k[0] + 1) * 0.5f, (k[1] + 1) * 0.5f, r, g, bl, a);
        }
    }

    private static void draw(BufferBuilder builder, Matrix4f view, Matrix4f projection, ShaderInstance shader) {
        MeshData mesh = builder.build();
        if (mesh == null || buffer == null) {
            return;
        }
        buffer.bind();
        buffer.upload(mesh);
        buffer.drawWithShader(view, projection, shader);
        VertexBuffer.unbind();
    }

    // ------------------------------------------------------------------ the seal over the Breach

    private static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !sealed) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.dimension() != AetheriaWorld.LEVEL || HeliarchFx.heliarch() != null) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vec3 centre = new Vec3(HeliarchArena.CX, HeliarchArena.FLOOR + SEAL_UP, HeliarchArena.CZ);
        Vec3 rel = centre.subtract(cam);
        double far = rel.length();
        if (far > 600) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double time = mc.level.getGameTime() + partial;
        double form = 1.0;
        float burst = 0f;
        if (formingStart >= 0) {
            double s = time - formingStart;
            form = HeliarchPose.smooth(s / 100.0);
            burst = (float) Math.max(0.0, 1.0 - s / 60.0);
            if (s > 200) {
                formingStart = -1;
            }
        }
        long t = (long) Math.floor(time);
        float beat = (float) Math.exp(-(VesperClock.tickInBeat(t) + (time - t)) / VesperClock.PULSE_DECAY);
        float pulse = 0.75f + 0.25f * beat;
        // at a short render distance keep it inside the far plane, at the same apparent size
        double limit = mc.gameRenderer.getDepthFar() * 0.8;
        double scale = far > limit ? limit / far : 1.0;
        Vec3 c = rel.scale(scale);
        float radius = (float) (SEAL_RADIUS * form * scale);
        double spin = Math.toRadians(time * 0.05);
        Vec3 ax = new Vec3(Math.cos(spin), 0, Math.sin(spin)).scale(radius);
        Vec3 az = new Vec3(-Math.sin(spin), 0, Math.cos(spin)).scale(radius);
        float fogStart = RenderSystem.getShaderFogStart();
        float fogEnd = RenderSystem.getShaderFogEnd();
        RenderSystem.setShaderFogStart(1.0e7f);
        RenderSystem.setShaderFogEnd(2.0e7f);
        try {
            VertexConsumer out = BUFFERS.getBuffer(FxRenderTypes.additive(SEAL));
            com.cosmicbreach.client.entity.ShardDraw.quad(out, new Matrix4f(), c, ax, az, 1.0f, 0.78f, 0.36f, 0.8f * pulse);
            com.cosmicbreach.client.entity.ShardDraw.quad(out, new Matrix4f(), c.add(0, -0.3, 0), ax.scale(0.98), az.scale(0.98), 1.0f, 0.95f,
                    0.8f, 0.45f * pulse + burst);
            VertexConsumer glow = BUFFERS.getBuffer(FxRenderTypes.additive(FxRenderTypes.GLOW));
            com.cosmicbreach.client.entity.ShardDraw.quad(glow, new Matrix4f(), c, ax.scale(1.3), az.scale(1.3), 1.0f, 0.6f, 0.2f,
                    0.25f * pulse + 0.6f * burst);
            BUFFERS.endBatch();
            sealFrames++;
        } finally {
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
            RenderSystem.defaultBlendFunc();
        }
    }
}
