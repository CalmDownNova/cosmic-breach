package com.cosmicbreach.accessory;

import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.MoveInstance;
import com.cosmicbreach.combat.data.MoveKind;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The Choir Pendant's ability hits (GDD 5.2): abilities earn no Resonance by the engine's rules, but with the Pendant
 * each enemy a cast hits gives +3, at most 15 a cast (a cast is one ability move: its pulses and late hits count to
 * it, a second press is a cast of its own). Told of every hit by {@code HitResolver.onStrike}. Its +20 max Resonance is
 * {@link AccessoryHooks#maxResonanceBonus}. Server only.
 */
final class ChoirPendant {
    private static final class Cast {
        int serial = -1;
        int given;
        final Set<Integer> hit = new HashSet<>();
    }

    private static final Map<UUID, Cast> CASTS = new ConcurrentHashMap<>();

    private ChoirPendant() {
    }

    static void onStrike(ServerPlayer player, MoveInstance move, LivingEntity target, Vec3 point) {
        if (move.def().kind() != MoveKind.ABILITY || !Worn.wears(player, Accessory.CHOIR_PENDANT)) {
            return;
        }
        Cast cast = CASTS.computeIfAbsent(player.getUUID(), id -> new Cast());
        if (cast.serial != move.serial()) {
            cast.serial = move.serial();
            cast.given = 0;
            cast.hit.clear();
        }
        if (!cast.hit.add(target.getId())) {
            return; // this enemy already counted for this cast
        }
        int gain = AccessoryRules.pendantGain(cast.given);
        if (gain > 0) {
            cast.given += gain;
            PlayerCombat.of(player).machine().grantResonance(gain);
        }
    }

    /** What the player's latest cast has given so far (the scenario reads it). */
    static int givenThisCast(UUID player) {
        Cast cast = CASTS.get(player);
        return cast == null ? 0 : cast.given;
    }

    static void forget(UUID player) {
        CASTS.remove(player);
    }

    static void clear() {
        CASTS.clear();
    }
}
