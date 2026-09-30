package com.cosmicbreach.client.weather;

import com.cosmicbreach.client.fx.FxBudget;
import com.cosmicbreach.client.fx.FxParticle;
import com.cosmicbreach.client.fx.FxParticles;
import com.cosmicbreach.world.Layer;
import com.cosmicbreach.world.weather.CosmicWeather;
import com.cosmicbreach.world.weather.DriftCurrents;
import com.cosmicbreach.world.weather.LayerWeather;
import com.cosmicbreach.world.weather.WeatherKind;
import com.cosmicbreach.world.weather.WeatherSchedule.Phase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The Drift's dust, as streaks: each current near the camera is a stream of slate dust and pale glints running
 * along its tube, so it reads from a distance and shows which way it carries. A Gravity Tide's warning swings every
 * stream round to the Tide's heading over its 10 s, and fills the air around the player with dust that
 * turns from drifting every which way to flowing as one; in the Tide itself it all streams along the heading,
 * faster.
 */
final class DustStreams {
    private static final RandomSource RANDOM = RandomSource.create();
    /** Streams are drawn for currents within this many blocks of the camera. */
    private static final double RANGE = 72.0;
    private static int spawned;

    private DustStreams() {
    }

    /** Motes spawned in the last tick (for checks). */
    static int spawned() {
        return spawned;
    }

    static void tick(Minecraft mc) {
        spawned = 0;
        ClientLevel level = mc.level;
        if (level == null || !FxParticles.ready() || !CosmicWeather.synced(level)) {
            return;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        if (cam.y < DriftCurrents.MIN_Y - 60 || cam.y > DriftCurrents.MAX_Y + 60) {
            return;
        }
        long time = level.getGameTime();
        LayerWeather drift = CosmicWeather.client(Layer.DRIFT);
        double swing = 0.0;
        if (drift.is(WeatherKind.TIDE, Phase.WARNING)) {
            double p = drift.progress(time);
            swing = p * p * (3 - 2 * p);
        } else if (drift.is(WeatherKind.TIDE, Phase.ACTIVE)) {
            swing = 1.0;
        }
        double tide = drift.heading();
        long salt = CosmicWeather.currentSalt(level);
        for (DriftCurrents.Zone zone : DriftCurrents.near(salt, cam.x, cam.z, RANGE)) {
            stream(level, cam, zone, swing > 0 ? turn(zone.heading(), tide, swing) : zone.heading(), swing);
        }
        if (swing > 0 && Layer.at(cam.y) == Layer.DRIFT) {
            tideDust(level, cam, tide, swing, drift.phase() == Phase.ACTIVE);
        }
    }

    private static void stream(ClientLevel level, Vec3 cam, DriftCurrents.Zone zone, double heading, double swing) {
        double dx = Math.cos(zone.heading());
        double dz = Math.sin(zone.heading());
        // along the tube, near the camera's closest point on it
        double centreT = zone.along(cam.x, cam.z);
        Vec3 near = new Vec3(zone.x() + dx * centreT, zone.y(), zone.z() + dz * centreT);
        // denser, larger and darker than the first pass: the review found the streams faint against the Drift's pale fog
        int wanted = 16 + 3 * (int) Math.round(zone.radius());
        int n = FxBudget.count(wanted, near, false);
        Vec3 flow = new Vec3(Math.cos(heading), 0, Math.sin(heading));
        double speed = 0.3 + 0.15 * swing;
        for (int i = 0; i < n; i++) {
            double t = Math.max(-zone.halfLength(), Math.min(zone.halfLength(), centreT + (RANDOM.nextDouble() - 0.5) * 96.0));
            double a = RANDOM.nextDouble() * Math.PI * 2.0;
            double r = Math.sqrt(RANDOM.nextDouble()) * zone.radius() * 0.9;
            // offsets across the tube: sideways on the ground plane and up
            double side = Math.cos(a) * r;
            double up = Math.sin(a) * r;
            Vec3 at = new Vec3(zone.x() + dx * t - dz * side, zone.y() + up, zone.z() + dz * t + dx * side);
            Vec3 v = flow.scale(speed * (0.8 + RANDOM.nextDouble() * 0.4));
            if (FxBudget.spawn(mote(level, at, v, 0.14f, 7.0f, 30 + RANDOM.nextInt(20)))) {
                spawned++;
            }
        }
    }

    private static void tideDust(ClientLevel level, Vec3 cam, double tide, double swing, boolean active) {
        int n = FxBudget.count(active ? 18 : 12, cam, false);
        Vec3 flow = new Vec3(Math.cos(tide), 0, Math.sin(tide));
        for (int i = 0; i < n; i++) {
            Vec3 at = cam.add((RANDOM.nextDouble() - 0.5) * 36.0, (RANDOM.nextDouble() - 0.4) * 16.0, (RANDOM.nextDouble() - 0.5) * 36.0);
            Vec3 wander = new Vec3(RANDOM.nextDouble() - 0.5, (RANDOM.nextDouble() - 0.5) * 0.3, RANDOM.nextDouble() - 0.5).normalize();
            Vec3 dir = wander.scale(1.0 - swing).add(flow.scale(swing)).normalize();
            double speed = active ? 0.45 : 0.18 + 0.2 * swing;
            if (FxBudget.spawn(mote(level, at, dir.scale(speed), 0.1f, 5.0f, 24 + RANDOM.nextInt(16)))) {
                spawned++;
            }
        }
    }

    /**
     * One mote: mostly slate dust blended over the scene (dark streaks read against the Drift's bright sky,
     * where added light vanishes), one in five a pale glint added on top (for the asteroids' shadows).
     */
    private static FxParticle mote(ClientLevel level, Vec3 at, Vec3 velocity, float size, float perSpeed, int life) {
        FxParticle p = FxParticles.spark(level, at).velocity(velocity).drag(1.0f).streak(perSpeed, 0.4f).life(life);
        if (RANDOM.nextInt(5) == 0) {
            return p.color(0.8f, 0.86f, 1.0f).brightness(0.8f).size(size * 0.6f, size * 0.4f).fade(0.25f, 1.0f);
        }
        return p.color(0.26f, 0.3f, 0.44f).size(size, size * 0.7f).dark(0.85f).fade(0.25f, 1.0f);
    }

    /** From heading {@code from} towards {@code to} by {@code k} (0..1), the short way round. */
    static double turn(double from, double to, double k) {
        double d = Math.atan2(Math.sin(to - from), Math.cos(to - from));
        return from + d * k;
    }
}
