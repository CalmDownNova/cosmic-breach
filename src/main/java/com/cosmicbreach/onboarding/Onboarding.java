package com.cosmicbreach.onboarding;

import com.cosmicbreach.registry.ModCreativeTab;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * The way into Aetheria (W4, GDD 1.3), wired up:
 *
 * <ol>
 *   <li>{@link Starfalls}: a shard streaks down near each player at their first sunset and each later night;
 *       picking one up gives the {@link Codex}.</li>
 *   <li>{@link FallenRiftStructure}: meteor craters with a half-buried ring and a chest.</li>
 *   <li>{@link BreachRings}: 8 Breach Frames round an empty middle, opened with a shard.</li>
 *   <li>{@link FallUp}: stepping into the Breach, arriving over an island, the {@link Landings} and the way home;
 *       {@link ArrivalGrace} for the first minute.</li>
 * </ol>
 * Config: {@link OnboardingConfig}. Commands: {@link OnboardingCommands}. Client side:
 * {@code client.onboarding.OnboardingClient}.
 */
public final class Onboarding {
    private Onboarding() {
    }

    public static void register(IEventBus modBus, IEventBus game, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, OnboardingConfig.SPEC, OnboardingConfig.FILE);
        OnboardingRegistry.register(modBus);
        modBus.addListener(RegisterPayloadHandlersEvent.class, OnboardingNet::register);
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, Onboarding::onCreativeTab);

        game.addListener(LevelTickEvent.Post.class, Starfalls::onLevelTick);
        game.addListener(ItemEntityPickupEvent.Post.class, Starfalls::onPickup);
        game.addListener(ServerTickEvent.Post.class, FallUp::onServerTick);
        game.addListener(LivingChangeTargetEvent.class, ArrivalGrace::onChangeTarget);
        game.addListener(AttackEntityEvent.class, ArrivalGrace::onAttack);
        game.addListener(LivingIncomingDamageEvent.class, ArrivalGrace::onIncomingDamage);
        // last, so no other rule can let a spawn through next to a Landing
        game.addListener(EventPriority.LOWEST, MobSpawnEvent.SpawnPlacementCheck.class, ArrivalGrace::onSpawnPlacement);
        game.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> {
            Starfalls.forget(event.getEntity().getUUID());
            FallUp.forget(event.getEntity().getUUID());
            ArrivalGrace.forget(event.getEntity().getUUID());
        });
        game.addListener(ServerStoppedEvent.class, event -> {
            Starfalls.reset();
            FallUp.reset();
            ArrivalGrace.reset();
        });
        game.addListener(RegisterCommandsEvent.class, event -> OnboardingCommands.register(event.getDispatcher()));
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == ModCreativeTab.COSMIC_BREACH.getKey()) {
            event.accept(OnboardingRegistry.STARFALL_SHARD.get());
            event.accept(OnboardingRegistry.STARFALL_CODEX.get());
            event.accept(OnboardingRegistry.TORN_CODEX_PAGE.get());
        }
    }
}
