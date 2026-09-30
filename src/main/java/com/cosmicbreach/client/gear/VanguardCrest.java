package com.cosmicbreach.client.gear;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * The Starfall Vanguard's comet-tail crest: three segments behind the helm that hang at rest, stream straight
 * back as the wearer runs, and whip upward when they fall fast (a comet's tail points away from its motion).
 * A little flutter rides on top, faster with speed. Angles in Bedrock degrees about X, where positive lifts a
 * segment that points backward (GeckoLib's bones hold them negated, in radians).
 */
public final class VanguardCrest implements SetArmorModel.BoneDriver {
    /**
     * How far each segment droops at rest (Bedrock degrees, down): a gentle sweep back, not a curl (58 degrees in all
     * read as a hook from the side; F1 polish). Running and falling poses are unchanged: rest minus lift is the same.
     */
    private static final float[] REST = {18f, 9f, 6f};
    /** How far each segment lifts at full running speed. */
    private static final float[] RUN_LIFT = {14f, 7f, 5f};
    /** How far more each lifts when falling fast. */
    private static final float[] FALL_LIFT = {48f, 11f, 4f};
    private static final double RUN_SPEED = 0.28;
    private static final double FALL_SPEED = 0.9;
    private static final String[] BONES = {"crest_tail_1", "crest_tail_2", "crest_tail_3"};

    /** Smoothed (run, fall) per wearer, so the tail swings instead of snapping. */
    private final Map<Entity, float[]> smooth = new WeakHashMap<>();

    @Override
    public void pose(SetArmorModel model, Entity wearer, float partialTick) {
        Vec3 v = SetArmorRenderer.velocity(wearer);
        float run = (float) Mth.clamp(Math.sqrt(v.x * v.x + v.z * v.z) / RUN_SPEED, 0.0, 1.0);
        float fall = (float) Mth.clamp(-v.y / FALL_SPEED, 0.0, 1.0);
        float[] s = smooth.computeIfAbsent(wearer, e -> new float[] {run, fall});
        s[0] += (run - s[0]) * 0.25f;
        s[1] += (fall - s[1]) * 0.25f;
        double time = wearer.tickCount + partialTick;
        for (int i = 0; i < BONES.length; i++) {
            int segment = i;
            float flutter = (float) Math.sin(time * (0.35 + 0.5 * s[0]) - segment * 0.9) * (1.5f + 5f * s[0]) * (segment + 1) / 2f;
            float angle = -REST[segment] + RUN_LIFT[segment] * s[0] + FALL_LIFT[segment] * s[1] + flutter;
            model.getBone(BONES[segment]).ifPresent(bone -> set(bone, angle));
        }
    }

    private static void set(GeoBone bone, float bedrockDegrees) {
        bone.setRotX((float) Math.toRadians(-bedrockDegrees));
    }
}
