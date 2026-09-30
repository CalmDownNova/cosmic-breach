package com.cosmicbreach.structure.crypt;

import com.cosmicbreach.registry.ModBlocks;
import com.cosmicbreach.structure.StructureRegistry;
import com.cosmicbreach.structure.choir.ChoirDifficulty;
import com.cosmicbreach.structure.choir.ChoirRoom;
import com.cosmicbreach.structure.crypt.CryptLayout.Kind;
import com.cosmicbreach.structure.crypt.trap.ChuteRules;
import com.cosmicbreach.structure.crypt.trap.GravityPistonBlockEntity;
import com.cosmicbreach.structure.crypt.trap.StarfallChuteBlock;
import com.cosmicbreach.structure.crypt.trap.StarfallChuteBlockEntity;
import com.cosmicbreach.structure.crypt.trap.VoidPocketBlockEntity;
import com.cosmicbreach.structure.trap.KineticEmitterBlock;
import com.cosmicbreach.structure.trap.KineticEmitterBlockEntity;
import com.cosmicbreach.structure.trap.KineticThreadBlock;
import com.cosmicbreach.structure.vault.VaultBlock;
import com.cosmicbreach.structure.vault.VaultBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import org.jetbrains.annotations.Nullable;

/**
 * The Hollow Crypt drawn from its plan ({@link CryptLayout}), one chunk at a time: a block of carved Umbral Basalt set
 * into a Rift Abyss pillar's top, its roof flush with the platform, a gatehouse over the way down, and under it 2 or 3
 * levels of rooms joined by stairs. Dark on purpose (GDD 2.2): Neon Lichen on the walls (light 7, magenta and teal),
 * Rift Glass panels, black stone; the pads of the Choir Floor and the traps' tells are the only other light.
 * Procedural, like the W5 structures: the plan is a few numbers from the seed and the traps must line up exactly with
 * their rules.
 *
 * <p>Geometry: {@link #origin} is the grid's north-west corner at level 0's floor. Cell (x, z) of level l spans
 * {@code origin + (10x .. 10x + 10, -8l, 10z .. 10z + 10)} (walls shared): floor at 0, rooms 1 to 6 high, ceiling at
 * 7. The roof layer is at +8 (the platform's surface), the gatehouse above it.
 */
public class CryptPiece extends StructurePiece {
    private static final int C = CryptLayout.CELL;
    private final BlockPos origin;
    private final long seed;
    private final CryptLayout plan;

    public CryptPiece(BlockPos origin, long seed) {
        this(origin, seed, CryptLayout.generate(seed));
    }

    private CryptPiece(BlockPos origin, long seed, CryptLayout plan) {
        super(CryptRegistry.CRYPT_PIECE.get(), 0, box(origin, plan));
        this.origin = origin.immutable();
        this.seed = seed;
        this.plan = plan;
    }

    public CryptPiece(CompoundTag tag) {
        super(CryptRegistry.CRYPT_PIECE.get(), tag);
        this.origin = new BlockPos(tag.getInt("OX"), tag.getInt("OY"), tag.getInt("OZ"));
        this.seed = tag.getLong("Seed");
        this.plan = CryptLayout.generate(seed);
    }

    private static BoundingBox box(BlockPos o, CryptLayout plan) {
        return new BoundingBox(o.getX(), o.getY() - CryptLayout.STOREY * (plan.levels - 1) - 4, o.getZ(),
                o.getX() + CryptLayout.SIZE - 1, o.getY() + 16, o.getZ() + CryptLayout.SIZE - 1);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("OX", origin.getX());
        tag.putInt("OY", origin.getY());
        tag.putInt("OZ", origin.getZ());
        tag.putLong("Seed", seed);
    }

    public CryptLayout plan() {
        return plan;
    }

    public BlockPos origin() {
        return origin;
    }

    public long seed() {
        return seed;
    }

    /** Floor block y of level {@code l}. */
    public int floorY(int l) {
        return origin.getY() - CryptLayout.STOREY * l;
    }

    /** The middle of cell (x, z) of level {@code l}, on its floor's surface (where one stands). */
    public BlockPos cellCentre(int l, int x, int z) {
        return origin.offset(C * x + 5, -CryptLayout.STOREY * l + 1, C * z + 5);
    }

    /** Where the Conductor stands. */
    public BlockPos conductor() {
        return origin.offset(C * plan.choirX + C, -CryptLayout.STOREY * plan.bottom() + 1, C * plan.choirZ + C);
    }

    /** The side of the Choir Floor the main path comes in by (the vault is across the room from it). */
    public int choirEntry() {
        List<int[]> path = plan.mainPath();
        int[] last = path.get(path.size() - 1);
        for (int s = 0; s < 4; s++) {
            int nx = last[1] + CryptLayout.DX[s];
            int nz = last[2] + CryptLayout.DZ[s];
            if (plan.door(last[0], last[1], last[2], s) && plan.kind(last[0], nx, nz) == Kind.CHOIR) {
                return s;
            }
        }
        return 0;
    }

    /** The vault: against the Choir room's wall across from the way in, facing the Conductor. */
    public BlockPos vault() {
        int s = choirEntry();
        return conductor().offset(CryptLayout.DX[s] * 9, 0, CryptLayout.DZ[s] * 9);
    }

    /** Where one stands at the top of the entrance stair (in the gatehouse). */
    public BlockPos entrance() {
        int s = plan.side(0, plan.entranceX, plan.entranceZ);
        int[] strip = strip(s, 1);
        return origin.offset(C * plan.entranceX + strip[0], 9, C * plan.entranceZ + strip[1]);
    }

    public long choirSeed() {
        return seed * 0x9E3779B97F4A7C15L + 0x5C4010L;
    }

    // ------------------------------------------------------------------ drawing

    private static BlockState rock() {
        return ModBlocks.UMBRAL_BASALT.get().defaultBlockState();
    }

    private static BlockState brick() {
        return ModBlocks.UMBRAL_BASALT_BRICKS.get().defaultBlockState();
    }

    private static BlockState polished() {
        return ModBlocks.POLISHED_UMBRAL_BASALT.get().defaultBlockState();
    }

    private static BlockState glass() {
        return ModBlocks.RIFT_GLASS.get().defaultBlockState();
    }

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
            BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        BoundingBox box = getBoundingBox();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        int x0 = Math.max(box.minX(), chunkBox.minX());
        int x1 = Math.min(box.maxX(), chunkBox.maxX());
        int z0 = Math.max(box.minZ(), chunkBox.minZ());
        int z1 = Math.min(box.maxZ(), chunkBox.maxZ());
        for (int wx = x0; wx <= x1; wx++) {
            for (int wz = z0; wz <= z1; wz++) {
                for (int y = box.minY(); y <= box.maxY(); y++) {
                    BlockState s = planned(wx - origin.getX(), y, wz - origin.getZ());
                    if (s != null) {
                        level.setBlock(at.set(wx, y, wz), s, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        workingParts(level, chunkBox);
    }

    /** The block the plan puts at grid (gx, y, gz) (y in the world), or null to leave the world as it is. */
    public @Nullable BlockState planned(int gx, int y, int gz) {
        int f0 = origin.getY();
        int fb = floorY(plan.bottom());
        if (gx < 0 || gz < 0 || gx > CryptLayout.SIZE - 1 || gz > CryptLayout.SIZE - 1) {
            return null;
        }
        int cx = Math.min(gx / C, CryptLayout.GRID - 1);
        int cz = Math.min(gz / C, CryptLayout.GRID - 1);
        int lx = gx - C * cx;
        int lz = gz - C * cz;
        if (y > f0 + 8) {
            return above(gx, gz, y - f0 - 8);
        }
        if (y == f0 + 8) {
            if (cx == plan.entranceX && cz == plan.entranceZ) {
                BlockState st = stair(plan.side(0, cx, cz), f0 + 8, lx, lz, y);
                if (st != null) {
                    return st;
                }
            }
            int mid = CryptLayout.SIZE / 2;
            return Math.abs(gx - mid) <= 1 || Math.abs(gz - mid) <= 1 ? polished() : rock(); // paths from the parapet's gaps
        }
        if (y < fb) {
            int k = fb - y;
            int inset = 2 * k + (int) (hash(gx, k, gz) % 2);
            return gx >= inset && gz >= inset && gx <= CryptLayout.SIZE - 1 - inset && gz <= CryptLayout.SIZE - 1 - inset ? rock() : null;
        }
        int l = (f0 + 7 - y) / CryptLayout.STOREY;
        int ly = y - floorY(l);
        return inside(l, cx, cz, lx, lz, ly, y);
    }

    private BlockState inside(int l, int cx, int cz, int lx, int lz, int ly, int y) {
        Kind kind = plan.kind(l, cx, cz);
        // the Choir Floor: one room over its four cells
        if (l == plan.bottom() && choirInterior(cx * C + lx, cz * C + lz)) {
            return choir(cx * C + lx - (plan.choirX * C + C), cz * C + lz - (plan.choirZ * C + C), ly);
        }
        boolean wallX = lx == 0 || lx == C;
        boolean wallZ = lz == 0 || lz == C;
        if (wallX && wallZ) {
            return ly == 0 ? polished() : ly == 7 ? rock() : anyRoom(l, cx, cz, lx, lz) ? polished() : rock();
        }
        if (wallX || wallZ) {
            return wall(l, cx, cz, lx, lz, ly, wallX);
        }
        // stairs cut through whatever they cross
        BlockState st = stairsIn(l, cx, cz, lx, lz, y);
        if (st != null) {
            return st;
        }
        return switch (kind) {
            case NONE -> rock();
            case RIFT, CHUTES, TRIPWIRE -> corridor(l, cx, cz, kind, lx, lz, ly);
            default -> room(l, cx, cz, kind, lx, lz, ly);
        };
    }

    /** True for a column inside the Choir Floor's 2 by 2 block (its inner walls gone). */
    private boolean choirInterior(int gx, int gz) {
        int x0 = plan.choirX * C;
        int z0 = plan.choirZ * C;
        return gx > x0 && gx < x0 + 2 * C && gz > z0 && gz < z0 + 2 * C;
    }

    private boolean anyRoom(int l, int cx, int cz, int lx, int lz) {
        int ax = lx == 0 ? cx - 1 : cx;
        int az = lz == 0 ? cz - 1 : cz;
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                if (plan.kind(l, ax + dx, az + dz) != Kind.NONE) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A wall line between two cells: bricks where a room sees it, doors, the pocket's seal, a Rift Glass panel. */
    private BlockState wall(int l, int cx, int cz, int lx, int lz, int ly, boolean alongZ) {
        // the two cells either side, and the side from the first to the second
        int ax = alongZ ? (lx == 0 ? cx - 1 : cx) : cx;
        int az = alongZ ? cz : (lz == 0 ? cz - 1 : cz);
        int side = alongZ ? 0 : 1;
        int bx = ax + CryptLayout.DX[side];
        int bz = az + CryptLayout.DZ[side];
        Kind a = plan.kind(l, ax, az);
        Kind b = plan.kind(l, bx, bz);
        int along = alongZ ? lz : lx;
        if (ly == 0) {
            return a != Kind.NONE || b != Kind.NONE ? polished() : rock();
        }
        if (ly == 7) {
            return rock();
        }
        if (a == Kind.NONE && b == Kind.NONE) {
            return rock();
        }
        boolean door = plan.door(l, ax, az, side);
        if (door && along >= 4 && along <= 6 && ly <= 3) {
            return a == Kind.POCKET || b == Kind.POCKET ? CryptRegistry.POCKET_SEAL.get().defaultBlockState() : AIR;
        }
        if (door && (along == 3 || along == 7) && ly <= 3 || door && ly == 4 && along >= 3 && along <= 7) {
            return polished(); // the door's frame
        }
        boolean hall = a == Kind.HALL || b == Kind.HALL;
        if (!door && hall && along >= 4 && along <= 6 && ly >= 2 && ly <= 4 && hash(l, ax * 7 + az, side) % 3 == 0) {
            return glass();
        }
        return brick();
    }

    private BlockState room(int l, int cx, int cz, Kind kind, int lx, int lz, int ly) {
        if (ly == 0) {
            if (kind == Kind.GRAVITY && lx >= 3 && lx <= 7 && lz >= 3 && lz <= 7) {
                return CryptRegistry.GRAVITY_SIGIL.get().part(lx - 5, lz - 5);
            }
            if (kind == Kind.POCKET && lx == 5 && lz == 5) {
                return CryptRegistry.VOID_POCKET.get().defaultBlockState();
            }
            return polished();
        }
        if (ly == 7) {
            if (kind == Kind.GRAVITY && lx == 5 && lz == 5) {
                return CryptRegistry.GRAVITY_PISTON_BLOCK.get().defaultBlockState();
            }
            if (kind == Kind.POCKET && riftAbove(l, cx, cz, lx, lz)) {
                return AIR; // the shaft the rift drops you down
            }
            return rock();
        }
        boolean corner = (lx == 1 || lx == 9) && (lz == 1 || lz == 9);
        if (corner && kind != Kind.STAIR_DOWN && kind != Kind.STAIR_UP && kind != Kind.ENTRANCE) {
            return ModBlocks.UMBRAL_BASALT.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        }
        if (kind == Kind.HALL && plan.stalker(l, cx, cz) && lx == 2 && lz == 2 && ly == 1) {
            return CryptRegistry.STALKER_MARKER.get().defaultBlockState();
        }
        BlockState lichen = lichen(l, cx, cz, lx, lz, ly);
        return lichen != null ? lichen : AIR;
    }

    /** Neon Lichen on a room's walls: a few patches per room at eye height, magenta or teal. */
    private @Nullable BlockState lichen(int l, int cx, int cz, int lx, int lz, int ly) {
        if (ly < 3 || ly > 4) {
            return null;
        }
        Direction face = lx == 1 ? Direction.WEST : lx == 9 ? Direction.EAST : lz == 1 ? Direction.NORTH : lz == 9 ? Direction.SOUTH : null;
        if (face == null) {
            return null;
        }
        int along = face.getAxis() == Direction.Axis.X ? lz : lx;
        if (along != 3 && along != 7) {
            return null;
        }
        long h = hash(l * 31 + cx, cz * 17 + face.ordinal(), along);
        if (h % 5 >= 2 || ly == 4 && h % 2 == 0) {
            return null;
        }
        Block b = h % 7 < 4 ? ModBlocks.MAGENTA_NEON_LICHEN.get() : ModBlocks.TEAL_NEON_LICHEN.get();
        return b.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(face), true);
    }

    /** True if (lx, lz) of a pocket lies under its rift's clusters (the rift corridor is the cell above). */
    private boolean riftAbove(int l, int cx, int cz, int lx, int lz) {
        if (plan.kind(l - 1, cx, cz) != Kind.RIFT) {
            return false;
        }
        int axis = plan.side(l - 1, cx, cz);
        int p = axis == 0 ? lx : lz;
        int across = axis == 0 ? lz : lx;
        return across >= 4 && across <= 6 && p >= 2 && p <= 8 && p != 5;
    }

    private BlockState corridor(int l, int cx, int cz, Kind kind, int lx, int lz, int ly) {
        int axis = plan.side(l, cx, cz);
        int p = axis == 0 ? lx : lz;
        int across = axis == 0 ? lz : lx;
        boolean strip = across >= 4 && across <= 6;
        if (kind == Kind.TRIPWIRE && ly == 1 && (p == 3 || p == 7) && (across == 3 || across == 7)) {
            Direction toward = axis == 0 ? (across == 3 ? Direction.SOUTH : Direction.NORTH) : (across == 3 ? Direction.EAST : Direction.WEST);
            return CryptRegistry.UMBRAL_EMITTER.get().defaultBlockState().setValue(KineticEmitterBlock.FACING, toward);
        }
        if (!strip) {
            return ly == 0 ? polished() : rock();
        }
        if (ly == 0) {
            if (kind == Kind.RIFT && p >= 2 && p <= 8 && p != 5) {
                int mid = p <= 4 ? 3 : 7;
                int dp = p - mid;
                int da = across - 5;
                return axis == 0 ? CryptRegistry.VOID_RIFT_TILE.get().part(dp, da) : CryptRegistry.VOID_RIFT_TILE.get().part(da, dp);
            }
            return polished();
        }
        if (ly == 7) {
            if (kind == Kind.CHUTES && (p == 2 || p == 5 || p == 8)) {
                return CryptRegistry.STARFALL_CHUTE.get().defaultBlockState()
                        .setValue(StarfallChuteBlock.AXIS, axis == 0 ? Direction.Axis.Z : Direction.Axis.X)
                        .setValue(StarfallChuteBlock.MIDDLE, across == 5);
            }
            return rock();
        }
        if (kind == Kind.TRIPWIRE && ly == 1 && (p == 3 || p == 7)) {
            return StructureRegistry.KINETIC_THREAD.get().defaultBlockState()
                    .setValue(KineticThreadBlock.AXIS, axis == 0 ? Direction.Axis.Z : Direction.Axis.X);
        }
        if (ly == 6 && across == 5 && (p == 1 || p == 9) && kind != Kind.CHUTES) {
            return ModBlocks.TEAL_NEON_LICHEN.get().defaultBlockState().setValue(MultifaceBlock.getFaceProperty(Direction.UP), true);
        }
        return AIR;
    }

    /** The Choir Floor's room, relative to the Conductor's column: 19 by 19, the ring's floor comes from ChoirRoom. */
    private BlockState choir(int dx, int dz, int ly) {
        if (ly == 0) {
            return polished();
        }
        if (ly == 7) {
            return rock();
        }
        int ax = Math.abs(dx);
        int az = Math.abs(dz);
        if (ax == 8 && az == 8) {
            return ModBlocks.UMBRAL_BASALT.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        }
        int s = choirEntry();
        if (dx == CryptLayout.DX[s] * 9 && dz == CryptLayout.DZ[s] * 9 && ly == 1) {
            Direction facing = Direction.from2DDataValue(0);
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (d.getStepX() == -CryptLayout.DX[s] && d.getStepZ() == -CryptLayout.DZ[s]) {
                    facing = d;
                }
            }
            return CryptRegistry.CRYPT_VAULT.get().defaultBlockState().setValue(VaultBlock.FACING, facing);
        }
        if ((ax == 9 || az == 9) && ly >= 2 && ly <= 4) {
            int along = ax == 9 ? dz : dx;
            if (Math.abs(along) == 2 || Math.abs(along) == 8) {
                Direction face = ax == 9 ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
                Block b = Math.floorMod(along + ly, 8) < 4 ? ModBlocks.MAGENTA_NEON_LICHEN.get() : ModBlocks.TEAL_NEON_LICHEN.get();
                return b.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(face), true);
            }
        }
        return AIR;
    }

    /**
     * Over the roof layer, {@code up} blocks up (1 to 8): the gatehouse over the entrance, lichen flanking its door
     * outside, and a low parapet round the roof's edge (open in the middle of each side); elsewhere the world as it is.
     */
    private @Nullable BlockState above(int gx, int gz, int up) {
        int glx = gx - C * plan.entranceX;
        int glz = gz - C * plan.entranceZ;
        if (glx >= 0 && glx <= C && glz >= 0 && glz <= C) {
            return gatehouse(glx, glz, up);
        }
        int doorSide = (plan.side(0, plan.entranceX, plan.entranceZ) + 2) & 3;
        int ox = doorSide == 0 ? C + 1 : doorSide == 2 ? -1 : Integer.MIN_VALUE;
        int oz = doorSide == 1 ? C + 1 : doorSide == 3 ? -1 : Integer.MIN_VALUE;
        int along = doorSide == 0 || doorSide == 2 ? glz : glx;
        boolean outsideDoor = (glx == ox && glz >= 0 && glz <= C) || (glz == oz && glx >= 0 && glx <= C);
        if (outsideDoor && (along == 2 || along == 8) && (up == 2 || up == 3)) {
            Direction face = switch (doorSide) {
                case 0 -> Direction.WEST;
                case 1 -> Direction.NORTH;
                case 2 -> Direction.EAST;
                default -> Direction.SOUTH;
            };
            return ModBlocks.TEAL_NEON_LICHEN.get().defaultBlockState().setValue(MultifaceBlock.getFaceProperty(face), true);
        }
        int last = CryptLayout.SIZE - 1;
        boolean edge = gx == 0 || gz == 0 || gx == last || gz == last;
        if (edge && up <= 2) {
            int alongEdge = gx == 0 || gx == last ? gz : gx;
            boolean post = alongEdge % 5 == 0;
            boolean gap = Math.abs(alongEdge - last / 2) <= 1;
            if (gap) {
                return null;
            }
            if (up == 1) {
                return post ? polished() : ModBlocks.UMBRAL_BASALT_BRICK_SET.wall().get().defaultBlockState();
            }
            return post ? ModBlocks.UMBRAL_BASALT_BRICK_SET.slab().get().defaultBlockState() : null;
        }
        return null;
    }

    /** The gatehouse over the entrance cell, (glx, glz) from its corner (0 to 10), {@code up} blocks over the roof layer. */
    private @Nullable BlockState gatehouse(int glx, int glz, int up) {
        int stairSide = plan.side(0, plan.entranceX, plan.entranceZ);
        int doorSide = (stairSide + 2) & 3;
        boolean edgeX = glx == 0 || glx == C;
        boolean edgeZ = glz == 0 || glz == C;
        if (edgeX && edgeZ) {
            return up <= 8 ? polished() : null; // corner pillars, standing proud of the roof
        }
        if (up == 8) {
            return null;
        }
        if (up == 7) {
            return edgeX || edgeZ ? ModBlocks.UMBRAL_BASALT_BRICK_SET.wall().get().defaultBlockState() : null;
        }
        if (up == 6) {
            return edgeX || edgeZ ? polished() : glx >= 4 && glx <= 6 && glz >= 4 && glz <= 6 ? glass() : brick();
        }
        if (edgeX || edgeZ) {
            int side = glx == C ? 0 : glz == C ? 1 : glx == 0 ? 2 : 3;
            int along = edgeX ? glz : glx;
            if (side == doorSide && along >= 4 && along <= 6 && up <= 3) {
                return AIR;
            }
            if (side == doorSide && (along == 3 || along == 7) && up <= 4 || side == doorSide && up == 4 && along >= 3 && along <= 7) {
                return polished();
            }
            if (side != doorSide && up == 3 && along >= 3 && along <= 7) {
                return glass();
            }
            return brick();
        }
        BlockState st = stair(stairSide, origin.getY() + 8, glx, glz, origin.getY() + 8 + up);
        if (st != null) {
            return st;
        }
        if (up == 3 && (glx == 1 || glx == 9 || glz == 1 || glz == 9) && (glx == 5 || glz == 5)) {
            Direction face = glx == 1 ? Direction.WEST : glx == 9 ? Direction.EAST : glz == 1 ? Direction.NORTH : Direction.SOUTH;
            int faceSide = face == Direction.EAST ? 0 : face == Direction.SOUTH ? 1 : face == Direction.WEST ? 2 : 3;
            if (faceSide == doorSide) {
                return AIR;
            }
            return ModBlocks.MAGENTA_NEON_LICHEN.get().defaultBlockState().setValue(MultifaceBlock.getFaceProperty(face), true);
        }
        return AIR;
    }

    /** A stair cell's flight, if (lx, lz, y) is part of it (the stairs, their headroom, the ramp under them, the rail). */
    private @Nullable BlockState stairsIn(int l, int cx, int cz, int lx, int lz, int y) {
        Kind k = plan.kind(l, cx, cz);
        if (k == Kind.STAIR_DOWN) {
            return stair(plan.side(l, cx, cz), floorY(l), lx, lz, y);
        }
        if (k == Kind.STAIR_UP) {
            return stair(plan.side(l, cx, cz), floorY(l - 1), lx, lz, y);
        }
        if (k == Kind.ENTRANCE) {
            return stair(plan.side(l, cx, cz), origin.getY() + 8, lx, lz, y);
        }
        return null;
    }

    /** Across and along coordinates of the strip beside the wall of {@code side}: {lx, lz} of strip block {@code along}. */
    private static int[] strip(int side, int along) {
        return switch (side) {
            case 0 -> new int[] {8, along};
            case 1 -> new int[] {along, 8};
            case 2 -> new int[] {2, along};
            default -> new int[] {along, 2};
        };
    }

    /**
     * A straight flight of eight steps down along the wall of {@code side}, two blocks wide, from the floor at
     * {@code upper} (its top step replacing that floor at the cell's first corner) to the floor 8 blocks down.
     */
    private @Nullable BlockState stair(int side, int upper, int lx, int lz, int y) {
        int acrossWall = side == 0 || side == 2 ? lx : lz;
        int along = side == 0 || side == 2 ? lz : lx;
        int near = side == 0 || side == 1 ? 9 : 1;
        int inner = side == 0 || side == 1 ? 8 : 2;
        int rail = side == 0 || side == 1 ? 7 : 3;
        boolean inStrip = acrossWall == near || acrossWall == inner;
        if (inStrip && along >= 1 && along <= 8) {
            int p = along - 1;
            int stepY = upper - p;
            if (y == stepY) {
                Direction up = side == 0 || side == 2 ? Direction.NORTH : Direction.WEST;
                return ModBlocks.UMBRAL_BASALT_BRICK_SET.stairs().get().defaultBlockState().setValue(StairBlock.FACING, up);
            }
            if (y > stepY && y <= stepY + 3 && y <= upper + 3) {
                return AIR;
            }
            if (y < stepY && y > upper - 8) {
                return brick();
            }
            return null;
        }
        if (acrossWall == rail && along >= 2 && along <= 4 && y == upper + 1) {
            return ModBlocks.UMBRAL_BASALT_BRICK_SET.wall().get().defaultBlockState();
        }
        return null;
    }

    // ------------------------------------------------------------------ the working parts

    /** Configures the block entities inside this chunk: traps, pocket, Choir Floor, vault. */
    private void workingParts(WorldGenLevel level, BoundingBox chunk) {
        for (int l = 0; l < plan.levels; l++) {
            for (int x = 0; x < CryptLayout.GRID; x++) {
                for (int z = 0; z < CryptLayout.GRID; z++) {
                    Kind k = plan.kind(l, x, z);
                    BlockPos cell = origin.offset(C * x, floorY(l) - origin.getY(), C * z);
                    switch (k) {
                        case GRAVITY -> {
                            BlockPos p = cell.offset(5, 7, 5);
                            if (chunk.isInside(p) && level.getBlockEntity(p) instanceof GravityPistonBlockEntity be) {
                                be.configure(6);
                            }
                        }
                        case CHUTES -> {
                            int axis = plan.side(l, x, z);
                            ChuteRules.Pattern pattern = ChuteRules.Pattern.of(plan.pattern(l, x, z));
                            int i = 0;
                            for (int pp : new int[] {2, 5, 8}) {
                                BlockPos p = cell.offset(axis == 0 ? pp : 5, 7, axis == 0 ? 5 : pp);
                                if (chunk.isInside(p) && level.getBlockEntity(p) instanceof StarfallChuteBlockEntity be) {
                                    be.configure(pattern.period, pattern.phase(i), 6);
                                }
                                i++;
                            }
                        }
                        case TRIPWIRE -> {
                            int axis = plan.side(l, x, z);
                            for (int pp : new int[] {3, 7}) {
                                for (int a : new int[] {3, 7}) {
                                    BlockPos p = cell.offset(axis == 0 ? pp : a, 1, axis == 0 ? a : pp);
                                    if (chunk.isInside(p) && level.getBlockEntity(p) instanceof KineticEmitterBlockEntity be) {
                                        be.setLength(3);
                                    }
                                }
                            }
                        }
                        case POCKET -> {
                            BlockPos p = cell.offset(5, 0, 5);
                            if (chunk.isInside(p) && level.getBlockEntity(p) instanceof VoidPocketBlockEntity be) {
                                int e = plan.side(l, x, z);
                                List<BlockPos> seal = new ArrayList<>();
                                for (int along = 4; along <= 6; along++) {
                                    for (int ly = 1; ly <= 3; ly++) {
                                        int wx = e == 0 ? C : e == 2 ? 0 : along;
                                        int wz = e == 1 ? C : e == 3 ? 0 : along;
                                        seal.add(new BlockPos(wx - 5, ly, wz - 5));
                                    }
                                }
                                be.configure(new BlockPos(-4, 1, -4), new BlockPos(4, 6, 4), seal);
                            }
                        }
                        default -> {
                        }
                    }
                }
            }
        }
        BlockPos vault = vault();
        ChoirRoom.place(level, chunk, conductor(), ChoirDifficulty.CRYPT, choirSeed(), 3, vault);
        if (chunk.isInside(vault) && level.getBlockEntity(vault) instanceof VaultBlockEntity v) {
            v.configure(CryptRegistry.VAULT_LOOT, 3);
        }
    }

    private long hash(long a, long b, long c) {
        long h = seed ^ (a * 0x9E3779B97F4A7C15L) ^ (b * 0xC2B2AE3D27D4EB4FL) ^ (c * 0x165667B19E3779F9L);
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return h & Long.MAX_VALUE;
    }
}
