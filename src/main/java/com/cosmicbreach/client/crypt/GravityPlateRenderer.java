package com.cosmicbreach.client.crypt;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.fx.FxRenderTypes;
import com.cosmicbreach.structure.crypt.trap.GravityPistonBlockEntity;
import com.cosmicbreach.structure.crypt.trap.GravityPlateRules;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * A Crushing Gravity Plate (GDD 6.4): its faint concentric rings on the sigil, drawn only to a careful player within
 * {@link GravityPlateRules#REVEAL_RADIUS} blocks; and the Gravity Piston's head, a 5 by 5 slab of the ceiling that
 * drops onto the sigil on tick 40 of a cycle, rests, and rises back.
 */
public class GravityPlateRenderer implements BlockEntityRenderer<GravityPistonBlockEntity> {
    static final Map<BlockPos, Long> SHOWN = new HashMap<>();
    private static final net.minecraft.resources.ResourceLocation HEAD = CosmicBreach.id("block/gravity_piston_head");

    public static Long shownAt(BlockPos piston) {
        return SHOWN.get(piston);
    }

    @Override
    public void render(GravityPistonBlockEntity piston, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || piston.getLevel() == null) {
            return;
        }
        long t = piston.getLevel().getGameTime();
        double now = t + partialTick;
        Matrix4f m = pose.last().pose();
        int drop = piston.drop();
        // the head, while it slams
        if (piston.cycleStart() >= 0) {
            double d = GravityPlateRules.drop(now - piston.cycleStart());
            if (d > 0) {
                head(piston, buffers, pose, -d * (drop - 0.5));
            }
        }
        // the rings: for careful eyes only
        BlockPos sigil = piston.sigil();
        double dx = Math.max(0, Math.abs(player.getX() - (sigil.getX() + 0.5)) - 2.5);
        double dz = Math.max(0, Math.abs(player.getZ() - (sigil.getZ() + 0.5)) - 2.5);
        double dist = Math.hypot(dx, dz);
        double reach = com.cosmicbreach.accessory.TrapSight.radius(player, GravityPlateRules.REVEAL_RADIUS); // 8 with the Sunshard Compass
        if (Math.abs(player.getY() - (sigil.getY() + 1)) > 2.5 || dist > reach || !CryptClient.careful(player)) {
            return;
        }
        SHOWN.put(piston.getBlockPos(), t);
        float closeness = (float) (1.0 - dist / (reach + 0.5));
        float a = (0.18f + 0.35f * closeness) * (float) (0.75 + 0.25 * Math.sin(now * 0.12));
        VertexConsumer out = buffers.getBuffer(FxRenderTypes.additive(CryptClient.RINGS));
        float y = (float) (-drop + 0.004);
        out.addVertex(m, -2, y, -2).setUv(0, 0).setColor(0.62f, 0.7f, 1.0f, a);
        out.addVertex(m, 3, y, -2).setUv(1, 0).setColor(0.62f, 0.7f, 1.0f, a);
        out.addVertex(m, 3, y, 3).setUv(1, 1).setColor(0.62f, 0.7f, 1.0f, a);
        out.addVertex(m, -2, y, 3).setUv(0, 1).setColor(0.62f, 0.7f, 1.0f, a);
    }

    /** The piston's 5 by 5 head, half a block thick, its top {@code top} blocks below the ceiling's underside. */
    private static void head(GravityPistonBlockEntity piston, MultiBufferSource buffers, PoseStack pose, double top) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(HEAD);
        VertexConsumer out = buffers.getBuffer(RenderType.entitySolid(InventoryMenu.BLOCK_ATLAS));
        BlockPos at = piston.getBlockPos().below(Math.max(1, (int) Math.round(-top) + 1));
        int light = LevelRenderer.getLightColor(piston.getLevel(), at);
        PoseStack.Pose p = pose.last();
        float y1 = (float) top;
        float y0 = y1 - 0.5f;
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        float vh = v0 + (v1 - v0) * 0.5f;
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                // the underside, one texture per block
                v(out, p, i, y0, j + 1, u0, v1, light, 0, -1, 0);
                v(out, p, i + 1, y0, j + 1, u1, v1, light, 0, -1, 0);
                v(out, p, i + 1, y0, j, u1, v0, light, 0, -1, 0);
                v(out, p, i, y0, j, u0, v0, light, 0, -1, 0);
            }
        }
        for (int k = -2; k <= 2; k++) {
            // the four edges, half a block tall
            v(out, p, k, y1, -2, u0, v0, light, 0, 0, -1);
            v(out, p, k + 1, y1, -2, u1, v0, light, 0, 0, -1);
            v(out, p, k + 1, y0, -2, u1, vh, light, 0, 0, -1);
            v(out, p, k, y0, -2, u0, vh, light, 0, 0, -1);
            v(out, p, k + 1, y1, 3, u0, v0, light, 0, 0, 1);
            v(out, p, k, y1, 3, u1, v0, light, 0, 0, 1);
            v(out, p, k, y0, 3, u1, vh, light, 0, 0, 1);
            v(out, p, k + 1, y0, 3, u0, vh, light, 0, 0, 1);
            v(out, p, -2, y1, k + 1, u0, v0, light, -1, 0, 0);
            v(out, p, -2, y1, k, u1, v0, light, -1, 0, 0);
            v(out, p, -2, y0, k, u1, vh, light, -1, 0, 0);
            v(out, p, -2, y0, k + 1, u0, vh, light, -1, 0, 0);
            v(out, p, 3, y1, k, u0, v0, light, 1, 0, 0);
            v(out, p, 3, y1, k + 1, u1, v0, light, 1, 0, 0);
            v(out, p, 3, y0, k + 1, u1, vh, light, 1, 0, 0);
            v(out, p, 3, y0, k, u0, vh, light, 1, 0, 0);
        }
    }

    private static void v(VertexConsumer out, PoseStack.Pose p, float x, float y, float z, float u, float v, int light, float nx, float ny,
            float nz) {
        out.addVertex(p, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(p, nx, ny, nz);
    }

    @Override
    public boolean shouldRenderOffScreen(GravityPistonBlockEntity piston) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(GravityPistonBlockEntity piston) {
        return new AABB(piston.getBlockPos()).inflate(2.5, 0, 2.5).expandTowards(0, -piston.drop() - 1, 0);
    }
}
