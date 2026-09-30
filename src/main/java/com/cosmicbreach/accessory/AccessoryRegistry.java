package com.cosmicbreach.accessory;

import com.cosmicbreach.CosmicBreach;
import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The accessories' own registrations (GDD 5.2): the four accessories no other feature registered (the rest keep their
 * ids where their sources registered them), the sounds ({@code tools/sound/curios.py}), the Hourglass's slow, the
 * damage types of the Heart's nova and the Halo's cuts, and the per-player state (the Halo's shards, the Heart's rest,
 * the Compass's vault).
 */
public final class AccessoryRegistry {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CosmicBreach.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, CosmicBreach.MOD_ID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, CosmicBreach.MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, CosmicBreach.MOD_ID);

    // ------------------------------------------------------------------ items

    public static final DeferredItem<AccessoryItem> TWIN_COMET_BAND = accessory(Accessory.TWIN_COMET_BAND, Rarity.RARE);
    public static final DeferredItem<AccessoryItem> LEECHSTAR_SIGNET = accessory(Accessory.LEECHSTAR_SIGNET, Rarity.RARE);
    public static final DeferredItem<AccessoryItem> PERIHELION_LOOP = accessory(Accessory.PERIHELION_LOOP, Rarity.UNCOMMON);
    public static final DeferredItem<AccessoryItem> SUNSHARD_COMPASS = accessory(Accessory.SUNSHARD_COMPASS, Rarity.RARE);

    // ------------------------------------------------------------------ sounds

    public static final DeferredHolder<SoundEvent, SoundEvent> EQUIP = sound("accessory/equip");
    public static final DeferredHolder<SoundEvent, SoundEvent> COMET_CHAIN = sound("accessory/comet_chain");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEECH_HEAL = sound("accessory/leech_heal");
    public static final DeferredHolder<SoundEvent, SoundEvent> GRAVITY_WELL = sound("accessory/gravity_well");
    public static final DeferredHolder<SoundEvent, SoundEvent> NOVA = sound("accessory/nova");
    public static final DeferredHolder<SoundEvent, SoundEvent> HALO_BLOCK = sound("accessory/halo_block");
    public static final DeferredHolder<SoundEvent, SoundEvent> HALO_REGROW = sound("accessory/halo_regrow");
    public static final DeferredHolder<SoundEvent, SoundEvent> HALO_CUT = sound("accessory/halo_cut");
    public static final DeferredHolder<SoundEvent, SoundEvent> BLACK_HOLE = sound("accessory/black_hole");
    public static final DeferredHolder<SoundEvent, SoundEvent> VESPER_SLOW = sound("accessory/vesper_slow");

    // ------------------------------------------------------------------ the Hourglass's slow

    public static final DeferredHolder<MobEffect, VesperSlow> SLOWED = EFFECTS.register("vesper_slow", VesperSlow::new);

    // ------------------------------------------------------------------ damage

    public static final ResourceKey<DamageType> NOVA_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, CosmicBreach.id("dying_star"));
    public static final ResourceKey<DamageType> HALO_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, CosmicBreach.id("halo_shard"));

    // ------------------------------------------------------------------ per-player state

    /** The Halo's three shards: synced to the wearer and everyone who sees them (the client draws them). Never saved. */
    public static final Supplier<AttachmentType<HaloState>> HALO = ATTACHMENTS.register("halo",
            () -> AttachmentType.builder(() -> HaloState.NONE).sync(HaloState.STREAM_CODEC).build());

    /** The game time the Heart of a Dying Star may fire again: saved, so a relog doesn't end its rest. */
    public static final Supplier<AttachmentType<Long>> HEART_READY = ATTACHMENTS.register("heart_ready",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).build());

    /** The vault the Sunshard Compass points to: synced to its wearer alone. Never saved. */
    public static final Supplier<AttachmentType<CompassTarget>> COMPASS = ATTACHMENTS.register("compass",
            () -> AttachmentType.builder(() -> CompassTarget.NONE)
                    .sync((holder, to) -> holder == to, CompassTarget.STREAM_CODEC).build());

    private AccessoryRegistry() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        SOUNDS.register(modBus);
        EFFECTS.register(modBus);
        ATTACHMENTS.register(modBus);
    }

    private static DeferredItem<AccessoryItem> accessory(Accessory kind, Rarity rarity) {
        return ITEMS.registerItem(kind.path(), p -> new AccessoryItem(p, kind), new Item.Properties().rarity(rarity).stacksTo(1));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(CosmicBreach.id(name)));
    }
}
