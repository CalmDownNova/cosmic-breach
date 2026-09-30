package com.cosmicbreach.structure.gen;

import com.cosmicbreach.structure.lens.LensDifficulty;
import java.util.Random;

/**
 * A Spire Reliquary's plan (GDD 6.1), all from its seed: a hollow spire of {@link #chambers} (3 to 5) stacked
 * chambers, each {@link #STOREY} blocks, round a core of radius {@link #CHAMBER}; between the chambers' wall and
 * the outer shell a spiral ramp climbs one full turn per storey, starting at {@link #entryAngle}, where every
 * chamber's door is. The ground floor is the entrance hall, the top chamber holds the 5 by 5 Lens Array (easy or
 * medium) and the vault, the ones between are guard halls with a Shardling pack each. A cone roof tops it.
 *
 * <p>Coordinates are relative to the spire's axis at the entrance hall's floor: its floor blocks are at y = -1.
 */
public record ReliquaryLayout(int chambers, double entryAngle, LensDifficulty difficulty, int roof, long puzzleSeed, int[] packs) {
    public static final int STOREY = 8;
    public static final double CHAMBER = 8.5;
    public static final double INNER_WALL = 9.5;
    public static final double GALLERY = 11.5;
    public static final double OUTER = 12.5;
    public static final double PLAZA = 15.5;

    public static ReliquaryLayout of(long seed) {
        Random r = new Random((seed ^ 0x5EED5EEDL) * 0x9E3779B97F4A7C15L);
        int chambers = 3 + r.nextInt(3);
        double entry = r.nextInt(8) * Math.PI / 4;
        LensDifficulty diff = r.nextInt(3) == 0 ? LensDifficulty.MEDIUM_5 : LensDifficulty.EASY_5;
        int roof = 14 + r.nextInt(5);
        int[] packs = new int[chambers];
        for (int c = 1; c < chambers - 1; c++) {
            packs[c] = 3 + r.nextInt(3);
        }
        return new ReliquaryLayout(chambers, entry, diff, roof, r.nextLong(), packs);
    }

    /** Floor block y of chamber {@code c}. */
    public int floorY(int c) {
        return STOREY * c - 1;
    }

    /** The lens chamber (the top one). */
    public int lensChamber() {
        return chambers - 1;
    }

    /** The top of the chambers: the lens chamber's ceiling. */
    public int topY() {
        return STOREY * chambers - 1;
    }

    /** Height of everything above the entrance floor. */
    public int height() {
        return topY() + roof + 2;
    }

    /** The lens room's ceiling height over its pedestals (the aperture's offset). */
    public int ceiling() {
        return STOREY - 1;
    }

    /** Angle round the axis from the entry, 0 to 2 pi, in the ramp's climbing sense. */
    public double phase(int x, int z) {
        double a = Math.atan2(z, x) - entryAngle;
        a %= 2 * Math.PI;
        return a < 0 ? a + 2 * Math.PI : a;
    }

    /** The ramp's walking height of turn {@code k} at phase {@code phi}. */
    public double ramp(int k, double phi) {
        return STOREY * (k + phi / (2 * Math.PI));
    }
}
