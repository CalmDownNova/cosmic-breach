package com.cosmicbreach.client;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.anim.PlayerAnimations;
import com.cosmicbreach.client.combat.ClientCombat;
import com.cosmicbreach.client.combat.ClientConfig;
import com.cosmicbreach.client.combat.CombatHud;
import com.cosmicbreach.client.combat.ModKeyMappings;
import com.cosmicbreach.client.dev.AutoTest;
import com.cosmicbreach.client.entity.ShardlingClient;
import com.cosmicbreach.client.fx.ClientFx;
import com.cosmicbreach.client.gear.GearClient;
import com.cosmicbreach.client.fx.EdgesVisuals;
import com.cosmicbreach.client.fx.MaulVisuals;
import com.cosmicbreach.client.progression.ProgressionClient;
import com.cosmicbreach.client.sky.SkyClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/** Client-only entry point. NeoForge only constructs it on the physical client. */
@Mod(value = CosmicBreach.MOD_ID, dist = Dist.CLIENT)
public final class CosmicBreachClient {
    public CosmicBreachClient(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(RegisterKeyMappingsEvent.class, ModKeyMappings::register);
        modBus.addListener(RegisterGuiLayersEvent.class, CombatHud::register);
        PlayerAnimations.register(modBus, NeoForge.EVENT_BUS);
        ClientCombat.register(NeoForge.EVENT_BUS);
        ClientFx.register(modBus, NeoForge.EVENT_BUS);
        MaulVisuals.register();
        EdgesVisuals.register(modBus, NeoForge.EVENT_BUS);
        ShardlingClient.register(modBus, NeoForge.EVENT_BUS);
        ProgressionClient.register(modBus, NeoForge.EVENT_BUS);
        GearClient.register(modBus, NeoForge.EVENT_BUS);
        com.cosmicbreach.client.gear.SetsClient.register(modBus, NeoForge.EVENT_BUS);
        com.cosmicbreach.client.guardian.GuardianClient.register(modBus, NeoForge.EVENT_BUS);
        com.cosmicbreach.client.leviathan.LeviathanClient.register(modBus, NeoForge.EVENT_BUS);
        com.cosmicbreach.client.gyre.GyreClient.register(modBus, NeoForge.EVENT_BUS);
        com.cosmicbreach.client.stalker.StalkerClient.register(modBus, NeoForge.EVENT_BUS);
        com.cosmicbreach.client.jelly.JellyClient.register(modBus, NeoForge.EVENT_BUS);
        com.cosmicbreach.client.astrolabe.AstrolabeClient.register(modBus, NeoForge.EVENT_BUS);
        com.cosmicbreach.client.relic.RelicsClient.register(modBus, NeoForge.EVENT_BUS);
        com.cosmicbreach.client.accessory.AccessoriesClient.register(modBus, NeoForge.EVENT_BUS);
        com.cosmicbreach.client.mount.MountsClient.register(modBus, NeoForge.EVENT_BUS);
        com.cosmicbreach.client.familiar.FamiliarsClient.register(modBus, NeoForge.EVENT_BUS);
        com.cosmicbreach.client.satchel.SatchelClient.register(modBus, NeoForge.EVENT_BUS);
        com.cosmicbreach.client.tooltip.TooltipClient.register(NeoForge.EVENT_BUS);
        SkyClient.register(modBus, NeoForge.EVENT_BUS);
        com.cosmicbreach.client.weather.WeatherClient.register(modBus, NeoForge.EVENT_BUS);
        com.cosmicbreach.client.onboarding.OnboardingClient.register(modBus, NeoForge.EVENT_BUS);
        com.cosmicbreach.client.structure.StructuresClient.register(modBus, NeoForge.EVENT_BUS);
        com.cosmicbreach.client.lift.LiftClient.register(modBus, NeoForge.EVENT_BUS);
        com.cosmicbreach.client.shrine.ShrinesClient.register(modBus, NeoForge.EVENT_BUS);
        com.cosmicbreach.client.crypt.CryptClient.register(modBus, NeoForge.EVENT_BUS);
        com.cosmicbreach.client.sanctum.SanctumClient.register(modBus, NeoForge.EVENT_BUS);
        com.cosmicbreach.client.guardian.heliarch.HeliarchClient.register(modBus, NeoForge.EVENT_BUS);
        com.cosmicbreach.client.voice.EchoClient.register(modBus, NeoForge.EVENT_BUS);
        com.cosmicbreach.client.voice.BossVoiceClient.register(modBus, NeoForge.EVENT_BUS);
        com.cosmicbreach.client.codex.CodexGuide.build();
        AutoTest.installIfRequested();
    }
}
