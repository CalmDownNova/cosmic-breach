package com.cosmicbreach.relic.crown;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Heliarch's Crown (GDD 7.3, "Trophies"): a placeable block, the regent's crown set on a pedestal, with a small sun
 * orbiting it slowly (drawn by {@code client.relic.CrownRenderer} from its {@link CrownBlockEntity}). It gives light
 * {@value #LIGHT} and drops itself.
 */
public class HeliarchsCrownBlock extends Block implements EntityBlock {
    public static final MapCodec<HeliarchsCrownBlock> CODEC = simpleCodec(HeliarchsCrownBlock::new);
    /** Block light it gives. */
    public static final int LIGHT = 12;
    /** Where the crown sits on its pedestal, and the sun's orbit round it, in pixels. */
    public static final double CROWN_Y = 10.0;
    public static final double ORBIT_RADIUS = 7.0;
    public static final double ORBIT_Y = 13.5;
    /** One slow turn of the sun, in ticks. */
    public static final int ORBIT_TICKS = 160;

    private static final VoxelShape SHAPE = Shapes.or(
            box(2, 0, 2, 14, 2, 14),      // the pedestal's foot
            box(4, 2, 4, 12, 8, 12),      // its column
            box(3, 8, 3, 13, 10, 13),     // its top
            box(4.5, 10, 4.5, 11.5, 16, 11.5)); // the crown

    public HeliarchsCrownBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CrownBlockEntity(pos, state);
    }

    /** The sun's angle round the crown at {@code time} game ticks (plus partial), radians: one turn every {@link #ORBIT_TICKS}. */
    public static double orbitAngle(double time, BlockPos pos) {
        double phase = (pos.asLong() & 0xFF) / 256.0; // neighbouring crowns don't turn in step
        return 2.0 * Math.PI * (time / ORBIT_TICKS + phase);
    }
}
