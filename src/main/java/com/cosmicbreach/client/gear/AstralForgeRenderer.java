package com.cosmicbreach.client.gear;

import com.cosmicbreach.client.fx.FxRenderTypes;
import com.cosmicbreach.gear.forge.AstralForgeBlockEntity;
import com.cosmicbreach.gear.forge.ForgeRingGeometry;
import com.cosmicbreach.item.GearTier;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The Forge's orbit rings (GDD 3.5): one ring per tier around the anvil, each tilted its own way and turning
 * at its own pace, with a bright mote riding it: I gold, II cyan, III violet, IV white-gold. Drawn additive, as
 * lines of light that face the camera, so a ring reads from any side. A craft flashes them; a new ring grows
 * out from the anvil when the Forge rises a tier.
 */
public class AstralForgeRenderer implements BlockEntityRenderer<AstralForgeBlockEntity> {
    private static final int SEGMENTS = 56;
    private static final int GROW_TICKS = 30;
    private static final int FLASH_TICKS = 16;

    public AstralForgeRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(AstralForgeBlockEntity forge, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        if (forge.getLevel() == null) {
            return;
        }
        int tier = forge.tier();
        double now = forge.getLevel().getGameTime() + partialTick;
        float flash = forge.craftedAt() == Long.MIN_VALUE ? 0f
                : (float) Math.max(0.0, 1.0 - (now - forge.craftedAt()) / FLASH_TICKS);
        double sinceTierUp = forge.tierUpAt() == Long.MIN_VALUE ? Double.MAX_VALUE : now - forge.tierUpAt();
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 centre = Vec3.atLowerCornerOf(forge.getBlockPos()).add(0.5, ForgeRingGeometry.CENTRE_Y, 0.5);
        Vector3f toCamera = new Vector3f((float) (camera.x - centre.x), (float) (camera.y - centre.y), (float) (camera.z - centre.z));
        VertexConsumer out = buffers.getBuffer(FxRenderTypes.additive(FxRenderTypes.GLOW));
        pose.pushPose();
        pose.translate(0.5, 0.0, 0.5);
        Matrix4f m = pose.last().pose();
        for (int i = 0; i < ForgeRingGeometry.ringsFor(tier); i++) {
            ForgeRingGeometry.Ring ring = ForgeRingGeometry.ring(i);
            float grow = 1f;
            if (i == tier - 1 && sinceTierUp < GROW_TICKS) {
                double t = sinceTierUp / GROW_TICKS;
                grow = (float) (1 - Math.pow(1 - t, 3));
            }
            int color = GearTier.color(i + 1);
            float strength = 0.75f + 0.25f * flash + (grow < 1f ? 0.6f * (1f - grow) : 0f);
            drawRing(out, m, toCamera, ring, grow, (float) (now * ring.spinPerTick()), (float) ring.width() * (1f + flash), color,
                    Math.min(1f, strength));
        }
        pose.popPose();
    }

    /** One ring: a loop of camera-facing quads, and a mote at the leading point. */
    private static void drawRing(VertexConsumer out, Matrix4f m, Vector3f toCamera, ForgeRingGeometry.Ring ring, float grow,
                                 float spin, float width, int color, float strength) {
        if (ring.radius() * grow <= 0.01f) {
            return;
        }
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        Vector3f[] points = new Vector3f[SEGMENTS + 1];
        for (int s = 0; s <= SEGMENTS; s++) {
            double a = Math.toRadians(spin) + s * Math.PI * 2.0 / SEGMENTS;
            double[] p = ring.point(a, grow); // the shared geometry: the same points the clearance test walks
            points[s] = new Vector3f((float) p[0], (float) p[1], (float) p[2]);
        }
        for (int s = 0; s < SEGMENTS; s++) {
            Vector3f p0 = points[s];
            Vector3f p1 = points[s + 1];
            Vector3f along = new Vector3f(p1).sub(p0);
            Vector3f view = new Vector3f(toCamera).sub(p0);
            Vector3f side = along.cross(view, new Vector3f());
            if (side.lengthSquared() < 1e-8f) {
                continue;
            }
            side.normalize(width);
            float fade = 0.35f + 0.65f * (float) s / SEGMENTS; // brighter toward the mote, like a trail
            float a = strength * fade;
            quad(out, m, p0, p1, side, r, g, b, a);
        }
        // the mote riding the ring's head
        Vector3f head = points[SEGMENTS];
        Vector3f view = new Vector3f(toCamera).sub(head).normalize();
        Vector3f right = new Vector3f(0, 1, 0).cross(view, new Vector3f());
        if (right.lengthSquared() < 1e-6f) {
            right.set(1, 0, 0);
        }
        right.normalize(width * 4.5f);
        Vector3f up = view.cross(right, new Vector3f()).normalize(width * 4.5f);
        billboard(out, m, head, right, up, Math.min(1f, r * 0.4f + 0.6f), Math.min(1f, g * 0.4f + 0.6f), Math.min(1f, b * 0.4f + 0.6f),
                strength);
    }

    private static void quad(VertexConsumer out, Matrix4f m, Vector3f p0, Vector3f p1, Vector3f side, float r, float g, float b, float a) {
        vertex(out, m, p0.x - side.x, p0.y - side.y, p0.z - side.z, 0.5f, 0f, r, g, b, a);
        vertex(out, m, p0.x + side.x, p0.y + side.y, p0.z + side.z, 0.5f, 1f, r, g, b, a);
        vertex(out, m, p1.x + side.x, p1.y + side.y, p1.z + side.z, 0.5f, 1f, r, g, b, a);
        vertex(out, m, p1.x - side.x, p1.y - side.y, p1.z - side.z, 0.5f, 0f, r, g, b, a);
    }

    private static void billboard(VertexConsumer out, Matrix4f m, Vector3f c, Vector3f right, Vector3f up, float r, float g, float b, float a) {
        vertex(out, m, c.x - right.x - up.x, c.y - right.y - up.y, c.z - right.z - up.z, 1f, 1f, r, g, b, a);
        vertex(out, m, c.x - right.x + up.x, c.y - right.y + up.y, c.z - right.z + up.z, 1f, 0f, r, g, b, a);
        vertex(out, m, c.x + right.x + up.x, c.y + right.y + up.y, c.z + right.z + up.z, 0f, 0f, r, g, b, a);
        vertex(out, m, c.x + right.x - up.x, c.y + right.y - up.y, c.z + right.z - up.z, 0f, 1f, r, g, b, a);
    }

    private static void vertex(VertexConsumer out, Matrix4f m, float x, float y, float z, float u, float v, float r, float g, float b, float a) {
        out.addVertex(m, x, y, z).setUv(u, v).setColor(r, g, b, a);
    }

    @Override
    public AABB getRenderBoundingBox(AstralForgeBlockEntity forge) {
        return new AABB(forge.getBlockPos()).inflate(1.5);
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
