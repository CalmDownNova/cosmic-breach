package com.cosmicbreach.structure.trap;

import com.cosmicbreach.structure.Guards;
import com.cosmicbreach.structure.StructureRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A guard post: {@link #SHARDLINGS} (a pack of {@link #count}) or {@link #GYRE_KNIGHT} (the Observatory's elite,
 * GDD 7.1, spawned only once {@code cosmicbreach:gyre_knight} is registered; until then the post waits). Checks
 * for a player within {@link #RADIUS} blocks once a second.
 */
public class GuardMarkerBlockEntity extends BlockEntity {
    public static final int SHARDLINGS = 0;
    public static final int GYRE_KNIGHT = 1;
    public static final double RADIUS = 18.0;

    private int kind = SHARDLINGS;
    private int count = 4;

    public GuardMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(StructureRegistry.GUARD_MARKER_ENTITY.get(), pos, state);
    }

    public void configure(int kind, int count) {
        this.kind = kind;
        this.count = count;
        setChanged();
    }

    public int kind() {
        return kind;
    }

    static void serverTick(Level level, BlockPos pos, BlockState state, GuardMarkerBlockEntity marker) {
        if (!(level instanceof ServerLevel server) || (level.getGameTime() + pos.asLong()) % 20 != 0) {
            return;
        }
        if (level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, RADIUS, false) == null) {
            return;
        }
        Vec3 at = Vec3.atBottomCenterOf(pos);
        if (marker.kind == GYRE_KNIGHT) {
            if (!Guards.knightRegistered() || Guards.gyreKnight(server, at).isEmpty()) {
                return;
            }
        } else {
            Guards.shardlings(server, at, at.add(1, 0, 0), marker.count);
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("kind", kind);
        tag.putInt("count", count);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        kind = tag.getInt("kind");
        count = tag.contains("count") ? tag.getInt("count") : 4;
    }
}
