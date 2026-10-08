package com.cosmicbreach.provision;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.world.light.DeepLight;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Umbral Cap (1.1): a pale violet mushroom on the Deep's basalt, the Deep's food, eaten raw. It grows on the
 * {@link #SOIL} basalts and on {@code #minecraft:mushroom_grow_block} (mycelium, podzol, the nylium); unlike vanilla's
 * small mushrooms it does not grow on any solid block in the dark. It spreads like a vanilla mushroom, one try in 25 random
 * ticks and at most 5 in a 9 by 3 by 9 box, but only from a cap that stands in the dark by the Deep's own rule
 * ({@link DeepLight}: below Shear band B the sky gives 40% of its light). So it spreads through the Deep's shade, never
 * under the open sky of the layers above, and a cap in torchlight (above {@link #MAX_SPREAD_LIGHT}) stays put. Neon Lichen
 * (light 7) does not stop it.
 *
 * <p>The light is judged where the spreading cap stands, not where the new cap lands (vanilla's mushrooms judge the landing
 * spot). That is on purpose: a cap in the dark may plant one a few blocks off (up to five), even into lamplight, and the new
 * cap then stays put because it stands in the light itself, so a lit room is never overrun, and a cap that lands there is
 * still food. The patch is bounded by the 5 in the box and by the dark.
 */
public class UmbralCapBlock extends BushBlock {
    public static final MapCodec<UmbralCapBlock> CODEC = simpleCodec(UmbralCapBlock::new);
    /** The basalts it grows on. */
    public static final TagKey<Block> SOIL = TagKey.create(Registries.BLOCK, CosmicBreach.id("umbral_cap_soil"));
    /** It spreads only where the light is at most this. */
    public static final int MAX_SPREAD_LIGHT = 9;
    private static final VoxelShape SHAPE = Block.box(4.0, 0.0, 4.0, 12.0, 7.0, 12.0);

    public UmbralCapBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<UmbralCapBlock> codec() {
        return CODEC;
    }

    /** The block has a random offset, so its outline and click box move with the drawing (as vanilla's small plants do). */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Vec3 offset = state.getOffset(level, pos);
        return SHAPE.move(offset.x, offset.y, offset.z);
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(SOIL) || state.is(BlockTags.MUSHROOM_GROW_BLOCK);
    }

    /** The light it judges by: block light, or the sky's under the Deep's share, whichever is more. */
    public static int effectiveLight(int sky, int block, double skyScale) {
        return Math.max(block, DeepLight.shade(sky, skyScale));
    }

    public static boolean spreadsAt(int light) {
        return light <= MAX_SPREAD_LIGHT;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(25) != 0) {
            return;
        }
        int light = effectiveLight(level.getBrightness(LightLayer.SKY, pos), level.getBrightness(LightLayer.BLOCK, pos),
                DeepLight.skyScale(level, pos.getY()));
        if (!spreadsAt(light)) {
            return;
        }
        int room = 5;
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-4, -1, -4), pos.offset(4, 1, 4))) {
            if (level.getBlockState(p).is(this) && --room <= 0) {
                return;
            }
        }
        BlockPos from = pos;
        BlockPos to = from.offset(random.nextInt(3) - 1, random.nextInt(2) - random.nextInt(2), random.nextInt(3) - 1);
        for (int k = 0; k < 4; k++) {
            if (level.isEmptyBlock(to) && state.canSurvive(level, to)) {
                from = to;
            }
            to = from.offset(random.nextInt(3) - 1, random.nextInt(2) - random.nextInt(2), random.nextInt(3) - 1);
        }
        if (level.isEmptyBlock(to) && state.canSurvive(level, to)) {
            level.setBlock(to, state, Block.UPDATE_CLIENTS);
        }
    }
}
