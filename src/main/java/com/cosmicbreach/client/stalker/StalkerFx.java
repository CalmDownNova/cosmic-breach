package com.cosmicbreach.client.stalker;

import com.cosmicbreach.client.fx.FxBudget;
import com.cosmicbreach.client.fx.FxParticles;
import com.cosmicbreach.entity.stalker.HollowStalker;
import com.cosmicbreach.entity.stalker.StalkerEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The Hollow Stalker's effects (GDD 7.1): instead of smoke, dark flakes that fall upward off its body; a burst of them
 * where a Shadow Step leaves and where it arrives; the Rend's gold glint at its claw (the parry cue, GDD 4.1) and its
 * magenta swipe; flakes round a held player; at its death a rush of flakes and the mask breaking into pale shards.
 * Particle counts go through {@link FxBudget}. Client thread.
 */
final class StalkerFx implements StalkerEffects.Handler {
    static final int FLAKE = 0x0C0714;
    static final int FLAKE_EDGE = 0x1E1030;
    static final int MAGENTA = 0xFF3DD8;
    static final int GOLD = 0xFFC23A;
    static final int PORCELAIN = 0xEDE6DA;

    private final RandomSource random = RandomSource.create();

    private static ClientLevel level() {
        return Minecraft.getInstance().level;
    }

    private static boolean ready() {
        return level() != null && FxParticles.ready();
    }

    @Override
    public void tick(HollowStalker s) {
        if (!ready() || !s.isAlive()) {
            return;
        }
        if (random.nextInt(3) == 0) {
            flakes(s.position(), 1, 0.02, false);
        }
        Player held = s.heldPlayer();
        if (held != null && random.nextInt(2) == 0) {
            Vec3 at = held.position().add(random.nextGaussian() * 0.3, 0.4 + random.nextDouble() * 1.2, random.nextGaussian() * 0.3);
            flake(at, new Vec3(0, 0.03, 0), 0.07f, 26);
        }
    }

    @Override
    public void event(HollowStalker s, byte id) {
        if (!ready()) {
            return;
        }
        switch (id) {
            case HollowStalker.EVENT_STEP_FROM -> flakes(s.position(), 22, 0.07, true);
            case HollowStalker.EVENT_STEP_TO -> {
                flakes(s.position(), 14, 0.05, true);
                sparks(s.position().add(0, 2.3, 0), 4, MAGENTA, 0.04);
            }
            case HollowStalker.EVENT_GLINT -> glint(claw(s));
            case HollowStalker.EVENT_REND -> swipe(s);
            case HollowStalker.EVENT_GRASP -> {
                Player held = s.heldPlayer();
                if (held != null) {
                    flakes(held.position(), 16, 0.05, true);
                }
            }
            case EntityEvent.DEATH -> death(s);
            default -> {
            }
        }
    }

    /** The claw that strikes: out to its right, at the chest, a little ahead. */
    private static Vec3 claw(HollowStalker s) {
        float yaw = s.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        return s.position().add(0, 2.0, 0).add(forward.scale(0.3)).add(right.scale(0.75));
    }

    private void flakes(Vec3 feet, int wanted, double speed, boolean important) {
        int n = FxBudget.count(wanted, feet, important);
        for (int i = 0; i < n; i++) {
            Vec3 at = feet.add(random.nextGaussian() * 0.22, 0.2 + random.nextDouble() * 2.2, random.nextGaussian() * 0.22);
            Vec3 v = new Vec3(random.nextGaussian() * speed * 0.5, speed * (0.5 + random.nextDouble()), random.nextGaussian() * speed * 0.5);
            flake(at, v, 0.05f + random.nextFloat() * 0.05f, 24 + random.nextInt(18));
        }
    }

    /** One dark flake drifting up and fading, turning as it goes. */
    private void flake(Vec3 at, Vec3 v, float size, int life) {
        FxBudget.spawn(FxParticles.shard(level(), at).velocity(v).color(random.nextBoolean() ? FLAKE : FLAKE_EDGE)
                .size(size, size * 0.4f).life(life).gravity(-0.02f).drag(0.94f).spin(0.12f).dark(0.9f));
    }

    private void sparks(Vec3 at, int wanted, int color, double speed) {
        int n = FxBudget.count(wanted, at, false);
        for (int i = 0; i < n; i++) {
            Vec3 v = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize().scale(speed);
            FxBudget.spawn(FxParticles.spark(level(), at).velocity(v).color(color).size(0.035f, 0.01f).life(8).drag(0.85f));
        }
    }

    /** The parry cue: a gold star glint at the claw, round (GDD 4.1's gold). */
    private void glint(Vec3 at) {
        FxBudget.spawn(FxParticles.glint(level(), at).color(GOLD).size(0.55f, 0.1f).life(9).spin(0.25f));
        FxBudget.spawn(FxParticles.glint(level(), at).color(0xFFFFFF).size(0.25f, 0.05f).life(6));
        sparks(at, 5, GOLD, 0.06);
    }

    /** The Rend: dark streaks across its front and magenta sparks. */
    private void swipe(HollowStalker s) {
        float yaw = s.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        Vec3 mid = s.position().add(0, 1.3, 0).add(forward.scale(1.2));
        for (int i = 0; i < 3; i++) {
            Vec3 at = mid.add(right.scale(0.25 * (i - 1))).add(0, 0.2 * (1 - i), 0);
            FxBudget.spawn(FxParticles.spark(level(), at).velocity(right.scale(-0.35).add(0, -0.25, 0)).color(FLAKE_EDGE)
                    .size(0.09f, 0.03f).life(6).dark(0.8f));
        }
        sparks(mid, 8, MAGENTA, 0.08);
    }

    private void death(HollowStalker s) {
        flakes(s.position(), 40, 0.09, true);
        Vec3 mask = s.position().add(0, 2.3, 0);
        int n = FxBudget.count(9, mask, true);
        for (int i = 0; i < n; i++) {
            Vec3 v = new Vec3(random.nextGaussian() * 0.07, 0.08 + random.nextDouble() * 0.06, random.nextGaussian() * 0.07);
            FxBudget.spawn(FxParticles.shard(level(), mask).velocity(v).color(PORCELAIN).size(0.1f, 0.06f).life(26)
                    .gravity(1.0f).spin(0.4f).physics());
        }
        sparks(mask, 10, MAGENTA, 0.09);
    }
}
