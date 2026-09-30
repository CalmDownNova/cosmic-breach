package com.cosmicbreach.datagen;

import com.cosmicbreach.block.StarbloomCropBlock;
import com.cosmicbreach.registry.ModBlocks;
import com.cosmicbreach.registry.ModBlocks.StoneSet;
import com.cosmicbreach.registry.ModMaterials;
import java.util.Set;
import net.minecraft.advancements.critereon.StatePropertiesPredicate;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.BonusLevelTableCondition;
import net.minecraft.world.level.storage.loot.predicates.ExplosionCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

/**
 * Block drops. Ores drop their raw metal (Fortune applies, Silk Touch gives the ore); a Meteorite drops
 * 1 to 2 raw Starsteel and, 5% of the time (more with Fortune), a Heartstone; Glimmer Grass gives
 * Starfall Stone unless mined with Silk Touch; plants follow their vanilla models.
 */
public final class ModBlockLootProvider extends BlockLootSubProvider {
    /** The Heartstone's chance from a Meteorite by Fortune level (0 to 3). */
    public static final float[] HEARTSTONE_CHANCE = {0.05f, 0.0625f, 0.0833f, 0.1f};

    public ModBlockLootProvider(HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return ModBlocks.BLOCKS.getEntries().stream().map(h -> (Block) h.get())::iterator;
    }

    @Override
    protected void generate() {
        for (Block b : new Block[] {
                ModBlocks.STARFALL_STONE.get(), ModBlocks.POLISHED_STARFALL_STONE.get(), ModBlocks.STARFALL_STONE_BRICKS.get(),
                ModBlocks.SPIRE_QUARTZ.get(), ModBlocks.STARSTEEL_BLOCK.get(), ModBlocks.STARBLOOM.get(), ModBlocks.BREACH_FRAME.get(),
                ModBlocks.DRIFTSTONE.get(), ModBlocks.DRIFTSTONE_BRICKS.get(), ModBlocks.NEBULITE_BLOCK.get(),
                ModBlocks.DRIFTWOOD_LOG.get(), ModBlocks.DRIFTWOOD_WOOD.get(), ModBlocks.STRIPPED_DRIFTWOOD_LOG.get(),
                ModBlocks.STRIPPED_DRIFTWOOD_WOOD.get(), ModBlocks.DRIFTWOOD_PLANKS.get(), ModBlocks.DRIFTWOOD_STAIRS.get(),
                ModBlocks.DRIFTWOOD_FENCE.get(), ModBlocks.DRIFTWOOD_FENCE_GATE.get(), ModBlocks.DRIFTWOOD_TRAPDOOR.get(),
                ModBlocks.DRIFTWOOD_PRESSURE_PLATE.get(), ModBlocks.DRIFTWOOD_BUTTON.get(), ModBlocks.RIMEGLASS.get(),
                ModBlocks.UMBRAL_BASALT.get(), ModBlocks.POLISHED_UMBRAL_BASALT.get(), ModBlocks.UMBRAL_BASALT_BRICKS.get(),
                ModBlocks.RIFT_GLASS.get(), ModBlocks.ECLIPSIUM_BLOCK.get()}) {
            dropSelf(b);
        }
        for (StoneSet set : ModBlocks.stoneSets()) {
            add(set.slab().get(), this::createSlabItemTable);
            dropSelf(set.stairs().get());
            if (set.wall() != null) {
                dropSelf(set.wall().get());
            }
        }
        add(ModBlocks.DRIFTWOOD_SLAB.get(), this::createSlabItemTable);
        add(ModBlocks.DRIFTWOOD_DOOR.get(), this::createDoorTable);

        add(ModBlocks.STARSTEEL_ORE.get(), b -> createOreDrop(b, ModMaterials.RAW_STARSTEEL.get()));
        add(ModBlocks.NEBULITE_ORE.get(), b -> createOreDrop(b, ModMaterials.RAW_NEBULITE.get()));
        add(ModBlocks.ECLIPSIUM_ORE.get(), b -> createOreDrop(b, ModMaterials.RAW_ECLIPSIUM.get()));
        add(ModBlocks.METEORITE.get(), this::meteorite);
        add(ModBlocks.GLIMMER_GRASS.get(), b -> createSingleItemTableWithSilkTouch(b, ModBlocks.STARFALL_STONE.get()));

        addNetherVinesDropTable(ModBlocks.HALO_MOSS.get(), ModBlocks.HALO_MOSS_PLANT.get());
        add(ModBlocks.MAGENTA_NEON_LICHEN.get(), b -> createMultifaceBlockDrops(b, HAS_SHEARS));
        add(ModBlocks.TEAL_NEON_LICHEN.get(), b -> createMultifaceBlockDrops(b, HAS_SHEARS));
        dropPottedContents(ModBlocks.POTTED_STARBLOOM.get());
        add(ModBlocks.STARBLOOM_CROP.get(), createCropDrops(ModBlocks.STARBLOOM_CROP.get(), ModBlocks.STARBLOOM.get().asItem(),
                ModBlocks.STARBLOOM_SEEDS.get(), LootItemBlockStatePropertyCondition.hasBlockStateProperties(ModBlocks.STARBLOOM_CROP.get())
                        .setProperties(StatePropertiesPredicate.Builder.properties()
                                .hasProperty(StarbloomCropBlock.AGE, StarbloomCropBlock.MAX_AGE))));
    }

    private LootTable.Builder meteorite(Block block) {
        Holder<Enchantment> fortune = registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE);
        return createSilkTouchDispatchTable(block, applyExplosionDecay(block, LootItem.lootTableItem(ModMaterials.RAW_STARSTEEL.get())
                        .apply(SetItemCountFunction.setCount(UniformGenerator.between(1f, 2f)))
                        .apply(ApplyBonusCount.addOreBonusCount(fortune))))
                .withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1f))
                        .add(LootItem.lootTableItem(ModMaterials.HEARTSTONE.get()))
                        .when(doesNotHaveSilkTouch())
                        .when(BonusLevelTableCondition.bonusLevelFlatChance(fortune, HEARTSTONE_CHANCE))
                        .when(ExplosionCondition.survivesExplosion()));
    }
}
