package com.cosmicbreach.client.anim;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Rotation maths for {@link WalkingLegs}, in the units playerAnimator hands around: a part's rotation
 * is (x, y, z) in radians, applied as {@code rotationZYX(z, y, x)} in model space (+Y down, forward
 * -Z, +X the player's left); the {@code body} part's rotation is (pitch, yaw, roll) in the local frame
 * (+Y up, forward -Z, +X the player's right), where model space is the local frame turned half a turn
 * about Z. Pure: unit-tested.
 */
final class LegMath {
    private LegMath() {
    }

    /** A part's model-space rotation as a quaternion. */
    static Quaternionf part(Vector3f xyz) {
        return new Quaternionf().rotationZYX(xyz.z(), xyz.y(), xyz.x());
    }

    /**
     * A quaternion back to a part's (x, y, z) rotation, read off its matrix R = Rz Ry Rx:
     * y = asin(-R20), x = atan2(R21, R22), z = atan2(R10, R00). (JOML's getEulerAnglesZYX is off by
     * up to 0.03 radians here.)
     */
    static Vector3f euler(Quaternionf q) {
        Vector3f c0 = q.transform(new Vector3f(1, 0, 0));
        Vector3f c1 = q.transform(new Vector3f(0, 1, 0));
        Vector3f c2 = q.transform(new Vector3f(0, 0, 1));
        float y = (float) Math.asin(Math.max(-1.0, Math.min(1.0, -c0.z())));
        float x = (float) Math.atan2(c1.z(), c2.z());
        float z = (float) Math.atan2(c0.y(), c0.x());
        return new Vector3f(x, y, z);
    }

    /** The body part's rotation as seen in model space (the local frame's half turn about Z undone). */
    static Quaternionf bodyInModelSpace(Vector3f bodyPitchYawRoll) {
        return new Quaternionf().rotationZYX(bodyPitchYawRoll.z(), -bodyPitchYawRoll.y(), -bodyPitchYawRoll.x());
    }

    /**
     * The leg rotation that shows {@code vanillaLeg} upright in the world under the animated body
     * rotation, turned {@code turnRad} about the vertical toward the player's right: vanilla's walking
     * legs under a leaning, twisting upper body.
     */
    static Quaternionf uprightLeg(Vector3f bodyPitchYawRoll, Vector3f vanillaLeg, float turnRad) {
        return bodyInModelSpace(bodyPitchYawRoll).conjugate()
                .mul(new Quaternionf().rotationY(turnRad))
                .mul(part(vanillaLeg));
    }

    /** {@code animated} turned {@code weight} of the way to {@code target} (0 keeps it, 1 is the target). */
    static Vector3f blend(Vector3f animated, Quaternionf target, float weight) {
        if (weight <= 0f) {
            return animated;
        }
        Quaternionf from = part(animated);
        return euler(weight >= 1f ? target : from.slerp(target, weight));
    }
}
