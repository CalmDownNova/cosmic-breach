package com.cosmicbreach.onboarding;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Breach's block entity: it carries no data; it is there so the client can draw the sky in the hole
 * ({@code BreachRenderer}) and, client side, let it breathe (motes rising out of the hole, the wind coming up
 * through it for a player close by: {@code client.onboarding.OnboardingClient#breachTick}).
 */
public class BreachBlockEntity extends BlockEntity {
    public BreachBlockEntity(BlockPos pos, BlockState state) {
        super(OnboardingRegistry.BREACH_ENTITY.get(), pos, state);
    }

    static void clientTick(Level level, BlockPos pos, BlockState state, BreachBlockEntity breach) {
        // a static call into the client package: only resolved (and loaded) on a client
        com.cosmicbreach.client.onboarding.OnboardingClient.breachTick(level, pos, state);
    }
}
