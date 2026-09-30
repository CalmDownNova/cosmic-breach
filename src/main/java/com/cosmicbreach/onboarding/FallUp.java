package com.cosmicbreach.onboarding;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.world.AetheriaAudio;
import com.cosmicbreach.world.AetheriaSpots;
import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.gen.AetheriaTerrain;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Falling up (GDD 1.3). A player who steps into a Breach is pulled upward for {@link #LIFT_TICKS} with a
 * whoosh while their screen fades to white (the client does the pull and the fade, {@link OnboardingNet.FallUpStart}),
 * then moved:
 *
 * <ul>
 *   <li>From the Overworld to Aetheria at the same X and Z, {@link #ABOVE} blocks above the nearest island
 *       within 64 blocks ({@link AetheriaSpots#nearestIslandColumn}; the nearest at any distance if none),
 *       with Slow Falling for 8 s, the Arrival cue and 60 s of {@link ArrivalGrace}. A Landing is built under
 *       the arrival ({@link Landings}) unless one lies within 32 blocks, in which case the player comes down
 *       over that one. The ring they came through is remembered.</li>
 *   <li>From Aetheria back to the Overworld: standing beside the ring they came through, or at the same X and
 *       Z on the surface if that ring is gone, or at the world spawn.</li>
 * </ul>
 * After an arrival, Breaches ignore that player for {@link #COOLDOWN_TICKS}.
 *
 * <p>For later tasks: {@link #lastArrival} tells where and when a player last came through.
 */
public final class FallUp {
    public static final int LIFT_TICKS = 24;
    public static final int COOLDOWN_TICKS = 10 * 20;
    public static final int ABOVE = 30;
    public static final int NEAR_ISLAND = 64;
    public static final int SLOW_FALLING_TICKS = 8 * 20;

    private record Travel(ResourceKey<Level> from, BlockPos ring, BlockPos entered, int arriveTick) {}

    /**
     * An arrival: where the player was put, the ring they came through (its origin, the Breach's north-west
     * block) and the Breach block they stepped into, when (server tick), and whether a Landing was built.
     */
    public record Arrival(ResourceKey<Level> to, Vec3 pos, BlockPos ring, BlockPos entered, int tick, boolean landingBuilt) {}

    private static final Map<UUID, Travel> TRAVELLING = new HashMap<>();
    private static final Map<UUID, Arrival> ARRIVED = new HashMap<>();

    private FallUp() {
    }

    /** {@code player} is inside the Breach block at {@code breach} of the ring at {@code ring} (server, every tick they are). */
    static void enter(ServerPlayer player, BlockPos breach, BlockPos ring) {
        UUID id = player.getUUID();
        if (TRAVELLING.containsKey(id)) {
            return;
        }
        Arrival last = ARRIVED.get(id);
        int now = player.server.getTickCount();
        if (last != null && now - last.tick() < COOLDOWN_TICKS) {
            return;
        }
        TRAVELLING.put(id, new Travel(player.level().dimension(), ring.immutable(), breach.immutable(), now + LIFT_TICKS));
        PacketDistributor.sendToPlayer(player, new OnboardingNet.FallUpStart(LIFT_TICKS));
        player.level().playSound(null, breach, OnboardingRegistry.FALL_UP.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int now = server.getTickCount();
        for (Iterator<Map.Entry<UUID, Travel>> it = TRAVELLING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Travel> e = it.next();
            Travel t = e.getValue();
            if (now < t.arriveTick()) {
                continue;
            }
            it.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            if (player == null || !player.isAlive() || player.level().dimension() != t.from()) {
                continue;
            }
            if (t.from() == Level.OVERWORLD) {
                toAetheria(player, t.ring(), t.entered());
            } else {
                toOverworld(player, t.ring(), t.entered());
            }
        }
    }

    // ------------------------------------------------------------------ up

    private static void toAetheria(ServerPlayer player, BlockPos ring, BlockPos entered) {
        ServerLevel aetheria = player.server.getLevel(AetheriaWorld.LEVEL);
        if (aetheria == null) {
            player.displayClientMessage(Component.translatable("cosmicbreach.breach.no_aetheria"), true);
            return;
        }
        int x = player.getBlockX();
        int z = player.getBlockZ();
        AetheriaTerrain terrain = AetheriaSpots.terrain(aetheria);
        Optional<BlockPos> column = AetheriaSpots.nearestIslandColumn(terrain, x, z, NEAR_ISLAND);
        if (column.isEmpty()) {
            column = AetheriaSpots.nearestIslandColumn(terrain, x, z, 3000);
        }
        Landings landings = Landings.get(aetheria);
        BlockPos arrivalColumn = null;
        int floorY = 0;
        boolean built = false;
        if (column.isPresent()) {
            Optional<BlockPos> near = landings.nearest(column.get(), Landings.SHARED);
            if (near.isPresent()) {
                int[] a = LandingLayout.arrivalFor(near.get().getX(), near.get().getZ());
                arrivalColumn = new BlockPos(a[0], 0, a[1]);
                floorY = near.get().getY();
            } else {
                Optional<BlockPos> feet = AetheriaSpots.settle(aetheria, column.get(), 6, 12);
                if (feet.isPresent()) {
                    floorY = feet.get().getY() - 1;
                    arrivalColumn = new BlockPos(feet.get().getX(), 0, feet.get().getZ());
                    int[] o = LandingLayout.originFor(arrivalColumn.getX(), arrivalColumn.getZ());
                    landings.build(aetheria, new BlockPos(o[0], floorY, o[1]));
                    built = true;
                }
            }
        }
        if (arrivalColumn == null) {
            // no island found at all: the safe-ground search (it always finds something in a normal world)
            BlockPos feet = AetheriaSpots.safeSpot(aetheria, AetheriaSpots.Kind.SPIRES, new BlockPos(x, 0, z))
                    .map(AetheriaSpots.Spot::feet).orElse(new BlockPos(x, 400, z));
            arrivalColumn = new BlockPos(feet.getX(), 0, feet.getZ());
            floorY = feet.getY() - 1;
        }
        Vec3 pos = new Vec3(arrivalColumn.getX() + 0.5, floorY + 1 + ABOVE, arrivalColumn.getZ() + 0.5);
        player.setData(OnboardingRegistry.STATE, player.getData(OnboardingRegistry.STATE).withRing(ring));
        player.teleportTo(aetheria, pos.x, pos.y, pos.z, Set.of(), player.getYRot(), 30.0f);
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        player.resetFallDistance();
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SLOW_FALLING_TICKS, 0, false, true, true));
        if (!player.getData(AetheriaAudio.HEARD_ARRIVAL)) {
            AetheriaAudio.playArrival(player);
        }
        ArrivalGrace.start(player);
        ARRIVED.put(player.getUUID(), new Arrival(AetheriaWorld.LEVEL, pos, ring, entered, player.server.getTickCount(), built));
        CosmicBreach.LOGGER.debug("[cosmicbreach] {} fell up through {} (ring {}) to {} (Landing {})", player.getGameProfile().getName(),
                entered.toShortString(), ring.toShortString(), BlockPos.containing(pos).toShortString(), built ? "built" : "shared");
    }

    // ------------------------------------------------------------------ home

    private static void toOverworld(ServerPlayer player, BlockPos through, BlockPos entered) {
        ServerLevel overworld = player.server.overworld();
        Optional<BlockPos> ring = player.getData(OnboardingRegistry.STATE).ring();
        BlockPos feet = null;
        if (ring.isPresent() && load(overworld, ring.get()) && BreachRings.stands(overworld, ring.get())) {
            feet = besideRing(overworld, ring.get());
        }
        if (feet == null && ring.isPresent()) {
            feet = surface(overworld, player.getBlockX(), player.getBlockZ());
        }
        if (feet == null) {
            BlockPos spawn = overworld.getSharedSpawnPos();
            feet = Optional.ofNullable(surface(overworld, spawn.getX(), spawn.getZ())).orElse(spawn);
        }
        Vec3 pos = Vec3.atBottomCenterOf(feet);
        // facing the middle of the ring (the corner its four Breach blocks share)
        float yaw = ring.map(r -> (float) Math.toDegrees(Math.atan2(-(r.getX() + 1.0 - pos.x), r.getZ() + 1.0 - pos.z))).orElse(player.getYRot());
        player.teleportTo(overworld, pos.x, pos.y, pos.z, Set.of(), yaw, 20.0f);
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        player.resetFallDistance();
        ARRIVED.put(player.getUUID(), new Arrival(Level.OVERWORLD, pos, through, entered, player.server.getTickCount(), false));
        CosmicBreach.LOGGER.debug("[cosmicbreach] {} came home through {} (ring {}) to {}", player.getGameProfile().getName(),
                entered.toShortString(), through.toShortString(), feet.toShortString());
    }

    /** Loads the ring's chunk (a player going home needs it anyway); true once it is loaded. */
    private static boolean load(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        }
        return level.isLoaded(pos);
    }

    /**
     * A spot to stand just outside the 4 by 4 ring whose origin is {@code origin}: the middle of each side first,
     * then along the sides, then the corners, then a little higher.
     */
    private static BlockPos besideRing(ServerLevel level, BlockPos origin) {
        for (int up = 0; up <= 3; up++) {
            for (int[] o : new int[][] {{0, 3}, {1, 3}, {3, 0}, {3, 1}, {0, -2}, {1, -2}, {-2, 0}, {-2, 1},
                    {-1, 3}, {2, 3}, {3, -1}, {3, 2}, {-1, -2}, {2, -2}, {-2, -1}, {-2, 2},
                    {3, 3}, {-2, 3}, {3, -2}, {-2, -2}}) {
                BlockPos p = origin.offset(o[0], up, o[1]);
                if (AetheriaSpots.standable(level, p) && level.getBlockState(p.below()).getFluidState().isEmpty()) {
                    return p;
                }
            }
        }
        return null;
    }

    /** The feet position on top of the column at (x, z), or null over water or lava. */
    private static BlockPos surface(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos feet = new BlockPos(x, y, z);
        if (!level.getBlockState(feet.below()).getFluidState().isEmpty()
                || !level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)) {
            return null;
        }
        return feet;
    }

    // ------------------------------------------------------------------ bookkeeping

    /** Where {@code player} last came through a Breach, or null. */
    public static Arrival lastArrival(UUID player) {
        return ARRIVED.get(player);
    }

    /** True while {@code player} is being pulled up. */
    public static boolean travelling(UUID player) {
        return TRAVELLING.containsKey(player);
    }

    static void forget(UUID player) {
        TRAVELLING.remove(player);
    }

    static void reset() {
        TRAVELLING.clear();
        ARRIVED.clear();
    }
}
