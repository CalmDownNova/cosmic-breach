package com.cosmicbreach.onboarding;

/**
 * When each player's Starfalls come (GDD 1.3), as pure arithmetic on the Overworld's day time: the absolute
 * tick count that {@code /time set} and sleeping move, 24,000 ticks a day.
 *
 * <ul>
 *   <li>The first falls at the player's first sunset: a random tick in the window (12,000 to 12,500 by
 *       default, before beds work) of the first day whose window has not passed when the player is first
 *       seen. Joining inside a window still counts if at least {@link #NOTICE} ticks of it are left.</li>
 *   <li>After that, one falls each later night at a random tick from 13,000 to 18,000.</li>
 *   <li>A fall that came due while the night was skipped by sleeping comes at once, at dawn. One missed by
 *       more than {@link #MAX_LATE} ticks (the player was elsewhere all night) is dropped and the next one is
 *       planned; so is one planned more than two days ahead (the clock was set back).</li>
 * </ul>
 * Randomness comes in as a number from 0 (inclusive) to 1 (exclusive), so every rule is testable.
 */
public final class StarfallSchedule {
    public static final long DAY = 24_000L;
    public static final int NIGHT_START = 13_000;
    public static final int NIGHT_END = 18_000;
    /** The least warning a fall gets after it is planned. */
    public static final int NOTICE = 100;
    /** Sleeping skips at most from tick 12,542 to dawn, so a fall this late was slept through, not missed. */
    public static final long MAX_LATE = 12_000L;

    /** What to do with a planned fall. */
    public enum Decision { WAIT, FALL, REPLAN }

    private StarfallSchedule() {
    }

    /** The first fall for a player first seen at {@code now}: in the next sunset window that is still open. */
    public static long firstFall(long now, int windowStart, int windowEnd, double random) {
        int start = Math.max(0, Math.min(windowStart, (int) DAY - 1));
        int end = Math.max(start + 1, Math.min(windowEnd, (int) DAY));
        long day = Math.floorDiv(now, DAY);
        long opens = day * DAY + start;
        long closes = day * DAY + end;
        if (now < opens) {
            return opens + (long) (random * (end - start));
        }
        if (now + NOTICE < closes) {
            long from = now + NOTICE;
            return from + (long) (random * (closes - from));
        }
        return opens + DAY + (long) (random * (end - start));
    }

    /** The fall after one that was due at {@code due}: in the next day's night. */
    public static long nightAfter(long due, double random) {
        return (Math.floorDiv(due, DAY) + 1) * DAY + NIGHT_START + (long) (random * (NIGHT_END - NIGHT_START));
    }

    /** A fresh plan after a missed fall: tonight if the night has not begun (or has time left), else tomorrow night. */
    public static long nextNight(long now, double random) {
        long day = Math.floorDiv(now, DAY);
        long nightOpens = day * DAY + NIGHT_START;
        long nightCloses = day * DAY + NIGHT_END;
        if (now + NOTICE <= nightOpens) {
            return nightOpens + (long) (random * (NIGHT_END - NIGHT_START));
        }
        if (now + NOTICE < nightCloses) {
            long from = now + NOTICE;
            return from + (long) (random * (nightCloses - from));
        }
        return nightOpens + DAY + (long) (random * (NIGHT_END - NIGHT_START));
    }

    /** Whether a fall planned for {@code due} should wait, fall now, or be planned again. */
    public static Decision decide(long now, long due) {
        if (now < due) {
            return due - now > 2 * DAY ? Decision.REPLAN : Decision.WAIT;
        }
        return now - due <= MAX_LATE ? Decision.FALL : Decision.REPLAN;
    }

    /** The time of day (0 to 23,999) of an absolute day time. */
    public static int timeOfDay(long dayTime) {
        return (int) Math.floorMod(dayTime, DAY);
    }
}
