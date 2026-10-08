package com.cosmicbreach.mount;

import com.cosmicbreach.world.Layer;
import com.cosmicbreach.world.ShearBand;
import net.minecraft.world.phys.Vec3;

/**
 * Keeping a tamed mount (1.1 design section 4), pure. A mount remembers where it last was safe (a stag standing on its
 * islands, a stingray in the Drift's air); left alone below its layer's floor it climbs straight back up the way it fell
 * and crosses to that spot. It shrugs off its owner's hits and the weather's. Just tamed, it comes to wait beside its
 * owner, within reach.
 */
public final class MountCareRules {
    /** How often, in ticks, a mount in a safe place remembers it. */
    public static final int SAFE_EVERY = 20;
    /** How fast a rescue climbs and crosses, blocks a tick. */
    public static final double RESCUE_SPEED = 0.35;
    /** A rescue that gains no ground for this many ticks ends by setting the mount down at its safe spot. */
    public static final int STUCK_TICKS = 60;
    /** Within this many blocks of its safe spot a rescue is over. */
    public static final double HOME = 1.0;
    /** How long after taming a mount keeps coming to its owner, in goal updates (a running goal updates every second tick: about 20 s). */
    public static final int COME_TICKS = 200;
    /** How far from its owner (to its middle) it waits. */
    public static final double COME_DISTANCE = 2.2;
    /** How many directions round its owner it tries for a place to wait. */
    public static final int COME_DIRECTIONS = 8;
    /** A rescue crosses over this many blocks above the remembered spot's height, clearing a rock's edge instead of hitting its side. */
    public static final double CLEARANCE = 1.0;
    /** A mount falls back on an owner no farther than this (a mount only ticks near a player: a farther owner is another place). */
    public static final double OWNER_RANGE = 96.0;
    /** How far beside its owner a mount rescued to them is set down, blocks. */
    public static final double OWNER_SIDE = 2.0;

    private MountCareRules() {
    }

    /** Below this height a tamed mount of {@code kind} has left its layer. */
    public static double safeFloor(MountGear.Kind kind) {
        return kind == MountGear.Kind.MANTA ? Layer.DRIFT.bandMinY : ShearBand.A.maxY;
    }

    /**
     * True on the ticks a mount refreshes its safe spot: one in {@value #SAFE_EVERY}, counted from {@code tickCount} plus its
     * entity id so it always lands on a tick the mount really takes. Far from every player a mount ticks only when
     * {@code tickCount + id} divides by 2 or 5 ({@code AiLod}), so a plain {@code tickCount % 20} would never come up for
     * most ids.
     */
    public static boolean rememberNow(int tickCount, int id) {
        return Math.floorMod(tickCount + id, SAFE_EVERY) == 0;
    }

    /** True if a mount here may be left: a stag on the ground above its gap, a stingray in the Drift's air clear of the gap below. */
    public static boolean safeHere(MountGear.Kind kind, double y, boolean onGround, boolean inDrift) {
        return kind == MountGear.Kind.MANTA ? inDrift && y >= Layer.DRIFT.bandMinY + 6 : onGround && y >= ShearBand.A.maxY;
    }

    /** True if a mount should climb back now. */
    public static boolean needsRescue(boolean tamed, boolean ridden, boolean inAetheria, MountGear.Kind kind, double y) {
        return tamed && !ridden && inAetheria && y < safeFloor(kind);
    }

    /** The rescue's velocity: straight up to the safe spot's height first (back the way it fell), then across to it. */
    public static Vec3 rescueVelocity(Vec3 pos, Vec3 safe) {
        return rescueVelocity(pos, safe, 0.0);
    }

    /**
     * The rescue's velocity with the crossing {@code clearance} blocks higher: straight up until half a block under that
     * height over the spot, then across (sinking toward it). A spot on a rock's top is then reached over the rock's edge
     * and not through its side.
     */
    public static Vec3 rescueVelocity(Vec3 pos, Vec3 safe, double clearance) {
        if (pos.y < safe.y + clearance - 0.5) {
            return new Vec3(0, RESCUE_SPEED, 0);
        }
        Vec3 d = safe.subtract(pos);
        double len = d.length();
        return len < 1e-6 ? Vec3.ZERO : d.scale(Math.min(RESCUE_SPEED, len) / len);
    }

    /**
     * True if a mount of {@code kind} that never recorded a safe spot (one from a world saved before 1.1) may take its
     * owner's side as one: the owner stands where such a mount is safe ({@link #safeHere}) and is near enough
     * ({@link #OWNER_RANGE}, horizontally).
     */
    public static boolean ownerSpotWorks(MountGear.Kind kind, double ownerY, boolean ownerOnGround, boolean ownerInDrift, double ownerDistance) {
        return ownerDistance <= OWNER_RANGE && safeHere(kind, ownerY, ownerOnGround, ownerInDrift);
    }

    /**
     * Where a mount rescued to its owner is set down: {@link #OWNER_SIDE} blocks from them toward the mount (the side it
     * comes from; along +x if it is straight below), a little up, so it lands beside them and not in them.
     */
    public static Vec3 ownerSpot(Vec3 owner, Vec3 mount) {
        double dx = mount.x - owner.x;
        double dz = mount.z - owner.z;
        double len = Math.hypot(dx, dz);
        if (len < 1e-6) {
            dx = 1.0;
            dz = 0.0;
            len = 1.0;
        }
        return new Vec3(owner.x + dx / len * OWNER_SIDE, owner.y + 0.6, owner.z + dz / len * OWNER_SIDE);
    }

    /**
     * The {@code attempt}th heading, in radians, round its owner that a freshly tamed stingray tries for a place to wait:
     * first {@code base} (the way it already lies from them), then 45 degrees to one side and to the other, and so on out
     * to the far side.
     */
    public static double comeAngle(double base, int attempt) {
        return base + (attempt % 2 == 0 ? 1 : -1) * ((attempt + 1) / 2) * Math.PI / 4.0;
    }

    /**
     * True if a hit on a tamed mount does nothing: its owner's (even ridden), the weather's, and a player's hit that it is
     * sheltered from: the server's PvP setting and teams decide ({@code TargetShield}, as for the mod's own weapons), so
     * where PvP is off a friend's sword, sweep or arrow cannot kill it by accident, parked or ridden, and where it is on
     * another player's hit is fair game.
     */
    public static boolean shrugsOff(boolean fromOwner, boolean shelteredFromPlayer, boolean weather) {
        return fromOwner || shelteredFromPlayer || weather;
    }
}
