package com.cosmicbreach.client.lift;

import com.cosmicbreach.guardian.leviathan.RiftLayout;
import com.cosmicbreach.lift.AscentCurrent;
import com.cosmicbreach.lift.LiftRiders;
import com.cosmicbreach.lift.LiftZones;
import com.cosmicbreach.lift.RiftLift;
import com.cosmicbreach.structure.StructureRegistry;
import com.cosmicbreach.structure.trap.UpdraftRiders;
import com.cosmicbreach.world.AetheriaWorld;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The lifts on the client (1.1 design sections 6 and 5): the zones the server sent, the local player lifted by the same
 * pure steps the server mirrors (the rescue lift under the Rifts and the rising currents beside the shrines; the client moves
 * its own player, as the updraft block does), the catch's whoosh, the list
 * of visible streams (the Rifts' air vents) drawn by {@link StreamRenderer} and heard through {@link StreamSound}, and the
 * feeding of {@link RiderTrails} (who is carried, where they are each tick) that the renderer draws trails from.
 */
public final class LiftClient {
    /** The air vents' colour: pale cyan. */
    public static final int VENT = 0x8FE8FF;
    /** The currents' colour: warm gold, apart from the vents' cyan. */
    public static final int CURRENT = 0xFFE6A0;
    /** A current's stream goes on this far past the shrine's floor, so it is seen over the island's edge. */
    public static final double CURRENT_TOP = 2.5;
    /** A vent's stream goes on up this far past its top, fading out: seen over the platform's edge from across the arena. */
    public static final double VENT_PLUME = 16.0;
    private static final Map<BlockPos, RiftLayout> RIFTS = new LinkedHashMap<>();
    private static List<StreamRenderer.Stream> streams = List.of();
    private static @Nullable RiftLift.Ride ride;
    private static List<AscentCurrent.Current> currents = List.of();
    /** What the local player carries between ticks for the rising currents. */
    private static AscentCurrent.State ascent = AscentCurrent.State.START;
    private static @Nullable RiftLayout rideIn;
    private static @Nullable Vec3 lastFeet;
    private static @Nullable net.minecraft.client.multiplayer.ClientLevel lastLevel;
    /** The players the lift carries and the trail behind each (their history, the server's list): see {@link RiderTrails}. */
    private static final RiderTrails TRAILS = new RiderTrails();

    private LiftClient() {
    }

    public static void register(IEventBus modBus, IEventBus game) {
        game.addListener(PlayerTickEvent.Pre.class, LiftClient::onPlayerTick);
        game.addListener(ClientTickEvent.Post.class, event -> {
            StreamSound.update();
            particles();
            trailTick();
        });
        game.addListener(RenderLevelStageEvent.class, StreamRenderer::render);
        game.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> clear());
    }

    /** The server's zones near this player (main thread). */
    public static void zones(LiftZones zones) {
        Map<BlockPos, RiftLayout> keep = new LinkedHashMap<>();
        for (LiftZones.Rift r : zones.rifts()) {
            RiftLayout known = RIFTS.get(r.centre());
            keep.put(r.centre(), known != null && known.seed() == r.seed() ? known
                    : new RiftLayout(r.centre().getX(), r.centre().getY(), r.centre().getZ(), r.seed()));
        }
        RIFTS.clear();
        RIFTS.putAll(keep);
        currents = List.copyOf(zones.currents());
        rebuildStreams();
    }

    /** The server's list of who the lift carries now (main thread). */
    public static void riders(LiftRiders riders) {
        TRAILS.list(riders.ids());
    }

    /**
     * The players a trail is drawn behind now: those the server listed who are not sneaking (a sneaking rider is lowered, and nothing
     * climbs behind them; each client reads that from the player's own synced state, so a sneak tap costs the network nothing), and the
     * local player while their own lift runs and they are not sneaking.
     */
    private static Map<Integer, Entity> carried(Minecraft mc) {
        Map<Integer, Entity> out = new HashMap<>();
        for (int id : TRAILS.listed()) {
            Entity e = mc.level.getEntity(id);
            if (e != null && LiftRiders.carried(true, e.isShiftKeyDown())) {
                out.put(id, e);
            }
        }
        if (LiftRiders.carried(ride != null, mc.player.isShiftKeyDown())) {
            out.put(mc.player.getId(), mc.player);
        }
        return out;
    }

    /** Once a client tick: notes where each carried player's feet are now (see {@link RiderTrails#note}). */
    private static void trailTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !AetheriaWorld.is(mc.level)) {
            // outside the Drift nobody is carried as far as this client knows: it must not keep the list it had when it left
            TRAILS.clear();
            return;
        }
        Map<Integer, Vec3> feet = new HashMap<>();
        carried(mc).forEach((id, e) -> feet.put(id, e.position()));
        TRAILS.note(mc.level.getGameTime(), feet);
    }

    /** The trails to draw this frame: each rider's last ticks and, while they are carried, their smoothly moving feet as the last point. */
    static List<RiderTrails.Trail> trails(float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return List.of();
        }
        Map<Integer, Vec3> heads = new HashMap<>();
        carried(mc).forEach((id, e) -> heads.put(id, e.getPosition(partial)));
        return TRAILS.drawn(mc.level.getGameTime(), partial, heads);
    }

    static void rebuildStreams() {
        List<StreamRenderer.Stream> s = new ArrayList<>();
        for (RiftLayout l : RIFTS.values()) {
            for (RiftLayout.Updraft u : l.updrafts()) {
                s.add(new StreamRenderer.Stream(u.x() + 0.5, u.z() + 0.5, u.bottom(), u.top() + 1.0, 0.9, VENT, VENT_PLUME));
            }
        }
        for (AscentCurrent.Current c : currents) {
            s.add(new StreamRenderer.Stream(c.x(), c.z(), c.bottom(), c.top() + CURRENT_TOP, 1.6, CURRENT));
        }
        streams = List.copyOf(s);
    }

    /** Every visible stream this client knows of. */
    public static List<StreamRenderer.Stream> streams() {
        return streams;
    }

    /** How many Rifts this client knows of (tests). */
    public static int knownRifts() {
        return RIFTS.size();
    }

    /** How many rising currents this client knows of (tests). */
    public static int knownCurrents() {
        return currents.size();
    }

    /** True while the local player is carried by a rising current (tests). */
    public static boolean ascending() {
        return ascent.ride() != null;
    }

    /** True while the local player rides the rescue lift (tests). */
    public static boolean riding() {
        return ride != null;
    }

    static void clear() {
        RIFTS.clear();
        currents = List.of();
        streams = List.of();
        ride = null;
        rideIn = null;
        lastFeet = null;
        lastLevel = null;
        ascent = AscentCurrent.State.START;
        TRAILS.clear();
        StreamSound.stopAll();
    }

    private static @Nullable RiftLayout riftAt(Vec3 feet) {
        for (RiftLayout l : RIFTS.values()) {
            if (l.inside(feet)) {
                return l;
            }
        }
        return null;
    }

    private static void onPlayerTick(PlayerTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getEntity() != mc.player || mc.level == null) {
            return;
        }
        LocalPlayer p = mc.player;
        // a teleport (or another level) ends the ride: the stale onGround of the tick after one would keep it going
        Vec3 last = lastFeet != null && lastLevel == mc.level ? lastFeet : null;
        lastFeet = p.position();
        lastLevel = mc.level;
        if (!AetheriaWorld.is(mc.level) || p.isSpectator() || p.isPassenger() || p.getAbilities().flying || p.isFallFlying() || !p.isAlive()
                || UpdraftRiders.riding(p, mc.level.getGameTime())) {
            ride = null;
            rideIn = null;
            ascent = AscentCurrent.State.START;
            return;
        }
        if (ride == null && ascend(mc, p)) {
            return;
        }
        Vec3 feet = p.position();
        RiftLayout l = ride != null ? rideIn : riftAt(feet);
        if (l == null) {
            ride = null;
            rideIn = null;
            return;
        }
        RiftLift.Step s = RiftLift.stepAfterMove(l, ride, last, feet, p.getDeltaMovement(), p.onGround(), p.isShiftKeyDown());
        if (ride == null && s.ride() != null) {
            mc.level.playLocalSound(p.getX(), p.getY(), p.getZ(), StructureRegistry.UPDRAFT_RUSH.get(), SoundSource.PLAYERS, 0.9f, 0.85f, false);
        }
        ride = s.ride();
        rideIn = ride != null ? l : null;
        if (s.velocity() != null) {
            p.setDeltaMovement(s.velocity());
            p.resetFallDistance();
        }
    }

    /**
     * The rising currents for the local player: moves them by {@link AscentCurrent#step} (the server runs the same step to cancel
     * fall damage and greet the arrival). Returns true while they are carried (the rescue lift stands aside).
     */
    private static boolean ascend(Minecraft mc, LocalPlayer p) {
        if (currents.isEmpty()) {
            ascent = AscentCurrent.State.START;
            return false;
        }
        boolean was = ascent.ride() != null;
        AscentCurrent.Step s = AscentCurrent.step(currents, ascent, p.position(), p.getDeltaMovement(), p.onGround(), p.isShiftKeyDown());
        ascent = s.state();
        if (!was && ascent.ride() != null) {
            mc.level.playLocalSound(p.getX(), p.getY(), p.getZ(), StructureRegistry.UPDRAFT_RUSH.get(), SoundSource.PLAYERS, 0.9f, 1.1f, false);
        }
        if (s.velocity() != null) {
            p.setDeltaMovement(s.velocity());
            p.resetFallDistance();
        }
        return ascent.ride() != null;
    }

    /** Rising sparks along every stream within 32 blocks. */
    private static void particles() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused() || streams.isEmpty() || !AetheriaWorld.is(mc.level)) {
            return;
        }
        RandomSource r = mc.level.getRandom();
        Vec3 eye = mc.player.getEyePosition();
        for (StreamRenderer.Stream s : streams) {
            if (Math.hypot(s.x() - eye.x, s.z() - eye.z) > 32.0) {
                continue;
            }
            for (int i = 0; i < 2; i++) {
                double y = Mth.clamp(eye.y + (r.nextDouble() - 0.5) * 40.0, s.bottom(), s.top());
                mc.level.addParticle(ParticleTypes.END_ROD, s.x() + (r.nextDouble() - 0.5) * s.width(), y,
                        s.z() + (r.nextDouble() - 0.5) * s.width(), 0.0, 0.18, 0.0);
            }
        }
    }
}
