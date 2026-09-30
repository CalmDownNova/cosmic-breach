package com.cosmicbreach.structure.choir;

/** A Choir Floor's three rounds, drawn together from the room's seed ({@link ChoirGenerator#song}). */
public record ChoirSong(ChoirPhrase[] rounds, boolean coop) {
    public ChoirPhrase phrase(int round) {
        return rounds[Math.max(0, Math.min(rounds.length - 1, round))];
    }

    /** A bit per pad the whole song sounds. */
    public int padMask() {
        int mask = 0;
        for (ChoirPhrase p : rounds) {
            mask |= p.padMask();
        }
        return mask;
    }
}
