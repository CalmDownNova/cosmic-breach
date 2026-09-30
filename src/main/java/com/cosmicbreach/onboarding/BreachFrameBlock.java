package com.cosmicbreach.onboarding;

import com.cosmicbreach.world.AetheriaWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * One piece of a Breach Ring (GDD 1.3; W1 made the block, W4 the ring). {@link #ACTIVE} while its ring is
 * open: the frame glows (light 10, drawn full bright). Breaking an active frame closes its Breach. In
 * Aetheria an open ring is a Landing's way home, so there its frames can't be broken in survival or blown
 * up.
 */
public class BreachFrameBlock extends Block {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final int ACTIVE_LIGHT = 10;

    public BreachFrameBlock(Properties properties) {
        super(properties.lightLevel(state -> state.getValue(ACTIVE) ? ACTIVE_LIGHT : 0)
                .emissiveRendering((state, level, pos) -> state.getValue(ACTIVE)));
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && state.getValue(ACTIVE) && !newState.is(this)) {
            BreachRings.closeAround(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (anchored(state, level)) {
            return 0.0f;
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return anchored(state, level) ? 3_600_000.0f : super.getExplosionResistance(state, level, pos, explosion);
    }

    /** An open ring's frame in Aetheria: a way home that must not be lost. */
    private static boolean anchored(BlockState state, BlockGetter level) {
        return state.getValue(ACTIVE) && level instanceof Level l && AetheriaWorld.is(l);
    }
}
