package com.cosmicbreach.structure.gen;

import com.cosmicbreach.registry.ModBlocks;
import com.cosmicbreach.structure.StructureRegistry;
import com.cosmicbreach.structure.trap.GuardMarkerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import org.jetbrains.annotations.Nullable;

/**
 * The Gyre Observatory drawn from its plan ({@link ObservatoryLayout}), one chunk at a time: grey-blue Driftstone
 * bricks, Rimeglass windows and a glass dome, cyan Nebulite lamps, petrified Driftwood pillars, the asteroid it
 * grows through (its own lumpy rock, merging with the natural one it was placed on) sliced flat at the door.
 * Procedural for the same reasons as the Reliquary ({@link ReliquaryPiece}).
 */
public class ObservatoryPiece extends StructurePiece {
    private final BlockPos origin;
    private final long seed;
    private final int headroom;
    private final ObservatoryLayout plan;

    public ObservatoryPiece(BlockPos origin, long seed, int asteroid, int headroom) {
        this(origin, seed, headroom, ObservatoryLayout.of(seed, asteroid, headroom));
    }

    private ObservatoryPiece(BlockPos origin, long seed, int headroom, ObservatoryLayout plan) {
        super(StructureRegistry.OBSERVATORY_PIECE.get(), 0, box(origin, plan));
        this.origin = origin.immutable();
        this.seed = seed;
        this.headroom = headroom;
        this.plan = plan;
    }

    public ObservatoryPiece(CompoundTag tag) {
        super(StructureRegistry.OBSERVATORY_PIECE.get(), tag);
        this.origin = new BlockPos(tag.getInt("OX"), tag.getInt("OY"), tag.getInt("OZ"));
        this.seed = tag.getLong("Seed");
        this.headroom = tag.getInt("Headroom");
        this.plan = ObservatoryLayout.of(seed, tag.getInt("Asteroid"), headroom);
    }

    private static BoundingBox box(BlockPos o, ObservatoryLayout plan) {
        int r = Math.max(plan.asteroid() + 4, (int) ObservatoryLayout.CHAMBER + 2);
        return new BoundingBox(o.getX() - r, o.getY() - plan.depth() - 1, o.getZ() - r, o.getX() + r, o.getY() + plan.height() + 1,
                o.getZ() + r);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("OX", origin.getX());
        tag.putInt("OY", origin.getY());
        tag.putInt("OZ", origin.getZ());
        tag.putLong("Seed", seed);
        tag.putInt("Asteroid", plan.asteroid());
        tag.putInt("Headroom", headroom);
    }

    public ObservatoryLayout plan() {
        return plan;
    }

    public BlockPos origin() {
        return origin;
    }

    /** The Lens room's core, in the world. */
    public BlockPos core() {
        return origin.offset(0, plan.floorY(plan.top()) - 1, 0);
    }

    /** The vault: across the telescope chamber from where the last lift arrives. */
    public BlockPos vault() {
        Direction away = plan.liftSide(plan.floors() - 2).getOpposite();
        return origin.offset(away.getStepX() * 11, plan.floorY(plan.top()), away.getStepZ() * 11);
    }

    /** Where floor {@code k}'s lift leaves from, in the world. */
    public BlockPos lift(int k) {
        return origin.offset(plan.liftX(k), plan.floorY(k), plan.liftZ(k));
    }

    // ------------------------------------------------------------------ drawing

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private static BlockState bricks() {
        return ModBlocks.DRIFTSTONE_BRICKS.get().defaultBlockState();
    }

    private static BlockState glass() {
        return ModBlocks.RIMEGLASS.get().defaultBlockState();
    }

    private static BlockState lamp() {
        return StructureRegistry.NEBULITE_LAMP.get().defaultBlockState();
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
            BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        Placer p = new Placer(level, chunkBox);
        int r = Math.max(plan.asteroid() + 4, (int) ObservatoryLayout.CHAMBER + 2);
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                int wx = origin.getX() + x;
                int wz = origin.getZ() + z;
                if (wx < chunkBox.minX() || wx > chunkBox.maxX() || wz < chunkBox.minZ() || wz > chunkBox.maxZ()) {
                    continue;
                }
                for (int y = -plan.depth(); y <= plan.height(); y++) {
                    BlockState s = planned(x, y, z);
                    if (s != null) {
                        p.set(at.set(wx, origin.getY() + y, wz), s);
                    }
                }
            }
        }
        // the working parts
        int top = plan.top();
        Direction last = plan.liftSide(plan.floors() - 2);
        p.lensGrid(core(), plan.difficulty(), plan.puzzleSeed(), 2, ObservatoryLayout.CEILING, vault());
        p.vault(vault(), StructureRegistry.OBSERVATORY_VAULT.get(), last);
        for (int k = 0; k <= plan.floors() - 2; k++) {
            Direction side = plan.liftSide(k);
            Direction exit = k == plan.floors() - 2 ? side.getClockWise() : side.getOpposite();
            p.updraft(lift(k), ObservatoryLayout.STOREY + 2, exit);
        }
        for (int k = 1; k <= plan.floors() - 2; k++) {
            Direction across = plan.lift().getClockWise();
            int y = plan.floorY(k);
            p.tripwire(origin.offset(across.getStepX() * 9, y, across.getStepZ() * 9),
                    origin.offset(-across.getStepX() * 9, y, -across.getStepZ() * 9));
        }
        p.guard(origin.offset(0, plan.floorY(top) + 5, 0), GuardMarkerBlockEntity.GYRE_KNIGHT, 1);
    }

    /** The block the plan puts at (x, y, z) relative to the asteroid's centre, or null to leave the world as it is. */
    public @Nullable BlockState planned(int x, int y, int z) {
        double d = Math.sqrt(x * x + z * z);
        int base = plan.base();
        int top = plan.floorY(plan.top());
        if (y > top + ObservatoryLayout.CEILING) {
            return dome(x, y, z, d, top + ObservatoryLayout.CEILING);
        }
        if (y >= top - 1) {
            return chamber(x, y - top, z, d);
        }
        if (y >= base - 1) {
            return tower(x, y, z, d);
        }
        return below(x, y, z, d);
    }

    /** The asteroid under the flattened top, and the root spike through it. */
    private @Nullable BlockState below(int x, int y, int z, double d) {
        int tip = -plan.depth();
        int base = plan.base();
        double rr = 7.0 * (y - tip) / (double) (base - 1 - tip);
        if (d <= rr) {
            return d <= 0.6 ? ModBlocks.NEBULITE_BLOCK.get().defaultBlockState() : bricks();
        }
        return rock(x, y, z);
    }

    private @Nullable BlockState rock(int x, int y, int z) {
        double lump = (hash(x >> 2, y >> 2, z >> 2) - 0.5) * 4.0 + (hash(x, y, z) - 0.5) * 0.8;
        double d3 = Math.sqrt(x * x + y * y + z * z) + lump;
        if (d3 > plan.asteroid()) {
            return null;
        }
        double h = hash(x * 7 + 3, y * 7 + 1, z * 7 + 5);
        if (h < 0.05) {
            return ModBlocks.NEBULITE_ORE.get().defaultBlockState();
        }
        if (d3 > plan.asteroid() - 1.5 && h > 0.985) {
            return glass();
        }
        return ModBlocks.DRIFTSTONE.get().defaultBlockState();
    }

    private @Nullable BlockState tower(int x, int y, int z, double d) {
        int base = plan.base();
        if (y == base - 1) {
            // the asteroid's flattened top, and the entrance floor
            if (d <= ObservatoryLayout.TOWER) {
                return bricks();
            }
            if (d <= ObservatoryLayout.TOWER + 2) {
                return bricks();
            }
            return d <= plan.asteroid() + 1 ? ModBlocks.DRIFTSTONE.get().defaultBlockState() : null;
        }
        if (d > ObservatoryLayout.TOWER) {
            // clear the natural rock above the flattened top round the tower
            return d <= plan.asteroid() + 4 && y < base + 10 ? AIR : null;
        }
        int k = Math.floorDiv(y - base + 1, ObservatoryLayout.STOREY);
        int ly = y - plan.floorY(k);
        if (ly == -1) {
            // a floor: the lift hole from below, lamps (the ceiling of the floor below)
            if (k >= 1 && isLiftHole(x, z, k - 1)) {
                return AIR;
            }
            if (Math.abs(x) == 4 && Math.abs(z) == 4) {
                return lamp();
            }
            return bricks();
        }
        if (d > ObservatoryLayout.TOWER_INSIDE) {
            if (k == 0 && ly < 3 && onAxis(x, z, plan.entry(), 1)) {
                return AIR;
            }
            if (ly == ObservatoryLayout.STOREY - 1) {
                return ModBlocks.NEBULITE_BLOCK.get().defaultBlockState();
            }
            if (Math.abs(x) == Math.abs(z) && ly >= 1 && ly < 4) {
                return glass();
            }
            return bricks();
        }
        if (Math.abs(x) == 5 && Math.abs(z) == 5) {
            return ModBlocks.DRIFTWOOD_LOG.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        }
        return AIR;
    }

    private boolean isLiftHole(int x, int z, int k) {
        return x == plan.liftX(k) && z == plan.liftZ(k);
    }

    private static boolean onAxis(int x, int z, Direction dir, int halfWidth) {
        if (dir.getAxis() == Direction.Axis.X) {
            return Math.abs(z) <= halfWidth && Integer.signum(x) == dir.getStepX();
        }
        return Math.abs(x) <= halfWidth && Integer.signum(z) == dir.getStepZ();
    }

    /** The telescope chamber, ly relative to its pedestals (floor at -1, ceiling at {@link ObservatoryLayout#CEILING}). */
    private @Nullable BlockState chamber(int x, int ly, int z, double d) {
        if (d > ObservatoryLayout.CHAMBER) {
            return null;
        }
        if (ly == -1) {
            return isLiftHole(x, z, plan.floors() - 2) ? AIR : bricks();
        }
        if (ly == ObservatoryLayout.CEILING) {
            int ax = Math.abs(x);
            int az = Math.abs(z);
            if ((ax == 10 && az == 0) || (ax == 0 && az == 10) || (ax == 7 && az == 7)) {
                return lamp();
            }
            return x % 4 == 0 || z % 4 == 0 || d > ObservatoryLayout.CHAMBER_INSIDE ? bricks() : glass();
        }
        if (d > ObservatoryLayout.CHAMBER_INSIDE) {
            boolean window = ly >= 1 && ly <= 6 && (Math.abs(x) == Math.abs(z) || x == 0 || z == 0);
            if (ly == 0 || ly == 7) {
                return ModBlocks.NEBULITE_BLOCK.get().defaultBlockState();
            }
            return window ? glass() : bricks();
        }
        return AIR;
    }

    /** The glass dome over the chamber and the telescope standing out of it. */
    private @Nullable BlockState dome(int x, int y, int z, double d, int ceiling) {
        int ly = y - ceiling;
        double dc = Math.sqrt(x * x + ly * ly + z * z);
        int apex = ObservatoryLayout.DOME;
        if (ly >= apex - 2 && ly <= apex + 7) {
            if (d <= 1.2) {
                return ly == apex + 7 ? glass() : AIR;
            }
            if (d <= 2.3) {
                return ModBlocks.NEBULITE_BLOCK.get().defaultBlockState();
            }
        }
        if (dc > apex || dc <= apex - 1.1) {
            return dc <= apex - 1.1 ? AIR : null;
        }
        return x == 0 || z == 0 || Math.abs(x) == Math.abs(z) ? bricks() : glass();
    }

    /** A cheap position hash in [0, 1), fixed per structure seed. */
    private double hash(int x, int y, int z) {
        long h = seed * 0x9E3779B97F4A7C15L + x * 0xC2B2AE3D27D4EB4FL + y * 0x165667B19E3779F9L + z * 0x27D4EB2F165667C5L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (h >>> 11) * 0x1.0p-53;
    }
}
