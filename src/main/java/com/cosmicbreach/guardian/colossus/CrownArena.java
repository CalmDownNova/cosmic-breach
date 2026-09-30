package com.cosmicbreach.guardian.colossus;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The crown arena's geometry, shared by the structure that builds it, the Colossus that fights in it and the
 * client that draws its telegraphs. Pure (no level).
 *
 * <p>The arena's centre is the block corner {@code (x, z)}, where the Colossus stands; {@code floorY} is the
 * first block of air above the floor. The six crown crystals stand at {@link #CRYSTAL_RADIUS}, crystal {@code k}
 * at {@code k * 60} degrees clockwise from east (seen from above; +X east, +Z south), each 2 by 2 blocks round
 * the block corner nearest its ideal spot and {@link #CRYSTAL_HEIGHT} tall. Beams run between crystals at
 * {@link #BEAM_HEIGHT} above the floor: chest height.
 */
public record CrownArena(int x, int floorY, int z) {
    /** The floor's radius, in blocks from the centre. */
    public static final double FLOOR_RADIUS = 18.0;
    /** Inside this horizontal distance a player counts as in the arena (the floor plus the rim wall). */
    public static final double INSIDE_RADIUS = 19.0;
    /** How far above the floor the arena reaches, for "inside". */
    public static final double INSIDE_HEIGHT = 14.0;
    public static final double CRYSTAL_RADIUS = 14.0;
    public static final int CRYSTAL_HEIGHT = 4;
    public static final double BEAM_HEIGHT = 1.1;
    /** The eye facet, above the floor at the centre (plus the facing offset). */
    public static final double EYE_HEIGHT = 6.25;
    public static final double EYE_AHEAD = 1.3;
    /** The chest core, above the floor at the centre (plus the facing offset). */
    public static final double CORE_HEIGHT = 4.7;
    public static final double CORE_AHEAD = 1.1;
    /** Where the core sits while the Colossus is Broken (slumped forward): low and in front. */
    public static final double BROKEN_CORE_HEIGHT = 2.2;
    public static final double BROKEN_CORE_AHEAD = 3.0;

    public static CrownArena at(BlockPos centre) {
        return new CrownArena(centre.getX(), centre.getY(), centre.getZ());
    }

    /** The centre block position (its corner is the arena's centre), at floor level. */
    public BlockPos centreBlock() {
        return new BlockPos(x, floorY, z);
    }

    /** The centre point on the floor. */
    public Vec3 centre() {
        return new Vec3(x, floorY, z);
    }

    /** The ideal angle of crystal {@code k}, radians clockwise from east seen from above. */
    public static double crystalAngle(int k) {
        return Math.toRadians(60.0 * Math.floorMod(k, Refraction.CRYSTALS));
    }

    /** The block corner crystal {@code k} stands round, as {x, z} offsets from the centre. */
    public static int[] crystalOffset(int k) {
        double a = crystalAngle(k);
        return new int[] {(int) Math.round(CRYSTAL_RADIUS * Math.cos(a)), (int) Math.round(CRYSTAL_RADIUS * Math.sin(a))};
    }

    /** The lowest north-west block of crystal {@code k} (its controller). */
    public BlockPos crystalBase(int k) {
        int[] o = crystalOffset(k);
        return new BlockPos(x + o[0] - 1, floorY, z + o[1] - 1);
    }

    /** The point the beam strikes on crystal {@code k}: its axis at {@link #BEAM_HEIGHT}. */
    public Vec3 crystalPoint(int k) {
        int[] o = crystalOffset(k);
        return new Vec3(x + o[0], floorY + BEAM_HEIGHT, z + o[1]);
    }

    /** Crystal {@code k}'s blocks as a box. */
    public AABB crystalBox(int k) {
        int[] o = crystalOffset(k);
        return new AABB(x + o[0] - 1, floorY, z + o[1] - 1, x + o[0] + 1, floorY + CRYSTAL_HEIGHT, z + o[1] + 1);
    }

    /** The crystal whose blocks hold {@code pos}, or -1. */
    public int crystalAt(BlockPos pos) {
        for (int k = 0; k < Refraction.CRYSTALS; k++) {
            BlockPos base = crystalBase(k);
            int dx = pos.getX() - base.getX();
            int dz = pos.getZ() - base.getZ();
            int dy = pos.getY() - base.getY();
            if (dx >= 0 && dx < 2 && dz >= 0 && dz < 2 && dy >= 0 && dy < CRYSTAL_HEIGHT) {
                return k;
            }
        }
        return -1;
    }

    /** The eye facet for a Colossus facing {@code yawDeg} (Minecraft yaw: 0 south, 90 west). */
    public Vec3 eye(float yawDeg) {
        Vec3 f = forward(yawDeg);
        return new Vec3(x + f.x * EYE_AHEAD, floorY + EYE_HEIGHT, z + f.z * EYE_AHEAD);
    }

    /** The chest core for a Colossus facing {@code yawDeg}, standing or slumped in a Break. */
    public Vec3 core(float yawDeg, boolean broken) {
        Vec3 f = forward(yawDeg);
        double ahead = broken ? BROKEN_CORE_AHEAD : CORE_AHEAD;
        return new Vec3(x + f.x * ahead, floorY + (broken ? BROKEN_CORE_HEIGHT : CORE_HEIGHT), z + f.z * ahead);
    }

    /** The point of a beam node: a crystal, or the core. */
    public Vec3 node(int node, float yawDeg, boolean broken) {
        return node == Refraction.CORE ? core(yawDeg, broken) : crystalPoint(node);
    }

    /** Horizontal distance from the centre. */
    public double distance(double px, double pz) {
        return Math.hypot(px - x, pz - z);
    }

    /** True if a point is in the arena: over the floor (or its rim wall) and not far above it. */
    public boolean contains(double px, double py, double pz) {
        return distance(px, pz) <= INSIDE_RADIUS && py >= floorY - 2.0 && py <= floorY + INSIDE_HEIGHT;
    }

    public boolean contains(Vec3 p) {
        return contains(p.x, p.y, p.z);
    }

    /** The whole arena as a box (for entity searches). */
    public AABB bounds() {
        return new AABB(x - INSIDE_RADIUS, floorY - 2.0, z - INSIDE_RADIUS, x + INSIDE_RADIUS, floorY + INSIDE_HEIGHT, z + INSIDE_RADIUS);
    }

    /** The unit vector a Minecraft yaw faces, flat. */
    public static Vec3 forward(float yawDeg) {
        double r = Math.toRadians(yawDeg);
        return new Vec3(-Math.sin(r), 0.0, Math.cos(r));
    }

    /** The Minecraft yaw that faces from {@code (fx, fz)} toward {@code (tx, tz)}. */
    public static float yawToward(double fx, double fz, double tx, double tz) {
        return (float) (Math.toDegrees(Math.atan2(tz - fz, tx - fx)) - 90.0);
    }
}
