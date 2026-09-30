package com.cosmicbreach.client.crypt;

import com.cosmicbreach.client.fx.FxRenderTypes;
import com.cosmicbreach.structure.crypt.trap.ChuteRules;
import com.cosmicbreach.structure.crypt.trap.StarfallChuteBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * A Starfall Chute (GDD 6.4): the glint at its three apertures in the {@link ChuteRules#GLINT_LEAD} ticks before each
 * drop, for a careful player within {@link ChuteRules#REVEAL_RADIUS} blocks of its strip, and the star-rocks
 * themselves (for everyone), glowing, falling faster as they go, streaking light behind them.
 */
public class ChuteRenderer implements BlockEntityRenderer<StarfallChuteBlockEntity> {
    static final Map<BlockPos, Long> SHOWN = new HashMap<>();
    /** How long a landed star-rock keeps glowing on the floor. */
    private static final int EMBER_TICKS = 16;

    public static Long shownAt(BlockPos chute) {
        return SHOWN.get(chute);
    }

    @Override
    public void render(StarfallChuteBlockEntity chute, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || chute.getLevel() == null) {
            return;
        }
        long t = chute.getLevel().getGameTime();
        Matrix4f m = pose.last().pose();
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f left = new Vector3f(cam.getLeftVector());
        Vector3f up = new Vector3f(cam.getUpVector());
        boolean alongX = chute.axis() == Direction.Axis.X;
        int period = chute.period();
        int phase = chute.phase();
        // the rocks: during the fall after each release, then glowing where they struck, fading
        int since = ChuteRules.sinceRelease(t, period, phase);
        if (since >= ChuteRules.FALL_TICKS && since < ChuteRules.FALL_TICKS + EMBER_TICKS) {
            float fade = 1.0f - (since - ChuteRules.FALL_TICKS + partialTick) / EMBER_TICKS;
            VertexConsumer glow = buffers.getBuffer(FxRenderTypes.additive(CryptClient.GLOW));
            float y = (float) (-chute.drop() + 0.25);
            for (int k = -1; k <= 1; k++) {
                float x = 0.5f + (alongX ? k : 0);
                float z = 0.5f + (alongX ? 0 : k);
                billboard(glow, m, x, y, z, left, up, 0.3f, 1.0f, 0.8f, 0.45f, 0.9f * fade);
                billboard(glow, m, x, y, z, left, up, 0.7f, 1.0f, 0.5f, 0.25f, 0.35f * fade);
            }
        }
        if (since < ChuteRules.FALL_TICKS) {
            double s = (since + partialTick) / ChuteRules.FALL_TICKS;
            double y = -s * s * (chute.drop() - 0.3);
            VertexConsumer glow = buffers.getBuffer(FxRenderTypes.additive(CryptClient.GLOW));
            for (int k = -1; k <= 1; k++) {
                float x = 0.5f + (alongX ? k : 0);
                float z = 0.5f + (alongX ? 0 : k);
                billboard(glow, m, x, (float) y - 0.2f, z, left, up, 0.38f, 1.0f, 0.86f, 0.55f, 1.0f);
                billboard(glow, m, x, (float) y - 0.2f, z, left, up, 0.75f, 1.0f, 0.55f, 0.3f, 0.45f);
                // the streak above it
                for (int q = 1; q <= 3; q++) {
                    billboard(glow, m, x, (float) (y - 0.2 + q * 0.45 * s + 0.1), z, left, up, 0.26f - 0.05f * q, 1.0f, 0.8f, 0.5f,
                            0.5f - 0.13f * q);
                }
            }
        }
        // the glint: careful eyes only
        if (!ChuteRules.glinting(t, period, phase) || !CryptClient.careful(player)) {
            return;
        }
        AABB strip = chute.strip();
        double dx = Math.max(0, Math.max(strip.minX - player.getX(), player.getX() - strip.maxX));
        double dz = Math.max(0, Math.max(strip.minZ - player.getZ(), player.getZ() - strip.maxZ));
        if (Math.hypot(dx, dz) > com.cosmicbreach.accessory.TrapSight.radius(player, ChuteRules.REVEAL_RADIUS) || Math.abs(player.getY() - strip.minY) > 2.5) {
            return;
        }
        SHOWN.put(chute.getBlockPos(), t);
        int until = ChuteRules.untilRelease(t, period, phase);
        float k = 1.0f - (until - partialTick) / ChuteRules.GLINT_LEAD;
        float twinkle = (float) (0.7 + 0.3 * Math.sin((t + partialTick) * 1.7));
        VertexConsumer glint = buffers.getBuffer(FxRenderTypes.additive(CryptClient.GLINT));
        for (int i = -1; i <= 1; i++) {
            float x = 0.5f + (alongX ? i : 0);
            float z = 0.5f + (alongX ? 0 : i);
            billboard(glint, m, x, -0.08f, z, left, up, 0.25f + 0.2f * k, 1.0f, 0.95f, 0.75f, (0.35f + 0.65f * k) * twinkle);
        }
    }

    /** A camera-facing square round (x, y, z), {@code half} blocks each way. */
    private static void billboard(VertexConsumer out, Matrix4f m, float x, float y, float z, Vector3f left, Vector3f up, float half,
            float r, float g, float b, float a) {
        float lx = left.x() * half;
        float ly = left.y() * half;
        float lz = left.z() * half;
        float ux = up.x() * half;
        float uy = up.y() * half;
        float uz = up.z() * half;
        out.addVertex(m, x + lx - ux, y + ly - uy, z + lz - uz).setUv(0, 1).setColor(r, g, b, a);
        out.addVertex(m, x - lx - ux, y - ly - uy, z - lz - uz).setUv(1, 1).setColor(r, g, b, a);
        out.addVertex(m, x - lx + ux, y - ly + uy, z - lz + uz).setUv(1, 0).setColor(r, g, b, a);
        out.addVertex(m, x + lx + ux, y + ly + uy, z + lz + uz).setUv(0, 0).setColor(r, g, b, a);
    }

    @Override
    public boolean shouldRenderOffScreen(StarfallChuteBlockEntity chute) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(StarfallChuteBlockEntity chute) {
        return new AABB(chute.getBlockPos()).inflate(1.5, 0, 1.5).expandTowards(0, -chute.drop() - 1, 0);
    }
}
