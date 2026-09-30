package com.cosmicbreach.world;

import org.jetbrains.annotations.Nullable;

/**
 * The two Shear bands (GDD 2.1): open sky between the layers. A player falling into a band needs the
 * attunement of the layer below it ({@link #guards}) to pass; see {@link ShearBands}.
 */
public enum ShearBand {
    /** Between the Upper Reach and the Drift. */
    A(300, 320, Layer.DRIFT),
    /** Between the Drift and the Deep. */
    B(145, 160, Layer.DEEP);

    /** Lowest Y inside the band. */
    public final int minY;
    /** First Y above the band. */
    public final int maxY;
    /** The layer the band opens onto: its attunement lets a player through. */
    public final Layer guards;

    ShearBand(int minY, int maxY, Layer guards) {
        this.minY = minY;
        this.maxY = maxY;
        this.guards = guards;
    }

    /** The band holding {@code y}, or null outside both. */
    public static @Nullable ShearBand at(double y) {
        if (y >= A.minY && y < A.maxY) {
            return A;
        }
        if (y >= B.minY && y < B.maxY) {
            return B;
        }
        return null;
    }
}
