package com.cosmicbreach.client.entity;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.fx.FxShaders;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.function.Function;

/**
 * Drawing for the Shardling's needles, shards and telegraphs. Two blends: {@link #translucent} (ordinary
 * alpha blending) for anything whose colour has to read on a bright floor or sky, since light added
 * onto white calcite or a noon sky vanishes; and {@code FxRenderTypes.additive} for the glow on top.
 * Both use the effects' shader (texture times vertex colour, no alpha cut-off, full bright), depth
 * tested, never depth written, both faces. Positions are relative to whatever the matrix maps from.
 */
public final class ShardDraw {
    public static final ResourceLocation STAR = CosmicBreach.id("textures/particle/star_glint.png");
    public static final ResourceLocation SPARK = CosmicBreach.id("textures/particle/spark.png");
    public static final ResourceLocation RING = CosmicBreach.id("textures/particle/ring.png");
    public static final ResourceLocation NEEDLE = CosmicBreach.id("textures/particle/shard_0.png");
    public static final ResourceLocation GLOW = CosmicBreach.id("textures/fx/glow.png");
    /** The middle of the glow texture is solid white: sampling only there draws flat colour. */
    private static final float SOLID_U = 0.5f;
    private static final float SOLID_V = 0.5f;

    private static final RenderStateShard.ShaderStateShard SHADER = new RenderStateShard.ShaderStateShard(FxShaders::additive);

    private static final Function<ResourceLocation, RenderType> CRISP = Util.memoize(texture -> create(texture, false));
    private static final Function<ResourceLocation, RenderType> SMOOTH = Util.memoize(texture -> create(texture, true));

    private ShardDraw() {
    }

    private static RenderType create(ResourceLocation texture, boolean smooth) {
        return RenderType.create("cosmicbreach_translucent_" + (smooth ? "smooth_" : "crisp_") + texture.getPath(),
                DefaultVertexFormat.POSITION_TEX_COLOR,
                VertexFormat.Mode.QUADS,
                1536,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState(SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, smooth, false))
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                        .setOverlayState(RenderStateShard.NO_OVERLAY)
                        .createCompositeState(false));
    }

    /** Alpha-blended, with nearest filtering for pixel-art sprites ({@code smooth} false) or linear. */
    public static RenderType translucent(ResourceLocation texture, boolean smooth) {
        return (smooth ? SMOOTH : CRISP).apply(texture);
    }

    /** Flat colour, alpha-blended: for decals. */
    public static RenderType solid() {
        return SMOOTH.apply(GLOW);
    }

    public static void vertex(VertexConsumer out, Matrix4f pose, Vec3 p, float u, float v, float r, float g, float b, float a) {
        out.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setUv(u, v).setColor(r, g, b, a);
    }

    /** A square round {@code at} spanned by {@code right} and {@code up} (half sizes), the whole texture on it. */
    public static void quad(VertexConsumer out, Matrix4f pose, Vec3 at, Vec3 right, Vec3 up, float r, float g, float b, float a) {
        vertex(out, pose, at.subtract(right).subtract(up), 0f, 1f, r, g, b, a);
        vertex(out, pose, at.add(right).subtract(up), 1f, 1f, r, g, b, a);
        vertex(out, pose, at.add(right).add(up), 1f, 0f, r, g, b, a);
        vertex(out, pose, at.subtract(right).add(up), 0f, 0f, r, g, b, a);
    }

    /**
     * A diamond whose long axis runs along {@code along} (unit) {@code length} long and {@code width}
     * wide across {@code side} (unit), the texture's bottom-left to top-right diagonal on the long axis:
     * the shard sprites lie on that diagonal, so this stretches one along a needle's flight.
     */
    public static void diamond(VertexConsumer out, Matrix4f pose, Vec3 at, Vec3 along, Vec3 side, double length, double width,
                               float r, float g, float b, float a) {
        Vec3 l = along.scale(length * 0.5);
        Vec3 w = side.scale(width * 0.5);
        vertex(out, pose, at.subtract(l), 0f, 1f, r, g, b, a);
        vertex(out, pose, at.add(w), 1f, 1f, r, g, b, a);
        vertex(out, pose, at.add(l), 1f, 0f, r, g, b, a);
        vertex(out, pose, at.subtract(w), 0f, 0f, r, g, b, a);
    }

    /**
     * A streak along {@code along}: from {@code back} behind {@code at} to {@code ahead} in front,
     * {@code width} across {@code side}; the texture's v runs along it (the spark sprite's line).
     */
    public static void streak(VertexConsumer out, Matrix4f pose, Vec3 at, Vec3 along, Vec3 side, double back, double ahead,
                              double width, float r, float g, float b, float a) {
        Vec3 tail = at.subtract(along.scale(back));
        Vec3 head = at.add(along.scale(ahead));
        Vec3 w = side.scale(width * 0.5);
        vertex(out, pose, tail.subtract(w), 0f, 1f, r, g, b, a);
        vertex(out, pose, tail.add(w), 1f, 1f, r, g, b, a);
        vertex(out, pose, head.add(w), 1f, 0f, r, g, b, a);
        vertex(out, pose, head.subtract(w), 0f, 0f, r, g, b, a);
    }

    /** A flat disc of solid colour on the ground plane round {@code at}, in {@code segments} slices. */
    public static void disc(VertexConsumer out, Matrix4f pose, Vec3 at, double radius, int segments, float r, float g, float b, float a) {
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0 * i / segments;
            double a1 = Math.PI * 2.0 * (i + 1) / segments;
            Vec3 p0 = at.add(Math.cos(a0) * radius, 0, Math.sin(a0) * radius);
            Vec3 p1 = at.add(Math.cos(a1) * radius, 0, Math.sin(a1) * radius);
            vertex(out, pose, at, SOLID_U, SOLID_V, r, g, b, a);
            vertex(out, pose, p0, SOLID_U, SOLID_V, r, g, b, a);
            vertex(out, pose, p1, SOLID_U, SOLID_V, r, g, b, a);
            vertex(out, pose, p1, SOLID_U, SOLID_V, r, g, b, a);
        }
    }

    /** A flat band of solid colour from {@code inner} to {@code outer} blocks round {@code at}. */
    public static void band(VertexConsumer out, Matrix4f pose, Vec3 at, double inner, double outer, int segments,
                            float r, float g, float b, float a) {
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0 * i / segments;
            double a1 = Math.PI * 2.0 * (i + 1) / segments;
            double c0 = Math.cos(a0);
            double s0 = Math.sin(a0);
            double c1 = Math.cos(a1);
            double s1 = Math.sin(a1);
            vertex(out, pose, at.add(c0 * inner, 0, s0 * inner), SOLID_U, SOLID_V, r, g, b, a);
            vertex(out, pose, at.add(c0 * outer, 0, s0 * outer), SOLID_U, SOLID_V, r, g, b, a);
            vertex(out, pose, at.add(c1 * outer, 0, s1 * outer), SOLID_U, SOLID_V, r, g, b, a);
            vertex(out, pose, at.add(c1 * inner, 0, s1 * inner), SOLID_U, SOLID_V, r, g, b, a);
        }
    }

    /** Red, green and blue of 0xRRGGBB, 0 to 1. */
    public static float[] rgb(int color) {
        return new float[] {((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f};
    }
}
