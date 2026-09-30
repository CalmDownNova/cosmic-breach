package com.cosmicbreach.structure.array;

import com.cosmicbreach.combat.data.HitShape;
import com.cosmicbreach.structure.StructureRegistry;
import com.cosmicbreach.structure.lens.Lens;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Every loaded Lens Array, per level (both sides), and the rules players meet at one (GDD 6.2):
 *
 * <ul>
 *   <li>Use a mirror or splitter to turn it clockwise, sneak-use to turn it back (with anything in hand: a
 *       combat weapon's ability waits, these blocks are in {@code #cosmicbreach:ability_passthrough}).</li>
 *   <li>Punch a loose mirror or filter (bare-handed or with any non-weapon) to lift it; use it on an empty
 *       pedestal of the same room to set it down, turned as it was lifted.</li>
 *   <li>A strike of 20 Impact or more knocks an Umbral block one tile away from the striker.</li>
 *   <li>Puzzle blocks can't be broken (creative players may, sneaking) and nothing can be placed in a room's
 *       grid (creative players excepted).</li>
 *   <li>A carried piece thrown away, dropped on death, or taken out of range goes back on its grid.</li>
 * </ul>
 */
public final class LensArrays {
    /** The least Impact that knocks an Umbral block (GDD 6.2). */
    public static final double PUSH_IMPACT = 20.0;
    private static final Map<Level, Set<LensCoreBlockEntity>> BY_LEVEL = new HashMap<>();
    /** Pedestals each strike already moved an Umbral block from or to: "playerUuid:serial" to cells' positions. */
    private static final Map<String, Set<BlockPos>> STRUCK = new HashMap<>();
    private static final Map<String, Long> STRUCK_AT = new HashMap<>();

    private LensArrays() {
    }

    static synchronized void add(LensCoreBlockEntity core) {
        if (core.getLevel() != null) {
            BY_LEVEL.computeIfAbsent(core.getLevel(), l -> new LinkedHashSet<>()).add(core);
        }
    }

    static synchronized void remove(LensCoreBlockEntity core) {
        if (core.getLevel() != null) {
            Set<LensCoreBlockEntity> set = BY_LEVEL.get(core.getLevel());
            if (set != null) {
                set.remove(core);
            }
        }
    }

    /** Every loaded array in {@code level}. */
    public static synchronized List<LensCoreBlockEntity> all(Level level) {
        Set<LensCoreBlockEntity> set = BY_LEVEL.get(level);
        return set == null ? List.of() : new ArrayList<>(set);
    }

    /** The array whose room holds {@code pos}, or null. */
    public static @Nullable LensCoreBlockEntity at(Level level, BlockPos pos) {
        for (LensCoreBlockEntity core : all(level)) {
            if (!core.isRemoved() && core.protects(pos)) {
                return core;
            }
        }
        return null;
    }

    /** Arrays whose core is within {@code radius} of {@code pos}, nearest first. */
    public static List<LensCoreBlockEntity> near(Level level, Vec3 pos, double radius) {
        List<LensCoreBlockEntity> out = new ArrayList<>();
        for (LensCoreBlockEntity core : all(level)) {
            if (!core.isRemoved() && Vec3.atCenterOf(core.getBlockPos()).distanceToSqr(pos) <= radius * radius) {
                out.add(core);
            }
        }
        out.sort(java.util.Comparator.comparingDouble(c -> Vec3.atCenterOf(c.getBlockPos()).distanceToSqr(pos)));
        return out;
    }

    /** The nearest array to {@code player} within 32 blocks (for the Codex), or null. */
    public static @Nullable LensCoreBlockEntity nearest(Player player) {
        List<LensCoreBlockEntity> list = near(player.level(), player.position(), 32);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * The Codex's hint (GDD 6.2): lights one correct mirror in the nearest unsolved array now, whatever its timer.
     * False if there is none, or nothing left to hint.
     */
    public static boolean hint(ServerPlayer player) {
        LensCoreBlockEntity core = nearest(player);
        return core != null && core.getLevel() instanceof ServerLevel level && core.showHint(level);
    }

    /** Told when an array is solved (after its vault unseals): the Sanctum lights an Eclipse Lock this way. */
    public interface SolveListener {
        void solved(ServerLevel level, LensCoreBlockEntity core);
    }

    private static final List<SolveListener> SOLVE_LISTENERS = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void onSolved(SolveListener listener) {
        SOLVE_LISTENERS.add(listener);
    }

    static void solved(ServerLevel level, LensCoreBlockEntity core) {
        for (SolveListener l : SOLVE_LISTENERS) {
            l.solved(level, core);
        }
    }

    // ------------------------------------------------------------------ events

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (!PuzzleBlock.isPuzzle(state)) {
            return;
        }
        Player player = event.getEntity();
        ItemStack held = player.getMainHandItem();
        Block block = state.getBlock();
        boolean acted = false;
        if (event.getHand() == InteractionHand.MAIN_HAND && (block instanceof MirrorBlock || block instanceof SplitterBlock)) {
            if (level instanceof ServerLevel server) {
                LensCoreBlockEntity core = at(server, pos);
                if (core != null) {
                    core.rotate(server, pos, player.isSecondaryUseActive());
                }
            }
            acted = true;
        } else if (event.getHand() == InteractionHand.MAIN_HAND && block instanceof PedestalBlock && held.getItem() instanceof LensPieceItem) {
            if (level instanceof ServerLevel server) {
                LensCoreBlockEntity core = at(server, pos);
                if (core == null || !core.place(server, player, pos, held)) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable("cosmicbreach.lens.not_here"), true);
                }
            }
            acted = true;
        }
        event.setCanceled(true);
        event.setCancellationResult(acted ? InteractionResult.SUCCESS : InteractionResult.CONSUME);
    }

    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (!PuzzleBlock.isPuzzle(state)) {
            return;
        }
        Player player = event.getEntity();
        if (player.isCreative() && player.isShiftKeyDown()) {
            return;
        }
        event.setCanceled(true);
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp
                && event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START) {
            LensCoreBlockEntity core = at(server, pos);
            if (core != null && Lens.loose(LensCoreBlockEntity.encode(state))) {
                core.pickUp(server, sp, pos);
            }
        }
    }

    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof Level level) || (event.getEntity() instanceof Player p && p.isCreative())) {
            return;
        }
        if (PuzzleBlock.isPuzzle(event.getPlacedBlock())) {
            return;
        }
        if (at(level, event.getPos()) != null) {
            event.setCanceled(true);
        }
    }

    public static void onBreak(BlockEvent.BreakEvent event) {
        if (PuzzleBlock.isPuzzle(event.getState()) && !(event.getPlayer().isCreative() && event.getPlayer().isShiftKeyDown())) {
            event.setCanceled(true);
        }
    }

    public static void onToss(ItemTossEvent event) {
        ItemStack stack = event.getEntity().getItem();
        LensPiece piece = stack.get(StructureRegistry.LENS_PIECE.get());
        if (piece != null && event.getPlayer() instanceof ServerPlayer player) {
            returnPiece(player.serverLevel(), piece);
            event.setCanceled(true);
        }
    }

    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        event.getDrops().removeIf(entity -> {
            LensPiece piece = entity.getItem().get(StructureRegistry.LENS_PIECE.get());
            if (piece == null) {
                return false;
            }
            returnPiece(level, piece);
            return true;
        });
    }

    /** Puts a carried piece back on its grid, if its array is loaded. */
    public static void returnPiece(ServerLevel level, LensPiece piece) {
        ServerLevel home = level.getServer().getLevel(piece.dimension());
        if (home != null && home.isLoaded(piece.core()) && home.getBlockEntity(piece.core()) instanceof LensCoreBlockEntity core) {
            core.returnPiece(home, piece.token());
        }
    }

    public static synchronized void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) {
            BY_LEVEL.remove(level);
        }
    }

    public static synchronized void reset() {
        BY_LEVEL.clear();
        STRUCK.clear();
        STRUCK_AT.clear();
    }

    // ------------------------------------------------------------------ strikes knock Umbral blocks

    /**
     * A strike ({@link com.cosmicbreach.combat.server.BlockStrikes}): with 20+ Impact, every Umbral block its shape
     * touches in an unsolved array moves one tile, away from the striker along the grid. Each strike moves a
     * block once, however many of its active ticks touch it.
     */
    public static void onStrike(ServerPlayer player, HitShape shape, Vec3 origin, float yaw, double impact, int serial) {
        if (impact < PUSH_IMPACT) {
            return;
        }
        ServerLevel level = player.serverLevel();
        String key = player.getUUID() + ":" + serial;
        long now = level.getGameTime();
        synchronized (LensArrays.class) {
            STRUCK_AT.entrySet().removeIf(e -> {
                if (now - e.getValue() > 100) {
                    STRUCK.remove(e.getKey());
                    return true;
                }
                return false;
            });
        }
        for (LensCoreBlockEntity core : near(level, player.position(), 32)) {
            if (core.phase() != LensCoreBlockEntity.ACTIVE) {
                continue;
            }
            List<BlockPos> hit = new ArrayList<>();
            int n = core.size();
            for (int i = 0; i < n * n; i++) {
                BlockPos pos = core.cellPos(i);
                if (level.getBlockState(pos).getBlock() instanceof LensUmbralBlock && shape.hits(origin, yaw, new AABB(pos))) {
                    hit.add(pos);
                }
            }
            for (BlockPos pos : hit) {
                Set<BlockPos> done;
                synchronized (LensArrays.class) {
                    done = STRUCK.computeIfAbsent(key, k -> new HashSet<>());
                    STRUCK_AT.put(key, now);
                }
                if (done.contains(pos)) {
                    continue;
                }
                double dx = pos.getX() + 0.5 - player.getX();
                double dz = pos.getZ() + 0.5 - player.getZ();
                int heading = Math.abs(dx) >= Math.abs(dz) ? (dx >= 0 ? Lens.EAST : Lens.WEST) : (dz >= 0 ? Lens.SOUTH : Lens.NORTH);
                done.add(pos);
                if (core.push(level, pos, heading)) {
                    done.add(pos.relative(PuzzleBlock.direction(heading), LensCoreBlockEntity.SPACING));
                }
            }
        }
    }
}
