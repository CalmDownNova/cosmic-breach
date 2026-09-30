package com.cosmicbreach.structure.sanctum;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Sanctum Gate (GDD 6.1): gilded doors while shut; while {@link #OPEN}, a veil of light that only players the Gate
 * lets through ({@link SanctumPasses}) walk into. Everyone and everything else, mobs and arrows too, still meets a
 * wall. The collision depends on who asks, so the block has a dynamic shape (like powder snow).
 */
public class SanctumGateBlock extends Block {
    public static final MapCodec<SanctumGateBlock> CODEC = simpleCodec(SanctumGateBlock::new);
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    public SanctumGateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(OPEN, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OPEN);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(OPEN) && context instanceof EntityCollisionContext ec) {
            Entity entity = ec.getEntity();
            if (entity instanceof Player player && SanctumPasses.passes(player)) {
                return Shapes.empty();
            }
        }
        return Shapes.block();
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? Shapes.empty() : Shapes.block();
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacent, Direction side) {
        return adjacent.is(this) && adjacent.getValue(OPEN) == state.getValue(OPEN) || super.skipRendering(state, adjacent, side);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(OPEN);
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0f;
    }
}
