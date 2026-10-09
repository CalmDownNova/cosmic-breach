package com.cosmicbreach.client.sky;

import com.cosmicbreach.CosmicBreach;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.shaders.Uniform;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * Draws Aetheria's sky (GDD 2.3), back to front:
 *
 * <ol>
 *   <li>the dome: the sky's gradient and Solenne's glare, with the nebula (two baked cubemap atlases
 *       cross-faded by the camera's height) as daytime veils or night-time light (one draw, opaque);</li>
 *   <li>stars and the three clusters, static buffers turned by the celestial rotation, each star twinkling
 *       on its own phase in the vertex shader (two draws, additive);</li>
 *   <li>Vesper: its two beams sweeping round it once every 12 s and its flickering star, on the shared
 *       {@link com.cosmicbreach.world.VesperClock} (three draws, additive);</li>
 *   <li>Solenne: disc and corona (one draw, additive), drawn before Thalassa so the planet can eclipse it;</li>
 *   <li>Thalassa and its rings, ray-traced on one quad (one draw, premultiplied alpha);</li>
 *   <li>weather's layers ({@link SkyWeather}), then the aurora ribbons at sunset (one draw, additive).</li>
 * </ol>
 * Nothing here evaluates noise: the nebula, the bands and the aurora's rays are baked textures
 * ({@code tools/sky}). All numbers come from {@link SkyModel} via {@link SkyState}.
 */
public final class AetheriaSkyRenderer {
    private static final ResourceLocation[] NEBULA = {
            CosmicBreach.id("textures/sky/nebula_reach.png"),
            CosmicBreach.id("textures/sky/nebula_drift.png"),
            CosmicBreach.id("textures/sky/nebula_deep.png")};
    private static final ResourceLocation BANDS = CosmicBreach.id("textures/sky/thalassa_bands.png");
    private static final ResourceLocation RING = CosmicBreach.id("textures/sky/thalassa_ring.png");
    private static final ResourceLocation AURORA = CosmicBreach.id("textures/sky/aurora.png");
    private static final float DOME = 50f;

    private static @Nullable VertexBuffer dome;
    private static @Nullable VertexBuffer stars;
    private static @Nullable VertexBuffer clusters;
    private static @Nullable VertexBuffer aurora;
    private static @Nullable VertexBuffer quadsTexColor;
    private static @Nullable VertexBuffer quadsPosition;
    private static final PoseStack SCRATCH = new PoseStack();
    private static long frames;

    private AetheriaSkyRenderer() {
    }

    /** Frames the sky has been drawn (checks use it). */
    public static long frames() {
        return frames;
    }

    static void render(float partialTick, Matrix4f view, Camera camera, Matrix4f projection) {
        if (!SkyShaders.ready()) {
            return;
        }
        long started = System.nanoTime();
        SkyStats.begin();
        ensureBuffers();
        SkyFrame f = SkyState.FRAME;

        RenderSystem.depthMask(false);
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        // 1. the dome and the nebula
        RenderSystem.disableBlend();
        ShaderInstance s = SkyShaders.dome;
        skyUniforms(s, f);
        matrix(s, "CelestialMat", rotation(f.skyRot, true));
        vec(s, "NebulaParams", f.nebulaMix, f.nebula[0], f.nebula[1], f.nebula[2]);
        vec(s, "NebulaVeil", f.nebula[3]);
        vec(s, "NebulaClear", (float) f.layers[2]);
        RenderSystem.setShaderTexture(0, NEBULA[f.nebulaA]);
        RenderSystem.setShaderTexture(1, NEBULA[f.nebulaB]);
        draw(dome, view, projection, s);

        // 2. stars and clusters
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        if (f.stars[0] > 0.004f) {
            s = SkyShaders.stars;
            matrix(s, "SkyRot", rotation(f.skyRot, false));
            vec(s, "StarParams", f.stars[0], f.stars[1], (float) f.seconds, f.stars[2]);
            draw(stars, view, projection, s);
            draw(clusters, view, projection, s);
        }

        // 3. Vesper: beams, then its star
        s = SkyShaders.glow;
        if (f.vesperBeam > 0.003f) {
            glow(s, 2, 0f, 0f, 0f, f.vesperBeam);
            drawQuads(SkyQuads.beam(f, f.beamAngle), 0.55f, 0.82f, 1.0f, view, projection, s);
            drawQuads(SkyQuads.beam(f, f.beamAngle + Math.PI), 0.55f, 0.82f, 1.0f, view, projection, s);
        }
        glow(s, 1, 0.07f, 0.55f, f.beamAngle, f.vesperCore);
        drawQuads(SkyQuads.facing(f.vesperDir, SkyQuads.VESPER_HALF_ANGLE), 0.78f, 0.9f, 1.0f, view, projection, s);

        // 4. Solenne (before Thalassa, which eclipses it)
        float disc = f.discStrength * f.sunVisible;
        if (disc > 0.002f || f.coronaStrength > 0.002f) {
            glow(s, 0, SkyQuads.sunDiscFraction(f), f.coronaStrength, (float) (f.seconds * 0.05), disc);
            vec(s, "GlowColor2", 1.0f, 0.86f, 0.6f);
            drawQuads(SkyQuads.facing(f.sunDir, SkyQuads.sunHalfAngle(f)), 1f, 1f, 1f, view, projection, s);
        }

        // 5. Thalassa
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        s = SkyShaders.body;
        skyUniforms(s, f);
        vec(s, "PlanetPos", f.planetDir[0] * SkyQuads.DISTANCE, f.planetDir[1] * SkyQuads.DISTANCE, f.planetDir[2] * SkyQuads.DISTANCE);
        vec(s, "PlanetGeom", SkyQuads.planetRadius(f), (float) SkyQuads.RING_INNER, (float) SkyQuads.RING_OUTER, f.bandDrift);
        vec(s, "RingAxis", f.ringAxis[0], f.ringAxis[1], f.ringAxis[2]);
        vec(s, "LightDir", f.planetLightDir[0], f.planetLightDir[1], f.planetLightDir[2]);
        vec(s, "PlanetLight", f.planetLight[0], f.planetLight[1], f.planetLight[2], f.planetLight[3]);
        vec(s, "PlanetParams", f.planetParams[0], f.planetParams[1], f.planetParams[2], f.planetParams[3]);
        vec(s, "PlanetAmbient", f.planetAmbient[0], f.planetAmbient[1], f.planetAmbient[2]);
        RenderSystem.setShaderTexture(0, BANDS);
        RenderSystem.setShaderTexture(1, RING);
        drawPlanetQuad(SkyQuads.facing(f.planetDir, SkyQuads.planetHalfAngle(f)), view, projection, s);

        // 6. weather's layers, then the aurora
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        for (SkyWeather.Layer layer : SkyWeather.layers()) {
            layer.render(view, projection, camera, partialTick, f, SCRATCH);
        }
        if (f.aurora > 0.002f) {
            s = SkyShaders.aurora;
            vec(s, "AuroraParams", (float) f.seconds, f.aurora, 0f, 0f);
            RenderSystem.setShaderTexture(0, AURORA);
            draw(aurora, view, projection, s);
        }

        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        frames++;
        SkyStats.end(System.nanoTime() - started);
    }

    // ------------------------------------------------------------------ uniforms

    private static void skyUniforms(ShaderInstance s, SkyFrame f) {
        vec(s, "ZenithColor", f.zenith);
        vec(s, "MidColor", f.mid);
        vec(s, "HazeColor", f.haze);
        vec(s, "HorizonColor", f.horizon);
        vec(s, "LowColor", f.low);
        vec(s, "NadirColor", f.nadir);
        vec(s, "SunDir", f.sunDir);
        vec(s, "SunGlow", f.glow[0], f.glow[1], f.glow[2], f.glow[3]);
    }

    private static void glow(ShaderInstance s, int mode, float x, float y, float z, float w) {
        Uniform u = s.getUniform("GlowMode");
        if (u != null) {
            u.set(mode);
        }
        vec(s, "GlowParams", x, y, z, w);
    }

    private static void vec(ShaderInstance s, String name, float... v) {
        Uniform u = s.getUniform(name);
        if (u == null) {
            return;
        }
        switch (v.length) {
            case 1 -> u.set(v[0]);
            case 2 -> u.set(v[0], v[1]);
            case 3 -> u.set(v[0], v[1], v[2]);
            default -> u.set(v[0], v[1], v[2], v[3]);
        }
    }

    private static void matrix(ShaderInstance s, String name, Matrix4f m) {
        Uniform u = s.getUniform(name);
        if (u != null) {
            u.set(m);
        }
    }

    /** The row-major 3 by 3 rotation as a matrix (or its inverse, the transpose). */
    static Matrix4f rotation(float[] r, boolean transpose) {
        Matrix4f m = new Matrix4f();
        if (!transpose) {
            m.m00(r[0]).m01(r[3]).m02(r[6]).m10(r[1]).m11(r[4]).m12(r[7]).m20(r[2]).m21(r[5]).m22(r[8]);
        } else {
            m.m00(r[0]).m01(r[1]).m02(r[2]).m10(r[3]).m11(r[4]).m12(r[5]).m20(r[6]).m21(r[7]).m22(r[8]);
        }
        return m;
    }

    // ------------------------------------------------------------------ buffers

    private static void draw(@Nullable VertexBuffer buffer, Matrix4f view, Matrix4f projection, ShaderInstance s) {
        if (buffer == null) {
            return;
        }
        buffer.bind();
        buffer.drawWithShader(view, projection, s);
        VertexBuffer.unbind();
    }

    /** Quads given as x, y, z, u, v per vertex, one colour for all, through a reused dynamic buffer. */
    private static void drawQuads(float[] v, float r, float g, float b, Matrix4f view, Matrix4f projection, ShaderInstance s) {
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int i = 0; i + 4 < v.length; i += 5) {
            builder.addVertex(v[i], v[i + 1], v[i + 2]).setUv(v[i + 3], v[i + 4]).setColor(r, g, b, 1f);
        }
        upload(quadsTexColor, builder.build(), view, projection, s);
    }

    private static void drawPlanetQuad(float[] v, Matrix4f view, Matrix4f projection, ShaderInstance s) {
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        for (int i = 0; i + 4 < v.length; i += 5) {
            builder.addVertex(v[i], v[i + 1], v[i + 2]);
        }
        upload(quadsPosition, builder.build(), view, projection, s);
    }

    private static void upload(@Nullable VertexBuffer buffer, @Nullable MeshData mesh, Matrix4f view, Matrix4f projection, ShaderInstance s) {
        if (buffer == null || mesh == null) {
            if (mesh != null) {
                mesh.close();
            }
            return;
        }
        buffer.bind();
        buffer.upload(mesh);
        buffer.drawWithShader(view, projection, s);
        VertexBuffer.unbind();
    }

    private static void ensureBuffers() {
        if (dome != null) {
            return;
        }
        dome = staticBuffer(domeMesh());
        stars = staticBuffer(mesh(SkyStars.field()));
        clusters = staticBuffer(mesh(SkyStars.clusters()));
        aurora = staticBuffer(mesh(SkyAurora.ribbons()));
        quadsTexColor = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        quadsPosition = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
    }

    private static VertexBuffer staticBuffer(MeshData mesh) {
        VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        buffer.bind();
        buffer.upload(mesh);
        VertexBuffer.unbind();
        return buffer;
    }

    private static MeshData mesh(SkyStars.Mesh m) {
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float[] v = m.vertices();
        int[] c = m.colors();
        for (int i = 0; i < m.quads() * 4; i++) {
            builder.addVertex(v[i * 5], v[i * 5 + 1], v[i * 5 + 2]).setUv(v[i * 5 + 3], v[i * 5 + 4]).setColor(c[i]);
        }
        return builder.buildOrThrow();
    }

    /** A cube round the camera; the fragment shader only needs each pixel's direction. */
    private static MeshData domeMesh() {
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        for (int axis = 0; axis < 3; axis++) {
            for (int sign = -1; sign <= 1; sign += 2) {
                int u = axis == 0 ? 1 : 0;
                int w = axis == 2 ? 1 : 2;
                float[][] corners = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
                for (float[] c : corners) {
                    float[] p = new float[3];
                    p[axis] = sign * DOME;
                    p[u] = c[0] * DOME;
                    p[w] = c[1] * DOME;
                    builder.addVertex(p[0], p[1], p[2]);
                }
            }
        }
        return builder.buildOrThrow();
    }
}
