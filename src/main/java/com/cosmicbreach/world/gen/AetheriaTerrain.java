package com.cosmicbreach.world.gen;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * Aetheria's terrain as one pure model: the Upper Reach's islands ({@link ReachIslands}), the Drift's
 * asteroid belts ({@link DriftBelts}) and the Deep's pillars, needles and spans ({@link DeepSpans}), all
 * clear of the Breach ({@link BreachShape}). The chunk generator reads it through {@link TerrainDensity};
 * features, commands and tests read it directly (it never touches a level).
 *
 * <p>Everything follows from one salt, derived from the world seed through the noise
 * {@code cosmicbreach:terrain_seed} ({@link #saltOf}), so the density function (which only sees wired
 * noises) and the features (which see the {@link RandomState}) build identical models.
 *
 * <p>Evaluation is per block, not interpolated: a column's 2D data (island top, bottom and rim, the rocks
 * that reach it) is computed once ({@link #sampleColumn}) and each block's density is then a handful of
 * comparisons, with 3D noise only within a few blocks of a surface. Densities are in blocks (positive is
 * rock) and {@link #density} returns them divided by 8 so structure terrain adaptation (the beardifier)
 * keeps vanilla's scale.
 */
public final class AetheriaTerrain {
    /** Density value returned for certain air, in the router's units. */
    public static final double AIR = -1.0;
    private static final ConcurrentHashMap<Long, AetheriaTerrain> BY_SALT = new ConcurrentHashMap<>();

    public final long salt;
    public final BreachShape breach;
    public final ReachIslands reach;
    public final DriftBelts drift;
    public final DeepSpans deep;
    public final SpireField spires;

    private AetheriaTerrain(long salt) {
        this.salt = salt;
        this.breach = new BreachShape(salt);
        this.reach = new ReachIslands(salt, breach);
        this.drift = new DriftBelts(salt);
        this.deep = new DeepSpans(salt);
        this.spires = new SpireField(salt, reach);
    }

    /** The shared model for a salt (models are immutable apart from per-thread caches). */
    public static AetheriaTerrain forSalt(long salt) {
        return BY_SALT.computeIfAbsent(salt, AetheriaTerrain::new);
    }

    /** The model of a level, from its generator's random state. */
    public static AetheriaTerrain of(RandomState randomState) {
        return forSalt(saltOf(randomState.getOrCreateNoise(TerrainDensity.SEED_NOISE)));
    }

    /** A 64-bit salt from a seeded noise: its values at three fixed points, mixed. */
    public static long saltOf(NormalNoise noise) {
        long a = Double.doubleToLongBits(noise.getValue(0.137, 11.3, -7.9));
        long b = Double.doubleToLongBits(noise.getValue(-19.21, 3.7, 5.03));
        long c = Double.doubleToLongBits(noise.getValue(42.5, -0.33, 17.77));
        return Hashing.mix(a ^ Hashing.mix(b ^ Hashing.mix(c)));
    }

    /** Everything one column needs. Mutable, reused per thread. */
    public static final class Column {
        public final ReachIslands.Column reach = new ReachIslands.Column();
        public final List<DriftBelts.Asteroid> asteroids = new ArrayList<>(6);
        public final DeepSpans.Column deep = new DeepSpans.Column();
    }

    public void sampleColumn(int x, int z, Column out) {
        reach.sample(x + 0.5, z + 0.5, out.reach);
        drift.candidates(x, z, out.asteroids);
        deep.sample(x, z, out.deep);
    }

    /** Density in blocks at (x, y, z) of a sampled column: positive is rock. */
    public double blocks(Column col, int x, int y, int z) {
        if (y >= ReachIslands.FLOOR_Y) {
            return reach.density(col.reach, x, y, z);
        }
        if (y >= DriftBelts.FLOOR_Y && y <= DriftBelts.CEIL_Y) {
            double best = -8.0;
            for (int i = 0, n = col.asteroids.size(); i < n; i++) {
                best = Math.max(best, drift.density(col.asteroids.get(i), x, y, z));
            }
            return best;
        }
        if (y <= DeepSpans.CEIL_Y) {
            return deep.density(col.deep, x, y, z);
        }
        return -8.0;
    }

    /** Router-scale density: blocks divided by 8, clamped to [-1, 1]. */
    public double density(Column col, int x, int y, int z) {
        double b = blocks(col, x, y, z);
        return b <= -8.0 ? AIR : Math.max(-1.0, Math.min(1.0, b / 8.0));
    }

    /** True if some rock may exist at this y (a quick test before any column work). */
    public static boolean mayHaveRock(int y) {
        return (y >= ReachIslands.FLOOR_Y && y < 400)
                || (y >= DriftBelts.FLOOR_Y && y <= DriftBelts.CEIL_Y)
                || (y >= 1 && y <= DeepSpans.CEIL_Y);
    }

    /** Density at one point without any caching (slow; for tools and tests). */
    public double blocksAt(int x, int y, int z) {
        Column col = new Column();
        sampleColumn(x, z, col);
        return blocks(col, x, y, z);
    }
}
