package com.cosmicbreach.gear.regalia;

import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.MoveInstance;
import com.cosmicbreach.combat.data.MoveKind;
import com.cosmicbreach.combat.data.WeaponDef;
import com.cosmicbreach.gear.set.ArmorSet;
import com.cosmicbreach.gear.set.ArmorSets;
import com.cosmicbreach.gear.set.SetState;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * Harmonics (GDD 5.1), the Choir Regalia's four-piece bonus: the wearer's weapon ability casts (the first press;
 * a second press like the Edges' blink doesn't count) are counted over 10 s, and the third within that window
 * echoes ({@link Echoes}) 10 ticks later, at 60% power and free. The set's meter holds the count for the halo (2:
 * primed).
 */
public final class Harmonics {
    private static final Map<UUID, RegaliaRules.Harmonics> COUNTS = new ConcurrentHashMap<>();
    private static final long FOREVER = 1L << 50;

    private Harmonics() {
    }

    /** A move the wearer (in the full set) started: a cast of its weapon's ability counts. */
    static void onMoveStarted(ServerPlayer player, PlayerCombat combat, MoveInstance move) {
        WeaponDef weapon = combat.machine().weapon();
        if (weapon == null || move.def().kind() != MoveKind.ABILITY
                || weapon.ability().map(a -> !a.move().equals(move.id())).orElse(true)) {
            return;
        }
        long now = player.level().getGameTime();
        RegaliaRules.Harmonics count = COUNTS.computeIfAbsent(player.getUUID(), id -> new RegaliaRules.Harmonics());
        if (count.cast(now)) {
            Echoes.schedule(player, combat, weapon, move, now + RegaliaRules.ECHO_DELAY);
        }
        show(player, count, now);
    }

    /** Every server tick while a piece is worn: the count's window slides, and the halo hears of it. */
    static void tick(ServerPlayer player, int pieces, long now) {
        RegaliaRules.Harmonics count = COUNTS.get(player.getUUID());
        if (count == null) {
            return;
        }
        if (pieces < ArmorSet.FULL_SET) {
            count.clear();
        }
        show(player, count, now);
    }

    /** The set's meter follows the count (rewritten only when it changes). */
    private static void show(ServerPlayer player, RegaliaRules.Harmonics count, long now) {
        int n = count.count(now);
        SetState state = ArmorSets.state(player);
        if (state.meter(ChoirRegalia.SET.id(), now) != n) {
            ArmorSets.setState(player, state.withMeter(ChoirRegalia.SET.id(), n, now + FOREVER));
        }
    }

    /** Casts counted for {@code player} now (tests and scenarios). */
    public static int count(ServerPlayer player) {
        RegaliaRules.Harmonics count = COUNTS.get(player.getUUID());
        return count == null ? 0 : count.count(player.level().getGameTime());
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        COUNTS.clear();
    }
}
