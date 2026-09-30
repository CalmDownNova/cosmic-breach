package com.cosmicbreach.client.mount;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.guardian.GuardianGlowLayer;
import com.cosmicbreach.mount.LumenStag;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * The Lumen Stag through GeckoLib. Its crystal antlers hold light: their glow is added over the model unshaded
 * ({@link GuardianGlowLayer}), as bright as the stag's trust ({@link LumenStag#antlerGlow}): faint when wild, full once
 * tamed. The gear's own lights (the Comet Bridle's comet, the Halo Reins' ring) glow at full.
 */
public class LumenStagRenderer extends GeoEntityRenderer<LumenStag> {
    static final ResourceLocation ANTLERS = CosmicBreach.id("textures/entity/lumen_stag_glowmask.png");
    static final ResourceLocation GEAR = CosmicBreach.id("textures/entity/lumen_stag_gear_glowmask.png");

    public LumenStagRenderer(EntityRendererProvider.Context context) {
        super(context, new LumenStagModel());
        addRenderLayer(new GuardianGlowLayer<>(this, s -> ANTLERS, s -> grey(s.antlerGlow())));
        addRenderLayer(new GuardianGlowLayer<>(this, s -> GEAR, s -> 0xFFFFFF));
        this.shadowRadius = 0.75f;
    }

    static int grey(float f) {
        int v = Math.round(255 * Math.max(0f, Math.min(1f, f)));
        return (v << 16) | (v << 8) | v;
    }
}
