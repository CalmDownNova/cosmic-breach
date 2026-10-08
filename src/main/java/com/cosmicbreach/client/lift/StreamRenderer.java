package com.cosmicbreach.client.lift;

import com.cosmicbreach.client.entity.ShardDraw;
import com.cosmicbreach.client.fx.FxRenderTypes;
import com.cosmicbreach.client.guardian.TelegraphDraw;
import com.cosmicbreach.world.AetheriaWorld;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Rising streams of air (1.1 design sections 6 and 5): the Rifts' air vents and the currents under the shrines, drawn from
 * far off so they can be found by sight, in daylight and at dusk. Each is a soft dark rim (it reads on the Drift's pale
 * sky), a cyan body, an additive halo and a white-hot core, then bright streaks climbing it about 7 blocks a second, a
 * plume that goes on up past its top and fades out, and a glowing beacon where it ends. Every layer keeps a width in
 * pixels from any distance ({@link StreamLook}), so a vent across the arena is a broad column and not a thin line, and
 * up close it is no wider than a few blocks. A piece of a stream within a block of the eye is left out and fades in over
 * the next five, so riding up inside one never fills the screen. The same look, narrowing and fading, trails behind every
 * player the rescue lift carries ({@link RiderTrails}), so a rescue is seen across the arena.
 */
public final class StreamRenderer {
    /**
     * A vertical stream: its column's middle, bottom and top, width in blocks and colour, and how far a plume goes on up past
     * its top, fading out (0: none).
     */
    public record Stream(double x, double z, double bottom, double top, double width, int color, double rise) {
        /** A stream with no plume: the currents under the shrines (task B6) are cut off at their top. */
        public Stream(double x, double z, double bottom, double top, double width, int color) {
            this(x, z, bottom, top, width, color, 0.0);
        }
    }

    public static final double RANGE = 192.0;
    private static final double PIECE = 6.0;
    private static final double STREAK_EVERY = 5.0;
    private static final double STREAK_LENGTH = 2.4;
    private static final double CLIMB = 0.35;
    private static final int OUTLINE = 0x0A1B2C;
    private static final int BODY = 0x38D6F0;
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new ByteBufferBuilder(1 << 16));
    private static int drawn;
    private static int trailsDrawn;

    private StreamRenderer() {
    }

    /** How many streams the last frame drew (tests). */
    public static int drawnLastFrame() {
        return drawn;
    }

    /** How many riders' trails the last frame drew (tests). */
    public static int trailsDrawnLastFrame() {
        return trailsDrawn;
    }

    /** How far the piece {@code a} to {@code b} (camera-relative) passes from the eye, as a fade: 0 within a block, 1 from six blocks. */
    static float nearFade(Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double len2 = ab.lengthSqr();
        double t = len2 < 1e-9 ? 0.0 : Mth.clamp(-a.dot(ab) / len2, 0.0, 1.0);
        return (float) Mth.clamp((a.add(ab.scale(t)).length() - 1.0) / 5.0, 0.0, 1.0);
    }

    private static void vertex(VertexConsumer out, Vec3 p, float[] col, float alpha) {
        ShardDraw.vertex(out, IDENTITY, p, 0.5f, 0.5f, col[0], col[1], col[2], alpha);
    }

    /**
     * A band from {@code a} to {@code b} (camera-relative) turned to face the camera, {@code widthA} wide at a and
     * {@code widthB} at b: full colour across the middle {@code plateau} of its width, fading to nothing at both edges, and
     * {@code alphaA} at a to {@code alphaB} at b along it. Draw into a buffer whose texture is solid in the middle.
     */
    private static void soft(VertexConsumer out, Vec3 a, Vec3 b, double widthA, double widthB, double plateau, float[] col, float alphaA, float alphaB) {
        if (alphaA <= 0.0f && alphaB <= 0.0f) {
            return;
        }
        Vec3 along = b.subtract(a);
        Vec3 side = along.cross(a.add(b).scale(0.5));
        if (along.lengthSqr() < 1e-8 || side.lengthSqr() < 1e-12) {
            return;
        }
        side = side.normalize();
        Vec3 ha = side.scale(widthA / 2.0);
        Vec3 hb = side.scale(widthB / 2.0);
        Vec3 pa = ha.scale(plateau);
        Vec3 pb = hb.scale(plateau);
        // the left fringe, the full middle, the right fringe
        vertex(out, a.subtract(ha), col, 0.0f);
        vertex(out, a.subtract(pa), col, alphaA);
        vertex(out, b.subtract(pb), col, alphaB);
        vertex(out, b.subtract(hb), col, 0.0f);
        vertex(out, a.subtract(pa), col, alphaA);
        vertex(out, a.add(pa), col, alphaA);
        vertex(out, b.add(pb), col, alphaB);
        vertex(out, b.subtract(pb), col, alphaB);
        vertex(out, a.add(pa), col, alphaA);
        vertex(out, a.add(ha), col, 0.0f);
        vertex(out, b.add(hb), col, 0.0f);
        vertex(out, b.add(pb), col, alphaB);
    }

    /**
     * One piece of a stream or a trail, camera-relative: its two ends, their distances from the eye, how strongly each end shows
     * (the near fade, shimmer and plume or age fade together) and how wide each is as a share of the layer's width (a trail narrows).
     */
    private record Piece(Vec3 a, Vec3 b, double da, double db, float strengthA, float strengthB, double taperA, double taperB) {
    }

    /** The pieces of a stream's length (a body and the plume over it), left out where they pass within a block of the eye. */
    private static List<Piece> pieces(Stream s, Vec3 cam, float shimmer) {
        List<Piece> out = new ArrayList<>();
        double length = s.top() + s.rise() - s.bottom();
        int n = Math.max(1, (int) Math.ceil(length / PIECE));
        for (int i = 0; i < n; i++) {
            double y0 = s.bottom() + length * i / n;
            double y1 = s.bottom() + length * (i + 1) / n;
            Vec3 a = new Vec3(s.x(), y0, s.z()).subtract(cam);
            Vec3 b = new Vec3(s.x(), y1, s.z()).subtract(cam);
            float fade = nearFade(a, b) * shimmer;
            if (fade > 0.0f) {
                out.add(new Piece(a, b, a.length(), b.length(), fade * StreamLook.fadeAbove(y0, s.top(), s.rise()), fade * StreamLook.fadeAbove(y1, s.top(), s.rise()),
                        1.0, 1.0));
            }
        }
        return out;
    }

    /** The streaks climbing a stream's body, each fainter at its tail than at its head, all dimmed by {@code far} (the range fade). */
    private static List<Piece> streaks(Stream s, Vec3 cam, double time, float far) {
        List<Piece> out = new ArrayList<>();
        double phase = (time * CLIMB) % STREAK_EVERY;
        for (double y = s.bottom() + phase; y + STREAK_LENGTH <= s.top(); y += STREAK_EVERY) {
            Vec3 a = new Vec3(s.x(), y, s.z()).subtract(cam);
            Vec3 b = new Vec3(s.x(), y + STREAK_LENGTH, s.z()).subtract(cam);
            float fade = nearFade(a, b) * far;
            if (fade > 0.0f) {
                out.add(new Piece(a, b, a.length(), b.length(), 0.2f * fade, fade, 1.0, 1.0));
            }
        }
        return out;
    }

    /**
     * The pieces of a rider's trail, between each pair of its points: fading and narrowing with age, easing in over its first blocks
     * back from the rider's feet (a point under them, not a flat cut across their body), left out within a block of the eye.
     */
    private static List<Piece> pieces(RiderTrails.Trail t, Vec3 cam) {
        List<Piece> out = new ArrayList<>();
        int n = t.points().size();
        double[] back = new double[n]; // how far along the trail each point lies from the rider (the last point)
        for (int i = n - 2; i >= 0; i--) {
            back[i] = back[i + 1] + t.points().get(i + 1).distanceTo(t.points().get(i));
        }
        for (int i = 0; i + 1 < n; i++) {
            Vec3 a = t.points().get(i).subtract(cam);
            Vec3 b = t.points().get(i + 1).subtract(cam);
            double ageA = t.ages().get(i);
            double ageB = t.ages().get(i + 1);
            float near = nearFade(a, b);
            if (near > 0.0f) {
                out.add(new Piece(a, b, a.length(), b.length(), near * StreamLook.trailFade(ageA) * StreamLook.headRamp(back[i]),
                        near * StreamLook.trailFade(ageB) * StreamLook.headRamp(back[i + 1]),
                        StreamLook.trailTaper(ageA) * StreamLook.headWidth(back[i]), StreamLook.trailTaper(ageB) * StreamLook.headWidth(back[i + 1])));
            }
        }
        return out;
    }

    private static void band(VertexConsumer out, Piece p, double pixels, double min, double plateau, float[] col, float alpha) {
        soft(out, p.a(), p.b(), StreamLook.width(p.da(), pixels * p.taperA(), min * p.taperA()), StreamLook.width(p.db(), pixels * p.taperB(), min * p.taperB()),
                plateau, col, Math.min(1.0f, alpha * p.strengthA()), Math.min(1.0f, alpha * p.strengthB()));
    }

    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        drawn = 0;
        trailsDrawn = 0;
        Minecraft mc = Minecraft.getInstance();
        List<Stream> streams = LiftClient.streams();
        if (mc.level == null || !AetheriaWorld.is(mc.level)) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double time = mc.level.getGameTime() + partial;
        List<Stream> seen = new ArrayList<>();
        List<Float> fars = new ArrayList<>();
        List<List<Piece>> bodies = new ArrayList<>();
        List<List<Piece>> climbing = new ArrayList<>();
        for (Stream s : streams) {
            // a stream keeps its width in pixels at any distance, so over the last stretch of its range it fades out instead of popping in
            float far = StreamLook.rangeFade(Math.hypot(s.x() - cam.x, s.z() - cam.z), RANGE);
            if (far > 0.0f) {
                float shimmer = StreamLook.shimmer(time, s.x() * 0.37 + s.z() * 0.21);
                seen.add(s);
                fars.add(far);
                bodies.add(pieces(s, cam, shimmer * far));
                climbing.add(streaks(s, cam, time, far));
            }
        }
        List<List<Piece>> wakes = new ArrayList<>();
        for (RiderTrails.Trail t : LiftClient.trails(partial)) {
            if (t.points().size() >= 2) {
                wakes.add(pieces(t, cam));
            }
        }
        if (seen.isEmpty() && wakes.isEmpty()) {
            return;
        }
        float[] dark = ShardDraw.rgb(OUTLINE);
        float[] body = ShardDraw.rgb(BODY);
        float[] white = ShardDraw.rgb(0xFFFFFF);
        float[] glowColor = ShardDraw.rgb(LiftClient.VENT);
        // each kind of layer in a batch of its own, in this order, so what is drawn over what never depends on how a batch sorts its quads:
        // the dark rim (it reads on a pale sky), the cyan body, then light added on top (a halo, a white-hot core, the climbing streaks)
        VertexConsumer rim = BUFFERS.getBuffer(FxRenderTypes.shade(FxRenderTypes.GLOW));
        for (int i = 0; i < seen.size(); i++) {
            double w = seen.get(i).width();
            for (Piece p : bodies.get(i)) {
                band(rim, p, StreamLook.OUTLINE_PX, w * 2.0, 0.3, dark, StreamLook.OUTLINE_ALPHA);
            }
        }
        for (List<Piece> wake : wakes) {
            for (Piece p : wake) {
                band(rim, p, StreamLook.TRAIL_PX * 1.8, StreamLook.TRAIL_MIN * 1.8, 0.3, dark, StreamLook.TRAIL_RIM_ALPHA);
            }
        }
        VertexConsumer fill = BUFFERS.getBuffer(ShardDraw.solid());
        for (int i = 0; i < seen.size(); i++) {
            double w = seen.get(i).width();
            float[] fillColor = ShardDraw.rgb(StreamLook.bodyColor(seen.get(i).color(), BODY));
            for (Piece p : bodies.get(i)) {
                band(fill, p, StreamLook.BODY_PX, w * 1.3, 0.35, fillColor, StreamLook.BODY_ALPHA);
            }
        }
        for (List<Piece> wake : wakes) {
            for (Piece p : wake) {
                band(fill, p, StreamLook.TRAIL_PX, StreamLook.TRAIL_MIN, 0.4, body, StreamLook.TRAIL_BODY_ALPHA);
            }
        }
        VertexConsumer light = BUFFERS.getBuffer(FxRenderTypes.additive(FxRenderTypes.GLOW));
        for (int i = 0; i < seen.size(); i++) {
            Stream s = seen.get(i);
            double w = s.width();
            float[] glow = ShardDraw.rgb(s.color());
            for (Piece p : bodies.get(i)) {
                band(light, p, StreamLook.GLOW_PX, w * 1.1, 0.25, glow, StreamLook.GLOW_ALPHA);
                band(light, p, StreamLook.CORE_PX, w * 0.4, 0.5, white, StreamLook.CORE_ALPHA);
            }
            for (Piece p : climbing.get(i)) {
                band(light, p, StreamLook.STREAK_PX, w * 0.8, 0.5, glow, StreamLook.STREAK_ALPHA);
                band(light, p, StreamLook.CORE_PX, w * 0.35, 0.5, white, StreamLook.CORE_ALPHA);
            }
        }
        for (List<Piece> wake : wakes) {
            for (Piece p : wake) {
                band(light, p, StreamLook.TRAIL_PX * 0.8, StreamLook.TRAIL_MIN * 0.8, 0.3, glowColor, StreamLook.TRAIL_GLOW_ALPHA);
                band(light, p, StreamLook.TRAIL_PX * 0.3, StreamLook.TRAIL_MIN * 0.3, 0.5, white, StreamLook.TRAIL_CORE_ALPHA);
            }
            trailsDrawn++;
        }
        // a beacon where each stream ends, so it is found by its top as well as its length
        for (int i = 0; i < seen.size(); i++) {
            Stream s = seen.get(i);
            Vec3 top = new Vec3(s.x(), s.top() + 0.5, s.z()).subtract(cam);
            double distance = top.length();
            if (distance > 7.0) {
                TelegraphDraw.glow(BUFFERS, camera, top, (float) (StreamLook.width(distance, StreamLook.BEACON_PX, 1.6) / 2.0), s.color(),
                        0.75f * StreamLook.shimmer(time, s.x() * 0.37 + s.z() * 0.21) * fars.get(i));
            }
            drawn++;
        }
        BUFFERS.endBatch();
        RenderSystem.defaultBlendFunc();
    }
}
