package com.cosmicbreach.relic.crown;

import com.cosmicbreach.relic.Relics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The Heliarch's Crown's block entity: it holds nothing, it is there so the client can draw the orbiting sun. */
public class CrownBlockEntity extends BlockEntity {
    public CrownBlockEntity(BlockPos pos, BlockState state) {
        super(Relics.CROWN.get(), pos, state);
    }
}
