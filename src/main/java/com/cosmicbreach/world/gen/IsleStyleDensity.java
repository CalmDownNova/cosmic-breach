package com.cosmicbreach.world.gen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.jetbrains.annotations.Nullable;

/**
 * Density function {@code cosmicbreach:isle_style}: +1 where the Reach's island at (x, z) is a Sunfield
 * Terraces island, -1 where it is Shattered Spires. It sits in the noise router's {@code temperature} slot,
 * the only seeded value a biome source can read, so {@link AetheriaBiomeSource} picks the same style the
 * terrain was shaped with. Keeps the last 16 columns per thread (biome filling asks column by column).
 */
public final class IsleStyleDensity implements DensityFunction {
    public static final MapCodec<IsleStyleDensity> DATA_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NoiseHolder.CODEC.fieldOf("seed_noise").forGetter(IsleStyleDensity::seedNoise)
    ).apply(i, IsleStyleDensity::new));
    public static final KeyDispatchDataCodec<IsleStyleDensity> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

    private final NoiseHolder seedNoise;
    private final @Nullable AetheriaTerrain terrain;
    private final ThreadLocal<long[]> keys = ThreadLocal.withInitial(() -> {
        long[] k = new long[16];
        java.util.Arrays.fill(k, Long.MIN_VALUE);
        return k;
    });
    private final ThreadLocal<double[]> values = ThreadLocal.withInitial(() -> new double[16]);

    public IsleStyleDensity(NoiseHolder seedNoise) {
        this.seedNoise = seedNoise;
        this.terrain = seedNoise.noise() == null ? null : AetheriaTerrain.forSalt(AetheriaTerrain.saltOf(seedNoise.noise()));
    }

    public NoiseHolder seedNoise() {
        return seedNoise;
    }

    @Override
    public double compute(FunctionContext context) {
        AetheriaTerrain t = terrain;
        if (t == null) {
            return -1;
        }
        int x = context.blockX();
        int z = context.blockZ();
        long key = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
        int slot = ((x >> 2) & 3) << 2 | ((z >> 2) & 3);
        long[] k = keys.get();
        double[] v = values.get();
        if (k[slot] == key) {
            return v[slot];
        }
        ReachIslands.Isle isle = t.reach.isleAt(x + 0.5, z + 0.5);
        double value = isle != null && isle.sunfield ? 1.0 : -1.0;
        k[slot] = key;
        v[slot] = value;
        return value;
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        provider.fillAllDirectly(array, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        NoiseHolder wired = visitor.visitNoise(seedNoise);
        return visitor.apply(wired.noise() == seedNoise.noise() ? this : new IsleStyleDensity(wired));
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
