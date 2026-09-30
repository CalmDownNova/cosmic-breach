package com.cosmicbreach.structure.sanctum;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Who the Gate's veil lets through, on either side. The server knows (the attunement or the admitted party,
 * {@link SanctumGate#passes}); a client only simulates its own player, so it keeps the one answer the server sent it
 * ({@code SanctumNet.Pass}).
 */
public final class SanctumPasses {
    /** The local player's pass, set by the client's payload handler. */
    private static volatile boolean clientPass;

    private SanctumPasses() {
    }

    public static boolean passes(Player player) {
        if (player.level().isClientSide()) {
            return clientPass;
        }
        return player instanceof ServerPlayer sp && SanctumGate.passes(sp);
    }

    public static void setClient(boolean pass) {
        clientPass = pass;
    }

    public static boolean client() {
        return clientPass;
    }
}
