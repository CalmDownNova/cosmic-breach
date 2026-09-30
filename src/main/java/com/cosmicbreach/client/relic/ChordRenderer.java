package com.cosmicbreach.client.relic;

import com.cosmicbreach.client.fx.FxRenderTypes;
import com.cosmicbreach.client.fx.WorldFx;
import com.cosmicbreach.relic.cantor.CantorRules;
import com.cosmicbreach.relic.cantor.ChordField;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * A chord: the ground inside its triangle darkened with violet shadow (the Umbra's silence), its edges strung with
 * violet light and raised as thin curtains, its three notes at the corners; the whole of it flares on every pulse
 * (every 20 ticks) and fades at the end. The triangle is drawn on the ground under its notes, so the silenced ground
 * reads from anywhere.
 */
public class ChordRenderer extends EntityRenderer<ChordField> {
    private static final float CURTAIN = 1.4f;

    public ChordRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
    }

    @Override
    public boolean shouldRender(ChordField chord, Frustum frustum, double camX, double camY, double camZ) {
        return frustum.isVisible(chord.reach());
    }

    @Override
    public void render(ChordField chord, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 cam = camera.getPosition();
        float age = chord.tickCount + partialTick;
        float in = Math.min(1f, age / 3f);
        float out = Math.max(0f, Math.min(1f, (CantorRules.CHORD_LIFE - age) / 10f));
        float since = age % CantorRules.PULSE_TICKS;
        float pulse = age >= CantorRules.PULSE_TICKS - 1 ? (float) Math.exp(-since / 4.0) : 0f;
        float a = in * out;
        Vec3[] c = chord.corners();
        Vec3[] rel = new Vec3[3];
        Vec3[] ground = new Vec3[3];
        double floor = Math.min(c[0].y, Math.min(c[1].y, c[2].y));
        for (int i = 0; i < 3; i++) {
            rel[i] = c[i].subtract(cam);
            ground[i] = new Vec3(c[i].x, floor + 0.04, c[i].z).subtract(cam);
        }
        // the silenced ground: a wash of violet shadow over it
        VertexConsumer shade = buffers.getBuffer(FxRenderTypes.shade(RelicDraw.GLOW));
        triangle(shade, ground, 0x1E0838, (0.45f + 0.15f * pulse) * a);
        // its edges: bright threads on the ground, thin curtains rising off them, the notes strung together
        VertexConsumer glow = buffers.getBuffer(FxRenderTypes.additive(RelicDraw.GLOW));
        triangle(glow, ground, RelicDraw.DEEP_VIOLET, (0.12f + 0.35f * pulse) * a);
        int edge = RelicDraw.mix(RelicDraw.VIOLET, RelicDraw.PALE_VIOLET, pulse);
        for (int i = 0; i < 3; i++) {
            int j = (i + 1) % 3;
            RelicDraw.ribbon(glow, camera, ground[i], ground[j], 0.12f, RelicDraw.DEEP_VIOLET, 0.8f * a, RelicDraw.DEEP_VIOLET, 0.8f * a);
            RelicDraw.ribbon(glow, camera, ground[i], ground[j], 0.04f, edge, a, edge, a);
            RelicDraw.ribbon(glow, camera, rel[i], rel[j], 0.035f, edge, (0.6f + 0.4f * pulse) * a, edge, (0.6f + 0.4f * pulse) * a);
            curtain(glow, ground[i], ground[j], (0.35f + 0.4f * pulse) * a);
        }
        for (int i = 0; i < 3; i++) {
            RelicDraw.billboard(glow, camera, rel[i], 0.7f + 0.3f * pulse, 0f, RelicDraw.VIOLET, 0.7f * a);
        }
        VertexConsumer ring = buffers.getBuffer(FxRenderTypes.additive(RelicDraw.RING));
        for (int i = 0; i < 3; i++) {
            RelicDraw.billboard(ring, camera, rel[i], 0.5f, age * 0.05f + i, RelicDraw.PALE_VIOLET, 0.85f * a);
        }
        VertexConsumer glyph = buffers.getBuffer(FxRenderTypes.additive(RelicDraw.NOTE));
        for (int i = 0; i < 3; i++) {
            RelicDraw.billboard(glyph, camera, rel[i], 0.34f, 0f, RelicDraw.PALE_VIOLET, a);
        }
        super.render(chord, yaw, partialTick, pose, buffers, light);
    }

    /** A thin wall of violet light standing on the edge from {@code a} to {@code b}, fading upward. */
    private static void curtain(VertexConsumer out, Vec3 a, Vec3 b, float alpha) {
        float[] c = WorldFx.rgb(RelicDraw.VIOLET);
        Vec3 up = new Vec3(0, CURTAIN, 0);
        WorldFx.vertex(out, a, 0.5f, 0.5f, c[0], c[1], c[2], alpha);
        WorldFx.vertex(out, b, 0.5f, 0.5f, c[0], c[1], c[2], alpha);
        WorldFx.vertex(out, b.add(up), 0.5f, 0.02f, c[0], c[1], c[2], 0f);
        WorldFx.vertex(out, a.add(up), 0.5f, 0.02f, c[0], c[1], c[2], 0f);
    }

    /** A filled triangle (a quad with its last corner doubled), sampled at the glow's bright middle so it is even. */
    private static void triangle(VertexConsumer out, Vec3[] p, int color, float alpha) {
        float[] c = WorldFx.rgb(color);
        WorldFx.vertex(out, p[0], 0.5f, 0.5f, c[0], c[1], c[2], alpha);
        WorldFx.vertex(out, p[1], 0.5f, 0.5f, c[0], c[1], c[2], alpha);
        WorldFx.vertex(out, p[2], 0.5f, 0.5f, c[0], c[1], c[2], alpha);
        WorldFx.vertex(out, p[2], 0.5f, 0.5f, c[0], c[1], c[2], alpha);
    }

    @Override
    public ResourceLocation getTextureLocation(ChordField chord) {
        return TextureAtlas.LOCATION_PARTICLES;
    }
}
