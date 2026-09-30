package com.cosmicbreach.accessory;

import com.cosmicbreach.combat.server.CombatHooks;
import com.cosmicbreach.combat.server.HitModifiers;
import com.cosmicbreach.combat.server.HitResolver;
import com.cosmicbreach.net.ModNetworking;
import com.cosmicbreach.registry.ModCreativeTab;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The accessories (G6a, GDD 5.2) in Curios slots: two rings, a necklace and a charm, set up by data
 * ({@code data/cosmicbreach/curios/}: the ring slot's size 2 and the player's slots; the {@code curios:<slot>} item tags
 * say which accessory goes where). The ten accessories are {@link AccessoryItem}s ({@link Accessory}); what each does
 * lives here: the engine's numbers and events ({@link AccessoryHooks}), the pulls ({@link PullFields}), the Heart's nova
 * ({@link DyingStar}), the Pendant's ability hits ({@link ChoirPendant}), the Halo's shards ({@link HaloOfNine}), the
 * Compass's vault ({@link SunshardCompass}) and trap sight ({@link TrapSight}). Sources: the vault loot tables
 * {@code vaults/accessories_t1}, {@code _t2} and {@code _charms}, the guardians' first kills, the Gyre Knight and the
 * Sanctum's wing vaults.
 */
public final class Accessories {
    private Accessories() {
    }

    public static void register(IEventBus modBus, IEventBus game) {
        AccessoryRegistry.register(modBus);
        modBus.addListener(RegisterPayloadHandlersEvent.class, Accessories::registerPayloads);
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, Accessories::fillCreativeTab);

        AccessoryHooks hooks = new AccessoryHooks();
        CombatHooks.register(hooks);
        HitModifiers.register(hooks);
        HitResolver.onStrike(ChoirPendant::onStrike);

        game.addListener(ServerTickEvent.Post.class, PullFields::onServerTick);
        game.addListener(PlayerTickEvent.Post.class, HaloOfNine::onPlayerTick);
        game.addListener(PlayerTickEvent.Post.class, SunshardCompass::onPlayerTick);
        game.addListener(LivingDamageEvent.Post.class, DyingStar::onDamaged);
        // the shards meet a projectile before anything else sees its hit
        game.addListener(EventPriority.HIGHEST, ProjectileImpactEvent.class, HaloOfNine::onProjectileImpact);
        game.addListener(EventPriority.HIGHEST, LivingIncomingDamageEvent.class, HaloOfNine::onIncomingDamage);
        game.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> {
            UUID id = event.getEntity().getUUID();
            AccessoryHooks.forget(id);
            ChoirPendant.forget(id);
            HaloOfNine.forget(id);
        });
        game.addListener(ServerStoppingEvent.class, event -> {
            PullFields.onServerStopping(event);
            AccessoryHooks.clear();
            ChoirPendant.clear();
            HaloOfNine.clear();
        });
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ModNetworking.PROTOCOL_VERSION);
        registrar.playToClient(AccessoryFxPayload.TYPE, AccessoryFxPayload.STREAM_CODEC,
                (payload, context) -> com.cosmicbreach.client.accessory.AccessoryFx.handle(payload, context));
    }

    /** The four accessories no other feature puts in the tab (the other six sit with their sources). */
    private static void fillCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModCreativeTab.COSMIC_BREACH.getKey())) {
            event.accept(AccessoryRegistry.TWIN_COMET_BAND.get());
            event.accept(AccessoryRegistry.LEECHSTAR_SIGNET.get());
            event.accept(AccessoryRegistry.PERIHELION_LOOP.get());
            event.accept(AccessoryRegistry.SUNSHARD_COMPASS.get());
        }
    }

    // ------------------------------------------------------------------ for the scenario (dev tests only)

    /** The Halo's shards on a player now. */
    public static int haloShards(Player player) {
        return HaloOfNine.shards(player);
    }

    /** What the Choir Pendant gave for the player's latest cast. */
    public static int pendantGiven(UUID player) {
        return ChoirPendant.givenThisCast(player);
    }

    /** The Compass looks again now. */
    public static CompassTarget refreshCompass(ServerPlayer player) {
        return SunshardCompass.refresh(player);
    }

    /** Where a shard of a player's halo is at a time. */
    public static Vec3 shardAt(Entity player, int shard, double time) {
        return HaloOfNine.shardAt(player, shard, time);
    }
}
