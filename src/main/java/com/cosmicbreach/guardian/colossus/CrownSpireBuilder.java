package com.cosmicbreach.guardian.colossus;

import com.cosmicbreach.guardian.GuardianAltarBlockEntity;
import com.cosmicbreach.guardian.GuardianRegistry;
import com.cosmicbreach.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Builds a {@link CrownSpireLayout} into the world, all of it or the part inside a box (a chunk, during world
 * generation), and sets up the lair's block entities: the altar (which registers the lair and raises the Colossus
 * statue) and each crown crystal's index and resting target.
 */
public final class CrownSpireBuilder {
    private CrownSpireBuilder() {
    }

    /** Builds the part of {@code layout} inside {@code clip}. Flags 2: no neighbour updates, clients told. */
    public static void build(WorldGenLevel level, CrownSpireLayout layout, BoundingBox clip) {
        int[] b = layout.bounds();
        int x0 = Math.max(b[0], clip.minX());
        int x1 = Math.min(b[3], clip.maxX());
        int z0 = Math.max(b[2], clip.minZ());
        int z1 = Math.min(b[5], clip.maxZ());
        int y0 = Math.max(Math.max(b[1], clip.minY()), level.getMinBuildHeight());
        int y1 = Math.min(Math.min(b[4], clip.maxY()), level.getMaxBuildHeight() - 1);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        States s = new States(layout);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int y = y0; y <= y1; y++) {
                    CrownSpireLayout.Kind kind = layout.kind(x, y, z);
                    if (kind == CrownSpireLayout.Kind.KEEP) {
                        continue;
                    }
                    pos.set(x, y, z);
                    BlockState state = s.of(kind, layout, x, y, z);
                    if (kind == CrownSpireLayout.Kind.AIR && level.getBlockState(pos).isAir()) {
                        continue;
                    }
                    level.setBlock(pos, state, Block.UPDATE_CLIENTS);
                    if (kind == CrownSpireLayout.Kind.ALTAR && level.getBlockEntity(pos) instanceof GuardianAltarBlockEntity altar) {
                        altar.setup(layout.arena().centreBlock());
                    }
                }
            }
        }
        setUpCrystals(level, layout, clip);
    }

    private static void setUpCrystals(WorldGenLevel level, CrownSpireLayout layout, BoundingBox clip) {
        CrownArena arena = layout.arena();
        for (int k = 0; k < Refraction.CRYSTALS; k++) {
            BlockPos base = arena.crystalBase(k);
            if (clip.isInside(base) && level.getBlockEntity(base) instanceof CrownCrystalBlockEntity crystal) {
                crystal.setup(k, Refraction.resting(k), arena.centreBlock());
            }
        }
    }

    /** Block states for each kind. */
    private static final class States {
        final BlockState stone = ModBlocks.STARFALL_STONE.get().defaultBlockState();
        final BlockState bricks = ModBlocks.STARFALL_STONE_BRICKS.get().defaultBlockState();
        final BlockState polished = ModBlocks.POLISHED_STARFALL_STONE.get().defaultBlockState();
        final BlockState quartz = ModBlocks.SPIRE_QUARTZ.get().defaultBlockState();
        final BlockState gold = GuardianRegistry.GILDED_STARFALL_BRICKS.get().defaultBlockState();
        final BlockState glass = GuardianRegistry.PRISM_GLASS.get().defaultBlockState();
        final BlockState crystal = GuardianRegistry.CROWN_CRYSTAL.get().defaultBlockState();
        final BlockState pillar = GuardianRegistry.CROWN_PILLAR.get().defaultBlockState().setValue(CrownPillarBlock.LIT, true);
        final BlockState rising = GuardianRegistry.RISING_LIGHT.get().defaultBlockState();
        final BlockState falling = GuardianRegistry.FALLING_LIGHT.get().defaultBlockState();
        final BlockState altar = GuardianRegistry.PRISM_ALTAR.get().defaultBlockState();
        final BlockState crownQuartz = GuardianRegistry.CROWN_QUARTZ.get().defaultBlockState();
        final BlockState lamp = GuardianRegistry.CROWN_PILLAR.get().defaultBlockState().setValue(CrownPillarBlock.LIT, true);

        final Direction exit;

        States(CrownSpireLayout layout) {
            int[] step = layout.exitStep();
            this.exit = Direction.fromDelta(step[0], 0, step[1]);
        }

        BlockState of(CrownSpireLayout.Kind kind, CrownSpireLayout layout, int x, int y, int z) {
            return switch (kind) {
                case KEEP, AIR -> Blocks.AIR.defaultBlockState();
                case STONE -> stone;
                case BRICKS -> bricks;
                case POLISHED -> polished;
                case QUARTZ -> quartz;
                case GOLD -> gold;
                case GLASS -> glass;
                case PILLAR -> pillar;
                case RISING -> rising.setValue(LiftLightBlock.FACING, exit);
                case RISING_TOP -> rising.setValue(LiftLightBlock.TOP, true).setValue(LiftLightBlock.FACING, exit);
                case FALLING -> falling;
                case ALTAR -> altar;
                case CROWN_QUARTZ -> crownQuartz;
                case LAMP -> lamp;
                case CRYSTAL -> {
                    CrownArena arena = layout.arena();
                    int k = arena.crystalAt(new BlockPos(x, y, z));
                    BlockPos base = arena.crystalBase(Math.max(0, k));
                    yield CrownCrystalBlock.stateFor(crystal, x - base.getX(), y - base.getY(), z - base.getZ(), false);
                }
            };
        }
    }
}
