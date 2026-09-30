package com.cosmicbreach.onboarding;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The Breach (GDD 1.3): the 2 by 2 middle of an open ring, one hole with Aetheria's sky under it, made of four
 * blocks that each know their {@link Quarter} (drawn by the client's {@code BreachRenderer} as one opening).
 * No collision: a player who steps into any quarter drops into it and falls up ({@link FallUp}). It can't be
 * broken; breaking one of its frames closes all four quarters. The outline (what the crosshair picks) is the
 * whole 2 by 2 from any quarter, so the hole highlights as one.
 */
public class BreachBlock extends BaseEntityBlock {
    public static final MapCodec<BreachBlock> CODEC = simpleCodec(BreachBlock::new);
    /**
     * Where the sky's surface is drawn (and the outline's top): near the rim, as the End portal's is, so the
     * sky fills the opening from any angle.
     */
    public static final double SURFACE = 0.8;

    /** Which block of the 2 by 2 opening this is; the ring's origin is the north-west one. */
    public enum Quarter implements StringRepresentable {
        NORTH_WEST(0, 0), NORTH_EAST(1, 0), SOUTH_WEST(0, 1), SOUTH_EAST(1, 1);

        public final int dx;
        public final int dz;

        Quarter(int dx, int dz) {
            this.dx = dx;
            this.dz = dz;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        /** The quarter at offset (dx, dz) from the origin (each 0 or 1). */
        public static Quarter at(int dx, int dz) {
            return dz == 0 ? (dx == 0 ? NORTH_WEST : NORTH_EAST) : (dx == 0 ? SOUTH_WEST : SOUTH_EAST);
        }
    }

    public static final EnumProperty<Quarter> QUARTER = EnumProperty.create("quarter", Quarter.class);
    private static final VoxelShape[] OUTLINES = new VoxelShape[4];

    static {
        for (Quarter q : Quarter.values()) {
            OUTLINES[q.ordinal()] = Shapes.box(-q.dx, 0.0, -q.dz, 2.0 - q.dx, SURFACE, 2.0 - q.dz);
        }
    }

    public BreachBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(QUARTER, Quarter.NORTH_WEST));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(QUARTER);
    }

    /** The ring's origin (the opening's north-west block) seen from the quarter at {@code pos}. */
    public static BlockPos origin(BlockState state, BlockPos pos) {
        Quarter q = state.getValue(QUARTER);
        return pos.offset(-q.dx, 0, -q.dz);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINES[state.getValue(QUARTER).ordinal()];
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer player && !player.isSpectator()) {
            FallUp.enter(player, pos, origin(state, pos));
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !newState.is(this)) {
            BreachRings.close(level, origin(state, pos));
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected boolean canBeReplaced(BlockState state, Fluid fluid) {
        return false;
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return ItemStack.EMPTY;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BreachBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? createTickerHelper(type, OnboardingRegistry.BREACH_ENTITY.get(), BreachBlockEntity::clientTick) : null;
    }
}
