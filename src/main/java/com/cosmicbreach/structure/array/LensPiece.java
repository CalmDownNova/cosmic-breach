package com.cosmicbreach.structure.array;

import com.cosmicbreach.structure.StructureRegistry;
import com.cosmicbreach.structure.lens.Lens;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * A loose Lens Array piece in someone's hands (the {@code cosmicbreach:lens_piece} component): the array it
 * belongs to (its core's position and dimension), the piece as the puzzle core encodes it ({@link Lens}, turn
 * included, so a mirror is set down as it was lifted), and the token the array handed out with it. The array
 * keeps a list of the tokens it has out; a piece whose token it no longer knows (it was returned while
 * carried off) crumbles.
 */
public record LensPiece(BlockPos core, ResourceKey<Level> dimension, int piece, long token) {
    public static final Codec<LensPiece> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("core").forGetter(LensPiece::core),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(LensPiece::dimension),
            Codec.INT.fieldOf("piece").forGetter(LensPiece::piece),
            Codec.LONG.fieldOf("token").forGetter(LensPiece::token)).apply(i, LensPiece::new));
    public static final StreamCodec<ByteBuf, LensPiece> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, LensPiece::core,
            ResourceKey.streamCodec(Registries.DIMENSION), LensPiece::dimension,
            ByteBufCodecs.VAR_INT, LensPiece::piece,
            ByteBufCodecs.VAR_LONG, LensPiece::token,
            LensPiece::new);

    /** What kind of loose piece an item is. */
    public enum Kind {
        MIRROR,
        GOLD_FILTER,
        TEAL_FILTER,
        MAGENTA_FILTER;

        /** The kind of a loose cell encoding, or null for anything that isn't loose. */
        public static Kind of(int cell) {
            if (!Lens.loose(cell)) {
                return null;
            }
            if (Lens.kind(cell) == Lens.MIRROR) {
                return MIRROR;
            }
            return switch (Lens.color(cell)) {
                case Lens.GOLD -> GOLD_FILTER;
                case Lens.TEAL -> TEAL_FILTER;
                case Lens.MAGENTA -> MAGENTA_FILTER;
                default -> null;
            };
        }

        public Item item() {
            return switch (this) {
                case MIRROR -> StructureRegistry.LOOSE_MIRROR.get();
                case GOLD_FILTER -> StructureRegistry.LOOSE_GOLD_FILTER.get();
                case TEAL_FILTER -> StructureRegistry.LOOSE_TEAL_FILTER.get();
                case MAGENTA_FILTER -> StructureRegistry.LOOSE_MAGENTA_FILTER.get();
            };
        }
    }
}
