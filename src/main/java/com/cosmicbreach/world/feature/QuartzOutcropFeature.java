package com.cosmicbreach.world.feature;

import com.cosmicbreach.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A Spire Quartz outcrop (GDD 2.2): two to five turquoise crystal points, 1 to 5 tall, splaying out from a
 * shared root in the ground, the main one two wide at its foot. Place it on a surface (the origin is the
 * air block above solid ground).
 */
public final class QuartzOutcropFeature extends Feature<NoneFeatureConfiguration> {
    public QuartzOutcropFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();
        BlockState ground = level.getBlockState(origin.below());
        if (!level.getBlockState(origin).isAir() || !ground.is(ModBlocks.GLIMMER_GRASS.get()) && !ground.is(ModBlocks.STARFALL_STONE.get())) {
            return false;
        }
        BlockState quartz = ModBlocks.SPIRE_QUARTZ.get().defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int points = 2 + random.nextInt(4);
        for (int p = 0; p < points; p++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double splay = p == 0 ? 0.0 : 0.45 + random.nextDouble() * 0.45;
            double dx = Math.cos(angle) * splay;
            double dz = Math.sin(angle) * splay;
            int height = p == 0 ? 3 + random.nextInt(3) : 1 + random.nextInt(3);
            double x = origin.getX() + 0.5 + (p == 0 ? 0 : Math.cos(angle) * 0.9);
            double z = origin.getZ() + 0.5 + (p == 0 ? 0 : Math.sin(angle) * 0.9);
            for (int i = -1; i < height; i++) {
                pos.set((int) Math.floor(x + dx * i), origin.getY() + i, (int) Math.floor(z + dz * i));
                BlockState at = level.getBlockState(pos);
                if (i < 0 ? at.isAir() : !at.isAir()) {
                    continue;
                }
                level.setBlock(pos, quartz, Block.UPDATE_CLIENTS);
                // the lower half of the main point is two wide
                if (p == 0 && i < height / 2) {
                    BlockPos side = pos.relative(net.minecraft.core.Direction.Plane.HORIZONTAL.getRandomDirection(random));
                    if (level.getBlockState(side).isAir() && !level.getBlockState(side.below()).isAir()) {
                        level.setBlock(side, quartz, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        return true;
    }
}
