package com.cosmicbreach.client.mount;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.guardian.GuardianGlowLayer;
import com.cosmicbreach.mount.DriftManta;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * The Drift Manta through GeckoLib. Its song spots glow: softly always, flaring on each note it sings and fading over
 * half a beat ({@link DriftManta#sungAt}); the gear's lights (the Nebula Reins' orb, the Gale Fins) at full.
 */
public class DriftMantaRenderer extends GeoEntityRenderer<DriftManta> {
    static final ResourceLocation SPOTS = CosmicBreach.id("textures/entity/drift_manta_glowmask.png");
    static final ResourceLocation GEAR = CosmicBreach.id("textures/entity/drift_manta_gear_glowmask.png");

    public DriftMantaRenderer(EntityRendererProvider.Context context) {
        super(context, new DriftMantaModel());
        addRenderLayer(new GuardianGlowLayer<>(this, m -> SPOTS, DriftMantaRenderer::spotLight));
        addRenderLayer(new GuardianGlowLayer<>(this, m -> GEAR, m -> 0xFFFFFF));
        this.shadowRadius = 0.9f;
    }

    static int spotLight(DriftManta m) {
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        double since = m.level().getGameTime() + partial - m.sungAt();
        double flare = since >= 0 ? Math.exp(-since / 5.0) : 0.0;
        return LumenStagRenderer.grey((float) (0.3 + 0.7 * flare));
    }
}
