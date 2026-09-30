package com.cosmicbreach.client.progression;

import com.cosmicbreach.client.fx.CombatEffects;
import com.cosmicbreach.client.fx.FxBudget;
import com.cosmicbreach.client.fx.FxParticles;
import com.cosmicbreach.client.fx.WorldFx;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * A level-up: a gold ring opening at the feet and star glints streaming up round the player for half
 * a second, with the player as it moves. Seen from the player's own eyes a small starburst also opens
 * in front of the camera, where it can be seen.
 */
public final class LevelUpEffects {
    private static final RandomSource RANDOM = RandomSource.create();
    private static final int STREAM_TICKS = 10;

    private LevelUpEffects() {
    }

    public static void burst(Entity entity) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !FxParticles.ready()) {
            return;
        }
        Vec3 feet = entity.position();
        FxBudget.spawn(FxParticles.ring(level, feet.add(0, 0.06, 0)).flat().size(0.3f, 2.1f).sizeEase(2.0f).life(16)
                .color(CombatEffects.GOLD).fade(0.0f, 1.2f));
        FxBudget.spawn(FxParticles.ring(level, feet.add(0, 0.07, 0)).flat().size(0.2f, 1.3f).sizeEase(2.0f).life(11)
                .color(CombatEffects.WHITE_GOLD).fade(0.0f, 1.5f));
        WorldFx.follow(entity, STREAM_TICKS, LevelUpEffects::stream);
        if (WorldFx.firstPersonOf(entity)) {
            starburst(level);
        }
    }

    /** One tick of the rising stream: glints on a loose ring round the body, swirling as they climb. */
    private static void stream(Entity entity) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Vec3 base = entity.position();
        int count = FxBudget.count(4, base, true);
        for (int i = 0; i < count; i++) {
            double angle = RANDOM.nextDouble() * Mth.TWO_PI;
            double radius = 0.55 + RANDOM.nextDouble() * 0.3;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            Vec3 at = base.add(cos * radius, RANDOM.nextDouble() * 0.5, sin * radius);
            Vec3 velocity = new Vec3(-sin * 0.035, 0.08 + RANDOM.nextDouble() * 0.07, cos * 0.035);
            FxBudget.spawn(FxParticles.glint(level, at).velocity(velocity).drag(0.96f).size(0.2f, 0.02f)
                    .life(18 + RANDOM.nextInt(10)).color(mix(CombatEffects.PALE_GOLD, CombatEffects.GOLD, RANDOM.nextFloat()))
                    .fade(0.1f, 1.0f).spin(0.15f));
        }
    }

    private static void starburst(ClientLevel level) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f look = camera.getLookVector();
        Vec3 centre = camera.getPosition().add(look.x() * 1.6, look.y() * 1.6 + 0.25, look.z() * 1.6);
        FxBudget.spawn(FxParticles.glint(level, centre).size(0.28f, 0.04f).life(10).color(CombatEffects.WHITE_GOLD)
                .fade(0.0f, 1.3f).spin(0.12f));
        int count = FxBudget.count(10, centre, true);
        for (int i = 0; i < count; i++) {
            Vec3 out = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian() * 0.6 + 0.4, RANDOM.nextGaussian());
            Vec3 unit = out.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : out.normalize();
            FxBudget.spawn(FxParticles.glint(level, centre.add(unit.scale(0.25))).velocity(unit.scale(0.06)).drag(0.88f)
                    .size(0.09f, 0.0f).life(12 + RANDOM.nextInt(6)).color(mix(CombatEffects.WHITE_GOLD, CombatEffects.GOLD,
                            RANDOM.nextFloat())).fade(0.1f, 1.0f).spin(0.2f));
        }
    }

    private static int mix(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
        return (r << 16) | (g << 8) | bl;
    }
}
