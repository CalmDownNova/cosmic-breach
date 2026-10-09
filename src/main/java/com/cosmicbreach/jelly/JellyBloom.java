package com.cosmicbreach.jelly;

import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.Layer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * The bloom (1.2 design section 7): now and then (a roll between twenty and sixty minutes apart, counted from the first
 * time a player is in layer 3), 15 to 25 jellies rise from the void under the layer near a player in it, drift up through it
 * for a few minutes and sink away. Server only, in memory, and cheap: a check once every 100 ticks per Aetheria level
 * and, when one starts, a handful of placements. Each jelly carries its own plan ({@link DriftJelly#joinBloom}), saved
 * with it, so a bloom ends by itself wherever its jellies are, loaded or not, and nothing here tracks them.
 */
public final class JellyBloom {
    /** How far from the player the bloom's middle is, and how wide it spreads. */
    private static final double DISTANCE_MIN = 28.0;
    private static final double DISTANCE_MAX = 56.0;
    private static final double SPREAD = 12.0;
    /** The floor it starts from: the layer's lowest rock is at 1, and a jelly needs room. */
    private static final int START_MIN_Y = 3;
    private static final int START_MAX_Y = 14;

    private static final class State {
        long next = -1;
        long activeUntil;
    }

    private static final Map<ResourceKey<Level>, State> STATES = new HashMap<>();

    private JellyBloom() {
    }

    public static void register(IEventBus game) {
        game.addListener(LevelTickEvent.Post.class, JellyBloom::onLevelTick);
        game.addListener(ServerStartingEvent.class, e -> reset());
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !AetheriaWorld.is(level) || level.getGameTime() % 100 != 0) {
            return;
        }
        long now = level.getGameTime();
        State st = STATES.computeIfAbsent(level.dimension(), k -> new State());
        List<ServerPlayer> inDeep = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (!p.isSpectator() && Layer.at(p.getY()) == Layer.DEEP) {
                inDeep.add(p);
            }
        }
        if (inDeep.isEmpty()) {
            return;
        }
        if (st.next < 0) {
            st.next = now + JellyRules.bloomDelay(level.random.nextDouble());
        }
        if (now < st.next || now < st.activeUntil) {
            return;
        }
        ServerPlayer target = inDeep.get(level.random.nextInt(inDeep.size()));
        st.next = now + JellyRules.bloomDelay(level.random.nextDouble());
        start(level, target.position(), JellyRules.bloomCount(level.random.nextDouble()));
        st.activeUntil = now + 8400;
    }

    /** Forgets the schedule (a new server, a test): game time starts again with a world. */
    public static void reset() {
        STATES.clear();
    }

    /** Ticks until the next bloom, or -1 if none has been scheduled yet (for tests). */
    public static long nextIn(ServerLevel level) {
        State st = STATES.get(level.dimension());
        return st == null || st.next < 0 ? -1 : st.next - level.getGameTime();
    }

    /**
     * Starts a bloom of up to {@code count} jellies from the void under the layer, 28 to 56 blocks from {@code around}
     * (inside the chunks that are loaded), drifting off together. Returns the jellies placed.
     */
    public static List<DriftJelly> start(ServerLevel level, Vec3 around, int count) {
        RandomSource random = level.random;
        double angle = random.nextDouble() * Math.PI * 2.0;
        double distance = DISTANCE_MIN + random.nextDouble() * (DISTANCE_MAX - DISTANCE_MIN);
        double cx = around.x + Math.cos(angle) * distance;
        double cz = around.z + Math.sin(angle) * distance;
        double heading = random.nextDouble() * Math.PI * 2.0;
        long now = level.getGameTime();
        List<DriftJelly> placed = new ArrayList<>();
        for (int attempt = 0; attempt < count * 6 && placed.size() < count; attempt++) {
            double x = cx + (random.nextDouble() * 2.0 - 1.0) * SPREAD;
            double z = cz + (random.nextDouble() * 2.0 - 1.0) * SPREAD;
            double y = START_MIN_Y + random.nextInt(START_MAX_Y - START_MIN_Y + 1);
            BlockPos pos = BlockPos.containing(x, y, z);
            if (!level.isLoaded(pos)) {
                continue;
            }
            DriftJelly jelly = Jellies.DRIFT_JELLY.get().create(level);
            if (jelly == null) {
                continue;
            }
            jelly.moveTo(x, y, z, random.nextFloat() * 360.0f, 0.0f);
            AABB box = jelly.getBoundingBox();
            if (!level.noCollision(jelly, box) || level.containsAnyLiquid(box)) {
                continue;
            }
            jelly.joinBloom(now, JellyRules.bloomLife(random.nextDouble()), 55.0 + random.nextDouble() * 70.0, 0.75 + random.nextDouble() * 0.5,
                    heading + (random.nextDouble() - 0.5) * 0.6);
            if (level.addFreshEntity(jelly)) {
                placed.add(jelly);
            }
        }
        return placed;
    }
}
