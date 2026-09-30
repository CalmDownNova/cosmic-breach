package com.cosmicbreach.guardian;

import com.cosmicbreach.guardian.colossus.CrystalHits;
import com.cosmicbreach.guardian.colossus.RefractionPayload;
import com.cosmicbreach.net.ModNetworking;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The biome guardians (GDD 7.2): registrations, payloads and server hooks, the common entry point. So far the
 * Prism Colossus and its Crown Spire. Client side: {@code client.guardian.GuardianClient}.
 *
 * <p>What the next guardians reuse: {@link GuardianType} and {@link LairGuardian} (a lair's guardian),
 * {@link GuardianAltarBlock} with its {@link GuardianAltarBlockEntity} (cooldown, re-arming, the Echo),
 * {@link GuardianLairs} (the locate API), {@link GuardianBossBar} (the bar with the Break gauge, a countdown and
 * the boss music), {@link GuardianPart} (a piece that takes hits for its owner), {@link GuardianRewards} and
 * {@link RewardTable} (per-player rewards at each participant's feet), {@link BossTargeting},
 * {@link AttackPicker}, {@link BreakGauge}, {@link GuardianHealth}, {@link Telegraphs} (the shapes that are both
 * warning and hitbox), {@link ArenaRules} and {@link GuardianFights} (anti-cheese).
 */
public final class Guardians {
    private Guardians() {
    }

    public static void register(IEventBus modBus, IEventBus game) {
        GuardianTypes.init();
        GuardianRegistry.register(modBus);
        modBus.addListener(RegisterPayloadHandlersEvent.class, Guardians::registerPayloads);
        ArenaRules.register(game);
        GuardianPayouts.register(game);
        GuardianPayouts.register(GuardianTypes.COLOSSUS, com.cosmicbreach.guardian.colossus.ColossusLoot.TABLE, null);
        CrystalHits.register(game);
        com.cosmicbreach.guardian.unsung.UnsungSetup.register(modBus, game);
        game.addListener(RegisterCommandsEvent.class, event -> GuardianCommands.register(event.getDispatcher()));
        // boss bars never outlive a death, a trip to another dimension or a fight nobody is running any more
        game.addListener(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent.class, event -> forgetBars(event.getEntity()));
        game.addListener(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent.class,
                event -> forgetBars(event.getEntity()));
        game.addListener(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent.class, event -> forgetBars(event.getEntity()));
        game.addListener(net.neoforged.neoforge.event.tick.ServerTickEvent.Post.class, event -> GuardianBossBar.sweep());
    }

    private static void forgetBars(net.minecraft.world.entity.player.Player player) {
        if (player instanceof net.minecraft.server.level.ServerPlayer server) {
            GuardianBossBar.forget(server);
        }
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ModNetworking.PROTOCOL_VERSION);
        // lambdas calling the client class, so a dedicated server never loads it
        registrar.playToClient(GuardianBarPayload.TYPE, GuardianBarPayload.STREAM_CODEC,
                (payload, context) -> com.cosmicbreach.client.guardian.GuardianClientHandlers.bar(payload, context));
        registrar.playToClient(RefractionPayload.TYPE, RefractionPayload.STREAM_CODEC,
                (payload, context) -> com.cosmicbreach.client.guardian.GuardianClientHandlers.refraction(payload, context));
    }
}
