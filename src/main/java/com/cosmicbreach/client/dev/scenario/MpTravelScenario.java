package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.mount.LumenStag;
import com.cosmicbreach.mount.Mounts;
import com.cosmicbreach.onboarding.BreachRing;
import com.cosmicbreach.onboarding.OnboardingRegistry;
import com.cosmicbreach.registry.ModBlocks;
import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.Layer;
import com.cosmicbreach.world.weather.CosmicWeather;
import com.cosmicbreach.world.weather.WeatherKind;
import java.util.Locale;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * {@code mp-travel} (join mode): the way in and back on a real server, where vanilla's anti-flying kick is live (a
 * single-player world always allows flight). A ring of frames is set down by command and opened with the item by hand;
 * the player steps in, falls up, and must land and stay connected; then the dimension's weather reaches this client, a
 * mount is tamed, saddled and ridden (a jump), and the player goes home through the arrival's return ring.
 */
public final class MpTravelScenario implements Scenario {
    @Override
    public boolean multiplayer() {
        return true;
    }

    @Override
    public int timeBudgetSeconds() {
        return 600;
    }

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        MpKit.listen();
        BlockPos[] ring = {null};
        long[] marks = {0L, 0L};
        LumenStag[] stag = {null};
        BlockPos[] home = {null};
        steps.check("playing on a real server", MpKit::remote)
                .command("gamemode survival")
                .command("effect clear @s")
                .command("clear @s")
                .command("time set noon")
                .command("weather clear")
                .command("gamerule doMobSpawning false")
                .waitTicks(20)
                // a ring set down by command, four blocks south
                .run("the ring's site", () -> {
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
                .waitUntil("the shard arrives", 100, () -> MpKit.count(OnboardingRegistry.STARFALL_SHARD.get()) == 1)
                .run("hold it", () -> MpKit.select(OnboardingRegistry.STARFALL_SHARD.get()))
                .run("stand south of the ring", () -> MpKit.send("tp @s %.2f %d %.2f facing %.2f %.2f %.2f", ring[0].getX() + 1.0,
                        ring[0].getY(), ring[0].getZ() + 3.6, ring[0].getX() + 0.5, ring[0].getY() + 0.5, ring[0].getZ() + 2.5))
                .waitTicks(15)
                .run("look at a south frame's top", () -> MpKit.aim(new Vec3(ring[0].getX() + 0.5, ring[0].getY() + 0.95, ring[0].getZ() + 2.5)))
                .waitTicks(2)
                .press(mc.options.keyUse)
                .waitUntil("the ring opens", 100, () -> mc.level.getBlockState(ring[0]).is(OnboardingRegistry.BREACH.get()))
                .check("the item was spent", () -> MpKit.count(OnboardingRegistry.STARFALL_SHARD.get()) == 0)
                // in
                .run("step into the opening", () -> MpKit.send("tp @s %.2f %d %.2f", ring[0].getX() + 1.0, ring[0].getY(), ring[0].getZ() + 1.0))
                .waitUntil("across", 800, () -> mc.level != null && AetheriaWorld.is(mc.level))
                .run("the clock starts", () -> marks[0] = mc.level.getGameTime())
                .waitUntil("landed, still connected", 900, () -> mc.player != null && mc.player.onGround())
                .log("the arrival", () -> String.format(Locale.ROOT, "airborne %d ticks after arriving, landed at %s",
                        mc.level.getGameTime() - marks[0], mc.player.blockPosition().toShortString()))
                .waitTicks(100)
                .check("still on the server after the arrival", MpKit::remote)
                .run("remember the landing", () -> home[0] = mc.player.blockPosition())
                // the dimension's weather reaches this client
                .command("cosmicbreach weather flare")
                .waitUntil("the weather reached this client", 300, () -> CosmicWeather.client(Layer.REACH).any(WeatherKind.FLARE))
                .command("cosmicbreach weather clear")
                .waitUntil("and its end", 300, () -> CosmicWeather.client(Layer.REACH).calm())
                // a mount: tamed by command, saddled and mounted by hand, a jump
                .command("cosmicbreach debug mount spawn stag 1")
                .waitUntil("a mount is near", 200, () -> (stag[0] = MpKit.nearest(LumenStag.class, 24)) != null)
                .command("cosmicbreach debug mount tame")
                .waitUntil("it is tamed", 200, () -> stag[0].isTamed())
                .command("give @s cosmicbreach:astral_saddle")
                .waitUntil("the saddle arrives", 100, () -> MpKit.count(Mounts.ASTRAL_SADDLE.get()) == 1)
                .run("hold it", () -> MpKit.select(Mounts.ASTRAL_SADDLE.get()))
                .run("stand beside the mount", () -> MpKit.send("tp @s %.2f %.2f %.2f facing entity %s", stag[0].getX() + 1.8, stag[0].getY(),
                        stag[0].getZ(), stag[0].getStringUUID()))
                .waitTicks(15)
                .run("look at it", () -> MpKit.aim(stag[0].position().add(0, stag[0].getBbHeight() * 0.6, 0)))
                .waitTicks(2)
                .press(mc.options.keyUse)
                .waitUntil("it is saddled", 100, () -> stag[0].isSaddled())
                .run("an empty hand", () -> KeyMapping.click(mc.options.keyHotbarSlots[8].getKey()))
                .waitTicks(3)
                .run("look at it again", () -> MpKit.aim(stag[0].position().add(0, stag[0].getBbHeight() * 0.6, 0)))
                .waitTicks(2)
                .press(mc.options.keyUse)
                .waitUntil("riding it", 100, () -> mc.player.getVehicle() instanceof LumenStag)
                .hold(mc.options.keyUp)
                .waitTicks(30)
                .press(mc.options.keyJump, 4)
                .waitTicks(40)
                .release(mc.options.keyUp)
                .waitTicks(40)
                .check("still riding and on the server", () -> mc.player.getVehicle() instanceof LumenStag && MpKit.remote())
                .press(mc.options.keyShift, 3)
                .waitUntil("off it", 60, () -> mc.player.getVehicle() == null)
                // home through the arrival's return ring
                .run("into the return ring", () -> {
                    BlockPos breach = nearestBreach(mc, home[0], 40);
                    if (breach == null) {
                        throw new Steps.Failure("no return ring within 40 blocks of the landing");
                    }
                    BlockPos o = com.cosmicbreach.onboarding.BreachBlock.origin(mc.level.getBlockState(breach), breach);
                    MpKit.send("tp @s %.2f %d %.2f", o.getX() + 1.0, o.getY(), o.getZ() + 1.0);
                })
                .waitUntil("home", 800, () -> mc.level != null && mc.level.dimension() == Level.OVERWORLD)
                .waitUntil("on the ground at home", 600, () -> mc.player != null && mc.player.onGround())
                .check("still on the server at the end", MpKit::remote);
    }

    /** The nearest open ring's block within {@code range} of {@code around} that this client has loaded, or null. */
    private static BlockPos nearestBreach(Minecraft mc, BlockPos around, int range) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(around.offset(-range, -12, -range), around.offset(range, 12, range))) {
            if (mc.level.getBlockState(p).is(OnboardingRegistry.BREACH.get())) {
                double d = p.distSqr(around);
                if (d < bestD) {
                    bestD = d;
                    best = p.immutable();
                }
            }
        }
        return best;
    }
}
