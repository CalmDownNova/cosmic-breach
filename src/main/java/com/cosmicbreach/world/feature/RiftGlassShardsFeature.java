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
 * Rift Glass shards on the Shattered Field's chunks (Aetheria 1.2): one to three thin spikes of dark glass, 1 to 4
 * blocks tall, jutting from the basalt as if the field were glass that broke. Place it at an air block standing on
 * Umbral Basalt.
 */
public final class RiftGlassShardsFeature extends Feature<NoneFeatureConfiguration> {
    public RiftGlassShardsFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();
        if (!level.getBlockState(origin.below()).is(ZoneBlocks.DEEP_STONE)) {
            return false;
        }
        BlockState glass = ModBlocks.RIFT_GLASS.get().defaultBlockState();
        int spikes = 1 + random.nextInt(3);
        int placed = 0;
        for (int s = 0; s < spikes; s++) {
            BlockPos base = s == 0 ? origin : origin.offset(random.nextInt(3) - 1, 0, random.nextInt(3) - 1);
            if (!level.getBlockState(base.below()).is(ZoneBlocks.DEEP_STONE)) {
                continue;
            }
            int height = 1 + random.nextInt(s == 0 ? 4 : 2);
            for (int y = 0; y < height; y++) {
                BlockPos p = base.above(y);
                if (!level.getBlockState(p).isAir()) {
                    break;
                }
                level.setBlock(p, glass, Block.UPDATE_CLIENTS);
                placed++;
            }
        }
        return placed > 0;
    }
}
