package com.cosmicbreach.client.net;

import com.cosmicbreach.client.combat.ClientCombat;
import com.cosmicbreach.client.fx.ClientMoveEffects;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.data.CombatData;
import com.cosmicbreach.net.CombatDataSyncPayload;
import com.cosmicbreach.net.CombatFxPayload;
import com.cosmicbreach.net.CombatSyncPayload;
import com.cosmicbreach.net.HitFxPayload;
import com.cosmicbreach.net.MoveEffectPayload;
import com.cosmicbreach.net.MoveStartedPayload;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

/**
 * Client handlers for the server's combat payloads (client only; see {@code ModNetworking} for why
 * this class is only reached through lambdas). They run on the client's main thread; the feel lives
 * in {@link ClientCombat}.
 */
public final class ClientPayloadHandlers {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ClientPayloadHandlers() {
    }

    /** Every move and weapon: fills the client's {@link CombatData}. */
    public static void combatData(CombatDataSyncPayload payload, IPayloadContext context) {
        CombatData.client().replace(payload.moves(), payload.weapons());
        LOGGER.info("[cosmicbreach] received {} combat moves and {} weapons from the server",
                payload.moves().size(), payload.weapons().size());
    }

    /** The server's Resonance, dash charges and ability cooldown for the local player. */
    public static void combatSync(CombatSyncPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (player != null) {
            PlayerCombat.of(player).machine().syncFromServer(payload.resonance(), payload.dashCharges(), payload.abilityCooldown());
        }
    }

    /** Another player started a move: its animation and its slash trail, which follows its blade. */
    public static void moveStarted(MoveStartedPayload payload, IPayloadContext context) {
        ClientCombat.onMoveStarted(payload);
    }

    /** A hit landed: sparks, hit-stop and camera shake (the server plays the hit's sound for everyone). */
    public static void hitFx(HitFxPayload payload, IPayloadContext context) {
        ClientCombat.onHitFx(payload);
    }

    /**
     * A parry, perfect dodge, dash, stagger, raised parry, charge or plunge landing: animations and
     * effects, and the local machine kept in step.
     */
    public static void combatFx(CombatFxPayload payload, IPayloadContext context) {
        ClientCombat.onCombatFx(payload);
    }

    /** A moment of a move effect only the server knows (a well planted, a Collapse, a crater): its visuals. */
    public static void moveEffect(MoveEffectPayload payload, IPayloadContext context) {
        ClientMoveEffects.onServerMoment(payload);
    }
}
