package com.cosmicbreach.progression;

/**
 * Where Attunement XP comes from, per layer tier (GDD section 3.2). Use {@link #at(LayerTier)} for the
 * amount, and {@link AttunementXp} to hand it out: a kill goes to every participant in full, a
 * structure or puzzle to whoever found or solved it.
 */
public enum XpSource {
    TRASH_MOB(25, 75, 180),
    ELITE(250, 600, 1_200),
    /** The first time a player finds a structure. */
    STRUCTURE_FOUND(150, 400, 900),
    /** The first time a player solves a structure's puzzle. */
    PUZZLE_SOLVED(800, 2_000, 4_000),
    GUARDIAN_FIRST_KILL(3_000, 9_000, 25_000),
    GUARDIAN_REPEAT_KILL(600, 1_800, 5_000);

    /** The Hollow Heliarch, which stands outside the tiers. */
    public static final int HELIARCH_FIRST_KILL = 40_000;
    public static final int HELIARCH_REPEAT_KILL = 8_000;

    /** Stat points on top of the levels (GDD section 3.3): 3 guardians x 1 plus the Heliarch's 2. */
    public static final int GUARDIAN_FIRST_KILL_POINTS = 1;
    public static final int HELIARCH_FIRST_KILL_POINTS = 2;

    private final int reach;
    private final int drift;
    private final int deep;

    XpSource(int reach, int drift, int deep) {
        this.reach = reach;
        this.drift = drift;
        this.deep = deep;
    }

    public int at(LayerTier tier) {
        return switch (tier) {
            case REACH -> reach;
            case DRIFT -> drift;
            case DEEP -> deep;
        };
    }

    /** The three layers of Aetheria, which set a source's tier: the Upper Reach (T1), the Drift (T2), the Deep (T3). */
    public enum LayerTier {
        REACH,
        DRIFT,
        DEEP
    }
}
