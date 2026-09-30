package com.cosmicbreach.gear;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.combat.server.CombatHooks;
import com.cosmicbreach.combat.server.HitModifiers;
import com.cosmicbreach.gear.driftweave.Drift;
import com.cosmicbreach.gear.driftweave.Driftweave;
import com.cosmicbreach.gear.driftweave.Slipstream;
import com.cosmicbreach.gear.net.SetFxPayload;
import com.cosmicbreach.gear.regalia.Aligned;
import com.cosmicbreach.gear.regalia.ChoirRegalia;
import com.cosmicbreach.gear.regalia.Echoes;
import com.cosmicbreach.gear.regalia.Harmonics;
import com.cosmicbreach.gear.regalia.HymnRings;
import com.cosmicbreach.gear.set.ArmorSet;
import com.cosmicbreach.net.ModNetworking;
import com.cosmicbreach.registry.ModCreativeTab;
import com.cosmicbreach.registry.ModMaterials;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The second and third armor sets on the set framework (GDD 5.1): the Driftweave (T2, Forge II) and the Choir
 * Regalia (T3, Forge III). Their armor materials and pieces (through {@link GearRegistry#registerSet}), sounds,
 * the Aligned effect, the damage types their ghosts hit with, their payload, their engine hooks and hit
 * modifiers, and their server upkeep. Client side: {@code client.gear.SetsClient}.
 */
public final class GearSets {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, CosmicBreach.MOD_ID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, CosmicBreach.MOD_ID);

    // ------------------------------------------------------------------ the sets

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> DRIFTWEAVE_MATERIAL = GearRegistry.ARMOR_MATERIALS.register(
            Driftweave.SET.id().getPath(), () -> material(Driftweave.SET, SoundEvents.ARMOR_EQUIP_ELYTRA,
                    () -> Ingredient.of(ModMaterials.NEBULITE_INGOT.get())));
    public static final GearRegistry.SetItems DRIFTWEAVE = GearRegistry.registerSet(Driftweave.SET, DRIFTWEAVE_MATERIAL);

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> REGALIA_MATERIAL = GearRegistry.ARMOR_MATERIALS.register(
            ChoirRegalia.SET.id().getPath(), () -> material(ChoirRegalia.SET, SoundEvents.ARMOR_EQUIP_GOLD,
                    () -> Ingredient.of(ModMaterials.ECLIPSIUM_INGOT.get())));
    public static final GearRegistry.SetItems REGALIA = GearRegistry.registerSet(ChoirRegalia.SET, REGALIA_MATERIAL);

    /** Aligned (the Hymn of Alignment's mark on enemies). */
    public static final DeferredHolder<MobEffect, Aligned> ALIGNED = EFFECTS.register("aligned", Aligned::new);

    /** The Afterimage's copies and Harmonics' echoes: their own damage types, the wearer gets the credit. */
    public static final ResourceKey<DamageType> AFTERIMAGE_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, CosmicBreach.id("afterimage"));
    public static final ResourceKey<DamageType> ECHO_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, CosmicBreach.id("echo"));

    // ------------------------------------------------------------------ sounds (tools/sound/sets.py)

    public static final DeferredHolder<SoundEvent, SoundEvent> AFTERIMAGE = sound("driftweave/afterimage");
    public static final DeferredHolder<SoundEvent, SoundEvent> AFTERIMAGE_STRIKE = sound("driftweave/afterimage_strike");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRIFT_START = sound("driftweave/drift_start");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRIFT_END = sound("driftweave/drift_end");
    public static final DeferredHolder<SoundEvent, SoundEvent> ECHO = sound("regalia/echo");
    public static final DeferredHolder<SoundEvent, SoundEvent> HYMN = sound("regalia/hymn");

    private GearSets() {
    }

    public static void register(IEventBus modBus, IEventBus game) {
        SOUNDS.register(modBus);
        EFFECTS.register(modBus);
        modBus.addListener(RegisterPayloadHandlersEvent.class, GearSets::registerPayloads);
        modBus.addListener(EventPriority.LOW, BuildCreativeModeTabContentsEvent.class, GearSets::fillCreativeTab);

        CombatHooks.register(Driftweave.HOOK);
        CombatHooks.register(ChoirRegalia.HOOK);
        HitModifiers.register(Driftweave.AERIAL);
        HitModifiers.register(Aligned.MODIFIER);

        game.addListener(PlayerTickEvent.Post.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                Drift.onPlayerTick(player);
            }
        });
        game.addListener(ServerTickEvent.Post.class, Slipstream::onServerTick);
        game.addListener(ServerTickEvent.Post.class, Echoes::onServerTick);
        game.addListener(ServerTickEvent.Post.class, HymnRings::onServerTick);
        game.addListener(LivingIncomingDamageEvent.class, Aligned::onIncomingDamage);
        game.addListener(ServerStoppingEvent.class, Slipstream::onServerStopping);
        game.addListener(ServerStoppingEvent.class, Echoes::onServerStopping);
        game.addListener(ServerStoppingEvent.class, Harmonics::onServerStopping);
        game.addListener(ServerStoppingEvent.class, HymnRings::onServerStopping);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ModNetworking.PROTOCOL_VERSION);
        registrar.playToClient(SetFxPayload.TYPE, SetFxPayload.STREAM_CODEC,
                (payload, context) -> com.cosmicbreach.client.gear.SetsClient.handle(payload, context));
    }

    /** The two sets after the Vanguard in the mod's tab. */
    private static void fillCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModCreativeTab.COSMIC_BREACH.getKey())) {
            DRIFTWEAVE.all().forEach(piece -> event.accept(piece.get()));
            REGALIA.all().forEach(piece -> event.accept(piece.get()));
        }
    }

    /** Every set piece of both sets. */
    public static List<GearRegistry.SetItems> sets() {
        return List.of(DRIFTWEAVE, REGALIA);
    }

    private static ArmorMaterial material(ArmorSet set, Holder<SoundEvent> equipSound, Supplier<Ingredient> repair) {
        return new ArmorMaterial(set.defense(), 15, equipSound, repair, List.of(new ArmorMaterial.Layer(set.id())),
                set.toughness(), set.knockbackResistance());
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(CosmicBreach.id(name)));
    }
}
