package com.cosmicbreach.world.weather;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.registry.ModBlocks;
import com.cosmicbreach.world.Layer;
import com.cosmicbreach.world.weather.WeatherSchedule.Phase;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Meteor Shower (GDD 2.5), over the Reach and the Drift every 40 to 60 minutes: a 10 s warning of red
 * streaks across the sky, then 60 s in which a meteor comes down near each player every 4 to 6 s, 12 to 32
 * blocks away, on solid ground. Each is telegraphed {@value #TELEGRAPH_TICKS} ticks ahead by a red circle of
 * radius {@value #RADIUS} and a whistle; the impact deals {@value #DAMAGE} ({@link #METEOR}: an explosion, so
 * a well-timed dash slips it and Blast Protection helps) and knocks everything in the circle outwards, and
 * leaves a Meteorite block where it hit (raw Starsteel, 5% a Heartstone).
 */
public final class MeteorShower {
    public static final ResourceKey<DamageType> METEOR = ResourceKey.create(Registries.DAMAGE_TYPE, CosmicBreach.id("meteor"));
    public static final float DAMAGE = 8.0f;
    public static final double RADIUS = 3.0;
    public static final int TELEGRAPH_TICKS = 40;
    public static final int MIN_GAP_TICKS = 80;
    public static final int MAX_GAP_TICKS = 120;
    public static final double MIN_DISTANCE = 12.0;
    public static final double MAX_DISTANCE = 32.0;
    public static final double KNOCKBACK = 1.1;
    /** How far above and below the player a landing spot is looked for. */
    static final int SEARCH_UP = 12;
    static final int SEARCH_DOWN = 24;

    /** A meteor on its way: where it lands, the ground block it hits, when. */
    public record Strike(Vec3 centre, BlockPos ground, long landsAt) {}

    /** A landed meteor (for checks): where, when, what it hit, whether it left a Meteorite. */
    public record Impact(Vec3 centre, BlockPos ground, long at, int hurt, boolean meteorite) {}

    private static final List<Strike> PENDING = new ArrayList<>();
    private static final Map<UUID, Long> NEXT = new HashMap<>();
    private static final List<Impact> IMPACTS = new ArrayList<>();

    private MeteorShower() {
    }

    /** True while a shower falls on {@code layer} (either side). */
    public static boolean active(net.minecraft.world.level.Level level, Layer layer) {
        return CosmicWeather.is(level, layer, WeatherKind.SHOWER, Phase.ACTIVE);
    }

    static void tick(ServerLevel level, WeatherData data) {
        long now = level.getGameTime();
        Iterator<Strike> it = PENDING.iterator();
        List<Strike> landing = new ArrayList<>();
        while (it.hasNext()) {
            Strike s = it.next();
            if (now >= s.landsAt()) {
                landing.add(s);
                it.remove();
            }
        }
        for (Strike s : landing) {
            impact(level, s);
        }
        RandomSource random = level.getRandom();
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            LayerWeather w = data.snapshot(Layer.at(player.getY()));
            if (!w.is(WeatherKind.SHOWER, Phase.ACTIVE)) {
                NEXT.remove(player.getUUID());
                continue;
            }
            Long due = NEXT.get(player.getUUID());
            if (due == null) {
                NEXT.put(player.getUUID(), now + 20 + random.nextInt(41));
                continue;
            }
            if (now < due) {
                continue;
            }
            Optional<BlockPos> ground = pickGround(level, player, random);
            if (ground.isPresent()) {
                strike(level, ground.get(), TELEGRAPH_TICKS, w.heading());
                NEXT.put(player.getUUID(), now + MIN_GAP_TICKS + random.nextInt(MAX_GAP_TICKS - MIN_GAP_TICKS + 1));
            } else {
                NEXT.put(player.getUUID(), now + 10); // nothing to land on here: look again soon
            }
        }
    }

    /**
     * Sends a meteor down onto the top of {@code ground} in {@code delay} ticks: the circle and whistle now,
     * the impact then. Server side; the shower calls it, and so can anything else.
     */
    public static Strike strike(ServerLevel level, BlockPos ground, int delay, float heading) {
        Vec3 centre = new Vec3(ground.getX() + 0.5, ground.getY() + 1.0, ground.getZ() + 0.5);
        Strike s = new Strike(centre, ground.immutable(), level.getGameTime() + delay);
        PENDING.add(s);
        PacketDistributor.sendToPlayersNear(level, null, centre.x, centre.y, centre.z, 160.0,
                new WeatherNet.Meteor(centre.x, centre.y, centre.z, delay, (float) RADIUS, heading));
        level.playSound(null, centre.x, centre.y, centre.z, WeatherSounds.METEOR_WHISTLE.get(), SoundSource.WEATHER,
                2.5f, 0.95f + level.getRandom().nextFloat() * 0.1f);
        return s;
    }

    private static void impact(ServerLevel level, Strike s) {
        Vec3 c = s.centre();
        DamageSource source = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(METEOR), c);
        int hurt = 0;
        AABB box = new AABB(c.x - RADIUS, c.y - 1.5, c.z - RADIUS, c.x + RADIUS, c.y + 3.0, c.z + RADIUS);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            double dx = e.getX() - c.x;
            double dz = e.getZ() - c.z;
            if (dx * dx + dz * dz > RADIUS * RADIUS || !e.isAlive()) {
                continue;
            }
            if (e.hurt(source, DAMAGE)) {
                hurt++;
                e.knockback(KNOCKBACK, c.x - e.getX(), c.z - e.getZ());
                e.hurtMarked = true;
            }
        }
        boolean meteorite = false;
        if (WeatherConfig.meteoritesLeaveBlocks() && level.isLoaded(s.ground())) {
            BlockState state = level.getBlockState(s.ground());
            if (!state.isAir() && !state.hasBlockEntity() && state.getFluidState().isEmpty()
                    && state.getDestroySpeed(level, s.ground()) >= 0f) {
                level.setBlockAndUpdate(s.ground(), ModBlocks.METEORITE.get().defaultBlockState());
                meteorite = true;
            }
        }
        level.playSound(null, c.x, c.y, c.z, WeatherSounds.METEOR_IMPACT.get(), SoundSource.WEATHER,
                4.0f, 0.9f + level.getRandom().nextFloat() * 0.2f);
        IMPACTS.add(new Impact(c, s.ground(), level.getGameTime(), hurt, meteorite));
        if (IMPACTS.size() > 64) {
            IMPACTS.remove(0);
        }
    }

    /**
     * A landing spot near {@code player}: 12 to 32 blocks away in any direction, the first solid top face
     * within 12 above and 24 below them, dry, clear of anything solid above (in the Reach, open sky).
     */
    static Optional<BlockPos> pickGround(ServerLevel level, ServerPlayer player, RandomSource random) {
        Layer layer = Layer.at(player.getY());
        int top = Math.min((int) Math.floor(player.getY()) + SEARCH_UP, layer.bandMaxY - 1);
        int bottom = Math.max((int) Math.floor(player.getY()) - SEARCH_DOWN, layer.bandMinY);
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = MIN_DISTANCE + random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
            int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, top, z);
            if (!level.isLoaded(pos)) {
                continue;
            }
            for (int y = top; y >= bottom; y--) {
                pos.setY(y);
                BlockState state = level.getBlockState(pos);
                if (state.isAir()) {
                    continue;
                }
                if (!state.getFluidState().isEmpty()) {
                    break; // water or lava: no landing here
                }
                if (state.getCollisionShape(level, pos).isEmpty()) {
                    continue; // flowers and grass: look below them
                }
                BlockPos ground = pos.immutable();
                if (layer != Layer.REACH || SolarFlare.openSky(level, ground.above())) {
                    return Optional.of(ground);
                }
                break;
            }
        }
        return Optional.empty();
    }

    /** The shower ended somewhere: players there get a fresh first delay next time. */
    static void weatherChanged(ServerLevel level, WeatherData data) {
        NEXT.clear();
    }

    /** Impacts since the server started, oldest first (for checks). */
    public static List<Impact> impacts() {
        return List.copyOf(IMPACTS);
    }

    /** Meteors still falling (for checks). */
    public static List<Strike> pending() {
        return List.copyOf(PENDING);
    }

    static void reset() {
        PENDING.clear();
        NEXT.clear();
        IMPACTS.clear();
    }
}
