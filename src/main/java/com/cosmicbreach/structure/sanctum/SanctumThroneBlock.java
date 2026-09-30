package com.cosmicbreach.structure.sanctum;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Regent's Throne on the arena's dais (GDD 7.3): use a Dying Star Heart on it to set the Heart in its socket
 * ({@link #HEART}), which calls {@link SanctumThrone#onHeartPlaced}.
 */
public class SanctumThroneBlock extends Block {
    public static final MapCodec<SanctumThroneBlock> CODEC = simpleCodec(SanctumThroneBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty HEART = BooleanProperty.create("heart");
    /** The seat and the high back (its back toward the side it doesn't face). */
    private static final VoxelShape[] SHAPES = new VoxelShape[4];

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            VoxelShape seat = Block.box(1, 0, 1, 15, 9, 15);
            VoxelShape back = switch (d) {
                case NORTH -> Block.box(1, 9, 12, 15, 24, 15);
                case SOUTH -> Block.box(1, 9, 1, 15, 24, 4);
                case WEST -> Block.box(12, 9, 1, 15, 24, 15);
                default -> Block.box(1, 9, 1, 4, 24, 15);
            };
            SHAPES[d.get2DDataValue()] = Shapes.or(seat, back);
        }
    }

    public SanctumThroneBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HEART, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HEART);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(SanctumRegistry.DYING_STAR_HEART.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (state.getValue(HEART)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("cosmicbreach.sanctum.throne.busy"), true);
            }
            return ItemInteractionResult.CONSUME;
        }
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
            stack.consume(1, player);
            SanctumThrone.onHeartPlaced(server, pos, sp);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int n = state.getValue(HEART) ? 3 : random.nextInt(4) == 0 ? 1 : 0;
        for (int i = 0; i < n; i++) {
            level.addParticle(state.getValue(HEART) ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME,
                    pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.5, pos.getY() + 1.1 + random.nextDouble() * 0.4,
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.5, 0.0, 0.02 + random.nextDouble() * 0.02, 0.0);
        }
    }
}
