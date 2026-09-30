package com.cosmicbreach.client.leviathan;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.guardian.GuardianGlowLayer;
import com.cosmicbreach.guardian.leviathan.LeviathanMoves;
import com.cosmicbreach.guardian.leviathan.LeviathanTactics;
import com.cosmicbreach.guardian.leviathan.ThalassineLeviathan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * The Leviathan through GeckoLib, drawn {@value #SCALE} times its built size, with its light added over it full bright
 * ({@link GuardianGlowLayer}): the cyan spots along its flanks always (faint while it sleeps), the four song glands
 * warm white-gold while moored (dim otherwise), and the tail's fan going gold through a flick's telegraph.
 */
public class LeviathanRenderer extends GeoEntityRenderer<ThalassineLeviathan> {
    public static final float SCALE = 2.0f;
    static final ResourceLocation SPOTS = CosmicBreach.id("textures/entity/thalassine_leviathan_glowmask.png");
    static final ResourceLocation SPOTS_DORMANT = CosmicBreach.id("textures/entity/thalassine_leviathan_dormant_glowmask.png");
    static final ResourceLocation GLANDS = CosmicBreach.id("textures/entity/thalassine_leviathan_glands_glowmask.png");
    static final ResourceLocation FAN = CosmicBreach.id("textures/entity/thalassine_leviathan_fan_glowmask.png");

    public LeviathanRenderer(EntityRendererProvider.Context context) {
        super(context, new LeviathanModel());
        addRenderLayer(new GuardianGlowLayer<>(this, l -> l.state() == ThalassineLeviathan.State.DORMANT ? SPOTS_DORMANT : SPOTS, l -> 0xFFFFFF));
        addRenderLayer(new GuardianGlowLayer<>(this, l -> GLANDS, LeviathanRenderer::glandLight));
        addRenderLayer(new GuardianGlowLayer<>(this, l -> l.action() == LeviathanTactics.Attack.FLICK ? FAN : null, LeviathanRenderer::fanLight));
        withScale(SCALE);
        this.shadowRadius = 0f;
    }

    /**
     * A floor under the light it is drawn in: deep inside the Rift's shell the sky's light is thin and a mid blue hide went
     * as dark as the charcoal rock (art review G5p). It swims lit, as the Rift's singing crystal lights it.
     */
    @Override
    protected int getBlockLightLevel(ThalassineLeviathan l, BlockPos pos) {
        return Math.max(LIGHT_FLOOR, super.getBlockLightLevel(l, pos));
    }

    static final int LIGHT_FLOOR = 13;

    @Override
    public int getPackedOverlay(ThalassineLeviathan animatable, float u, float partialTick) {
        return OverlayTexture.NO_OVERLAY;
    }

    @Override
    public boolean shouldRender(ThalassineLeviathan l, Frustum frustum, double x, double y, double z) {
        return l.isAlive() && frustum.isVisible(l.getBoundingBoxForCulling());
    }

    /** The glands: dim embers under their plates, warm white-gold and breathing while moored. */
    static int glandLight(ThalassineLeviathan l) {
        if (!l.glandsExposed()) {
            return l.state() == ThalassineLeviathan.State.DORMANT ? 0x0A0806 : 0x2A2012;
        }
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        double time = l.level().getGameTime() + partial;
        double k = 0.8 + 0.2 * Math.sin(time * 0.25);
        return grey(k, 1.0, 0.92, 0.7);
    }

    /** The fan's gold: rising through the telegraph, brightest at the glint, fading after. */
    static int fanLight(ThalassineLeviathan l) {
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        double t = l.level().getGameTime() + partial - l.actionStart();
        double k;
        if (t < LeviathanMoves.FLICK_TELL) {
            k = 0.35 + 0.65 * Math.min(1.0, t / LeviathanMoves.FLICK_GLINT);
        } else {
            k = Math.max(0.0, 1.0 - (t - LeviathanMoves.FLICK_TELL) / 8.0);
        }
        return grey(k, 1.0, 0.85, 0.35);
    }

    private static int grey(double k, double r, double g, double b) {
        int ri = (int) Math.round(255 * Math.max(0, Math.min(1, k * r)));
        int gi = (int) Math.round(255 * Math.max(0, Math.min(1, k * g)));
        int bi = (int) Math.round(255 * Math.max(0, Math.min(1, k * b)));
        return (ri << 16) | (gi << 8) | bi;
    }
}
