package com.cosmicbreach.world.feature;

import com.cosmicbreach.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A neon crystal chandelier in the Deep (GDD 2.2): from the rock above, a short chain, then a ring of Rift
 * Glass pendants around a longer central one, their sides grown with magenta and teal Neon Lichen so the
 * whole cluster glows. Place it at an air block right under a ceiling.
 */
public final class CrystalChandelierFeature extends Feature<NoneFeatureConfiguration> {
    public CrystalChandelierFeature() {
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
        int chain = 1 + random.nextInt(4);
        int room = 0;
        while (room < chain + 9 && level.getBlockState(origin.below(room)).isAir()) {
            room++;
        }
        if (room < chain + 5) {
            return false;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState chainState = Blocks.CHAIN.defaultBlockState();
        for (int i = 0; i < chain; i++) {
            level.setBlock(pos.set(origin.getX(), origin.getY() - i, origin.getZ()), chainState, Block.UPDATE_CLIENTS);
        }
        BlockPos hub = origin.below(chain);
        BlockState glass = ModBlocks.RIFT_GLASS.get().defaultBlockState();
        // the hub: a small cross of glass
        level.setBlock(hub, glass, Block.UPDATE_CLIENTS);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            level.setBlock(hub.relative(d), glass, Block.UPDATE_CLIENTS);
        }
        // pendants: one long in the middle, one on each arm
        pendant(level, hub.below(), 3 + random.nextInt(3), random);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (random.nextFloat() < 0.85f) {
                pendant(level, hub.relative(d).below(), 1 + random.nextInt(3), random);
            }
        }
        return true;
    }

    private static void pendant(WorldGenLevel level, BlockPos top, int length, RandomSource random) {
        BlockState glass = ModBlocks.RIFT_GLASS.get().defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < length; i++) {
            pos.set(top.getX(), top.getY() - i, top.getZ());
            if (!level.getBlockState(pos).isAir()) {
                return;
            }
            level.setBlock(pos, glass, Block.UPDATE_CLIENTS);
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (random.nextFloat() < 0.4f) {
                    BlockPos side = pos.relative(d);
                    if (level.getBlockState(side).isAir()) {
                        Block lichen = random.nextBoolean() ? ModBlocks.MAGENTA_NEON_LICHEN.get() : ModBlocks.TEAL_NEON_LICHEN.get();
                        BlockState face = lichen.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(d.getOpposite()), true);
                        level.setBlock(side, face, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }
}
