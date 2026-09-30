package com.cosmicbreach.structure.sanctum;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/**
 * An Eclipse Lock (GDD 6.1): a dark disc in the Throne Seal's wall that burns gold once its wing's puzzle is solved.
 * {@link SanctumLocks} lights it; nothing puts it out.
 */
public class EclipseLockBlock extends Block {
    public static final MapCodec<EclipseLockBlock> CODEC = simpleCodec(EclipseLockBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty LIT = BooleanProperty.create("lit");

    public EclipseLockBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT) || random.nextInt(2) != 0) {
            return;
        }
        Direction f = state.getValue(FACING);
        double a = random.nextDouble() * Math.PI * 2;
        double x = pos.getX() + 0.5 + f.getStepX() * 0.56 + Math.cos(a) * 0.4 * Math.abs(f.getStepZ());
        double z = pos.getZ() + 0.5 + f.getStepZ() * 0.56 + Math.cos(a) * 0.4 * Math.abs(f.getStepX());
        double y = pos.getY() + 0.5 + Math.sin(a) * 0.4;
        level.addParticle(ParticleTypes.SMALL_FLAME, x, y, z, f.getStepX() * 0.01, 0.01, f.getStepZ() * 0.01);
    }
}
