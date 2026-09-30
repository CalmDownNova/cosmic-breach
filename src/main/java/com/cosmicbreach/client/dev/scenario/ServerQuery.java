package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.dev.Steps;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerPlayer;

/** Reads this player's server-side state from the integrated server, on the server thread (dev tests only). */
final class ServerQuery {
    private ServerQuery() {}

    /** {@code query} applied to this client's player on the server; fails the scenario if it can't. */
    static <T> T ask(Function<ServerPlayer, T> query) {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            throw new Steps.Failure("there is no integrated server");
        }
        UUID id = mc.player.getUUID();
        try {
            return server.submit(() -> {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player == null) {
                    throw new IllegalStateException("the server has no player " + id);
                }
                return query.apply(player);
            }).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new Steps.Failure("could not read the server's state: " + e);
        }
    }
}
