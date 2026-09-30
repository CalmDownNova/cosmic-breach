package com.cosmicbreach.client.guardian.unsung;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.guardian.GuardianGlowLayer;
import com.cosmicbreach.guardian.unsung.UnsungMask;
import com.cosmicbreach.guardian.unsung.UnsungMoves;
import com.cosmicbreach.guardian.unsung.Voice;
import com.cosmicbreach.world.VesperClock;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.Color;

/**
 * An Unsung mask through GeckoLib (Unsung design v1, "Look and sound"): its own model and animations per voice
 * ({@code tools/art/gen_unsung.py}), drawn translucent so the cloak fades into smoke, then its light added over it full
 * bright ({@link GuardianGlowLayer}): the porcelain's glaze and highlights, the cracks in the voice's colour, the light
 * inside the eyes and mouth, the hood's hem. The singing mask burns at full strength, pulsing on Vesper's beat, white
 * and drawn as if lit (the fight is lit by the singers; its halo is {@link UnsungFx}'s); the humming masks are tinted
 * a cool grey and glow a third as bright; a sleeping mask is nearly dark; a broken one goes cold.
 */
public class UnsungMaskRenderer extends GeoEntityRenderer<UnsungMask> {
    public UnsungMaskRenderer(EntityRendererProvider.Context context) {
        super(context, new Model());
        addRenderLayer(new GuardianGlowLayer<>(this, UnsungMaskRenderer::glowTexture, UnsungMaskRenderer::glowStrength));
        this.shadowRadius = 0f;
    }

    /** The mask's model, texture and animations by its voice and state. */
    static final class Model extends GeoModel<UnsungMask> {
        @Override
        public ResourceLocation getModelResource(UnsungMask mask) {
            return CosmicBreach.id("geo/entity/unsung_" + mask.voice().id() + ".geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(UnsungMask mask) {
            return texture(mask.voice(), mask.shattered());
        }

        @Override
        public ResourceLocation getAnimationResource(UnsungMask mask) {
            return CosmicBreach.id("animations/entity/unsung_" + mask.voice().id() + ".animation.json");
        }
    }

    static ResourceLocation texture(Voice v, boolean broken) {
        return CosmicBreach.id("textures/entity/unsung_" + v.id() + (broken ? "_broken" : "") + ".png");
    }

    static @Nullable ResourceLocation glowTexture(UnsungMask mask) {
        return CosmicBreach.id("textures/entity/unsung_" + mask.voice().id() + (mask.shattered() ? "_broken" : "") + "_glowmask.png");
    }

    /** A humming mask's glow: a cool grey, so the three hummers read as porcelain in shadow beside the white singer. */
    static final int HUM_GLOW = 0x4D5C7A;
    /** The humming mask's porcelain, tinted cool (the lamp light it gets is warm). */
    static final Color HUM_TINT = Color.ofRGB(168, 194, 255);
    static final Color REST_TINT = Color.ofRGB(150, 164, 200);
    static final Color FALLEN_TINT = Color.ofRGB(214, 222, 240);

    /** The colour the mask's light is added in now (0xRRGGBB, its strength). */
    static int glowStrength(UnsungMask mask) {
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        long now = mask.level().getGameTime();
        double t = now + partial - mask.modeStart();
        if (mask.mode() == UnsungMask.Mode.FLOAT && !mask.singing() && !mask.hasFlag(UnsungMask.FLAG_INHALE)) {
            return HUM_GLOW;
        }
        double s = switch (mask.mode()) {
            case REST -> 0.10;
            case RISE -> 0.15 + 0.85 * Math.min(1.0, t / (2.0 * UnsungMoves.BEAT));
            case FLOAT -> 0.82 + 0.18 * VesperClock.pulse(now, partial);
            case DROP -> mask.hasFlag(UnsungMask.FLAG_GLINT) ? 1.0 : 0.9;
            case FALLEN -> 0.45 + 0.2 * Math.sin(t * 0.9);
            case BROKEN -> Math.max(0.08, 0.5 - t / 30.0);
            case GONE -> Math.max(0.0, 0.08 - t / 200.0);
        };
        int level = (int) Math.round(255 * Math.max(0.0, Math.min(1.0, s)));
        return level == 0 ? 0 : (level << 16) | (level << 8) | level;
    }

    /** The singer (and a mask rising or dropping) in white porcelain, the hummers cool grey. */
    @Override
    public Color getRenderColor(UnsungMask mask, float partialTick, int packedLight) {
        return switch (mask.mode()) {
            case REST -> REST_TINT;
            case FALLEN, BROKEN, GONE -> FALLEN_TINT;
            case FLOAT -> mask.singing() || mask.hasFlag(UnsungMask.FLAG_INHALE) ? Color.WHITE : HUM_TINT;
            case RISE, DROP -> Color.WHITE;
        };
    }

    /** Lit by its own song: a singing mask as if full bright, a humming one never darker than a lamp's glow. */
    @Override
    public void render(UnsungMask mask, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        int block = LightTexture.block(packedLight);
        int sky = LightTexture.sky(packedLight);
        int least = switch (mask.mode()) {
            case REST, BROKEN, GONE -> 0;
            case FALLEN -> 11;
            default -> mask.singing() || mask.mode() == UnsungMask.Mode.DROP ? 15 : 9;
        };
        super.render(mask, yaw, partialTick, poseStack, buffers, LightTexture.pack(Math.max(block, least), sky));
    }

    @Override
    public RenderType getRenderType(UnsungMask mask, ResourceLocation texture, @Nullable MultiBufferSource buffers, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    /** No red hurt tint: the hits show as sparks and the tink. */
    @Override
    public int getPackedOverlay(UnsungMask mask, float u, float partialTick) {
        return OverlayTexture.NO_OVERLAY;
    }

    @Override
    public boolean shouldShowName(UnsungMask mask) {
        return false;
    }
}
