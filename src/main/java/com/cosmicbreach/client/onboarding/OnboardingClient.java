package com.cosmicbreach.client.onboarding;

import com.cosmicbreach.onboarding.BreachBlock;
import com.cosmicbreach.onboarding.OnboardingNet;
import com.cosmicbreach.onboarding.OnboardingRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * The way in, client side (W4): the Starfall streak ({@link StarfallSky}), the shard's light pillar
 * ({@link StarfallShardRenderer}), the Breach's sky ({@link BreachRenderer}, {@link BreachShaders}), the pull
 * and the white of falling up ({@link FallUpClient}), and the Breach's breath (motes, and the wind's loop,
 * {@link BreachWind}).
 */
public final class OnboardingClient {
    private OnboardingClient() {
    }

    public static void register(IEventBus modBus, IEventBus game) {
        modBus.addListener(RegisterShadersEvent.class, BreachShaders::register);
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerBlockEntityRenderer(OnboardingRegistry.STARFALL_SHARD_ENTITY.get(), context -> new StarfallShardRenderer());
            event.registerBlockEntityRenderer(OnboardingRegistry.BREACH_ENTITY.get(), context -> new BreachRenderer());
        });
        modBus.addListener(RegisterGuiLayersEvent.class, FallUpClient::register);
        game.addListener(ClientTickEvent.Pre.class, event -> FallUpClient.tick());
        game.addListener(ClientTickEvent.Post.class, event -> StarfallSky.tick());
        game.addListener(RenderLevelStageEvent.class, StarfallSky::render);
        game.addListener(ScreenEvent.Render.Post.class, FallUpClient::onScreenRender);
    }

    /** A Starfall's streak arrived from the server (client thread). */
    public static void onStreak(OnboardingNet.Streak payload) {
        StarfallSky.add(payload);
    }

    /** This player stepped into a Breach (client thread). */
    public static void onFallUp(OnboardingNet.FallUpStart payload) {
        FallUpClient.start(payload.ticks());
    }

    /**
     * Each client tick of an open Breach: motes rising anywhere over the whole 2 by 2 opening, and the wind's loop
     * ({@link BreachWind}) for a player close by, once for the opening. Only its north-west quarter (the ring's
     * origin) does this.
     */
    public static void breachTick(Level level, BlockPos pos, BlockState state) {
        if (state.hasProperty(BreachBlock.QUARTER) && state.getValue(BreachBlock.QUARTER) != BreachBlock.Quarter.NORTH_WEST) {
            return;
        }
        if (level.random.nextInt(6) == 0) {
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.1 + level.random.nextDouble() * 1.8, pos.getY() + 0.85,
                    pos.getZ() + 0.1 + level.random.nextDouble() * 1.8, 0.0, 0.03 + level.random.nextDouble() * 0.03, 0.0);
        }
        BreachWind.near(level, pos);
    }
}
