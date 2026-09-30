package com.cosmicbreach.combat.server;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Multiplayer friendly fire beyond players themselves: another player's pet or mount is as safe from a player's hits,
 * area effects and pulls as that player is (no PvP, or the same team), and so is anything such a player is riding. And a
 * mount another player rides is never pushed by the server, whatever the PvP setting: its rider's own client moves it,
 * and a server-side push would only rubber-band it. {@link #shelters} is the pure rule; the rest reads the world.
 */
public final class TargetShield {
    private TargetShield() {
    }

    /**
     * True if the attacker may not touch a target owned by {@code owner} (null for nobody) and ridden by a player the
     * attacker may not hurt ({@code carriesProtectedRider}). {@code ownerHarmable} is whether the attacker may hurt the
     * owner, or null if the owner is offline, when the server's PvP setting decides. The attacker's own things are not
     * this rule's business (see {@link HitResolver#isValidTarget}).
     */
    public static boolean shelters(UUID attacker, @Nullable UUID owner, @Nullable Boolean ownerHarmable, boolean pvpAllowed,
                                   boolean carriesProtectedRider) {
        if (carriesProtectedRider) {
            return true;
        }
        if (owner == null || owner.equals(attacker)) {
            return false;
        }
        return !(ownerHarmable != null ? ownerHarmable : pvpAllowed);
    }

    /** True if {@code target} belongs to, or carries, another player whom {@code attacker} may not hurt. */
    public static boolean shelters(ServerPlayer attacker, Entity target) {
        UUID owner = target instanceof OwnableEntity owned ? owned.getOwnerUUID() : null;
        Boolean ownerHarmable = null;
        if (owner != null && !owner.equals(attacker.getUUID())) {
            Player p = attacker.server.getPlayerList().getPlayer(owner);
            ownerHarmable = p == null ? null : attacker.canHarmPlayer(p);
        }
        boolean protectedRider = false;
        for (Entity rider : target.getPassengers()) {
            if (rider instanceof Player p && p != attacker && !attacker.canHarmPlayer(p)) {
                protectedRider = true;
                break;
            }
        }
        return shelters(attacker.getUUID(), owner, ownerHarmable, attacker.server.isPvpAllowed(), protectedRider);
    }

    /** True if a player other than {@code attacker} rides {@code target}: the server must not push it (its rider's client moves it). */
    public static boolean riddenByAnotherPlayer(Entity target, Player attacker) {
        for (Entity rider : target.getPassengers()) {
            if (rider instanceof Player p && p != attacker) {
                return true;
            }
        }
        return false;
    }
}
