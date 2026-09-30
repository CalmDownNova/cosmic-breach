package com.cosmicbreach.client.crypt;

import com.cosmicbreach.client.fx.FxRenderTypes;
import com.cosmicbreach.structure.crypt.trap.VoidRiftBlockEntity;
import com.cosmicbreach.structure.crypt.trap.VoidRiftRules;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * A Void Rift cluster's tell (GDD 6.4): hairline cracks with a violet shimmer across its 3 by 3 patch, drawn only
 * to a careful player (sneaking or walking) within {@link VoidRiftRules#REVEAL_RADIUS} blocks, fading in as they
 * come closer. While the patch is open, its rim glows violet over the drop. Records when each cluster was last
 * shown (tests read it).
 */
public class RiftTileRenderer implements BlockEntityRenderer<VoidRiftBlockEntity> {
    static final Map<BlockPos, Long> SHOWN = new HashMap<>();

    public static Long shownAt(BlockPos middle) {
        return SHOWN.get(middle);
    }

    @Override
    public void render(VoidRiftBlockEntity rift, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || rift.getLevel() == null) {
            return;
        }
        long t = rift.getLevel().getGameTime();
        Matrix4f m = pose.last().pose();
        VertexConsumer out = buffers.getBuffer(FxRenderTypes.additive(CryptClient.CRACKS));
        if (rift.open()) {
            quad(out, m, 1.02, 0.85f, 0.35f, 1.0f, 0.9f);
            return;
        }
        double closeness = closeness(rift.patch(), player);
        if (closeness <= 0 || !CryptClient.careful(player)) {
            return;
        }
        SHOWN.put(rift.getBlockPos(), t);
        float shimmer = (float) (0.55 + 0.35 * Math.sin((t + partialTick) * 0.35));
        quad(out, m, 1.004, 0.72f, 0.38f, 1.0f, (float) (0.25 + 0.6 * closeness) * shimmer);
    }

    /** 0 beyond the reveal radius, up to 1 standing on it: from the player's feet to the patch's nearest point. */
    static double closeness(AABB patch, LocalPlayer player) {
        double dx = Math.max(0, Math.max(patch.minX - player.getX(), player.getX() - patch.maxX));
        double dz = Math.max(0, Math.max(patch.minZ - player.getZ(), player.getZ() - patch.maxZ));
        double dy = Math.abs(player.getY() - patch.maxY);
        if (dy > 2.5) {
            return 0;
        }
        double d = Math.hypot(dx, dz);
        double reach = com.cosmicbreach.accessory.TrapSight.radius(player, VoidRiftRules.REVEAL_RADIUS); // 8 with the Sunshard Compass
        return d > reach ? 0 : 1.0 - d / (reach + 0.5);
    }

    /** The cracks texture over the 3 by 3 patch at height {@code y} (the middle tile's block space). */
    private static void quad(VertexConsumer out, Matrix4f m, double y, float r, float g, float b, float a) {
        float yy = (float) y;
        out.addVertex(m, -1, yy, -1).setUv(0, 0).setColor(r, g, b, a);
        out.addVertex(m, 2, yy, -1).setUv(1, 0).setColor(r, g, b, a);
        out.addVertex(m, 2, yy, 2).setUv(1, 1).setColor(r, g, b, a);
        out.addVertex(m, -1, yy, 2).setUv(0, 1).setColor(r, g, b, a);
    }

    @Override
    public boolean shouldRenderOffScreen(VoidRiftBlockEntity rift) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(VoidRiftBlockEntity rift) {
        return new AABB(rift.getBlockPos()).inflate(1.5, 0.5, 1.5);
    }
}
