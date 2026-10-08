package com.cosmicbreach.shrine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

/**
 * A player's shrine save (1.1 design section 9): a right click on a shrine makes it the player's respawn (a normal spawn
 * point, so a bed or another shrine replaces it) and remembers which shrine, kept through deaths. Each player saves on
 * their own.
 */
public final class ShrineSave {
    /** The shrine a player last saved at; {@link #NONE} if never. */
    public record Saved(ResourceKey<Level> dimension, BlockPos pos, String kind) {
        public static final Saved NONE = new Saved(Level.OVERWORLD, BlockPos.ZERO, "");
        public static final Codec<Saved> CODEC = RecordCodecBuilder.create(i -> i.group(
                Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Saved::dimension),
                BlockPos.CODEC.fieldOf("pos").forGetter(Saved::pos),
                Codec.STRING.fieldOf("kind").forGetter(Saved::kind)).apply(i, Saved::new));

        public boolean none() {
            return kind.isEmpty();
        }
    }

    private ShrineSave() {
    }

    /** True while {@code player}'s respawn is still the shrine they last saved at. */
    public static boolean active(ServerPlayer player) {
        Saved s = player.getData(ShrineRegistry.SAVED);
        return ShrineRules.saveActive(s.none() ? null : s.pos(), s.dimension().location().toString(),
                player.getRespawnPosition(), player.getRespawnDimension().location().toString());
    }

    /** A right click on the shrine at {@code pos}: it keeps this player's place, with a chime, a burst of light and a caption (only the caption again if it already does). */
    public static void save(ServerPlayer player, BlockPos pos, ShrineKind kind) {
        ServerLevel level = player.serverLevel();
        boolean again = active(player) && pos.equals(player.getData(ShrineRegistry.SAVED).pos());
        player.setRespawnPosition(level.dimension(), pos, player.getYRot(), false, false);
        if (!pos.equals(player.getRespawnPosition()) || !level.dimension().equals(player.getRespawnDimension())) {
            return; // something refused the spawn point
        }
        player.setData(ShrineRegistry.SAVED, new Saved(level.dimension(), pos.immutable(), kind.id()));
        if (again) {
            player.displayClientMessage(Component.translatable("cosmicbreach.shrine.saved"), true); // already kept here: the caption only
            return;
        }
        level.playSound(null, pos, ShrineRegistry.SAVE_SOUND.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.6, pos.getZ() + 0.5, 30, 0.5, 0.8, 0.5, 0.03);
        player.displayClientMessage(Component.translatable("cosmicbreach.shrine.saved"), true);
    }
}
