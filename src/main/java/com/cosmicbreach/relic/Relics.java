package com.cosmicbreach.relic;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.item.CombatWeaponItem;
import com.cosmicbreach.registry.ModCreativeTab;
import com.cosmicbreach.relic.cantor.Cantor;
import com.cosmicbreach.relic.cantor.ChordField;
import com.cosmicbreach.relic.cantor.ResonantNote;
import com.cosmicbreach.relic.cantor.UmbraArrow;
import com.cosmicbreach.relic.crown.CrownBlockEntity;
import com.cosmicbreach.relic.crown.HeliarchsCrownBlock;
import com.cosmicbreach.relic.lastlight.LastLight;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Hollow Heliarch's unique rewards (GDD 7.3), G9b: <b>Last Light</b> ({@code cosmicbreach:last_light}, the solar
 * glaive, {@link LastLight}), the <b>Umbra Cantor</b> ({@code cosmicbreach:umbra_cantor}, the longbow, {@link Cantor})
 * and the <b>Heliarch's Crown</b> ({@code cosmicbreach:heliarchs_crown}, the trophy block). The Heliarch's Reliquary
 * (G9a) hands them out by these ids. The weapons' movesets are {@code data/cosmicbreach/combat/weapons/last_light.json}
 * and {@code umbra_cantor.json}; their sounds ({@code tools/sound/relics.py}) are registered here.
 */
public final class Relics {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CosmicBreach.MOD_ID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CosmicBreach.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CosmicBreach.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, CosmicBreach.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, CosmicBreach.MOD_ID);

    /** The solar glaive: T4, Resilience A, Power B. */
    public static final DeferredItem<CombatWeaponItem> LAST_LIGHT = ITEMS.registerItem("last_light", CombatWeaponItem::new,
            new Item.Properties().rarity(Rarity.EPIC).fireResistant());
    /** The longbow: T4, Agility A, Arcane C. */
    public static final DeferredItem<CombatWeaponItem> UMBRA_CANTOR = ITEMS.registerItem("umbra_cantor", CombatWeaponItem::new,
            new Item.Properties().rarity(Rarity.EPIC).fireResistant());

    /** The trophy: a crown on its pedestal, a small sun orbiting it, light 12. */
    public static final DeferredBlock<HeliarchsCrownBlock> HELIARCHS_CROWN = BLOCKS.registerBlock("heliarchs_crown",
            HeliarchsCrownBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(1.5f, 6.0f)
                    .sound(SoundType.METAL).lightLevel(state -> HeliarchsCrownBlock.LIGHT).noOcclusion()
                    .pushReaction(PushReaction.BLOCK));
    public static final DeferredItem<BlockItem> HELIARCHS_CROWN_ITEM = ITEMS.registerSimpleBlockItem(HELIARCHS_CROWN,
            new Item.Properties().rarity(Rarity.EPIC).fireResistant());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CrownBlockEntity>> CROWN = BLOCK_ENTITIES.register(
            "heliarchs_crown", () -> BlockEntityType.Builder.of(CrownBlockEntity::new, HELIARCHS_CROWN.get()).build(null));

    /** The Cantor's arrow: moved by the server, position sent every tick. */
    public static final DeferredHolder<EntityType<?>, EntityType<UmbraArrow>> UMBRA_ARROW = ENTITY_TYPES.register("umbra_arrow",
            () -> EntityType.Builder.<UmbraArrow>of(UmbraArrow::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .noSave()
                    .build(CosmicBreach.id("umbra_arrow").toString()));
    /** A resonant note where a charged shot landed. */
    public static final DeferredHolder<EntityType<?>, EntityType<ResonantNote>> RESONANT_NOTE = ENTITY_TYPES.register("resonant_note",
            () -> EntityType.Builder.<ResonantNote>of(ResonantNote::new, MobCategory.MISC)
                    .sized(0.4f, 0.4f)
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .noSave()
                    .build(CosmicBreach.id("resonant_note").toString()));
    /** Three notes' chord: the triangle between them. */
    public static final DeferredHolder<EntityType<?>, EntityType<ChordField>> CHORD = ENTITY_TYPES.register("chord",
            () -> EntityType.Builder.<ChordField>of(ChordField::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .noSave()
                    .build(CosmicBreach.id("chord").toString()));

    // Last Light's sounds
    public static final DeferredHolder<SoundEvent, SoundEvent> LL_THRUST = sound("lastlight/thrust");
    public static final DeferredHolder<SoundEvent, SoundEvent> LL_THRUST_HEAVY = sound("lastlight/thrust_heavy");
    public static final DeferredHolder<SoundEvent, SoundEvent> LL_HIT = sound("lastlight/hit");
    /** Loops while the Sunspear's charge is held; its pitch rises with the charge. */
    public static final DeferredHolder<SoundEvent, SoundEvent> LL_CHARGE = sound("lastlight/charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> LL_SUNSPEAR = sound("lastlight/sunspear");
    public static final DeferredHolder<SoundEvent, SoundEvent> LL_SUNFALL = sound("lastlight/sunfall");
    public static final DeferredHolder<SoundEvent, SoundEvent> LL_DAWNGUARD = sound("lastlight/dawnguard");
    /** The bell that tolls on every parry. */
    public static final DeferredHolder<SoundEvent, SoundEvent> LL_BELL = sound("lastlight/bell");
    public static final DeferredHolder<SoundEvent, SoundEvent> LL_DAYBREAK = sound("lastlight/daybreak");
    public static final DeferredHolder<SoundEvent, SoundEvent> LL_DAYBREAK_HIT = sound("lastlight/daybreak_hit");
    /** A Sunlight charge stored (pitched up by the count) and the charges spent. */
    public static final DeferredHolder<SoundEvent, SoundEvent> LL_SUNLIGHT = sound("lastlight/sunlight");
    // The Umbra Cantor's sounds
    public static final DeferredHolder<SoundEvent, SoundEvent> UC_LOOSE = sound("cantor/loose");
    public static final DeferredHolder<SoundEvent, SoundEvent> UC_LOOSE_HEAVY = sound("cantor/loose_heavy");
    /** Loops while the charged shot is drawn. */
    public static final DeferredHolder<SoundEvent, SoundEvent> UC_DRAW = sound("cantor/draw");
    public static final DeferredHolder<SoundEvent, SoundEvent> UC_FERMATA = sound("cantor/fermata");
    public static final DeferredHolder<SoundEvent, SoundEvent> UC_ARROW_HIT = sound("cantor/arrow_hit");
    /** One tone on D5 that the game pitches to each note of D major pentatonic. */
    public static final DeferredHolder<SoundEvent, SoundEvent> UC_NOTE = sound("cantor/note");
    /** The chord: a D major triad. */
    public static final DeferredHolder<SoundEvent, SoundEvent> UC_CHORD = sound("cantor/chord");
    public static final DeferredHolder<SoundEvent, SoundEvent> UC_PULSE = sound("cantor/pulse");
    public static final DeferredHolder<SoundEvent, SoundEvent> UC_CADENCE = sound("cantor/cadence");
    /** A silenced enemy's shot or spell fizzling out. */
    public static final DeferredHolder<SoundEvent, SoundEvent> UC_FIZZLE = sound("cantor/fizzle");

    private Relics() {
    }

    public static void register(IEventBus modBus, IEventBus game) {
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ENTITY_TYPES.register(modBus);
        SOUNDS.register(modBus);
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, event -> {
            if (event.getTabKey().equals(ModCreativeTab.COSMIC_BREACH.getKey())) {
                event.accept(LAST_LIGHT.get());
                event.accept(UMBRA_CANTOR.get());
                event.accept(HELIARCHS_CROWN_ITEM.get());
            }
        });
        LastLight.register(game);
        Cantor.register(game);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(CosmicBreach.id(name)));
    }
}
