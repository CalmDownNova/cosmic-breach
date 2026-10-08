package com.cosmicbreach.client.gyre;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.guardian.GuardianGlowLayer;
import com.cosmicbreach.entity.gyre.GyreKnight;
import com.cosmicbreach.entity.gyre.GyreModes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * The Gyre Knight through GeckoLib, its light added over it full bright ({@link GuardianGlowLayer}): the gyroscope's
 * white-cyan heart, the gimbal rings' and the visor's cyan (dimmed while stunned), and the blades' gold through a Sweep
 * Orbit's telegraph and cut (the parry cue); red on the blades through a Lance Volley and a Recall Crash.
 */
public class GyreKnightRenderer extends GeoEntityRenderer<GyreKnight> {
    static final ResourceLocation GLOW = CosmicBreach.id("textures/entity/gyre_knight_glowmask.png");
    static final ResourceLocation BLADES = CosmicBreach.id("textures/entity/gyre_knight_blades_glowmask.png");

    public GyreKnightRenderer(EntityRendererProvider.Context context) {
        super(context, new GyreKnightModel());
        addRenderLayer(new GuardianGlowLayer<>(this, k -> GLOW, k -> k.mode() == GyreModes.Mode.STUNNED ? 0x606870 : 0xFFFFFF));
        addRenderLayer(new GuardianGlowLayer<>(this, GyreKnightRenderer::bladeTexture, GyreKnightRenderer::bladeLight));
        this.shadowRadius = 0.5f;
    }

    private static ResourceLocation bladeTexture(GyreKnight k) {
        return switch (k.mode()) {
            case SWEEP, LANCE, RECALL, DIVE -> BLADES;
            default -> null;
        };
    }

    /** Gold through a sweep's telegraph and cut, red through the lances and the recall. */
    private static int bladeLight(GyreKnight k) {
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        double t = k.level().getGameTime() + partial - k.modeStart();
        return switch (k.mode()) {
            case SWEEP -> {
                int a = k.approachEnd();
                if (a < 0 || t < a) {
                    yield 0;
                }
                double u = t - a;
                double f = u < GyreModes.SWEEP_TELL ? 0.3 + 0.7 * u / GyreModes.SWEEP_TELL
                        : u < GyreModes.SWEEP_TELL + GyreModes.SWEEP_ACTIVE ? 1.0 : 0.0;
                yield rgb(f, 1.0, 0.8, 0.25);
            }
            case DIVE -> {
                // gold from the tell through the cut: the parry cue, as in a sweep
                int a = k.approachEnd();
                double f = t < GyreModes.DIVE_TELL ? 0.3 + 0.7 * t / GyreModes.DIVE_TELL
                        : a < 0 || t < a + GyreModes.DIVE_STRIKE ? 1.0 : 0.0;
                yield rgb(f, 1.0, 0.8, 0.25);
            }
            case LANCE, RECALL -> rgb(0.55 + 0.25 * Math.sin(t * 0.8), 1.0, 0.18, 0.12);
            default -> 0;
        };
    }

    private static int rgb(double k, double r, double g, double b) {
        int ri = (int) Math.round(255 * Math.max(0, Math.min(1, k * r)));
        int gi = (int) Math.round(255 * Math.max(0, Math.min(1, k * g)));
        int bi = (int) Math.round(255 * Math.max(0, Math.min(1, k * b)));
        return (ri << 16) | (gi << 8) | bi;
    }
}
