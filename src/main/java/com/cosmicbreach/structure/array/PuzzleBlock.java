package com.cosmicbreach.structure.array;

import com.cosmicbreach.structure.lens.Lens;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A Lens Array block (GDD 6.2): unbreakable, unpushable, one shape. Pieces stand on a pedestal and the beam
 * crosses the pedestal row at {@link #BEAM_HEIGHT} of the block. The puzzle core's headings are Minecraft's
 * horizontal directions (north is -z, east +x): {@link #direction} and {@link #heading} convert.
 */
public class PuzzleBlock extends Block {
    /** Where beams cross a pedestal, as a fraction of the block. */
    public static final double BEAM_HEIGHT = 0.75;
    /** A piece on its pedestal: a column players walk round. */
    public static final VoxelShape PIECE = Block.box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0);

    private final VoxelShape shape;

    public PuzzleBlock(Properties properties, VoxelShape shape) {
        super(properties);
        this.shape = shape;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape;
    }

    /** True for every block of a Lens Array's grid, walls and ceiling. */
    public static boolean isPuzzle(BlockState state) {
        return state.getBlock() instanceof PuzzleBlock || state.getBlock() instanceof LensCoreBlock;
    }

    public static Direction direction(int heading) {
        return switch (heading & 3) {
            case Lens.NORTH -> Direction.NORTH;
            case Lens.EAST -> Direction.EAST;
            case Lens.SOUTH -> Direction.SOUTH;
            default -> Direction.WEST;
        };
    }

    public static int heading(Direction direction) {
        return switch (direction) {
            case EAST -> Lens.EAST;
            case SOUTH -> Lens.SOUTH;
            case WEST -> Lens.WEST;
            default -> Lens.NORTH;
        };
    }
}
