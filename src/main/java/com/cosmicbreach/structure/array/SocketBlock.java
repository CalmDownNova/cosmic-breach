package com.cosmicbreach.structure.array;

import net.minecraft.world.level.block.Block;

/** A bare socket on the wall round the grid: light that reaches it just ends there. */
public class SocketBlock extends PuzzleBlock {
    public SocketBlock(Properties properties) {
        super(properties, Block.box(3.0, 0.0, 3.0, 13.0, 12.0, 13.0));
    }
}
