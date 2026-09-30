package com.cosmicbreach.guardian.unsung;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.guardian.GuardianRegistry;
import com.cosmicbreach.registry.ModCreativeTab;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Everything the Unsung and its lair register (Unsung design v1): the Hymnal Altar (the guardian framework's altar
 * block entity, which it joins through {@link BlockEntityTypeAddBlocksEvent}), the lichen-lit windows the awakening
 * dims, the circles of silence, the Choir Pendant, the Unsung, its masks and their notes, Silence, the Silent Nave's
 * structure and piece, and the sounds ({@code tools/sound/unsung.py}): effects plus the choir's music, one 8-beat
 * block per voice, way of singing and chord.
 */
public final class UnsungRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CosmicBreach.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CosmicBreach.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, CosmicBreach.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, CosmicBreach.MOD_ID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, CosmicBreach.MOD_ID);
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, CosmicBreach.MOD_ID);
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, CosmicBreach.MOD_ID);
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, CosmicBreach.MOD_ID);

    /**
     * The Rift Abyss's ambient mote (its biome's particle), the Silent Nave's air: a faint soft glow drifting in the
     * dark. It replaced vanilla's warped spore, which the game tints near black navy, so it showed as dark squares.
     */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ABYSS_MOTE = PARTICLES.register("abyss_mote",
            () -> new SimpleParticleType(false));

    public static final ResourceKey<Structure> SILENT_NAVE = ResourceKey.create(Registries.STRUCTURE, CosmicBreach.id("silent_nave"));
    public static final ResourceKey<DamageType> NOTE_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, CosmicBreach.id("unsung_note"));
    public static final ResourceKey<DamageType> HARMONIZE_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, CosmicBreach.id("unsung_harmonize"));
    /**
     * What the Unsung's boss bar names as its music. Not a sound event (the framework's single loop ignores it): the
     * client's {@code UnsungMusic} plays the choir's blocks while a bar names it.
     */
    public static final ResourceLocation MUSIC_MARKER = CosmicBreach.id("unsung_choir");

    // ------------------------------------------------------------------ blocks

    private static BlockBehaviour.Properties lair(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(-1.0f, 3_600_000.0f).noLootTable().sound(sound)
                .pushReaction(PushReaction.BLOCK).isValidSpawn((s, l, p, t) -> false);
    }

    public static final DeferredBlock<HymnalAltarBlock> HYMNAL_ALTAR = BLOCKS.registerBlock("hymnal_altar", HymnalAltarBlock::new,
            lair(MapColor.COLOR_BLACK, SoundType.POLISHED_DEEPSLATE).noOcclusion().lightLevel(s -> 3));
    public static final DeferredBlock<LichenWindowBlock> LICHEN_WINDOW = BLOCKS.registerBlock("lichen_window", LichenWindowBlock::new,
            lair(MapColor.COLOR_PURPLE, SoundType.GLASS).lightLevel(s -> s.getValue(LichenWindowBlock.LIT) ? 7 : 1));
    public static final DeferredBlock<SilenceCircleBlock> SILENCE_CIRCLE = BLOCKS.registerBlock("silence_circle", SilenceCircleBlock::new,
            lair(MapColor.COLOR_PURPLE, SoundType.GLASS).lightLevel(s -> s.getValue(SilenceCircleBlock.LIT) ? 8 : 0));

    // ------------------------------------------------------------------ items

    /** The Choir Pendant, a necklace (what it does: {@code accessory/}). */
    public static final DeferredItem<com.cosmicbreach.accessory.AccessoryItem> CHOIR_PENDANT = ITEMS.registerItem("choir_pendant",
            p -> new com.cosmicbreach.accessory.AccessoryItem(p, com.cosmicbreach.accessory.Accessory.CHOIR_PENDANT),
            new Item.Properties().rarity(Rarity.EPIC).stacksTo(1));

    // ------------------------------------------------------------------ entities

    public static final DeferredHolder<EntityType<?>, EntityType<Unsung>> UNSUNG = ENTITY_TYPES.register("unsung",
            () -> EntityType.Builder.<Unsung>of(Unsung::new, MobCategory.MISC)
                    .sized(0.6f, 0.6f)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build(CosmicBreach.id("unsung").toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<UnsungMask>> UNSUNG_MASK = ENTITY_TYPES.register("unsung_mask",
            () -> EntityType.Builder.<UnsungMask>of(UnsungMask::new, MobCategory.MISC)
                    .sized(UnsungMoves.MASK_WIDTH, UnsungMoves.MASK_HEIGHT)
                    .eyeHeight((float) UnsungMoves.FACE_UP)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .noSave()
                    .noSummon()
                    .build(CosmicBreach.id("unsung_mask").toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<SongNote>> SONG_NOTE = ENTITY_TYPES.register("song_note",
            () -> EntityType.Builder.<SongNote>of(SongNote::new, MobCategory.MISC)
                    .sized(0.7f, 0.7f)
                    .fireImmune()
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .noSave()
                    .noSummon()
                    .build(CosmicBreach.id("song_note").toString()));

    // ------------------------------------------------------------------ Silence

    public static final DeferredHolder<MobEffect, Silenced> SILENCED = EFFECTS.register("silenced", Silenced::new);

    // ------------------------------------------------------------------ the lair's structure

    public static final Supplier<StructureType<SilentNaveStructure>> SILENT_NAVE_TYPE = STRUCTURE_TYPES.register("silent_nave",
            () -> () -> SilentNaveStructure.CODEC);
    public static final Supplier<StructurePieceType> SILENT_NAVE_PIECE = STRUCTURE_PIECES.register("silent_nave",
            () -> (StructurePieceType.ContextlessType) NavePiece::new);

    // ------------------------------------------------------------------ sounds (tools/sound/unsung.py)

    public static final DeferredHolder<SoundEvent, SoundEvent> AWAKEN = sound("unsung/awaken");
    public static final DeferredHolder<SoundEvent, SoundEvent> HYMNAL = sound("unsung/hymnal");
    public static final DeferredHolder<SoundEvent, SoundEvent> DIM = sound("unsung/dim");
    public static final DeferredHolder<SoundEvent, SoundEvent> FIRST_ALTO = sound("unsung/first_alto");
    public static final DeferredHolder<SoundEvent, SoundEvent> FIRST_TENOR = sound("unsung/first_tenor");
    public static final DeferredHolder<SoundEvent, SoundEvent> FIRST_BASS = sound("unsung/first_bass");
    public static final DeferredHolder<SoundEvent, SoundEvent> NOTE_FORM = sound("unsung/note_form");
    public static final DeferredHolder<SoundEvent, SoundEvent> NOTE_BURST = sound("unsung/note_burst");
    public static final DeferredHolder<SoundEvent, SoundEvent> NOTE_BREAK = sound("unsung/note_break");
    public static final DeferredHolder<SoundEvent, SoundEvent> INHALE = sound("unsung/inhale");
    public static final DeferredHolder<SoundEvent, SoundEvent> WAVE = sound("unsung/wave");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIPPLE_TELL = sound("unsung/ripple_tell");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIPPLE = sound("unsung/ripple");
    public static final DeferredHolder<SoundEvent, SoundEvent> DROP_RISE = sound("unsung/drop_rise");
    public static final DeferredHolder<SoundEvent, SoundEvent> GLINT = sound("unsung/glint");
    public static final DeferredHolder<SoundEvent, SoundEvent> DROP = sound("unsung/drop");
    public static final DeferredHolder<SoundEvent, SoundEvent> TINK = sound("unsung/tink");
    public static final DeferredHolder<SoundEvent, SoundEvent> HURT = sound("unsung/hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> BREAK = sound("unsung/break");
    public static final DeferredHolder<SoundEvent, SoundEvent> CRACK = sound("unsung/crack");
    public static final DeferredHolder<SoundEvent, SoundEvent> CIRCLES = sound("unsung/circles");
    public static final DeferredHolder<SoundEvent, SoundEvent> HARMONIZE = sound("unsung/harmonize");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAST_CHORD = sound("unsung/last_chord");

    /** The ways a voice's music block is sung. */
    public enum Singing {
        /** Its own line, the vowel open: the singer. */
        SUNG,
        /** A hum of the chord under the singer. */
        HUM,
        /** The rising chord through a Harmonize warning. */
        RISE
    }

    private static final Map<String, DeferredHolder<SoundEvent, SoundEvent>> MUSIC = new LinkedHashMap<>();

    static {
        for (Voice v : Voice.values()) {
            for (int chord = 0; chord < UnsungMoves.CHORDS; chord++) {
                musicSound(v, Singing.SUNG, chord);
                musicSound(v, Singing.HUM, chord);
            }
            musicSound(v, Singing.RISE, 0);
        }
    }

    private static void musicSound(Voice v, Singing how, int chord) {
        String name = musicName(v, how, chord);
        MUSIC.put(name, sound(name));
    }

    /** The music block's sound name: {@code music/unsung/alto_sung_0}, {@code .../bass_hum_3}, {@code .../tenor_rise}. */
    public static String musicName(Voice v, Singing how, int chord) {
        String base = "music/unsung/" + v.id() + "_" + how.name().toLowerCase(java.util.Locale.ROOT);
        return how == Singing.RISE ? base : base + "_" + Math.floorMod(chord, UnsungMoves.CHORDS);
    }

    /** A music block's sound event. */
    public static SoundEvent music(Voice v, Singing how, int chord) {
        return MUSIC.get(musicName(v, how, chord)).get();
    }

    /** Every music block's name, for checks. */
    public static List<String> musicNames() {
        return Collections.unmodifiableList(new ArrayList<>(MUSIC.keySet()));
    }

    private UnsungRegistry() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITY_TYPES.register(modBus);
        SOUNDS.register(modBus);
        EFFECTS.register(modBus);
        STRUCTURE_TYPES.register(modBus);
        STRUCTURE_PIECES.register(modBus);
        PARTICLES.register(modBus);
        modBus.addListener(EntityAttributeCreationEvent.class, event -> {
            event.put(UNSUNG_MASK.get(), UnsungMask.createAttributes().build());
            event.put(SONG_NOTE.get(), SongNote.createAttributes().build());
        });
        // the Hymnal Altar keeps its lair in the framework's altar block entity
        modBus.addListener(BlockEntityTypeAddBlocksEvent.class, event -> event.modify(GuardianRegistry.ALTAR_ENTITY.get(), HYMNAL_ALTAR.get()));
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, event -> {
            if (event.getTabKey().equals(ModCreativeTab.COSMIC_BREACH.getKey())) {
                event.accept(CHOIR_PENDANT.get());
            }
        });
    }

    /** The lair blocks' tags, for the data run: the lair's heart is wither- and dragon-proof. */
    public static void blockTags(BiConsumer<TagKey<Block>, Block[]> tag) {
        Block[] lair = {HYMNAL_ALTAR.get(), LICHEN_WINDOW.get(), SILENCE_CIRCLE.get()};
        tag.accept(BlockTags.WITHER_IMMUNE, lair);
        tag.accept(BlockTags.DRAGON_IMMUNE, lair);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(CosmicBreach.id(name)));
    }
}
