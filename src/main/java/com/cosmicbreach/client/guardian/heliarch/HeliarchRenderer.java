package com.cosmicbreach.client.guardian.heliarch;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.guardian.GuardianGlowLayer;
import com.cosmicbreach.guardian.heliarch.HaloShed;
import com.cosmicbreach.guardian.heliarch.HeliarchPose;
import com.cosmicbreach.guardian.heliarch.HollowHeliarch;
import com.cosmicbreach.guardian.heliarch.HollowHeliarch.Action;
import com.cosmicbreach.guardian.heliarch.HollowHeliarch.State;
import com.cosmicbreach.world.VesperClock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * The Hollow Heliarch through GeckoLib, drawn twice its built size, with its light added over it full bright
 * ({@link GuardianGlowLayer}): the core's white heart and gold, the plates' enamel suns and gilt rims, the ember seams
 * of the gauntlets. The light pulses on Vesper's beat, flashes as the plates shed, and dims while it is Broken. The rest
 * of its light (the corona, the eclipse, the telegraphs) is {@link HeliarchFx}'s.
 */
public class HeliarchRenderer extends GeoEntityRenderer<HollowHeliarch> {
    static final ResourceLocation GLOW = CosmicBreach.id("textures/entity/hollow_heliarch_glowmask.png");

    public HeliarchRenderer(EntityRendererProvider.Context context) {
        super(context, new HeliarchModel());
        addRenderLayer(new GuardianGlowLayer<>(this, h -> GLOW, HeliarchRenderer::glow));
        withScale(HeliarchModel.SCALE);
        this.shadowRadius = 0f;
    }

    /**
     * Drawn full-bright: the eclipse darkens the arena through the lightmap ({@link HeliarchSky#fightDim}), but not the
     * boss itself, whose shape still takes the directional light.
     */
    @Override
    public void render(HollowHeliarch entity, float entityYaw, float partialTick, com.mojang.blaze3d.vertex.PoseStack poseStack,
                       net.minecraft.client.renderer.MultiBufferSource bufferSource, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, net.minecraft.client.renderer.LightTexture.FULL_BRIGHT);
    }

    /** No red hurt tint: its hits show in the flares and the bar. */
    @Override
    public int getPackedOverlay(HollowHeliarch animatable, float u, float partialTick) {
        return OverlayTexture.NO_OVERLAY;
    }

    /** The grey level of the light added over the model now. */
    static int glow(HollowHeliarch h) {
        Minecraft mc = Minecraft.getInstance();
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(false);
        double time = h.level().getGameTime() + partial;
        long t = (long) Math.floor(time);
        double beat = Math.exp(-(VesperClock.tickInBeat(t) + (time - t)) / VesperClock.PULSE_DECAY);
        double level = 0.78 + 0.22 * beat;
        double s = time - h.stateStart();
        if (h.state() == State.INTRO) {
            level *= 0.25 + 0.75 * HeliarchPose.smooth((s - 40) / 100.0);
        }
        if (h.state() == State.REGENT && h.action() == Action.HALO_SHED) {
            double a = time - h.actionStart();
            if (a < HaloShed.FLASH) {
                level = Math.floorMod((long) (a / 3), 2) == 0 ? 1.0 : 0.55;
            }
        }
        if (h.brokenNow(t)) {
            level *= 0.55 + 0.15 * Math.sin(time * 0.9);
        }
        int g = (int) Math.round(255 * Math.max(0.0, Math.min(1.0, level)));
        return (g << 16) | (g << 8) | g;
    }
}
