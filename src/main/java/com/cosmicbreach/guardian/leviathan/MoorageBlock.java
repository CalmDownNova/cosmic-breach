package com.cosmicbreach.guardian.leviathan;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * What a Moorage lays down for the time it holds (Thalassine Leviathan design v1, "Moorage"), both indestructible and
 * both gone on their own when no moored Leviathan holds them (after a reset, a reload or a crash mid-Moorage):
 * <ul>
 *   <li>{@link #coil}: the coil's walkable back, an invisible solid block along the top of the moored body, since
 *       Minecraft doesn't carry players on entities and the body holds still anyway.</li>
 *   <li>{@link #bridge}: Driftwood grown out from the nearest platforms to the coil.</li>
 * </ul>
 * Each checks every {@value #CHECK_TICKS} ticks for a moored Leviathan within {@value #HOLD_RANGE} blocks.
 */
public class MoorageBlock extends Block {
    public static final MapCodec<MoorageBlock> COIL_CODEC = simpleCodec(p -> new MoorageBlock(true, p));
    public static final MapCodec<MoorageBlock> BRIDGE_CODEC = simpleCodec(p -> new MoorageBlock(false, p));
    public static final int CHECK_TICKS = 40;
    public static final double HOLD_RANGE = 48.0;

    private final boolean coil;

    public MoorageBlock(boolean coil, BlockBehaviour.Properties properties) {
        super(properties);
        this.coil = coil;
    }

    public static MoorageBlock coil(BlockBehaviour.Properties properties) {
        return new MoorageBlock(true, properties);
    }

    public static MoorageBlock bridge(BlockBehaviour.Properties properties) {
        return new MoorageBlock(false, properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return coil ? COIL_CODEC : BRIDGE_CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return coil ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return coil;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return coil ? 1.0f : super.getShadeBrightness(state, level, pos);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide()) {
            level.scheduleTick(pos, this, CHECK_TICKS);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (held(level, pos)) {
            level.scheduleTick(pos, this, CHECK_TICKS);
        } else {
            level.removeBlock(pos, false);
        }
    }

    /** True while a moored Leviathan is near enough to hold this block. */
    static boolean held(Level level, BlockPos pos) {
        for (ThalassineLeviathan l : level.getEntitiesOfClass(ThalassineLeviathan.class, new AABB(pos).inflate(HOLD_RANGE))) {
            if (l.holdsMoorage()) {
                return true;
            }
        }
        return false;
    }
}
