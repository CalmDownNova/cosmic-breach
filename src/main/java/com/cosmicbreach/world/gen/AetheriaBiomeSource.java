package com.cosmicbreach.world.gen;

import com.cosmicbreach.world.Layer;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * Biome source {@code cosmicbreach:aetheria_layers}: the biome follows the height band first (GDD 2.1):
 * the Upper Reach from Y 300 up, the Drift Belt from Y 160 to 299, the Rift Abyss below. Within the Reach
 * each island is Shattered Spires or Sunfield Terraces, read from the router's temperature slot
 * ({@link IsleStyleDensity}), about three to one.
 */
public final class AetheriaBiomeSource extends BiomeSource {
    public static final MapCodec<AetheriaBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Biome.CODEC.fieldOf("shattered_spires").forGetter(s -> s.spires),
            Biome.CODEC.fieldOf("sunfield_terraces").forGetter(s -> s.sunfield),
            Biome.CODEC.fieldOf("drift_belt").forGetter(s -> s.drift),
            Biome.CODEC.fieldOf("rift_abyss").forGetter(s -> s.deep)
    ).apply(i, AetheriaBiomeSource::new));

    private final Holder<Biome> spires;
    private final Holder<Biome> sunfield;
    private final Holder<Biome> drift;
    private final Holder<Biome> deep;

    public AetheriaBiomeSource(Holder<Biome> spires, Holder<Biome> sunfield, Holder<Biome> drift, Holder<Biome> deep) {
        this.spires = spires;
        this.sunfield = sunfield;
        this.drift = drift;
        this.deep = deep;
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.of(spires, sunfield, drift, deep);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        int blockY = QuartPos.toBlock(y);
        if (blockY >= Layer.REACH.bandMinY) {
            return sampler.sample(x, y, z).temperature() > 0 ? sunfield : spires;
        }
        return blockY >= Layer.DRIFT.bandMinY ? drift : deep;
    }
}
