package com.cosmicbreach.client.gear;

/**
 * Which generated item layers draw on the faces only (1.1 design section 8). A weapon's or set piece's tier trim is its
 * model's second layer, an outline of the first. Vanilla builds edge walls for every layer, and the trim's walls lie in
 * the base layer's planes with other ends, so the two depth-fight along the item's edge once a reforge makes the trim
 * opaque (2026-09-30 render audit, bug A). A trim layer keeps only its front and back faces, which are identical to the
 * base layer's and win by model order; the base layer alone draws the edges, as it does before any reforge.
 */
public final class TrimLayers {
    public static final String NAMESPACE = "cosmicbreach";
    public static final String SUFFIX = "_trim";

    private TrimLayers() {
    }

    /** True if generated layer {@code tintIndex} with sprite {@code namespace:path} keeps its two faces only. */
    public static boolean facesOnly(int tintIndex, String namespace, String path) {
        return tintIndex > 0 && NAMESPACE.equals(namespace) && path.endsWith(SUFFIX);
    }
}
