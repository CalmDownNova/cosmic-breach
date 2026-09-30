package com.cosmicbreach.block;

import com.cosmicbreach.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Starbloom, the Reach's flower (GDD 2.2). Grows where vanilla flowers do, and on Glimmer Grass. */
public class StarbloomBlock extends FlowerBlock {
    public static final MapCodec<StarbloomBlock> CODEC = RecordCodecBuilder.mapCodec(
            i -> i.group(EFFECTS_FIELD.forGetter(FlowerBlock::getSuspiciousEffects), propertiesCodec()).apply(i, StarbloomBlock::new));

    public StarbloomBlock(Holder<MobEffect> effect, float seconds, Properties properties) {
        super(effect, seconds, properties);
    }

    public StarbloomBlock(SuspiciousStewEffects effects, Properties properties) {
        super(effects, properties);
    }

    @Override
    public MapCodec<StarbloomBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(ModBlocks.GLIMMER_GRASS.get()) || super.mayPlaceOn(state, level, pos);
    }
}
