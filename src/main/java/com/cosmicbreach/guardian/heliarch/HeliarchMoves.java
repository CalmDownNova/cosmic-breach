package com.cosmicbreach.guardian.heliarch;

/**
 * Every number of the Hollow Heliarch's fight (GDD 7.3), in one place. Ticks are server ticks (20 a second); damage
 * is before armor, on Normal. The pure rules ({@link HaloShed}, {@link EclipseCover}, {@link NovaRules},
 * {@link CollapseSchedule}, {@link SoftEnrage}, {@link ThreatTable}, {@link HeliarchLoot}, {@link TendrilRules})
 * and the entity read them from here.
 */
public final class HeliarchMoves {
    private HeliarchMoves() {
    }

    // ------------------------------------------------------------------ stats

    /** Health for one player; scaled by {@code GuardianHealth} for the players within {@link #COUNT_RADIUS} at the summon. */
    public static final double BASE_HEALTH = 1200.0;
    public static final double ARMOR = 10.0;
    public static final double TOUGHNESS = 6.0;
    /** Players this close to the throne when the Heart is set count toward its health (capped at four). */
    public static final double COUNT_RADIUS = 48.0;
    /** A hand takes this share of a hit's damage (and all of its Impact). */
    public static final double HAND_DAMAGE = 0.5;

    // ------------------------------------------------------------------ the Break gauge (GDD 3.4 and 7.3)

    /** Poise per phase: the gauge empties at each phase change. */
    public static final double BREAK_POISE = 400.0;
    /** Impact older than this drains out of the gauge (a long window: 400 is a lot of Impact). */
    public static final int GAUGE_WINDOW = 600;
    public static final int BREAK_TICKS = 100;
    /** The core's damage taken while Broken. */
    public static final double BREAK_TAKEN = 1.5;

    // ------------------------------------------------------------------ the fight's shape

    /** The intro: the eclipse, the plates assembling, the bar filling (10 s). */
    public static final int INTRO_TICKS = 200;
    /** Phase 2 starts at this share of health (the Hollowing). */
    public static final double HOLLOW_AT = 0.6;
    /** Nova's channel starts at this share. */
    public static final double NOVA_AT = 0.4;
    /** The Collapse starts at this share. */
    public static final double COLLAPSE_AT = 0.2;
    /** The Hollowing: invulnerable, the plates become monoliths. */
    public static final int HOLLOWING_TICKS = 100;
    /** The plates slam down this far into the Hollowing. */
    public static final int HOLLOWING_SLAM = 50;
    /** The death: the eclipse breaks and the light comes back, then the Echo and the Reliquaries. */
    public static final int DYING_TICKS = 160;
    /** No fighter left in the arena for this long ends the fight (the regent withdraws). */
    public static final int EMPTY_TICKS = 200;

    // ------------------------------------------------------------------ phase 1: the Regent

    /** A pause after each attack, with the halo closed round the core. */
    public static final int GAP_TICKS = 26;
    /** The first attack waits this long after the intro. */
    public static final int OPENING_TICKS = 40;

    public static final double SUNDER_DAMAGE = 24.0;
    public static final int SUNDER_TELL = 24;
    /** The gold glint: the parry cue, four ticks before the slam lands. */
    public static final int SUNDER_GLINT = 20;
    public static final double SUNDER_RADIUS = 4.0;
    public static final int SUNDER_COOLDOWN = 100;
    /** A parry deals twice this to the gauge (60). */
    public static final double SUNDER_IMPACT = 30.0;
    /** The hand lies where it landed this long (longer when parried). */
    public static final int SUNDER_REST = 26;
    public static final int SUNDER_PARRIED_REST = 50;
    public static final int SUNDER_RETURN = 14;

    public static final double SWEEP_DAMAGE = 20.0;
    public static final int SWEEP_TELL = 30;
    public static final double SWEEP_INNER = 10.0;
    public static final double SWEEP_OUTER = 16.0;
    /** The wall of fire goes once round the band in this long. */
    public static final int SWEEP_TICKS = 20;
    /** Feet this high over the floor clear the wall (a jump does). */
    public static final double SWEEP_CLEAR = 0.75;
    public static final int SWEEP_COOLDOWN = 240;
    public static final double SWEEP_IMPACT = 12.0;

    /**
     * Corona Flare, the push-out: once a player has stayed within {@value #FLARE_RADIUS} of the core for
     * {@value #FLARE_HUG} ticks, a white-gold ring fills the floor out to it for {@value #FLARE_TELL} ticks, then the
     * flare burns everyone inside for 12 and throws them outward. Not parryable: a dash's i-frames slip it.
     */
    public static final double FLARE_RADIUS = 6.0;
    public static final int FLARE_HUG = 60;
    public static final int FLARE_TELL = 20;
    public static final double FLARE_DAMAGE = 12.0;
    public static final double FLARE_KNOCKBACK = 2.0;
    public static final double FLARE_IMPACT = 10.0;
    public static final int FLARE_COOLDOWN = 200;
    /** The core burns on this long after the flare before the halo closes. */
    public static final int FLARE_REST = 14;

    public static final double LANCE_DAMAGE = 18.0;
    /** The red line tracks the target... */
    public static final int LANCE_TRACK = 20;
    /** ...then locks and turns white... */
    public static final int LANCE_LOCK = 6;
    /** ...then burns along the locked line. */
    public static final int LANCE_BURN = 6;
    public static final double LANCE_RADIUS = 0.55;
    public static final double LANCE_LENGTH = 40.0;
    /** The target must be at least this far from the core for a lance (mid range). */
    public static final double LANCE_MIN_RANGE = 6.0;
    public static final int LANCE_COOLDOWN = 160;
    public static final double LANCE_IMPACT = 10.0;

    public static final int SHED_EVERY = 600;
    public static final double SHED_DAMAGE = 14.0;
    public static final double SHED_EXPOSED = 1.25;
    public static final double SHED_IMPACT = 8.0;

    // ------------------------------------------------------------------ phase 2: the Hollow

    public static final int BEAM_TELL = 40;
    public static final int BEAM_SWEEP = 60;
    public static final double BEAM_ARC = 180.0;
    /** The beam's wedge, in degrees. */
    public static final double BEAM_WIDTH = 24.0;
    public static final double BEAM_DAMAGE = 10.0;
    /** A player caught in the wedge burns at most this often. */
    public static final int BEAM_EVERY = 10;
    public static final int BEAM_COOLDOWN = 400;
    /** The first beam of phase 2 waits this long. */
    public static final int BEAM_OPENING = 160;

    public static final double LASH_DAMAGE = 16.0;
    public static final int LASH_TELL = 20;
    public static final double LASH_WIDTH = 1.5;
    public static final double LASH_LENGTH = 12.0;
    /** Each tendril lashes this often. */
    public static final int LASH_EVERY = 120;
    /** The whip lies on the floor this long after it lands. */
    public static final int LASH_REST = 12;
    public static final double LASH_IMPACT = 20.0;

    public static final double TENDRIL_HEALTH = 30.0;
    public static final double TENDRIL_RESONANCE = 15.0;
    /**
     * High over the pillars in phase 2 the eclipse bleeds through its tendrils: a blow on one reaches it in full, with
     * its full Impact (at half, a near-perfect fight took five minutes); through Nova's channel a blow on one drains the
     * Corona Shield instead.
     */
    public static final double TENDRIL_SHARE = 1.0;
    public static final int TENDRIL_SILENCE = 200;
    public static final int TENDRIL_COUNT = 4;

    public static final int INVERSION_TICKS = 160;
    public static final double INVERSION_GRAVITY = 0.3;
    public static final double INVERSION_CHANCE = 0.5;
    public static final int SEEDS = 6;
    /** One Star Seed every this many ticks through the inversion. */
    public static final int SEED_EVERY = 22;
    public static final double SEED_DAMAGE = 12.0;
    public static final double SEED_HEALTH = 6.0;
    /** Blocks a tick: slow enough to outrun. */
    public static final double SEED_SPEED = 0.22;
    public static final int SEED_LIFE = 220;

    public static final int NOVA_CHANNEL = 400;
    public static final double SHIELD_BASE = 250.0;
    public static final int NOVA_STUN = 160;
    public static final double NOVA_EXPOSED = 1.5;
    public static final double NOVA_DAMAGE = 60.0;
    /** Behind cover the detonation deals this share (60% less). */
    public static final double NOVA_COVERED = 0.4;
    public static final int NOVA_RETURN = 600;

    // ------------------------------------------------------------------ the Collapse

    public static final int COLLAPSE_EVERY = 300;
    public static final int SHAKE_TICKS = 100;
    public static final int RAIN_EVERY = 100;
    public static final int RAIN_COUNT = 6;
    public static final int RAIN_TELL = 20;
    public static final double RAIN_RADIUS = 2.0;
    public static final double RAIN_DAMAGE = 14.0;
    /** No warning in the fight is ever shorter than this, however late it gets. */
    public static final int MIN_TELL = 12;

    // ------------------------------------------------------------------ soft enrage and threat

    /** Past 8 minutes of fight... */
    public static final int ENRAGE_AFTER = 9600;
    /** ...its damage rises every 30 s... */
    public static final int ENRAGE_STEP = 600;
    /** ...by 10%. */
    public static final double ENRAGE_PER_STEP = 0.10;

    public static final double THREAT_SWITCH = 1.3;
    public static final int THREAT_CHECK = 40;
    /** A Gravikin's taunt counts as this much threat (GDD 8.2). */
    public static final double GRAVIKIN_TAUNT = 150.0;

    // ------------------------------------------------------------------ the arena's rules

    /** Nothing can be placed this close to the throne during the fight. */
    public static final double NO_PLACE_RADIUS = 32.0;
    /** Projectiles fired from farther than this burn up. */
    public static final double PROJECTILE_RANGE = 40.0;
    /** The player_down line waits at least this long between two falls. */
    public static final int PLAYER_DOWN_GAP = 400;

    /** Every warning's length, for the rule that none is shorter than {@link #MIN_TELL}. */
    public static int[] tells() {
        return new int[] {SUNDER_TELL, SUNDER_GLINT, SWEEP_TELL, FLARE_TELL, LANCE_TRACK + LANCE_LOCK, BEAM_TELL, LASH_TELL, RAIN_TELL,
                SHAKE_TICKS, HaloShed.TRAILS, HaloShed.FLASH};
    }

    /** A warning's length never under {@link #MIN_TELL}. */
    public static int tell(int ticks) {
        return Math.max(MIN_TELL, ticks);
    }
}
