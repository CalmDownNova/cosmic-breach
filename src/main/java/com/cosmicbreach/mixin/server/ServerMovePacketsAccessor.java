package com.cosmicbreach.mixin.server;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * How many movement packets a player's client has sent, in all and as of the server's last tick: each is one tick
 * of the client's movement, so the traps can time a player's speed by the client's ticks, which a busy server can
 * bunch but never change ({@link com.cosmicbreach.structure.trap.KineticTripwires#movePackets}). Read-only access,
 * nothing changed.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public interface ServerMovePacketsAccessor {
    @Accessor("receivedMovePacketCount")
    int cosmicbreach$receivedMovePackets();

    @Accessor("knownMovePacketCount")
    int cosmicbreach$knownMovePackets();
}
