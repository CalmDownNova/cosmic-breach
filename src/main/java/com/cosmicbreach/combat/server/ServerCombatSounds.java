package com.cosmicbreach.combat.server;

import com.cosmicbreach.combat.MoveSound;
import com.cosmicbreach.combat.core.MoveInstance;
import com.cosmicbreach.combat.data.MoveDef;
import com.cosmicbreach.combat.data.MoveKind;
import com.cosmicbreach.combat.data.MoveTraits;
import com.cosmicbreach.registry.ModSounds;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Combat sounds the server plays. Two kinds:
 * <ul>
 *   <li>A player's own actions (swings, dashes, charges, the ability): the player's client already
 *       played them from prediction, so the server plays them at the player for everyone else
 *       ({@code Level.playSound} with the player as the one left out).</li>
 *   <li>Outcomes only the server knows (hits, crits, parries, perfect dodges, plunge impacts): at the
 *       place they happened, for everyone including the player.</li>
 * </ul>
 */
public final class ServerCombatSounds {
    private ServerCombatSounds() {
    }

    /** At the player, for everyone near it but the player. */
    public static void forOthers(ServerPlayer player, Holder<SoundEvent> sound, float volume, float pitch) {
        player.level().playSound(player, player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(),
                sound, SoundSource.PLAYERS, volume, pitch);
    }

    /** At {@code at}, for everyone near it. */
    public static void forEveryone(Level level, Vec3 at, Holder<SoundEvent> sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    /**
     * A move's first active tick, for everyone but the player: its swing (the data's own, or the engine's
     * pick) and, if its data names one, the slam where it strikes the ground (a plunge slams on landing).
     */
    public static void swing(ServerPlayer player, MoveInstance move) {
        MoveDef def = move.def();
        Holder<SoundEvent> override = MoveSound.swingOverride(def);
        if (override != null) {
            forOthers(player, override, 1.0f, MoveSound.swingPitch(def, move.mv())
                    + (player.getRandom().nextFloat() - 0.5f) * 2f * MoveSound.PITCH_SPREAD);
        } else {
            MoveSound sound = MoveSound.forMove(def);
            Holder<SoundEvent> event = sound.sound();
            if (event != null) {
                forOthers(player, event, sound.volume(), sound.pitch(player.getRandom().nextFloat()));
            }
        }
        if (def.kind() != MoveKind.PLUNGE) {
            slam(player, def, 1.0f, false);
        }
    }

    /**
     * The move's slam ({@code sound.slam}) at the player's feet: for everyone but the player on a swing
     * (its client played it), for everyone on a plunge landing (only the server knows the fall).
     */
    public static void slam(ServerPlayer player, MoveDef def, float volume, boolean everyone) {
        def.traits().sound().flatMap(MoveTraits.Sounds::slam).ifPresent(id -> {
            Holder<SoundEvent> event = MoveSound.resolve(id);
            float pitch = 0.97f + player.getRandom().nextFloat() * 0.06f;
            if (everyone) {
                forEveryone(player.level(), player.position(), event, volume, pitch);
            } else {
                player.level().playSound(player, player.getX(), player.getY(), player.getZ(), event, SoundSource.PLAYERS, volume, pitch);
            }
        });
    }

    /** A landed hit, at the point struck: louder and a little lower with Impact; a crit has its own sound. */
    public static void hit(Level level, Vec3 at, double impact, boolean crit, RandomSource random) {
        hit(level, at, impact, crit, random, null);
    }

    /** {@link #hit}, with the move's own hit sound in place of the engine's (a crit keeps the crit sound). */
    public static void hit(Level level, Vec3 at, double impact, boolean crit, RandomSource random,
                           @Nullable Holder<SoundEvent> own) {
        float volume = 0.85f + (float) Math.min(0.35, impact / 70.0);
        float pitch = crit ? 1.0f : (float) (1.06 - Math.min(0.14, impact * 0.005)) + (random.nextFloat() - 0.5f) * 0.08f;
        if (!crit && own != null) {
            forEveryone(level, at, own, volume, 0.94f + random.nextFloat() * 0.12f);
            return;
        }
        forEveryone(level, at, crit ? ModSounds.HIT_CRIT : ModSounds.HIT, volume, pitch);
    }

    /** A plunge landing at the player's feet, for everyone: bigger and deeper with the fall. */
    public static void plungeImpact(ServerPlayer player, double fallBlocks) {
        float volume = 0.8f + (float) Math.min(0.5, fallBlocks * 0.06);
        float pitch = 1.05f - (float) Math.min(0.2, fallBlocks * 0.02);
        forEveryone(player.level(), player.position(), ModSounds.PLUNGE_IMPACT, volume, pitch);
    }
}
