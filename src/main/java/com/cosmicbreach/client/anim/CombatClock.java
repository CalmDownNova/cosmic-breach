package com.cosmicbreach.client.anim;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractModifier;
import dev.kosmx.playerAnim.core.util.Vec3f;
import org.jetbrains.annotations.NotNull;

/**
 * The combat layer's speed modifier: runs everything inside it (the animation and its cross-fades) on
 * game time, bent by hit-stop ({@link HitStopTime}). It reads game time as a whole
 * ({@link AnimationTime#ticks} plus the frame's partial tick) rather than adding up the deltas it
 * is handed, so an animation started just after its player's tick shows its tick t during the game's
 * tick t, and a new animation never inherits the fraction of a tick the old one was at. (The library's
 * SpeedModifier carries that fraction over, which can put a new animation a tick ahead of its move.)
 */
final class CombatClock extends AbstractModifier {
    private static final double EPSILON = 1e-6;

    private final HitStopTime hitStop = new HitStopTime();
    /** The latest game time taken in, in ticks; NaN before the first. */
    private double seen = Double.NaN;
    /** How far into its current tick the animation is, 0 to 1. */
    private double fraction;
    /** Dev only: time stands still. */
    private boolean frozen;

    /** A new animation starts now: its tick 0, at normal speed, with nothing to make up. */
    void restart() {
        hitStop.reset();
        frozen = false;
        fraction = 0;
        double tick = AnimationTime.ticks();
        seen = Double.isNaN(seen) ? tick : Math.max(seen, tick);
    }

    /** No hold and nothing to make up (what is playing stopped), keeping the time as it is. */
    void clearHold() {
        hitStop.reset();
    }

    /** Hit-stop: hold still for {@code ticks} ticks, then catch up. */
    void hold(int ticks) {
        hitStop.hold(ticks);
    }

    /** Dev only: stop time at {@code fraction} into the animation's current tick (exact key poses). */
    void freeze(double fraction) {
        this.fraction = fraction;
        this.frozen = true;
    }

    boolean isHolding() {
        return hitStop.isHolding();
    }

    double lag() {
        return hitStop.lag();
    }

    double fraction() {
        return fraction;
    }

    @Override
    public void tick() {
        catchUpTo(AnimationTime.ticks());
    }

    @Override
    public void setupAnim(float tickDelta) {
        if (tickDelta < 1f) { // the inventory draws its model at 1.0, a moment the world has not reached
            catchUpTo(AnimationTime.ticks() + tickDelta);
        }
        super.setupAnim((float) fraction);
    }

    @Override
    public @NotNull Vec3f get3DTransform(@NotNull String modelName, @NotNull TransformType type, float tickDelta, @NotNull Vec3f value0) {
        return super.get3DTransform(modelName, type, (float) fraction, value0);
    }

    private void catchUpTo(double now) {
        if (Double.isNaN(seen) || frozen) {
            seen = now;
            return;
        }
        double real = now - seen;
        if (real <= 0) {
            return;
        }
        seen = now;
        fraction += hitStop.advance(real);
        while (fraction >= 1.0 - EPSILON) {
            fraction = Math.max(0.0, fraction - 1.0);
            super.tick();
        }
    }
}
