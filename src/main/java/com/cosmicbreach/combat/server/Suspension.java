package com.cosmicbreach.combat.server;

/**
 * Zenith's Suspended state for one launched entity (an attachment, never saved). The entity first
 * rises on its launch velocity; from its apex it hovers for {@code suspendTicks}, sinking slowly with
 * its horizontal speed damped, then gravity takes over again. Landing ends it early.
 *
 * <p>It only ever overrides velocity after the entity's own tick and changes no saved flag (no
 * NoGravity, no NoAI), so an entity whose chunk unloads, or that dies, mid-effect simply falls
 * normally next time it ticks. Pure stepping; the server applies the result.
 */
public final class Suspension {
    /** Ticks the rise may take before the hover starts anyway. */
    public static final int MAX_RISE_TICKS = 20;
    /**
     * Downward speed while hovering, in blocks per tick (0.9 blocks over 30 ticks). Movement uses the
     * velocity from the end of the previous tick, so a plain zero would hold the entity dead still.
     */
    public static final double SINK_PER_TICK = 0.03;
    /** Horizontal velocity kept after each hovering tick. */
    public static final double HORIZONTAL_KEEP = 0.6;

    public enum Phase { RISING, HOVERING, DONE }

    private Phase phase = Phase.RISING;
    private int riseTicks;
    private int hoverLeft;

    public Suspension(int suspendTicks) {
        this.hoverLeft = Math.max(0, suspendTicks);
    }

    /**
     * Called after each of the entity's ticks with its vertical velocity and ground contact. Returns
     * the vertical velocity to force (and damp horizontal speed with {@link #HORIZONTAL_KEEP}), or NaN
     * to leave the entity alone. Once {@link #finished()} the attachment can go.
     */
    public double afterTick(double verticalVelocity, boolean onGround) {
        switch (phase) {
            case RISING -> {
                riseTicks++;
                if (onGround) {
                    // The first call follows a whole tick of moving on the launch velocity, so still
                    // standing (or already back down) means the launch was blocked.
                    phase = Phase.DONE;
                    return Double.NaN;
                }
                if (verticalVelocity > 0 && riseTicks < MAX_RISE_TICKS) {
                    return Double.NaN;
                }
                phase = hoverLeft > 0 ? Phase.HOVERING : Phase.DONE;
                return phase == Phase.HOVERING ? -SINK_PER_TICK : Double.NaN;
            }
            case HOVERING -> {
                if (onGround || --hoverLeft <= 0) {
                    phase = Phase.DONE;
                    return Double.NaN;
                }
                return -SINK_PER_TICK;
            }
            default -> {
                return Double.NaN;
            }
        }
    }

    public Phase phase() {
        return phase;
    }

    public boolean finished() {
        return phase == Phase.DONE;
    }
}
