package com.cosmicbreach.client.fx;

import com.cosmicbreach.CosmicBreach;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/**
 * How the effects are drawn: light added onto the scene with straight alpha (blend SRC_ALPHA, ONE,
 * which is vanilla's lightning transparency; the generated textures are white with the shape in
 * alpha), depth tested but never written, both faces, full bright. {@link #additive} is for the
 * shaped effects (slash ribbons, beams, glows), with linear filtering since they are stretched over
 * world geometry; {@link #PARTICLES} is the same blend for the particle sheet.
 */
public final class FxRenderTypes {
    public static final ResourceLocation SLASH = CosmicBreach.id("textures/fx/slash.png");
    public static final ResourceLocation BEAM = CosmicBreach.id("textures/fx/beam.png");
    public static final ResourceLocation GLOW = CosmicBreach.id("textures/fx/glow.png");
    public static final ResourceLocation CRACK = CosmicBreach.id("textures/fx/crack.png");

    private static final RenderStateShard.ShaderStateShard ADDITIVE_SHADER = new RenderStateShard.ShaderStateShard(FxShaders::additive);

    private static final Function<ResourceLocation, RenderType> ADDITIVE = Util.memoize(texture -> RenderType.create(
            "cosmicbreach_additive_" + texture.getPath(),
            DefaultVertexFormat.POSITION_TEX_COLOR,
            VertexFormat.Mode.QUADS,
            4096,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(ADDITIVE_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(texture, true, false))
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                    .setOverlayState(RenderStateShard.NO_OVERLAY)
                    .createCompositeState(false)));

    /** The same, blended over the scene (SRC_ALPHA, ONE_MINUS_SRC_ALPHA): for what darkens, like cracks and dust. */
    private static final Function<ResourceLocation, RenderType> SHADE = Util.memoize(texture -> RenderType.create(
            "cosmicbreach_shade_" + texture.getPath(),
            DefaultVertexFormat.POSITION_TEX_COLOR,
            VertexFormat.Mode.QUADS,
            4096,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(ADDITIVE_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(texture, true, false))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                    .setOverlayState(RenderStateShard.NO_OVERLAY)
                    .createCompositeState(false)));

    /**
     * Our particles that darken instead of glow (the Gravity Well's dust): the particle sheet with the
     * ordinary blend, depth tested but not written.
     */
    public static final ParticleRenderType DARK_PARTICLES = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public String toString() {
            return "cosmicbreach:dark_particles";
        }
    };

    /**
     * Our particles, drawn with the particle shader (full-bright light) and the additive blend.
     * The engine draws translucent particle types just before {@code AFTER_PARTICLES}, where
     * {@link WorldFx} puts the default blend function back.
     */
    public static final ParticleRenderType PARTICLES = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public String toString() {
            return "cosmicbreach:additive_particles";
        }
    };

    private FxRenderTypes() {
    }

    /** Additive light shaped by {@code texture}: format position, uv, colour; quads. */
    public static RenderType additive(ResourceLocation texture) {
        return ADDITIVE.apply(texture);
    }

    /** {@code texture} blended over the scene with the vertex colour's alpha: dark shapes. Same format. */
    public static RenderType shade(ResourceLocation texture) {
        return SHADE.apply(texture);
    }
}
