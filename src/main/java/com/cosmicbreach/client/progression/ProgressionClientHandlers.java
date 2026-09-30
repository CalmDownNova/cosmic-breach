package com.cosmicbreach.client.progression;

import com.cosmicbreach.progression.net.LevelUpPayload;
import com.cosmicbreach.progression.net.XpGainedPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client handlers for the progression payloads (client only, reached through lambdas). */
public final class ProgressionClientHandlers {
    private ProgressionClientHandlers() {
    }

    /** A player gained a level: the burst on it for everyone, and the message for that player. */
    public static void levelUp(LevelUpPayload payload, IPayloadContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Entity entity = mc.level.getEntity(payload.entityId());
        if (entity != null) {
            LevelUpEffects.burst(entity);
        }
        if (mc.player != null && payload.entityId() == mc.player.getId()) {
            AttunementHud.showLevelUp(payload.level());
        }
    }

    /** The local player gained XP (its synced state already has it): show the XP line. */
    public static void xpGained(XpGainedPayload payload, IPayloadContext context) {
        AttunementHud.showXpGain();
    }
}
