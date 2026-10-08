package com.cosmicbreach.guardian.unsung;

import com.cosmicbreach.world.VesperClock;

/**
 * Every number of the Unsung's fight (Unsung design v1), in one place. Time is in game ticks on Vesper's clock: a
 * beat is 12 ticks, a turn 8 beats, a Harmonize cycle 48 beats. Damage is before armor.
 */
public final class UnsungMoves {
    // ------------------------------------------------------------------ the clock
    public static final int BEAT = VesperClock.TICKS_PER_BEAT;
    /** Half a beat: the shortest telegraph (the design's floor). */
    public static final int HALF_BEAT = BEAT / 2;
    /** A turn: the song passes on every other bar line. */
    public static final int TURN_BEATS = 8;
    public static final int TURN_TICKS = TURN_BEATS * BEAT;
    /** Harmonize comes every 48 beats; its warning is the cycle's last 8. */
    public static final int CYCLE_BEATS = 48;
    public static final int WARNING_BEATS = 8;
    /** The masks lift in the last 8 beats before the fight's first turn (the intro takes 8 to 23 beats: one more turn when the opener's first word would come before the wake sounds are over). */
    public static final int INTRO_BEATS = 8;
    /** The chords of the song, one per turn: D minor, B flat, G minor, A. */
    public static final int CHORDS = 4;

    // ------------------------------------------------------------------ the masks
    public static final double MASK_HEALTH = 250.0;
    public static final double ARMOR = 10.0;
    public static final double TOUGHNESS = 4.0;
    /** The shared Break gauge: Impact from hits on the masks and parries, over a rolling window. */
    public static final double BREAK_CAPACITY = 400.0;
    /**
     * The gauge's memory (30 s). The framework's 5 s never reaches 400: a parried Bass Drop is 80 and a heavy combo
     * about 80 more. With 30 s a Comet Maul Breaks the choir about twice a minute, twin sickles need their parries.
     */
    public static final int GAUGE_WINDOW = 600;
    public static final int BREAK_TICKS = 100;
    public static final double BREAK_DAMAGE_TAKEN = 1.5;
    /** Nobody on the choir floor for this long resets the fight (30 s). */
    public static final int RESET_ABSENT = 600;
    /** How far from the apse's centre a projectile may be fired and still land. */
    public static final double PROJECTILE_RANGE = 17.0;
    /** A mask: its box (the porcelain face and the shroud under it). */
    public static final float MASK_WIDTH = 1.6f;
    public static final float MASK_HEIGHT = 3.4f;
    /** The face's centre above the box's bottom. */
    public static final double FACE_UP = 2.6;

    // ------------------------------------------------------------------ where the masks are
    /** The triangle they circle in, round the dais. */
    public static final double ORBIT_RADIUS = 6.0;
    /** The face's height over the choir floor (the dais is one block, so this is a little over 3 above the dais). */
    public static final double FACE_HEIGHT = 4.6;
    /** The singer comes down a little (its shroud within a blade's reach); the others float a little higher. */
    public static final double SINGER_DIP = 0.45;
    public static final double HUM_LIFT = 0.25;
    /** One slow turn of the triangle: 64 beats. */
    public static final int ORBIT_TICKS = 64 * BEAT;
    /** How far the masks rise through a Harmonize warning. */
    public static final double HARMONIZE_RISE = 3.0;
    /** Where a fallen mask's face rests in a Break, over the floor. */
    public static final double FALLEN_FACE = 0.9;

    // ------------------------------------------------------------------ Homing Notes (the Alto)
    /**
     * Damage before armor, chosen with {@code combat.core.ArmorMath} so each hit lands as the design's table says against
     * a T3 set with Resilience 24 (armor 20, toughness 8, 12%): note about 5, wave 7, ripple 3, drop 9, Harmonize 9.
     * The first numbers (10, 14, 6, 18, 18) had assumed armor takes half of any hit; vanilla takes 70% of a small one.
     */
    public static final float NOTE_DAMAGE = 16f;
    /** A note forms at the mask for a beat, then flies two beats and bursts on the third beat after it formed. */
    public static final int NOTE_FORM = BEAT;
    public static final int NOTE_FLIGHT = 2 * BEAT;
    /** How close the burst must be to a player's box. */
    public static final double NOTE_BURST = 1.0;
    /** How far a note can bend toward its target per tick (degrees): slow homing, a sidestep beats it. */
    public static final double NOTE_TURN_DEG = 7.0;
    public static final double NOTE_HEIGHT = 1.1;

    // ------------------------------------------------------------------ the Sweeping Wave (the Tenor)
    public static final float WAVE_DAMAGE = 20f;
    public static final double WAVE_IMPACT = 12.0;
    /** The Tenor inhales for a beat; the wave leaves the dais on the beat and runs to the wall. */
    public static final int WAVE_INHALE = BEAT;
    /** Blocks a tick: from the centre to the wall in about 8 ticks, so a jump on the beat clears it anywhere past the dais. */
    public static final double WAVE_SPEED = 2.0;
    /** Knee high over the ground under it. */
    public static final double WAVE_HEIGHT = 0.6;

    // ------------------------------------------------------------------ Ground Ripples (the Bass)
    public static final float RIPPLE_DAMAGE = 11f;
    public static final double RIPPLE_IMPACT = 8.0;
    /** The floor darkens half a beat ahead. */
    public static final int RIPPLE_TELL = HALF_BEAT;
    public static final double RIPPLE_RADIUS = 5.0;
    /** The throw away from the Bass (the engine adds its own 0.4 plus Impact on the hit). */
    public static final double RIPPLE_PUSH = 1.4;
    /** A player crouching stands firm: this share of the throw. */
    public static final double BRACED_PUSH = 0.2;

    // ------------------------------------------------------------------ the Bass Drop
    public static final float DROP_DAMAGE = 23.5f;
    public static final double DROP_IMPACT = 40.0;
    /** Two beats: the Bass rises over its target (the ring follows the target for the first beat), then falls. */
    public static final int DROP_TELL = 2 * BEAT;
    public static final int DROP_TRACK = BEAT;
    /** The gold glint, half a beat before it lands. */
    public static final int DROP_GLINT = DROP_TELL - HALF_BEAT;
    public static final double DROP_RADIUS = 2.5;
    /** How high over its target the Bass hangs before it falls. */
    public static final double DROP_HEIGHT = 7.0;
    /** It rests where it landed for a beat, then goes home over the next. */
    public static final int DROP_REST = BEAT;
    public static final double DROP_PUSH = 0.8;

    // ------------------------------------------------------------------ Harmonize
    public static final float HARMONIZE_DAMAGE = 23.5f;
    /** Silence: no weapon abilities for this long, and this much Resonance drained. */
    public static final int SILENCE_TICKS = 60;
    public static final double SILENCE_DRAIN = 30.0;

    // ------------------------------------------------------------------ the rest of the fight
    public static final int DYING = 80;
    /** The last chord, after the silence. */
    public static final int LAST_CHORD = 2 * BEAT;

    private UnsungMoves() {
    }
}
