package com.cosmicbreach.client.anim;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractModifier;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Walking while attacking: when the player walks, the legs leave the animation and walk the way
 * vanilla's do, so the feet never slide over the ground under a planted stance. The upper body keeps
 * the whole animation (arms, blade, lean, twist); the walking legs are kept upright in the world under
 * it, turned toward the way the body moves (up to 50 degrees off the facing, as vanilla turns the body
 * of a player walking sideways), and the body stands at its normal height. The hand-over takes 3 ticks
 * both ways.
 *
 * <p>How far the legs walk follows vanilla's own walk animation speed, which every player has (the
 * local one and those the server sends), so the rule is the same for everyone. A dash, a lunge or a
 * dive moves the body too, but there the animation's own legs carry the motion: each animation plants
 * its legs for its first {@link AnimationStyle#plantedTicks} ticks. Better Combat, for comparison,
 * decides once when an attack starts, only disables the legs (they keep leaning with the body) and by
 * default never does it at all.
 */
final class WalkingLegs extends AbstractModifier {
    /** Vanilla walk animation speed (0 to 1; walking is about 0.87) where the hand-over starts and ends. */
    static final float WALK_FROM = 0.15f;
    static final float WALK_FULL = 0.45f;
    /** Hand-over per tick. */
    private static final float WEIGHT_STEP = 1f / 3f;
    /** Share of the remaining turn toward the movement per tick, and the most it turns. */
    private static final float TURN_RATE = 0.4f;
    private static final float MAX_TURN_DEG = 50f;

    private final AbstractClientPlayer player;
    private int plantedLeft;
    /** 0: the animation's legs; 1: vanilla's walking legs. */
    private float weight;
    private float weightO;
    /** Degrees the walking legs are turned toward the movement, to the player's right positive. */
    private float turn;
    private float turnO;
    private double lastX = Double.NaN;
    private double lastZ;

    WalkingLegs(AbstractClientPlayer player) {
        this.player = player;
    }

    /**
     * A new animation started: its legs stay for its first {@code ticks} ticks. With {@code fromRest}
     * (nothing was playing, so this modifier has not been following the body) the hand-over starts
     * where the body is now rather than easing in from the animation's legs.
     */
    void start(int ticks, boolean fromRest) {
        plantedLeft = Math.max(0, ticks);
        if (fromRest) {
            lastX = player.getX();
            lastZ = player.getZ();
            weight = weightO = target();
            turn = turnO = 0f;
        }
    }

    /** How much the legs walk right now, 0 to 1. */
    float weight(float partialTick) {
        return Mth.lerp(partialTick, weightO, weight);
    }

    /** The share of the legs vanilla's walk takes at a walk animation speed, before easing in over time. */
    static float walkShare(float walkSpeed) {
        float t = Mth.clamp((walkSpeed - WALK_FROM) / (WALK_FULL - WALK_FROM), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    @Override
    public void tick() {
        super.tick();
        if (plantedLeft > 0 && plantedLeft != AnimationStyle.ALWAYS) {
            plantedLeft--;
        }
        double dx = Double.isNaN(lastX) ? 0 : player.getX() - lastX;
        double dz = Double.isNaN(lastX) ? 0 : player.getZ() - lastZ;
        lastX = player.getX();
        lastZ = player.getZ();

        weightO = weight;
        turnO = turn;
        weight += Mth.clamp(target() - weight, -WEIGHT_STEP, WEIGHT_STEP);
        float turnTarget = weight > 0f && dx * dx + dz * dz > 1e-6 ? turnToward(dx, dz) : 0f;
        turn += (turnTarget - turn) * TURN_RATE;
    }

    /** How much the legs should walk now: none while planted or off the ground, else by vanilla's walk speed. */
    private float target() {
        boolean free = plantedLeft == 0 && player.onGround() && !player.isPassenger() && !player.isFallFlying();
        return free ? walkShare(player.walkAnimation.speed()) : 0f;
    }

    /** Vanilla's choice of body facing for a movement, as degrees off the body's facing, clamped. */
    private float turnToward(double dx, double dz) {
        float moveYaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        float off = Mth.abs(Mth.wrapDegrees(player.getYRot()) - moveYaw);
        if (95f < off && off < 265f) {
            moveYaw -= 180f; // walking backwards: the legs face forward and step back
        }
        return Mth.clamp(Mth.wrapDegrees(moveYaw - player.yBodyRot), -MAX_TURN_DEG, MAX_TURN_DEG);
    }

    @Override
    public @NotNull Vec3f get3DTransform(@NotNull String modelName, @NotNull TransformType type, float tickDelta, @NotNull Vec3f value0) {
        Vec3f animated = super.get3DTransform(modelName, type, tickDelta, value0);
        float w = weight(tickDelta);
        if (w <= 0f) {
            return animated;
        }
        boolean leg = modelName.equals("rightLeg") || modelName.equals("leftLeg");
        if (leg && type == TransformType.ROTATION) {
            Vec3f body = super.get3DTransform("body", TransformType.ROTATION, tickDelta, Vec3f.ZERO);
            float turnRad = Mth.lerp(tickDelta, turnO, turn) * Mth.DEG_TO_RAD;
            Quaternionf walking = LegMath.uprightLeg(joml(body), joml(value0), turnRad);
            Vector3f out = LegMath.blend(joml(animated), walking, w);
            return new Vec3f(out.x(), out.y(), out.z());
        }
        if (leg && type == TransformType.POSITION) {
            return lerp(animated, value0, w);
        }
        if (modelName.equals("body") && type == TransformType.POSITION) {
            return new Vec3f(animated.getX(), Mth.lerp(w, animated.getY(), value0.getY()), animated.getZ());
        }
        return animated;
    }

    private static Vector3f joml(Vec3f v) {
        return new Vector3f(v.getX(), v.getY(), v.getZ());
    }

    private static Vec3f lerp(Vec3f a, Vec3f b, float t) {
        return new Vec3f(Mth.lerp(t, a.getX(), b.getX()), Mth.lerp(t, a.getY(), b.getY()), Mth.lerp(t, a.getZ(), b.getZ()));
    }
}
