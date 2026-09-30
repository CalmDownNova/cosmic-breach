package com.cosmicbreach.structure.array;

import net.minecraft.world.level.block.Block;

/** An empty pedestal of the grid: where a loose mirror or filter can be set down. */
public class PedestalBlock extends PuzzleBlock {
    public PedestalBlock(Properties properties) {
        super(properties, Block.box(2.0, 0.0, 2.0, 14.0, 8.0, 14.0));
    }
}
