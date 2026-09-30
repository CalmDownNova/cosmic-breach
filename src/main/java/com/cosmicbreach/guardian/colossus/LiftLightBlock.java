package com.cosmicbreach.guardian.colossus;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Crown Spire's lift (Prism Colossus design v1, "The way up"): a column of rising light in the spire's hollow
 * core carries players (not mobs) up to the crown at {@value #RISE_SPEED} blocks a tick (about 10 s), and the
 * topmost block ({@link #TOP}) sets them down on the crown floor toward {@link #FACING}, beside the Prism Altar. A
 * second well of falling light lowers players gently ({@value #FALL_SPEED} a tick) back to the spire's foot.
 * Light without substance: no collision, no outline, it can't be mined.
 */
public class LiftLightBlock extends Block {
    public static final BooleanProperty TOP = BooleanProperty.create("top");
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** Blocks a tick upward in the rising column. */
    public static final double RISE_SPEED = 0.6;
    /** Blocks a tick downward in the falling well. */
    public static final double FALL_SPEED = 0.3;
    /** Leaving the top: a hop up and a push toward the altar. */
    public static final double EXIT_HOP = 0.55;
    public static final double EXIT_PUSH = 0.28;

    private final boolean rising;

    public LiftLightBlock(boolean rising, Properties properties) {
        super(properties);
        this.rising = rising;
        registerDefaultState(stateDefinition.any().setValue(TOP, false).setValue(FACING, Direction.NORTH));
    }

    public static LiftLightBlock rising(BlockBehaviour.Properties properties) {
        return new LiftLightBlock(true, properties);
    }

    public static LiftLightBlock falling(BlockBehaviour.Properties properties) {
        return new LiftLightBlock(false, properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return rising ? RISING_CODEC : FALLING_CODEC;
    }

    private static final MapCodec<LiftLightBlock> RISING_CODEC = simpleCodec(LiftLightBlock::rising);
    private static final MapCodec<LiftLightBlock> FALLING_CODEC = simpleCodec(LiftLightBlock::falling);

    public boolean rising() {
        return rising;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TOP, FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** The column reads as one shaft of light: no faces between two blocks of it. */
    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
        return adjacent.is(this) || super.skipRendering(state, adjacent, direction);
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0f;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!(entity instanceof Player player) || player.isSpectator() || player.getAbilities().flying) {
            return;
        }
        Vec3 v = player.getDeltaMovement();
        if (rising) {
            if (state.getValue(TOP)) {
                Direction out = state.getValue(FACING);
                player.setDeltaMovement(out.getStepX() * EXIT_PUSH, Math.max(v.y, EXIT_HOP), out.getStepZ() * EXIT_PUSH);
            } else {
                // drift to the column's middle a little, so the ride doesn't scrape the shaft's side
                double cx = pos.getX() + 0.5 - player.getX();
                double cz = pos.getZ() + 0.5 - player.getZ();
                player.setDeltaMovement(v.x * 0.6 + cx * 0.02, RISE_SPEED, v.z * 0.6 + cz * 0.02);
            }
        } else {
            player.setDeltaMovement(v.x * 0.7, -FALL_SPEED, v.z * 0.7);
        }
        player.resetFallDistance(); // both sides: the local player's client moves it, the server keeps it safe
    }
}
