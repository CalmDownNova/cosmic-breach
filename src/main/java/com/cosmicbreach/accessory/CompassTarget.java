package com.cosmicbreach.accessory;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** The vault a Sunshard Compass points to ({@code found} false: none it knows of, and the needle wanders). */
public record CompassTarget(boolean found, BlockPos pos) {
    public static final CompassTarget NONE = new CompassTarget(false, BlockPos.ZERO);

    public static final StreamCodec<ByteBuf, CompassTarget> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, CompassTarget::found,
            BlockPos.STREAM_CODEC, CompassTarget::pos,
            CompassTarget::new);

    public static CompassTarget at(BlockPos pos) {
        return new CompassTarget(true, pos.immutable());
    }
}
