package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Blocks;

/** The harness checking itself: the world loads with our data, the game renders, a screenshot comes out. */
public final class SmokeScenario implements Scenario {
    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        steps.check("the player stands on superflat grass",
                        () -> mc.level.getBlockState(mc.player.blockPosition().below()).is(Blocks.GRASS_BLOCK))
                .check("the world loaded the mod's data pack",
                        () -> mc.getSingleplayerServer().getResourceManager()
                                .getResource(CosmicBreach.id("combat/weapons/meridian.json")).isPresent())
                .waitTicks(40)
                .screenshot("spawn");
    }

    @Override
    public int timeBudgetSeconds() {
        return 60;
    }
}
