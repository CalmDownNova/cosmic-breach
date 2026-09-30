package com.cosmicbreach.datagen;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.onboarding.OnboardingRegistry;
import com.cosmicbreach.onboarding.Starfalls;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.Nullable;

/**
 * The way in's generated files (W4): the shard's and the Breach's block states and models, the Codex, torn
 * page and shard item models, the shard's drop, and the tag of what a Starfall crater may dig. The Breach
 * Frame's lit variant stays with the other W1 blocks in {@link ModModelProvider}. Provider names are their
 * own so the data run can hold them next to the W1 providers.
 */
public final class OnboardingData {
    private OnboardingData() {
    }

    /** Block states and models. */
    public static final class Models extends BlockStateProvider {
        public Models(PackOutput output, ExistingFileHelper files) {
            super(output, CosmicBreach.MOD_ID, files);
        }

        @Override
        protected void registerStatesAndModels() {
            simpleBlock(OnboardingRegistry.STARFALL_SHARD_BLOCK.get(),
                    models().cross("starfall_shard", modLoc("block/starfall_shard")).renderType("cutout"));
            simpleBlock(OnboardingRegistry.BREACH.get(),
                    models().getBuilder("breach").texture("particle", modLoc("block/breach_frame_top_active")));
            itemModels().basicItem(OnboardingRegistry.STARFALL_SHARD.get());
            itemModels().basicItem(OnboardingRegistry.STARFALL_CODEX.get());
            itemModels().basicItem(OnboardingRegistry.TORN_CODEX_PAGE.get());
        }

        @Override
        public String getName() {
            return "Block States: " + CosmicBreach.MOD_ID + " onboarding";
        }
    }

    /** The shard drops itself as the Starfall Shard item; the Breach drops nothing. */
    public static final class Loot extends BlockLootSubProvider {
        public Loot(HolderLookup.Provider registries) {
            super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return List.of(OnboardingRegistry.STARFALL_SHARD_BLOCK.get(), OnboardingRegistry.BREACH.get());
        }

        @Override
        protected void generate() {
            add(OnboardingRegistry.STARFALL_SHARD_BLOCK.get(), createSingleItemTable(OnboardingRegistry.STARFALL_SHARD.get()));
        }
    }

    /** What a Starfall crater may dig: natural ground only, never anything built. */
    public static final class BlockTagsData extends BlockTagsProvider {
        public BlockTagsData(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, @Nullable ExistingFileHelper files) {
            super(output, lookup, CosmicBreach.MOD_ID, files);
        }

        @Override
        protected void addTags(HolderLookup.Provider provider) {
            tag(Starfalls.CRATER_REPLACEABLE)
                    .addTag(BlockTags.DIRT).addTag(BlockTags.SAND).addTag(BlockTags.BASE_STONE_OVERWORLD)
                    .addTag(BlockTags.TERRACOTTA).addTag(BlockTags.SNOW)
                    .add(Blocks.GRAVEL, Blocks.CLAY, Blocks.SANDSTONE, Blocks.RED_SANDSTONE, Blocks.CALCITE,
                            Blocks.DRIPSTONE_BLOCK, Blocks.SMOOTH_BASALT);
        }

        @Override
        public String getName() {
            return "Block Tags: " + CosmicBreach.MOD_ID + " onboarding";
        }
    }
}
