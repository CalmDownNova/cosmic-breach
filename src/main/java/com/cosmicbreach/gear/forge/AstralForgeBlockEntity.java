package com.cosmicbreach.gear.forge;

import com.cosmicbreach.gear.GearRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Forge's block entity. It holds nothing saved (the tier is the block state); it exists so the rings can
 * be drawn, and remembers on the client when the last craft flashed and the last ring appeared.
 */
public class AstralForgeBlockEntity extends BlockEntity {
    private long craftedAt = Long.MIN_VALUE;
    private long tierUpAt = Long.MIN_VALUE;

    public AstralForgeBlockEntity(BlockPos pos, BlockState state) {
        super(GearRegistry.ASTRAL_FORGE_ENTITY.get(), pos, state);
    }

    public int tier() {
        return getBlockState().getValue(AstralForgeBlock.TIER);
    }

    /** Game time of the last craft here (client), or {@link Long#MIN_VALUE}. */
    public long craftedAt() {
        return craftedAt;
    }

    /** Game time the last ring appeared (client), or {@link Long#MIN_VALUE}. */
    public long tierUpAt() {
        return tierUpAt;
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (level == null) {
            return false;
        }
        switch (id) {
            case AstralForgeBlock.EVENT_CRAFTED -> craftedAt = level.getGameTime();
            case AstralForgeBlock.EVENT_TIER_UP -> tierUpAt = level.getGameTime();
            default -> {
                return super.triggerEvent(id, param);
            }
        }
        return true;
    }
}
