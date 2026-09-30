package com.cosmicbreach.client.gear;

import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.gear.GearRegistry;
import com.cosmicbreach.gear.driftweave.Driftweave;
import com.cosmicbreach.gear.set.ArmorSets;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * The Driftweave's moving parts, posed from the wearer every frame:
 *
 * <ul>
 *   <li>The scarf's two tails and the half-cape hang down the back at rest (clear of it) and stream back as the
 *       wearer moves: lifted at least a little by any movement (so the legs swing clear), more with speed, up and
 *       back when falling fast, with a ripple running down them that grows with speed, and swung aside by sideways
 *       motion. The long tail ripples out of step with the short one, gently, so the pair reads as a scarf.</li>
 *   <li>The ankle fins flare out on every dash (the combat effects' dash listener) and fold back over 12 ticks.</li>
 *   <li>Three bands round the long tail show the dash charges ready, lit one per charge.</li>
 * </ul>
 *
 * Smoothing steps once a game tick per wearer (the armor's four pieces each pose the model every frame) and the
 * frame interpolates. Angles in Bedrock degrees; GeckoLib's bones hold X and Y negated, in radians. The rest
 * angles equal gen_driftweave.py's.
 */
public final class DriftweaveLook implements SetArmorModel.BoneDriver {
    static final float[] SCARF_A_REST = {-86f, 1.5f, 1.5f, 1f};
    static final float[] SCARF_B_REST = {-84f, 1.5f, 1f};
    static final float[] CAPE_REST = {-87f, 1.5f, 1.5f};
    static final float FIN_REST = 8f;
    private static final String[] SCARF_A = {"scarf_a1", "scarf_a2", "scarf_a3", "scarf_a4"};
    private static final String[] SCARF_B = {"scarf_b1", "scarf_b2", "scarf_b3"};
    private static final String[] CAPE = {"cape_1", "cape_2", "cape_3"};
    /** The bands round the long tail, one per dash charge ready: lit, and dark when that charge isn't back yet. */
    private static final int BANDS = 3;
    private static final double RUN_SPEED = 0.28;
    private static final double FALL_SPEED = 0.8;
    private static final float FLARE_TICKS = 12f;
    /** Lift of the first segment (degrees): any movement, then more up to a run, then a fall. */
    private static final float MOVE_LIFT = 20f;
    private static final float RUN_LIFT = 38f;
    private static final float FALL_LIFT = 96f;

    private static final class State {
        int tick = Integer.MIN_VALUE;
        float moving;
        float movingO;
        float run;
        float runO;
        float fall;
        float fallO;
        float side;
        float sideO;
    }

    private final Map<Entity, State> states = new WeakHashMap<>();
    private static final Map<Entity, Float> DASHED = new WeakHashMap<>();

    /** A dash was drawn for {@code entity}: its fins flare (from the combat effects' dash listener). */
    public static void dashed(Entity entity) {
        DASHED.put(entity, (float) entity.tickCount);
    }

    @Override
    public void pose(SetArmorModel model, Entity wearer, float partialTick) {
        State s = states.computeIfAbsent(wearer, e -> new State());
        if (s.tick != wearer.tickCount) {
            step(s, wearer, s.tick == Integer.MIN_VALUE);
            s.tick = wearer.tickCount;
        }
        float moving = Mth.lerp(partialTick, s.movingO, s.moving);
        float run = Mth.lerp(partialTick, s.runO, s.run);
        float fall = Mth.lerp(partialTick, s.fallO, s.fall);
        float side = Mth.lerp(partialTick, s.sideO, s.side);
        double time = wearer.tickCount + partialTick;

        float lift = MOVE_LIFT * moving + RUN_LIFT * run + FALL_LIFT * fall;
        float amplitude = 1.5f + 7f * run + 3f * fall;
        double speed = 0.22 + 0.45 * run + 0.2 * fall;
        ribbon(model, SCARF_A, SCARF_A_REST, lift, amplitude, speed, time, 0.0, side * -22f);
        ribbon(model, SCARF_B, SCARF_B_REST, lift * 0.92f, amplitude * 0.9f, speed * 1.1, time, 1.7, side * -18f);
        ribbon(model, CAPE, CAPE_REST, lift * 0.95f, amplitude * 0.85f, speed * 0.9, time, 0.6, side * -12f);

        int ready = chargesReady(wearer);
        for (int k = 1; k <= BANDS; k++) {
            boolean lit = ready >= k;
            model.getBone("charge_" + k).ifPresent(b -> b.setHidden(!lit));
            model.getBone("charge_" + k + "_off").ifPresent(b -> b.setHidden(lit));
        }

        Float dashedAt = DASHED.get(wearer);
        float flare = dashedAt == null ? 0f : (float) Math.sqrt(Mth.clamp(1.0 - (time - dashedAt) / FLARE_TICKS, 0.0, 1.0));
        float out = FIN_REST + 42f * flare;
        float tilt = 14f * flare;
        model.getBone("fin_r").ifPresent(b -> set(b, 0f, -out, -tilt));
        model.getBone("fin_l").ifPresent(b -> set(b, 0f, out, tilt));
    }

    /** One hanging ribbon: its first segment lifted, a ripple travelling down it, swung aside by {@code yaw}. */
    private static void ribbon(SetArmorModel model, String[] bones, float[] rest, float lift, float amplitude, double speed,
                               double time, double phase, float yaw) {
        for (int i = 0; i < bones.length; i++) {
            float ripple = (float) Math.sin(time * speed - i * 0.95 + phase) * amplitude * (0.6f + 0.4f * i);
            float angle;
            float turn = 0f;
            if (i == 0) {
                angle = Math.min(30f, rest[0] + lift + ripple * 0.4f);
                turn = yaw;
            } else {
                angle = rest[i] + ripple - lift * 0.05f * i;
            }
            float a = angle;
            float y = turn;
            model.getBone(bones[i]).ifPresent(b -> set(b, a, y, 0f));
        }
    }

    private static void step(State s, Entity wearer, boolean first) {
        Vec3 v = SetArmorRenderer.velocity(wearer);
        double speed = Math.sqrt(v.x * v.x + v.z * v.z);
        float yaw = wearer instanceof LivingEntity living ? living.yBodyRot : wearer.getYRot();
        double r = Math.toRadians(yaw);
        double acrossX = Math.cos(r);
        double acrossZ = Math.sin(r);
        double across = v.x * acrossX + v.z * acrossZ;
        float moving = speed > 0.012 ? 1f : 0f;
        float run = (float) Mth.clamp(speed / RUN_SPEED, 0.0, 1.0);
        float fall = (float) Mth.clamp(-v.y / FALL_SPEED, 0.0, 1.0);
        float side = (float) Mth.clamp(across / 0.2, -1.0, 1.0);
        s.movingO = s.moving;
        s.runO = s.run;
        s.fallO = s.fall;
        s.sideO = s.side;
        float k = first ? 1f : 0.3f;
        s.moving += (moving - s.moving) * (moving > s.moving ? 0.5f : k * 0.5f);
        s.run += (run - s.run) * k;
        s.fall += (fall - s.fall) * k;
        s.side += (side - s.side) * k;
        if (first) {
            s.movingO = s.moving;
            s.runO = s.run;
            s.fallO = s.fall;
            s.sideO = s.side;
        }
    }

    /**
     * The dash charges ready on {@code wearer}: the local player's own machine (at once), anyone else's from the set's
     * meter the server syncs; all of them on anything that has neither.
     */
    static int chargesReady(Entity wearer) {
        if (!(wearer instanceof Player player)) {
            return BANDS;
        }
        if (player == Minecraft.getInstance().player) {
            PlayerCombat combat = PlayerCombat.existing(player);
            if (combat != null) {
                return combat.machine().dashCharges();
            }
        }
        if (player.hasData(GearRegistry.SET_STATE)) {
            return (int) ArmorSets.state(player).meter(Driftweave.SET.id(), player.level().getGameTime());
        }
        return BANDS;
    }

    /** Bedrock degrees to GeckoLib's bone rotation (X and Y negated, radians). */
    static void set(GeoBone bone, float x, float y, float z) {
        bone.setRotX((float) Math.toRadians(-x));
        bone.setRotY((float) Math.toRadians(-y));
        bone.setRotZ((float) Math.toRadians(z));
    }
}
