package com.cosmicbreach.gear.datagen;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.gear.GearRegistry;
import com.cosmicbreach.gear.forge.AstralForgeBlock;
import com.cosmicbreach.gear.set.SetArmorItem;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.CopyBlockState;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * The gear system's part of the data run: the Forge's block state, item models (the set pieces with their
 * trim layer), the Forge's loot table and every recipe ({@link GearRecipeProvider}). Names are hand-written in
 * {@code assets/cosmicbreach_gear/lang/en_us.json}.
 */
public final class GearDataGen {
    private GearDataGen() {
    }

    public static void gather(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        ExistingFileHelper files = event.getExistingFileHelper();
        CompletableFuture<HolderLookup.Provider> lookup = event.getLookupProvider();
        generator.addProvider(event.includeClient(), new Models(output, files));
        generator.addProvider(event.includeServer(), named("Gear loot tables", new LootTableProvider(output, Set.of(),
                List.of(new LootTableProvider.SubProviderEntry(Loot::new, LootContextParamSets.BLOCK)), lookup)));
        generator.addProvider(event.includeServer(), named("Gear recipes", new GearRecipeProvider(output, lookup)));
    }

    /** Vanilla's recipe and loot providers have final names; the data run refuses two of one name, so wrap them. */
    private static DataProvider named(String name, DataProvider inner) {
        return new DataProvider() {
            @Override
            public CompletableFuture<?> run(CachedOutput output) {
                return inner.run(output);
            }

            @Override
            public String getName() {
                return name;
            }
        };
    }

    /** The Forge's block state (turned by its facing, the same model at every tier) and the gear's item models. */
    static final class Models extends BlockStateProvider {
        Models(PackOutput output, ExistingFileHelper files) {
            super(output, CosmicBreach.MOD_ID, files);
        }

        @Override
        public String getName() {
            return "Gear block states and models";
        }

        @Override
        protected void registerStatesAndModels() {
            ModelFile forge = models().getExistingFile(modLoc("block/astral_forge"));
            getVariantBuilder(GearRegistry.ASTRAL_FORGE.get()).forAllStates(state -> ConfiguredModel.builder()
                    .modelFile(forge)
                    .rotationY(((int) state.getValue(AstralForgeBlock.FACING).toYRot() + 180) % 360)
                    .build());
            itemModels().getBuilder("astral_forge").parent(forge);
            List<DeferredItem<SetArmorItem>> pieces = new java.util.ArrayList<>(GearRegistry.VANGUARD.all());
            com.cosmicbreach.gear.GearSets.sets().forEach(set -> pieces.addAll(set.all()));
            for (DeferredItem<SetArmorItem> piece : pieces) {
                String name = BuiltInRegistries.ITEM.getKey(piece.get()).getPath();
                itemModels().withExistingParent(name, mcLoc("item/generated"))
                        .texture("layer0", modLoc("item/" + name))
                        .texture("layer1", modLoc("item/" + name + "_trim"));
            }
        }
    }

    /** The Forge drops itself and keeps its tier: the item carries the block state, and placing it puts it back. */
    static final class Loot extends BlockLootSubProvider {
        Loot(HolderLookup.Provider registries) {
            super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return GearRegistry.BLOCKS.getEntries().stream().map(h -> (Block) h.get())::iterator;
        }

        @Override
        protected void generate() {
            Block forge = GearRegistry.ASTRAL_FORGE.get();
            add(forge, LootTable.lootTable().withPool(applyExplosionCondition(forge, LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1))
                    .add(LootItem.lootTableItem(forge).apply(CopyBlockState.copyState(forge).copy(AstralForgeBlock.TIER))))));
        }
    }
}
