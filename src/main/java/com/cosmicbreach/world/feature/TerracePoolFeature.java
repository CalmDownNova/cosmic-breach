package com.cosmicbreach.world.feature;

import com.cosmicbreach.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A shallow pool on a Sunfield terrace (GDD 2.2): a rounded patch of water one block deep (two at its
 * heart), tinted pale gold by the biome, only where the terrace is flat and the rim is whole, so the water
 * never spills. The origin is the air block above the terrace.
 */
public final class TerracePoolFeature extends Feature<NoneFeatureConfiguration> {
    public TerracePoolFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();
        int surface = origin.getY() - 1;
        double rx = 2.0 + random.nextDouble() * 2.5;
        double rz = 2.0 + random.nextDouble() * 2.5;
        double angle = random.nextDouble() * Math.PI;
        double c = Math.cos(angle);
        double s = Math.sin(angle);
        List<BlockPos> water = new ArrayList<>();
        List<BlockPos> deep = new ArrayList<>();
        int ir = (int) Math.ceil(Math.max(rx, rz));
        for (int dx = -ir; dx <= ir; dx++) {
            for (int dz = -ir; dz <= ir; dz++) {
                double u = (dx * c + dz * s) / rx;
                double v = (-dx * s + dz * c) / rz;
                double d = u * u + v * v;
                if (d > 1.0) {
                    continue;
                }
                BlockPos p = new BlockPos(origin.getX() + dx, surface, origin.getZ() + dz);
                water.add(p);
                if (d < 0.3) {
                    deep.add(p.below());
                }
            }
        }
        // every pool block must sit in a flat, whole terrace: ground at the surface, air above, rock below
        for (BlockPos p : water) {
            if (!isGround(level.getBlockState(p)) || !level.getBlockState(p.above()).isAir() || !isGround(level.getBlockState(p.below()))) {
                return false;
            }
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos n = p.relative(dir);
                if (!water.contains(n) && !isGround(level.getBlockState(n))) {
                    return false;
                }
            }
        }
        for (BlockPos p : deep) {
            if (!isGround(level.getBlockState(p.below()))) {
                return false;
            }
        }
        BlockState waterState = Blocks.WATER.defaultBlockState();
        for (BlockPos p : water) {
            level.setBlock(p, waterState, Block.UPDATE_CLIENTS);
        }
        for (BlockPos p : deep) {
            level.setBlock(p, waterState, Block.UPDATE_CLIENTS);
        }
        return true;
    }

    private static boolean isGround(BlockState state) {
        return state.is(ModBlocks.GLIMMER_GRASS.get()) || state.is(ModBlocks.STARFALL_STONE.get());
    }
}
