package com.cosmicbreach.client.anim;

import java.util.Random;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The first-person framing: the body channel values it hands back must draw the model exactly where
 * the intended transform puts it, T(eye) Ry(-yaw) Rx(-tilt) T(-eye) T(0, -down, -forward) times the
 * animation's own body transform T(p + hip) Rz Ry Rx T(-hip) (playerAnimator's order).
 */
class FirstPersonViewTest {
    private static final float HIP = 0.7f;
    private static final float EYE = 1.62f;

    @Test
    void theRecomposedBodyDrawsEveryPointWhereTheFramingPutsIt() {
        Random random = new Random(5);
        for (int i = 0; i < 200; i++) {
            Vector3f p = new Vector3f(r(random, 0.3f), r(random, 0.3f), r(random, 0.3f));
            Vector3f rot = new Vector3f(r(random, 0.8f), r(random, 1.2f), r(random, 0.5f));
            float forward = random.nextFloat() * 0.5f;
            float down = r(random, 0.3f);
            float tilt = r(random, 1.2f);
            float yaw = r(random, 0.6f);
            Matrix4f expected = new Matrix4f().translate(0, EYE, 0).rotateY(-yaw).rotateX(-tilt).translate(0, -EYE, 0)
                    .translate(0, -down, -forward).mul(body(p, rot));
            Vector3f[] out = FirstPersonView.recompose(p, rot, EYE, forward, down, tilt, yaw);
            Matrix4f actual = body(out[0], out[1]);
            for (int k = 0; k < 5; k++) {
                Vector3f point = new Vector3f(r(random, 1f), random.nextFloat() * 2f, r(random, 1f));
                Vector3f a = expected.transformPosition(new Vector3f(point));
                Vector3f b = actual.transformPosition(new Vector3f(point));
                assertEquals(0f, a.distance(b), 1e-4f, "case " + i);
            }
        }
    }

    @Test
    void theModelMovedToTheRightIsStillExact() {
        Random random = new Random(9);
        for (int i = 0; i < 100; i++) {
            Vector3f p = new Vector3f(r(random, 0.3f), r(random, 0.3f), r(random, 0.3f));
            Vector3f rot = new Vector3f(r(random, 0.8f), r(random, 1.2f), r(random, 0.5f));
            float forward = random.nextFloat() * 0.5f;
            float down = r(random, 0.3f);
            float right = r(random, 0.4f);
            float tilt = r(random, 1.2f);
            float yaw = r(random, 0.6f);
            Matrix4f expected = new Matrix4f().translate(0, EYE, 0).rotateY(-yaw).rotateX(-tilt).translate(0, -EYE, 0)
                    .translate(right, -down, -forward).mul(body(p, rot));
            Vector3f[] out = FirstPersonView.recompose(p, rot, EYE, forward, down, right, tilt, yaw);
            Matrix4f actual = body(out[0], out[1]);
            Vector3f point = new Vector3f(r(random, 1f), random.nextFloat() * 2f, r(random, 1f));
            assertEquals(0f, expected.transformPosition(new Vector3f(point)).distance(actual.transformPosition(new Vector3f(point))),
                    1e-4f, "case " + i);
        }
    }

    @Test
    void withNothingToDoTheBodyIsUnchanged() {
        Vector3f p = new Vector3f(0.1f, -0.05f, 0.02f);
        Vector3f rot = new Vector3f(-0.2f, 0.3f, 0.05f);
        Vector3f[] out = FirstPersonView.recompose(p, rot, EYE, 0f, 0f, 0f, 0f);
        assertEquals(0f, out[0].distance(p), 1e-5f);
        assertEquals(0f, out[1].distance(rot), 1e-5f);
    }

    /** playerAnimator's body transform: translate(p + hip), rotZ, rotY, rotX, translate(-hip). */
    private static Matrix4f body(Vector3f p, Vector3f rot) {
        return new Matrix4f().translate(p.x(), p.y() + HIP, p.z()).rotateZ(rot.z()).rotateY(rot.y()).rotateX(rot.x())
                .translate(0, -HIP, 0);
    }

    private static float r(Random random, float max) {
        return (random.nextFloat() * 2f - 1f) * max;
    }
}
