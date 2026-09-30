package com.cosmicbreach.astrolabe;

import com.cosmicbreach.CosmicBreach;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Choir Astrolabe's registrations (GDD 4.2; the item itself is {@code ModItems.CHOIR_ASTROLABE}, its moveset
 * {@code data/cosmicbreach/combat/weapons/choir_astrolabe.json}): the star bolt, the Pocket Star and the Parallax's
 * decoy, the sounds ({@code tools/sound/astrolabe.py}: one D5 chime the game pitches to every note, the rings' flick, a
 * bolt's landing, the star's warm hum, its pulse, a swallowed projectile, a mark, the beam, the charge, the choir hit of
 * the Supernova) and the server effects ({@link AstrolabeEffects}).
 */
public final class Astrolabes {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, CosmicBreach.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, CosmicBreach.MOD_ID);

    /** A bolt: position sent every tick, since it homes. */
    public static final DeferredHolder<EntityType<?>, EntityType<StarBolt>> STAR_BOLT = ENTITY_TYPES.register("star_bolt",
            () -> EntityType.Builder.<StarBolt>of(StarBolt::new, MobCategory.MISC)
                    .sized(0.3f, 0.3f)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .noSave()
                    .build(CosmicBreach.id("star_bolt").toString()));

    public static final DeferredHolder<EntityType<?>, EntityType<PocketStar>> POCKET_STAR = ENTITY_TYPES.register("pocket_star",
            () -> EntityType.Builder.<PocketStar>of(PocketStar::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(10)
                    .updateInterval(10)
                    .noSave()
                    .build(CosmicBreach.id("pocket_star").toString()));

    public static final DeferredHolder<EntityType<?>, EntityType<ParallaxDecoy>> PARALLAX_DECOY = ENTITY_TYPES.register("parallax_decoy",
            () -> EntityType.Builder.<ParallaxDecoy>of(ParallaxDecoy::new, MobCategory.MISC)
                    .sized(0.4f, 0.4f)
                    .clientTrackingRange(8)
                    .updateInterval(10)
                    .noSave()
                    .build(CosmicBreach.id("parallax_decoy").toString()));

    public static final DeferredHolder<SoundEvent, SoundEvent> CHIME = sound("astrolabe/chime");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLICK = sound("astrolabe/flick");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOLT_HIT = sound("astrolabe/bolt_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAR_PLACE = sound("astrolabe/star_place");
    public static final DeferredHolder<SoundEvent, SoundEvent> HUM = sound("astrolabe/hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> PULSE = sound("astrolabe/pulse");
    public static final DeferredHolder<SoundEvent, SoundEvent> SWALLOW = sound("astrolabe/swallow");
    public static final DeferredHolder<SoundEvent, SoundEvent> MARK = sound("astrolabe/mark");
    public static final DeferredHolder<SoundEvent, SoundEvent> BEAM = sound("astrolabe/beam");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHARGE = sound("astrolabe/charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> SUPERNOVA = sound("astrolabe/supernova");

    private Astrolabes() {
    }

    public static void register(IEventBus modBus, IEventBus game) {
        ENTITY_TYPES.register(modBus);
        SOUNDS.register(modBus);
        AstrolabeEffects.register(game);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(CosmicBreach.id(name)));
    }
}
