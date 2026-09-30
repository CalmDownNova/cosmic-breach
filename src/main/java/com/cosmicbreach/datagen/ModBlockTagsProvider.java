package com.cosmicbreach.datagen;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.registry.ModBlocks;
import com.cosmicbreach.registry.ModBlocks.StoneSet;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Block tags: which tool mines what and at which tier (the Reach's ores need a stone pickaxe, Nebulite
 * an iron one, Eclipsium a diamond one; vanilla's {@code incorrect_for_*_tool} tags read the
 * {@code needs_*_tool} ones), the vanilla family tags that make slabs, walls, fences, doors, flowers and
 * crops behave, and {@link #DRIFTWOOD_LOGS}.
 */
public final class ModBlockTagsProvider extends BlockTagsProvider {
    /** Every Driftwood log and wood, stripped or not: what planks are made from. */
    public static final TagKey<Block> DRIFTWOOD_LOGS = TagKey.create(Registries.BLOCK, CosmicBreach.id("driftwood_logs"));

    public ModBlockTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, @Nullable ExistingFileHelper files) {
        super(output, lookup, CosmicBreach.MOD_ID, files);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(com.cosmicbreach.gear.GearRegistry.ASTRAL_FORGE.get());
        com.cosmicbreach.guardian.GuardianRegistry.blockTags((key, blocks) -> tag(key).add(blocks));
        com.cosmicbreach.guardian.leviathan.LeviathanRegistry.blockTags((key, blocks) -> tag(key).add(blocks));
        com.cosmicbreach.datagen.StructureData.blockTags((key, blocks) -> tag(key).add(blocks));
        com.cosmicbreach.datagen.CryptData.blockTags((key, blocks) -> tag(key).add(blocks));
        com.cosmicbreach.guardian.unsung.UnsungRegistry.blockTags((key, blocks) -> tag(key).add(blocks));
        com.cosmicbreach.entity.stalker.Stalkers.blockTags((key, blocks) -> tag(key).add(blocks));
        com.cosmicbreach.structure.sanctum.SanctumRegistry.blockTags((key, blocks) -> tag(key).add(blocks));
        com.cosmicbreach.guardian.heliarch.HeliarchRegistry.blockTags((key, blocks) -> tag(key).add(blocks));
        com.cosmicbreach.familiar.FamiliarRegistry.blockTags((key, blocks) -> tag(key).add(blocks));
        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(com.cosmicbreach.structure.StructureRegistry.SUNSTONE.get(),
                com.cosmicbreach.structure.StructureRegistry.NEBULITE_LAMP.get(), com.cosmicbreach.structure.StructureRegistry.KINETIC_EMITTER.get());
        tag(BlockTags.NEEDS_IRON_TOOL).add(com.cosmicbreach.structure.StructureRegistry.KINETIC_EMITTER.get());
        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(
                ModBlocks.STARFALL_STONE.get(), ModBlocks.POLISHED_STARFALL_STONE.get(), ModBlocks.STARFALL_STONE_BRICKS.get(),
                ModBlocks.GLIMMER_GRASS.get(), ModBlocks.SPIRE_QUARTZ.get(), ModBlocks.STARSTEEL_ORE.get(),
                ModBlocks.STARSTEEL_BLOCK.get(), ModBlocks.METEORITE.get(), ModBlocks.BREACH_FRAME.get(),
                ModBlocks.DRIFTSTONE.get(), ModBlocks.DRIFTSTONE_BRICKS.get(), ModBlocks.NEBULITE_ORE.get(),
                ModBlocks.NEBULITE_BLOCK.get(), ModBlocks.RIMEGLASS.get(),
                ModBlocks.UMBRAL_BASALT.get(), ModBlocks.POLISHED_UMBRAL_BASALT.get(), ModBlocks.UMBRAL_BASALT_BRICKS.get(),
                ModBlocks.RIFT_GLASS.get(), ModBlocks.ECLIPSIUM_ORE.get(), ModBlocks.ECLIPSIUM_BLOCK.get());
        for (StoneSet set : ModBlocks.stoneSets()) {
            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(set.slab().get(), set.stairs().get());
            tag(BlockTags.SLABS).add(set.slab().get());
            tag(BlockTags.STAIRS).add(set.stairs().get());
            if (set.wall() != null) {
                tag(BlockTags.MINEABLE_WITH_PICKAXE).add(set.wall().get());
                tag(BlockTags.WALLS).add(set.wall().get());
            }
        }
        tag(BlockTags.NEEDS_STONE_TOOL).add(ModBlocks.STARSTEEL_ORE.get(), ModBlocks.STARSTEEL_BLOCK.get(),
                ModBlocks.METEORITE.get(), ModBlocks.BREACH_FRAME.get());
        tag(BlockTags.NEEDS_IRON_TOOL).add(ModBlocks.NEBULITE_ORE.get(), ModBlocks.NEBULITE_BLOCK.get());
        tag(BlockTags.NEEDS_DIAMOND_TOOL).add(ModBlocks.ECLIPSIUM_ORE.get(), ModBlocks.ECLIPSIUM_BLOCK.get());

        // Driftwood
        tag(DRIFTWOOD_LOGS).add(ModBlocks.DRIFTWOOD_LOG.get(), ModBlocks.DRIFTWOOD_WOOD.get(),
                ModBlocks.STRIPPED_DRIFTWOOD_LOG.get(), ModBlocks.STRIPPED_DRIFTWOOD_WOOD.get());
        tag(BlockTags.LOGS).addTag(DRIFTWOOD_LOGS);      // not logs_that_burn: it is petrified
        tag(BlockTags.PLANKS).add(ModBlocks.DRIFTWOOD_PLANKS.get());
        tag(BlockTags.WOODEN_SLABS).add(ModBlocks.DRIFTWOOD_SLAB.get());
        tag(BlockTags.WOODEN_STAIRS).add(ModBlocks.DRIFTWOOD_STAIRS.get());
        tag(BlockTags.WOODEN_FENCES).add(ModBlocks.DRIFTWOOD_FENCE.get());
        tag(BlockTags.FENCE_GATES).add(ModBlocks.DRIFTWOOD_FENCE_GATE.get());
        tag(BlockTags.WOODEN_DOORS).add(ModBlocks.DRIFTWOOD_DOOR.get());
        tag(BlockTags.WOODEN_TRAPDOORS).add(ModBlocks.DRIFTWOOD_TRAPDOOR.get());
        tag(BlockTags.WOODEN_BUTTONS).add(ModBlocks.DRIFTWOOD_BUTTON.get());
        tag(BlockTags.WOODEN_PRESSURE_PLATES).add(ModBlocks.DRIFTWOOD_PRESSURE_PLATE.get());
        tag(BlockTags.MINEABLE_WITH_AXE).addTag(DRIFTWOOD_LOGS).add(ModBlocks.DRIFTWOOD_PLANKS.get(),
                ModBlocks.DRIFTWOOD_SLAB.get(), ModBlocks.DRIFTWOOD_STAIRS.get(), ModBlocks.DRIFTWOOD_FENCE.get(),
                ModBlocks.DRIFTWOOD_FENCE_GATE.get(), ModBlocks.DRIFTWOOD_DOOR.get(), ModBlocks.DRIFTWOOD_TRAPDOOR.get(),
                ModBlocks.DRIFTWOOD_BUTTON.get(), ModBlocks.DRIFTWOOD_PRESSURE_PLATE.get());

        // plants, like their vanilla models (weeping vines, glow lichen, flowers, beetroots)
        tag(BlockTags.MINEABLE_WITH_AXE).add(ModBlocks.HALO_MOSS.get(), ModBlocks.HALO_MOSS_PLANT.get(),
                ModBlocks.MAGENTA_NEON_LICHEN.get(), ModBlocks.TEAL_NEON_LICHEN.get());
        tag(BlockTags.SWORD_EFFICIENT).add(ModBlocks.HALO_MOSS.get(), ModBlocks.HALO_MOSS_PLANT.get(),
                ModBlocks.MAGENTA_NEON_LICHEN.get(), ModBlocks.TEAL_NEON_LICHEN.get());
        tag(BlockTags.CLIMBABLE).add(ModBlocks.HALO_MOSS.get(), ModBlocks.HALO_MOSS_PLANT.get());
        tag(BlockTags.REPLACEABLE).add(ModBlocks.MAGENTA_NEON_LICHEN.get(), ModBlocks.TEAL_NEON_LICHEN.get());
        tag(BlockTags.SMALL_FLOWERS).add(ModBlocks.STARBLOOM.get());
        tag(BlockTags.FLOWER_POTS).add(ModBlocks.POTTED_STARBLOOM.get());
        tag(BlockTags.CROPS).add(ModBlocks.STARBLOOM_CROP.get());
        tag(BlockTags.MAINTAINS_FARMLAND).add(ModBlocks.STARBLOOM_CROP.get());

        // materials
        tag(BlockTags.CRYSTAL_SOUND_BLOCKS).add(ModBlocks.SPIRE_QUARTZ.get());
        tag(BlockTags.IMPERMEABLE).add(ModBlocks.RIMEGLASS.get(), ModBlocks.RIFT_GLASS.get());
        tag(BlockTags.BEACON_BASE_BLOCKS).add(ModBlocks.STARSTEEL_BLOCK.get(), ModBlocks.NEBULITE_BLOCK.get(),
                ModBlocks.ECLIPSIUM_BLOCK.get());
        tag(Tags.Blocks.ORES).add(ModBlocks.STARSTEEL_ORE.get(), ModBlocks.NEBULITE_ORE.get(), ModBlocks.ECLIPSIUM_ORE.get());
        tag(Tags.Blocks.STORAGE_BLOCKS).add(ModBlocks.STARSTEEL_BLOCK.get(), ModBlocks.NEBULITE_BLOCK.get(),
                ModBlocks.ECLIPSIUM_BLOCK.get());
    }
}
