package com.cosmicbreach.structure.trap;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.mixin.server.ServerMovePacketsAccessor;
import com.cosmicbreach.structure.StructureRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The Kinetic Tripwires' server side (GDD 6.4). Each player's horizontal speed is averaged over the last three ticks,
 * timed by their client's movement packets ({@link #speedOf}, {@link MotionWindow}), so packets bunched by a busy
 * server can't make a walker look like a sprinter; a thread crossed faster than walking pace
 * ({@link KineticRules#triggers}) makes both emitters of its line fire {@link KineticRules#BOLTS} bolts along it,
 * {@link KineticRules#BOLT_INTERVAL} ticks apart. A bolt is instant along the line and hurts every creature on it
 * with {@link KineticRules#damage} of the crossing's speed, as {@code cosmicbreach:kinetic_bolt} (no attacker, so a
 * dash's i-frames don't slip it; vanilla's hurt cooldown means one crossing costs one bolt's damage).
 */
public final class KineticTripwires {
    /** Farthest an emitter can be from a thread block on its line. */
    public static final int REACH = 24;
    private static final int LINE_COOLDOWN = 12;

    private record Bolt(ResourceKey<Level> dimension, BlockPos emitter, Direction along, int length, float damage, long fireAt) {}

    private static final Map<UUID, MotionWindow> MOTION = new HashMap<>();
    private static final List<Bolt> PENDING = new ArrayList<>();
    private static final Map<Long, Long> COOLDOWN = new HashMap<>();

    /** The last crossing that fired, for tests: {speed in blocks per tick, bolt damage, game time}. */
    private static volatile double[] lastFired = {0, 0, -1};

    private KineticTripwires() {
    }

    public static double[] lastFired() {
        return lastFired.clone();
    }

    /** End of each player's tick: where they stand, and the movement packets that brought them there. */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MOTION.computeIfAbsent(player.getUUID(), id -> new MotionWindow()).endTick(player.getX(), player.getZ(), movePackets(player));
    }

    /**
     * {@code player}'s horizontal speed now, blocks per tick of their movement: this tick's movement so far and the two
     * ticks before, over the movement packets that carried it.
     */
    public static double speedOf(ServerPlayer player) {
        MotionWindow w = MOTION.get(player.getUUID());
        return w == null ? 0 : w.averageWith(player.getX(), player.getZ(), movePackets(player));
    }

    /**
     * Movement packets {@code player}'s client has sent since the server's last tick ended (each is one tick of the
     * client's movement; vanilla counts them for its own speed check). Read at the end of a player's tick, before the
     * server marks them known, it is that tick's count.
     */
    public static int movePackets(ServerPlayer player) {
        if (player.connection instanceof ServerMovePacketsAccessor packets) {
            return Math.max(0, packets.cosmicbreach$receivedMovePackets() - packets.cosmicbreach$knownMovePackets());
        }
        return 0;
    }

    /** {@code player} is inside the thread at {@code pos}. */
    static void cross(ServerLevel level, BlockPos pos, Direction.Axis axis, ServerPlayer player) {
        if (player.isSpectator()) {
            return;
        }
        double speed = speedOf(player);
        if (!KineticRules.triggers(speed)) {
            return;
        }
        Direction plus = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        BlockPos start = emitter(level, pos, plus.getOpposite(), plus);
        BlockPos end = emitter(level, pos, plus, plus.getOpposite());
        if (start == null && end == null) {
            return;
        }
        long key = (start != null ? start : end).asLong();
        long now = level.getGameTime();
        Long until = COOLDOWN.get(key);
        if (until != null && now < until) {
            return;
        }
        COOLDOWN.put(key, now + LINE_COOLDOWN);
        float damage = KineticRules.damage(speed);
        int length = start != null && end != null ? start.distManhattan(end) - 1 : REACH;
        for (int b = 0; b < KineticRules.BOLTS; b++) {
            long at = now + KineticRules.boltTick(b);
            if (start != null) {
                PENDING.add(new Bolt(level.dimension(), start, plus, length, damage, at));
            }
            if (end != null) {
                PENDING.add(new Bolt(level.dimension(), end, plus.getOpposite(), length, damage, at));
            }
        }
        lastFired = new double[] {speed, damage, now};
        CosmicBreach.LOGGER.debug("[cosmicbreach] tripwire at {} crossed at {} b/t, bolts of {}", pos, speed, damage);
    }

    /** The emitter at the end of the thread line from {@code pos} going {@code way}, facing {@code facing}, or null. */
    private static BlockPos emitter(ServerLevel level, BlockPos pos, Direction way, Direction facing) {
        BlockPos.MutableBlockPos p = pos.mutable();
        for (int i = 1; i <= REACH; i++) {
            p.move(way);
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof KineticEmitterBlock) {
                return s.getValue(KineticEmitterBlock.FACING) == facing ? p.immutable() : null;
            }
            if (!(s.getBlock() instanceof KineticThreadBlock)) {
                return null;
            }
        }
        return null;
    }

    /** Fires the bolts that are due. */
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        for (Iterator<Bolt> it = PENDING.iterator(); it.hasNext(); ) {
            Bolt bolt = it.next();
            ServerLevel level = server.getLevel(bolt.dimension());
            if (level == null) {
                it.remove();
                continue;
            }
            if (level.getGameTime() < bolt.fireAt()) {
                continue;
            }
            it.remove();
            fire(level, bolt);
        }
        long now = server.overworld().getGameTime();
        COOLDOWN.values().removeIf(t -> t < now - 200);
    }

    private static void fire(ServerLevel level, Bolt bolt) {
        if (!(level.getBlockState(bolt.emitter()).getBlock() instanceof KineticEmitterBlock)) {
            return; // mined: disarmed
        }
        Vec3 from = Vec3.atCenterOf(bolt.emitter());
        Vec3 to = from.add(Vec3.atLowerCornerOf(bolt.along().getNormal()).scale(bolt.length() + 0.5));
        AABB line = new AABB(from, to).inflate(0.45, 0.0, 0.45).expandTowards(0, 1.4, 0).move(0, -0.5, 0);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, line, e -> e.isAlive() && !e.isSpectator())) {
            victim.hurt(level.damageSources().source(StructureRegistry.KINETIC_BOLT), bolt.damage());
        }
        level.playSound(null, bolt.emitter(), StructureRegistry.BOLT.get(), SoundSource.HOSTILE, 1.0f,
                0.9f + level.random.nextFloat() * 0.2f);
        Vec3 step = Vec3.atLowerCornerOf(bolt.along().getNormal()).scale(0.5);
        Vec3 at = from;
        for (int i = 0; i <= bolt.length() * 2; i++) {
            at = at.add(step);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y - 0.3, at.z, 1, 0.05, 0.05, 0.05, 0.0);
        }
    }

    public static void forget(UUID player) {
        MOTION.remove(player);
    }

    public static void reset() {
        MOTION.clear();
        PENDING.clear();
        COOLDOWN.clear();
        lastFired = new double[] {0, 0, -1};
    }
}
