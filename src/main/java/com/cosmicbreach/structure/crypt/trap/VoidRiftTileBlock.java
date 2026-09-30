package com.cosmicbreach.structure.crypt.trap;

import com.cosmicbreach.structure.crypt.CryptRegistry;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A Void Rift tile (GDD 6.4): to anyone not looking carefully, a floor tile like the others. Nine make a cluster;
 * {@link #PART} says where in it this one lies ({@code (dx + 1) + 3 (dz + 1)} from the middle), and the middle one
 * keeps the cluster's timer ({@link VoidRiftBlockEntity}). {@link #OPEN}: the patch has vanished, nothing to stand on.
 */
public class VoidRiftTileBlock extends BaseEntityBlock {
    public static final MapCodec<VoidRiftTileBlock> CODEC = simpleCodec(VoidRiftTileBlock::new);
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 8);
    public static final int MIDDLE = 4;

    public VoidRiftTileBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(OPEN, false).setValue(PART, MIDDLE));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OPEN, PART);
    }

    /** The cluster's middle tile for the tile at {@code pos}. */
    public static BlockPos middle(BlockPos pos, BlockState state) {
        int part = state.getValue(PART);
        return pos.offset(1 - part % 3, 0, 1 - part / 3);
    }

    /** The state of the tile {@code dx, dz} (-1 to 1) from the middle. */
    public BlockState part(int dx, int dz) {
        return defaultBlockState().setValue(PART, (dx + 1) + 3 * (dz + 1));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return state.getValue(OPEN) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? Shapes.empty() : Shapes.block();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? Shapes.empty() : Shapes.block();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(OPEN);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == MIDDLE ? new VoidRiftBlockEntity(pos, state) : null;
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || state.getValue(PART) != MIDDLE ? null
                : createTickerHelper(type, CryptRegistry.VOID_RIFT_ENTITY.get(), VoidRiftBlockEntity::serverTick);
    }
}
