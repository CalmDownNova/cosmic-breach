package com.cosmicbreach.client.gear;

import com.cosmicbreach.gear.GearRegistry;
import com.cosmicbreach.gear.regalia.ChoirRegalia;
import com.cosmicbreach.gear.regalia.RegaliaRules;
import com.cosmicbreach.gear.set.ArmorSets;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The Choir Regalia's moving parts, posed from the wearer every frame:
 *
 * <ul>
 *   <li>The halo (raised above and behind the head): its three small rings orbit on it, slowly, and faster when an
 *       echo is primed (the set's meter: Harmonics has counted two casts); each ring also turns on its own.</li>
 *   <li>The rings over the pauldrons turn slowly, one each way.</li>
 *   <li>The mantle hangs in three hinged pieces that lift as the wearer moves, each lagging behind the one above it
 *       (so it curves and trails instead of swinging out as a board), with a slow sway.</li>
 *   <li>The front skirt hangs between the legs and follows whichever leg is forward (vanilla's walk cycle), its
 *       lower piece lagging, so the robe stays a robe when running.</li>
 * </ul>
 *
 * Smoothing steps once a game tick per wearer and the frame interpolates. Angles in Bedrock degrees; the rest angles
 * equal gen_regalia.py's.
 */
public final class RegaliaLook implements SetArmorModel.BoneDriver {
    static final float[] MANTLE_REST = {-89f, -1f, -1f};
    private static final String[] MANTLE = {"mantle_1", "mantle_2", "mantle_3"};
    /** Degrees a tick the rings orbit: calm, and with an echo primed. */
    private static final float ORBIT_CALM = 1.4f;
    private static final float ORBIT_PRIMED = 10f;
    private static final float RING_SPIN = 3.5f;
    private static final float SHOULDER_SPIN = 1.1f;
    private static final double RUN_SPEED = 0.28;
    /** The mantle's lift (degrees) at a walk and more at a run, for its top piece; the lower ones lag toward it. */
    private static final float MOVE_LIFT = 8f;
    private static final float RUN_LIFT = 20f;
    /** How fast each mantle piece catches up with the one above it, a share a tick. */
    private static final float[] MANTLE_FOLLOW = {0.35f, 0.18f, 0.12f};
    /** Vanilla's leg swing: xRot = cos(limbSwing x 0.6662) x 1.4 x amount (radians), negative forward. */
    private static final float LEG_RATE = 0.6662f;
    private static final float LEG_SWING = 1.4f;

    private static final class State {
        int tick = Integer.MIN_VALUE;
        float orbit;
        float orbitO;
        float speed = ORBIT_CALM;
        final float[] lift = new float[3];
        final float[] liftO = new float[3];
        float run;
        float runO;
    }

    private final Map<Entity, State> states = new WeakHashMap<>();

    /** True if {@code wearer}'s next ability cast will echo. */
    public static boolean primed(Entity wearer) {
        if (!(wearer instanceof Player player) || !player.hasData(GearRegistry.SET_STATE)) {
            return false;
        }
        return ArmorSets.state(player).meter(ChoirRegalia.SET.id(), player.level().getGameTime()) >= RegaliaRules.ECHO_EVERY - 1;
    }

    @Override
    public void pose(SetArmorModel model, Entity wearer, float partialTick) {
        State s = states.computeIfAbsent(wearer, e -> new State());
        if (s.tick != wearer.tickCount) {
            step(s, wearer, s.tick == Integer.MIN_VALUE);
            s.tick = wearer.tickCount;
        }
        float orbit = Mth.lerp(partialTick, s.orbitO, s.orbit);
        float run = Mth.lerp(partialTick, s.runO, s.run);
        double time = wearer.tickCount + partialTick;

        model.getBone("halo").ifPresent(b -> DriftweaveLook.set(b, 0f, 0f, orbit));
        for (int i = 1; i <= 3; i++) {
            float spin = (float) (-time * RING_SPIN * (i % 2 == 0 ? 1 : -1));
            model.getBone("halo_ring_" + i).ifPresent(b -> DriftweaveLook.set(b, 0f, 0f, spin));
        }
        float shoulder = (float) (time * SHOULDER_SPIN);
        model.getBone("shoulder_ring_r_spin").ifPresent(b -> DriftweaveLook.set(b, 0f, shoulder, 0f));
        model.getBone("shoulder_ring_l_spin").ifPresent(b -> DriftweaveLook.set(b, 0f, -shoulder, 0f));

        // the mantle: each piece's absolute lift, so a piece's own angle is its lift less the one above it
        float above = 0f;
        for (int i = 0; i < MANTLE.length; i++) {
            float lift = Mth.lerp(partialTick, s.liftO[i], s.lift[i]);
            float sway = (float) Math.sin(time * (0.16 + 0.22 * run) - i * 1.1) * (0.6f + 2.2f * run) * (i + 1) * 0.6f;
            float angle = MANTLE_REST[i] + (lift - above) + sway;
            above = lift;
            model.getBone(MANTLE[i]).ifPresent(b -> DriftweaveLook.set(b, angle, 0f, 0f));
        }

        // the skirt follows the forward leg (it swings forward by the same angle: its bottom forward is negative)
        float forward = forwardLeg(wearer, partialTick);
        float skirt = -forward * 1.05f;
        float lag = forward * 0.3f + (float) Math.sin(time * 0.3) * 0.8f * run;
        model.getBone("skirt_1").ifPresent(b -> DriftweaveLook.set(b, skirt, 0f, 0f));
        model.getBone("skirt_2").ifPresent(b -> DriftweaveLook.set(b, lag, 0f, 0f));
    }

    /** How far forward the forward leg swings now, degrees (vanilla's walk cycle; 0 standing). */
    static float forwardLeg(Entity wearer, float partialTick) {
        if (!(wearer instanceof LivingEntity living)) {
            return 0f;
        }
        float amount = Math.min(1f, living.walkAnimation.speed(partialTick));
        float position = living.walkAnimation.position(partialTick);
        return (float) Math.toDegrees(Math.abs(Mth.cos(position * LEG_RATE)) * LEG_SWING * amount);
    }

    private static void step(State s, Entity wearer, boolean first) {
        float target = primed(wearer) ? ORBIT_PRIMED : ORBIT_CALM;
        s.speed += (target - s.speed) * (first ? 1f : 0.12f);
        s.orbitO = s.orbit;
        s.orbit = (s.orbit + s.speed) % 360f;
        if (s.orbit < s.orbitO) {
            s.orbitO -= 360f; // wrapped: interpolate the short way
        }
        Vec3 v = SetArmorRenderer.velocity(wearer);
        double speed = Math.sqrt(v.x * v.x + v.z * v.z);
        float moving = speed > 0.012 ? 1f : 0f;
        float run = (float) Mth.clamp(speed / RUN_SPEED, 0.0, 1.0);
        s.runO = s.run;
        s.run += (run - s.run) * (first ? 1f : 0.3f);
        float top = MOVE_LIFT * moving + RUN_LIFT * run;
        float follow = top;
        for (int i = 0; i < 3; i++) {
            s.liftO[i] = s.lift[i];
            float want = i == 0 ? top : follow * 0.85f;
            s.lift[i] += (want - s.lift[i]) * (first ? 1f : MANTLE_FOLLOW[i]);
            follow = s.lift[i];
            if (first) {
                s.liftO[i] = s.lift[i];
            }
        }
        if (first) {
            s.runO = s.run;
        }
    }
}
