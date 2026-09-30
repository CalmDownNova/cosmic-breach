package com.cosmicbreach.world;

/**
 * Vesper's beat, Aetheria's clock (GDD 2.3): 100 beats a minute, exactly 12 game ticks a beat. Everything
 * that keeps time in Aetheria follows it: the pulsar's flicker and its 12-second beam sweep in the sky, the
 * music (written at 100 BPM), the Choir Floor, the Unsung's mask switches, the weapons' idle hum, the
 * lichen's pulse. It is pure arithmetic on the level's game time, so the server and every client agree
 * without any syncing, and it costs nothing.
 *
 * <p>Use {@code level.getGameTime()} (it runs on even when the daylight cycle is off) on either side:
 * <pre>{@code
 * long t = level.getGameTime();
 * if (VesperClock.isBeat(t)) { ... }                       // once per beat, on the server or the client
 * int step = VesperClock.beatInBar(t);                      // 0..3, bars of four beats
 * float glow = VesperClock.pulse(t, partialTick);           // 1 on the beat, decaying to 0 by the next
 * double angle = VesperClock.sweepRadians(t, partialTick);  // the beam's heading, one turn per 12 s
 * }</pre>
 * A beat starts on every game tick that is a multiple of 12, so beat {@code n} spans ticks {@code 12n} to
 * {@code 12n + 11}. Server logic should act on {@link #isBeat} (whole ticks); client visuals can use the
 * partial-tick overloads for smooth motion. Weather can silence the pulse (the Eclipse Surge's warning):
 * that is a visual state the sky keeps ({@code client.sky.SkyWeather}), not a change to this clock, which
 * never stops.
 */
public final class VesperClock {
    /** Beats per minute. */
    public static final int BPM = 100;
    /** Game ticks per beat: 20 ticks a second times 60 seconds, over 100 beats. */
    public static final int TICKS_PER_BEAT = 12;
    /** Beats per bar (the music's and the Choir Floor's phrasing). */
    public static final int BEATS_PER_BAR = 4;
    /** One sweep of Vesper's beam across the sky: 12 seconds, 20 beats, 5 bars. */
    public static final int TICKS_PER_SWEEP = 240;
    /** Beats per sweep. */
    public static final int BEATS_PER_SWEEP = TICKS_PER_SWEEP / TICKS_PER_BEAT;
    /** How fast the flicker fades after each beat, per beat (the pulse is e^(-k * phase)). */
    public static final double PULSE_DECAY = 5.0;

    private VesperClock() {
    }

    /** The beat number at {@code gameTime} (beat 0 starts at tick 0). */
    public static long beat(long gameTime) {
        return Math.floorDiv(gameTime, TICKS_PER_BEAT);
    }

    /** Ticks since the current beat started, 0 to 11. */
    public static int tickInBeat(long gameTime) {
        return (int) Math.floorMod(gameTime, TICKS_PER_BEAT);
    }

    /** True on the first tick of every beat. */
    public static boolean isBeat(long gameTime) {
        return tickInBeat(gameTime) == 0;
    }

    /** The beat within its bar of four, 0 to 3. */
    public static int beatInBar(long gameTime) {
        return (int) Math.floorMod(beat(gameTime), BEATS_PER_BAR);
    }

    /** The bar number (bar 0 starts at tick 0). */
    public static long bar(long gameTime) {
        return Math.floorDiv(beat(gameTime), BEATS_PER_BAR);
    }

    /** How far through the current beat, 0 (on the beat) to just under 1, smooth with the partial tick. */
    public static double phase(long gameTime, float partialTick) {
        return (tickInBeat(gameTime) + clampPartial(partialTick)) / TICKS_PER_BEAT;
    }

    /** Beats since tick 0 as a smooth number (beat plus phase). */
    public static double beats(long gameTime, float partialTick) {
        return beat(gameTime) + phase(gameTime, partialTick);
    }

    /** The flicker: 1 on the beat, fading to about 0.007 just before the next. */
    public static float pulse(long gameTime, float partialTick) {
        return (float) Math.exp(-PULSE_DECAY * phase(gameTime, partialTick));
    }

    /** Where the beam points in its sweep, 0 to 2 pi, one full turn every {@link #TICKS_PER_SWEEP} ticks. */
    public static double sweepRadians(long gameTime, float partialTick) {
        return (Math.floorMod(gameTime, TICKS_PER_SWEEP) + clampPartial(partialTick)) / TICKS_PER_SWEEP * Math.PI * 2.0;
    }

    /** Ticks until the next beat starts, 1 to 12 (12 on a beat tick: the next one). */
    public static int ticksToNextBeat(long gameTime) {
        return TICKS_PER_BEAT - tickInBeat(gameTime);
    }

    private static double clampPartial(float partialTick) {
        return Math.max(0.0, Math.min(partialTick, 0.999999));
    }
}
