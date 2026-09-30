package com.cosmicbreach.structure.array;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.progression.AttunementXp;
import com.cosmicbreach.progression.XpSource;
import com.cosmicbreach.structure.Guards;
import com.cosmicbreach.structure.StructureConfig;
import com.cosmicbreach.structure.StructureRegistry;
import com.cosmicbreach.structure.lens.BeamTrace;
import com.cosmicbreach.structure.lens.Lens;
import com.cosmicbreach.structure.lens.LensDifficulty;
import com.cosmicbreach.structure.lens.LensGenerator;
import com.cosmicbreach.structure.lens.LensPuzzle;
import com.cosmicbreach.structure.vault.VaultBlockEntity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A Lens Array (GDD 6.2), run from its core under the grid's middle pedestal. The structure writes the room at
 * worldgen with an empty grid; the puzzle itself is generated from the room's seed the first time a player
 * comes within {@link StructureConfig#wakeRadius} blocks (off the server thread), written into the grid and
 * walls, and the aperture opens.
 *
 * <p>The grid is the world: pieces are blocks, and every change a player makes (a turn, a loose piece lifted or
 * set down, an Umbral block knocked) calls {@link #retrace}, which reads the pedestals, traces the light
 * ({@link BeamTrace}), lights receptors, wakes the Warden Eye and sends the segments to clients, which draw
 * them like beacon beams. Nothing is traced per tick. All receptors lit for {@link #HOLD_TICKS} ticks solve it:
 * the vault opens and everyone in the room gets the puzzle's Attunement XP once.
 *
 * <p>Geometry: cell (x, z) stands at the core plus (2x - 2c, 1, 2z - 2c) with c the middle index; port p one
 * cell step outside the grid (2 blocks); the aperture {@link #ceiling} blocks over the focus.
 */
public class LensCoreBlockEntity extends BlockEntity {
    public static final int DORMANT = 0;
    public static final int ACTIVE = 1;
    public static final int SOLVED = 2;
    /** All receptors lit this long (GDD 6.2: 60 ticks) and the vault opens. */
    public static final int HOLD_TICKS = 60;
    public static final int SPACING = 2;
    /** Most Shardlings the Eye keeps alive at once. */
    public static final int EYE_CAP = 4;

    // set by the structure
    private long seed;
    private String difficulty = LensDifficulty.EASY_5.name();
    private int tier = 1;
    private int n = 5;
    private int ceiling = 7;
    private @Nullable BlockPos vault;

    // the puzzle
    private int phase = DORMANT;
    private int[] ports = new int[0];
    private int[] start = new int[0];
    private int[] solution = new int[0];
    private int source = -1;
    private int minMoves = -1;
    private long activatedAt;
    private long holdStart = -1;
    private long hintAt = -1;
    private int hintCell = -1;
    /** The hint's wait is over: the Codex's Lens Array page offers it until someone takes it ({@link #takeHint}). */
    private boolean hintOffered;
    private int[] segments = new int[0];
    private int litMask;
    private long eyeReadyAt;
    private final List<UUID> summoned = new ArrayList<>();
    private final Set<UUID> solvedFor = new HashSet<>();
    /** Loose pieces out of the grid, by token: {piece, the pedestal it was lifted from}. */
    private final Map<Long, int[]> out = new LinkedHashMap<>();

    // not saved
    private boolean generating;
    private long wakeRequestedAt = -1;
    private long generationNanos;
    /** Recent retraces: {nanoseconds in the trace itself, nanoseconds for the whole retrace with its block updates}. */
    private final java.util.ArrayDeque<long[]> traceLog = new java.util.ArrayDeque<>();
    private long lastTraceNanos;
    private int traces;

    public LensCoreBlockEntity(BlockPos pos, BlockState state) {
        super(StructureRegistry.LENS_CORE_ENTITY.get(), pos, state);
    }

    /**
     * Sets the room up at worldgen: its seed, difficulty, layer tier (1 Reach, 2 Drift, 3 Deep: the XP it pays),
     * ceiling height (pedestal row to the aperture) and the vault it opens (or null).
     */
    public void configure(long seed, LensDifficulty difficulty, int tier, int ceiling, @Nullable BlockPos vault) {
        this.seed = seed;
        this.difficulty = difficulty.name();
        this.n = difficulty.size();
        this.tier = tier;
        this.ceiling = ceiling;
        this.vault = vault == null ? null : vault.immutable();
        setChanged();
    }

    // ------------------------------------------------------------------ geometry

    public int size() {
        return n;
    }

    public int phase() {
        return phase;
    }

    public int ceiling() {
        return ceiling;
    }

    public int[] ports() {
        return ports;
    }

    public int[] segments() {
        return segments;
    }

    public int litMask() {
        return litMask;
    }

    public int source() {
        return source;
    }

    public int hintCell() {
        return hintCell;
    }

    /** The solution's piece at the hinted pedestal (for the ghost the client draws), or 0. */
    public int hintPiece() {
        return hintCell >= 0 && hintCell < solution.length ? solution[hintCell] : 0;
    }

    public long holdStart() {
        return holdStart;
    }

    public int minMoves() {
        return minMoves;
    }

    /** Game time the room was asked to wake (a player came near, or a command), or -1. Not saved. */
    public long wakeRequestedAt() {
        return wakeRequestedAt;
    }

    /** Game time the room woke (its puzzle written in). */
    public long activatedAt() {
        return activatedAt;
    }

    /** How long generating this room's puzzle took, off the server thread. Not saved. */
    public long generationNanos() {
        return generationNanos;
    }

    /** Recent retraces, oldest first: {the trace alone, the whole retrace}, in nanoseconds. Not saved. */
    public List<long[]> traceLog() {
        return new ArrayList<>(traceLog);
    }

    public long lastTraceNanos() {
        return lastTraceNanos;
    }

    public int traces() {
        return traces;
    }

    public @Nullable BlockPos vault() {
        return vault;
    }

    public String difficulty() {
        return difficulty;
    }

    /** The pedestal of cell {@code i}. */
    public BlockPos cellPos(int i) {
        int c = (n - 1) / 2;
        return worldPosition.offset(SPACING * (i % n - c), 1, SPACING * (i / n - c));
    }

    /** Where port {@code p}'s socket stands. */
    public BlockPos portPos(int p) {
        int c = (n - 1) / 2;
        return worldPosition.offset(SPACING * (Lens.portX(n, p) - c), 1, SPACING * (Lens.portZ(n, p) - c));
    }

    /** A grid point in world coordinates at beam height (x and z in grid units, ports at -1 and n). */
    public Vec3 gridPoint(double gx, double gz) {
        int c = (n - 1) / 2;
        return new Vec3(worldPosition.getX() + 0.5 + SPACING * (gx - c), worldPosition.getY() + 1 + PuzzleBlock.BEAM_HEIGHT,
                worldPosition.getZ() + 0.5 + SPACING * (gz - c));
    }

    /** The aperture, over the focus (null before the room wakes). */
    public @Nullable BlockPos aperturePos() {
        return source < 0 ? null : cellPos(source).above(ceiling);
    }

    /** The cell whose pedestal is at {@code pos}, or -1. */
    public int cellAt(BlockPos pos) {
        int c = (n - 1) / 2;
        int dx = pos.getX() - worldPosition.getX() + SPACING * c;
        int dz = pos.getZ() - worldPosition.getZ() + SPACING * c;
        if (pos.getY() != worldPosition.getY() + 1 || dx < 0 || dz < 0 || dx % SPACING != 0 || dz % SPACING != 0) {
            return -1;
        }
        int x = dx / SPACING;
        int z = dz / SPACING;
        return x < n && z < n ? z * n + x : -1;
    }

    /** The room's protected space: grid, sockets and the air up to the ceiling. Nothing may be placed in it. */
    public AABB protectedBox() {
        int c = (n - 1) / 2;
        int reach = SPACING * (c + 1);
        return new AABB(worldPosition.getX() - reach, worldPosition.getY() + 1, worldPosition.getZ() - reach,
                worldPosition.getX() + reach + 1, worldPosition.getY() + 1 + ceiling, worldPosition.getZ() + reach + 1);
    }

    public boolean protects(BlockPos pos) {
        return protectedBox().contains(Vec3.atCenterOf(pos));
    }

    // ------------------------------------------------------------------ ticking

    public static void serverTick(Level level, BlockPos pos, BlockState state, LensCoreBlockEntity core) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        long now = level.getGameTime();
        boolean second = (now + pos.asLong()) % 20 == 0;
        if (core.phase == DORMANT) {
            if (second && !core.generating && level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                    StructureConfig.wakeRadius(), false) != null) {
                core.wake(server);
            }
            return;
        }
        if (core.phase != ACTIVE) {
            return;
        }
        if (core.holdStart >= 0 && now - core.holdStart >= HOLD_TICKS) {
            core.solve(server);
            return;
        }
        if (core.hintAt >= 0 && now >= core.hintAt && core.hintCell < 0 && !core.hintOffered) {
            core.offerHint(server);
        }
        if (second) {
            core.recoverPieces(server);
            if (now >= core.eyeReadyAt) {
                core.closeEye(server);
            }
        }
    }

    /** Generates the puzzle from the room's seed, off the server thread, then writes it in. */
    public void wake(ServerLevel level) {
        if (phase != DORMANT || generating) {
            return;
        }
        generating = true;
        wakeRequestedAt = level.getGameTime();
        LensDifficulty diff = LensDifficulty.byName(difficulty);
        long s = seed;
        CompletableFuture.supplyAsync(() -> {
                    long t0 = System.nanoTime();
                    LensPuzzle p = LensGenerator.generate(s, diff);
                    generationNanos = System.nanoTime() - t0;
                    return p;
                }, Util.backgroundExecutor())
                .whenComplete((puzzle, error) -> level.getServer().execute(() -> {
                    generating = false;
                    if (isRemoved() || level.getBlockEntity(worldPosition) != this) {
                        return;
                    }
                    if (error != null) {
                        CosmicBreach.LOGGER.error("[cosmicbreach] a puzzle room at {} failed to generate", worldPosition, error);
                        return;
                    }
                    apply(level, puzzle);
                }));
    }

    /** Writes {@code puzzle} into the room and opens the aperture. */
    public void apply(ServerLevel level, LensPuzzle puzzle) {
        ports = puzzle.ports().clone();
        start = puzzle.start().clone();
        solution = puzzle.solution().clone();
        minMoves = puzzle.minMoves();
        source = puzzle.sourceCell();
        out.clear();
        writeCells(level, start);
        for (int p = 0; p < ports.length; p++) {
            level.setBlock(portPos(p), portState(p, false), Block.UPDATE_ALL);
        }
        BlockPos aperture = aperturePos();
        if (aperture != null) {
            level.setBlock(aperture, StructureRegistry.SUN_APERTURE.get().defaultBlockState().setValue(ApertureBlock.OPEN, true),
                    Block.UPDATE_ALL);
            level.playSound(null, aperture, StructureRegistry.APERTURE_OPEN.get(), SoundSource.BLOCKS, 1.6f, 1.0f);
        }
        phase = ACTIVE;
        activatedAt = level.getGameTime();
        hintAt = activatedAt + StructureConfig.hintTicks();
        hintCell = -1;
        hintOffered = false;
        holdStart = -1;
        CosmicBreach.LOGGER.debug("[cosmicbreach] Lens Array at {} woke: {} ({} moves at least, {} template)", worldPosition,
                difficulty, minMoves, puzzle.template() ? "a" : "no");
        retrace(level);
    }

    private BlockState portState(int p, boolean lit) {
        int port = ports[p];
        Direction facing = PuzzleBlock.direction(Lens.opposite(Lens.portHeading(n, p)));
        if (port == Lens.PORT_EYE) {
            return StructureRegistry.WARDEN_EYE.get().defaultBlockState().setValue(WardenEyeBlock.FACING, facing);
        }
        if (Lens.isReceptor(port)) {
            return StructureRegistry.LENS_RECEPTOR.get().defaultBlockState().setValue(ReceptorBlock.FACING, facing)
                    .setValue(ReceptorBlock.COLOR, Tint.of(Lens.receptorColor(port))).setValue(ReceptorBlock.LIT, lit);
        }
        return StructureRegistry.LENS_SOCKET.get().defaultBlockState();
    }

    private void writeCells(ServerLevel level, int[] cells) {
        for (int i = 0; i < cells.length; i++) {
            BlockState want = decode(cells[i]);
            if (level.getBlockState(cellPos(i)) != want) {
                level.setBlock(cellPos(i), want, Block.UPDATE_ALL);
            }
        }
    }

    // ------------------------------------------------------------------ the grid as blocks

    /** A pedestal's block as the puzzle core's cell. Anything that isn't a piece is an empty pedestal. */
    public static int encode(BlockState s) {
        Block b = s.getBlock();
        if (b instanceof MirrorBlock) {
            return Lens.mirror(s.getValue(MirrorBlock.TURN), s.getValue(MirrorBlock.LOOSE));
        }
        if (b instanceof SplitterBlock) {
            return Lens.splitter(PuzzleBlock.heading(s.getValue(SplitterBlock.FACING)));
        }
        if (b instanceof FilterBlock) {
            return Lens.filter(s.getValue(FilterBlock.COLOR).color, s.getValue(FilterBlock.LOOSE));
        }
        if (b instanceof LensUmbralBlock) {
            return Lens.umbral();
        }
        if (b instanceof FocusBlock) {
            return Lens.source(PuzzleBlock.heading(s.getValue(FocusBlock.FACING)));
        }
        return Lens.EMPTY;
    }

    /** The block for a puzzle core cell. */
    public static BlockState decode(int cell) {
        return switch (Lens.kind(cell)) {
            case Lens.MIRROR -> StructureRegistry.LENS_MIRROR.get().defaultBlockState().setValue(MirrorBlock.TURN, Lens.turn(cell))
                    .setValue(MirrorBlock.LOOSE, Lens.loose(cell));
            case Lens.SPLITTER -> StructureRegistry.LENS_SPLITTER.get().defaultBlockState()
                    .setValue(SplitterBlock.FACING, PuzzleBlock.direction(Lens.turn(cell)));
            case Lens.FILTER -> StructureRegistry.LENS_FILTER.get().defaultBlockState()
                    .setValue(FilterBlock.COLOR, Tint.of(Lens.color(cell) == Lens.WHITE ? Lens.GOLD : Lens.color(cell)))
                    .setValue(FilterBlock.LOOSE, Lens.loose(cell));
            case Lens.UMBRAL -> StructureRegistry.LENS_UMBRAL.get().defaultBlockState();
            case Lens.SOURCE -> StructureRegistry.LENS_FOCUS.get().defaultBlockState()
                    .setValue(FocusBlock.FACING, PuzzleBlock.direction(Lens.turn(cell)));
            default -> StructureRegistry.LENS_PEDESTAL.get().defaultBlockState();
        };
    }

    public int[] readCells() {
        int[] cells = new int[n * n];
        if (level == null) {
            return cells;
        }
        for (int i = 0; i < cells.length; i++) {
            cells[i] = encode(level.getBlockState(cellPos(i)));
        }
        return cells;
    }

    // ------------------------------------------------------------------ the beam

    /**
     * Traces the light through the grid as it stands and acts on it: receptors lit or dark, the hold timer, the
     * Warden Eye, the segments for clients. Called only when something changed.
     */
    public void retrace(ServerLevel level) {
        if (phase == DORMANT || ports.length == 0) {
            return;
        }
        long t0 = System.nanoTime();
        int[] cells = readCells();
        long t1 = System.nanoTime();
        BeamTrace.Result r = BeamTrace.trace(n, cells, ports);
        long traceOnly = System.nanoTime() - t1;
        int before = litMask;
        litMask = r.litMask();
        segments = java.util.Arrays.copyOf(r.segments(), r.count() * 6);
        for (int p = 0; p < ports.length; p++) {
            if (!Lens.isReceptor(ports[p])) {
                continue;
            }
            boolean lit = (litMask & 1 << p) != 0;
            BlockPos at = portPos(p);
            BlockState was = level.getBlockState(at);
            if (was.getBlock() instanceof ReceptorBlock && was.getValue(ReceptorBlock.LIT) != lit) {
                level.setBlock(at, was.setValue(ReceptorBlock.LIT, lit), Block.UPDATE_ALL);
            }
            if (lit && (before & 1 << p) == 0) {
                level.playSound(null, at, StructureRegistry.RECEPTOR_LIT.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
            }
        }
        lastTraceNanos = System.nanoTime() - t0;
        traces++;
        traceLog.addLast(new long[] {traceOnly, lastTraceNanos});
        if (traceLog.size() > 256) {
            traceLog.removeFirst();
        }
        if (phase == ACTIVE) {
            boolean all = r.solves(ports);
            if (all && holdStart < 0) {
                holdStart = level.getGameTime();
            } else if (!all) {
                holdStart = -1;
            }
            if (r.eye()) {
                wakeEye(level);
            }
            if (hintCell >= 0 && cells[hintCell] == solution[hintCell]) {
                // the hinted mirror is set true: the next hint comes after another wait
                hintCell = -1;
                hintAt = level.getGameTime() + StructureConfig.hintTicks();
            }
        }
        sync();
    }

    // ------------------------------------------------------------------ what players do

    /** Turns the mirror or splitter at {@code pos} one step clockwise ({@code back}: counter-clockwise). */
    public boolean rotate(ServerLevel level, BlockPos pos, boolean back) {
        if (phase != ACTIVE || cellAt(pos) < 0) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        BlockState turned;
        if (state.getBlock() instanceof MirrorBlock) {
            turned = state.setValue(MirrorBlock.TURN, (state.getValue(MirrorBlock.TURN) + (back ? 3 : 1)) & 3);
        } else if (state.getBlock() instanceof SplitterBlock) {
            Direction f = state.getValue(SplitterBlock.FACING);
            turned = state.setValue(SplitterBlock.FACING, back ? f.getCounterClockWise() : f.getClockWise());
        } else {
            return false;
        }
        level.setBlock(pos, turned, Block.UPDATE_ALL);
        level.playSound(null, pos, StructureRegistry.MIRROR_TURN.get(), SoundSource.BLOCKS, 0.8f, back ? 0.9f : 1.1f);
        retrace(level);
        return true;
    }

    /** Lifts the loose piece at {@code pos} into {@code player}'s hands. */
    public boolean pickUp(ServerLevel level, ServerPlayer player, BlockPos pos) {
        int cell = cellAt(pos);
        if (phase != ACTIVE || cell < 0) {
            return false;
        }
        int piece = encode(level.getBlockState(pos));
        LensPiece.Kind kind = LensPiece.Kind.of(piece);
        if (kind == null) {
            return false;
        }
        long token = level.random.nextLong();
        ItemStack stack = new ItemStack(kind.item());
        stack.set(StructureRegistry.LENS_PIECE.get(), new LensPiece(worldPosition, level.dimension(), piece, token));
        if (!carry(player, stack)) {
            player.displayClientMessage(Component.translatable("cosmicbreach.lens.hands_full"), true);
            return false;
        }
        out.put(token, new int[] {piece, cell});
        level.setBlock(pos, decode(Lens.EMPTY), Block.UPDATE_ALL);
        level.playSound(null, pos, StructureRegistry.MIRROR_TURN.get(), SoundSource.BLOCKS, 0.8f, 0.7f);
        retrace(level);
        return true;
    }

    /**
     * Puts a lifted piece where it can be set down at once: in the hand if it is empty, else the first empty hotbar
     * slot, else anywhere in the inventory (a full hotbar used to send it to the inventory, out of reach of a
     * hotbar key). False if there is no room at all.
     */
    static boolean carry(ServerPlayer player, ItemStack stack) {
        net.minecraft.world.entity.player.Inventory inv = player.getInventory();
        int slot = inv.getItem(inv.selected).isEmpty() ? inv.selected : -1;
        for (int i = 0; slot < 0 && i < net.minecraft.world.entity.player.Inventory.getSelectionSize(); i++) {
            if (inv.getItem(i).isEmpty()) {
                slot = i;
            }
        }
        if (slot >= 0) {
            inv.setItem(slot, stack);
            inv.setChanged();
            return true;
        }
        return inv.add(stack);
    }

    /** Sets the carried piece {@code stack} on the empty pedestal at {@code pos}. */
    public boolean place(ServerLevel level, Player player, BlockPos pos, ItemStack stack) {
        LensPiece piece = stack.get(StructureRegistry.LENS_PIECE.get());
        int cell = cellAt(pos);
        if (phase != ACTIVE || cell < 0 || piece == null || !owns(piece)
                || !(level.getBlockState(pos).getBlock() instanceof PedestalBlock)) {
            return false;
        }
        out.remove(piece.token());
        level.setBlock(pos, decode(piece.piece()), Block.UPDATE_ALL);
        stack.shrink(1);
        level.playSound(null, pos, StructureRegistry.MIRROR_TURN.get(), SoundSource.BLOCKS, 0.8f, 1.3f);
        retrace(level);
        return true;
    }

    /** True if {@code piece} was lifted from this array and hasn't been returned yet. */
    public boolean owns(LensPiece piece) {
        return piece.core().equals(worldPosition) && level != null && piece.dimension() == level.dimension()
                && out.containsKey(piece.token());
    }

    /** Puts a carried piece back on a pedestal (where it was lifted, or the nearest empty one). */
    public void returnPiece(ServerLevel level, long token) {
        int[] entry = out.remove(token);
        if (entry == null) {
            return;
        }
        int home = entry[1];
        int best = -1;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < n * n; i++) {
            if (!(level.getBlockState(cellPos(i)).getBlock() instanceof PedestalBlock)) {
                continue;
            }
            int d = Math.abs(i % n - home % n) + Math.abs(i / n - home / n);
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        if (best >= 0) {
            level.setBlock(cellPos(best), decode(entry[0]), Block.UPDATE_ALL);
            level.playSound(null, cellPos(best), StructureRegistry.MIRROR_TURN.get(), SoundSource.BLOCKS, 0.6f, 1.5f);
        }
        retrace(level);
    }

    /** Knocks the Umbral block at {@code pos} one tile toward {@code heading}, if an empty pedestal is there. */
    public boolean push(ServerLevel level, BlockPos pos, int heading) {
        int cell = cellAt(pos);
        if (phase != ACTIVE || cell < 0 || !(level.getBlockState(pos).getBlock() instanceof LensUmbralBlock)) {
            return false;
        }
        int x = cell % n + Lens.DX[heading];
        int z = cell / n + Lens.DZ[heading];
        if (x < 0 || x >= n || z < 0 || z >= n) {
            level.playSound(null, pos, StructureRegistry.UMBRAL_PUSH.get(), SoundSource.BLOCKS, 0.5f, 0.6f);
            return false;
        }
        BlockPos to = cellPos(z * n + x);
        if (!(level.getBlockState(to).getBlock() instanceof PedestalBlock)) {
            level.playSound(null, pos, StructureRegistry.UMBRAL_PUSH.get(), SoundSource.BLOCKS, 0.5f, 0.6f);
            return false;
        }
        level.setBlock(to, decode(Lens.umbral()), Block.UPDATE_ALL);
        level.setBlock(pos, decode(Lens.EMPTY), Block.UPDATE_ALL);
        level.playSound(null, to, StructureRegistry.UMBRAL_PUSH.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
        retrace(level);
        return true;
    }

    /** Pieces carried off that nobody near is holding any more go back on the grid. */
    private void recoverPieces(ServerLevel level) {
        if (out.isEmpty()) {
            return;
        }
        for (Long token : new ArrayList<>(out.keySet())) {
            boolean held = false;
            for (Player p : level.players()) {
                if (p.distanceToSqr(Vec3.atCenterOf(worldPosition)) > StructureConfig.CARRY_RANGE * StructureConfig.CARRY_RANGE) {
                    continue;
                }
                if (carries(p, token)) {
                    held = true;
                    break;
                }
            }
            if (!held) {
                returnPiece(level, token);
            }
        }
    }

    private static boolean carries(Player player, long token) {
        for (ItemStack s : player.getInventory().items) {
            LensPiece piece = s.get(StructureRegistry.LENS_PIECE.get());
            if (piece != null && piece.token() == token) {
                return true;
            }
        }
        for (ItemStack s : player.getInventory().offhand) {
            LensPiece piece = s.get(StructureRegistry.LENS_PIECE.get());
            if (piece != null && piece.token() == token) {
                return true;
            }
        }
        LensPiece carried = player.containerMenu.getCarried().get(StructureRegistry.LENS_PIECE.get());
        return carried != null && carried.token() == token;
    }

    // ------------------------------------------------------------------ the Eye, the hint, the solve

    private void wakeEye(ServerLevel level) {
        long now = level.getGameTime();
        if (now < eyeReadyAt) {
            return;
        }
        summoned.removeIf(id -> {
            Entity e = level.getEntity(id);
            return e == null || !e.isAlive();
        });
        eyeReadyAt = now + StructureConfig.eyeCooldownTicks();
        for (int p = 0; p < ports.length; p++) {
            if (ports[p] != Lens.PORT_EYE) {
                continue;
            }
            BlockPos at = portPos(p);
            BlockState eye = level.getBlockState(at);
            if (eye.getBlock() instanceof WardenEyeBlock) {
                level.setBlock(at, eye.setValue(WardenEyeBlock.AWAKE, true), Block.UPDATE_ALL);
            }
            level.playSound(null, at, StructureRegistry.WARDEN_EYE_WAKES.get(), SoundSource.HOSTILE, 1.4f, 1.0f);
            int room = EYE_CAP - summoned.size();
            if (room <= 0) {
                continue;
            }
            Direction in = PuzzleBlock.direction(Lens.opposite(Lens.portHeading(n, p)));
            Vec3 spawn = Vec3.atBottomCenterOf(at.relative(in));
            for (Entity e : Guards.shardlings(level, spawn, Vec3.atCenterOf(worldPosition), Math.min(2, room))) {
                summoned.add(e.getUUID());
            }
        }
        setChanged();
    }

    private void closeEye(ServerLevel level) {
        for (int p = 0; p < ports.length; p++) {
            if (ports[p] == Lens.PORT_EYE) {
                BlockState eye = level.getBlockState(portPos(p));
                if (eye.getBlock() instanceof WardenEyeBlock && eye.getValue(WardenEyeBlock.AWAKE)) {
                    level.setBlock(portPos(p), eye.setValue(WardenEyeBlock.AWAKE, false), Block.UPDATE_ALL);
                }
            }
        }
    }

    /**
     * The hint's wait ran out (GDD 6.2: after five minutes unsolved the Codex offers a hint): players near the room
     * are told their Codex stirs, and its Lens Array page offers the hint until someone takes it.
     */
    void offerHint(ServerLevel level) {
        hintAt = -1;
        hintOffered = true;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(Vec3.atCenterOf(worldPosition)) < 32 * 32) {
                p.displayClientMessage(Component.translatable("cosmicbreach.lens.hint_offered"), true);
            }
        }
        level.playSound(null, worldPosition, StructureRegistry.RECEPTOR_HUM.get(), SoundSource.BLOCKS, 0.4f, 1.8f);
        setChanged();
        sync();
    }

    /** True while the Codex offers this room's hint. */
    public boolean hintOffered() {
        return hintOffered;
    }

    /** Game ticks until the hint is offered, 0 if it is now, -1 if no wait is running (solved, or a hint is lit). */
    public long ticksToHint(long now) {
        if (hintOffered) {
            return 0;
        }
        return hintAt < 0 ? -1 : Math.max(0, hintAt - now);
    }

    /** Takes the offered hint (from the Codex's page): lights one mirror of the answer. False if none is offered. */
    public boolean takeHint(ServerLevel level) {
        if (!hintOffered || phase != ACTIVE) {
            return false;
        }
        hintOffered = false;
        boolean lit = showHint(level);
        setChanged();
        sync();
        return lit;
    }

    /**
     * Lights one mirror of the answer (GDD 6.2's hint, what the Codex offers after five minutes): a fixed mirror
     * turned wrong first, else a pedestal a loose mirror belongs on, else a splitter turned wrong. The client
     * draws a ghost of the right piece there. Returns false if nothing is left to hint.
     */
    public boolean showHint(ServerLevel level) {
        if (phase != ACTIVE) {
            return false;
        }
        int[] cells = readCells();
        int pick = -1;
        for (int pass = 0; pass < 3 && pick < 0; pass++) {
            for (int i = 0; i < cells.length && pick < 0; i++) {
                int want = solution[i];
                boolean match = switch (pass) {
                    case 0 -> Lens.kind(want) == Lens.MIRROR && !Lens.loose(want);
                    case 1 -> Lens.kind(want) == Lens.MIRROR;
                    default -> Lens.kind(want) == Lens.SPLITTER;
                };
                if (match && cells[i] != want) {
                    pick = i;
                }
            }
        }
        hintAt = -1;
        hintOffered = false;
        if (pick < 0) {
            return false;
        }
        hintCell = pick;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(Vec3.atCenterOf(worldPosition)) < 32 * 32) {
                p.displayClientMessage(Component.translatable("cosmicbreach.lens.hint"), true);
            }
        }
        level.playSound(null, cellPos(pick), StructureRegistry.RECEPTOR_LIT.get(), SoundSource.BLOCKS, 0.5f, 1.5f);
        sync();
        return true;
    }

    /** For tests and the debug command: the hint timer runs out {@code ticks} from now. */
    public void hintIn(ServerLevel level, int ticks) {
        hintCell = -1;
        hintOffered = false;
        hintAt = level.getGameTime() + ticks;
        setChanged();
    }

    private void solve(ServerLevel level) {
        phase = SOLVED;
        holdStart = -1;
        hintCell = -1;
        hintAt = -1;
        XpSource.LayerTier layer = tier >= 3 ? XpSource.LayerTier.DEEP : tier == 2 ? XpSource.LayerTier.DRIFT : XpSource.LayerTier.REACH;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(Vec3.atCenterOf(worldPosition)) <= StructureConfig.wakeRadius() * StructureConfig.wakeRadius()
                    && solvedFor.add(p.getUUID())) {
                AttunementXp.award(p, XpSource.PUZZLE_SOLVED.at(layer));
                p.displayClientMessage(Component.translatable("cosmicbreach.lens.solved"), true);
            }
        }
        if (vault != null && level.getBlockEntity(vault) instanceof VaultBlockEntity v) {
            v.unlock(level);
        }
        LensArrays.solved(level, this);
        BlockPos aperture = aperturePos();
        if (aperture != null) {
            level.playSound(null, aperture, StructureRegistry.RECEPTOR_LIT.get(), SoundSource.BLOCKS, 1.6f, 0.75f);
        }
        CosmicBreach.LOGGER.debug("[cosmicbreach] Lens Array at {} solved after {} traces", worldPosition, traces);
        sync();
    }

    // ------------------------------------------------------------------ debug

    /** Sets every pedestal to the solution (loose pieces carried off come back too). The hold still has to run. */
    public void debugSolve(ServerLevel level) {
        if (phase == DORMANT) {
            return;
        }
        out.clear();
        writeCells(level, solution);
        retrace(level);
    }

    /** Puts the grid back as the room first showed it. */
    public void debugScramble(ServerLevel level) {
        if (phase == DORMANT) {
            return;
        }
        out.clear();
        phase = ACTIVE;
        holdStart = -1;
        hintCell = -1;
        hintOffered = false;
        hintAt = level.getGameTime() + StructureConfig.hintTicks();
        writeCells(level, start);
        if (vault != null && level.getBlockEntity(vault) instanceof VaultBlockEntity v) {
            v.seal(level);
        }
        retrace(level);
    }

    public int[] solution() {
        return solution;
    }

    public int[] startCells() {
        return start;
    }

    // ------------------------------------------------------------------ lifecycle, saving, syncing

    @Override
    public void onLoad() {
        super.onLoad();
        LensArrays.add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        LensArrays.remove(this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        LensArrays.remove(this);
    }

    private void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        saveShared(tag);
        tag.putLong("seed", seed);
        tag.putInt("tier", tier);
        if (vault != null) {
            tag.put("vault", NbtUtils.writeBlockPos(vault));
        }
        tag.putIntArray("start", start);
        tag.putIntArray("solution", solution);
        tag.putInt("min_moves", minMoves);
        tag.putLong("activated_at", activatedAt);
        tag.putLong("hint_at", hintAt);
        tag.putLong("eye_ready_at", eyeReadyAt);
        ListTag ids = new ListTag();
        summoned.forEach(id -> ids.add(NbtUtils.createUUID(id)));
        tag.put("summoned", ids);
        ListTag solved = new ListTag();
        solvedFor.forEach(id -> solved.add(NbtUtils.createUUID(id)));
        tag.put("solved_for", solved);
        ListTag pieces = new ListTag();
        out.forEach((token, entry) -> {
            CompoundTag t = new CompoundTag();
            t.putLong("token", token);
            t.putInt("piece", entry[0]);
            t.putInt("cell", entry[1]);
            pieces.add(t);
        });
        tag.put("out", pieces);
    }

    /** What clients need too. */
    private void saveShared(CompoundTag tag) {
        tag.putString("difficulty", difficulty);
        tag.putInt("size", n);
        tag.putInt("ceiling", ceiling);
        tag.putInt("phase", phase);
        tag.putIntArray("ports", ports);
        tag.putInt("source", source);
        tag.putIntArray("segments", segments);
        tag.putInt("lit", litMask);
        tag.putInt("hint_cell", hintCell);
        tag.putInt("hint_piece", hintPiece());
        tag.putBoolean("hint_offered", hintOffered);
        tag.putLong("hold_start", holdStart);
    }

    private int syncedHintPiece;

    /** On a client, the hinted piece as the server sent it. */
    public int clientHintPiece() {
        return syncedHintPiece;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        difficulty = tag.getString("difficulty").isEmpty() ? LensDifficulty.EASY_5.name() : tag.getString("difficulty");
        n = tag.contains("size") ? tag.getInt("size") : 5;
        ceiling = tag.contains("ceiling") ? tag.getInt("ceiling") : 7;
        phase = tag.getInt("phase");
        ports = tag.getIntArray("ports");
        source = tag.contains("source") ? tag.getInt("source") : -1;
        segments = tag.getIntArray("segments");
        litMask = tag.getInt("lit");
        hintCell = tag.contains("hint_cell") ? tag.getInt("hint_cell") : -1;
        syncedHintPiece = tag.getInt("hint_piece");
        hintOffered = tag.getBoolean("hint_offered");
        holdStart = tag.contains("hold_start") ? tag.getLong("hold_start") : -1;
        if (tag.contains("seed")) {
            seed = tag.getLong("seed");
            tier = tag.getInt("tier");
            vault = tag.contains("vault") ? NbtUtils.readBlockPos(tag, "vault").orElse(null) : null;
            start = tag.getIntArray("start");
            solution = tag.getIntArray("solution");
            minMoves = tag.getInt("min_moves");
            activatedAt = tag.getLong("activated_at");
            hintAt = tag.getLong("hint_at");
            eyeReadyAt = tag.getLong("eye_ready_at");
            summoned.clear();
            for (Tag t : tag.getList("summoned", Tag.TAG_INT_ARRAY)) {
                summoned.add(NbtUtils.loadUUID(t));
            }
            solvedFor.clear();
            for (Tag t : tag.getList("solved_for", Tag.TAG_INT_ARRAY)) {
                solvedFor.add(NbtUtils.loadUUID(t));
            }
            out.clear();
            for (Tag t : tag.getList("out", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) t;
                out.put(c.getLong("token"), new int[] {c.getInt("piece"), c.getInt("cell")});
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveShared(tag);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Plays {@code sound} at the aperture (or the core) for everyone near. */
    public void play(ServerLevel level, SoundEvent sound, float volume, float pitch) {
        BlockPos at = aperturePos() == null ? worldPosition : aperturePos();
        level.playSound(null, at, sound, SoundSource.BLOCKS, volume, pitch);
    }
}
