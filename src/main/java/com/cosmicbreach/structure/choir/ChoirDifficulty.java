package com.cosmicbreach.structure.choir;

/**
 * How hard a Choir Floor plays (GDD 6.3). Every floor has the three rounds: 4 notes, one per beat; 6 notes with
 * an eighth-note pair; 8 notes with the ring turning a slot between the call and the answer and one ghost note.
 * {@link #CRYPT} is that, as the GDD writes it. {@link #SANCTUM}, for the east wing of the Breach Sanctum, is the
 * hardest: the same, plus an eighth-note pair in round 3 as well.
 */
public enum ChoirDifficulty {
    CRYPT(false),
    SANCTUM(true);

    private final boolean pairInLastRound;

    ChoirDifficulty(boolean pairInLastRound) {
        this.pairInLastRound = pairInLastRound;
    }

    /** Notes in round {@code round} (0-based). */
    public int notes(int round) {
        return switch (round) {
            case 0 -> 4;
            case 1 -> 6;
            default -> 8;
        };
    }

    /** Eighth-note pairs in round {@code round}. */
    public int pairs(int round) {
        return round == 1 || round == 2 && pairInLastRound ? 1 : 0;
    }

    /** True if every note of the round falls one beat after the last (round 1: steps only). */
    public boolean onePerBeat(int round) {
        return round == 0;
    }

    /** True if the ring turns between the call and the answer in this round. */
    public boolean rotates(int round) {
        return round == 2;
    }

    /** True if one note of this round's phrase is a ghost: played dim, and only once. */
    public boolean ghost(int round) {
        return round == 2;
    }

    /** Chords in round {@code round} when two or more players woke the floor; none solo. */
    public int chords(int round, boolean coop) {
        if (!coop) {
            return 0;
        }
        return round == 2 ? 2 : 1;
    }

    public static ChoirDifficulty byName(String name) {
        for (ChoirDifficulty d : values()) {
            if (d.name().equals(name)) {
                return d;
            }
        }
        return CRYPT;
    }
}
