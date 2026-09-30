package com.cosmicbreach.world.feature;

import com.cosmicbreach.registry.ModBlocks;
import com.cosmicbreach.world.gen.AetheriaTerrain;
import com.cosmicbreach.world.gen.Hashing;
import com.cosmicbreach.world.gen.SpireField;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Builds the spires of {@link SpireField} into the chunk being decorated, and only into that chunk: each
 * chunk draws its own slice of every spire (and fallen top) whose bounds touch it, so spires of any size
 * never write outside the area a feature owns. Place it once per chunk (no placement modifiers).
 *
 * <p>Materials: Starfall Stone; a Starsteel Ore core (denser at a snapped stump's broken top, where it
 * shows); Spire Quartz on crystal tips and as rare inclusions on the faces.
 */
public final class SpireFieldFeature extends Feature<NoneFeatureConfiguration> {
    public SpireFieldFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        int x0 = SectionPos.blockToSectionCoord(origin.getX()) << 4;
        int z0 = SectionPos.blockToSectionCoord(origin.getZ()) << 4;
        AetheriaTerrain terrain = AetheriaTerrain.of(level.getLevel().getChunkSource().randomState());
        List<SpireField.Spire> spires = new ArrayList<>();
        terrain.spires.spiresTouching(x0, z0, x0 + 15, z0 + 15, spires);
        boolean any = false;
        for (SpireField.Spire s : spires) {
            any |= drawStanding(level, s, x0, z0);
            if (s.snapped) {
                any |= drawFallen(level, s, x0, z0);
            }
        }
        return any;
    }

    private static boolean drawStanding(WorldGenLevel level, SpireField.Spire s, int x0, int z0) {
        BlockState stone = ModBlocks.STARFALL_STONE.get().defaultBlockState();
        BlockState ore = ModBlocks.STARSTEEL_ORE.get().defaultBlockState();
        BlockState quartz = ModBlocks.SPIRE_QUARTZ.get().defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        double[] cos = new double[s.facets];
        double[] sin = new double[s.facets];
        double inv = 1.0 / Math.cos(Math.PI / s.facets);
        boolean any = false;
        int top = s.groundY + 1 + (int) Math.ceil(s.standing);
        for (int y = Math.max(1, s.minY); y <= Math.min(top, 477); y++) {
            double h = y + 0.5 - (s.groundY + 1);
            double r = s.radiusAt(h);
            double hc = Math.max(h, 0);
            double ax = s.axisX(hc);
            double az = s.axisZ(hc);
            double a0 = s.rotation + s.twist * hc;
            for (int k = 0; k < s.facets; k++) {
                double a = a0 + k * (Math.PI * 2 / s.facets);
                cos[k] = Math.cos(a);
                sin[k] = Math.sin(a);
            }
            int bx0 = Math.max(x0, (int) Math.floor(ax - r * 1.2 - 1));
            int bx1 = Math.min(x0 + 15, (int) Math.ceil(ax + r * 1.2 + 1));
            int bz0 = Math.max(z0, (int) Math.floor(az - r * 1.2 - 1));
            int bz1 = Math.min(z0 + 15, (int) Math.ceil(az + r * 1.2 + 1));
            for (int x = bx0; x <= bx1; x++) {
                for (int z = bz0; z <= bz1; z++) {
                    double dx = x + 0.5 - ax;
                    double dz = z + 0.5 - az;
                    double pd = 0;
                    for (int k = 0; k < s.facets; k++) {
                        double d = dx * cos[k] + dz * sin[k];
                        if (d > pd) {
                            pd = d;
                        }
                    }
                    pd *= inv;
                    if (pd > r) {
                        continue;
                    }
                    long ph = Hashing.hash(s.seed, 7, x, y, z);
                    double cut = s.standing;
                    if (s.snapped) {
                        cut -= 2.5 * Hashing.unit(Hashing.hash(s.seed, 8, x, 0, z));
                    }
                    if (h > cut) {
                        continue;
                    }
                    pos.set(x, y, z);
                    if (h < 0.5 && level.getBlockState(pos).isAir()) {
                        continue; // below the grass line the spire only replaces rock
                    }
                    BlockState state = stone;
                    double core = Math.max(0.9, r * 0.3);
                    if (pd < core && s.height > 30) {
                        double oreChance = s.snapped && h > cut - 5 ? 0.45 : 0.14;
                        if (Hashing.unit(ph) < oreChance) {
                            state = ore;
                        }
                    } else if (s.crystalTip && h > s.height * 0.8) {
                        state = quartz;
                    } else if (pd > r - 1.0 && Hashing.unit(ph, 1) < 0.035) {
                        state = quartz;
                    }
                    level.setBlock(pos, state, Block.UPDATE_CLIENTS);
                    any = true;
                }
            }
        }
        return any;
    }

    private static boolean drawFallen(WorldGenLevel level, SpireField.Spire s, int x0, int z0) {
        BlockState stone = ModBlocks.STARFALL_STONE.get().defaultBlockState();
        BlockState ore = ModBlocks.STARSTEEL_ORE.get().defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        double ex = s.f1x - s.f0x;
        double ey = s.f1y - s.f0y;
        double ez = s.f1z - s.f0z;
        double len2 = ex * ex + ey * ey + ez * ez;
        if (len2 < 1) {
            return false;
        }
        double pad = s.fr0 + 1.5;
        int bx0 = Math.max(x0, (int) Math.floor(Math.min(s.f0x, s.f1x) - pad));
        int bx1 = Math.min(x0 + 15, (int) Math.ceil(Math.max(s.f0x, s.f1x) + pad));
        int bz0 = Math.max(z0, (int) Math.floor(Math.min(s.f0z, s.f1z) - pad));
        int bz1 = Math.min(z0 + 15, (int) Math.ceil(Math.max(s.f0z, s.f1z) + pad));
        int by0 = Math.max(1, (int) Math.floor(Math.min(s.f0y, s.f1y) - pad));
        int by1 = Math.min(477, (int) Math.ceil(Math.max(s.f0y, s.f1y) + pad));
        boolean any = false;
        for (int x = bx0; x <= bx1; x++) {
            for (int z = bz0; z <= bz1; z++) {
                for (int y = by0; y <= by1; y++) {
                    double px = x + 0.5 - s.f0x;
                    double py = y + 0.5 - s.f0y;
                    double pz = z + 0.5 - s.f0z;
                    double t = Math.max(0, Math.min(1, (px * ex + py * ey + pz * ez) / len2));
                    double qx = px - ex * t;
                    double qy = py - ey * t;
                    double qz = pz - ez * t;
                    double d = Math.sqrt(qx * qx + qy * qy + qz * qz);
                    double r = s.fr0 + (s.fr1 - s.fr0) * t;
                    if (d > r) {
                        continue;
                    }
                    pos.set(x, y, z);
                    if (!level.getBlockState(pos).canBeReplaced()) {
                        continue;
                    }
                    BlockState state = stone;
                    if (t < 0.08 && d < r * 0.35 && Hashing.unit(Hashing.hash(s.seed, 9, x, y, z)) < 0.5) {
                        state = ore;
                    }
                    level.setBlock(pos, state, Block.UPDATE_CLIENTS);
                    any = true;
                }
            }
        }
        return any;
    }
}
