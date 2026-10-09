package com.cosmicbreach.world.gen;

import com.cosmicbreach.world.Layer;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.jetbrains.annotations.Nullable;

/**
 * Density function {@code cosmicbreach:deep_zone}: the Deep's zone at (x, z) ({@link DeepZones}) as a number, -0.75,
 * -0.25, 0.25 or 0.75 for zones 0 to 3 ({@link #value}, {@link #zoneOf}). It sits in the noise router's
 * {@code vegetation} slot, read by {@link AetheriaBiomeSource} so each zone gets its biome, the same field the terrain
 * was shaped with. Above the Deep's band it is 0 (no work). Keeps the last 16 columns per thread.
 */
public final class DeepZoneDensity implements DensityFunction {
    public static final MapCodec<DeepZoneDensity> DATA_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NoiseHolder.CODEC.fieldOf("seed_noise").forGetter(DeepZoneDensity::seedNoise)
    ).apply(i, DeepZoneDensity::new));
    public static final KeyDispatchDataCodec<DeepZoneDensity> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

    private final NoiseHolder seedNoise;
    private final @Nullable AetheriaTerrain terrain;
    private final ThreadLocal<long[]> keys = ThreadLocal.withInitial(() -> {
        long[] k = new long[16];
        Arrays.fill(k, Long.MIN_VALUE);
        return k;
    });
    private final ThreadLocal<double[]> values = ThreadLocal.withInitial(() -> new double[16]);

    public DeepZoneDensity(NoiseHolder seedNoise) {
        this.seedNoise = seedNoise;
        this.terrain = seedNoise.noise() == null ? null : AetheriaTerrain.forSalt(AetheriaTerrain.saltOf(seedNoise.noise()));
    }

    public NoiseHolder seedNoise() {
        return seedNoise;
    }

    /** The router value for a zone. */
    public static double value(int zone) {
        return (zone + 0.5) / DeepZones.COUNT * 2.0 - 1.0;
    }

    /** The zone a router value stands for (the inverse of {@link #value}, tolerant of quantizing). */
    public static int zoneOf(double value) {
        int z = (int) Math.floor((value + 1.0) / 2.0 * DeepZones.COUNT);
        return Math.max(0, Math.min(DeepZones.COUNT - 1, z));
    }

    @Override
    public double compute(FunctionContext context) {
        AetheriaTerrain t = terrain;
        if (t == null || context.blockY() >= Layer.DRIFT.bandMinY) {
            return 0.0;
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
        double value = value(t.zones.zoneAt(x + 0.5, z + 0.5));
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
        return visitor.apply(wired.noise() == seedNoise.noise() ? this : new DeepZoneDensity(wired));
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
