package com.cosmicbreach.client.fx;

import com.cosmicbreach.client.anim.PlayerAnimations;
import com.cosmicbreach.mixin.client.CompositeStateTextureAccessor;
import com.cosmicbreach.mixin.client.RenderTypeTextureAccessor;
import com.cosmicbreach.mixin.client.TextureShardAccessor;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.util.TriState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Ghosts of a player that move on their own: the Driftweave's Afterimage (a star-dust copy standing where a perfect
 * dodge left it, repeating its wearer's attacks) and the Choir Regalia's echoes (an ivory-gold copy replaying an
 * ability where it was cast).
 *
 * <p>A ghost is a body of its own, a client-only copy of the player (never in the level, so nothing else draws or
 * ticks it; a negative id, so it never meets a server entity) wearing the owner's skin, armor and held items, posed
 * every frame by the combat animation layer: a move's animation at the replay's time, or the pose it was left in.
 * Each frame it is drawn through its own player renderer into a recorder that keeps every quad by the texture it
 * was drawn with (GeckoLib's armor included: {@code GeoArmorRendererCaptureMixin} points it at the recorder while
 * one records). The quads are then redrawn with those textures, so cut-out pixels stay cut out: once translucent and
 * tinted in the ghost's colours, once as added light (the eyes shader), brighter toward the head, each quad
 * flickering a little like dust. It fades in, fades out at its end, and fades when the camera is right on it.
 */
public final class GhostBodies {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_QUADS = 8000;
    private static final int FADE_IN = 3;
    private static final int FADE_OUT = 8;
    /** 5 floats a vertex: x, y, z (world), u, v. */
    private static final int STRIDE = 5;
    private static int nextId = -2_000_000;

    /** How a ghost looks: its tint at the feet and at the head, its body's opacity and its glow's strength. */
    public enum Style {
        STARDUST(0x7A6CFF, 0xC8F6FF, 0.13f, 0.30f, 1.0f),
        CHOIR(0xF0C060, 0xFFF6DC, 0.34f, 0.45f, 0.55f);

        final float[] low;
        final float[] high;
        final int lowRgb;
        final int highRgb;
        final float body;
        final float glow;
        /** How much of it is loose sparkling dust (points of light over the body, motes drifting off). */
        final float sparkle;

        Style(int low, int high, float body, float glow, float sparkle) {
            this.low = WorldFx.rgb(low);
            this.high = WorldFx.rgb(high);
            this.lowRgb = low;
            this.highRgb = high;
            this.body = body;
            this.glow = glow;
            this.sparkle = sparkle;
        }
    }

    /** The copy's body: the owner's skin, never added to the level. */
    static final class GhostBody extends RemotePlayer {
        private final PlayerSkin skin;

        GhostBody(ClientLevel level, AbstractClientPlayer owner) {
            super(level, new GameProfile(UUID.randomUUID(), owner.getGameProfile().getName()));
            this.skin = owner.getSkin();
            setId(nextId--);
            getEntityData().set(Player.DATA_PLAYER_MODE_CUSTOMISATION, owner.getEntityData().get(Player.DATA_PLAYER_MODE_CUSTOMISATION));
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                setItemSlot(slot, owner.getItemBySlot(slot).copy());
            }
        }

        @Override
        public PlayerSkin getSkin() {
            return skin;
        }

        void place(Vec3 feet, float yaw) {
            moveTo(feet.x, feet.y, feet.z, yaw, 0f);
            setOldPosAndRot();
            setYHeadRot(yaw);
            yHeadRotO = yaw;
            yBodyRot = yaw;
            yBodyRotO = yaw;
            setOnGround(true);
        }
    }

    /** One ghost: where it stands, what it plays, how long it lasts, and its quads as last recorded. */
    public static final class Ghost implements WorldFx.Effect {
        private final GhostBody body;
        private final Style style;
        private final double born;
        private double endsAt;
        private @Nullable ResourceLocation animation;
        private boolean mirrored;
        private double animationStart = Double.NaN;
        private double heldTick;
        private int length;
        private float strength = 1f;
        private final Map<ResourceLocation, Recorder> quads = new LinkedHashMap<>();

        Ghost(GhostBody body, Style style, int life) {
            this.body = body;
            this.style = style;
            this.born = FxClock.ticks();
            this.endsAt = born + life;
        }

        /** Plays {@code animation} from its start now, over {@code ticks} (then holds its last pose). */
        public Ghost play(@Nullable ResourceLocation animation, int ticks, boolean mirrored) {
            this.animation = animation;
            this.mirrored = mirrored;
            this.animationStart = FxClock.ticks();
            this.length = Math.max(1, ticks);
            return this;
        }

        /** Holds {@code animation} still at {@code tick}. */
        public Ghost hold(@Nullable ResourceLocation animation, double tick, boolean mirrored) {
            this.animation = animation;
            this.mirrored = mirrored;
            this.animationStart = Double.NaN;
            this.heldTick = tick;
            return this;
        }

        /** Turns it to {@code yaw}, where it stands. */
        public Ghost face(float yaw) {
            body.place(body.position(), yaw);
            return this;
        }

        /** Lasts {@code ticks} more from now (and then fades). */
        public Ghost lastFor(int ticks) {
            endsAt = Math.max(endsAt, FxClock.ticks() + ticks);
            return this;
        }

        /** Starts fading out now. */
        public void fade() {
            endsAt = Math.min(endsAt, FxClock.ticks());
        }

        /** Scales how strongly it shows (1 as made). */
        public Ghost strength(float s) {
            this.strength = s;
            return this;
        }

        public Player body() {
            return body;
        }

        /** Quads recorded in the last frame (tests). */
        public int quadCount() {
            int n = 0;
            for (Recorder r : quads.values()) {
                n += r.vertices / 4;
            }
            return n;
        }

        boolean over(double now) {
            return now >= endsAt + FADE_OUT;
        }

        @Override
        public boolean tick() {
            long now = FxClock.ticks();
            if (now < endsAt) {
                int motes = style.sparkle >= 1f ? 2 : (now % 2 == 0 ? 1 : 0);
                for (int i = 0; i < motes; i++) {
                    shed();
                }
            }
            return !over(now);
        }

        /** A mote of its dust drifting off the body, from a point on its surface. */
        private void shed() {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null || !FxParticles.ready()) {
                return;
            }
            var random = level.getRandom();
            Vec3 at = surfacePoint(random.nextInt(Integer.MAX_VALUE));
            if (at == null) {
                at = body.position().add((random.nextDouble() - 0.5) * 0.7, 0.2 + random.nextDouble() * 1.6,
                        (random.nextDouble() - 0.5) * 0.7);
            }
            Vec3 drift = new Vec3((random.nextDouble() - 0.5) * 0.02, 0.008 + random.nextDouble() * 0.014, (random.nextDouble() - 0.5) * 0.02);
            FxBudget.spawn(FxParticles.glint(level, at).velocity(drift).drag(0.94f)
                    .size(0.05f + random.nextFloat() * 0.05f, 0.01f).life(10 + random.nextInt(10))
                    .color(random.nextInt(3) == 0 ? 0xFFFFFF : random.nextBoolean() ? style.highRgb : style.lowRgb).fade(0.1f, 1.0f).spin(0.2f));
        }

        /** The middle of one of the quads last recorded (pick by {@code seed}), in the world; null before any. */
        private Vec3 surfacePoint(int seed) {
            int total = 0;
            for (Recorder r : quads.values()) {
                total += r.vertices / 4;
            }
            if (total == 0) {
                return null;
            }
            int pick = Math.floorMod(seed, total);
            for (Recorder r : quads.values()) {
                int n = r.vertices / 4;
                if (pick < n) {
                    return r.centre(pick);
                }
                pick -= n;
            }
            return null;
        }

        /** Poses the body for this frame and records its quads (called before the effects draw). */
        void capture(Camera camera, float partialTick) {
            double now = FxClock.now(partialTick);
            if (animation != null) {
                double t = Double.isNaN(animationStart) ? heldTick : Math.min(length - 0.01, Math.max(0.0, now - animationStart));
                try {
                    PlayerAnimations.showPose(body, animation, mirrored, t);
                } catch (IllegalStateException e) {
                    animation = null; // no such animation here: the ghost stands as it is
                }
            }
            Minecraft mc = Minecraft.getInstance();
            EntityRenderer<? super Player> renderer = mc.getEntityRenderDispatcher().getRenderer(body);
            Vec3 cam = camera.getPosition();
            PoseStack pose = new PoseStack();
            pose.translate(body.getX() - cam.x, body.getY() - cam.y, body.getZ() - cam.z);
            for (Recorder r : quads.values()) {
                r.reset(cam);
            }
            CAPTURE.begin(this, cam);
            BladeTracker.suppress(true);
            try {
                renderer.render(body, body.getYRot(), partialTick, pose, CAPTURE, LightTexture.FULL_BRIGHT);
            } finally {
                BladeTracker.suppress(false);
                CAPTURE.end();
            }
        }

        @Override
        public void render(WorldFx.Frame f) {
            double now = f.now();
            double in = Math.min(1.0, (now - born) / FADE_IN);
            double out = Math.min(1.0, Math.max(0.0, (endsAt + FADE_OUT - now) / FADE_OUT));
            Vec3 cam = f.cameraPos();
            Vec3 middle = body.position().add(0, 1.0, 0);
            double near = Mth.clamp((middle.distanceTo(cam) - 1.2) / 1.4, 0.0, 1.0);
            float a = (float) (in * out * near * near) * strength;
            if (a <= 0.003f) {
                return;
            }
            double feet = body.getY();
            for (Map.Entry<ResourceLocation, Recorder> e : quads.entrySet()) {
                Recorder r = e.getValue();
                if (r.vertices < 4) {
                    continue;
                }
                emit(f.buffers().getBuffer(RenderType.entityTranslucent(e.getKey())), r, cam, feet, now, a * style.body, false);
                emit(f.buffers().getBuffer(RenderType.eyes(e.getKey())), r, cam, feet, now, a * style.glow, true);
            }
            sparkles(f, cam, feet, now, a);
        }

        /**
         * Points of light over the body, a different few every couple of ticks, twinkling: the star dust it is made of
         * (the Afterimage is mostly this; the echo's is fainter).
         */
        private void sparkles(WorldFx.Frame f, Vec3 cam, double feet, double now, float a) {
            if (style.sparkle <= 0f) {
                return;
            }
            VertexConsumer out = f.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.GLOW));
            long epoch = (long) Math.floor(now / 2.0);
            int index = 0;
            for (Recorder r : quads.values()) {
                int n = r.vertices / 4;
                for (int q = 0; q < n; q++, index++) {
                    long h = (index * 0x9E3779B97F4A7C15L + epoch * 0xC2B2AE3D27D4EB4FL) >>> 40;
                    if ((h & 1023) > (int) (160 * style.sparkle)) {
                        continue;
                    }
                    Vec3 at = r.centre(q);
                    double twinkle = 0.5 + 0.5 * Math.sin(now * 1.7 + index * 2.39);
                    float hgt = (float) Mth.clamp((at.y - feet) / 1.9, 0.0, 1.0);
                    boolean white = (h & 3) == 0;
                    float red = white ? 1f : Mth.lerp(hgt, style.low[0], style.high[0]);
                    float green = white ? 1f : Mth.lerp(hgt, style.low[1], style.high[1]);
                    float blue = white ? 1f : Mth.lerp(hgt, style.low[2], style.high[2]);
                    float half = (float) (0.04 + 0.06 * twinkle) * (white ? 1.35f : 1f);
                    WorldFx.billboard(out, f.camera(), at.subtract(cam), half, red, green, blue,
                            (float) (a * style.sparkle * (0.45 + 0.55 * twinkle)));
                }
            }
        }

        private void emit(VertexConsumer out, Recorder r, Vec3 cam, double feet, double now, float amount, boolean light) {
            float[] d = r.data;
            int quadsHere = r.vertices / 4;
            for (int q = 0; q < quadsHere; q++) {
                // dust: each quad breathes on its own, some thin almost to nothing for a moment, so the body reads
                // as a cloud of motes rather than glass; a few catch the light brightly
                double phase = (q * 0.618034) % 1.0;
                double breath = 0.5 + 0.5 * Math.sin(now * (0.25 + 0.2 * phase) + phase * Mth.TWO_PI * 3.0);
                float flicker = (float) (0.1 + 0.9 * breath * breath * breath);
                if (light && (q * 7919) % 19 == 0) {
                    flicker *= 1.5f + 0.5f * (float) Math.sin(now * 1.3 + q);
                }
                for (int v = 0; v < 4; v++) {
                    int i = (q * 4 + v) * STRIDE;
                    float h = (float) Mth.clamp((d[i + 1] - feet) / 1.9, 0.0, 1.0);
                    float k = amount * flicker * (0.8f + 0.3f * h);
                    float red = Mth.lerp(h, style.low[0], style.high[0]);
                    float green = Mth.lerp(h, style.low[1], style.high[1]);
                    float blue = Mth.lerp(h, style.low[2], style.high[2]);
                    int argb = light
                            ? 0xFF000000 | channel(red * k) << 16 | channel(green * k) << 8 | channel(blue * k)
                            : channel(Math.min(1f, k)) << 24 | channel(red) << 16 | channel(green) << 8 | channel(blue);
                    out.addVertex((float) (d[i] - cam.x), (float) (d[i + 1] - cam.y), (float) (d[i + 2] - cam.z), argb, d[i + 3], d[i + 4],
                            OverlayTexture.NO_OVERLAY, LightTexture.FULL_BRIGHT, 0f, 1f, 0f);
                }
            }
        }

        private static int channel(float x) {
            return Mth.clamp((int) (x * 255f), 0, 255);
        }
    }

    private static final List<Ghost> GHOSTS = new ArrayList<>();
    private static final CaptureSource CAPTURE = new CaptureSource();
    private static final Map<RenderType, ResourceLocation> TEXTURES = new ConcurrentHashMap<>();
    private static final ResourceLocation NONE = ResourceLocation.withDefaultNamespace("none");
    private static boolean reportedError;

    private GhostBodies() {
    }

    /** Hooks the ghosts into the level's drawing (the recording after the entities) and the name tags. */
    public static void register(IEventBus gameBus) {
        gameBus.addListener(RenderLevelStageEvent.class, GhostBodies::onRenderStage);
        gameBus.addListener(RenderNameTagEvent.class, GhostBodies::onNameTag);
    }

    /**
     * A ghost of {@code owner} standing at {@code feet} facing {@code yaw}, lasting {@code life} ticks, in
     * {@code style}. Null if the owner can't be copied here.
     */
    public static @Nullable Ghost spawn(AbstractClientPlayer owner, Vec3 feet, float yaw, Style style, int life) {
        if (!(owner.level() instanceof ClientLevel level)) {
            return null;
        }
        GhostBody body = new GhostBody(level, owner);
        body.place(feet, yaw);
        Ghost ghost = new Ghost(body, style, life);
        GHOSTS.add(ghost);
        WorldFx.add(ghost);
        return ghost;
    }

    /** While a ghost is being recorded: the buffers its whole body goes to (GeckoLib's armor included). */
    public static MultiBufferSource.BufferSource captureSource() {
        return CAPTURE.recording() ? CAPTURE : null;
    }

    /** True while a ghost is being recorded (a second draw of a player that must leave no trace). */
    public static boolean capturing() {
        return CAPTURE.recording();
    }

    /** From the level's drawing, after the entities: every live ghost is posed and recorded for this frame. */
    static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || GHOSTS.isEmpty()) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double now = FxClock.now(partialTick);
        GHOSTS.removeIf(ghost -> ghost.over(now));
        for (Ghost ghost : GHOSTS) {
            try {
                ghost.capture(event.getCamera(), partialTick);
            } catch (RuntimeException e) {
                ghost.quads.clear();
                if (!reportedError) {
                    reportedError = true;
                    LOGGER.error("[cosmicbreach] could not draw a visual effect", e);
                }
            }
        }
    }

    /** No name tag over a ghost. */
    static void onNameTag(RenderNameTagEvent event) {
        if (capturing() || event.getEntity() instanceof GhostBody) {
            event.setCanRender(TriState.FALSE);
        }
    }

    /** Ghosts alive now (tests and scenarios). */
    public static int count() {
        return GHOSTS.size();
    }

    public static List<Ghost> all() {
        return List.copyOf(GHOSTS);
    }

    static void clear() {
        GHOSTS.clear();
    }

    /** The texture a render type draws with, or null for one without (a glint, lines). */
    static @Nullable ResourceLocation textureOf(RenderType type) {
        ResourceLocation texture = TEXTURES.computeIfAbsent(type, t -> {
            if ((Object) t instanceof RenderTypeTextureAccessor composite) {
                RenderStateShard.EmptyTextureStateShard shard = ((CompositeStateTextureAccessor) (Object) composite.cosmicbreach$state())
                        .cosmicbreach$textureState();
                return ((TextureShardAccessor) shard).cosmicbreach$cutoutTexture().orElse(NONE);
            }
            return NONE;
        });
        return texture == NONE || type.toString().contains("glint") ? null : texture;
    }

    /** Where a ghost's body goes while it is recorded: each render type's quads, kept by the texture it draws with. */
    static final class CaptureSource extends MultiBufferSource.BufferSource {
        private @Nullable Ghost ghost;
        private Vec3 camera = Vec3.ZERO;

        CaptureSource() {
            super(new ByteBufferBuilder(256), new LinkedHashMap<>());
        }

        void begin(Ghost ghost, Vec3 camera) {
            this.ghost = ghost;
            this.camera = camera;
        }

        void end() {
            this.ghost = null;
        }

        boolean recording() {
            return ghost != null;
        }

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            ResourceLocation texture = ghost == null ? null : textureOf(type);
            if (texture == null) {
                return Recorder.DISCARD;
            }
            Recorder recorder = ghost.quads.computeIfAbsent(texture, t -> new Recorder());
            recorder.camera = camera;
            return recorder;
        }

        @Override
        public void endLastBatch() {
        }

        @Override
        public void endBatch() {
        }

        @Override
        public void endBatch(RenderType type) {
        }
    }

    /** Takes in drawn vertices: their positions (camera-relative in, world out) and texture coordinates. */
    static class Recorder implements VertexConsumer {
        static final Recorder DISCARD = new Recorder() {
            @Override
            public VertexConsumer addVertex(float x, float y, float z) {
                return this;
            }
        };

        Vec3 camera = Vec3.ZERO;
        float[] data = new float[1024];
        int vertices;

        void reset(Vec3 cam) {
            camera = cam;
            vertices = 0;
        }

        /** The middle of quad {@code q}, in the world. */
        Vec3 centre(int q) {
            int i = q * 4 * STRIDE;
            double x = 0;
            double y = 0;
            double z = 0;
            for (int v = 0; v < 4; v++) {
                x += data[i + v * STRIDE];
                y += data[i + v * STRIDE + 1];
                z += data[i + v * STRIDE + 2];
            }
            return new Vec3(x / 4, y / 4, z / 4);
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            if (vertices < MAX_QUADS * 4) {
                int i = vertices * STRIDE;
                if (i + STRIDE > data.length) {
                    float[] bigger = new float[Math.min(MAX_QUADS * 4 * STRIDE, data.length * 2)];
                    System.arraycopy(data, 0, bigger, 0, data.length);
                    data = bigger;
                }
                data[i] = (float) (x + camera.x);
                data[i + 1] = (float) (y + camera.y);
                data[i + 2] = (float) (z + camera.z);
                data[i + 3] = 0f;
                data[i + 4] = 0f;
                vertices++;
            }
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            if (vertices > 0 && vertices <= MAX_QUADS * 4) {
                int i = (vertices - 1) * STRIDE;
                data[i + 3] = u;
                data[i + 4] = v;
            }
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    }
}
