package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.world.AetheriaWorld;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * {@code mp-shrines} (join mode, against a copy of an existing world on a dedicated server): the shrines were placed at
 * the world's known lairs on its first 1.1 start; in front of the first one listed, a right click saves, a death keeps
 * 37 diamonds and level 23, and the player wakes beside it.
 */
public final class MpShrinesScenario implements Scenario {
    private static final Pattern SHRINE = Pattern.compile("shrine (\\w+) (-?\\d+) (-?\\d+) (-?\\d+) (\\w+)");

    @Override
    public boolean multiplayer() {
        return true;
    }

    @Override
    public int timeBudgetSeconds() {
        return 300;
    }

    /** The bottom middle of a listed shrine's block ({kind, x, y, z, facing}). */
    private static Vec3 base(String[] s) {
        return new Vec3(Integer.parseInt(s[1]) + 0.5, Integer.parseInt(s[2]), Integer.parseInt(s[3]) + 0.5);
    }

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        MpKit.listen();
        int[] mark = {0};
        String[][] shrine = {null};
        steps.check("playing on a real server", MpKit::remote)
                .command("gamemode creative")
                .run("mark the chat", () -> mark[0] = MpKit.chatSize())
                .command("cosmicbreach debug shrines list")
                .waitUntil("the server lists a shrine", 100, () -> (shrine[0] = MpKit.lastMatch(SHRINE, mark[0])) != null)
                .log("shrine", () -> "listed: " + String.join(" ", shrine[0]))
                .run("to the front of it", () -> MpKit.send("cosmicbreach debug shrines go %s", shrine[0][0]))
                .waitUntil("there", 400, () -> mc.level != null && AetheriaWorld.is(mc.level)
                        && mc.player.position().distanceTo(base(shrine[0])) < 3.5)
                .waitTicks(60)
                .command("gamemode survival")
                .command("clear @s")
                .command("xp set @s 23 levels")
                .command("give @s minecraft:diamond 37")
                .waitUntil("the diamonds arrived", 100, () -> MpKit.count(Items.DIAMOND) == 37)
                .run("look at the shrine", () -> MpKit.aim(base(shrine[0]).add(0, 0.7, 0)))
                .waitTicks(2)
                .press(mc.options.keyUse)
                .waitTicks(10)
                .run("mark the chat", () -> mark[0] = MpKit.chatSize())
                .command("cosmicbreach debug shrines mine")
                .waitUntil("the save is active", 100, () -> MpKit.chatSince(mark[0], "active true"))
                .command("kill @s")
                .waitUntil("dead", 200, () -> mc.player.isDeadOrDying() || mc.screen instanceof DeathScreen)
                .waitTicks(30)
                .check("nothing of the kept death lies on the floor (no copy, no orbs)", () -> {
                    var box = mc.player.getBoundingBox().inflate(32.0);
                    boolean diamonds = mc.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box)
                            .stream().anyMatch(e -> e.getItem().is(Items.DIAMOND));
                    return !diamonds && mc.level.getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class, box).isEmpty();
                })
                .run("respawn", () -> mc.player.respawn())
                .waitUntil("respawned", 300, () -> {
                    if (mc.player != null && mc.player.isAlive() && mc.screen instanceof DeathScreen) {
                        mc.setScreen(null);
                    }
                    return mc.player != null && mc.player.isAlive() && mc.screen == null;
                })
                .waitTicks(40)
                .check("beside the shrine", () -> mc.player.position().distanceTo(base(shrine[0])) < 3.5)
                .check("the diamonds and the levels came back, each once", () -> MpKit.count(Items.DIAMOND) == 37 && mc.player.experienceLevel == 23)
                .log("kept", () -> "kept " + MpKit.count(Items.DIAMOND) + " diamonds, level " + mc.player.experienceLevel)
                .command("clear @s")
                .command("gamemode creative");
    }
}
