package com.cosmicbreach;

import com.cosmicbreach.combat.data.CombatDataLoader;
import com.cosmicbreach.combat.server.CombatServerEvents;
import com.cosmicbreach.combat.server.effect.EdgesEffects;
import com.cosmicbreach.combat.server.effect.MaulEffects;
import com.cosmicbreach.datagen.ModDataGen;
import com.cosmicbreach.entity.shardling.Shardlings;
import com.cosmicbreach.gear.Gear;
import com.cosmicbreach.net.ModNetworking;
import com.cosmicbreach.progression.Progression;
import com.cosmicbreach.registry.ModAttachments;
import com.cosmicbreach.registry.ModAttributes;
import com.cosmicbreach.registry.ModBlocks;
import com.cosmicbreach.registry.ModCreativeTab;
import com.cosmicbreach.registry.ModEntities;
import com.cosmicbreach.registry.ModItems;
import com.cosmicbreach.registry.ModMaterials;
import com.cosmicbreach.registry.ModParticles;
import com.cosmicbreach.registry.ModSounds;
import com.cosmicbreach.sandbox.CosmicBreachCommand;
import com.cosmicbreach.sandbox.Sandbox;
import com.cosmicbreach.sandbox.SelfTest;
import com.cosmicbreach.world.AetheriaAudio;
import com.cosmicbreach.world.AetheriaWorld;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

/** Cosmic Breach. Slice 0: the combat engine in a sandbox. Common entry point, both sides. */
@Mod(CosmicBreach.MOD_ID)
public final class CosmicBreach {
    public static final String MOD_ID = "cosmicbreach";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CosmicBreach(IEventBus modBus, ModContainer container) {
        ModItems.register(modBus);
        ModBlocks.register(modBus);
        ModMaterials.register(modBus);
        ModEntities.register(modBus);
        ModCreativeTab.register(modBus);
        ModAttachments.register(modBus);
        ModSounds.register(modBus);
        ModParticles.register(modBus);
        ModAttributes.register(modBus);
        modBus.addListener(RegisterPayloadHandlersEvent.class, ModNetworking::register);
        // A lambda, not a method reference, so ModDataGen (client model providers) loads only in the data run.
        modBus.addListener(GatherDataEvent.class, event -> ModDataGen.gather(event));

        IEventBus game = NeoForge.EVENT_BUS;
        Progression.register(modBus, game);
        Gear.register(modBus, game);
        com.cosmicbreach.provision.Provisions.register(modBus, game);
        com.cosmicbreach.guardian.Guardians.register(modBus, game);
        com.cosmicbreach.guardian.leviathan.Leviathans.register(modBus, game);
        com.cosmicbreach.entity.gyre.GyreKnights.register(modBus, game);
        com.cosmicbreach.entity.stalker.Stalkers.register(modBus, game);
        com.cosmicbreach.astrolabe.Astrolabes.register(modBus, game);
        com.cosmicbreach.relic.Relics.register(modBus, game);
        com.cosmicbreach.accessory.Accessories.register(modBus, game);
        com.cosmicbreach.mount.Mounts.register(modBus, game);
        com.cosmicbreach.mount.MountCare.register(modBus, game);
        com.cosmicbreach.lift.Lifts.register(modBus, game);
        com.cosmicbreach.shrine.Shrines.register(modBus, game);
        com.cosmicbreach.familiar.Familiars.register(modBus, game);
        AetheriaWorld.register(modBus, game);
        AetheriaAudio.register(modBus, game);
        com.cosmicbreach.world.weather.CosmicWeather.register(modBus, game, container);
        com.cosmicbreach.onboarding.Onboarding.register(modBus, game, container);
        com.cosmicbreach.structure.Structures.register(modBus, game, container);
        com.cosmicbreach.structure.crypt.Crypts.register(modBus, game, container);
        com.cosmicbreach.structure.sanctum.Sanctums.register(modBus, game);
        com.cosmicbreach.guardian.heliarch.Heliarchs.register(modBus, game);
        com.cosmicbreach.voice.Echo.register(modBus, game);
        com.cosmicbreach.voice.boss.BossVoices.register(modBus, game);
        com.cosmicbreach.codex.Codices.register(modBus, game);
        CombatServerEvents.register(game);
        MaulEffects.register(game);
        EdgesEffects.register(game);
        game.addListener(AddReloadListenerEvent.class, CombatDataLoader::onAddReloadListeners);
        game.addListener(RegisterCommandsEvent.class, CosmicBreachCommand::onRegisterCommands);
        game.addListener(ServerTickEvent.Post.class, SelfTest::onServerTick);
        game.addListener(PlayerEvent.PlayerLoggedOutEvent.class, SelfTest::onLoggedOut);
        game.addListener(ServerStoppingEvent.class, SelfTest::onServerStopping);
        game.addListener(LivingDropsEvent.class, SelfTest::onLivingDrops);
        game.addListener(LivingExperienceDropEvent.class, SelfTest::onExperienceDrop);
        Shardlings.register(game);
        Sandbox.register(game);
        com.cosmicbreach.sandbox.StressScene.register(game);
        com.cosmicbreach.world.AiLod.register(game);
        com.cosmicbreach.world.VoidSafeDrops.register(game);
        LOGGER.info("[cosmicbreach] loaded");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
