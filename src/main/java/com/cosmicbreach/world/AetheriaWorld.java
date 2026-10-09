package com.cosmicbreach.world;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.world.feature.AsteroidDecorFeature;
import com.cosmicbreach.world.feature.CrystalChandelierFeature;
import com.cosmicbreach.world.feature.CrystalStalactiteFeature;
import com.cosmicbreach.world.feature.QuartzOutcropFeature;
import com.cosmicbreach.world.feature.SpireFieldFeature;
import com.cosmicbreach.world.feature.TerracePoolFeature;
import com.cosmicbreach.world.gen.AetheriaBiomeSource;
import com.cosmicbreach.world.gen.IsleStyleDensity;
import com.cosmicbreach.world.gen.TerrainDensity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

/**
 * Aetheria, the dimension (GDD 2.1 to 2.5): its keys, the generator's Java parts (density functions, biome
 * source, features) and the rules that run in it (Shear bands, Drift gravity, natural spawns, debug
 * commands). The dimension itself is data: {@code data/cosmicbreach/dimension/aetheria.json} with its
 * type, noise settings, biomes and features under {@code data/cosmicbreach/}.
 */
public final class AetheriaWorld {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, CosmicBreach.id("aetheria"));
    public static final ResourceKey<DimensionType> TYPE = ResourceKey.create(Registries.DIMENSION_TYPE, CosmicBreach.id("aetheria"));
    /** The dimension type's effects id, for the sky renderer (W3 registers the DimensionSpecialEffects). */
    public static final ResourceLocation EFFECTS = CosmicBreach.id("aetheria");

    public static final ResourceKey<Biome> SHATTERED_SPIRES = biome("shattered_spires");
    public static final ResourceKey<Biome> SUNFIELD_TERRACES = biome("sunfield_terraces");
    public static final ResourceKey<Biome> DRIFT_BELT = biome("drift_belt");
    public static final ResourceKey<Biome> RIFT_ABYSS = biome("rift_abyss");
    public static final ResourceKey<Biome> LICHEN_GARDENS = biome("lichen_gardens");
    public static final ResourceKey<Biome> HANGING_WOOD = biome("hanging_wood");
    public static final ResourceKey<Biome> SHATTERED_FIELD = biome("shattered_field");

    public static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTION_TYPES =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, CosmicBreach.MOD_ID);
    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, CosmicBreach.MOD_ID);
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, CosmicBreach.MOD_ID);

    static {
        DENSITY_FUNCTION_TYPES.register("aetheria_terrain", () -> TerrainDensity.DATA_CODEC);
        DENSITY_FUNCTION_TYPES.register("isle_style", () -> IsleStyleDensity.DATA_CODEC);
        DENSITY_FUNCTION_TYPES.register("deep_zone", () -> com.cosmicbreach.world.gen.DeepZoneDensity.DATA_CODEC);
        BIOME_SOURCES.register("aetheria_layers", () -> AetheriaBiomeSource.CODEC);
    }

    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> SPIRE_FIELD =
            FEATURES.register("spire_field", SpireFieldFeature::new);
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> CRYSTAL_STALACTITE =
            FEATURES.register("crystal_stalactite", CrystalStalactiteFeature::new);
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> QUARTZ_OUTCROP =
            FEATURES.register("quartz_outcrop", QuartzOutcropFeature::new);
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> TERRACE_POOL =
            FEATURES.register("terrace_pool", TerracePoolFeature::new);
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> ASTEROID_DECOR =
            FEATURES.register("asteroid_decor", AsteroidDecorFeature::new);
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> CRYSTAL_CHANDELIER =
            FEATURES.register("crystal_chandelier", CrystalChandelierFeature::new);
    // the layer 3 zones (Aetheria 1.2)
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> GIANT_UMBRAL_CAP =
            FEATURES.register("giant_umbral_cap", com.cosmicbreach.world.feature.GiantUmbralCapFeature::new);
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> LICHEN_CARPET =
            FEATURES.register("lichen_carpet", com.cosmicbreach.world.feature.LichenCarpetFeature::new);
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> UNDERSIDE_ORE =
            FEATURES.register("underside_ore", com.cosmicbreach.world.feature.UndersideOreFeature::new);
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> TEAL_CURTAIN_SHEET =
            FEATURES.register("teal_curtain_sheet", com.cosmicbreach.world.feature.TealCurtainSheetFeature::new);
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> RIFT_GLASS_SHARDS =
            FEATURES.register("rift_glass_shards", com.cosmicbreach.world.feature.RiftGlassShardsFeature::new);

    private AetheriaWorld() {
    }

    public static void register(IEventBus modBus, IEventBus game) {
        DENSITY_FUNCTION_TYPES.register(modBus);
        BIOME_SOURCES.register(modBus);
        FEATURES.register(modBus);
        com.cosmicbreach.world.feature.ZoneBlocks.register(modBus);
        AetheriaRules.register(modBus, game);
    }

    /** True in Aetheria (either side). */
    public static boolean is(@Nullable Level level) {
        return level != null && level.dimension() == LEVEL;
    }

    private static ResourceKey<Biome> biome(String name) {
        return ResourceKey.create(Registries.BIOME, CosmicBreach.id(name));
    }
}
