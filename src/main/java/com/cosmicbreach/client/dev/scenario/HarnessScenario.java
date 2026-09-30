package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Exercises every kind of step in the real game, so later scenarios can trust the primitives. */
public final class HarnessScenario implements Scenario {
    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        Options keys = mc.options;
        Vec3[] start = {Vec3.ZERO};
        steps.command("give @s minecraft:stone 3")
                .waitForChat("Gave 3 .*Stone", 40)
                .waitUntil("3 stone in the inventory", 20, () -> mc.player.getInventory().countItem(Items.STONE) == 3)
                .look(90, -20)
                .check("facing west and looking up", () -> Mth.equal(mc.player.getYRot(), 90) && Mth.equal(mc.player.getXRot(), -20))
                .check("the mouse counts as grabbed, as in a focused game", () -> mc.mouseHandler.isMouseGrabbed())
                .run("remember the start position", () -> start[0] = mc.player.position())
                .press(keys.keyUp, 20)
                .check("walking forward for 20 ticks moved the player at least 2 blocks west", () -> start[0].x - mc.player.getX() > 2.0)
                .press(keys.keyJump)
                .waitUntil("the player is in the air", 5, () -> !mc.player.onGround())
                .waitUntil("the player landed", 40, () -> mc.player.onGround())
                .press(keys.keyAttack)
                .check("the attack click swung the arm", () -> mc.player.swinging)
                .look(90, 15)
                .screenshot("harness");
    }

    @Override
    public int timeBudgetSeconds() {
        return 60;
    }
}
