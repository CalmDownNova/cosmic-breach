package com.cosmicbreach.guardian.leviathan;

/**
 * The Thalassine Leviathan's numbers (Thalassine Leviathan design v1), in ticks and blocks, shared by the server (which
 * moves it and hits with them) and the client (which draws the same shapes from the same synced inputs). Telegraphs are
 * never shorter than {@value #MIN_TELEGRAPH} ticks.
 */
public final class LeviathanMoves {
    public static final int MIN_TELEGRAPH = 12;

    // ------------------------------------------------------------------ the creature
    /**
     * Its health for one player: the design's 700, tuned down. A scripted near-perfect bot at level 24 with the Comet Maul
     * needed 265 s at 700 (it reaches the body on about 15% of the orbit's ticks, not the design's 30%, for about 2 damage a
     * second, not 5) and 182 s at 300; the Moorages and the waits between passes are fixed, so a person's slower hitting
     * only stretches the rest. At 220 the bot needs about two and a half minutes, which puts a person near the design's
     * three and a half to four.
     */
    public static final double BASE_HEALTH = 220.0;
    public static final double ARMOR = 8.0;
    public static final double TOUGHNESS = 4.0;
    /**
     * Its Break gauge (300 poise) and how long Impact stays in it: a minute rather than a creature's 5 s, three passes of
     * its orbit, since a platform only reaches it as it goes by (at 40 s a near-perfect bot peaked at 97%).
     */
    public static final double BREAK_POISE = 300.0;
    public static final int BREAK_WINDOW = 1200;
    public static final int BREAK_TICKS = 100;
    /** Damage taken while Broken (its head sunk level with the platforms). */
    public static final double BREAK_DAMAGE_TAKEN = 1.5;
    /** Hit parts: the head, the four body segments, the tail, and the song glands (only while moored). */
    public static final double HEAD_TAKEN = 1.0;
    public static final double BODY_TAKEN = 0.8;
    public static final double TAIL_TAKEN = 0.6;
    public static final double GLAND_TAKEN = 2.0;

    /** The body: where each follower's middle is, in blocks of path behind the head's middle (segments 1 to 4, the tail). */
    public static final double[] FOLLOW = {7.0, 13.0, 19.0, 25.0, 31.0};
    /**
     * The body's half widths at the head's and each follower's middle (the model tapers from the head's 5 blocks to the
     * tail stock's 1; the fan is wider but thin): the Moorage coil's walkable back narrows with it.
     */
    public static final double[] HALF_WIDTH = {2.5, 2.1, 1.7, 1.35, 0.9, 0.6};
    /**
     * Hit boxes, width and height: the head (the entity itself), segments 1 to 4, the tail. The model's size at each
     * middle, a little wider (the tail's box takes in the root of its flukes); the heights set the coil's level back.
     */
    public static final float[] PART_WIDTH = {5.0f, 4.3f, 3.6f, 2.9f, 2.1f, 3.6f};
    public static final float[] PART_HEIGHT = {4.2f, 3.75f, 3.1f, 2.5f, 1.75f, 1.6f};
    public static final float GLAND_SIZE = 1.5f;
    /** The glands sit on the spine of segments 1 to 4, this far over each segment's middle. */
    public static final double GLAND_RISE = 1.9;
    /** The mouth, ahead of the head's middle. */
    public static final double MOUTH_AHEAD = 3.6;

    // ------------------------------------------------------------------ moving
    public static final double ORBIT_RADIUS = 22.0;
    /** It rises and falls this much in a slow wave, twice a lap. */
    public static final double WAVE = 4.0;
    public static final double SPEED = 0.35;
    public static final double SPEED_TWO = 0.42;
    /** Its song's speed while singing, and while winding up a flick (it holds nearly still). */
    public static final double SONG_SPEED = 0.1;
    public static final int INTRO = 200;
    /** Nobody inside the sphere this long: it resets. */
    public static final int RESET_ABSENT = 600;
    /** Ticks between the end of one attack and the choice of the next (it swims its orbit meanwhile). */
    public static final int GAP = 50;
    /**
     * Ticks between the end of a Breach Dive and the choice of the next attack: short, because the dive's loop leaves the
     * target nearly a lap ahead again, so its next dive is a long one (about 160 ticks with the tell).
     */
    public static final int DIVE_GAP = 15;
    public static final int FIRST_ATTACK = 60;
    /** Her song in the intro starts this many ticks in, and plays in full whatever else is said (it is her identity). */
    public static final int INTRO_SONG = 150;

    /** The intro tick her song is over on: its watched length ({@code BossVoiceSounds}), counted from {@link #INTRO_SONG}. */
    public static int introSongOver() {
        return INTRO_SONG + com.cosmicbreach.voice.boss.BossVoiceSounds.clearTicks("leviathan/song");
    }

    // ------------------------------------------------------------------ Breach Dive
    public static final int DIVE_TELL = 30;
    /**
     * The dive's dust wake shows its whole path for this many ticks from the start of the tell (the engine's minimum
     * telegraph), then fades over {@value #WAKE_FADE} ticks and is gone for the rest of the tell and the dive: the player
     * must remember the line. The song's swell runs the whole tell as the audio cue.
     */
    public static final int WAKE_SHOW = MIN_TELEGRAPH;
    public static final int WAKE_FADE = 6;
    public static final double DIVE_SPEED = 1.3;
    public static final double DIVE_DAMAGE = 20.0;
    public static final double DIVE_IMPACT = 40.0;
    public static final int DIVE_COOLDOWN = 140;
    public static final int DIVE_COOLDOWN_TWO = 120;
    /** Phase 2: the chance a dive is chained straight into a second. */
    public static final double DIVE_CHAIN = 0.4;
    /**
     * It dives at a target this far ahead along its orbit (radians). The path keeps to the ring between the core and the
     * platforms however far round the target is ({@link LeviathanPaths#dive}), so the dive never cuts across the core. Up to
     * 330 degrees: a ledge farmer who has just been passed gets a dive that loops almost a lap to reach them (it was 150, so
     * a dive came about once a lap).
     */
    public static final double DIVE_AHEAD_MIN = Math.toRadians(35.0);
    public static final double DIVE_AHEAD_MAX = Math.toRadians(330.0);
    /** Its voice calls a target "out of reach" past this far ahead (the old edge of the dive window). */
    public static final double VOICE_FAR_AHEAD = Math.toRadians(150.0);
    /** Dives in a row it may make (the third attack must be another one, when another is ready). */
    public static final int DIVE_RUN = 2;
    /** Weight of a dive past that run: only when nothing else is ready. */
    public static final double DIVE_FALLBACK_WEIGHT = 0.05;
    /** A player this close to the head's (or the first segment's) middle during a dive is struck. */
    public static final double DIVE_REACH = 2.7;

    // ------------------------------------------------------------------ Song of Pulling
    public static final int SONG_TELL = 40;
    public static final int SONG_PULL = 80;
    /** Ticks the pull takes to rise from its least to its most. */
    public static final int SONG_RAMP = 40;
    public static final double SONG_CONE = 60.0;
    public static final double SONG_RANGE = 30.0;
    public static final double PULL_MIN = 0.06;
    public static final double PULL_MAX = 0.14;
    /** A dash breaks the pull for this long. */
    public static final int DASH_BREAK = 20;
    /** Elytra fliers are pulled this much harder. */
    public static final double FLIER_PULL = 2.0;
    public static final double BITE_DAMAGE = 14.0;
    public static final double BITE_REACH = 2.8;
    public static final double BITE_THROW = 1.3;
    public static final int SONG_COOLDOWN = 320;

    // ------------------------------------------------------------------ Tail Flick
    public static final int FLICK_TELL = 20;
    public static final int FLICK_GLINT = 16;
    public static final int FLICK_ACTIVE = 3;
    public static final int FLICK_RECOVER = 12;
    public static final double FLICK_DAMAGE = 12.0;
    public static final double FLICK_KNOCKBACK = 1.6;
    /** A parry deals twice this to its Break gauge: 60. */
    public static final double FLICK_IMPACT = 30.0;
    /**
     * It flicks when a player stands this close to its tail's middle (the design's 5 blocks, from the tail's own hit box
     * rather than its middle: the fan is 9 blocks across); the flick's fan reaches a little farther.
     */
    public static final double FLICK_TRIGGER = 6.5;
    public static final double FLICK_REACH = 7.0;
    public static final int FLICK_COOLDOWN = 120;
    /** A parried flick: the tail droops this long, holding still (a free window on the tail). */
    public static final int DROOP = 40;

    // ------------------------------------------------------------------ Scale Shed (phase 2)
    public static final int SHED_TELL = 20;
    public static final int SHED_COUNT = 6;
    public static final double SCALE_DAMAGE = 8.0;
    public static final double SCALE_HEALTH = 4.0;
    public static final double SCALE_SPEED = 0.16;
    public static final int SCALE_LIFE = 500;
    public static final int SHED_COOLDOWN = 280;

    // ------------------------------------------------------------------ Moorage
    /** Its health lines: the first Moorage at half, the second at a fifth. */
    public static final double[] MOORAGE_AT = {0.5, 0.2};
    /** The coil's middle line round the central asteroid (its flank rests on the rock). */
    public static final double COIL_RADIUS = 15.0;
    /** The coil's back (its walkable top) this far over the lair's centre: the coil's spine sinks toward the tail to keep it level. */
    public static final double COIL_TOP = 1.0;
    public static final double SWIM_IN_SPEED = 0.6;
    public static final double SHUDDER_DAMAGE = 6.0;
    public static final double SHUDDER_UP = 1.2;
    /** Standing this close to a gland you hold on through a shudder. */
    public static final double HOLD_ON = 2.2;

    // ------------------------------------------------------------------ death
    public static final double SINK_SPEED = 0.35;
    public static final int PEARL = 60;
    /** How long it may sink before the pearl rises whatever happens. */
    public static final int SINK_MAX = 200;

    private LeviathanMoves() {
    }

    /** Its speed along the orbit in a phase. */
    public static double orbitSpeed(boolean phaseTwo) {
        return phaseTwo ? SPEED_TWO : SPEED;
    }

    /** How strongly the dive's wake shows {@code sinceStart} ticks into the tell (0 to 1): full, then fading, then gone. */
    public static float wakeAlpha(double sinceStart) {
        if (sinceStart < 0 || sinceStart >= WAKE_SHOW + WAKE_FADE) {
            return 0.0f;
        }
        return sinceStart <= WAKE_SHOW ? 1.0f : (float) (1.0 - (sinceStart - WAKE_SHOW) / WAKE_FADE);
    }

    /** The dive's cooldown in a phase (every 7 s, every 6 s in phase 2). */
    public static int diveCooldown(boolean phaseTwo) {
        return phaseTwo ? DIVE_COOLDOWN_TWO : DIVE_COOLDOWN;
    }
}
