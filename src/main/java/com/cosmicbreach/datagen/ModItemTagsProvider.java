package com.cosmicbreach.datagen;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.registry.ModBlocks;
import com.cosmicbreach.registry.ModMaterials;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.Nullable;

/** Item tags: the block tags' item halves, the common metal tags, seeds and beacon payment. */
public final class ModItemTagsProvider extends ItemTagsProvider {
    public static final TagKey<Item> DRIFTWOOD_LOGS = TagKey.create(Registries.ITEM, CosmicBreach.id("driftwood_logs"));

    public ModItemTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup,
            CompletableFuture<TagsProvider.TagLookup<Block>> blockTags, @Nullable ExistingFileHelper files) {
        super(output, lookup, blockTags, CosmicBreach.MOD_ID, files);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        copy(ModBlockTagsProvider.DRIFTWOOD_LOGS, DRIFTWOOD_LOGS);
        copy(BlockTags.LOGS, ItemTags.LOGS);
        copy(BlockTags.PLANKS, ItemTags.PLANKS);
        copy(BlockTags.WOODEN_SLABS, ItemTags.WOODEN_SLABS);
        copy(BlockTags.WOODEN_STAIRS, ItemTags.WOODEN_STAIRS);
        copy(BlockTags.WOODEN_FENCES, ItemTags.WOODEN_FENCES);
        copy(BlockTags.FENCE_GATES, ItemTags.FENCE_GATES);
        copy(BlockTags.WOODEN_DOORS, ItemTags.WOODEN_DOORS);
        copy(BlockTags.WOODEN_TRAPDOORS, ItemTags.WOODEN_TRAPDOORS);
        copy(BlockTags.WOODEN_BUTTONS, ItemTags.WOODEN_BUTTONS);
        copy(BlockTags.WOODEN_PRESSURE_PLATES, ItemTags.WOODEN_PRESSURE_PLATES);
        copy(BlockTags.SLABS, ItemTags.SLABS);
        copy(BlockTags.STAIRS, ItemTags.STAIRS);
        copy(BlockTags.WALLS, ItemTags.WALLS);
        copy(BlockTags.SMALL_FLOWERS, ItemTags.SMALL_FLOWERS);
        copy(Tags.Blocks.ORES, Tags.Items.ORES);
        copy(Tags.Blocks.STORAGE_BLOCKS, Tags.Items.STORAGE_BLOCKS);

        tag(Tags.Items.RAW_MATERIALS).add(ModMaterials.RAW_STARSTEEL.get(), ModMaterials.RAW_NEBULITE.get(),
                ModMaterials.RAW_ECLIPSIUM.get());
        tag(Tags.Items.INGOTS).add(ModMaterials.STARSTEEL_INGOT.get(), ModMaterials.NEBULITE_INGOT.get(),
                ModMaterials.ECLIPSIUM_INGOT.get());
        tag(Tags.Items.NUGGETS).add(ModMaterials.STARSTEEL_NUGGET.get(), ModMaterials.ECLIPSIUM_NUGGET.get());
        tag(Tags.Items.GEMS).add(ModMaterials.HEARTSTONE.get());
        tag(ItemTags.BEACON_PAYMENT_ITEMS).add(ModMaterials.STARSTEEL_INGOT.get(), ModMaterials.NEBULITE_INGOT.get(),
                ModMaterials.ECLIPSIUM_INGOT.get());
        tag(ItemTags.VILLAGER_PLANTABLE_SEEDS).add(ModBlocks.STARBLOOM_SEEDS.get());
        tag(ItemTags.CHICKEN_FOOD).add(ModBlocks.STARBLOOM_SEEDS.get());
        com.cosmicbreach.provision.ProvisionRegistry.itemTags((key, items) -> tag(key).add(items), (key, sub) -> tag(key).addTag(sub));
    }
}
