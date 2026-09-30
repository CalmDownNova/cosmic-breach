package com.cosmicbreach.client.anim;

import java.util.Random;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The walking legs' rotations: Euler round trips, upright legs under a posed body, the turn's direction. */
class LegMathTest {
    private static final float EPS = 1e-4f;

    @Test
    void eulerAnglesRoundTripThroughTheModelPartsRotationOrder() {
        Random random = new Random(3);
        for (int i = 0; i < 200; i++) {
            Vector3f v = new Vector3f(angle(random, 1.4f), angle(random, 1.4f), angle(random, 1.4f));
            Vector3f back = LegMath.euler(LegMath.part(v));
            assertClose(v, back);
        }
    }

    @Test
    void theModelPartRotationIsZThenYThenXAsMinecraftAppliesIt() {
        // ModelPart.translateAndRotate: rotationZYX(zRot, yRot, xRot); a pitch alone turns a leg forward.
        Vector3f down = new Vector3f(0, 1, 0); // a leg hangs down: +Y in model space
        Vector3f forward = LegMath.part(new Vector3f((float) Math.toRadians(-90), 0, 0)).transform(new Vector3f(down));
        assertClose(new Vector3f(0, 0, -1), forward); // model forward is -Z
    }

    @Test
    void withNoBodyPoseAndNoTurnTheLegIsVanillas() {
        Vector3f vanilla = new Vector3f(0.6f, 0.05f, -0.02f);
        Quaternionf upright = LegMath.uprightLeg(new Vector3f(), vanilla, 0f);
        assertClose(vanilla, LegMath.euler(upright));
    }

    @Test
    void underALeaningTwistingBodyTheLegStaysUprightInTheWorld() {
        Random random = new Random(11);
        for (int i = 0; i < 100; i++) {
            Vector3f body = new Vector3f(angle(random, 0.7f), angle(random, 0.9f), angle(random, 0.4f));
            Vector3f vanilla = new Vector3f(angle(random, 0.8f), angle(random, 0.1f), angle(random, 0.1f));
            Quaternionf upright = LegMath.uprightLeg(body, vanilla, 0f);
            // In the world the leg is the body's rotation (in model space) times the leg's own.
            Quaternionf world = LegMath.bodyInModelSpace(body).mul(upright);
            Vector3f foot = world.transform(new Vector3f(0, 1, 0));
            Vector3f vanillaFoot = LegMath.part(vanilla).transform(new Vector3f(0, 1, 0));
            assertClose(vanillaFoot, foot);
        }
    }

    @Test
    void aPositiveTurnPointsTheLegsTowardThePlayersRight() {
        // A leg swung forward, turned 40 degrees: its foot moves toward the player's right, model -X.
        Vector3f swungForward = new Vector3f((float) Math.toRadians(-60), 0, 0);
        Quaternionf turned = LegMath.uprightLeg(new Vector3f(), swungForward, (float) Math.toRadians(40));
        Vector3f foot = turned.transform(new Vector3f(0, 1, 0));
        assertTrue(foot.x() < -0.3f, "foot x " + foot.x());
        assertTrue(foot.z() < -0.3f, "still forward, z " + foot.z());
    }

    @Test
    void blendRunsFromTheAnimationToTheTarget() {
        Vector3f animated = new Vector3f(0.3f, -0.2f, 0.1f);
        Quaternionf target = LegMath.part(new Vector3f(-0.5f, 0.1f, 0f));
        assertClose(animated, LegMath.blend(animated, target, 0f));
        assertClose(new Vector3f(-0.5f, 0.1f, 0f), LegMath.blend(animated, target, 1f));
        Vector3f half = LegMath.blend(animated, target, 0.5f);
        assertTrue(half.x() < 0.3f && half.x() > -0.5f, "halfway pitch " + half.x());
    }

    private static float angle(Random random, float max) {
        return (random.nextFloat() * 2f - 1f) * max;
    }

    private static void assertClose(Vector3f expected, Vector3f actual) {
        assertEquals(expected.x(), actual.x(), EPS, "x of " + actual);
        assertEquals(expected.y(), actual.y(), EPS, "y of " + actual);
        assertEquals(expected.z(), actual.z(), EPS, "z of " + actual);
    }
}
