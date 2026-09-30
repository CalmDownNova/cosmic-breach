package com.cosmicbreach.world.feature;

import com.cosmicbreach.registry.ModBlocks;
import com.cosmicbreach.world.ShearBand;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A crystal stalactite under a Reach island (GDD 2.2): a tapering cone hanging from the rock above the
 * origin, 4 to 20 long and up to 3 wide at the root. Half are all Spire Quartz, half Starfall Stone with a
 * quartz point. Only fills air, and never reaches into Shear band A. Place it at an air block right under
 * a ceiling (environment scan up, then one down).
 */
public final class CrystalStalactiteFeature extends Feature<NoneFeatureConfiguration> {
    public CrystalStalactiteFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();
        if (!level.getBlockState(origin).isAir() || level.getBlockState(origin.above()).isAir()) {
            return false;
        }
        int room = origin.getY() - (ShearBand.A.maxY + 1);
        int length = Math.min(room, 4 + (int) (16 * Math.pow(random.nextDouble(), 1.6)));
        if (length < 3) {
            return false;
        }
        double rootR = 1.2 + length * 0.09 + random.nextDouble() * 0.9;
        boolean crystal = random.nextBoolean();
        BlockState quartz = ModBlocks.SPIRE_QUARTZ.get().defaultBlockState();
        BlockState stone = ModBlocks.STARFALL_STONE.get().defaultBlockState();
        double ox = (random.nextDouble() - 0.5) * 0.4;
        double oz = (random.nextDouble() - 0.5) * 0.4;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < length; i++) {
            double t = i / (double) length;
            double r = rootR * Math.pow(1.0 - t, 1.3) + 0.35;
            int ir = (int) Math.ceil(r);
            double cx = ox * i;
            double cz = oz * i;
            for (int dx = -ir; dx <= ir; dx++) {
                for (int dz = -ir; dz <= ir; dz++) {
                    double ddx = dx - cx;
                    double ddz = dz - cz;
                    if (ddx * ddx + ddz * ddz > r * r) {
                        continue;
                    }
                    pos.set(origin.getX() + dx, origin.getY() - i, origin.getZ() + dz);
                    if (!level.getBlockState(pos).isAir()) {
                        continue;
                    }
                    boolean tip = t > 0.6;
                    level.setBlock(pos, crystal || tip ? quartz : stone, Block.UPDATE_CLIENTS);
                }
            }
        }
        return true;
    }
}
