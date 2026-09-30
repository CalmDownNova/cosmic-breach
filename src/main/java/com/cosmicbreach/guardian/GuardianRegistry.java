package com.cosmicbreach.guardian;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.guardian.colossus.CrownCrystalBlock;
import com.cosmicbreach.guardian.colossus.CrownCrystalBlockEntity;
import com.cosmicbreach.guardian.colossus.CrownPillarBlock;
import com.cosmicbreach.guardian.colossus.CrownSpirePiece;
import com.cosmicbreach.guardian.colossus.CrownSpireStructure;
import com.cosmicbreach.guardian.colossus.LiftLightBlock;
import com.cosmicbreach.guardian.colossus.PrismColossus;
import com.cosmicbreach.guardian.colossus.PrismShard;
import com.cosmicbreach.registry.ModCreativeTab;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Everything the guardians register (GDD 7.2; the Prism Colossus design v1): the lair blocks (the Prism Altar, the
 * crown crystals, the pillar's stump, the lift's rising and falling light, the gilded bricks and the prism glass),
 * the Guardian Echo and the Heart of a Dying Star, the Colossus, its shards and the parts, the Crown Spire
 * structure and piece, and the sounds ({@code tools/sound/colossus.py}).
 */
public final class GuardianRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CosmicBreach.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CosmicBreach.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CosmicBreach.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, CosmicBreach.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, CosmicBreach.MOD_ID);
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, CosmicBreach.MOD_ID);
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, CosmicBreach.MOD_ID);

    // ------------------------------------------------------------------ blocks

    private static BlockBehaviour.Properties lair(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(-1.0f, 3_600_000.0f).noLootTable().sound(sound)
                .pushReaction(PushReaction.BLOCK);
    }

    public static final DeferredBlock<GuardianAltarBlock> PRISM_ALTAR = BLOCKS.registerBlock("prism_altar",
            p -> new GuardianAltarBlock("colossus", p), lair(MapColor.QUARTZ, SoundType.AMETHYST).noOcclusion().lightLevel(s -> 9));
    public static final DeferredBlock<CrownCrystalBlock> CROWN_CRYSTAL = BLOCKS.registerBlock("crown_crystal", CrownCrystalBlock::new,
            lair(MapColor.COLOR_CYAN, SoundType.AMETHYST).lightLevel(s -> s.getValue(CrownCrystalBlock.LIT) ? 14 : 6));
    public static final DeferredBlock<CrownPillarBlock> CROWN_PILLAR = BLOCKS.registerBlock("crown_pillar", CrownPillarBlock::new,
            lair(MapColor.COLOR_CYAN, SoundType.AMETHYST).lightLevel(s -> s.getValue(CrownPillarBlock.LIT) ? 10 : 2));
    public static final DeferredBlock<LiftLightBlock> RISING_LIGHT = BLOCKS.registerBlock("rising_light", LiftLightBlock::rising,
            lair(MapColor.GOLD, SoundType.AMETHYST).noCollission().noOcclusion().lightLevel(s -> 13)
                    .isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false));
    public static final DeferredBlock<LiftLightBlock> FALLING_LIGHT = BLOCKS.registerBlock("falling_light", LiftLightBlock::falling,
            lair(MapColor.COLOR_LIGHT_BLUE, SoundType.AMETHYST).noCollission().noOcclusion().lightLevel(s -> 11)
                    .isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false));
    public static final DeferredBlock<Block> GILDED_STARFALL_BRICKS = BLOCKS.registerSimpleBlock("gilded_starfall_bricks",
            BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(2.0f, 6.0f).requiresCorrectToolForDrops().sound(SoundType.STONE));
    /** The crown's pale stone: its rim and battlements, its points and the lift's gates (the Colossus owns turquoise). */
    public static final DeferredBlock<Block> CROWN_QUARTZ = BLOCKS.registerSimpleBlock("crown_quartz",
            BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(2.0f, 6.0f).requiresCorrectToolForDrops().sound(SoundType.CALCITE));
    public static final DeferredBlock<TransparentBlock> PRISM_GLASS = BLOCKS.registerBlock("prism_glass", TransparentBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(0.6f).sound(SoundType.GLASS).noOcclusion()
                    .lightLevel(s -> 3).isValidSpawn((s, l, p, t) -> false).isRedstoneConductor((s, l, p) -> false)
                    .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false));

    public static final Supplier<BlockEntityType<GuardianAltarBlockEntity>> ALTAR_ENTITY = BLOCK_ENTITIES.register("guardian_altar",
            () -> BlockEntityType.Builder.of(GuardianAltarBlockEntity::new, PRISM_ALTAR.get()).build(null));
    public static final Supplier<BlockEntityType<CrownCrystalBlockEntity>> CROWN_CRYSTAL_ENTITY = BLOCK_ENTITIES.register("crown_crystal",
            () -> BlockEntityType.Builder.of(CrownCrystalBlockEntity::new, CROWN_CRYSTAL.get()).build(null));

    // ------------------------------------------------------------------ items

    public static final DeferredItem<Item> GUARDIAN_ECHO = ITEMS.registerSimpleItem("guardian_echo",
            new Item.Properties().rarity(Rarity.RARE).stacksTo(16));
    /** The Heart of a Dying Star, a necklace (what it does: {@code accessory/}). */
    public static final DeferredItem<com.cosmicbreach.accessory.AccessoryItem> HEART_OF_A_DYING_STAR = ITEMS.registerItem("heart_of_a_dying_star",
            p -> new com.cosmicbreach.accessory.AccessoryItem(p, com.cosmicbreach.accessory.Accessory.HEART_OF_A_DYING_STAR),
            new Item.Properties().rarity(Rarity.EPIC).stacksTo(1));
    public static final DeferredItem<BlockItem> GILDED_STARFALL_BRICKS_ITEM = ITEMS.registerSimpleBlockItem(GILDED_STARFALL_BRICKS);
    public static final DeferredItem<BlockItem> PRISM_GLASS_ITEM = ITEMS.registerSimpleBlockItem(PRISM_GLASS);
    public static final DeferredItem<BlockItem> CROWN_QUARTZ_ITEM = ITEMS.registerSimpleBlockItem(CROWN_QUARTZ);

    // ------------------------------------------------------------------ entities

    public static final DeferredHolder<EntityType<?>, EntityType<PrismColossus>> PRISM_COLOSSUS = ENTITY_TYPES.register("prism_colossus",
            () -> EntityType.Builder.of(PrismColossus::new, MobCategory.MONSTER)
                    .sized(PrismColossus.WIDTH, PrismColossus.HEIGHT)
                    .eyeHeight((float) (com.cosmicbreach.guardian.colossus.CrownArena.EYE_HEIGHT - PrismColossus.STUMP))
                    .fireImmune()
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build(CosmicBreach.id("prism_colossus").toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<PrismShard>> PRISM_SHARD = ENTITY_TYPES.register("prism_shard",
            () -> EntityType.Builder.of(PrismShard::new, MobCategory.MONSTER)
                    .sized(1.2f, 1.2f)
                    .eyeHeight(0.85f)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .noSave()
                    .build(CosmicBreach.id("prism_shard").toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<GuardianPart>> GUARDIAN_PART = ENTITY_TYPES.register("guardian_part",
            () -> EntityType.Builder.<GuardianPart>of(GuardianPart::new, MobCategory.MISC)
                    .sized(1.0f, 1.0f)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .noSave()
                    .noSummon()
                    .build(CosmicBreach.id("guardian_part").toString()));

    // ------------------------------------------------------------------ the lair's structure

    public static final Supplier<StructureType<CrownSpireStructure>> CROWN_SPIRE_TYPE = STRUCTURE_TYPES.register("crown_spire",
            () -> () -> CrownSpireStructure.CODEC);
    public static final Supplier<StructurePieceType> CROWN_SPIRE_PIECE = STRUCTURE_PIECES.register("crown_spire",
            () -> (StructurePieceType.ContextlessType) CrownSpirePiece::new);

    // ------------------------------------------------------------------ sounds (tools/sound/colossus.py)

    public static final DeferredHolder<SoundEvent, SoundEvent> COLOSSUS_AWAKEN = sound("colossus/awaken");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLOSSUS_ROAR = sound("colossus/roar");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLOSSUS_GRIND = sound("colossus/grind");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLOSSUS_CRYSTAL_TURN = sound("colossus/crystal_turn");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLOSSUS_GLINT = sound("colossus/glint");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLOSSUS_SLAM = sound("colossus/slam");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLOSSUS_SWEEP = sound("colossus/sweep");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLOSSUS_CHARGE = sound("colossus/charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLOSSUS_BEAM = sound("colossus/beam");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLOSSUS_CORE_HIT = sound("colossus/core_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLOSSUS_BURST_TELL = sound("colossus/burst_tell");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLOSSUS_BURST = sound("colossus/burst");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLOSSUS_BREAK = sound("colossus/break");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLOSSUS_FRACTURE = sound("colossus/fracture");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLOSSUS_SHATTER = sound("colossus/shatter");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLOSSUS_REFORM = sound("colossus/reform");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLOSSUS_DEATH = sound("colossus/death");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLOSSUS_HURT = sound("colossus/hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHARD_CHITTER = sound("colossus/shard_chitter");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHARD_TELL = sound("colossus/shard_tell");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHARD_LUNGE = sound("colossus/shard_lunge");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHARD_HURT = sound("colossus/shard_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHARD_DEATH = sound("colossus/shard_death");
    public static final DeferredHolder<SoundEvent, SoundEvent> ALTAR_ECHO = sound("colossus/echo");
    /** The boss loop, 100 BPM, retriggered on the Vesper clock's bars by the client. */
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_COLOSSUS = sound("music/colossus");

    private GuardianRegistry() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ENTITY_TYPES.register(modBus);
        SOUNDS.register(modBus);
        STRUCTURE_TYPES.register(modBus);
        STRUCTURE_PIECES.register(modBus);
        modBus.addListener(EntityAttributeCreationEvent.class, event -> {
            event.put(PRISM_COLOSSUS.get(), PrismColossus.createAttributes().build());
            event.put(PRISM_SHARD.get(), PrismShard.createAttributes().build());
            event.put(GUARDIAN_PART.get(), GuardianPart.createAttributes().build());
        });
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, GuardianRegistry::fillCreativeTab);
    }

    private static void fillCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModCreativeTab.COSMIC_BREACH.getKey())) {
            event.accept(GUARDIAN_ECHO.get());
            event.accept(HEART_OF_A_DYING_STAR.get());
            event.accept(GILDED_STARFALL_BRICKS_ITEM.get());
            event.accept(PRISM_GLASS_ITEM.get());
            event.accept(CROWN_QUARTZ_ITEM.get());
        }
    }

    /** The lair blocks' tags, for the data run: the bricks and the quartz mine with a pickaxe; the lair's heart is wither- and dragon-proof. */
    public static void blockTags(java.util.function.BiConsumer<net.minecraft.tags.TagKey<Block>, Block[]> tag) {
        tag.accept(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE, new Block[] {GILDED_STARFALL_BRICKS.get(), CROWN_QUARTZ.get()});
        Block[] lair = {PRISM_ALTAR.get(), CROWN_CRYSTAL.get(), CROWN_PILLAR.get(), RISING_LIGHT.get(), FALLING_LIGHT.get()};
        tag.accept(net.minecraft.tags.BlockTags.WITHER_IMMUNE, lair);
        tag.accept(net.minecraft.tags.BlockTags.DRAGON_IMMUNE, lair);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(CosmicBreach.id(name)));
    }
}
