package com.cosmicbreach.accessory;

/**
 * Every number of the ten accessories (GDD 5.2), pure so each one is unit-tested. Times are ticks (20 a second).
 * <ul>
 *   <li><b>Twin Comet Band</b> (ring): one dash each time in the air that costs no charge, and air dashes fly level, so
 *       two chain into a double dash without landing.</li>
 *   <li><b>Leechstar Signet</b> (ring): a parry heals 2 plus 25% of the damage it stopped.</li>
 *   <li><b>Perihelion Loop</b> (ring): +8% crit chance; crits during a dash or within 40 ticks after it add +0.25 to
 *       the crit multiplier (GDD 3.4's gear term; still capped at 2.5).</li>
 *   <li><b>Gravity Loop</b> (ring): plunges grow past their cap; a plunge from 10 or more blocks opens a Gravity Well
 *       of radius 4 for 20 ticks where it lands.</li>
 *   <li><b>Heart of a Dying Star</b> (necklace): +4 max health; a hit that leaves the wearer under 30% health releases a
 *       nova (radius 5, 8 damage, knockback) and grants Resistance II for 60 ticks, then rests 90 s.</li>
 *   <li><b>Choir Pendant</b> (necklace): +20 max Resonance; ability hits give +3 Resonance for each enemy hit, at most
 *       15 a cast.</li>
 *   <li><b>Halo of Nine</b> (charm): three shards orbit at radius 1.6, one turn a second; each blocks one projectile and
 *       regrows 120 ticks after it broke; at Arcane 20 or more a shard cuts what it passes through (3 damage, then it
 *       rests 10 ticks).</li>
 *   <li><b>Event Horizon Lens</b> (charm): the parry window is 2 ticks longer; a parry in its first 2 ticks drops a black
 *       hole on the attacker (pull radius 4, 20 ticks).</li>
 *   <li><b>Hourglass of Vesper</b> (charm): a perfect dodge slows every enemy within 6 blocks by 70% for 20 ticks.</li>
 *   <li><b>Sunshard Compass</b> (charm): points to the nearest vault the wearer hasn't opened; trap tells show within 8
 *       blocks at any speed.</li>
 * </ul>
 */
public final class AccessoryRules {
    // ------------------------------------------------------------------ Twin Comet Band
    public static final int COMET_AIR_DASHES = 1;

    // ------------------------------------------------------------------ Leechstar Signet
    public static final double LEECH_FLAT = 2.0;
    public static final double LEECH_SHARE = 0.25;

    // ------------------------------------------------------------------ Perihelion Loop
    public static final double PERIHELION_CRIT_CHANCE = 0.08;
    public static final double PERIHELION_CRIT_DAMAGE = 0.25;
    public static final int PERIHELION_WINDOW = 40;

    // ------------------------------------------------------------------ Gravity Loop
    public static final double LOOP_WELL_FALL = 10.0;
    public static final double LOOP_WELL_RADIUS = 4.0;
    public static final int LOOP_WELL_TICKS = 20;

    // ------------------------------------------------------------------ Heart of a Dying Star
    public static final double HEART_HEALTH = 4.0;
    public static final double HEART_THRESHOLD = 0.30;
    public static final double NOVA_RADIUS = 5.0;
    public static final float NOVA_DAMAGE = 8.0f;
    /** The nova's push: the engine's knockback for this much Impact (0.4 + 0.02 x 30 = 1.0). */
    public static final double NOVA_IMPACT = 30.0;
    public static final int NOVA_RESISTANCE_TICKS = 60;
    /** Resistance II: amplifier 1. */
    public static final int NOVA_RESISTANCE_AMPLIFIER = 1;
    public static final int HEART_COOLDOWN = 1800;

    // ------------------------------------------------------------------ Choir Pendant
    public static final int PENDANT_RESONANCE = 20;
    public static final int PENDANT_PER_ENEMY = 3;
    public static final int PENDANT_PER_CAST = 15;

    // ------------------------------------------------------------------ Halo of Nine
    public static final int HALO_SHARDS = 3;
    public static final double HALO_RADIUS = 1.6;
    public static final int HALO_TURN_TICKS = 20;
    public static final int HALO_REGROW_TICKS = 120;
    public static final int HALO_CUT_ARCANE = 20;
    public static final float HALO_CUT_DAMAGE = 3.0f;
    public static final int HALO_CUT_EVERY = 10;
    /** The shards turn at this height over the feet (a little under the chest). */
    public static final double HALO_HEIGHT = 1.1;
    /** A shard cuts a body whose box comes this close to it. */
    public static final double HALO_CUT_REACH = 0.35;

    // ------------------------------------------------------------------ Event Horizon Lens
    public static final int LENS_PARRY_TICKS = 2;
    public static final double HOLE_RADIUS = 4.0;
    public static final int HOLE_TICKS = 20;

    // ------------------------------------------------------------------ the wells' pull (the Gravity Well's)
    /** Blocks a tick a well or black hole pulls (the Comet Maul's Gravity Well pulls the same). */
    public static final double PULL = 0.12;
    /** A pulled body stops this close to the middle (horizontally) instead of jittering across it. */
    public static final double PULL_ARRIVED = 0.6;

    // ------------------------------------------------------------------ Hourglass of Vesper
    public static final double HOURGLASS_RADIUS = 6.0;
    public static final double HOURGLASS_SLOW = 0.70;
    public static final int HOURGLASS_TICKS = 20;

    // ------------------------------------------------------------------ Sunshard Compass
    public static final double COMPASS_TELL_RADIUS = 8.0;
    /** The vaults it knows: loaded ones within this many chunks. */
    public static final int COMPASS_SCAN_CHUNKS = 8;
    public static final int COMPASS_SCAN_EVERY = 20;
    /** Needle frames in its icon, a sixteenth of a turn apart. */
    public static final int NEEDLE_FRAMES = 16;

    private AccessoryRules() {
    }

    /** The Leechstar Signet's heal for a parry that stopped {@code stopped} damage. */
    public static double leechHeal(double stopped) {
        return LEECH_FLAT + LEECH_SHARE * (stopped > 0 ? stopped : 0.0);
    }

    /** True while the Perihelion Loop's crit damage holds: during a dash, or up to 40 ticks after one ended. */
    public static boolean afterDash(boolean dashing, long lastDashEnd, long now) {
        if (dashing) {
            return true;
        }
        long since = now - lastDashEnd;
        return since >= 0 && since <= PERIHELION_WINDOW;
    }

    /** The Perihelion Loop's crit multiplier bonus now: +0.25 during or just after a dash, else nothing. */
    public static double perihelionCritBonus(boolean dashing, long lastDashEnd, long now) {
        return afterDash(dashing, lastDashEnd, now) ? PERIHELION_CRIT_DAMAGE : 0.0;
    }

    /** True if a plunge that fell {@code fallBlocks} opens the Gravity Loop's well. */
    public static boolean opensWell(double fallBlocks) {
        return fallBlocks >= LOOP_WELL_FALL;
    }

    /**
     * True if the Heart's nova goes off: the hit left the wearer alive under 30% of max health, and its rest is over
     * ({@code readyAt} is the game time it may fire again).
     */
    public static boolean novaDue(double health, double maxHealth, long readyAt, long now) {
        return health > 0 && maxHealth > 0 && health < HEART_THRESHOLD * maxHealth && now >= readyAt;
    }

    /** The game time the Heart may fire again after a nova at {@code now}. */
    public static long heartReadyAfter(long now) {
        return now + HEART_COOLDOWN;
    }

    /** The Choir Pendant's Resonance for one more enemy a cast hit, when it has already given {@code given}. */
    public static int pendantGain(int given) {
        return Math.max(0, Math.min(PENDANT_PER_ENEMY, PENDANT_PER_CAST - given));
    }

    /** Shard {@code shard}'s angle round the wearer at {@code time} ticks, in radians (one turn a second, a third apart). */
    public static double shardAngle(int shard, double time) {
        return 2.0 * Math.PI * (time / HALO_TURN_TICKS + shard / (double) HALO_SHARDS);
    }

    /** Where shard {@code shard} is at {@code time}, as x and z from the wearer's middle. */
    public static double[] shardOffset(int shard, double time) {
        double a = shardAngle(shard, time);
        return new double[] {HALO_RADIUS * Math.cos(a), HALO_RADIUS * Math.sin(a)};
    }

    /** True if a shard that regrows at {@code regrowAt} is there at {@code now} (0: it never broke). */
    public static boolean shardAlive(long regrowAt, long now) {
        return regrowAt <= now;
    }

    /** When a shard broken at {@code now} is back. */
    public static long regrowAt(long now) {
        return now + HALO_REGROW_TICKS;
    }

    /** True if the shards cut at this much Arcane. */
    public static boolean shardsCut(int arcane) {
        return arcane >= HALO_CUT_ARCANE;
    }

    /** True if a shard that last cut at {@code lastCut} may cut again at {@code now}. */
    public static boolean cutReady(long lastCut, long now) {
        return now - lastCut >= HALO_CUT_EVERY;
    }

    /**
     * One tick of a well's pull on a body {@code dx}, {@code dz} from its middle (the middle minus the body): the step
     * toward it, at most {@link #PULL}, stopping {@link #PULL_ARRIVED} out. {0, 0} when it has arrived.
     */
    public static double[] pullStep(double dx, double dz, double pull) {
        double flat = Math.sqrt(dx * dx + dz * dz);
        if (!(flat > PULL_ARRIVED) || !(pull > 0)) {
            return new double[] {0.0, 0.0};
        }
        double step = Math.min(pull, flat - PULL_ARRIVED);
        return new double[] {dx / flat * step, dz / flat * step};
    }

    /** A body's walking speed under the Hourglass's slow, as a share of its own: 30%. */
    public static double slowedSpeed(double speed) {
        return speed * (1.0 - HOURGLASS_SLOW);
    }

    /** How far a trap's tell shows: its own radius, or 8 blocks for a Compass wearer. */
    public static double tellRadius(boolean compass, double base) {
        return compass ? Math.max(base, COMPASS_TELL_RADIUS) : base;
    }

    /**
     * The Compass needle for someone at {@code x}, {@code z} facing {@code yawDeg} (Minecraft's yaw: 0 south, 90 west),
     * toward {@code tx}, {@code tz}: the share of a turn, clockwise from straight ahead, in [0, 1).
     */
    public static double needle(double x, double z, float yawDeg, double tx, double tz) {
        double toward = Math.toDegrees(Math.atan2(-(tx - x), tz - z)); // the yaw that faces the target
        double turns = (toward - yawDeg) / 360.0;
        turns -= Math.floor(turns);
        return turns >= 1.0 ? 0.0 : turns;
    }

    /** The icon frame (0 to 15, 0 straight up) for a needle value. */
    public static int needleFrame(double turns) {
        return Math.floorMod((int) Math.round(turns * NEEDLE_FRAMES), NEEDLE_FRAMES);
    }
}
