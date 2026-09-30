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
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * The Spire Reliquary drawn from its plan ({@link ReliquaryLayout}), one chunk at a time: a white Starfall Stone
 * spire with turquoise quartz ribs and gold-lit Sunstone, open windows on its spiral ramp, a plaza round its foot.
 * Procedural rather than jigsaw: the plan is a handful of numbers (storeys, entry, roof, difficulty) that give a
 * different spire each time, the Lens room's grid must line up exactly with the puzzle's geometry, and nothing
 * depends on hand-made template files that would drift from the blocks.
 */
public class ReliquaryPiece extends StructurePiece {
    private final BlockPos origin;
    private final long seed;
    private final ReliquaryLayout plan;

    public ReliquaryPiece(BlockPos origin, long seed) {
        this(origin, seed, ReliquaryLayout.of(seed));
    }

    private ReliquaryPiece(BlockPos origin, long seed, ReliquaryLayout plan) {
        super(StructureRegistry.RELIQUARY_PIECE.get(), 0, box(origin, plan));
        this.origin = origin.immutable();
        this.seed = seed;
        this.plan = plan;
    }

    public ReliquaryPiece(CompoundTag tag) {
        super(StructureRegistry.RELIQUARY_PIECE.get(), tag);
        this.origin = new BlockPos(tag.getInt("OX"), tag.getInt("OY"), tag.getInt("OZ"));
        this.seed = tag.getLong("Seed");
        this.plan = ReliquaryLayout.of(seed);
    }

    private static BoundingBox box(BlockPos o, ReliquaryLayout plan) {
        int r = (int) Math.ceil(ReliquaryLayout.PLAZA) + 1;
        return new BoundingBox(o.getX() - r, o.getY() - 12, o.getZ() - r, o.getX() + r, o.getY() + plan.height() + 1, o.getZ() + r);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("OX", origin.getX());
        tag.putInt("OY", origin.getY());
        tag.putInt("OZ", origin.getZ());
        tag.putLong("Seed", seed);
    }

    public ReliquaryLayout plan() {
        return plan;
    }

    public BlockPos origin() {
        return origin;
    }

    /** The Lens room's core, in the world. */
    public BlockPos core() {
        return origin.offset(0, plan.floorY(plan.lensChamber()), 0);
    }

    /** The vault, in the world: across the lens room from its door, just past the grid's sockets. */
    public BlockPos vault() {
        Direction away = awayFromDoor();
        return origin.offset(away.getStepX() * 8, plan.floorY(plan.lensChamber()) + 1, away.getStepZ() * 8);
    }

    private Direction awayFromDoor() {
        double a = plan.entryAngle() + Math.PI;
        return Direction.getNearest(Math.cos(a), 0, Math.sin(a));
    }

    // ------------------------------------------------------------------ drawing

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private static BlockState stone() {
        return ModBlocks.STARFALL_STONE_BRICKS.get().defaultBlockState();
    }

    private static BlockState polished() {
        return ModBlocks.POLISHED_STARFALL_STONE.get().defaultBlockState();
    }

    private static BlockState quartz() {
        return ModBlocks.SPIRE_QUARTZ.get().defaultBlockState();
    }

    private static BlockState sunstone() {
        return StructureRegistry.SUNSTONE.get().defaultBlockState();
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
            BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        Placer p = new Placer(level, chunkBox);
        int r = (int) Math.ceil(ReliquaryLayout.PLAZA);
        int height = plan.height();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                int wx = origin.getX() + x;
                int wz = origin.getZ() + z;
                if (wx < chunkBox.minX() || wx > chunkBox.maxX() || wz < chunkBox.minZ() || wz > chunkBox.maxZ()) {
                    continue;
                }
                double d = Math.sqrt(x * x + z * z);
                if (d > ReliquaryLayout.PLAZA) {
                    continue;
                }
                // foundation: fill down under the floor so the spire and its plaza never float over an edge
                int deep = d <= ReliquaryLayout.OUTER ? 11 : 5;
                for (int y = -2; y >= -1 - deep; y--) {
                    at.set(wx, origin.getY() + y, wz);
                    if (p.get(at).isSolid()) {
                        break;
                    }
                    p.set(at, ModBlocks.STARFALL_STONE.get().defaultBlockState());
                }
                p.set(at.set(wx, origin.getY() - 1, wz), base(x, z, d));
                if (d > ReliquaryLayout.OUTER) {
                    for (int y = 0; y < 6; y++) {
                        BlockState b = buttress(x, y, z, d);
                        p.set(at.set(wx, origin.getY() + y, wz), b == null ? AIR : b);
                    }
                    continue;
                }
                for (int y = 0; y <= height; y++) {
                    p.set(at.set(wx, origin.getY() + y, wz), shell(x, y, z, d));
                }
            }
        }
        // the working parts
        BlockPos vault = vault();
        p.lensGrid(core(), plan.difficulty(), plan.puzzleSeed(), 1, plan.ceiling(), vault);
        p.vault(vault, StructureRegistry.RELIQUARY_VAULT.get(), awayFromDoor().getOpposite());
        for (int c = 1; c < plan.chambers() - 1; c++) {
            p.guard(origin.offset(0, ReliquaryLayout.STOREY * c, 0), GuardMarkerBlockEntity.SHARDLINGS, plan.packs()[c]);
        }
    }

    /**
     * A buttress at the spire's foot: on each of the eight ribs, stepping out two blocks at the ground and back into
     * the wall by y = 5, capped with Spire Quartz. Null where there is none (and never across the entrance).
     */
    private BlockState buttress(int x, int y, int z, double d) {
        boolean rib = x == 0 || z == 0 || Math.abs(x) == Math.abs(z);
        if (!rib || near(plan.phase(x, z), entrancePhase(), ReliquaryLayout.OUTER, 2.5)) {
            return null;
        }
        double reach = ReliquaryLayout.OUTER + 2.2 * (1.0 - y / 5.0);
        if (d > reach) {
            return null;
        }
        return d > reach - 1.0 ? quartz() : stone();
    }

    /** The ground floor and the plaza round the spire. */
    private BlockState base(int x, int z, double d) {
        if (d > ReliquaryLayout.OUTER) {
            return d > 14.0 && d <= 14.7 ? quartz() : polished();
        }
        if (d > ReliquaryLayout.CHAMBER) {
            return stone();
        }
        return floor(x, z, d, false);
    }

    /** A chamber floor: polished stone, a quartz ring, gold at the middle; the lens room's is plain. */
    private BlockState floor(int x, int z, double d, boolean lensRoom) {
        if (!lensRoom && Math.abs(x) == 5 && Math.abs(z) == 5) {
            return ModBlocks.GLIMMER_GRASS.get().defaultBlockState();
        }
        if (!lensRoom && d > 3.5 && d <= 4.5) {
            return quartz();
        }
        if (!lensRoom && d <= 0.5) {
            return ModBlocks.STARSTEEL_BLOCK.get().defaultBlockState();
        }
        if (lightSpot(x, z)) {
            return sunstone();
        }
        return polished();
    }

    /** Sunstone panels in every ceiling (the floor above): eight round the room. */
    private static boolean lightSpot(int x, int z) {
        int ax = Math.abs(x);
        int az = Math.abs(z);
        return (ax == 7 && az == 0) || (ax == 0 && az == 7) || (ax == 5 && az == 5);
    }

    private BlockState shell(int x, int y, int z, double d) {
        int top = plan.topY();
        if (y > top) {
            return roof(x, y, z, d);
        }
        if (y == top) {
            return d <= ReliquaryLayout.CHAMBER && lightSpot(x, z) ? sunstone() : stone();
        }
        double phi = plan.phase(x, z);
        if (d > ReliquaryLayout.GALLERY) {
            return outerWall(x, y, z, phi);
        }
        if (d > ReliquaryLayout.INNER_WALL) {
            return gallery(y, phi);
        }
        if (d > ReliquaryLayout.CHAMBER) {
            return innerWall(y, phi);
        }
        return chamber(x, y, z, d);
    }

    private static boolean near(double phi, double target, double radius, double halfWidth) {
        double diff = Math.abs(phi - target);
        diff = Math.min(diff, 2 * Math.PI - diff);
        return diff * radius <= halfWidth;
    }

    private BlockState outerWall(int x, int y, int z, double phi) {
        if (y >= 0 && y < 3 && near(phi, entrancePhase(), ReliquaryLayout.OUTER, 1.6)) {
            return AIR;
        }
        for (int k = 0; k < plan.chambers() - 1; k++) {
            for (int w = 1; w <= 3; w++) {
                double wp = w * Math.PI / 2;
                double s = plan.ramp(k, wp);
                if (near(phi, wp, ReliquaryLayout.OUTER, 1.1) && y >= s + 1 && y < s + 4) {
                    return AIR;
                }
            }
        }
        if (Math.floorMod(y + 1, ReliquaryLayout.STOREY) == 0) {
            return ModBlocks.STARSTEEL_BLOCK.get().defaultBlockState();
        }
        if (x == 0 || z == 0 || Math.abs(x) == Math.abs(z)) {
            return quartz();
        }
        return stone();
    }

    /** Where the way in cuts the outer wall: just before the ramp's foot, under its first turn's high end. */
    private static double entrancePhase() {
        return 2 * Math.PI - 0.45;
    }

    private BlockState gallery(int y, double phi) {
        for (int k = 0; k < plan.chambers() - 1; k++) {
            double s = plan.ramp(k, phi);
            double t = Math.round(s * 2) / 2.0;
            if (t == Math.floor(t)) {
                if (y == (int) t - 1) {
                    return stone();
                }
            } else if (y == (int) Math.floor(t)) {
                return ModBlocks.STARFALL_STONE_BRICK_SET.slab().get().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
            }
        }
        // a landing at the top, past the lens room's door, closed off at its far end
        int lensFloor = plan.floorY(plan.lensChamber());
        if (phi < Math.PI / 3 && y == lensFloor) {
            return stone();
        }
        if (phi >= Math.PI / 3 && phi < Math.PI / 3 + 0.12 && y > lensFloor && y < plan.topY()) {
            return stone();
        }
        return AIR;
    }

    private BlockState innerWall(int y, double phi) {
        for (int c = 0; c < plan.chambers(); c++) {
            int fy = ReliquaryLayout.STOREY * c;
            if (y >= fy && y < fy + 3 && near(phi, 0.0, ReliquaryLayout.INNER_WALL, 1.6)) {
                return AIR;
            }
            // slits between each hall and the ramp, for daylight
            if (y >= fy + 2 && y < fy + 4 && near(phi, Math.PI, ReliquaryLayout.INNER_WALL, 0.6)) {
                return AIR;
            }
        }
        if (Math.floorMod(y + 1, ReliquaryLayout.STOREY) == 0) {
            return polished();
        }
        return near(phi, Math.PI / 2, ReliquaryLayout.INNER_WALL, 0.5) || near(phi, 3 * Math.PI / 2, ReliquaryLayout.INNER_WALL, 0.5)
                ? quartz() : stone();
    }

    private BlockState chamber(int x, int y, int z, double d) {
        int c = Math.floorDiv(y + 1, ReliquaryLayout.STOREY);
        int ly = y - ReliquaryLayout.STOREY * c;
        boolean lens = c == plan.lensChamber();
        if (ly == -1) {
            return floor(x, z, d, lens);
        }
        if (c == 0) {
            // the entrance hall: a quartz crystal in the middle and four Starbloom beds
            if (d <= 1.0 && ly < 3 || d < 0.5 && ly == 3) {
                return quartz();
            }
            if (ly == 0 && Math.abs(x) == 5 && Math.abs(z) == 5) {
                return ModBlocks.STARBLOOM.get().defaultBlockState();
            }
            return AIR;
        }
        if (!lens) {
            // a guard hall: quartz clusters in the corners
            if ((Math.abs(x) == 5 && Math.abs(z) == 5) && ly < 2) {
                return quartz();
            }
        }
        return AIR;
    }

    private BlockState roof(int x, int y, int z, double d) {
        int top = plan.topY();
        int h = plan.roof();
        int ly = y - top;
        if (ly > h + 1) {
            return AIR;
        }
        if (ly == h + 1) {
            return d < 0.5 ? quartz() : AIR;
        }
        double rc = ReliquaryLayout.OUTER * (1.0 - ly / (double) (h + 1));
        if (d > rc) {
            return AIR;
        }
        if (ly == 1 && d > rc - 1.3) {
            return StructureRegistry.SUNSTONE.get().defaultBlockState();
        }
        if (d > rc - 1.3) {
            if (ly % 4 == 2 && (x == 0 || z == 0)) {
                return AIR; // small windows in the cone
            }
            return x == 0 || z == 0 || Math.abs(x) == Math.abs(z) ? quartz() : stone();
        }
        return AIR;
    }

    /** For tests: the block the plan puts at (x, y, z) relative to the entrance floor's axis (inside the shell). */
    public BlockState planned(int x, int y, int z) {
        double d = Math.sqrt(x * x + z * z);
        return y == -1 ? base(x, z, d) : shell(x, y, z, d);
    }
}
