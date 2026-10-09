package com.cosmicbreach.world.feature;

import com.cosmicbreach.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A carpet of Neon Lichen on the Lichen Gardens' ground (Aetheria 1.2): a ragged patch 4 to 9 blocks across, magenta or
 * teal with a few of the other colour mixed in, lying on the top faces of Umbral Basalt. The carpets are the Gardens'
 * light; the ground between them stays dark. Place it at an air block standing on Umbral Basalt.
 */
public final class LichenCarpetFeature extends Feature<NoneFeatureConfiguration> {
    public LichenCarpetFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();
        Block magenta = ModBlocks.MAGENTA_NEON_LICHEN.get();
        Block teal = ModBlocks.TEAL_NEON_LICHEN.get();
        // one colour per drift about 28 blocks across, so the carpets come in big patches rather than speckle
        long cell = (Math.floorDiv(origin.getX(), 28) * 341873128712L) ^ (Math.floorDiv(origin.getZ(), 28) * 132897987541L);
        Block main = ((cell ^ (cell >>> 29)) & 1) == 0 ? magenta : teal;
        Block other = main == magenta ? teal : magenta;
        int r = 3 + random.nextInt(4);
        double stretch = 0.7 + random.nextDouble() * 0.6;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int placed = 0;
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                double d = Math.sqrt(dx * dx * stretch + dz * dz / stretch);
                if (d > r + random.nextDouble() * 1.5 || random.nextFloat() < 0.08f) {
                    continue;
                }
                // the ground near the origin's height
                for (int dy = 3; dy >= -3; dy--) {
                    pos.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).is(ZoneBlocks.DEEP_STONE)) {
                        Block b = random.nextFloat() < 0.03f ? other : main;
                        level.setBlock(pos, b.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(Direction.DOWN), true),
                                Block.UPDATE_CLIENTS);
                        placed++;
                        break;
                    }
                }
            }
        }
        return placed > 0;
    }
}
