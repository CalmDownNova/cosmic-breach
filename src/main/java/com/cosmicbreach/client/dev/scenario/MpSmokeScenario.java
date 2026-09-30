package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.onboarding.BreachRing;
import com.cosmicbreach.onboarding.OnboardingRegistry;
import com.cosmicbreach.registry.ModBlocks;
import com.cosmicbreach.world.AetheriaWorld;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * {@code mp-smoke} (join mode): the short check before a real server goes live. The combat runtime's own server-side
 * checks for this remote player, then the way in: a ring set down by command, opened with the item by hand, the fall
 * up, and a landing that must not end in vanilla's anti-flying kick; the player stays connected a while after.
 */
public final class MpSmokeScenario implements Scenario {
    @Override
    public boolean multiplayer() {
        return true;
    }

    @Override
    public int timeBudgetSeconds() {
        return 420;
    }

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        MpKit.listen();
        int[] mark = {0};
        BlockPos[] ring = {null};
        long[] arrived = {0L};
        steps.check("playing on a real server", MpKit::remote)
                .command("gamemode survival")
                .command("effect clear @s")
                .command("clear @s")
                .command("time set noon")
                .command("weather clear")
                .command("gamerule doMobSpawning false")
                .waitTicks(20)
                .run("mark the chat", () -> mark[0] = MpKit.chatSize())
                .command("cosmicbreach selftest")
                .waitUntil("the selftest prints its verdict", 2400, () -> MpKit.chatSince(mark[0], "SELFTEST "))
                .check("the selftest passed", () -> MpKit.chatSince(mark[0], "SELFTEST PASS"))
                .waitTicks(40)
                .command("clear @s")
                .run("the ring's site, four blocks south", () -> {
                    BlockPos o = mc.player.blockPosition().offset(0, 0, 4);
                    ring[0] = o;
                    MpKit.send("fill %d %d %d %d %d %d minecraft:stone", o.getX() - 2, o.getY() - 1, o.getZ() - 2, o.getX() + 3, o.getY() - 1, o.getZ() + 3);
                    MpKit.send("fill %d %d %d %d %d %d minecraft:air", o.getX() - 2, o.getY(), o.getZ() - 2, o.getX() + 3, o.getY() + 4, o.getZ() + 3);
                    for (int[] f : BreachRing.RING) {
                        MpKit.send("setblock %d %d %d cosmicbreach:breach_frame", o.getX() + f[0], o.getY(), o.getZ() + f[1]);
                    }
                })
                .waitUntil("all twelve frames stand", 200, () -> {
                    for (int[] f : BreachRing.RING) {
                        if (!mc.level.getBlockState(ring[0].offset(f[0], 0, f[1])).is(ModBlocks.BREACH_FRAME.get())) {
                            return false;
                        }
                    }
                    return true;
                })
                .command("give @s cosmicbreach:starfall_shard")
                .waitUntil("the item arrives", 100, () -> MpKit.count(OnboardingRegistry.STARFALL_SHARD.get()) == 1)
                .run("hold it", () -> MpKit.select(OnboardingRegistry.STARFALL_SHARD.get()))
                .run("stand south of the ring", () -> MpKit.send("tp @s %.2f %d %.2f facing %.2f %.2f %.2f", ring[0].getX() + 1.0,
                        ring[0].getY(), ring[0].getZ() + 3.6, ring[0].getX() + 0.5, ring[0].getY() + 0.5, ring[0].getZ() + 2.5))
                .waitTicks(15)
                .run("look at a south frame's top", () -> MpKit.aim(new Vec3(ring[0].getX() + 0.5, ring[0].getY() + 0.95, ring[0].getZ() + 2.5)))
                .waitTicks(2)
                .press(mc.options.keyUse)
                .waitUntil("the ring opens", 100, () -> mc.level.getBlockState(ring[0]).is(OnboardingRegistry.BREACH.get()))
                .run("step into the opening", () -> MpKit.send("tp @s %.2f %d %.2f", ring[0].getX() + 1.0, ring[0].getY(), ring[0].getZ() + 1.0))
                .waitUntil("across", 800, () -> mc.level != null && AetheriaWorld.is(mc.level))
                .run("the clock starts", () -> arrived[0] = mc.level.getGameTime())
                .waitUntil("landed, still connected", 900, () -> mc.player != null && mc.player.onGround())
                .log("the arrival", () -> String.format(Locale.ROOT, "airborne %d ticks after arriving", mc.level.getGameTime() - arrived[0]))
                .waitTicks(200)
                .check("still on the server ten seconds after landing", MpKit::remote);
    }
}
