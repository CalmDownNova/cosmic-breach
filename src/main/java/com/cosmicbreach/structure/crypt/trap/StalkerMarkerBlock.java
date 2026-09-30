package com.cosmicbreach.structure.crypt.trap;

import com.cosmicbreach.structure.crypt.CryptGuards;
import com.cosmicbreach.structure.crypt.CryptRegistry;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A Hollow Stalker's spot in a crypt hall (GDD 7.1): invisible, in the dark. Once {@code cosmicbreach:hollow_stalker}
 * exists, the first time a player comes within {@link Spot#RADIUS} blocks a Stalker steps out of the dark here and
 * the spot is gone; until then it waits.
 */
public class StalkerMarkerBlock extends BaseEntityBlock {
    public static final MapCodec<StalkerMarkerBlock> CODEC = simpleCodec(StalkerMarkerBlock::new);

    public StalkerMarkerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Spot(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, CryptRegistry.STALKER_MARKER_ENTITY.get(), Spot::serverTick);
    }

    /** The spot's memory: nothing but its check. */
    public static class Spot extends BlockEntity {
        public static final double RADIUS = 16.0;

        public Spot(BlockPos pos, BlockState state) {
            super(CryptRegistry.STALKER_MARKER_ENTITY.get(), pos, state);
        }

        static void serverTick(Level level, BlockPos pos, BlockState state, Spot spot) {
            if (!(level instanceof ServerLevel server) || (level.getGameTime() + pos.asLong()) % 20 != 0 || !CryptGuards.stalkerRegistered()) {
                return;
            }
            if (level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, RADIUS, false) == null) {
                return;
            }
            if (CryptGuards.stalker(server, Vec3.atBottomCenterOf(pos)).isPresent()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
        }
    }
}
