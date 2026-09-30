package com.cosmicbreach.client.sky;

import com.cosmicbreach.world.AetheriaAudio;
import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.Layer;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.Musics;
import net.neoforged.neoforge.client.event.SelectMusicEvent;

/**
 * Aetheria's music on the client. The biomes' music entries pick the layer's track for survival players
 * (vanilla); in creative, where vanilla would play its creative tracks everywhere but the Nether and the End,
 * Aetheria plays its own too. The Arrival cue comes from the server once per player
 * ({@link AetheriaAudio}) and starts at once through the music manager, so no other track plays over it.
 */
public final class AetheriaMusic {
    /** Delays between tracks, in ticks: two to five minutes (the biome entries use the same). */
    public static final int MIN_DELAY = 2400;
    public static final int MAX_DELAY = 6000;

    private static Music reach;
    private static Music drift;
    private static Music deep;
    private static Music arrival;
    private static int arrivalCues;

    private AetheriaMusic() {
    }

    static void onSelectMusic(SelectMusicEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !AetheriaWorld.is(mc.player.level())) {
            return;
        }
        Music music = event.getMusic();
        if (music == Musics.CREATIVE || music == Musics.GAME) {
            event.setMusic(forLayer(Layer.at(mc.player.getY())));
        }
    }

    /** The track for a layer. */
    public static Music forLayer(Layer layer) {
        if (reach == null) {
            reach = new Music(AetheriaAudio.MUSIC_REACH, MIN_DELAY, MAX_DELAY, false);
            drift = new Music(AetheriaAudio.MUSIC_DRIFT, MIN_DELAY, MAX_DELAY, false);
            deep = new Music(AetheriaAudio.MUSIC_DEEP, MIN_DELAY, MAX_DELAY, false);
        }
        return switch (layer) {
            case REACH -> reach;
            case DRIFT -> drift;
            case DEEP -> deep;
        };
    }

    /** From the server's Arrival payload (main thread). */
    public static void onArrivalCue() {
        Minecraft mc = Minecraft.getInstance();
        if (arrival == null) {
            arrival = new Music(AetheriaAudio.MUSIC_ARRIVAL, 0, 0, true);
        }
        arrivalCues++;
        mc.getMusicManager().stopPlaying();
        mc.getMusicManager().startPlaying(arrival);
    }

    /** How many Arrival cues this client has been sent (for checks). */
    public static int arrivalCues() {
        return arrivalCues;
    }
}
