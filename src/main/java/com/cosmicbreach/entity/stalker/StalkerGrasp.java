package com.cosmicbreach.entity.stalker;

import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatEvent;
import com.cosmicbreach.combat.server.CombatHooks;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jetbrains.annotations.Nullable;

/**
 * Who a Hollow Stalker holds (GDD 7.1, Grasp): a held player can't walk or jump (its movement speed and jump strength
 * are multiplied by zero while held; both attributes reach its client), and its first dash or parry breaks the hold,
 * heard as a combat event on the server. One Stalker holds a player at a time.
 */
public final class StalkerGrasp {
    private static final Map<UUID, HollowStalker> HELD = new ConcurrentHashMap<>();

    private StalkerGrasp() {
    }

    /** Hooks the dash and the parry that break a hold. */
    public static void register() {
        CombatHooks.register(new CombatHooks.Hook() {
            @Override
            public void onCombatEvent(ServerPlayer player, PlayerCombat combat, CombatEvent event) {
                if ((event instanceof CombatEvent.DashStarted || event instanceof CombatEvent.ParryStarted) && HELD.containsKey(player.getUUID())) {
                    breakFree(player);
                }
            }
        });
    }

    /** {@code stalker} takes hold of {@code player}; false if another already holds it. */
    static boolean hold(ServerPlayer player, HollowStalker stalker) {
        HollowStalker other = HELD.get(player.getUUID());
        if (other != null && other != stalker && other.isAlive() && !other.isRemoved()) {
            return false;
        }
        HELD.put(player.getUUID(), stalker);
        pin(player, true);
        return true;
    }

    /** {@code stalker} lets go of {@code player}. */
    static void let(ServerPlayer player, HollowStalker stalker) {
        if (HELD.remove(player.getUUID(), stalker)) {
            pin(player, false);
        }
    }

    /** The player broke free (a dash or a parry). */
    public static void breakFree(ServerPlayer player) {
        HollowStalker stalker = HELD.get(player.getUUID());
        if (stalker != null) {
            stalker.releaseGrasp();
        }
        if (HELD.remove(player.getUUID()) != null) {
            pin(player, false);
        }
    }

    /** The Stalker holding {@code player}, or null. */
    public static @Nullable HollowStalker holder(ServerPlayer player) {
        return HELD.get(player.getUUID());
    }

    /** True while a Stalker holds {@code player}. */
    public static boolean isHeld(ServerPlayer player) {
        return HELD.containsKey(player.getUUID());
    }

    /** A player leaving the world is let go (its modifiers would otherwise stay). */
    public static void forget(ServerPlayer player) {
        HELD.remove(player.getUUID());
        pin(player, false);
    }

    private static void pin(ServerPlayer player, boolean on) {
        for (AttributeInstance a : new AttributeInstance[] {player.getAttribute(Attributes.MOVEMENT_SPEED),
                player.getAttribute(Attributes.JUMP_STRENGTH)}) {
            if (a == null) {
                continue;
            }
            a.removeModifier(HollowStalker.holdModifier().id());
            if (on) {
                a.addTransientModifier(HollowStalker.holdModifier());
            }
        }
    }
}
