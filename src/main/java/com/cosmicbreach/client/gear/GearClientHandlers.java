package com.cosmicbreach.client.gear;

import com.cosmicbreach.client.fx.VanguardVisuals;
import com.cosmicbreach.gear.net.GearFxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client handlers of the gear payloads (main thread). Reached only through lambdas, so a server never loads it. */
public final class GearClientHandlers {
    private GearClientHandlers() {
    }

    public static void fx(GearFxPayload payload, IPayloadContext context) {
        Minecraft mc = Minecraft.getInstance();
        boolean own = mc.player != null && mc.player.getId() == payload.entityId();
        switch (payload.kind()) {
            case GearFxPayload.Kind.SHOCKWAVE -> VanguardVisuals.shockwave(payload.at(), payload.value(), own);
            case GearFxPayload.Kind.METEOR_MARK -> {
                Entity caster = mc.level == null ? null : mc.level.getEntity(payload.entityId());
                VanguardVisuals.meteorMark(payload.at(), payload.value(), payload.ticks(), caster == null ? null : caster.position());
            }
            case GearFxPayload.Kind.METEOR_IMPACT -> VanguardVisuals.meteorImpact(payload.at(), payload.value(), payload.ticks());
            default -> {
            }
        }
    }
}
