package com.cosmicbreach.combat.server;

import com.cosmicbreach.combat.core.MoveInstance;
import com.cosmicbreach.combat.data.HitShape;
import com.cosmicbreach.combat.data.WeaponDef;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * A charged move's ground wave (Meridian Line): it leaves the attacker's feet after the move's first
 * active tick and travels {@code length} blocks along the attacker's yaw in {@link #STEPS} ticks. Each
 * tick it sweeps the next slice of its path, a line segment {@code length / STEPS} long, so nothing
 * between two slices is skipped; each target is hit at most once per wave.
 *
 * <p>The stepping and the once-per-target rule are pure; the server resolves the hits.
 */
public final class GroundWave {
    public static final int STEPS = 4;
    /** Used when the move's own hitbox is not a line. */
    public static final double DEFAULT_WIDTH = 1.2;
    /** The wave hits a band this tall, centred half a block above the attacker's feet. */
    public static final double BAND_HEIGHT = 2.0;
    public static final double BAND_CENTRE = 0.5;

    /** One tick of travel: the segment's start point and its shape (a line along the wave's yaw). */
    public record Step(int index, Vec3 origin, HitShape.Line shape, double from, double to) {
    }

    private final Vec3 feet;
    private final float yaw;
    private final double length;
    private final double width;
    private final long createdTick;
    private final Set<Integer> hit = new HashSet<>();
    private int stepsDone;

    private final @Nullable MoveInstance move;
    private final @Nullable WeaponDef weapon;
    private final double mv;

    public GroundWave(Vec3 feet, float yaw, double length, double width, long createdTick,
                      @Nullable MoveInstance move, @Nullable WeaponDef weapon, double mv) {
        this.feet = feet;
        this.yaw = yaw;
        this.length = Math.max(0, length);
        this.width = width > 0 ? width : DEFAULT_WIDTH;
        this.createdTick = createdTick;
        this.move = move;
        this.weapon = weapon;
        this.mv = mv;
    }

    /** Where step {@code index} (1 to {@link #STEPS}) starts, in blocks ahead of the feet. */
    public static double segmentFrom(double length, int index) {
        return length * (index - 1) / STEPS;
    }

    /** Where step {@code index} (1 to {@link #STEPS}) ends, in blocks ahead of the feet. */
    public static double segmentTo(double length, int index) {
        return length * index / STEPS;
    }

    /** The wave moves on the ticks after the one it was made in. */
    public boolean dueAt(long tick) {
        return !finished() && tick > createdTick;
    }

    /** Takes the next step of travel, or returns null when the wave has run its length. */
    public @Nullable Step advance() {
        if (finished()) {
            return null;
        }
        int index = ++stepsDone;
        double from = segmentFrom(length, index);
        double to = segmentTo(length, index);
        Vec3 origin = feet.add(HitShape.forward(yaw).scale(from)).add(0, BAND_CENTRE, 0);
        return new Step(index, origin, new HitShape.Line(to - from, width, BAND_HEIGHT), from, to);
    }

    /** True the first time a target is hit by this wave, false every time after. */
    public boolean markHit(int targetId) {
        return hit.add(targetId);
    }

    public boolean finished() {
        return stepsDone >= STEPS;
    }

    public float yaw() {
        return yaw;
    }

    public Vec3 feet() {
        return feet;
    }

    public double width() {
        return width;
    }

    public @Nullable MoveInstance move() {
        return move;
    }

    public @Nullable WeaponDef weapon() {
        return weapon;
    }

    public double mv() {
        return mv;
    }
}
