package com.cosmicbreach.client.fx;

import com.cosmicbreach.combat.data.MoveDef;
import com.cosmicbreach.combat.data.MoveEffect;
import com.cosmicbreach.net.MoveEffectPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/** The client's move effect visuals by id ({@link ClientMoveEffect}), and the calls that reach them. */
public final class ClientMoveEffects {
    private static final Map<ResourceLocation, ClientMoveEffect> HANDLERS = new HashMap<>();

    private ClientMoveEffects() {
    }

    public static void register(ResourceLocation id, ClientMoveEffect handler) {
        if (HANDLERS.putIfAbsent(id, handler) != null) {
            throw new IllegalStateException("duplicate client move effect " + id);
        }
    }

    static void started(Player player, MoveDef def) {
        for (MoveEffect effect : def.traits().effects()) {
            ClientMoveEffect handler = HANDLERS.get(effect.id());
            if (handler != null) {
                handler.started(player, def, effect);
            }
        }
    }

    static void active(Player player, MoveDef def) {
        for (MoveEffect effect : def.traits().effects()) {
            ClientMoveEffect handler = HANDLERS.get(effect.id());
            if (handler != null) {
                handler.active(player, def, effect);
            }
        }
    }

    /** A plunge landed: its effects' visuals. True if one of them drew the landing itself. */
    public static boolean landed(Player player, MoveDef def, double fallBlocks) {
        boolean drawn = false;
        if (!FxParticles.ready()) {
            return false;
        }
        for (MoveEffect effect : def.traits().effects()) {
            ClientMoveEffect handler = HANDLERS.get(effect.id());
            if (handler != null) {
                drawn |= handler.landed(player, def, effect, fallBlocks);
            }
        }
        return drawn;
    }

    /** From the server's {@link MoveEffectPayload} (main thread). */
    public static void onServerMoment(MoveEffectPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        ClientMoveEffect handler = HANDLERS.get(payload.effect());
        if (handler == null || mc.level == null || !FxParticles.ready()) {
            return;
        }
        Entity entity = mc.level.getEntity(payload.entityId());
        handler.serverMoment(entity instanceof Player player ? player : null, payload);
    }
}
