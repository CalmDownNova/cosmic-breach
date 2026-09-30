package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.guardian.GuardianRegistry;
import com.cosmicbreach.guardian.colossus.PrismColossus;
import com.cosmicbreach.guardian.colossus.PrismShard;
import com.cosmicbreach.registry.ModItems;
import com.cosmicbreach.registry.ModMaterials;
import java.util.Locale;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;

/**
 * A guardian's rewards on a real server (join mode), where a participant can be dead or gone when the kill lands. A lair
 * is built by command; the player wakes it, shatters it, and brings one shard down with real swings (a participant);
 * the other shards die by command. Then:
 *
 * <ul>
 *   <li>{@code mp-rewards}: alive at the kill, the first-kill items land straight in the pack.</li>
 *   <li>{@code mp-rewards-dead}: dead on the respawn screen at the kill; the rewards wait and arrive on respawning.</li>
 *   <li>{@code mp-rewards-offline}: the setup only; the player leaves with the lair kept loaded, the runner kills the
 *       shards from the server console, and {@code mp-rewards-return} (the same name) then finds the rewards in the pack
 *       on logging back in.</li>
 * </ul>
 * Each run should use its own player name, so each kill is that player's first.
 */
public final class MpRewardsScenario implements Scenario {
    public enum Mode { ALIVE, DEAD, OFFLINE_SETUP, RETURN }

    private static final Pattern ARENA = Pattern.compile("arena centre ([-]?[0-9]+) ([-]?[0-9]+) ([-]?[0-9]+)");
    private final Mode mode;

    public MpRewardsScenario(Mode mode) {
        this.mode = mode;
    }

    @Override
    public boolean multiplayer() {
        return true;
    }

    @Override
    public int timeBudgetSeconds() {
        return mode == Mode.RETURN ? 120 : 600;
    }

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        MpKit.listen();
        steps.check("playing on a real server", MpKit::remote);
        if (mode == Mode.RETURN) {
            steps.waitUntil("the rewards held while away are in the pack", 400, MpRewardsScenario::firstKillRewards)
                    .check("still on the server", MpKit::remote);
            return;
        }
        int[] arena = {0, 0, 0};
        int[] mark = {0};
        int spot = switch (mode) {
            case ALIVE -> 1000;
            case DEAD -> 2000;
            default -> 3000;
        };
        steps.command("gamemode survival")
                .command("effect clear @s")
                .command("clear @s")
                .command("time set noon")
                .command("weather clear")
                .command("gamerule doMobSpawning false")
                .command("gamerule keepInventory false")
                .command("tp @s " + spot + " ~ 0 180 0")
                .waitTicks(60)
                .run("mark the chat", () -> mark[0] = MpKit.chatSize())
                .command("cosmicbreach debug lair colossus")
                .waitUntil("the lair is built", 1200, () -> MpKit.lastMatch(ARENA, mark[0]) != null)
                .run("read the arena", () -> {
                    String[] g = MpKit.lastMatch(ARENA, mark[0]);
                    for (int i = 0; i < 3; i++) {
                        arena[i] = Integer.parseInt(g[i]);
                    }
                })
                .log("the arena", () -> String.format(Locale.ROOT, "arena centre %d %d %d", arena[0], arena[1], arena[2]))
                .command("cosmicbreach give")
                .waitUntil("the weapon arrives", 100, () -> MpKit.count(ModItems.MERIDIAN.get()) >= 1)
                .run("hold it", () -> MpKit.select(ModItems.MERIDIAN.get()))
                .run("onto the crown", () -> MpKit.send("tp @s %.1f %d %.1f facing %.1f %d %.1f", arena[0] + 0.5, arena[1], arena[2] + 8.5,
                        arena[0] + 0.5, arena[1] + 3, arena[2] + 0.5))
                .waitUntil("it wakes", 400, () -> {
                    PrismColossus c = MpKit.nearest(PrismColossus.class, 64);
                    return c != null && c.state() != PrismColossus.State.DORMANT;
                })
                .waitUntil("its fight begins", 800, () -> {
                    PrismColossus c = MpKit.nearest(PrismColossus.class, 64);
                    return c != null && c.state() == PrismColossus.State.FIGHT;
                })
                .command("cosmicbreach debug colossus hold 1000000")
                .command("cosmicbreach debug colossus shatter")
                .waitUntil("three shards", 200, () -> shards() == 3)
                .waitTicks(40)
                .waitUntil("one shard falls to real swings (a participant)", 1200, MpKit.swingAt(PrismShard.class, 48, () -> shards() <= 2));
        switch (mode) {
            case ALIVE -> steps.command("kill @e[type=cosmicbreach:prism_shard]")
                    .waitUntil("the rewards land in the pack", 800, MpRewardsScenario::firstKillRewards)
                    .check("still on the server", MpKit::remote);
            case DEAD -> steps.command("kill @s")
                    .waitUntil("dead, on the respawn screen", 100, () -> mc.player.isDeadOrDying() || mc.screen instanceof DeathScreen)
                    .run("mark the chat", () -> mark[0] = MpKit.chatSize())
                    .command("kill @e[type=cosmicbreach:prism_shard]")
                    .waitUntil("the guardian goes down while this player is dead", 400,
                            () -> MpKit.near(PrismColossus.class, 96, c -> c.isAlive() && c.state() != PrismColossus.State.DYING).isEmpty())
                    .waitTicks(200)
                    .check("nothing arrived while dead", () -> MpKit.count(ModMaterials.PRISM_HEART.get()) == 0)
                    .run("respawn", () -> {
                        mc.player.respawn();
                        mc.setScreen(null);
                    })
                    .waitUntil("alive again", 200, () -> mc.player != null && mc.player.isAlive() && !(mc.screen instanceof DeathScreen))
                    .waitUntil("the held rewards land in the pack", 400, MpRewardsScenario::firstKillRewards)
                    .waitUntil("with a word about it", 200, () -> MpKit.chatSince(mark[0], "Rewards kept for you"))
                    .check("still on the server", MpKit::remote);
            case OFFLINE_SETUP -> steps.run("keep the lair loaded after this player leaves", () ->
                            MpKit.send("forceload add %d %d", arena[0], arena[2]))
                    .waitTicks(20)
                    .log("offline setup", () -> String.format(Locale.ROOT,
                            "OFFLINE-SETUP arena %d %d %d: now kill the shards from the server console, then run mp-rewards-return",
                            arena[0], arena[1], arena[2]));
            default -> {
            }
        }
    }

    private static int shards() {
        return MpKit.near(PrismShard.class, 64, PrismShard::isAlive).size();
    }

    private static boolean firstKillRewards() {
        return MpKit.count(ModMaterials.PRISM_HEART.get()) >= 1 && MpKit.count(GuardianRegistry.HEART_OF_A_DYING_STAR.get()) >= 1;
    }
}
