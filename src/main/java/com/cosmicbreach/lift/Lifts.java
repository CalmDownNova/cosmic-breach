package com.cosmicbreach.lift;

import com.cosmicbreach.guardian.GuardianLairs;
import com.cosmicbreach.guardian.GuardianTypes;
import com.cosmicbreach.guardian.leviathan.LeviathanRiftStructure;
import com.cosmicbreach.guardian.leviathan.RiftLayout;
import com.cosmicbreach.net.ModNetworking;
import com.cosmicbreach.shrine.ShrineData;
import com.cosmicbreach.structure.StructureRegistry;
import com.cosmicbreach.structure.trap.UpdraftBlock;
import com.cosmicbreach.world.AetheriaWorld;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.Nullable;

/**
 * The lifts (1.1 design sections 6 and 5), common side: the rescue lift under every known Leviathan Rift's platforms and the
 * rising currents beside the shrines over a drop ({@link AscentCurrent}, saved in {@link ShrineData}). Each client lifts its own player from the zones this syncs to it
 * ({@link LiftZones}, sent when a player is first seen and then checked every {@value #SYNC_EVERY} ticks, sent when they
 * change); the server runs the same pure step for every player in Aetheria to cancel fall damage while lifted, play the
 * catch's whoosh to everyone else and tell every client who it carries ({@link LiftRiders}: each draws a trail behind them).
 * Only players are ever moved. Rifts come from the lair registry, so the Rifts of existing worlds lift too.
 */
public final class Lifts {
    public static final double SYNC_RANGE = 320.0;
    public static final int SYNC_EVERY = 40;
    /**
     * A Rift's sphere spans its centre's Y plus or minus this much (its shell's radius up); nobody outside that band is ever checked.
     * The Drift is about as tall as the band, so in the Drift every player is checked each tick (a walk of the lair registry, which is
     * short): the band only spares the layers above and below it.
     */
    private static final double BAND = RiftLayout.RY + 2.0;
    private static final Map<UUID, RiftLift.Ride> RIDES = new ConcurrentHashMap<>();
    private static final Map<UUID, BlockPos> RIDE_RIFT = new ConcurrentHashMap<>();
    /** What each player carries between ticks for the rising currents (see {@link AscentCurrent.State}). */
    private static final Map<UUID, AscentCurrent.State> ASCENTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Vec3> LAST = new ConcurrentHashMap<>();
    private static final Map<UUID, LiftZones> SENT = new ConcurrentHashMap<>();
    private static final Map<BlockPos, RiftLayout> LAYOUTS = new ConcurrentHashMap<>();
    /** The entity ids of the riders each level's clients were last told of (see {@link LiftRiders}). */
    private static final Map<ResourceKey<Level>, List<Integer>> RIDERS = new ConcurrentHashMap<>();

    private Lifts() {
    }

    public static void register(IEventBus modBus, IEventBus game) {
        LiftRegistry.register(modBus);
        modBus.addListener(RegisterPayloadHandlersEvent.class, Lifts::registerPayloads);
        game.addListener(PlayerTickEvent.Post.class, Lifts::onPlayerTick);
        game.addListener(ServerTickEvent.Post.class, Lifts::broadcastRiders);
        game.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> forget(event.getEntity()));
        game.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, event -> forget(event.getEntity()));
        game.addListener(PlayerEvent.PlayerRespawnEvent.class, event -> forget(event.getEntity()));
        game.addListener(ServerStoppedEvent.class, event -> {
            RIDES.clear();
            RIDE_RIFT.clear();
            ASCENTS.clear();
            LAST.clear();
            SENT.clear();
            LAYOUTS.clear();
            RIDERS.clear();
        });
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ModNetworking.PROTOCOL_VERSION);
        // a lambda calling the client class, so a dedicated server never loads it
        registrar.playToClient(LiftZones.TYPE, LiftZones.STREAM_CODEC,
                (payload, context) -> com.cosmicbreach.client.lift.LiftClient.zones(payload));
        registrar.playToClient(LiftRiders.TYPE, LiftRiders.STREAM_CODEC,
                (payload, context) -> com.cosmicbreach.client.lift.LiftClient.riders(payload));
    }

    static void forget(Player player) {
        UUID id = player.getUUID();
        RIDES.remove(id);
        RIDE_RIFT.remove(id);
        ASCENTS.remove(id);
        LAST.remove(id);
        SENT.remove(id);
    }

    /** The layout of the Rift centred at {@code centre} (cached; the maths worldgen built it with). */
    static RiftLayout layout(ServerLevel level, BlockPos centre) {
        return LAYOUTS.computeIfAbsent(centre.immutable(), c -> LeviathanRiftStructure.layout(level.getSeed(), c));
    }

    /** The known Rift whose sphere holds {@code feet}, or null. */
    static @Nullable RiftLayout riftAt(ServerLevel level, Vec3 feet) {
        for (GuardianLairs.Lair lair : GuardianLairs.get(level).all().values()) {
            if (!lair.guardian().equals(GuardianTypes.LEVIATHAN.name())) {
                continue;
            }
            RiftLayout l = layout(level, lair.arenaCentre());
            if (l.inside(feet)) {
                return l;
            }
        }
        return null;
    }

    /** The zones within {@value #SYNC_RANGE} blocks of {@code at}. */
    static LiftZones zonesNear(ServerLevel level, Vec3 at) {
        List<LiftZones.Rift> rifts = new ArrayList<>();
        for (GuardianLairs.Lair lair : GuardianLairs.get(level).all().values()) {
            BlockPos c = lair.arenaCentre();
            if (lair.guardian().equals(GuardianTypes.LEVIATHAN.name())
                    && Math.hypot(c.getX() + 0.5 - at.x, c.getZ() + 0.5 - at.z) <= SYNC_RANGE) {
                rifts.add(new LiftZones.Rift(c, layout(level, c).seed()));
            }
        }
        List<AscentCurrent.Current> currents = new ArrayList<>();
        for (AscentCurrent.Current c : ShrineData.get(level).currentList()) {
            if (Math.hypot(c.x() - at.x, c.z() - at.z) <= SYNC_RANGE) {
                currents.add(c);
            }
        }
        return new LiftZones(List.copyOf(rifts), List.copyOf(currents));
    }

    static void sync(ServerPlayer player) {
        boolean joining = !SENT.containsKey(player.getUUID());
        LiftZones zones = zonesNear(player.serverLevel(), player.position());
        if (!zones.equals(SENT.get(player.getUUID()))) {
            SENT.put(player.getUUID(), zones);
            PacketDistributor.sendToPlayer(player, zones);
        }
        // a player arriving is told who is carried, even when nobody is (their client may still hold a list from before they left); the
        // rest hear of changes as they happen
        LiftRiders told = LiftRiders.forArrival(joining, RIDERS.getOrDefault(player.serverLevel().dimension(), List.of()));
        if (told != null) {
            PacketDistributor.sendToPlayer(player, told);
        }
    }

    /** True while the server sees {@code player} on the rescue lift (tests; also meant for other lanes, such as a voice line when someone is caught). */
    public static boolean riding(Player player) {
        return RIDES.containsKey(player.getUUID());
    }

    /** True while the server sees {@code player} carried by a rising current (tests). */
    public static boolean ascending(Player player) {
        AscentCurrent.State s = ASCENTS.get(player.getUUID());
        return s != null && s.ride() != null;
    }

    /** Once a tick: tells every client in the Drift who the lift is holding, when that has changed (a handful of bytes, twice a rescue). */
    private static void broadcastRiders(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (!AetheriaWorld.is(level)) {
                continue;
            }
            List<Integer> ids = new ArrayList<>();
            for (ServerPlayer p : level.players()) {
                if (RIDES.containsKey(p.getUUID())) {
                    ids.add(p.getId()); // sneaking or not: each client reads that from the player's own synced state, so a sneak tap sends nothing
                }
            }
            Collections.sort(ids);
            if (!ids.equals(RIDERS.getOrDefault(level.dimension(), List.of()))) {
                RIDERS.put(level.dimension(), List.copyOf(ids));
                PacketDistributor.sendToPlayersInDimension(level, new LiftRiders(List.copyOf(ids)));
            }
        }
    }

    /**
     * True while the player's body is in an air vent's updraft block. The client stands aside for those (the vent carries the player up
     * and in, and its own lift would only fight it), so the server's mirror does too: nobody hears a catch's whoosh or sees a ribbon
     * behind a player a vent is carrying, and the two sides agree about who the lift holds.
     */
    private static boolean inUpdraft(ServerLevel level, ServerPlayer player) {
        AABB box = player.getBoundingBox().deflate(1.0E-7);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX); x++) {
            for (int y = Mth.floor(box.minY); y <= Mth.floor(box.maxY); y++) {
                for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ); z++) {
                    if (level.getBlockState(pos.set(x, y, z)).getBlock() instanceof UpdraftBlock) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * The rising currents for one player: the same pure step the client moves its own player by ({@link AscentCurrent}), here to
     * cancel fall damage, whoosh for everyone else at the catch and greet the arrival. Returns true while they are carried.
     */
    private static boolean ascend(ServerLevel level, ServerPlayer player, Vec3 feet, Vec3 v) {
        UUID id = player.getUUID();
        List<AscentCurrent.Current> currents = ShrineData.get(level).currentList();
        if (currents.isEmpty()) {
            ASCENTS.remove(id);
            return false;
        }
        AscentCurrent.State before = ASCENTS.getOrDefault(id, AscentCurrent.State.START);
        AscentCurrent.Step step = AscentCurrent.step(currents, before, feet, v, player.onGround(), player.isShiftKeyDown());
        if (step.state().ride() != null && inUpdraft(level, player)) {
            ASCENTS.remove(id); // an air vent has them (only looked for once a current would carry them)
            return false;
        }
        ASCENTS.put(id, step.state());
        boolean was = before.ride() != null;
        boolean now = step.state().ride() != null;
        if (now) {
            if (!was) {
                // the catch: a whoosh for everyone else (the player's own client plays theirs at once)
                level.playSound(player, player.getX(), player.getY(), player.getZ(), StructureRegistry.UPDRAFT_RUSH.get(), SoundSource.PLAYERS, 0.9f, 1.1f);
            }
            player.resetFallDistance();
            if (level.getGameTime() % 2 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 0.4, player.getZ(), 2, 0.3, 0.4, 0.3, 0.02);
            }
        } else if (was && step.velocity() != null) {
            // set down in front of the shrine above
            player.resetFallDistance();
            player.displayClientMessage(Component.translatable("cosmicbreach.lift.carried"), true);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.2f);
        }
        return now;
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !AetheriaWorld.is(player.level())) {
            return;
        }
        ServerLevel level = player.serverLevel();
        UUID id = player.getUUID();
        if (!SENT.containsKey(id) || (level.getGameTime() + player.getId()) % SYNC_EVERY == 0) {
            sync(player);
        }
        Vec3 feet = player.position();
        Vec3 last = LAST.put(id, feet);
        Vec3 v = last == null ? Vec3.ZERO : feet.subtract(last);
        if (player.isSpectator() || player.isPassenger() || player.getAbilities().flying || player.isFallFlying() || !player.isAlive()) {
            RIDES.remove(id);
            RIDE_RIFT.remove(id);
            ASCENTS.remove(id);
            return;
        }
        if (!RIDES.containsKey(id) && ascend(level, player, feet, v)) {
            return;
        }
        if (Math.abs(feet.y - RiftLayout.CENTRE_Y) > BAND) {
            RIDES.remove(id);
            RIDE_RIFT.remove(id);
            return;
        }
        RiftLift.Ride ride = RIDES.get(id);
        BlockPos rideRift = RIDE_RIFT.get(id);
        RiftLayout l = ride != null && rideRift != null ? layout(level, rideRift) : riftAt(level, feet);
        if (l == null) {
            RIDES.remove(id);
            RIDE_RIFT.remove(id);
            return;
        }
        RiftLift.Step step = RiftLift.stepAfterMove(l, ride, last, feet, v, player.onGround(), player.isShiftKeyDown());
        if (step.ride() == null || inUpdraft(level, player)) {
            RIDES.remove(id);
            RIDE_RIFT.remove(id);
            return;
        }
        if (ride == null) {
            // the catch: a whoosh for everyone else (the player's own client plays theirs at once)
            level.playSound(player, player.getX(), player.getY(), player.getZ(), StructureRegistry.UPDRAFT_RUSH.get(), SoundSource.PLAYERS,
                    0.9f, 0.85f);
        }
        RIDES.put(id, step.ride());
        RIDE_RIFT.put(id, l.centreBlock());
        player.resetFallDistance();
        // the rising trail is each client's own drawing behind every rider it is told of (LiftRiders): no particles any more
    }
}
