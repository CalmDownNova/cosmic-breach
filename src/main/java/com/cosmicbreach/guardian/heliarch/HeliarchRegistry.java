package com.cosmicbreach.guardian.heliarch;

import com.cosmicbreach.CosmicBreach;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Everything the Hollow Heliarch registers (G9a, GDD 7.3): the Heliarch and its Star Seeds, the monoliths its plates
 * become, each participant's Reliquary, its damage types and its sounds ({@code tools/sound/heliarch.py}: its voice
 * from the recorded takes, its effects, and its music by phase). Names are in
 * {@code assets/cosmicbreach_heliarch/lang/en_us.json}; textures and models come from {@code tools/art/gen_heliarch.py}.
 * Its rewards (Last Light, Umbra Cantor, the Heliarch's Crown) are the relics' ({@code relic.Relics}), handed out by id.
 */
public final class HeliarchRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CosmicBreach.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CosmicBreach.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, CosmicBreach.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, CosmicBreach.MOD_ID);

    // ------------------------------------------------------------------ damage types (data/cosmicbreach/damage_type/)

    /** Sunderfall's hand. */
    public static final ResourceKey<DamageType> HAND = key("heliarch_hand");
    /** Solar fire: the Corona Sweep, the Solar Lance, Solar Rain. */
    public static final ResourceKey<DamageType> SOLAR = key("heliarch_solar");
    /** A plate of the Halo Shed. */
    public static final ResourceKey<DamageType> PLATE = key("heliarch_plate");
    /** The eclipse: its beam and a failed Nova. */
    public static final ResourceKey<DamageType> ECLIPSE = key("heliarch_eclipse");
    /** The void: a tendril's lash, a Star Seed. */
    public static final ResourceKey<DamageType> VOID = key("heliarch_void");

    // ------------------------------------------------------------------ blocks

    private static BlockBehaviour.Properties relic(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(-1.0f, 3_600_000.0f).noLootTable().sound(sound)
                .pushReaction(PushReaction.BLOCK).isValidSpawn(Blocks::never);
    }

    /** A monolith: one of a fallen plate's twelve blocks (it stands until the eclipse's beams wear it down). */
    public static final DeferredBlock<MonolithBlock> MONOLITH = BLOCKS.registerBlock("heliarch_monolith", MonolithBlock::new,
            relic(MapColor.GOLD, SoundType.NETHERITE_BLOCK).lightLevel(s -> 3));
    /** A participant's Reliquary: opened once, by them alone. */
    public static final DeferredBlock<ReliquaryBlock> RELIQUARY = BLOCKS.registerBlock("heliarch_reliquary", ReliquaryBlock::new,
            relic(MapColor.GOLD, SoundType.METAL).noOcclusion().lightLevel(s -> s.getValue(ReliquaryBlock.OPEN) ? 6 : 12));

    public static final Supplier<BlockEntityType<ReliquaryBlockEntity>> RELIQUARY_ENTITY = BLOCK_ENTITIES.register("heliarch_reliquary",
            () -> BlockEntityType.Builder.of(ReliquaryBlockEntity::new, RELIQUARY.get()).build(null));

    // ------------------------------------------------------------------ entities

    public static final DeferredHolder<EntityType<?>, EntityType<HollowHeliarch>> HOLLOW_HELIARCH = ENTITY_TYPES.register("hollow_heliarch",
            () -> EntityType.Builder.of(HollowHeliarch::new, MobCategory.MONSTER)
                    .sized(HeliarchArena.CORE_SIZE, HeliarchArena.CORE_SIZE)
                    .eyeHeight(HeliarchArena.CORE_SIZE / 2f)
                    .fireImmune()
                    .clientTrackingRange(14)
                    .updateInterval(1)
                    .noSave()
                    .noSummon()
                    .build(CosmicBreach.id("hollow_heliarch").toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<StarSeed>> STAR_SEED = ENTITY_TYPES.register("star_seed",
            () -> EntityType.Builder.<StarSeed>of(StarSeed::new, MobCategory.MISC)
                    .sized(0.9f, 0.9f)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .noSave()
                    .noSummon()
                    .build(CosmicBreach.id("star_seed").toString()));

    // ------------------------------------------------------------------ sounds (tools/sound/heliarch.py)

    public static final DeferredHolder<SoundEvent, SoundEvent> VOICE_INTRO = sound("heliarch/voice_intro");
    public static final DeferredHolder<SoundEvent, SoundEvent> VOICE_HOLLOWING = sound("heliarch/voice_hollowing");
    public static final DeferredHolder<SoundEvent, SoundEvent> VOICE_NOVA = sound("heliarch/voice_nova");
    public static final DeferredHolder<SoundEvent, SoundEvent> VOICE_COLLAPSE = sound("heliarch/voice_collapse");
    public static final DeferredHolder<SoundEvent, SoundEvent> VOICE_DEATH = sound("heliarch/voice_death");
    public static final DeferredHolder<SoundEvent, SoundEvent> VOICE_PLAYER_DOWN = sound("heliarch/voice_player_down");

    public static final DeferredHolder<SoundEvent, SoundEvent> ASSEMBLE = sound("heliarch/assemble");
    public static final DeferredHolder<SoundEvent, SoundEvent> IGNITE = sound("heliarch/ignite");
    public static final DeferredHolder<SoundEvent, SoundEvent> OPEN = sound("heliarch/open");
    public static final DeferredHolder<SoundEvent, SoundEvent> CLOSE = sound("heliarch/close");
    public static final DeferredHolder<SoundEvent, SoundEvent> CLANG = sound("heliarch/clang");
    public static final DeferredHolder<SoundEvent, SoundEvent> HURT = sound("heliarch/hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> HAND_RISE = sound("heliarch/hand_rise");
    public static final DeferredHolder<SoundEvent, SoundEvent> GLINT = sound("heliarch/glint");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLAM = sound("heliarch/slam");
    public static final DeferredHolder<SoundEvent, SoundEvent> PARRIED = sound("heliarch/parried");
    public static final DeferredHolder<SoundEvent, SoundEvent> SWEEP_TELL = sound("heliarch/sweep_tell");
    public static final DeferredHolder<SoundEvent, SoundEvent> SWEEP = sound("heliarch/sweep");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLARE_TELL = sound("heliarch/flare_tell");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLARE = sound("heliarch/flare");
    public static final DeferredHolder<SoundEvent, SoundEvent> LANCE_TRACK = sound("heliarch/lance_track");
    public static final DeferredHolder<SoundEvent, SoundEvent> LANCE_LOCK = sound("heliarch/lance_lock");
    public static final DeferredHolder<SoundEvent, SoundEvent> LANCE_FIRE = sound("heliarch/lance_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHED_FLASH = sound("heliarch/shed_flash");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHED_OUT = sound("heliarch/shed_out");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHED_RETURN = sound("heliarch/shed_return");
    public static final DeferredHolder<SoundEvent, SoundEvent> BREAK = sound("heliarch/break");
    public static final DeferredHolder<SoundEvent, SoundEvent> TEAR = sound("heliarch/tear");
    public static final DeferredHolder<SoundEvent, SoundEvent> MONOLITH_SLAM = sound("heliarch/monolith_slam");
    public static final DeferredHolder<SoundEvent, SoundEvent> PIP = sound("heliarch/pip");
    public static final DeferredHolder<SoundEvent, SoundEvent> MONOLITH_SHATTER = sound("heliarch/monolith_shatter");
    public static final DeferredHolder<SoundEvent, SoundEvent> BEAM_CHARGE = sound("heliarch/beam_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> BEAM = sound("heliarch/beam");
    public static final DeferredHolder<SoundEvent, SoundEvent> LASH_TELL = sound("heliarch/lash_tell");
    public static final DeferredHolder<SoundEvent, SoundEvent> LASH = sound("heliarch/lash");
    public static final DeferredHolder<SoundEvent, SoundEvent> TENDRIL_CUT = sound("heliarch/tendril_cut");
    public static final DeferredHolder<SoundEvent, SoundEvent> INVERSION = sound("heliarch/inversion");
    public static final DeferredHolder<SoundEvent, SoundEvent> SEED = sound("heliarch/seed");
    public static final DeferredHolder<SoundEvent, SoundEvent> SEED_BURST = sound("heliarch/seed_burst");
    public static final DeferredHolder<SoundEvent, SoundEvent> NOVA_CHARGE = sound("heliarch/nova_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> NOVA_BLAST = sound("heliarch/nova_blast");
    public static final DeferredHolder<SoundEvent, SoundEvent> NOVA_BREAK = sound("heliarch/nova_break");
    public static final DeferredHolder<SoundEvent, SoundEvent> ECLIPSE_DRONE = sound("heliarch/eclipse_drone");
    public static final DeferredHolder<SoundEvent, SoundEvent> CRACK = sound("heliarch/crack");
    public static final DeferredHolder<SoundEvent, SoundEvent> FALL = sound("heliarch/fall");
    public static final DeferredHolder<SoundEvent, SoundEvent> RAIN_TELL = sound("heliarch/rain_tell");
    public static final DeferredHolder<SoundEvent, SoundEvent> RAIN = sound("heliarch/rain");
    public static final DeferredHolder<SoundEvent, SoundEvent> DEATH = sound("heliarch/death");
    public static final DeferredHolder<SoundEvent, SoundEvent> RELIQUARY_APPEAR = sound("heliarch/reliquary");
    public static final DeferredHolder<SoundEvent, SoundEvent> RELIQUARY_OPEN = sound("heliarch/reliquary_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> SEAL = sound("heliarch/seal");
    /** The boss loop by phase: regal and solar, hollow and eclipsed, urgent. */
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_REGENT = sound("music/heliarch_regent");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_HOLLOW = sound("music/heliarch_hollow");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_COLLAPSE = sound("music/heliarch_collapse");

    private HeliarchRegistry() {
    }

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ENTITY_TYPES.register(modBus);
        SOUNDS.register(modBus);
        modBus.addListener(EntityAttributeCreationEvent.class, event -> {
            event.put(HOLLOW_HELIARCH.get(), HollowHeliarch.createAttributes().build());
            event.put(STAR_SEED.get(), StarSeed.createAttributes().build());
        });
    }

    /** The relic blocks' tags, for the data run: no wither or dragon breaks them. */
    public static void blockTags(java.util.function.BiConsumer<net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block>,
            net.minecraft.world.level.block.Block[]> tag) {
        net.minecraft.world.level.block.Block[] all = {MONOLITH.get(), RELIQUARY.get()};
        tag.accept(net.minecraft.tags.BlockTags.WITHER_IMMUNE, all);
        tag.accept(net.minecraft.tags.BlockTags.DRAGON_IMMUNE, all);
    }

    /** Every Heliarch sound event, for checks. */
    public static java.util.List<DeferredHolder<SoundEvent, SoundEvent>> sounds() {
        java.util.List<DeferredHolder<SoundEvent, SoundEvent>> out = new java.util.ArrayList<>();
        for (var e : SOUNDS.getEntries()) {
            @SuppressWarnings("unchecked")
            DeferredHolder<SoundEvent, SoundEvent> h = (DeferredHolder<SoundEvent, SoundEvent>) e;
            out.add(h);
        }
        return out;
    }

    private static ResourceKey<DamageType> key(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, CosmicBreach.id(name));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(CosmicBreach.id(name)));
    }
}
