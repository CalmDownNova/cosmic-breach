package com.cosmicbreach.structure.array;

import net.minecraft.world.level.block.Block;

/**
 * An Umbral block on a pedestal: it drinks the light. It can't be lifted, but any hit of 20 Impact or more (a
 * charged attack, the Comet Maul, a plunge) knocks it one tile along the grid, onto an empty pedestal.
 */
public class LensUmbralBlock extends PuzzleBlock {
    public LensUmbralBlock(Properties properties) {
        super(properties, Block.box(1.0, 0.0, 1.0, 15.0, 15.0, 15.0));
    }
}
