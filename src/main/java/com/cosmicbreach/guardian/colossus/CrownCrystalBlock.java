package com.cosmicbreach.guardian.colossus;

import com.cosmicbreach.guardian.GuardianRegistry;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
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
import org.jetbrains.annotations.Nullable;

/**
 * A crown crystal (Prism Colossus design v1): 2 by 2 blocks and 4 tall, indestructible, one of six round the crown
 * arena. {@link #SECTION} is the block's layer (3 is the stepped point), {@link #QUADRANT} which of the four
 * columns it is (0 north-west, 1 north-east, 2 south-west, 3 south-east). The lowest north-west block is the
 * crystal's controller: its {@link CrownCrystalBlockEntity} keeps the crystal's index and the target it points at.
 * {@link #LIT} shows while it carries a Refraction beam.
 */
public class CrownCrystalBlock extends BaseEntityBlock {
    public static final MapCodec<CrownCrystalBlock> CODEC = simpleCodec(CrownCrystalBlock::new);
    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    public static final IntegerProperty SECTION = IntegerProperty.create("section", 0, CrownArena.CRYSTAL_HEIGHT - 1);
    public static final IntegerProperty QUADRANT = IntegerProperty.create("quadrant", 0, 3);

    public CrownCrystalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false).setValue(SECTION, 0).setValue(QUADRANT, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, SECTION, QUADRANT);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    public static boolean isController(BlockState state) {
        return state.getValue(SECTION) == 0 && state.getValue(QUADRANT) == 0;
    }

    /** The state of one of a crystal's blocks: {@code dx}, {@code dz} 0 or 1 from its base, {@code dy} its layer. */
    public static BlockState stateFor(BlockState base, int dx, int dy, int dz, boolean lit) {
        return base.setValue(SECTION, dy).setValue(QUADRANT, dx + 2 * dz).setValue(LIT, lit);
    }

    /** The controller's position for any of the crystal's blocks. */
    public static BlockPos controllerOf(BlockPos pos, BlockState state) {
        int q = state.getValue(QUADRANT);
        return pos.offset(-(q & 1), -state.getValue(SECTION), -(q >> 1));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isController(state) ? new CrownCrystalBlockEntity(pos, state) : null;
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return null;
    }

    /** Lights or darkens all 16 blocks of the crystal whose controller is at {@code base}. */
    public static void setLit(Level level, BlockPos base, boolean lit) {
        for (int dy = 0; dy < CrownArena.CRYSTAL_HEIGHT; dy++) {
            for (int dz = 0; dz < 2; dz++) {
                for (int dx = 0; dx < 2; dx++) {
                    BlockPos p = base.offset(dx, dy, dz);
                    BlockState s = level.getBlockState(p);
                    if (s.is(GuardianRegistry.CROWN_CRYSTAL.get()) && s.getValue(LIT) != lit) {
                        level.setBlock(p, s.setValue(LIT, lit), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }
}
