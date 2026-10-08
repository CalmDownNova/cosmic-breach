package com.cosmicbreach.entity.stalker;

import java.util.List;
import java.util.Optional;
import net.minecraft.world.phys.Vec3;

/**
 * Every number and rule of the Hollow Stalker (GDD 7.1), pure so the tests pin them down.
 *
 * <ul>
 *   <li><b>Light.</b> What the Stalker calls lit is the brighter of block light and sky light (the time of day
 *       taken off; in the Deep only {@value #DEEP_SKY_SHARE} of it, since the layers above shade it, by the rule the
 *       client also draws sky light with, {@link com.cosmicbreach.world.light.DeepLight}): 8 or more is
 *       lit ground, which its pathfinding prices at eight times normal ({@link #pathMalus}). Block light of 12 or more
 *       it never enters, and it leaves at once when it finds itself in it (a torch, an ability's light).</li>
 *   <li><b>Your back.</b> It checks whether your view is turned more than {@value #BACK_ANGLE} degrees away from it
 *       ({@link #turnedAway}); it attacks from there, Grasps only from there, and steps only when you look away.</li>
 *   <li><b>Caught.</b> Looked at directly (within {@value #CAUGHT_CONE} degrees of your aim) from within
 *       {@value #CAUGHT_RANGE} blocks with nothing between, it freezes for {@value #CAUGHT_TICKS} ticks and its mask
 *       flares; for {@value #CAUGHT_IMMUNE} ticks after that it can't be caught again, and backs off into the dark.</li>
 *   <li><b>Shadow Step.</b> When you aren't looking, it teleports up to {@value #STEP_RANGE} blocks (twice that in an
 *       Eclipse Surge) to a dark spot you aren't looking at, as close as it can get to {@value #STEP_BEHIND} blocks
 *       behind you ({@link #chooseStep}); {@value #STEP_COOLDOWN} ticks between steps.</li>
 *   <li><b>Rend</b>: a {@value #REND_TELL}-tick tell (the mask flares, a rising whisper, the gold glint at tick
 *       {@value #REND_GLINT}), then {@value #REND_DAMAGE} damage and a Rift stack to whoever is in its reach and arc;
 *       parryable on the hit.</li>
 *   <li><b>Grasp</b>: only on a target standing in the dark and facing away ({@link #canGrasp}); a
 *       {@value #GRASP_TELL}-tick whisper behind it, then a hold dealing {@value #GRASP_DAMAGE} every
 *       {@value #GRASP_INTERVAL} ticks for {@value #GRASP_TICKS} ticks through armor, until a dash or a parry
 *       breaks it; once per {@value #GRASP_COOLDOWN} ticks per Stalker.</li>
 * </ul>
 */
public final class StalkerRules {
    public static final double HEALTH = 40.0;
    public static final double ARMOR = 6.0;
    public static final double POISE = 20.0;
    /** Walk speed in the dark. */
    public static final double SPEED = 0.30;
    /** Walk speed on lit ground (it hates light). */
    public static final double LIT_SPEED_SCALE = 0.7;
    public static final float HEIGHT = 2.6f;
    public static final float WIDTH = 0.6f;
    /** The mask's middle over its feet. */
    public static final double MASK_Y = 2.4;
    public static final double FOLLOW_RANGE = 32.0;
    /** Attunement XP a kill (GDD: 180, a Deep trash mob). */
    public static final int XP = 180;

    /** Light at or over this is lit ground. */
    public static final int LIT = 8;
    /** Block light at or over this is never entered. */
    public static final int NEVER_ENTER = 12;
    /** Lit ground costs this many times normal to walk. */
    public static final double LIT_COST = 8.0;
    /** The share of sky light that reaches the Deep's floor under the layers above (the shared rule's constant). */
    public static final double DEEP_SKY_SHARE = com.cosmicbreach.world.light.DeepLight.DEEP_SKY_SHARE;

    public static final double BACK_ANGLE = 100.0;

    public static final double CAUGHT_RANGE = 6.0;
    public static final double CAUGHT_CONE = 15.0;
    public static final int CAUGHT_TICKS = 10;
    public static final int CAUGHT_IMMUNE = 30;

    public static final double STEP_RANGE = 10.0;
    public static final int STEP_COOLDOWN = 100;
    /** It doesn't step when it is already this close; it walks. */
    public static final double STEP_MIN_DISTANCE = 4.0;
    public static final double STEP_BEHIND = 2.5;
    /** Never lands closer than this to the target. */
    public static final double STEP_NEAREST = 1.5;

    public static final int REND_TELL = 20;
    public static final int REND_GLINT = 16;
    public static final int REND_RECOVERY = 14;
    public static final double REND_DAMAGE = 12.0;
    public static final double REND_IMPACT = 12.0;
    public static final double REND_REACH = 3.0;
    /** The Rend's arc in front of it, degrees. */
    public static final double REND_ARC = 140.0;
    /** It starts a Rend from this close. */
    public static final double REND_START = 2.6;

    public static final int GRASP_TELL = 14;
    public static final int GRASP_TICKS = 40;
    public static final int GRASP_INTERVAL = 10;
    public static final double GRASP_DAMAGE = 3.0;
    public static final int GRASP_COOLDOWN = 600;
    public static final double GRASP_REACH = 2.6;
    /** It starts a Grasp from this close. */
    public static final double GRASP_START = 2.4;

    /** Where an ability hits, light 12 stays this long. */
    public static final int ABILITY_LIGHT = 12;
    public static final int ABILITY_LIGHT_TICKS = 40;

    /** Close and watched this long, it attacks from the front after all. */
    public static final int PATIENCE = 60;
    /**
     * A natural spawn attempt that passes the light rules starts a Stalker this often (x1.5 in an Eclipse Surge). Playtest 3
     * halved it (it was 0.5): the Deep is dark nearly everywhere and they are its only natural monster, so they came back as
     * fast as they were killed.
     */
    public static final float SPAWN_CHANCE = 0.25f;
    /** Natural spawns need block light at or under this (lichen and torches keep them off). */
    public static final int SPAWN_BLOCK_LIGHT = 3;
    /** No natural spawn where this many Stalkers already are within {@link #SPAWN_SPACING} blocks (2 before playtest 3). */
    public static final int SPAWN_NEIGHBOURS = 1;
    public static final double SPAWN_SPACING = 32.0;
    /** And none where this many are within {@link #AREA_RADIUS} blocks: a cap for the whole stretch round a player. */
    public static final int AREA_CAP = 3;
    public static final double AREA_RADIUS = 64.0;

    private StalkerRules() {
    }

    // ------------------------------------------------------------------ light

    /**
     * The light the Stalker judges ground by: the brighter of block light and the sky light left after the time of day
     * ({@code skyDarken}, 0 at noon, 11 at midnight), times the share of sky light that reaches the cell
     * ({@link com.cosmicbreach.world.light.DeepLight#skyScale}: 1 above Shear band B, {@value #DEEP_SKY_SHARE} in the
     * Deep), rounded down.
     */
    public static int effectiveLight(int blockLight, int skyLight, int skyDarken, double skyScale) {
        int sky = com.cosmicbreach.world.light.DeepLight.shade(Math.max(0, skyLight - skyDarken), skyScale);
        return Math.max(blockLight, sky);
    }

    /** {@link #effectiveLight(int, int, int, double)} with the Deep's full shade or none. */
    public static int effectiveLight(int blockLight, int skyLight, int skyDarken, boolean deepShade) {
        return effectiveLight(blockLight, skyLight, skyDarken, deepShade ? DEEP_SKY_SHARE : 1.0);
    }

    public static boolean lit(int effectiveLight) {
        return effectiveLight >= LIT;
    }

    public static boolean forbidden(int blockLight) {
        return blockLight >= NEVER_ENTER;
    }

    /** True where it would stand gladly: not lit, not forbidden. */
    public static boolean dark(int effectiveLight, int blockLight) {
        return !lit(effectiveLight) && !forbidden(blockLight);
    }

    /**
     * What a path node costs on top of a step ({@code baseMalus} is the node's ordinary malus): -1 (impassable) in
     * block light of 12 or more; on lit ground enough more that a straight step costs {@value #LIT_COST} times as
     * much; otherwise the ordinary malus.
     */
    public static float pathMalus(int effectiveLight, int blockLight, float baseMalus) {
        if (forbidden(blockLight)) {
            return -1.0f;
        }
        if (baseMalus < 0.0f) {
            return baseMalus;
        }
        return lit(effectiveLight) ? baseMalus + (float) (LIT_COST - 1.0) : baseMalus;
    }

    // ------------------------------------------------------------------ looking

    /** Degrees between the view {@code look} (unit) from {@code eye} and the direction to {@code point}. */
    public static double viewAngle(Vec3 eye, Vec3 look, Vec3 point) {
        Vec3 to = point.subtract(eye);
        double len = to.length();
        if (len < 1e-6) {
            return 0.0;
        }
        double cos = to.dot(look) / (len * Math.max(1e-9, look.length()));
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, cos))));
    }

    /** True if the view is turned more than {@value #BACK_ANGLE} degrees away from {@code point}: its back is turned. */
    public static boolean turnedAway(Vec3 eye, Vec3 look, Vec3 point) {
        return viewAngle(eye, look, point) > BACK_ANGLE;
    }

    /** Caught: seen straight on, near, with nothing between, and not just after the last time. */
    public static boolean caught(double distance, double angle, boolean lineOfSight, boolean immune) {
        return !immune && lineOfSight && distance <= CAUGHT_RANGE && angle <= CAUGHT_CONE;
    }

    // ------------------------------------------------------------------ Shadow Step

    /** A spot it could step to: where it would stand (feet), and the light there. */
    public record StepCandidate(Vec3 at, int light, int blockLight) {
    }

    /** {@code distance} blocks behind a body at {@code feet} looking along {@code look}, on the level. */
    public static Vec3 behind(Vec3 feet, Vec3 look, double distance) {
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        if (flat.lengthSqr() < 1e-9) {
            flat = new Vec3(0, 0, 1);
        }
        return feet.subtract(flat.normalize().scale(distance));
    }

    /**
     * Where a Shadow Step goes, if anywhere: among the candidates, the dark spots within {@code range} of {@code from}
     * that the target isn't looking at (its view turned more than {@value #BACK_ANGLE} degrees from the mask there)
     * and no nearer the target than {@value #STEP_NEAREST}, the one nearest {@value #STEP_BEHIND} blocks behind the
     * target; a tie goes to the nearer to {@code from}.
     */
    public static Optional<Vec3> chooseStep(List<StepCandidate> candidates, Vec3 from, Vec3 targetFeet, Vec3 targetEye,
                                            Vec3 targetLook, double range) {
        Vec3 ideal = behind(targetFeet, targetLook, STEP_BEHIND);
        Vec3 best = null;
        double bestScore = Double.MAX_VALUE;
        double bestFrom = Double.MAX_VALUE;
        for (StepCandidate c : candidates) {
            if (!dark(c.light(), c.blockLight()) || c.at().distanceTo(from) > range) {
                continue;
            }
            if (horizontal(c.at(), targetFeet) < STEP_NEAREST) {
                continue;
            }
            if (!turnedAway(targetEye, targetLook, c.at().add(0, MASK_Y, 0))) {
                continue;
            }
            double score = c.at().distanceTo(ideal);
            double d = c.at().distanceTo(from);
            if (score < bestScore - 1e-9 || (Math.abs(score - bestScore) <= 1e-9 && d < bestFrom)) {
                best = c.at();
                bestScore = score;
                bestFrom = d;
            }
        }
        return Optional.ofNullable(best);
    }

    /** True if it would step now: off cooldown, the target not looking at it, and not already close. */
    public static boolean wantsStep(int cooldownLeft, boolean targetTurnedAway, double distance) {
        return cooldownLeft <= 0 && targetTurnedAway && distance > STEP_MIN_DISTANCE;
    }

    /** The farthest a step reaches: {@value #STEP_RANGE}, times the Eclipse Surge's multiplier. */
    public static double stepRange(double surgeMultiplier) {
        return STEP_RANGE * Math.max(1.0, surgeMultiplier);
    }

    // ------------------------------------------------------------------ attacks

    /** True if the Rend connects: within reach and inside the arc in front of it. */
    public static boolean rendHits(double distance, double angleFromFacing) {
        return distance <= REND_REACH && Math.abs(angleFromFacing) <= REND_ARC / 2.0;
    }

    /** True if a Grasp may start: the target stands in the dark, facing away, near, and the Stalker's Grasp is ready. */
    public static boolean canGrasp(boolean targetInDark, boolean targetTurnedAway, int cooldownLeft, double distance) {
        return targetInDark && targetTurnedAway && cooldownLeft <= 0 && distance <= GRASP_START;
    }

    /** True if the hold deals its damage after {@code heldTicks} ticks of it: every 10th, up to the 40th. */
    public static boolean graspHitsAt(int heldTicks) {
        return heldTicks > 0 && heldTicks <= GRASP_TICKS && heldTicks % GRASP_INTERVAL == 0;
    }

    /** True while the Rend's parry cue shows: from the glint to the hit. */
    public static boolean rendGlinting(int tellTick) {
        return tellTick >= REND_GLINT && tellTick <= REND_TELL;
    }

    /**
     * Which attack it starts when close: a Grasp if it may ({@link #canGrasp}); else a Rend, from behind, or from the
     * front once it has waited {@value #PATIENCE} ticks close by; null to keep circling.
     */
    public static Attack pickAttack(boolean targetInDark, boolean targetTurnedAway, int graspCooldown, double distance,
                                    int waitedClose) {
        if (canGrasp(targetInDark, targetTurnedAway, graspCooldown, distance)) {
            return Attack.GRASP;
        }
        if (distance <= REND_START && (targetTurnedAway || waitedClose >= PATIENCE)) {
            return Attack.REND;
        }
        return null;
    }

    public enum Attack { REND, GRASP }

    /** The light and luck part of the natural spawn rule: dark ground, little block light, one attempt in two (x surge). */
    /**
     * True if a natural spawn may not go where {@code near} Stalkers are within {@value #SPAWN_SPACING} blocks and
     * {@code area} within {@value #AREA_RADIUS} (an ambusher, not a swarm).
     */
    public static boolean crowded(int near, int area) {
        return near >= SPAWN_NEIGHBOURS || area >= AREA_CAP;
    }

    public static boolean spawnRule(boolean ground, int light, int blockLight, float roll, double surge) {
        return ground && dark(light, blockLight) && blockLight <= SPAWN_BLOCK_LIGHT && roll < SPAWN_CHANCE * surge;
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
