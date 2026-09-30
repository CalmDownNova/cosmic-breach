package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.guardian.GuardianRegistry;
import com.cosmicbreach.guardian.colossus.PrismColossus;
import com.cosmicbreach.guardian.colossus.PrismShard;
import com.cosmicbreach.onboarding.BreachRing;
import com.cosmicbreach.onboarding.OnboardingRegistry;
import com.cosmicbreach.registry.ModBlocks;
import com.cosmicbreach.registry.ModItems;
import com.cosmicbreach.registry.ModMaterials;
import com.cosmicbreach.world.AetheriaWorld;
import java.util.Locale;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Two hidden clients on one real server (join mode), synced through chat lines that start with {@code PAIR}. Player A
 * ({@code mp-pair-a}) drives; player B ({@code mp-pair-b}) follows. Both fight one guardian (each a participant); B is
 * dead on the respawn screen when it falls, so A is paid at once and B on respawning. Then both step through the same
 * ring and must land near each other (a shared landing). B leaves at the end; {@code mp-pair-b2} logs B back in and
 * finds its rewards still in its pack and itself where it left. Names: A's and B's player names come from the runs
 * ({@code -Pusername}); each role finds the other as the other player on the server.
 */
public final class MpPairScenario implements Scenario {
    public enum Role { A, B, B_AGAIN }

    private static final Pattern ARENA = Pattern.compile("PAIR arena ([-]?[0-9]+) ([-]?[0-9]+) ([-]?[0-9]+)");
    private static final Pattern RING = Pattern.compile("PAIR ring ([-]?[0-9]+) ([-]?[0-9]+) ([-]?[0-9]+)");
    private static final Pattern BUILT = Pattern.compile("arena centre ([-]?[0-9]+) ([-]?[0-9]+) ([-]?[0-9]+)");
    private final Role role;

    public MpPairScenario(Role role) {
        this.role = role;
    }

    @Override
    public boolean multiplayer() {
        return true;
    }

    @Override
    public int timeBudgetSeconds() {
        return role == Role.B_AGAIN ? 180 : 1200;
    }

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        MpKit.listen();
        steps.check("playing on a real server", MpKit::remote);
        if (role == Role.B_AGAIN) {
            steps.check("the rewards survived leaving and coming back", MpPairScenario::firstKillRewards)
                    .check("back where it left: across", () -> AetheriaWorld.is(mc.level))
                    .check("still on the server", MpKit::remote);
            return;
        }
        boolean a = role == Role.A;
        int[] arena = {0, 0, 0};
        int[] ring = {0, 0, 0};
        int[] mark = {0};
        steps.waitUntil("the other player is here", 2400, () -> other(mc) != null);
        if (a) {
            steps.command("gamemode survival @a")
                    .command("effect clear @a")
                    .command("clear @a")
                    .command("time set noon")
                    .command("weather clear")
                    .command("gamerule doMobSpawning false")
                    .command("gamerule keepInventory false")
                    .command("tp @a 5000 ~ 0 180 0")
                    .waitTicks(80)
                    .run("mark the chat", () -> mark[0] = MpKit.chatSize())
                    .command("cosmicbreach debug lair colossus")
                    .waitUntil("the lair is built", 1200, () -> MpKit.lastMatch(BUILT, mark[0]) != null)
                    .run("tell B where", () -> {
                        String[] g = MpKit.lastMatch(BUILT, mark[0]);
                        MpKit.send("tellraw @a \"PAIR arena %s %s %s\"", g[0], g[1], g[2]);
                    });
        }
        steps.waitUntil("the arena is known", 2400, () -> MpKit.lastMatch(ARENA, 0) != null)
                .run("read it", () -> {
                    String[] g = MpKit.lastMatch(ARENA, 0);
                    for (int i = 0; i < 3; i++) {
                        arena[i] = Integer.parseInt(g[i]);
                    }
                })
                .command("cosmicbreach give")
                .waitUntil("the weapon arrives", 100, () -> MpKit.count(ModItems.MERIDIAN.get()) >= 1)
                .run("hold it", () -> MpKit.select(ModItems.MERIDIAN.get()))
                .run("onto the crown", () -> MpKit.send("tp @s %.1f %d %.1f facing %.1f %d %.1f", arena[0] + (a ? 0.5 : 3.5), arena[1],
                        arena[2] + 8.5, arena[0] + 0.5, arena[1] + 3, arena[2] + 0.5))
                .waitUntil("its fight begins", 1200, () -> {
                    PrismColossus c = MpKit.nearest(PrismColossus.class, 64);
                    return c != null && c.state() == PrismColossus.State.FIGHT;
                });
        if (a) {
            steps.command("cosmicbreach debug colossus hold 1000000")
                    .command("cosmicbreach debug colossus shatter");
        }
        steps.waitUntil("three shards", 400, () -> shards() == 3)
                .waitTicks(40)
                .waitUntil("a shard falls to this player's swings", 1600, MpKit.swingAt(PrismShard.class, 48, () -> shards() <= (a ? 2 : 1)));
        if (a) {
            steps.waitUntil("B is down", 1200, () -> MpKit.chatSince(0, "PAIR b-dead"))
                    .command("kill @e[type=cosmicbreach:prism_shard]")
                    .waitUntil("A's rewards land in the pack at once", 800, MpPairScenario::firstKillRewards)
                    .command("tellraw @a \"PAIR killed\"");
        } else {
            steps.command("kill @s")
                    .waitUntil("dead, on the respawn screen", 100, () -> mc.player.isDeadOrDying() || mc.screen instanceof DeathScreen)
                    .command("tellraw @a \"PAIR b-dead\"")
                    .waitUntil("A reports the kill", 1200, () -> MpKit.chatSince(0, "PAIR killed"))
                    .waitTicks(100)
                    .check("nothing arrived while dead", () -> MpKit.count(ModMaterials.PRISM_HEART.get()) == 0)
                    .run("respawn", () -> {
                        mc.player.respawn();
                        mc.setScreen(null);
                    })
                    .waitUntil("alive again", 200, () -> mc.player != null && mc.player.isAlive() && !(mc.screen instanceof DeathScreen))
                    .waitUntil("B's held rewards land in the pack", 400, MpPairScenario::firstKillRewards)
                    .command("tellraw @a \"PAIR b-back\"");
        }
        // both through one ring
        if (a) {
            steps.waitUntil("B is back on its feet", 1600, () -> MpKit.chatSince(0, "PAIR b-back"))
                    .command("gamemode survival @a")
                    .command("tp @a 0 ~ 0 180 0")
                    .waitTicks(80)
                    .run("a ring", () -> {
                        BlockPos o = mc.player.blockPosition().offset(0, 0, 4);
                        ring[0] = o.getX();
                        ring[1] = o.getY();
                        ring[2] = o.getZ();
                        MpKit.send("fill %d %d %d %d %d %d minecraft:stone", o.getX() - 2, o.getY() - 1, o.getZ() - 2, o.getX() + 3, o.getY() - 1, o.getZ() + 3);
                        MpKit.send("fill %d %d %d %d %d %d minecraft:air", o.getX() - 2, o.getY(), o.getZ() - 2, o.getX() + 3, o.getY() + 4, o.getZ() + 3);
                        for (int[] f : BreachRing.RING) {
                            MpKit.send("setblock %d %d %d cosmicbreach:breach_frame", o.getX() + f[0], o.getY(), o.getZ() + f[1]);
                        }
                    })
                    .waitUntil("its frames stand", 200, () -> mc.level.getBlockState(new BlockPos(ring[0] - 1, ring[1], ring[2] - 1))
                            .is(ModBlocks.BREACH_FRAME.get()))
                    .command("give @s cosmicbreach:starfall_shard")
                    .waitUntil("the item arrives", 100, () -> MpKit.count(OnboardingRegistry.STARFALL_SHARD.get()) == 1)
                    .run("hold it", () -> MpKit.select(OnboardingRegistry.STARFALL_SHARD.get()))
                    .run("south of the ring", () -> MpKit.send("tp @s %.2f %d %.2f facing %.2f %.2f %.2f", ring[0] + 1.0, ring[1], ring[2] + 3.6,
                            ring[0] + 0.5, ring[1] + 0.5, ring[2] + 2.5))
                    .waitTicks(15)
                    .run("look at a south frame's top", () -> MpKit.aim(new Vec3(ring[0] + 0.5, ring[1] + 0.95, ring[2] + 2.5)))
                    .waitTicks(2)
                    .press(mc.options.keyUse)
                    .waitUntil("the ring opens", 100, () -> mc.level.getBlockState(new BlockPos(ring[0], ring[1], ring[2]))
                            .is(OnboardingRegistry.BREACH.get()))
                    .run("tell B", () -> MpKit.send("tellraw @a \"PAIR ring %d %d %d\"", ring[0], ring[1], ring[2]));
        }
        steps.waitUntil("the ring is open", 2400, () -> MpKit.lastMatch(RING, 0) != null)
                .run("read it", () -> {
                    String[] g = MpKit.lastMatch(RING, 0);
                    for (int i = 0; i < 3; i++) {
                        ring[i] = Integer.parseInt(g[i]);
                    }
                })
                .waitTicks(a ? 20 : 40)
                .run("step in", () -> MpKit.send("tp @s %.2f %d %.2f", ring[0] + 1.0, ring[1], ring[2] + 1.0))
                .waitUntil("across", 800, () -> mc.level != null && AetheriaWorld.is(mc.level))
                .waitUntil("landed, still connected", 900, () -> mc.player != null && mc.player.onGround())
                .waitUntil("the other player is across too", 1200, () -> other(mc) != null)
                .waitTicks(100)
                .log("where each landed", () -> String.format(Locale.ROOT, "%.1f blocks apart", mc.player.distanceTo(other(mc))))
                .check("both came down at one landing (within 40 blocks)", () -> mc.player.distanceTo(other(mc)) <= 40.0);
        if (a) {
            steps.waitUntil("B leaves", 2400, () -> other(mc) == null)
                    .check("still on the server", MpKit::remote);
        } else {
            steps.check("still on the server", MpKit::remote);
        }
    }

    private static Player other(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            return null;
        }
        for (Player p : mc.level.players()) {
            if (p != mc.player && p.isAlive()) {
                return p;
            }
        }
        return null;
    }

    private static int shards() {
        return MpKit.near(PrismShard.class, 64, PrismShard::isAlive).size();
    }

    private static boolean firstKillRewards() {
        return MpKit.count(ModMaterials.PRISM_HEART.get()) >= 1 && MpKit.count(GuardianRegistry.HEART_OF_A_DYING_STAR.get()) >= 1;
    }
}
