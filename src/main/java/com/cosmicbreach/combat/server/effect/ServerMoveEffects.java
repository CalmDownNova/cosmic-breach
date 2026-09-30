package com.cosmicbreach.combat.server.effect;

import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatEvent;
import com.cosmicbreach.combat.core.MoveInstance;
import com.cosmicbreach.combat.data.MoveDef;
import com.cosmicbreach.combat.data.MoveEffect;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * The server's move effect handlers by id, and the dispatch from a player's state machine events to
 * them ({@link com.cosmicbreach.combat.server.CombatServerEvents} calls {@link #dispatch} for every
 * event, before the engine's own handling). An effect id in the data with no handler here does nothing
 * on the server (it may be purely visual) and is logged once.
 */
public final class ServerMoveEffects {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ResourceLocation, ServerMoveEffect> HANDLERS = new ConcurrentHashMap<>();
    private static final Set<ResourceLocation> REPORTED = ConcurrentHashMap.newKeySet();

    private ServerMoveEffects() {
    }

    public static void register(ResourceLocation id, ServerMoveEffect handler) {
        if (HANDLERS.putIfAbsent(id, handler) != null) {
            throw new IllegalStateException("duplicate server move effect " + id);
        }
    }

    public static @Nullable ServerMoveEffect get(ResourceLocation id) {
        return HANDLERS.get(id);
    }

    /** True if one of the move's effects deals its damage itself: the engine skips the hitbox. */
    public static boolean dealsItsOwnHits(MoveDef def) {
        for (MoveEffect effect : def.traits().effects()) {
            ServerMoveEffect handler = HANDLERS.get(effect.id());
            if (handler != null && handler.dealsItsOwnHits()) {
                return true;
            }
        }
        return false;
    }

    /** One state machine event of {@code player}: to the handlers of the effects of the move it concerns. */
    public static void dispatch(ServerPlayer player, PlayerCombat combat, CombatEvent event, long now) {
        switch (event) {
            case CombatEvent.MoveStarted e -> each(player, combat, e.move(), now, (h, use) -> h.started(use));
            case CombatEvent.ActiveTick e -> each(player, combat, e.move(), now, (h, use) -> h.activeTick(use, e.activeTick()));
            case CombatEvent.PlungeLanded e -> each(player, combat, e.move(), now, (h, use) -> h.landed(use, e.fallBlocks()));
            case CombatEvent.MoveCancelled e ->
                    each(player, combat, e.move(), now, (h, use) -> h.cancelled(use, e.by(), e.stage(), e.tick()));
            case CombatEvent.MoveEnded e -> each(player, combat, e.move(), now, (h, use) -> h.ended(use, e.cancelled()));
            default -> {
                // the rest concern no move
            }
        }
    }

    private static void each(ServerPlayer player, PlayerCombat combat, MoveInstance move, long now,
                             BiConsumer<ServerMoveEffect, ServerMoveEffect.Use> hook) {
        for (MoveEffect effect : move.def().traits().effects()) {
            ServerMoveEffect handler = HANDLERS.get(effect.id());
            if (handler == null) {
                if (REPORTED.add(effect.id())) {
                    LOGGER.debug("[cosmicbreach] no server handler for move effect {} (visual only?)", effect.id());
                }
                continue;
            }
            hook.accept(handler, new ServerMoveEffect.Use(player, combat, move, effect, now));
        }
    }

    /** Every registered id, for tests and tools. */
    public static Set<ResourceLocation> ids() {
        return new HashSet<>(HANDLERS.keySet());
    }
}
