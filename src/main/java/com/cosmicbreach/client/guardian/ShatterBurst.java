package com.cosmicbreach.client.guardian;

import com.cosmicbreach.client.entity.ShardDraw;
import com.cosmicbreach.client.fx.FxClock;
import com.cosmicbreach.client.fx.FxRenderTypes;
import com.cosmicbreach.client.fx.WorldFx;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The Shatter's burst, so the moment reads even with the sun behind the Colossus: a white flash drawn with ordinary
 * blending under an additive core (light added onto a noon sky vanishes), three rings in red, green and blue racing
 * out over the floor a beat apart, a white shock ring round the body, and a spray of crystal fragments in every colour
 * of the spectrum that fly out, fall, and fade within {@value #LANDED_FADE} ticks of touching the floor (left
 * lying there they outshone the shards). Client only, drawn by {@link WorldFx}.
 */
final class ShatterBurst implements WorldFx.Effect {
    private static final Matrix4f IDENTITY = new Matrix4f();
    /** The whole burst is gone this soon (so the shards that follow stand out), and fragments fade out from here. */
    private static final int LIFE = 34;
    private static final int FADE_FROM = 18;
    /** Ticks a fragment takes to fade once it has touched the floor. */
    private static final int LANDED_FADE = 10;
    private static final int[] SPECTRUM = {0xFF3B3B, 0xFF9A2E, 0xFFE83B, 0x4BE36B, 0x3BD8FF, 0x3B6BFF, 0x9B5BFF, 0xFFFFFF, 0x48DCCF};
    private static final int[] RINGS = {0xFF4A3B, 0x4BE36B, 0x3B7BFF};
    private static final double GRAVITY = 0.045;

    private final Vec3 origin;
    private final double floorY;
    private final double start = FxClock.ticks();
    private final Fragment[] fragments;

    private static final class Fragment {
        Vec3 pos;
        Vec3 prev;
        Vec3 vel;
        final float length;
        final float width;
        final float[] colour;
        final float spin;
        float roll;
        float prevRoll;
        /** The effect's age when it first touched the floor, or -1. */
        double landed = -1;

        Fragment(Vec3 pos, Vec3 vel, float length, float width, int colour, float spin, float roll) {
            this.pos = pos;
            this.prev = pos;
            this.vel = vel;
            this.length = length;
            this.width = width;
            this.colour = ShardDraw.rgb(colour);
            this.spin = spin;
            this.roll = roll;
            this.prevRoll = roll;
        }
    }

    ShatterBurst(Vec3 origin, double floorY, RandomSource random, int count) {
        this.origin = origin;
        this.floorY = floorY;
        this.fragments = new Fragment[count];
        for (int i = 0; i < count; i++) {
            Vec3 dir = new Vec3(random.nextGaussian(), Math.abs(random.nextGaussian()) * 0.6 + 0.2, random.nextGaussian()).normalize();
            double speed = 0.3 + random.nextDouble() * 0.4;
            float length = 0.35f + random.nextFloat() * 0.55f;
            fragments[i] = new Fragment(origin.add(dir.scale(0.8)), dir.scale(speed), length, length * (0.35f + random.nextFloat() * 0.2f),
                    SPECTRUM[i % SPECTRUM.length], (random.nextFloat() - 0.5f) * 0.9f, random.nextFloat() * 6.28f);
        }
    }

    @Override
    public boolean tick() {
        double age = FxClock.ticks() - start;
        for (Fragment f : fragments) {
            f.prev = f.pos;
            f.prevRoll = f.roll;
            f.vel = f.vel.scale(0.975).add(0, -GRAVITY, 0);
            Vec3 next = f.pos.add(f.vel);
            if (next.y < floorY + 0.06 && f.vel.y < 0) {
                next = new Vec3(next.x, floorY + 0.06, next.z);
                f.vel = new Vec3(f.vel.x * 0.45, -f.vel.y * 0.3, f.vel.z * 0.45);
                if (f.landed < 0) {
                    f.landed = age;
                }
            }
            f.pos = next;
            f.roll += f.spin;
        }
        return FxClock.ticks() - start < LIFE;
    }

    @Override
    public void render(WorldFx.Frame frame) {
        double age = frame.now() - start;
        if (age < 0 || age >= LIFE) {
            return;
        }
        Vector3f l = frame.camera().getLeftVector();
        Vector3f u = frame.camera().getUpVector();
        Vec3 left = new Vec3(l.x(), l.y(), l.z());
        Vec3 up = new Vec3(u.x(), u.y(), u.z());
        Vec3 centre = frame.relative(origin);

        // the flash: white that shows on a bright sky, then light added over it
        if (age < 12) {
            double t = age / 12.0;
            float a = (float) Math.pow(1.0 - t, 2.0);
            double size = Math.min(2.5 + 8.0 * Math.sqrt(t), centre.length() * 0.6);
            ShardDraw.quad(frame.buffers().getBuffer(ShardDraw.translucent(ShardDraw.GLOW, true)), IDENTITY, centre, left.scale(size), up.scale(size),
                    1f, 1f, 1f, 0.95f * a);
            ShardDraw.quad(frame.buffers().getBuffer(FxRenderTypes.additive(ShardDraw.GLOW)), IDENTITY, centre, left.scale(size * 0.6),
                    up.scale(size * 0.6), 1f, 1f, 1f, a);
        }
        // three rings over the floor, red, green and blue a beat apart: the light split as by a prism
        Vec3 floor = frame.relative(new Vec3(origin.x, floorY + 0.05, origin.z));
        for (int k = 0; k < RINGS.length; k++) {
            double t = (age - k * 2.0) / 18.0;
            if (t <= 0.0 || t >= 1.0) {
                continue;
            }
            double r = 1.5 + 17.0 * Math.pow(t, 0.6);
            float[] c = ShardDraw.rgb(RINGS[k]);
            float a = (float) Math.pow(1.0 - t, 1.4);
            ShardDraw.quad(frame.buffers().getBuffer(ShardDraw.translucent(ShardDraw.RING, true)), IDENTITY, floor.add(0, k * 0.01, 0),
                    new Vec3(r, 0, 0), new Vec3(0, 0, r), c[0], c[1], c[2], 0.9f * a);
        }
        // a white shock ring round the body, facing the camera
        double ts = age / 14.0;
        if (ts < 1.0) {
            double r = Math.min(1.0 + 9.0 * Math.pow(ts, 0.7), centre.length() * 0.6);
            float a = (float) Math.pow(1.0 - ts, 1.6);
            ShardDraw.quad(frame.buffers().getBuffer(ShardDraw.translucent(ShardDraw.RING, true)), IDENTITY, centre, left.scale(r), up.scale(r),
                    1f, 1f, 1f, 0.85f * a);
        }
        // the fragments: crisp diamonds of colour with a white glint, fading over their last second
        float fade = (float) Math.max(0.0, Math.min(1.0, (LIFE - age) / (LIFE - FADE_FROM)));
        float p = frame.partialTick();
        VertexConsumer solid = frame.buffers().getBuffer(ShardDraw.solid());
        for (Fragment f : fragments) {
            float a = fade * landedFade(f, age);
            if (a <= 0f) {
                continue;
            }
            Vec3 at = frame.relative(f.prev.add(f.pos.subtract(f.prev).scale(p)));
            double roll = f.prevRoll + (f.roll - f.prevRoll) * p;
            Vec3 along = left.scale(Math.cos(roll)).add(up.scale(Math.sin(roll)));
            Vec3 side = up.scale(Math.cos(roll)).subtract(left.scale(Math.sin(roll)));
            diamond(solid, at, along, side, f.length, f.width, f.colour, 0.95f * a);
        }
        VertexConsumer glint = frame.buffers().getBuffer(FxRenderTypes.additive(ShardDraw.GLOW));
        for (Fragment f : fragments) {
            float a = fade * landedFade(f, age);
            if (a <= 0f) {
                continue;
            }
            Vec3 at = frame.relative(f.prev.add(f.pos.subtract(f.prev).scale(p)));
            ShardDraw.quad(glint, IDENTITY, at, left.scale(f.width * 0.9), up.scale(f.width * 0.9), 1f, 1f, 1f, 0.7f * a);
        }
    }

    /** 1 in the air, falling to 0 over {@link #LANDED_FADE} ticks once the fragment has touched the floor. */
    private static float landedFade(Fragment f, double age) {
        return f.landed < 0 ? 1f : (float) Math.max(0.0, 1.0 - (age - f.landed) / LANDED_FADE);
    }

    /** A flat diamond of solid colour: the glow texture's solid middle on every corner. */
    private static void diamond(VertexConsumer out, Vec3 at, Vec3 along, Vec3 side, double length, double width, float[] c, float a) {
        Vec3 l = along.scale(length * 0.5);
        Vec3 w = side.scale(width * 0.5);
        ShardDraw.vertex(out, IDENTITY, at.subtract(l), 0.5f, 0.5f, c[0], c[1], c[2], a);
        ShardDraw.vertex(out, IDENTITY, at.add(w), 0.5f, 0.5f, c[0], c[1], c[2], a);
        ShardDraw.vertex(out, IDENTITY, at.add(l), 0.5f, 0.5f, c[0], c[1], c[2], a);
        ShardDraw.vertex(out, IDENTITY, at.subtract(w), 0.5f, 0.5f, c[0], c[1], c[2], a);
    }
}
