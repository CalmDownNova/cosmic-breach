package com.cosmicbreach.voice.boss;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.guardian.heliarch.HeliarchLine;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * A sound event for every take in the four catalogs, straight from them, so a new line is data only: its catalog entry,
 * its take, its sounds.json entry and its caption. The Heliarch's six 1.0 lines are registered by its own register
 * ({@code HeliarchRegistry}) and skipped here.
 */
public final class BossVoiceRegistry {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, CosmicBreach.MOD_ID);

    static {
        Set<String> elsewhere = new HashSet<>();
        for (HeliarchLine l : HeliarchLine.values()) {
            elsewhere.add("heliarch/voice_" + l.id());
        }
        Set<String> done = new HashSet<>();
        for (String boss : BossCatalog.BOSSES) {
            for (VoiceLine line : BossCatalog.of(boss).lines()) {
                for (VoiceLine.Variant v : line.variants().values()) {
                    ResourceLocation sound = v.sound();
                    if (sound.getNamespace().equals(CosmicBreach.MOD_ID) && !elsewhere.contains(sound.getPath()) && done.add(sound.getPath())) {
                        SOUNDS.register(sound.getPath(), () -> SoundEvent.createVariableRangeEvent(sound));
                    }
                }
            }
        }
    }

    private BossVoiceRegistry() {
    }

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
