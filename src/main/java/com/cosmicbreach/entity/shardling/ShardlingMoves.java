package com.cosmicbreach.entity.shardling;

/**
 * One Shardling's attack timeline (GDD section 7.1), in server ticks. Pure; the Shardling calls
 * {@link #tick} first thing every tick and acts on the phase change it returns.
 *
 * <ul>
 *   <li><b>Splinter Lunge:</b> {@value #TELL_TICKS} ticks of telegraph (crouch, gold spines, the
 *       rising ting), then {@value #LUNGE_TICKS} active ticks in which the leap can hit and can be
 *       parried, then {@value #RECOVER_TICKS} ticks of recovery taking x{@value #RECOVERY_DAMAGE_TAKEN}
 *       damage.</li>
 *   <li><b>Shard Spit:</b> {@value #SPIT_TELL_TICKS} ticks of telegraph (throat glow, red glint), the
 *       needles fly as it ends, then {@value #SPIT_TICKS} ticks of punish window. Never parryable.</li>
 *   <li><b>Stagger:</b> breaks off anything, for as long as the poise break says.</li>
 * </ul>
 *
 * A phase counts the tick it began on: a telegraph started on tick {@code t} (after that tick's
 * {@link #tick}) launches on tick {@code t + 12}, so the Shardling shows it for exactly 12 ticks.
 */
public final class ShardlingMoves {
    public enum Phase { NONE, TELL, LUNGE, RECOVER, SPIT_TELL, SPIT, STAGGER }

    /** What a tick changed. */
    public enum Event {
        /** Nothing changed. */
        NONE,
        /** The leap: the first active tick. */
        LAUNCHED,
        /** The active ticks are over; recovery starts. */
        RECOVERING,
        /** The needles fly; the punish window starts. */
        FIRED,
        /** Back to NONE: the attack or stagger is over. */
        ENDED
    }

    public static final int TELL_TICKS = 12;
    public static final int LUNGE_TICKS = 4;
    public static final int RECOVER_TICKS = 16;
    public static final int SPIT_TELL_TICKS = 10;
    public static final int SPIT_TICKS = 12;
    public static final float RECOVERY_DAMAGE_TAKEN = 1.25f;
    /**
     * Rest after an attack before the pack may pick the Shardling again: this many ticks plus up to
     * {@value #REST_SPREAD}, half of it after a stagger. Long enough that the pack's shape (the Taunter
     * in front, Flankers behind) holds between attacks instead of dissolving into a swarm.
     */
    public static final int REST_TICKS = 40;
    public static final int REST_SPREAD = 30;

    private Phase phase = Phase.NONE;
    private int ticks;
    private int staggerTicks;

    public Phase phase() {
        return phase;
    }

    /** Ticks spent in the current phase before this one (0 on the tick it began). */
    public int phaseTicks() {
        return ticks;
    }

    /** Starts the lunge's telegraph. Only from NONE; returns whether it started. */
    public boolean startLunge() {
        return start(Phase.TELL);
    }

    /** Starts the spit's telegraph. Only from NONE; returns whether it started. */
    public boolean startSpit() {
        return start(Phase.SPIT_TELL);
    }

    private boolean start(Phase telegraph) {
        if (phase != Phase.NONE) {
            return false;
        }
        enter(telegraph);
        return true;
    }

    /** Breaks off whatever is going on and staggers for {@code length} ticks (a new stagger restarts it). */
    public void stagger(int length) {
        staggerTicks = Math.max(1, length);
        enter(Phase.STAGGER);
    }

    /** One tick: the phase change it brought, if any. */
    public Event tick() {
        if (phase == Phase.NONE) {
            return Event.NONE;
        }
        ticks++;
        if (ticks < length(phase)) {
            return Event.NONE;
        }
        return switch (phase) {
            case TELL -> {
                enter(Phase.LUNGE);
                yield Event.LAUNCHED;
            }
            case LUNGE -> {
                enter(Phase.RECOVER);
                yield Event.RECOVERING;
            }
            case SPIT_TELL -> {
                enter(Phase.SPIT);
                yield Event.FIRED;
            }
            default -> {
                enter(Phase.NONE);
                yield Event.ENDED;
            }
        };
    }

    private void enter(Phase next) {
        phase = next;
        ticks = 0;
    }

    private int length(Phase p) {
        return switch (p) {
            case TELL -> TELL_TICKS;
            case LUNGE -> LUNGE_TICKS;
            case RECOVER -> RECOVER_TICKS;
            case SPIT_TELL -> SPIT_TELL_TICKS;
            case SPIT -> SPIT_TICKS;
            case STAGGER -> staggerTicks;
            case NONE -> Integer.MAX_VALUE;
        };
    }

    /**
     * Ticks of rest after an attack ({@code staggered}: after a stagger instead) for a roll of 0 to
     * {@value #REST_SPREAD}, times {@code pace}: 1 normally, more for a gentler pack (the sandbox's
     * warm-up pair rests 1.5 times as long).
     */
    public static int restAfter(boolean staggered, float pace, int roll) {
        int base = staggered ? REST_TICKS / 2 : REST_TICKS + Math.max(0, Math.min(REST_SPREAD, roll));
        return Math.round(base * Math.max(0f, pace));
    }

    /** True during the lunge's active ticks: the only parryable moment (the gold telegraph's attack). */
    public boolean parryable() {
        return phase == Phase.LUNGE;
    }

    /** Damage taken is multiplied by this: {@value #RECOVERY_DAMAGE_TAKEN} in the lunge's recovery. */
    public float damageTakenMultiplier() {
        return phase == Phase.RECOVER ? RECOVERY_DAMAGE_TAKEN : 1.0f;
    }

    /** In an attack, from the telegraph to the end of its punish window. */
    public boolean attacking() {
        return phase != Phase.NONE && phase != Phase.STAGGER;
    }

    public boolean staggered() {
        return phase == Phase.STAGGER;
    }

    /** Free to start an attack. */
    public boolean idle() {
        return phase == Phase.NONE;
    }
}
