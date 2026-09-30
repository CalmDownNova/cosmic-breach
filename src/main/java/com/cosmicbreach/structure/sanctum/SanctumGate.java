package com.cosmicbreach.structure.sanctum;

import com.cosmicbreach.progression.AttunementXp;
import com.cosmicbreach.progression.XpSource;
import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.LayerAttunement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * The Gate at work (GDD 6.1, the rules in {@link GateRule}): each tick it looks at the players near it, opens its doors
 * for anyone it lets through, admits an arriving attuned player's party, tells the others it doesn't know their song,
 * keeps each client's own pass current (the veil's collision on the client), and pays Structure Found once to each
 * player who reaches the halls.
 */
public final class SanctumGate {
    /** Players this close to the Gate are watched (and their clients told whether they pass). */
    public static final double WATCH = 32.0;
    private static final Map<ServerLevel, GateRule.Gate> GATES = new WeakHashMap<>();
    private static final Map<UUID, Boolean> SENT = new HashMap<>();
    private static int openings;
    private static int refusals;
    private static int admissions;

    private SanctumGate() {
    }

    /** True if the Gate lets {@code player} through: attuned to the Sanctum, or admitted with a party. */
    public static boolean passes(ServerPlayer player) {
        if (LayerAttunement.hasSanctum(player)) {
            return true;
        }
        ServerLevel aetheria = player.server.getLevel(AetheriaWorld.LEVEL);
        return aetheria != null && SanctumData.get(aetheria).admitted(player.getUUID());
    }

    public static void tick(ServerLevel level) {
        SanctumLayout layout = Sanctums.layout(level);
        double[] g = layout.gateCentre();
        List<ServerPlayer> around = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (!p.isSpectator() && p.distanceToSqr(g[0], g[1], g[2]) <= WATCH * WATCH) {
                around.add(p);
            }
        }
        GateRule.Gate gate = GATES.computeIfAbsent(level, k -> new GateRule.Gate());
        BlockPos first = SanctumBuilder.at(layout.gateBlocks().get(0));
        if (!level.isLoaded(first)) {
            return;
        }
        BlockState state = level.getBlockState(first);
        if (!state.is(SanctumRegistry.SANCTUM_GATE.get())) {
            return; // not built here (yet)
        }
        gate.setOpen(state.getValue(SanctumGateBlock.OPEN));
        if (around.isEmpty() && !gate.open()) {
            return;
        }
        SanctumData data = SanctumData.get(level);
        List<GateRule.Near> near = new ArrayList<>();
        List<UUID> close = new ArrayList<>();
        for (ServerPlayer p : around) {
            double d2 = p.distanceToSqr(g[0], g[1], g[2]);
            boolean attuned = LayerAttunement.hasSanctum(p);
            boolean admitted = data.admitted(p.getUUID());
            if (d2 <= GateRule.OPEN_RADIUS * GateRule.OPEN_RADIUS) {
                near.add(new GateRule.Near(p.getUUID(), attuned, admitted, pos(p)));
            }
            if (d2 <= GateRule.REFUSE_RADIUS * GateRule.REFUSE_RADIUS && !GateRule.passes(attuned, admitted)) {
                close.add(p.getUUID());
            }
        }
        Map<UUID, double[]> everyone = new LinkedHashMap<>();
        for (ServerPlayer p : level.players()) {
            everyone.put(p.getUUID(), pos(p));
        }
        GateRule.Update u = gate.tick(level.getGameTime(), near, everyone, inDoorway(level, layout), close);
        if (!u.admitted().isEmpty()) {
            welcome(level, data.admit(u.admitted()));
        }
        if (u.opened()) {
            setOpen(level, layout, true);
        } else if (u.closed()) {
            setOpen(level, layout, false);
        }
        for (UUID id : u.refused()) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p != null) {
                refusals++;
                Sanctums.subtitle(p, Component.translatable("cosmicbreach.sanctum.gate.refused"));
                level.playSound(null, BlockPos.containing(g[0], g[1], g[2]), SanctumRegistry.GATE_REFUSE.get(), SoundSource.BLOCKS, 1.2f, 1.0f);
            }
        }
        for (ServerPlayer p : around) {
            sync(p);
            if (layout.insideHalls(p.getX(), p.getY(), p.getZ()) && data.markEntered(p.getUUID())) {
                AttunementXp.award(p, XpSource.STRUCTURE_FOUND.at(XpSource.LayerTier.DEEP));
            }
        }
    }

    /**
     * An attuned player arrives at the Gate at {@code at}: everyone within 16 blocks of them is their party and is
     * admitted for good. The tick does this for each attuned player's arrival ({@link GateRule.Gate#tick}); a test calls
     * it for a stand-in opener (a fake player can't hold an advancement), so the caller vouches for the attunement.
     * Returns the party, the opener included.
     */
    public static List<UUID> arrive(ServerLevel level, UUID opener, double[] at) {
        Map<UUID, double[]> everyone = new LinkedHashMap<>();
        for (ServerPlayer p : level.players()) {
            everyone.put(p.getUUID(), pos(p));
        }
        everyone.put(opener, at);
        List<UUID> party = GateRule.party(at, everyone);
        welcome(level, SanctumData.get(level).admit(party));
        return party;
    }

    private static void welcome(ServerLevel level, List<UUID> fresh) {
        for (UUID id : fresh) {
            admissions++;
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p != null) {
                p.displayClientMessage(Component.translatable("cosmicbreach.sanctum.gate.admitted"), true);
                sync(p);
            }
        }
    }

    /** Opens or shuts every block of the Gate, with its sound and a shower of embers. */
    public static void setOpen(ServerLevel level, SanctumLayout layout, boolean open) {
        for (int[] b : layout.gateBlocks()) {
            BlockPos pos = SanctumBuilder.at(b);
            BlockState s = level.getBlockState(pos);
            if (s.is(SanctumRegistry.SANCTUM_GATE.get()) && s.getValue(SanctumGateBlock.OPEN) != open) {
                level.setBlock(pos, s.setValue(SanctumGateBlock.OPEN, open), Block.UPDATE_ALL);
            }
        }
        double[] g = layout.gateCentre();
        if (open) {
            openings++;
            level.playSound(null, BlockPos.containing(g[0], g[1], g[2]), SanctumRegistry.GATE_OPEN.get(), SoundSource.BLOCKS, 2.0f, 1.0f);
            level.sendParticles(ParticleTypes.END_ROD, g[0], g[1], g[2], 40, 1.6, 1.6, 0.4, 0.02);
        } else {
            level.playSound(null, BlockPos.containing(g[0], g[1], g[2]), SanctumRegistry.GATE_REFUSE.get(), SoundSource.BLOCKS, 0.7f, 0.8f);
        }
    }

    /** Tells a player's client whether the veil lets it through, when that changed. */
    public static void sync(ServerPlayer p) {
        boolean pass = passes(p);
        Boolean last = SENT.put(p.getUUID(), pass);
        if (last == null || last != pass) {
            SanctumNet.sendPass(p, pass);
        }
    }

    /** Sends a player's pass whatever was sent before (login, a change of dimension). */
    public static void resync(ServerPlayer p) {
        SENT.remove(p.getUUID());
        sync(p);
    }

    public static void forget(Player p) {
        SENT.remove(p.getUUID());
    }

    private static boolean inDoorway(ServerLevel level, SanctumLayout layout) {
        List<int[]> blocks = layout.gateBlocks();
        int[] a = blocks.get(0);
        int[] b = blocks.get(blocks.size() - 1);
        AABB box = new AABB(Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]), Math.max(a[0], b[0]) + 1,
                Math.max(a[1], b[1]) + 1, Math.max(a[2], b[2]) + 1).inflate(0.2);
        return !level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box).isEmpty();
    }

    private static double[] pos(Player p) {
        return new double[] {p.getX(), p.getY(), p.getZ()};
    }

    /** "openings refusals admissions" since the server started (for tests). */
    public static String counters() {
        return openings + " " + refusals + " " + admissions;
    }

    public static @Nullable GateRule.Gate gate(ServerLevel level) {
        return GATES.get(level);
    }

    public static void reset() {
        GATES.clear();
        SENT.clear();
        openings = 0;
        refusals = 0;
        admissions = 0;
    }
}
