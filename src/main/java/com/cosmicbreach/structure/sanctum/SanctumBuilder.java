package com.cosmicbreach.structure.sanctum;

import com.cosmicbreach.structure.array.LensRoom;
import com.cosmicbreach.structure.choir.ChoirDifficulty;
import com.cosmicbreach.structure.choir.ChoirRoom;
import com.cosmicbreach.structure.lens.LensDifficulty;
import com.cosmicbreach.structure.sanctum.SanctumLayout.Kind;
import com.cosmicbreach.structure.sanctum.SanctumLayout.Wing;
import com.cosmicbreach.structure.vault.VaultBlock;
import com.cosmicbreach.structure.vault.VaultBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Builds a {@link SanctumLayout} into the world, all of it or the part inside a box (a chunk, during world generation):
 * the stone, then the two puzzles through their rooms' own APIs ({@link LensRoom}, {@link ChoirRoom}, both at their
 * hardest: {@link LensDifficulty#HARD_7} and {@link ChoirDifficulty#SANCTUM}, layer tier 3) and the wings' vaults with
 * their own loot. Flags 2: no neighbour updates, clients told.
 */
public final class SanctumBuilder {
    private SanctumBuilder() {
    }

    public static void build(WorldGenLevel level, SanctumLayout layout, BoundingBox clip) {
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
                if (!layout.touches(x, z)) {
                    continue;
                }
                for (int y = y0; y <= y1; y++) {
                    Kind kind = layout.kind(x, y, z);
                    if (kind == Kind.KEEP) {
                        continue;
                    }
                    pos.set(x, y, z);
                    if (kind == Kind.AIR && level.getBlockState(pos).isAir()) {
                        continue;
                    }
                    level.setBlock(pos, s.of(kind), Block.UPDATE_CLIENTS);
                }
            }
        }
        BlockPos westVault = at(layout.vault(Wing.WEST));
        BlockPos eastVault = at(layout.vault(Wing.EAST));
        LensRoom.place(level, clip, at(layout.lensCore()), LensDifficulty.HARD_7, layout.lensSeed(), 3, SanctumLayout.LENS_CEILING, westVault);
        ChoirRoom.place(level, clip, at(layout.conductor()), ChoirDifficulty.SANCTUM, layout.choirSeed(), 3, eastVault);
        if (clip.isInside(westVault) && level.getBlockEntity(westVault) instanceof VaultBlockEntity v) {
            v.configure(SanctumRegistry.LENS_VAULT_LOOT, 3);
        }
        if (clip.isInside(eastVault) && level.getBlockEntity(eastVault) instanceof VaultBlockEntity v) {
            v.configure(SanctumRegistry.CHOIR_VAULT_LOOT, 3);
        }
    }

    public static BlockPos at(int[] p) {
        return new BlockPos(p[0], p[1], p[2]);
    }

    /** The direction from the arena toward the Gate. */
    public static Direction toGate(SanctumLayout layout) {
        return layout.side() < 0 ? Direction.NORTH : Direction.SOUTH;
    }

    /** Block states for each kind. */
    private static final class States {
        final BlockState ivory = SanctumRegistry.SANCTUM_IVORY.get().defaultBlockState();
        final BlockState bricks = SanctumRegistry.SANCTUM_IVORY_BRICKS.get().defaultBlockState();
        final BlockState gilt = SanctumRegistry.SANCTUM_GILT.get().defaultBlockState();
        final BlockState ember = SanctumRegistry.SANCTUM_EMBER.get().defaultBlockState();
        final BlockState lamp = SanctumRegistry.SANCTUM_RIFT_LAMP.get().defaultBlockState();
        final BlockState umbral = SanctumRegistry.SANCTUM_UMBRAL.get().defaultBlockState();
        final BlockState pillar = SanctumRegistry.CHOIR_PILLAR.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        final BlockState stair;
        final BlockState slab = SanctumRegistry.SANCTUM_IVORY_SLAB.get().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        final BlockState throne;
        final BlockState gate = SanctumRegistry.SANCTUM_GATE.get().defaultBlockState();
        final BlockState seal = SanctumRegistry.THRONE_SEAL.get().defaultBlockState();
        final BlockState lock;
        final BlockState vaultWest = SanctumRegistry.SANCTUM_VAULT.get().defaultBlockState().setValue(VaultBlock.FACING, Direction.EAST);
        final BlockState vaultEast = SanctumRegistry.SANCTUM_VAULT.get().defaultBlockState().setValue(VaultBlock.FACING, Direction.WEST);

        States(SanctumLayout layout) {
            Direction up = toGate(layout);
            this.stair = SanctumRegistry.SANCTUM_IVORY_STAIRS.get().defaultBlockState().setValue(StairBlock.FACING, up)
                    .setValue(StairBlock.HALF, Half.BOTTOM);
            this.throne = SanctumRegistry.SANCTUM_THRONE.get().defaultBlockState().setValue(SanctumThroneBlock.FACING, up);
            this.lock = SanctumRegistry.ECLIPSE_LOCK.get().defaultBlockState().setValue(EclipseLockBlock.FACING, up);
        }

        BlockState of(Kind kind) {
            return switch (kind) {
                case KEEP, AIR -> Blocks.AIR.defaultBlockState();
                case IVORY -> ivory;
                case BRICKS -> bricks;
                case GILT -> gilt;
                case EMBER -> ember;
                case LAMP -> lamp;
                case UMBRAL -> umbral;
                case PILLAR -> pillar;
                case STAIR -> stair;
                case SLAB -> slab;
                case THRONE -> throne;
                case GATE -> gate;
                case SEAL -> seal;
                case LOCK_WEST, LOCK_EAST -> lock;
                case VAULT_WEST -> vaultWest;
                case VAULT_EAST -> vaultEast;
            };
        }
    }
}
