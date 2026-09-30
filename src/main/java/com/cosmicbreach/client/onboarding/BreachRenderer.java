package com.cosmicbreach.client.onboarding;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.onboarding.BreachBlock;
import com.cosmicbreach.onboarding.BreachBlockEntity;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * The Breach: one quad per block near the rim of the hole (four make the 2 by 2 opening), drawn with
 * {@code cosmicbreach:breach_sky}, which colours each pixel by the direction the camera looks through it,
 * like the End portal but with Aetheria's own sky:
 * the Reach's daytime gradient and its baked nebula cubemap ({@code textures/sky/nebula_reach.png}, the same
 * atlas the sky dome samples), turned over so that looking down into the ground shows the sky you are about
 * to fall into. The frames' inner faces are the hole's walls.
 */
public class BreachRenderer implements BlockEntityRenderer<BreachBlockEntity> {
    public static final ResourceLocation NEBULA = CosmicBreach.id("textures/sky/nebula_reach.png");

    private static final RenderType SKY = RenderType.create("cosmicbreach_breach_sky", DefaultVertexFormat.POSITION_TEX,
            VertexFormat.Mode.QUADS, 256, false, false, RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(BreachShaders::sky))
                    .setTextureState(new RenderStateShard.TextureStateShard(NEBULA, true, false))
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                    .createCompositeState(false));

    private static long frames;

    @Override
    public void render(BreachBlockEntity breach, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        VertexConsumer out = buffers.getBuffer(SKY);
        Matrix4f m = pose.last().pose();
        float y = (float) BreachBlock.SURFACE;
        // UVs span the whole 2 by 2 opening (each quarter a quarter of it), so the rim glow runs round the
        // opening's edge and never along the seams between its blocks; the sky itself is by view direction
        BreachBlock.Quarter q = breach.getBlockState().hasProperty(BreachBlock.QUARTER)
                ? breach.getBlockState().getValue(BreachBlock.QUARTER) : BreachBlock.Quarter.NORTH_WEST;
        float u0 = q.dx * 0.5f;
        float v0 = q.dz * 0.5f;
        out.addVertex(m, 0f, y, 0f).setUv(u0, v0);
        out.addVertex(m, 0f, y, 1f).setUv(u0, v0 + 0.5f);
        out.addVertex(m, 1f, y, 1f).setUv(u0 + 0.5f, v0 + 0.5f);
        out.addVertex(m, 1f, y, 0f).setUv(u0 + 0.5f, v0);
        frames++;
    }

    /** Breach quads drawn since the game started (checks). */
    public static long frames() {
        return frames;
    }
}
