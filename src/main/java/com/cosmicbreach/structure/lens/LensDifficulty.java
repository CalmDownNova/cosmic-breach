package com.cosmicbreach.structure.lens;

/**
 * The Lens Array's difficulty knobs (GDD 6.2): grid size, receptor count, colours (filters), splitters (one per
 * fork, so receptors minus one), Umbral blocks, loose pieces, decoys and the minimum moves a puzzle must need.
 *
 * @param size          grid side
 * @param minReceptors  fewest receptors
 * @param maxReceptors  most receptors
 * @param colors        receptors want gold, teal or magenta light, so the paths carry filters
 * @param minLoose      fewest solution mirrors made loose and carried off
 * @param maxLoose      most of them
 * @param looseFilter   a filter may be loose and carried off too
 * @param decoyMirrors  mirrors off the solution (at most)
 * @param decoyUmbral   Umbral blocks off the solution (at most)
 * @param minTurns      fewest turns on the path to each receptor
 * @param maxTurns      most turns on it
 * @param minMoves      the fewest moves a puzzle of this difficulty may be solved in (4, 7 or 10)
 * @param maxMoves      the most moves the scramble may cost (so an easy room stays easy)
 */
public record LensDifficulty(String name, int size, int minReceptors, int maxReceptors, boolean colors, int minLoose, int maxLoose,
        boolean looseFilter, int decoyMirrors, int decoyUmbral, int minTurns, int maxTurns, int minMoves, int maxMoves) {
    /** 5 by 5, one receptor, sunlight only: the Spire Reliquary's easier rooms. */
    public static final LensDifficulty EASY_5 = new LensDifficulty("easy_5", 5, 1, 1, false, 1, 1, false, 1, 1, 2, 3, 4, 7);
    /** 5 by 5, one receptor: the Spire Reliquary's harder rooms. */
    public static final LensDifficulty MEDIUM_5 = new LensDifficulty("medium_5", 5, 1, 1, false, 1, 1, false, 2, 2, 3, 4, 7, 11);
    /** 5 by 5, one or two coloured receptors. */
    public static final LensDifficulty HARD_5 = new LensDifficulty("hard_5", 5, 1, 2, true, 2, 3, false, 2, 2, 2, 4, 10, 15);
    /** 7 by 7, one or two receptors, filters. */
    public static final LensDifficulty EASY_7 = new LensDifficulty("easy_7", 7, 1, 2, true, 1, 1, false, 1, 1, 1, 3, 4, 7);
    /** 7 by 7, two receptors, a splitter, filters: the Gyre Observatory's easier rooms. */
    public static final LensDifficulty MEDIUM_7 = new LensDifficulty("medium_7", 7, 2, 2, true, 1, 2, true, 2, 1, 1, 3, 7, 11);
    /**
     * 7 by 7, three receptors, two splitters, filters: the Observatory's hardest rooms and the Sanctum's west wing.
     * Its filters stay fixed: a third loose piece makes the solver's proof several times slower.
     */
    public static final LensDifficulty HARD_7 = new LensDifficulty("hard_7", 7, 3, 3, true, 2, 2, false, 2, 2, 1, 3, 10, 16);

    public static final LensDifficulty[] ALL = {EASY_5, MEDIUM_5, HARD_5, EASY_7, MEDIUM_7, HARD_7};

    public static LensDifficulty byName(String name) {
        for (LensDifficulty d : ALL) {
            if (d.name.equals(name)) {
                return d;
            }
        }
        throw new IllegalArgumentException("no Lens Array difficulty " + name);
    }
}
