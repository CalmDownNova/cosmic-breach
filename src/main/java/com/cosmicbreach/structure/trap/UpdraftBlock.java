package com.cosmicbreach.structure.trap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A gravity lift's updraft (GDD 6.1, the Gyre Observatory's floors): an invisible column of rising air shown by
 * drifting motes. Inside it you rise to the floor above; sneaking sinks you slowly instead. The column's
 * {@link #TOP} blocks nudge riders {@link #FACING}, off the shaft and onto the landing. Runs on both sides (the
 * client moves its own player).
 */
public class UpdraftBlock extends Block implements LiquidBlockContainer {
    public static final BooleanProperty TOP = BooleanProperty.create("top");
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** Upward speed a rider reaches, blocks per tick. */
    public static final double RISE = 0.42;
    private static final double LIFT = 0.1;
    private static final double SINK = -0.12;

    public UpdraftBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TOP, false).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TOP, FACING);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    // water and lava flow round it, never wash it away, and no bucket can be emptied into it
    @Override
    public boolean canPlaceLiquid(@Nullable Player player, BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
        return false;
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid) {
        return false;
    }

    @Override
    protected boolean canBeReplaced(BlockState state, Fluid fluid) {
        return false;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!(entity instanceof LivingEntity) && !(entity instanceof ItemEntity)) {
            return;
        }
        Vec3 v = entity.getDeltaMovement();
        double vy = entity.isShiftKeyDown() ? Math.max(v.y, SINK) : Math.min(Math.max(v.y, 0.0) + LIFT, RISE);
        double vx = v.x + (pos.getX() + 0.5 - entity.getX()) * 0.04;
        double vz = v.z + (pos.getZ() + 0.5 - entity.getZ()) * 0.04;
        if (state.getValue(TOP) && !entity.isShiftKeyDown()) {
            Direction out = state.getValue(FACING);
            vx = v.x + out.getStepX() * 0.09;
            vz = v.z + out.getStepZ() * 0.09;
            vy = Math.min(vy, 0.25);
        }
        entity.setDeltaMovement(vx, vy, vz);
        entity.resetFallDistance();
        if (level.isClientSide && entity instanceof LivingEntity living) {
            UpdraftRiders.touch(living, level.getGameTime());
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.WHITE_ASH, pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(),
                    pos.getZ() + random.nextDouble(), 0.0, 0.25, 0.0);
        }
        if (random.nextInt(6) == 0) {
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY(),
                    pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0.0, 0.12, 0.0);
        }
    }
}
