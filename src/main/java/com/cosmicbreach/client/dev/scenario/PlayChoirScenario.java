package com.cosmicbreach.client.dev.scenario;

import static com.cosmicbreach.client.dev.scenario.CryptKit.player;
import static com.cosmicbreach.client.dev.scenario.CryptKit.server;

import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.structure.choir.ChoirDifficulty;
import com.cosmicbreach.structure.crypt.CryptCommands;
import com.cosmicbreach.structure.crypt.CryptConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * Not a test: puts Nate at a Choir Floor's door in a visible client with sound, to play it by hand
 * ({@code ./gradlew runClient -Pplay=play-choir}). Peaceful, so a Discord's damage heals back. The config as
 * shipped (any pace). It then waits while he plays; closing the game ends it.
 */
public final class PlayChoirScenario implements Scenario {
    private static final int HOURS = 3;

    @Override
    public int timeBudgetSeconds() {
        return HOURS * 3600;
    }

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        BlockPos[] conductor = new BlockPos[1];
        steps.command("gamerule sendCommandFeedback false")
                .command("gamerule doDaylightCycle false")
                .command("time set 6000")
                .command("weather clear")
                .command("difficulty peaceful")
                .command("gamemode creative")
                .look(0, 0)
                .waitTicks(5)
                .run("the config as shipped", () -> {
                    CryptConfig.overrideAnyPace(null);
                    CryptConfig.override(null);
                })
                .run("build a Choir Floor ahead", () -> conductor[0] = server(srv -> CryptCommands.placeChoirAhead(player(srv), 7L, ChoirDifficulty.CRYPT)))
                .waitTicks(10)
                .run("to the door, facing the Conductor", () -> {
                    Vec3 centre = Vec3.atBottomCenterOf(conductor[0]);
                    CryptKit.tp(mc, centre.add(0, 0, -8.0), centre.add(0, 1, 0));
                })
                .command("gamemode survival")
                .run("full health", CryptKit::heal)
                .run("tell Nate", () -> {
                    mc.player.displayClientMessage(Component.literal("Choir Floor test (any pace). Walk onto the ring to wake it."), false);
                    mc.player.displayClientMessage(Component.literal("Fresh room: /cosmicbreach debug place choir   Creative: /gamemode creative"), false);
                })
                .waitUntil("Nate plays (close the game when done)", HOURS * 3600 * 20 - 200, () -> false);
    }
}
