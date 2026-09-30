package com.cosmicbreach.client.relic;

import com.cosmicbreach.client.fx.FxRenderTypes;
import com.cosmicbreach.relic.cantor.CantorRules;
import com.cosmicbreach.relic.cantor.ResonantNote;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

/**
 * A resonant note: a glowing quaver in its tone's colour hanging where the shot landed, a slow ring turning round it and
 * a soft halo that breathes, fading in when struck and out over its last half second.
 */
public class ResonantNoteRenderer extends EntityRenderer<ResonantNote> {
    public ResonantNoteRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(ResonantNote note, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = note.tickCount + partialTick;
        float in = Math.min(1f, age / 4f);
        float out = Math.max(0f, Math.min(1f, (CantorRules.NOTE_LIFE - age) / 10f));
        float a = in * out;
        int color = CantorFx.NoteColors.of(note.tone());
        pose.pushPose();
        pose.translate(0, 0.1 + 0.06 * Math.sin(age * 0.12), 0);
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        VertexConsumer glow = buffers.getBuffer(FxRenderTypes.additive(RelicDraw.GLOW));
        RelicDraw.facing(glow, pose.last(), 0.55f + 0.05f * (float) Math.sin(age * 0.3), 0f, color, 0.55f * a);
        VertexConsumer ring = buffers.getBuffer(FxRenderTypes.additive(RelicDraw.RING));
        RelicDraw.facing(ring, pose.last(), 0.42f, age * 0.04f, color, 0.7f * a);
        VertexConsumer glyph = buffers.getBuffer(FxRenderTypes.additive(RelicDraw.NOTE));
        RelicDraw.facing(glyph, pose.last(), 0.3f, 0f, RelicDraw.PALE_VIOLET, a);
        pose.popPose();
        super.render(note, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(ResonantNote note) {
        return TextureAtlas.LOCATION_PARTICLES;
    }
}
