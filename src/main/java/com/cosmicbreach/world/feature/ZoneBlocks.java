package com.cosmicbreach.world.feature;

import com.cosmicbreach.CosmicBreach;
import java.util.function.BiConsumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The blocks the layer 3 zones are made of and grow (Aetheria 1.2). Each zone has its own ground: the Lichen Gardens a
 * violet moss over the basalt, the Hanging Wood a pale rootstone, the Shattered Field an ember-veined basalt, the Spans
 * a lighter ashen basalt over most of the umbral; the Shattered Field's undersides glow with rift ember. Then the giant
 * umbral caps (a glowing violet cap on a pale stem) and the Hanging Wood's teal curtains.
 *
 * <p>World blocks only, with no items of their own: the stones drop Umbral Basalt (with a pickaxe), a cap sometimes an
 * Umbral Cap, a stem and a curtain nothing. Block states, models, loot tables and names are written by hand under
 * {@code src/main/resources} (textures: {@code tools/art/gen_blocks_zones.py}); their tool tags come from
 * {@link #blockTags} through the data run.
 */
public final class ZoneBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CosmicBreach.MOD_ID);
    /** Every stone the Deep's terrain is made of: what the Deep's features grow on. */
    public static final TagKey<Block> DEEP_STONE = TagKey.create(Registries.BLOCK, CosmicBreach.id("deep_stone"));

    public static final DeferredBlock<Block> LICHEN_MOSS = BLOCKS.registerBlock("lichen_moss", Block::new,
            stone(MapColor.COLOR_PURPLE, SoundType.NYLIUM).lightLevel(s -> 3));
    public static final DeferredBlock<Block> ROOTSTONE = BLOCKS.registerBlock("rootstone", Block::new,
            stone(MapColor.TERRACOTTA_WHITE, SoundType.BONE_BLOCK));
    public static final DeferredBlock<Block> EMBER_BASALT = BLOCKS.registerBlock("ember_basalt", Block::new,
            stone(MapColor.COLOR_MAGENTA, SoundType.BASALT).lightLevel(s -> 4));
    /** The Shattered Field's glowing seams, on its chunks' undersides. */
    public static final DeferredBlock<Block> RIFT_EMBER = BLOCKS.registerBlock("rift_ember", Block::new,
            stone(MapColor.COLOR_MAGENTA, SoundType.AMETHYST).lightLevel(s -> 11));
    public static final DeferredBlock<Block> ASHEN_BASALT = BLOCKS.registerBlock("ashen_basalt", Block::new,
            stone(MapColor.COLOR_GRAY, SoundType.BASALT));
    public static final DeferredBlock<Block> GIANT_UMBRAL_CAP = BLOCKS.registerBlock("giant_umbral_cap", Block::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(0.3f).sound(SoundType.FUNGUS).lightLevel(s -> 9));
    public static final DeferredBlock<RotatedPillarBlock> UMBRAL_STEM = BLOCKS.registerBlock("umbral_stem", RotatedPillarBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).strength(0.4f).sound(SoundType.STEM));
    public static final DeferredBlock<TealCurtainBlock> TEAL_CURTAIN = BLOCKS.registerBlock("teal_curtain", TealCurtainBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).noCollission().instabreak().sound(SoundType.WEEPING_VINES)
                    .lightLevel(s -> 8).noOcclusion().replaceable().pushReaction(PushReaction.DESTROY));

    private ZoneBlocks() {
    }

    private static BlockBehaviour.Properties stone(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(2.5f, 6f).requiresCorrectToolForDrops().sound(sound);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }

    /** The zone blocks' tool tags, for the data run ({@code ModBlockTagsProvider}). */
    public static void blockTags(BiConsumer<TagKey<Block>, Block[]> tag) {
        tag.accept(BlockTags.MINEABLE_WITH_PICKAXE, new Block[] {LICHEN_MOSS.get(), ROOTSTONE.get(), EMBER_BASALT.get(), ASHEN_BASALT.get(),
                RIFT_EMBER.get()});
        tag.accept(BlockTags.MINEABLE_WITH_AXE, new Block[] {UMBRAL_STEM.get()});
    }
}
