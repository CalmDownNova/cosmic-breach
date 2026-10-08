package com.cosmicbreach.codex;

import com.cosmicbreach.astrolabe.Astrolabes;
import com.cosmicbreach.guardian.GuardianLairs;
import com.cosmicbreach.guardian.GuardianRewards;
import com.cosmicbreach.guardian.GuardianType;
import com.cosmicbreach.guardian.GuardianTypes;
import com.cosmicbreach.registry.ModParticles;
import com.cosmicbreach.structure.StructureRegistry;
import com.cosmicbreach.structure.sanctum.SanctumArena;
import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.Layer;
import com.cosmicbreach.world.LayerAttunement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * The Codex's lair locator (GDD 6.1, "the Codex points to the nearest, like an Eye of Ender"): used while sneaking
 * in Aetheria, the book throws a mote of light that flies a few seconds toward the nearest lair of a guardian the
 * player has not yet beaten and can reach (the Colossus in the Reach; the Leviathan once attuned to the Drift; the
 * Unsung once attuned to the Deep), and toward the Breach Sanctum when none is left and the Breach is not sealed.
 * It points at the layer the player stands in first: a lair of that layer wins over a nearer one in another layer
 * (the layers stack, so "nearer across" used to send a player in the Deep back up to a lair above them). With
 * nothing left in this layer it points to the earliest guardian still unbeaten, in the order the layers are played.
 * The mote is particles sent from the server along a path, so nothing is saved and nothing can be picked up.
 */
public final class LairMote {
    /** How far the mote flies, in blocks, and in how many ticks; it hovers a little after. */
    public static final double REACH = 14.0;
    public static final int FLIGHT_TICKS = 40;
    public static final int LIFE_TICKS = 56;
    /** One throw per player this often. */
    public static final int COOLDOWN_TICKS = 40;

    /**
     * A lair the mote could point at: {@code layer} is the layer the lair belongs to and {@code rank} its place in the
     * order the game is played (Colossus 0, Leviathan 1, Unsung 2, the Sanctum 3).
     */
    public record Candidate(String name, BlockPos centre, Layer layer, int rank, boolean reachable, boolean defeated) {
    }

    /** The Sanctum's place in the order of play: after every guardian. */
    public static final int SANCTUM_RANK = 3;

    private record Mote(ServerLevel level, UUID owner, Vec3 start, Vec3 dir, long born) {
    }

    private static final List<Mote> LIVE = new ArrayList<>();
    private static final Map<UUID, Long> LAST = new HashMap<>();
    /** What the last mote flew toward (for tests), or null. */
    private static volatile Candidate lastTarget;

    private LairMote() {
    }

    /**
     * The lair to point at from {@code from}, standing in {@code here}, among the reachable, undefeated candidates, or
     * empty. Lairs of the player's own layer come first; with none there, every layer counts. Within that the earliest
     * in the order of play wins, and among equals the nearest (horizontally). Pure.
     */
    public static Optional<Candidate> choose(Layer here, BlockPos from, List<Candidate> candidates) {
        List<Candidate> open = new ArrayList<>();
        for (Candidate c : candidates) {
            if (c.reachable() && !c.defeated()) {
                open.add(c);
            }
        }
        List<Candidate> pool = new ArrayList<>();
        for (Candidate c : open) {
            if (c.layer() == here) {
                pool.add(c);
            }
        }
        if (pool.isEmpty()) {
            pool = open;
        }
        Candidate best = null;
        double bestD = Double.MAX_VALUE;
        for (Candidate c : pool) {
            double dx = c.centre().getX() - from.getX();
            double dz = c.centre().getZ() - from.getZ();
            double d = dx * dx + dz * dz;
            if (best == null || c.rank() < best.rank() || (c.rank() == best.rank() && d < bestD)) {
                bestD = d;
                best = c;
            }
        }
        return Optional.ofNullable(best);
    }

    /** The layer a guardian's lair belongs to, and its place in the order of play. */
    static Layer homeLayer(GuardianType type) {
        if (type == GuardianTypes.COLOSSUS) {
            return Layer.REACH;
        }
        return type == GuardianTypes.LEVIATHAN ? Layer.DRIFT : Layer.DEEP;
    }

    static int rank(GuardianType type) {
        if (type == GuardianTypes.COLOSSUS) {
            return 0;
        }
        return type == GuardianTypes.LEVIATHAN ? 1 : 2;
    }

    /**
     * Where the mote is {@code tick} ticks after the throw: it lifts off the book, flies {@link #REACH} blocks
     * along {@code dir} (a unit vector) and slows to a hover. Pure.
     */
    public static Vec3 position(Vec3 start, Vec3 dir, int tick) {
        double t = Math.min(1.0, tick / (double) FLIGHT_TICKS);
        double eased = 1.0 - (1.0 - t) * (1.0 - t);
        double lift = Math.sin(Math.min(1.0, tick / 12.0) * Math.PI * 0.5) * 1.2;
        double bob = tick > FLIGHT_TICKS ? Math.sin((tick - FLIGHT_TICKS) * 0.4) * 0.15 : 0.0;
        return start.add(dir.scale(REACH * eased)).add(0.0, lift + bob, 0.0);
    }

    /** The unit direction from {@code from} to {@code to}; straight down or up when the target is right below or above. */
    public static Vec3 direction(Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        if (d.lengthSqr() < 1.0e-6) {
            return new Vec3(0.0, 1.0, 0.0);
        }
        return d.normalize();
    }

    /** Throws the mote for {@code player}, if they are in Aetheria and anything is left to find. True if one flew. */
    public static boolean throwFor(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || !AetheriaWorld.is(level)) {
            return false;
        }
        long now = level.getGameTime();
        Long last = LAST.get(player.getUUID());
        if (last != null && now - last < COOLDOWN_TICKS) {
            return false;
        }
        LAST.put(player.getUUID(), now);
        BlockPos from = player.blockPosition();
        List<Candidate> candidates = new ArrayList<>();
        add(candidates, level, player, from, GuardianTypes.COLOSSUS, true);
        add(candidates, level, player, from, GuardianTypes.LEVIATHAN, LayerAttunement.has(player, Layer.DRIFT));
        add(candidates, level, player, from, GuardianTypes.UNSUNG, LayerAttunement.has(player, Layer.DEEP));
        if (LayerAttunement.hasSanctum(player)) {
            candidates.add(new Candidate("sanctum", SanctumArena.THRONE, Layer.at(SanctumArena.THRONE.getY()), SANCTUM_RANK, true,
                    GuardianRewards.hasAdvancement(player, Codices.BREACH_SEALED)));
        }
        Optional<Candidate> pick = choose(Layer.at(player.getY()), from, candidates);
        if (pick.isEmpty()) {
            lastTarget = null;
            player.displayClientMessage(Component.translatable("cosmicbreach.codex.mote.none"), true);
            return false;
        }
        lastTarget = pick.get();
        Vec3 start = player.getEyePosition().add(player.getLookAngle().scale(0.8));
        Vec3 dir = direction(start, Vec3.atCenterOf(pick.get().centre()));
        LIVE.add(new Mote(level, player.getUUID(), start, dir, now));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), Astrolabes.STAR_PLACE.get(), SoundSource.PLAYERS, 0.7f, 1.6f);
        player.displayClientMessage(Component.translatable("cosmicbreach.codex.mote." + pick.get().name(), heading(dir)), true);
        return true;
    }

    private static void add(List<Candidate> out, ServerLevel level, ServerPlayer player, BlockPos from, GuardianType type,
            boolean reachable) {
        if (!reachable) {
            return;
        }
        boolean defeated = GuardianRewards.killedBefore(player, type);
        if (defeated) {
            return;
        }
        GuardianLairs.nearest(level, from, type)
                .ifPresent(centre -> out.add(new Candidate(type.name(), centre, homeLayer(type), rank(type), true, false)));
    }

    /** "north-east, and far below": the way the mote flies, for the message under the hotbar. */
    static Component heading(Vec3 dir) {
        double yaw = Math.toDegrees(Math.atan2(dir.x, -dir.z));
        String[] names = {"north", "north_east", "east", "south_east", "south", "south_west", "west", "north_west"};
        int i = (int) Math.floorMod(Math.round(yaw / 45.0), 8);
        String vertical = dir.y < -0.35 ? "below" : dir.y > 0.35 ? "above" : "level";
        return Component.translatable("cosmicbreach.codex.mote.way." + vertical, Component.translatable("cosmicbreach.codex.dir." + names[i]));
    }

    /** Moves every mote one tick: particles along the way, a soft chime and a burst where it fades. */
    public static void tick(ServerLevel level) {
        if (LIVE.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        for (Iterator<Mote> it = LIVE.iterator(); it.hasNext(); ) {
            Mote m = it.next();
            if (m.level() != level) {
                continue;
            }
            int age = (int) (now - m.born());
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(m.owner());
            if (age > LIFE_TICKS || owner == null || owner.level() != level) {
                if (owner != null && owner.level() == level) {
                    Vec3 end = position(m.start(), m.dir(), LIFE_TICKS);
                    level.sendParticles(owner, ModParticles.STAR_GLINT.get(), true, end.x, end.y, end.z, 12, 0.3, 0.3, 0.3, 0.02);
                    level.playSound(null, end.x, end.y, end.z, StructureRegistry.RECEPTOR_LIT.get(), SoundSource.PLAYERS, 0.5f, 1.8f);
                }
                it.remove();
                continue;
            }
            Vec3 p = position(m.start(), m.dir(), age);
            Vec3 q = position(m.start(), m.dir(), Math.max(0, age - 1));
            // everyone near sees it (it is a light in the sky), the owner from farther
            for (ServerPlayer viewer : level.players()) {
                boolean mine = viewer == owner;
                if (!mine && viewer.distanceToSqr(p) > 48 * 48) {
                    continue;
                }
                level.sendParticles(viewer, ModParticles.STAR_GLINT.get(), true, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
                level.sendParticles(viewer, ParticleTypes.END_ROD, true, (p.x + q.x) * 0.5, (p.y + q.y) * 0.5, (p.z + q.z) * 0.5,
                        2, 0.05, 0.05, 0.05, 0.005);
            }
        }
    }

    public static void forget(UUID player) {
        LAST.remove(player);
        LIVE.removeIf(m -> m.owner().equals(player));
    }

    public static void reset() {
        LIVE.clear();
        LAST.clear();
        lastTarget = null;
    }

    /** What the last mote flew toward, or null (for tests). */
    public static Candidate lastTarget() {
        return lastTarget;
    }

    /** Motes in flight (for tests). */
    public static int live() {
        return LIVE.size();
    }
}
