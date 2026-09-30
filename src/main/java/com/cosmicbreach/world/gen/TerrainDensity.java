package com.cosmicbreach.world.gen;

import com.cosmicbreach.CosmicBreach;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.jetbrains.annotations.Nullable;

/**
 * Density function {@code cosmicbreach:aetheria_terrain}: Aetheria's whole terrain ({@link AetheriaTerrain})
 * as the noise router's final density. JSON: {@code {"type": "cosmicbreach:aetheria_terrain",
 * "seed_noise": "cosmicbreach:terrain_seed"}}; the noise only carries the world seed in.
 *
 * <p>Evaluated per block (not wrapped in {@code interpolated}): each worker thread keeps the 256 columns of
 * the chunk it is filling, so a column's 2D work is done once and each block costs a few comparisons.
 */
public final class TerrainDensity implements DensityFunction {
    public static final ResourceKey<NormalNoise.NoiseParameters> SEED_NOISE =
            ResourceKey.create(Registries.NOISE, CosmicBreach.id("terrain_seed"));

    public static final MapCodec<TerrainDensity> DATA_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NoiseHolder.CODEC.fieldOf("seed_noise").forGetter(TerrainDensity::seedNoise)
    ).apply(i, TerrainDensity::new));
    public static final KeyDispatchDataCodec<TerrainDensity> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

    private final NoiseHolder seedNoise;
    private final @Nullable AetheriaTerrain terrain;
    private final ThreadLocal<ChunkColumns> columns = ThreadLocal.withInitial(ChunkColumns::new);

    public TerrainDensity(NoiseHolder seedNoise) {
        this.seedNoise = seedNoise;
        this.terrain = seedNoise.noise() == null ? null : AetheriaTerrain.forSalt(AetheriaTerrain.saltOf(seedNoise.noise()));
    }

    public NoiseHolder seedNoise() {
        return seedNoise;
    }

    /** The 256 columns of the chunk this thread is filling. */
    private static final class ChunkColumns {
        long chunk = Long.MIN_VALUE;
        final AetheriaTerrain.Column[] columns = new AetheriaTerrain.Column[256];
        final boolean[] filled = new boolean[256];
    }

    @Override
    public double compute(FunctionContext context) {
        AetheriaTerrain t = terrain;
        int y = context.blockY();
        if (t == null || !AetheriaTerrain.mayHaveRock(y)) {
            return AetheriaTerrain.AIR;
        }
        int x = context.blockX();
        int z = context.blockZ();
        ChunkColumns cache = columns.get();
        long chunk = ((long) (x >> 4) << 32) ^ ((z >> 4) & 0xFFFFFFFFL);
        if (cache.chunk != chunk) {
            cache.chunk = chunk;
            Arrays.fill(cache.filled, false);
        }
        int index = ((x & 15) << 4) | (z & 15);
        AetheriaTerrain.Column column = cache.columns[index];
        if (column == null) {
            column = new AetheriaTerrain.Column();
            cache.columns[index] = column;
        }
        if (!cache.filled[index]) {
            t.sampleColumn(x, z, column);
            cache.filled[index] = true;
        }
        return t.density(column, x, y, z);
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        provider.fillAllDirectly(array, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        // Only the seed wiring changes the noise; every other visitor (one per chunk) gets this same
        // instance back, so the per-thread column caches are not rebuilt for each chunk.
        NoiseHolder wired = visitor.visitNoise(seedNoise);
        return visitor.apply(wired.noise() == seedNoise.noise() ? this : new TerrainDensity(wired));
    }

    @Override
    public double minValue() {
        return -1.0;
    }

    @Override
    public double maxValue() {
        return 1.0;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
