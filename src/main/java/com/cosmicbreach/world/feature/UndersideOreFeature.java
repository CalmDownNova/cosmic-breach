package com.cosmicbreach.world.feature;

import com.cosmicbreach.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Eclipsium showing on the undersides of the Shattered Field's chunks (Aetheria 1.2): a cluster of 6 to 14 ore blocks
 * in the stone just over an air block, each with a face open to the air below or beside it, ringed with glowing magenta
 * lichen on the faces around, so a player looking up sees lit points rather than dark rock. Place it at an air block
 * right under the Deep's stone.
 */
public final class UndersideOreFeature extends Feature<NoneFeatureConfiguration> {
    public UndersideOreFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();
        if (!level.getBlockState(origin).isAir() || !level.getBlockState(origin.above()).is(ZoneBlocks.DEEP_STONE)) {
            return false;
        }
        BlockState ore = ModBlocks.ECLIPSIUM_ORE.get().defaultBlockState();
        Block lichen = ModBlocks.MAGENTA_NEON_LICHEN.get();
        int want = 6 + random.nextInt(9);
        int placed = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int tries = 0; tries < want * 4 && placed < want; tries++) {
            pos.set(origin.getX() + random.nextInt(7) - 3, origin.getY() + 1 + random.nextInt(2), origin.getZ() + random.nextInt(7) - 3);
            if (!level.getBlockState(pos).is(ZoneBlocks.DEEP_STONE) || !exposed(level, pos)) {
                continue;
            }
            level.setBlock(pos, ore, Block.UPDATE_CLIENTS);
            placed++;
            // lichen on the open faces of the stone around it
            for (Direction d : new Direction[] {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    BlockPos stone = pos.relative(side);
                    BlockPos air = stone.relative(d);
                    if (random.nextFloat() < 0.35f && level.getBlockState(stone).is(ZoneBlocks.DEEP_STONE) && level.getBlockState(air).isAir()) {
                        level.setBlock(air, lichen.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(d.getOpposite()), true),
                                Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        return placed > 0;
    }

    private static boolean exposed(WorldGenLevel level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            if (d != Direction.UP && level.getBlockState(pos.relative(d)).isAir()) {
                return true;
            }
        }
        return false;
    }
}
