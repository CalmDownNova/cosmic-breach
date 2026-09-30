package com.cosmicbreach.client.astrolabe;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.fx.FxRenderTypes;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatStateMachine;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * The Choir Astrolabe in the hand (GDD 4.2: "a handheld astrolabe whose rings spin as you fight"; "brass rings orbit the
 * hand and spin faster with Resonance"). Built in the item's own space on its sprite's layout (a handle rising from the
 * lower left to a disc at the upper right, so the handheld display holds it by the handle): a violet-silk grip, an
 * enamel star-map disc in a brass rim, a glowing star at its heart, and three brass rings round the disc, each with
 * three gold beads so the spin reads: one tumbling about the handle, one turning flat round the disc, one tumbling across
 * it. They turn from slow to fast with the holder's Resonance, spin up while a charge is held, and kick on each bolt.
 * The inventory shows the flat sprite instead (the model's separate GUI view).
 */
public final class AstrolabeItemRenderer extends BlockEntityWithoutLevelRenderer {
    static final ResourceLocation PARTS = CosmicBreach.id("textures/item/choir_astrolabe_parts.png");
    private static AstrolabeItemRenderer instance;

    /** Everything in sprite pixels (32 across), y up. */
    private static final double PX = 1.0 / 32.0;
    private static final Vec3 C = new Vec3(19.5, 19.5, 16.0);
    private static final Vec3 KNOB = new Vec3(4.6, 4.6, 16.0);
    private static final double DISC_R = 7.0;
    private static final Vec3 H = new Vec3(1, 1, 0).normalize();      // along the handle, toward the disc
    private static final Vec3 N = new Vec3(0, 0, 1);                  // the disc's face
    private static final Vec3 P = N.cross(H).normalize();              // across the handle, in the disc's plane
    private static final double[] RING_R = {9.4, 10.9, 12.4};
    private static final double RING_HALF = 0.45;
    /** Where the hand holds it (Meridian's grip texel, 8.2, 22.8), and how big it is drawn round that point. */
    private static final Vec3 GRIP = new Vec3(8.2, 32.0 - 22.8, 16.0);
    private static final float SIZE = 0.88f;
    /**
     * In first person, while a move plays, the arm raises the Astrolabe right in front of the eye, where it filled half
     * the view (G7's open note): it is drawn at this share of its size there, eased in and out over a few frames.
     */
    private static final float CAST_FIRST_PERSON = 0.58f;
    private float castShare;
    private long castShareAt;

    // UV regions (64 x 64 texture)
    private static final float[] FACE = {0f, 0f, 0.5f, 0.5f};
    private static final float[] BRASS = {0.5f, 0f, 0.75f, 0.25f};
    private static final float[] SILK = {0.75f, 0f, 1f, 0.25f};
    private static final float[] GOLD = {0.5f, 0.25f, 0.75f, 0.5f};

    /** Per holder (entity id, or -1): the three ring angles and when they were last advanced (game ticks). */
    private final Map<Integer, double[]> spin = new HashMap<>();

    private AstrolabeItemRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet models) {
        super(dispatcher, models);
    }

    public static AstrolabeItemRenderer get() {
        if (instance == null) {
            Minecraft mc = Minecraft.getInstance();
            instance = new AstrolabeItemRenderer(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels());
        }
        return instance;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        Entity holder = AstrolabeHolder.current();
        if (holder == null && context.firstPerson()) {
            holder = mc.player;
        }
        double[] angles = advance(holder, mc);
        float resonance = resonance(holder);
        float size = SIZE;
        // the animated first person draws the local player's body, and the held item in its hand as a third-person item
        boolean ownView = context.firstPerson() || holder == mc.player && mc.options.getCameraType().isFirstPerson();
        if (ownView) {
            size *= 1f - (1f - CAST_FIRST_PERSON) * castShare(mc);
        }
        pose.pushPose();
        pose.scale((float) PX, (float) PX, (float) PX);
        pose.translate(GRIP.x, GRIP.y, GRIP.z); // a touch smaller than its sprite, held by the same grip
        pose.scale(size, size, size);
        pose.translate(-GRIP.x, -GRIP.y, -GRIP.z);
        PoseStack.Pose last = pose.last();
        VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(PARTS));
        handle(solid, last, light, overlay);
        disc(solid, last, light, overlay);
        torus(solid, last, light, overlay, C, H, P, DISC_R + 0.25, 0.55, 28, BRASS, 0.95f);
        Vec3[] u = new Vec3[3];
        Vec3[] v = new Vec3[3];
        // ring 0 tumbles about the handle, ring 1 turns flat round the disc, ring 2 tumbles across it (tilted)
        u[0] = H;
        v[0] = N.scale(Math.cos(angles[0])).add(P.scale(Math.sin(angles[0])));
        u[1] = H.scale(Math.cos(angles[1])).add(P.scale(Math.sin(angles[1])));
        v[1] = H.scale(-Math.sin(angles[1])).add(P.scale(Math.cos(angles[1])));
        Vec3 tiltAxis = P.scale(Math.cos(0.5)).add(H.scale(Math.sin(0.5)));
        Vec3 tiltOther = N.cross(tiltAxis).normalize();
        u[2] = tiltAxis;
        v[2] = N.scale(Math.cos(angles[2])).add(tiltOther.scale(Math.sin(angles[2])));
        for (int i = 0; i < 3; i++) {
            torus(solid, last, light, overlay, C, u[i], v[i], RING_R[i], RING_HALF, 40, BRASS, 1.0f);
            for (int b = 0; b < 3; b++) {
                double a = b * Math.PI * 2 / 3 + i * 0.6;
                Vec3 at = C.add(u[i].scale(Math.cos(a) * RING_R[i])).add(v[i].scale(Math.sin(a) * RING_R[i]));
                cube(solid, last, light, overlay, at, 0.85, GOLD);
            }
        }
        glow(buffers, last, resonance, angles, u, v);
        pose.popPose();
    }

    /** The star at the disc's heart and, with Resonance, the beads catching light: added light, full bright. */
    private static void glow(MultiBufferSource buffers, PoseStack.Pose pose, float resonance, double[] angles, Vec3[] u, Vec3[] v) {
        VertexConsumer out = buffers.getBuffer(FxRenderTypes.additive(AstroDraw.STAR));
        Matrix4f m = pose.pose();
        float heart = 0.45f + 0.55f * resonance;
        double spinHeart = angles[1] * 0.5;
        for (double z : new double[] {C.z + 1.35, C.z - 1.35}) {
            flatStar(out, m, C.x, C.y, z, 3.0 + 2.5 * resonance, spinHeart, 1f, 0.95f, 0.8f, heart);
        }
        if (resonance > 0.3f) {
            VertexConsumer g = buffers.getBuffer(FxRenderTypes.additive(AstroDraw.GLOW));
            float a = (resonance - 0.3f) / 0.7f;
            for (int i = 0; i < 3; i++) {
                for (int b = 0; b < 3; b++) {
                    double ang = b * Math.PI * 2 / 3 + i * 0.6;
                    Vec3 at = C.add(u[i].scale(Math.cos(ang) * RING_R[i])).add(v[i].scale(Math.sin(ang) * RING_R[i]));
                    flatStar(g, m, at.x, at.y, at.z + 0.9, 1.6, 0, 1f, 0.85f, 0.5f, 0.7f * a);
                }
            }
        }
    }

    private static void flatStar(VertexConsumer out, Matrix4f m, double x, double y, double z, double half, double roll,
                                 float r, float g, float b, float a) {
        float c = (float) (Math.cos(roll) * half);
        float s = (float) (Math.sin(roll) * half);
        out.addVertex(m, (float) (x - c + s), (float) (y - s - c), (float) z).setUv(0f, 1f).setColor(r, g, b, a);
        out.addVertex(m, (float) (x + c + s), (float) (y + s - c), (float) z).setUv(1f, 1f).setColor(r, g, b, a);
        out.addVertex(m, (float) (x + c - s), (float) (y + s + c), (float) z).setUv(1f, 0f).setColor(r, g, b, a);
        out.addVertex(m, (float) (x - c - s), (float) (y - s + c), (float) z).setUv(0f, 0f).setColor(r, g, b, a);
    }

    // ------------------------------------------------------------------ spin

    /** 0 at rest, 1 while the local player's move plays, eased (about a third of the way each 20 ms). */
    private float castShare(Minecraft mc) {
        boolean casting = mc.player != null
                && com.cosmicbreach.combat.PlayerCombat.of(mc.player).machine().current() != null;
        long now = net.minecraft.Util.getMillis();
        float dt = castShareAt == 0 ? 1000f : Math.min(1000f, now - castShareAt);
        castShareAt = now;
        float k = 1f - (float) Math.pow(0.65, dt / 20.0);
        castShare += ((casting ? 1f : 0f) - castShare) * k;
        return castShare;
    }

    private double[] advance(@Nullable Entity holder, Minecraft mc) {
        int key = holder == null ? -1 : holder.getId();
        double now = mc.level == null ? 0.0 : mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(false);
        double[] s = spin.computeIfAbsent(key, k -> new double[] {0.0, 1.3, 2.6, now});
        double dt = Math.max(0.0, Math.min(10.0, now - s[3]));
        s[3] = now;
        double rate = rate(holder) / 20.0; // radians a tick
        s[0] += dt * rate;
        s[1] += dt * rate * 0.7;
        s[2] += dt * rate * 1.35;
        if (spin.size() > 64) {
            spin.clear();
        }
        return s;
    }

    /** Radians a second: 1.2 at rest, six times that at full Resonance, more while charging or just after a bolt. */
    static double rate(@Nullable Entity holder) {
        double r = 1.2 * (1.0 + 5.0 * resonance(holder));
        if (holder instanceof Player p && p == Minecraft.getInstance().player) {
            CombatStateMachine m = PlayerCombat.of(p).machine();
            if (m.phase() == CombatStateMachine.Phase.CHARGING) {
                r *= 2.5;
            } else if (m.phase() == CombatStateMachine.Phase.ACTIVE || m.phase() == CombatStateMachine.Phase.STARTUP) {
                r *= 1.8;
            }
        }
        return r;
    }

    /** The holder's Resonance, 0 to 1 (only the local player's is known; others turn at a middling pace). */
    static float resonance(@Nullable Entity holder) {
        if (holder instanceof Player p && p == Minecraft.getInstance().player) {
            CombatStateMachine m = PlayerCombat.of(p).machine();
            return (float) Math.max(0.0, Math.min(1.0, m.resonance() / Math.max(1, m.maxResonance())));
        }
        return holder == null ? 0.0f : 0.4f;
    }

    // ------------------------------------------------------------------ geometry (sprite pixels)

    private static void handle(VertexConsumer out, PoseStack.Pose pose, int light, int overlay) {
        Vec3 top = C.subtract(H.scale(DISC_R));
        tube(out, pose, light, overlay, KNOB, top, 1.05, 8, SILK);
        for (double f : new double[] {0.28, 0.62}) { // gold bands round the silk
            Vec3 at = KNOB.add(top.subtract(KNOB).scale(f));
            tube(out, pose, light, overlay, at.subtract(H.scale(0.45)), at.add(H.scale(0.45)), 1.3, 8, GOLD);
        }
        tube(out, pose, light, overlay, top.subtract(H.scale(1.2)), top.add(H.scale(0.3)), 1.55, 8, BRASS); // the collar
        cube(out, pose, light, overlay, KNOB, 1.6, BRASS); // the pommel
    }

    /** The disc: the enamel star map on both faces, in its brass rim. */
    private static void disc(VertexConsumer out, PoseStack.Pose pose, int light, int overlay) {
        int n = 32;
        for (int side = -1; side <= 1; side += 2) {
            double z = C.z + side * 0.9;
            for (int i = 0; i < n; i++) {
                double a0 = 2 * Math.PI * i / n;
                double a1 = 2 * Math.PI * (i + 1) / n;
                Vec3 p0 = C.add(Math.cos(a0) * DISC_R, Math.sin(a0) * DISC_R, 0).with(net.minecraft.core.Direction.Axis.Z, z);
                Vec3 p1 = C.add(Math.cos(a1) * DISC_R, Math.sin(a1) * DISC_R, 0).with(net.minecraft.core.Direction.Axis.Z, z);
                Vec3 c = C.with(net.minecraft.core.Direction.Axis.Z, z);
                float u0 = (float) (0.5 + 0.5 * Math.cos(a0) * side);
                float v0 = (float) (0.5 - 0.5 * Math.sin(a0));
                float u1 = (float) (0.5 + 0.5 * Math.cos(a1) * side);
                float v1 = (float) (0.5 - 0.5 * Math.sin(a1));
                Vec3 nrm = N.scale(side);
                if (side > 0) {
                    quad(out, pose, light, overlay, c, p0, p1, c, uv(FACE, 0.5f, 0.5f), uv(FACE, u0, v0), uv(FACE, u1, v1), uv(FACE, 0.5f, 0.5f), nrm);
                } else {
                    quad(out, pose, light, overlay, c, p1, p0, c, uv(FACE, 0.5f, 0.5f), uv(FACE, u1, v1), uv(FACE, u0, v0), uv(FACE, 0.5f, 0.5f), nrm);
                }
            }
        }
        // the disc's edge
        for (int i = 0; i < n; i++) {
            double a0 = 2 * Math.PI * i / n;
            double a1 = 2 * Math.PI * (i + 1) / n;
            Vec3 r0 = new Vec3(Math.cos(a0), Math.sin(a0), 0);
            Vec3 r1 = new Vec3(Math.cos(a1), Math.sin(a1), 0);
            Vec3 a = C.add(r0.scale(DISC_R)).add(0, 0, -0.9);
            Vec3 b = C.add(r1.scale(DISC_R)).add(0, 0, -0.9);
            Vec3 c = C.add(r1.scale(DISC_R)).add(0, 0, 0.9);
            Vec3 d = C.add(r0.scale(DISC_R)).add(0, 0, 0.9);
            quad(out, pose, light, overlay, a, b, c, d, uv(BRASS, 0f, 0f), uv(BRASS, 1f, 0f), uv(BRASS, 1f, 1f), uv(BRASS, 0f, 1f),
                    r0.add(r1).normalize());
        }
    }

    /** A ring of radius {@code radius} round {@code centre} in the plane of {@code u} and {@code v}, a square tube {@code half} thick. */
    private static void torus(VertexConsumer out, PoseStack.Pose pose, int light, int overlay, Vec3 centre, Vec3 u, Vec3 v, double radius,
                              double half, int segments, float[] region, float shade) {
        Vec3 n = u.cross(v).normalize();
        for (int i = 0; i < segments; i++) {
            double a0 = 2 * Math.PI * i / segments;
            double a1 = 2 * Math.PI * (i + 1) / segments;
            Vec3 r0 = u.scale(Math.cos(a0)).add(v.scale(Math.sin(a0)));
            Vec3 r1 = u.scale(Math.cos(a1)).add(v.scale(Math.sin(a1)));
            float t0 = (float) i / segments;
            float t1 = (float) (i + 1) / segments;
            Vec3[][] faces = {
                    {r0.scale(radius + half).add(n.scale(-half)), r1.scale(radius + half).add(n.scale(-half)),
                            r1.scale(radius + half).add(n.scale(half)), r0.scale(radius + half).add(n.scale(half))},
                    {r0.scale(radius - half).add(n.scale(half)), r1.scale(radius - half).add(n.scale(half)),
                            r1.scale(radius - half).add(n.scale(-half)), r0.scale(radius - half).add(n.scale(-half))},
                    {r0.scale(radius + half).add(n.scale(half)), r1.scale(radius + half).add(n.scale(half)),
                            r1.scale(radius - half).add(n.scale(half)), r0.scale(radius - half).add(n.scale(half))},
                    {r0.scale(radius - half).add(n.scale(-half)), r1.scale(radius - half).add(n.scale(-half)),
                            r1.scale(radius + half).add(n.scale(-half)), r0.scale(radius + half).add(n.scale(-half))}};
            Vec3 mid = r0.add(r1).normalize();
            Vec3[] normals = {mid, mid.scale(-1), n, n.scale(-1)};
            for (int f = 0; f < 4; f++) {
                Vec3[] q = faces[f];
                quad(out, pose, light, overlay, centre.add(q[0]), centre.add(q[1]), centre.add(q[2]), centre.add(q[3]),
                        uv(region, t0, 0.15f + 0.2f * f), uv(region, t1, 0.15f + 0.2f * f), uv(region, t1, 0.3f + 0.2f * f),
                        uv(region, t0, 0.3f + 0.2f * f), normals[f]);
            }
        }
    }

    /** A tube from {@code a} to {@code b}, {@code radius} round, {@code sides} faces. */
    private static void tube(VertexConsumer out, PoseStack.Pose pose, int light, int overlay, Vec3 a, Vec3 b, double radius, int sides,
                             float[] region) {
        Vec3 axis = b.subtract(a).normalize();
        Vec3 s1 = axis.cross(N).lengthSqr() < 1e-6 ? axis.cross(new Vec3(1, 0, 0)).normalize() : axis.cross(N).normalize();
        Vec3 s2 = axis.cross(s1).normalize();
        for (int i = 0; i < sides; i++) {
            double a0 = 2 * Math.PI * i / sides;
            double a1 = 2 * Math.PI * (i + 1) / sides;
            Vec3 d0 = s1.scale(Math.cos(a0)).add(s2.scale(Math.sin(a0)));
            Vec3 d1 = s1.scale(Math.cos(a1)).add(s2.scale(Math.sin(a1)));
            float t0 = (float) i / sides;
            float t1 = (float) (i + 1) / sides;
            quad(out, pose, light, overlay, a.add(d0.scale(radius)), a.add(d1.scale(radius)), b.add(d1.scale(radius)), b.add(d0.scale(radius)),
                    uv(region, t0, 0f), uv(region, t1, 0f), uv(region, t1, 1f), uv(region, t0, 1f), d0.add(d1).normalize());
        }
    }

    /** A little cube (a bead, the pommel) of half size {@code half} at {@code at}. */
    private static void cube(VertexConsumer out, PoseStack.Pose pose, int light, int overlay, Vec3 at, double half, float[] region) {
        Vec3[] axes = {new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1)};
        for (Vec3 n : axes) {
            for (int sign = -1; sign <= 1; sign += 2) {
                Vec3 f = n.scale(sign * half);
                Vec3 a = n.x != 0 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
                Vec3 b = n.cross(a).scale(sign);
                a = a.scale(half);
                b = b.scale(half);
                Vec3 c = at.add(f);
                quad(out, pose, light, overlay, c.subtract(a).subtract(b), c.add(a).subtract(b), c.add(a).add(b), c.subtract(a).add(b),
                        uv(region, 0f, 0f), uv(region, 1f, 0f), uv(region, 1f, 1f), uv(region, 0f, 1f), n.scale(sign));
            }
        }
    }

    private static float[] uv(float[] region, float u, float v) {
        return new float[] {region[0] + (region[2] - region[0]) * u, region[1] + (region[3] - region[1]) * v};
    }

    private static void quad(VertexConsumer out, PoseStack.Pose pose, int light, int overlay, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
                             float[] ua, float[] ub, float[] uc, float[] ud, Vec3 normal) {
        vertex(out, pose, a, ua, light, overlay, normal);
        vertex(out, pose, b, ub, light, overlay, normal);
        vertex(out, pose, c, uc, light, overlay, normal);
        vertex(out, pose, d, ud, light, overlay, normal);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, Vec3 p, float[] uv, int light, int overlay, Vec3 n) {
        out.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(255, 255, 255, 255).setUv(uv[0], uv[1])
                .setOverlay(overlay).setLight(light).setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
    }

    /** Full bright, for a preview or a test. */
    static int fullBright() {
        return LightTexture.FULL_BRIGHT;
    }
}
