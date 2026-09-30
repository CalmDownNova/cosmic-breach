package com.cosmicbreach.client.fx;

import com.cosmicbreach.combat.MoveSound;
import com.cosmicbreach.combat.core.MoveInstance;
import com.cosmicbreach.combat.data.MoveDef;
import com.cosmicbreach.combat.data.MoveKind;
import com.cosmicbreach.combat.data.MoveTraits;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * The local player's own combat sounds, played the moment its machine predicts them (swings, the
 * whistle of a plunge, dashes, the charge's ready ting, a parry whiff, Resonance filling up). They
 * follow the player. The server plays the same ones for everyone else; hits, parries, perfect dodges
 * and landings come from the server for everyone, this player included.
 */
public final class CombatAudio {
    private CombatAudio() {
    }

    public static void playOwn(Player player, Holder<SoundEvent> sound, float volume, float pitch) {
        player.level().playLocalSound(player, sound.value(), SoundSource.PLAYERS, volume, pitch);
    }

    /** A move's first active tick: its swing (the data's own or the engine's pick), and its slam if it names one. */
    public static void swing(Player player, MoveInstance move) {
        MoveDef def = move.def();
        Holder<SoundEvent> override = MoveSound.swingOverride(def);
        if (override != null) {
            playOwn(player, override, 1.0f, MoveSound.swingPitch(def, move.mv())
                    + (player.getRandom().nextFloat() - 0.5f) * 2f * MoveSound.PITCH_SPREAD);
        } else {
            MoveSound sound = MoveSound.forMove(def);
            Holder<SoundEvent> event = sound.sound();
            if (event != null) {
                playOwn(player, event, sound.volume(), sound.pitch(player.getRandom().nextFloat()));
            }
        }
        if (def.kind() != MoveKind.PLUNGE) {
            def.traits().sound().flatMap(MoveTraits.Sounds::slam).ifPresent(slam -> playOwn(player, MoveSound.resolve(slam),
                    1.0f, 0.97f + player.getRandom().nextFloat() * 0.06f));
        }
    }
}
