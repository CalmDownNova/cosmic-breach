package com.cosmicbreach.client.fx;

import com.cosmicbreach.client.anim.PlayerAnimations;
import com.cosmicbreach.client.combat.TwinBlades;
import com.cosmicbreach.combat.data.WeaponDef;
import com.cosmicbreach.item.CombatWeaponItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;

/**
 * Where each player's combat weapon is drawn, frame by frame: read from the item's own pose as it is
 * rendered ({@code ItemInHandRendererMixin}), so it is exactly the blade on screen, whatever plays (an
 * animation and its hit-stop, a blend, the first-person framing, vanilla's hand) and at any frame rate.
 * Each frame gives a {@link BladePose}: the guard, the tip and one texel across the blade toward its
 * upper edge, with the animation and its time at that frame. Which part of the sprite is "the blade" is
 * the weapon's own ({@link WeaponDef#bladeOrDefault}): Meridian's crossguard to its point, the Comet
 * Maul's whole head, so everything that follows the blade follows the part that strikes.
 *
 * <p>A weapon with a second blade in the off hand ({@code TwinBlades}, the Binary Edges) has its off blade
 * tracked the same way, apart ({@link #offPoses}), with that blade's own layout.
 *
 * <p>Three spaces: a player drawn in the world is kept in world coordinates; the animated first person
 * (the camera's own player, drawn in the world pass by playerAnimator) in the camera's frame, since
 * that blade turns with the camera; vanilla's first-person hand in the camera's frame too, as the
 * hand pass draws it at its fixed 70 degree field of view (drawn over the world, so effects meant to be
 * seen there go past the blade's edge). Frames are counted from {@link #beginFrame}.
 */
public final class BladeTracker {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Meridian's blade's half width: from the sprite's diagonal to its upper edge, in texels. */
    public static final float HALF_WIDTH_TEXELS = WeaponDef.Blade.MERIDIAN.halfWidth();
    private static final int MAX_POSES = 120;
    /** Poses older than this many game ticks are dropped. */
    private static final double KEEP_TICKS = 30.0;
    private static final double HAND_FOV_TAN = Math.tan(Math.toRadians(35.0));

    public enum Space { WORLD, VIEW, HAND }

    /**
     * The blade in one frame. {@code guard}, {@code tip} and {@code across} (one texel toward the
     * blade's upper edge, a direction) are world coordinates for {@link Space#WORLD}, else the camera's
     * frame (x right, y up, -z ahead); {@code animation} and {@code animationTime} are what the
     * player's combat layer played then (null and 0 for none), {@code gameTime} the client's time.
     */
    public record BladePose(long frame, double gameTime, Space space, @Nullable ResourceLocation animation,
                            double animationTime, Vec3 guard, Vec3 tip, Vec3 across, float halfWidth) {
        /** A point on the blade, {@code along} from the guard (0) to the tip (1), {@code edge} texels toward its upper edge. */
        public Vec3 point(double along, double edge) {
            return guard.add(tip.subtract(guard).scale(along)).add(across.scale(edge));
        }
    }

    private static final Map<Integer, Deque<BladePose>> POSES = new HashMap<>();
    private static final Map<Integer, Deque<BladePose>> OFF_POSES = new HashMap<>();
    /** While true (an afterimage being captured), drawn blades are not tracked. */
    private static boolean suppressed;
    private static long frame;
    /** True while the level is drawn (from its sky to its end): only then is a held item drawn in the world. */
    private static boolean levelPass;
    /** tan(half the world's vertical field of view), from the last level render. */
    private static double worldFovTan = HAND_FOV_TAN;
    private static boolean reportedError;

    private BladeTracker() {
    }

    // ------------------------------------------------------------------ capture

    /** A new frame starts (before anything is drawn). */
    static void beginFrame() {
        frame++;
        levelPass = false;
    }

    /**
     * The level's drawing begins or ends. Items drawn in a third-person context outside it (the
     * inventory's player preview, a mod's paper doll) are in screen space, not the world, and are skipped.
     */
    static void levelPass(boolean drawing) {
        levelPass = drawing;
    }

    /** The level is drawn with this projection: its field of view converts the hand's poses. */
    static void levelProjection(Matrix4f projection) {
        float m11 = projection.m11();
        if (m11 > 1e-3f) {
            worldFovTan = 1.0 / m11;
        }
    }

    /** From the mixin: {@code entity} is about to draw {@code stack} held in a hand, posed by {@code pose}. */
    public static void onItemRendered(LivingEntity entity, ItemStack stack, ItemDisplayContext context, boolean leftHand,
                                      PoseStack pose) {
        if (suppressed || !(entity instanceof Player player) || (!context.firstPerson() && !levelPass)) {
            return;
        }
        boolean main = stack == player.getMainHandItem() && CombatWeaponItem.isCombatWeapon(stack);
        boolean off = !main && TwinBlades.isOffBlade(player, stack);
        if (!main && !off) {
            return;
        }
        try {
            capture(player, stack, context, leftHand, pose, off);
        } catch (RuntimeException e) {
            if (!reportedError) {
                reportedError = true;
                LOGGER.error("[cosmicbreach] could not read where the blade is drawn", e);
            }
        }
    }

    /** Afterimages being captured draw the player again: those blades are not tracked. */
    public static void suppress(boolean suppress) {
        suppressed = suppress;
    }

    private static void capture(Player player, ItemStack stack, ItemDisplayContext context, boolean leftHand, PoseStack pose,
                                boolean off) {
        Minecraft mc = Minecraft.getInstance();
        // The rest of what ItemRenderer.render does before drawing: the model's display transform, then centring.
        BakedModel model = mc.getItemRenderer().getModel(stack, player.level(), player, player.getId() + context.ordinal());
        PoseStack item = new PoseStack();
        item.last().pose().set(pose.last().pose());
        item.last().normal().set(pose.last().normal());
        model.applyTransform(context, item, leftHand);
        item.translate(-0.5f, -0.5f, -0.5f);
        Matrix4f m = item.last().pose();
        WeaponDef.Blade blade;
        if (off) {
            WeaponDef.OffHand offHand = TwinBlades.offHandOf(player);
            blade = offHand == null ? WeaponDef.Blade.MERIDIAN : offHand.blade();
        } else {
            WeaponDef weapon = CombatWeaponItem.weaponOf(stack, true);
            blade = weapon == null ? WeaponDef.Blade.MERIDIAN : weapon.bladeOrDefault();
        }
        Vector3f guardTexel = texel(blade.guard().get(0), blade.guard().get(1));
        Vector3f tipTexel = texel(blade.tip().get(0), blade.tip().get(1));
        Vector3f guard = m.transformPosition(guardTexel, new Vector3f());
        Vector3f tip = m.transformPosition(tipTexel, new Vector3f());
        Vector3f across = m.transformDirection(acrossOf(guardTexel, tipTexel), new Vector3f());

        // Both passes leave points camera-relative in world orientation (the hand pass undoes the view
        // rotation in its pose stack, the world pass draws entities relative to the camera).
        Camera camera = mc.gameRenderer.getMainCamera();
        Space space;
        if (context.firstPerson()) {
            space = Space.HAND;
        } else if (player == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) {
            space = Space.VIEW;
        } else {
            space = Space.WORLD;
        }
        Vec3 g;
        Vec3 t;
        Vec3 a;
        if (space == Space.WORLD) {
            Vec3 origin = camera.getPosition();
            g = origin.add(guard.x, guard.y, guard.z);
            t = origin.add(tip.x, tip.y, tip.z);
            a = new Vec3(across.x, across.y, across.z);
        } else {
            Quaternionf toView = camera.rotation().conjugate(new Quaternionf());
            g = vec(toView.transform(guard));
            t = vec(toView.transform(tip));
            a = vec(toView.transform(across));
        }
        ResourceLocation animation = null;
        double time = 0.0;
        if (player instanceof AbstractClientPlayer client) {
            PlayerAnimations.State state = PlayerAnimations.state(client);
            animation = state.animation();
            time = state.time();
        }
        double gameTime = FxClock.now(camera.getPartialTickTime());
        Deque<BladePose> poses = (off ? OFF_POSES : POSES).computeIfAbsent(player.getId(), id -> new ArrayDeque<>());
        BladePose last = poses.peekLast();
        if (last != null && last.frame() == frame && last.space() == space) {
            poses.pollLast(); // drawn twice in one frame (a mod's second pass): keep the latest
        }
        poses.addLast(new BladePose(frame, gameTime, space, animation, time, g, t, a, blade.halfWidth()));
        while (poses.size() > MAX_POSES || (poses.peekFirst() != null && gameTime - poses.peekFirst().gameTime() > KEEP_TICKS)) {
            poses.pollFirst();
        }
    }

    // ------------------------------------------------------------------ queries

    /** Every pose kept for this player, oldest first (empty if none). */
    public static List<BladePose> poses(Player player) {
        return poses(player, false);
    }

    /** Every pose kept for this player's off-hand blade, oldest first (empty if none). */
    public static List<BladePose> offPoses(Player player) {
        return poses(player, true);
    }

    /** The main blade's poses, or the off blade's. */
    public static List<BladePose> poses(Player player, boolean off) {
        Deque<BladePose> poses = (off ? OFF_POSES : POSES).get(player.getId());
        return poses == null ? List.of() : List.copyOf(poses);
    }

    /** The drawn blade's half width in texels (the edge is this far off its centre line); Meridian's if unseen. */
    public static float halfWidth(Player player) {
        BladePose pose = fresh(player);
        return pose == null ? HALF_WIDTH_TEXELS : pose.halfWidth();
    }

    /** One texel across the blade toward its upper edge: the blade's direction on the sprite turned a quarter left. */
    private static Vector3f acrossOf(Vector3f guard, Vector3f tip) {
        float ax = tip.x - guard.x;
        float ay = tip.y - guard.y;
        float length = (float) Math.sqrt(ax * ax + ay * ay);
        if (length < 1e-6f) {
            return new Vector3f(-1f / 32f, 1f / 32f, 0f).mul((float) (1.0 / Math.sqrt(2.0)));
        }
        return new Vector3f(-ay / length / 32f, ax / length / 32f, 0f);
    }

    /** The latest pose if it is from this frame or the one before, else null. */
    public static @Nullable BladePose fresh(Player player) {
        return fresh(player, false);
    }

    /** The latest pose of the main blade or the off blade if it is from this frame or the one before, else null. */
    public static @Nullable BladePose fresh(Player player, boolean off) {
        Deque<BladePose> poses = (off ? OFF_POSES : POSES).get(player.getId());
        BladePose last = poses == null ? null : poses.peekLast();
        return last != null && frame - last.frame() <= 2 ? last : null;
    }

    /**
     * A point of this pose in world coordinates now: world poses as they are, the camera's frame through
     * the camera as it is now, and the hand's points moved to where the world's field of view shows
     * them (the hand pass draws at 70 degrees whatever the setting).
     */
    public static Vec3 toWorld(BladePose pose, Vec3 point) {
        return switch (pose.space()) {
            case WORLD -> point;
            case VIEW -> viewToWorld(point);
            case HAND -> viewToWorld(handToView(point));
        };
    }

    /** A camera-frame point to world coordinates through the camera now. */
    private static Vec3 viewToWorld(Vec3 view) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f p = camera.rotation().transform(new Vector3f((float) view.x, (float) view.y, (float) view.z));
        return camera.getPosition().add(p.x, p.y, p.z);
    }

    /** tan(half the world's vertical field of view), as last drawn. */
    static double worldFovTan() {
        return worldFovTan;
    }

    /** A point the hand pass draws at 70 degrees, to where the world's field of view shows it, same depth. */
    static Vec3 handToView(Vec3 hand) {
        double k = worldFovTan / HAND_FOV_TAN;
        return new Vec3(hand.x * k, hand.y * k, hand.z);
    }

    static void clear() {
        POSES.clear();
        OFF_POSES.clear();
    }

    /** Forgets players no longer in the level. */
    static void prune(IntPredicate present) {
        for (Map<Integer, Deque<BladePose>> map : List.of(POSES, OFF_POSES)) {
            Iterator<Integer> it = map.keySet().iterator();
            while (it.hasNext()) {
                if (!present.test(it.next())) {
                    it.remove();
                }
            }
        }
    }

    private static Vector3f texel(float tx, float ty) {
        return new Vector3f((tx + 0.5f) / 32f, 1f - (ty + 0.5f) / 32f, 0.5f);
    }

    private static Vec3 vec(Vector3f v) {
        return new Vec3(v.x, v.y, v.z);
    }
}
