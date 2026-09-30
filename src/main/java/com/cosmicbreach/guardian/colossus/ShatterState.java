package com.cosmicbreach.guardian.colossus;

/**
 * Shatter's rule (Prism Colossus design v1, phase 4), pure: at zero the Colossus bursts into three Prism Shards.
 * The first shard's death starts a {@value #COUNTDOWN_TICKS}-tick countdown; all three must die before it ends.
 * If they don't, the survivors fly home and the Colossus re-forms at {@link #REMERGE_HEALTH} of its health, and
 * Shatter comes again at zero.
 */
public final class ShatterState {
    /** 20 seconds. */
    public static final int COUNTDOWN_TICKS = 400;
    /** The share of its health the Colossus re-forms with. */
    public static final double REMERGE_HEALTH = 0.25;

    /** What a death or a tick means for the fight. */
    public enum Result {
        /** Nothing changes yet. */
        CONTINUE,
        /** The last shard died in time: the Colossus is dead. */
        KILLED,
        /** The countdown ran out with shards alive: they re-merge. */
        REMERGE
    }

    private final int shards;
    private int alive;
    private long countdownStart = Long.MIN_VALUE;
    private boolean over;

    public ShatterState(int shards) {
        if (shards < 1) {
            throw new IllegalArgumentException("at least one shard, got " + shards);
        }
        this.shards = shards;
        this.alive = shards;
    }

    /** A shard died at {@code now}. The first death starts the countdown; the last, in time, is the kill. */
    public Result shardDied(long now) {
        if (over || alive <= 0) {
            return Result.CONTINUE;
        }
        if (!counting()) {
            countdownStart = now;
        } else if (now - countdownStart >= COUNTDOWN_TICKS) {
            return Result.CONTINUE; // too late: tick() re-merges them
        }
        alive--;
        if (alive == 0) {
            over = true;
            return Result.KILLED;
        }
        return Result.CONTINUE;
    }

    /** Called every tick: the countdown running out with shards alive re-merges them. */
    public Result tick(long now) {
        if (over || !counting() || alive <= 0) {
            return Result.CONTINUE;
        }
        if (now - countdownStart >= COUNTDOWN_TICKS) {
            over = true;
            return Result.REMERGE;
        }
        return Result.CONTINUE;
    }

    /** True once the first shard died (and until the end). */
    public boolean counting() {
        return countdownStart != Long.MIN_VALUE;
    }

    /** Ticks left on the countdown at {@code now}: the whole countdown before it starts, 0 once it ran out. */
    public int ticksLeft(long now) {
        if (!counting()) {
            return COUNTDOWN_TICKS;
        }
        return (int) Math.max(0, COUNTDOWN_TICKS - (now - countdownStart));
    }

    public int alive() {
        return alive;
    }

    public int shards() {
        return shards;
    }

    /** True once the shards were all killed or re-merged. */
    public boolean over() {
        return over;
    }

    /** The countdown's start (for saving), or {@link Long#MIN_VALUE}. */
    public long countdownStart() {
        return countdownStart;
    }

    /** Restores a saved state. */
    public static ShatterState restore(int shards, int alive, long countdownStart) {
        ShatterState s = new ShatterState(shards);
        s.alive = Math.max(0, Math.min(shards, alive));
        s.countdownStart = countdownStart;
        return s;
    }
}
