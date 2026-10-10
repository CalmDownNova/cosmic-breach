package com.cosmicbreach.guardian.unsung;

import com.cosmicbreach.guardian.GuardianAltarBlockEntity;
import com.cosmicbreach.registry.ModBlocks;
import com.cosmicbreach.structure.crypt.CryptNaveLink;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Builds a {@link NaveLayout} into the world, all of it or the part inside a box (a chunk, during world generation):
 * Umbral Basalt walls, columns and dome, brick stairs down the gable, brick wall posts for the pinnacles, lit lichen
 * glass in the windows and the rose, the lichen, the circles of silence, and the Hymnal Altar (set up with its lair,
 * so it registers the lair and raises the sleeping choir).
 */
public final class NaveBuilder {
    private NaveBuilder() {
    }

    /** Builds the part of {@code layout} inside {@code clip}. Flags 2: no neighbour updates, clients told. */
    public static void build(WorldGenLevel level, NaveLayout layout, BoundingBox clip) {
        build(level, layout, clip, List.of());
    }

    /**
     * Builds the part of {@code layout} inside {@code clip}, leaving what each {@link CryptNaveLink} spares (a crypt
     * sharing the platform keeps its rooms and its way in) and laying the floor of any lane past the crypt's roof.
     */
    public static void build(WorldGenLevel level, NaveLayout layout, BoundingBox clip, List<CryptNaveLink> crypts) {
        int[] b = layout.bounds();
        int x0 = Math.max(b[0], clip.minX());
        int x1 = Math.min(b[3], clip.maxX());
        int z0 = Math.max(b[2], clip.minZ());
        int z1 = Math.min(b[5], clip.maxZ());
        int y0 = Math.max(Math.max(b[1], clip.minY()), level.getMinBuildHeight());
        int y1 = Math.min(Math.min(b[4], clip.maxY()), level.getMaxBuildHeight() - 1);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        States s = new States(layout);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int y = y0; y <= y1; y++) {
                    NaveLayout.Kind kind = layout.kind(x, y, z);
                    if (spared(crypts, x, y, z)) {
                        continue;
                    }
                    pos.set(x, y, z);
                    if (carved(crypts, x, y, z)) {
                        // a crypt's lane: air
                        if (!level.getBlockState(pos).isAir()) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                        }
                        continue;
                    }
                    if (kind == NaveLayout.Kind.KEEP) {
                        continue;
                    }
                    if (kind == NaveLayout.Kind.AIR && level.getBlockState(pos).isAir()) {
                        continue;
                    }
                    level.setBlock(pos, s.of(kind, x, y, z), Block.UPDATE_CLIENTS);
                    if (kind == NaveLayout.Kind.ALTAR && level.getBlockEntity(pos) instanceof GuardianAltarBlockEntity altar) {
                        altar.setup(layout.arena().centreBlock());
                    }
                }
            }
        }
        for (NaveLayout.Lichen l : layout.lichen()) {
            if (!clip.isInside(l.x(), l.y(), l.z())) {
                continue;
            }
            pos.set(l.x(), l.y(), l.z());
            if (!level.getBlockState(pos).isAir() || spared(crypts, l.x(), l.y(), l.z()) || carved(crypts, l.x(), l.y(), l.z())) {
                continue;
            }
            Direction side = switch (l.side()) {
                case 0 -> Direction.EAST;
                case 1 -> Direction.SOUTH;
                case 2 -> Direction.WEST;
                default -> Direction.NORTH;
            };
            Block lichen = l.magenta() ? ModBlocks.MAGENTA_NEON_LICHEN.get() : ModBlocks.TEAL_NEON_LICHEN.get();
            level.setBlock(pos, lichen.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(side), true), Block.UPDATE_CLIENTS);
        }
        clearCryptWays(level, layout, crypts, clip);
    }

    private static void clearBlock(WorldGenLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).isAir()) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private static boolean carved(List<CryptNaveLink> crypts, int x, int y, int z) {
        for (CryptNaveLink c : crypts) {
            if (c.carves(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    private static boolean spared(List<CryptNaveLink> crypts, int x, int y, int z) {
        for (CryptNaveLink c : crypts) {
            if (c.spares(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    /**
     * For each crypt sharing the platform, wherever it lies (also past the nave's own bounds): clears the room its
     * gatehouse would have stood in, where the nave has built nothing, and its lane, and lays the lane's floor where it
     * runs past the crypt's roof over open air.
     */
    private static void clearCryptWays(WorldGenLevel level, NaveLayout layout, List<CryptNaveLink> crypts, BoundingBox clip) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (CryptNaveLink c : crypts) {
            int[] g = c.gatehouseBox();
            for (int x = Math.max(g[0], clip.minX()); x <= Math.min(g[3], clip.maxX()); x++) {
                for (int z = Math.max(g[2], clip.minZ()); z <= Math.min(g[5], clip.maxZ()); z++) {
                    for (int y = Math.max(g[1], clip.minY()); y <= Math.min(g[4], clip.maxY()); y++) {
                        if (layout.kind(x, y, z) == NaveLayout.Kind.KEEP || c.carves(x, y, z)) {
                            clearBlock(level, pos.set(x, y, z));
                        }
                    }
                }
            }
            for (int[] col : c.laneColumns()) {
                for (int y = c.roofY() + 1; y <= c.roofY() + CryptNaveLink.HEAD; y++) {
                    if (clip.isInside(col[0], y, col[1])) {
                        clearBlock(level, pos.set(col[0], y, col[1]));
                    }
                }
            }
            for (int[] col : c.laneColumns()) {
                if (clip.isInside(col[0], c.roofY(), col[1]) && c.laneFloor(col[0], col[1])
                        && level.getBlockState(pos.set(col[0], c.roofY(), col[1])).isAir()) {
                    level.setBlock(pos, ModBlocks.POLISHED_UMBRAL_BASALT.get().defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** Block states for each kind. */
    private static final class States {
        final BlockState polished = ModBlocks.POLISHED_UMBRAL_BASALT.get().defaultBlockState();
        final BlockState bricks = ModBlocks.UMBRAL_BASALT_BRICKS.get().defaultBlockState();
        final BlockState basalt = ModBlocks.UMBRAL_BASALT.get().defaultBlockState();
        final BlockState step = ModBlocks.POLISHED_UMBRAL_BASALT_SET.slab().get().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        final BlockState brickSlab = ModBlocks.UMBRAL_BASALT_BRICK_SET.slab().get().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        final BlockState stairs = ModBlocks.UMBRAL_BASALT_BRICK_SET.stairs().get().defaultBlockState();
        final BlockState post = ModBlocks.UMBRAL_BASALT_BRICK_SET.wall() != null
                ? ModBlocks.UMBRAL_BASALT_BRICK_SET.wall().get().defaultBlockState() : ModBlocks.UMBRAL_BASALT_BRICKS.get().defaultBlockState();
        final BlockState glass = ModBlocks.RIFT_GLASS.get().defaultBlockState();
        final BlockState circle = UnsungRegistry.SILENCE_CIRCLE.get().defaultBlockState();
        final BlockState window = UnsungRegistry.LICHEN_WINDOW.get().defaultBlockState().setValue(LichenWindowBlock.LIT, true);
        /** The gable's stairs on each side, rising toward the ridge. */
        final BlockState slopeRight;
        final BlockState slopeLeft;
        final BlockState altar;
        final NaveLayout layout;

        States(NaveLayout layout) {
            this.layout = layout;
            int[] a = layout.axis();
            Direction toDoor = Direction.fromDelta(a[0], 0, a[1]);
            this.altar = UnsungRegistry.HYMNAL_ALTAR.get().defaultBlockState().setValue(HymnalAltarBlock.FACING, toDoor);
            // +v runs (-a.z, a.x): on the +v side the stairs face -v, toward the ridge
            this.slopeRight = stairs.setValue(StairBlock.FACING, Direction.fromDelta(a[1], 0, -a[0])).setValue(StairBlock.HALF, Half.BOTTOM);
            this.slopeLeft = stairs.setValue(StairBlock.FACING, Direction.fromDelta(-a[1], 0, a[0])).setValue(StairBlock.HALF, Half.BOTTOM);
        }

        private int noise(int x, int y, int z) {
            long h = layout.seed() ^ (x * 73856093L) ^ (y * 19349663L) ^ (z * 83492791L);
            h ^= h >>> 17;
            h *= 0xED5AD4BBL;
            h ^= h >>> 11;
            return (int) (Math.floorMod(h, 100L));
        }

        BlockState of(NaveLayout.Kind kind, int x, int y, int z) {
            return switch (kind) {
                case KEEP, AIR -> Blocks.AIR.defaultBlockState();
                case FLOOR, DAIS, WALL_BASE, RIB, ROOF_RIB, KEEL_BAND -> polished;
                case FLOOR_BAND, DAIS_EDGE, ROOF -> bricks;
                case WALL -> noise(x, y, z) < 14 ? basalt : bricks;
                case STEP -> step;
                case CIRCLE -> circle;
                case COLUMN, KEEL -> basalt.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
                case WINDOW -> glass;
                case LICHEN_WINDOW_MAGENTA -> window.setValue(LichenWindowBlock.HUE, LichenWindowBlock.Hue.MAGENTA);
                case LICHEN_WINDOW_TEAL, NAVE_WINDOW_TEAL -> window.setValue(LichenWindowBlock.HUE, LichenWindowBlock.Hue.TEAL);
                case NAVE_WINDOW_MAGENTA -> window.setValue(LichenWindowBlock.HUE, LichenWindowBlock.Hue.MAGENTA);
                case ROOF_SLOPE -> layout.local(x, z)[1] > 0 ? slopeRight : slopeLeft;
                case RIDGE -> polished;
                case SPIRE -> bricks;
                case PINNACLE -> post;
                case ALTAR -> altar;
                case RUBBLE -> basalt.setValue(RotatedPillarBlock.AXIS, noise(x, y, z) < 50 ? Direction.Axis.X : Direction.Axis.Z);
                case RUBBLE_SLAB -> brickSlab;
            };
        }
    }
}
