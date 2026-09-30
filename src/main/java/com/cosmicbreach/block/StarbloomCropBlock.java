package com.cosmicbreach.block;

import com.cosmicbreach.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Starbloom crop: four stages (age 0 to 3) on farmland, grown from Starbloom Seeds, harvested for a
 * Starbloom and more seeds. Paced like beetroots (a third of the random ticks, a third of bone meal).
 *
 * <p>Aetheria has no dirt, so the crop also takes root in Glimmer Grass, where it grows as fast as on
 * moist farmland (vanilla's growth speed only counts farmland, so the Glimmer Grass case is handled here).
 */
public class StarbloomCropBlock extends CropBlock {
    public static final MapCodec<StarbloomCropBlock> CODEC = simpleCodec(StarbloomCropBlock::new);
    public static final int MAX_AGE = 3;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
    private static final VoxelShape[] SHAPE_BY_AGE = {
            Block.box(0.0, 0.0, 0.0, 16.0, 4.0, 16.0),
            Block.box(0.0, 0.0, 0.0, 16.0, 7.0, 16.0),
            Block.box(0.0, 0.0, 0.0, 16.0, 11.0, 16.0),
            Block.box(0.0, 0.0, 0.0, 16.0, 15.0, 16.0)
    };

    public StarbloomCropBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<StarbloomCropBlock> codec() {
        return CODEC;
    }

    @Override
    protected IntegerProperty getAgeProperty() {
        return AGE;
    }

    @Override
    public int getMaxAge() {
        return MAX_AGE;
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return ModBlocks.STARBLOOM_SEEDS.get();
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(ModBlocks.GLIMMER_GRASS.get()) || super.mayPlaceOn(state, level, pos);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) {
            return;
        }
        if (!level.getBlockState(pos.below()).is(ModBlocks.GLIMMER_GRASS.get())) {
            super.randomTick(state, level, pos, random);
            return;
        }
        // on Glimmer Grass: vanilla's crop step with the growth speed of a crop on moist farmland
        if (!level.isAreaLoaded(pos, 1) || level.getRawBrightness(pos, 0) < 9) {
            return;
        }
        int age = getAge(state);
        if (age < getMaxAge()) {
            float speed = GLIMMER_GRASS_GROWTH_SPEED;
            if (net.neoforged.neoforge.common.CommonHooks.canCropGrow(level, pos, state, random.nextInt((int) (25.0F / speed) + 1) == 0)) {
                level.setBlock(pos, getStateForAge(age + 1), 2);
                net.neoforged.neoforge.common.CommonHooks.fireCropGrowPost(level, pos, state);
            }
        }
    }

    /** What a crop in the middle of moist farmland gets from vanilla's growth speed (1 + 3 + 8 x 3/4). */
    static final float GLIMMER_GRASS_GROWTH_SPEED = 10.0F;

    @Override
    protected int getBonemealAgeIncrease(Level level) {
        return super.getBonemealAgeIncrease(level) / 3;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE_BY_AGE[getAge(state)];
    }
}
