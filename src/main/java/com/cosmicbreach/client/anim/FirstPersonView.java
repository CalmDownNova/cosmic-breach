package com.cosmicbreach.client.anim;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractModifier;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * How the animated body sits in front of the camera in first person (only in the first-person pass of
 * the player the camera looks out of; everyone else, and every other view, sees the animation as it
 * is). Seen from inside the head the arms are huge and a high guard's fists sit right in front of the
 * eyes, so the model moves a little ahead of and below the eye, and it keeps one framing: it turns about
 * the eye with the camera's pitch, so a swing crosses the view the way it would looking
 * {@link AnimationStyle#firstPersonPitch} degrees down, wherever the player looks. An overhead chop
 * frames steeper, so its raised blade waits above the view and comes down through it. Changes of
 * framing between animations ease over about 3 ticks. The body's yaw only changes once a tick while
 * the camera turns every frame, so the model also takes the camera's yaw here, and the blade stays put
 * in the view while the mouse moves. Exact: the body transform is recomposed, not added to channel by
 * channel.
 */
final class FirstPersonView extends AbstractModifier {
    /** The body's pivot above the feet, blocks (playerAnimator's body transform turns about it). */
    private static final float HIP = 0.7f;
    /** Share of the way to a new framing per tick. */
    private static final float EASE = 0.5f;

    /** Dev only: overrides for trying values (NaN: the style's). */
    static float devPush = Float.NaN;
    static float devDrop = Float.NaN;
    static float devPitch = Float.NaN;

    private final AbstractClientPlayer player;
    private AnimationStyle style;
    private float push;
    private float pushO;
    private float drop;
    private float dropO;
    private float side;
    private float sideO;
    /** How much of the framing applies: 0 shows the model where it is, 1 holds the reference pitch's framing. */
    private float framing;
    private float framingO;
    private float pitch;
    private float pitchO;

    FirstPersonView(AbstractClientPlayer player) {
        this.player = player;
    }

    /** A new animation's style; {@code snap} jumps straight to its framing (nothing was showing before). */
    void style(AnimationStyle next, boolean snap) {
        this.style = next;
        if (snap) {
            push = pushO = targetPush();
            drop = dropO = targetDrop();
            side = sideO = style.firstPersonSide();
            framing = framingO = Float.isNaN(targetPitch()) ? 0f : 1f;
            pitch = pitchO = Float.isNaN(targetPitch()) ? pitch : targetPitch();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (style == null) {
            return;
        }
        pushO = push;
        dropO = drop;
        sideO = side;
        framingO = framing;
        pitchO = pitch;
        push += (targetPush() - push) * EASE;
        drop += (targetDrop() - drop) * EASE;
        side += (style.firstPersonSide() - side) * EASE;
        float reference = targetPitch();
        framing += ((Float.isNaN(reference) ? 0f : 1f) - framing) * EASE;
        if (!Float.isNaN(reference)) {
            pitch += (reference - pitch) * EASE;
        }
    }

    private float targetPush() {
        return Float.isNaN(devPush) ? style.firstPersonPush() : devPush;
    }

    private float targetDrop() {
        return Float.isNaN(devDrop) ? style.firstPersonDrop() : devDrop;
    }

    private float targetPitch() {
        return Float.isNaN(devPitch) ? style.firstPersonPitch() : devPitch;
    }

    @Override
    public @NotNull Vec3f get3DTransform(@NotNull String modelName, @NotNull TransformType type, float tickDelta, @NotNull Vec3f value0) {
        if (style == null || !modelName.equals("body") || (type != TransformType.POSITION && type != TransformType.ROTATION)
                || !FirstPersonMode.isFirstPersonPass() || Minecraft.getInstance().getCameraEntity() != player) {
            return super.get3DTransform(modelName, type, tickDelta, value0);
        }
        float forward = Mth.lerp(tickDelta, pushO, push);
        float down = Mth.lerp(tickDelta, dropO, drop);
        float right = Mth.lerp(tickDelta, sideO, side);
        float weight = Mth.lerp(tickDelta, framingO, framing);
        float reference = Mth.lerp(tickDelta, pitchO, pitch);
        float tilt = weight * (player.getViewXRot(tickDelta) - reference) * Mth.DEG_TO_RAD;
        // The body turns once a tick, the camera every frame: turn the model the rest of the way.
        float bodyYaw = Mth.rotLerp(tickDelta, player.yBodyRotO, player.yBodyRot);
        float yaw = Mth.wrapDegrees(player.getViewYRot(tickDelta) - bodyYaw) * Mth.DEG_TO_RAD;
        if (forward == 0f && down == 0f && right == 0f && tilt == 0f && yaw == 0f) {
            return super.get3DTransform(modelName, type, tickDelta, value0);
        }
        Vec3f position = super.get3DTransform("body", TransformType.POSITION, tickDelta, type == TransformType.POSITION ? value0 : Vec3f.ZERO);
        Vec3f rotation = super.get3DTransform("body", TransformType.ROTATION, tickDelta, type == TransformType.ROTATION ? value0 : Vec3f.ZERO);
        Vector3f[] out = recompose(new Vector3f(position.getX(), position.getY(), position.getZ()),
                new Vector3f(rotation.getX(), rotation.getY(), rotation.getZ()), (float) player.getEyeHeight(), forward, down, right,
                tilt, yaw);
        Vector3f result = type == TransformType.POSITION ? out[0] : out[1];
        return new Vec3f(result.x(), result.y(), result.z());
    }

    /**
     * The body transform T(p + hip) R T(-hip) moved {@code forward} and {@code down} blocks, turned
     * {@code tilt} radians about the eye the way the camera pitches (positive: as if looking further
     * down), then {@code yaw} radians about the vertical through the eye toward the player's right (the
     * camera's yaw off the body's: vanilla turns the model by 180 - body yaw, the camera looks along
     * the view yaw). Returns {position, rotation} in the body channel's units (blocks; pitch, yaw, roll
     * radians).
     */
    static Vector3f[] recompose(Vector3f position, Vector3f rotation, float eyeHeight, float forward, float down, float tilt,
                                float yaw) {
        return recompose(position, rotation, eyeHeight, forward, down, 0f, tilt, yaw);
    }

    /** {@link #recompose(Vector3f, Vector3f, float, float, float, float, float)}, also moved {@code right} blocks to the right. */
    static Vector3f[] recompose(Vector3f position, Vector3f rotation, float eyeHeight, float forward, float down, float right,
                                float tilt, float yaw) {
        Vector3f eye = new Vector3f(0, eyeHeight, 0);
        Quaternionf turn = new Quaternionf().rotationY(-yaw).rotateX(-tilt);
        Vector3f pivot = new Vector3f(position).add(0, HIP, 0).add(right, -down, -forward).sub(eye);
        turn.transform(pivot);
        pivot.add(eye).sub(0, HIP, 0);
        Vector3f euler = LegMath.euler(new Quaternionf(turn).mul(LegMath.part(rotation)));
        return new Vector3f[] {pivot, euler};
    }
}
