package com.cosmicbreach.client.guardian.unsung;

import com.cosmicbreach.client.guardian.GuardianBarHud;
import com.cosmicbreach.guardian.unsung.Unsung;
import com.cosmicbreach.guardian.unsung.UnsungMoves;
import com.cosmicbreach.guardian.unsung.UnsungRegistry;
import com.cosmicbreach.guardian.unsung.UnsungRegistry.Singing;
import com.cosmicbreach.guardian.unsung.UnsungSong;
import com.cosmicbreach.guardian.unsung.Voice;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SelectMusicEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The music is the boss (Unsung design v1): three choir lines, one per mask, synthesized in D minor at Vesper's
 * 100 BPM ({@code tools/sound/unsung.py}): the Alto's "ah", the Tenor's "oh", the Bass's "oo". Each line comes as
 * 8-beat blocks, one per turn of the song and chord of the progression (D minor, B flat, G minor, A), sung or hummed,
 * plus the rising hum of a Harmonize warning. On every line of the fight the client starts, all on the same tick, one
 * block per living mask: the singer's line sung and loud, the others humming under it (rising, through a warning).
 * The stems stay in step because every block starts on a line of the Vesper clock. A broken mask's line fades out and
 * never comes back, so the music thins as the fight is won; in a Break the song falters (the sung line drops back);
 * at the death everything stops, and the server's one soft chord sounds after the silence. Other music waits while
 * it plays. Client thread.
 */
public final class UnsungMusic {
    private static final int FADE = 10;
    /** How late a block may still start after its line (ticks). */
    private static final int LATE = 3;
    private static final float HUM = 0.85f;
    private static final float RISE = 0.95f;
    private static final float FALTER = 0.3f;

    private static final Map<Voice, Block> PLAYING = new EnumMap<>(Voice.class);
    private static boolean active;
    private static long lastLine = Long.MIN_VALUE;
    private static int blocks;
    private static @Nullable Voice lastSinger;

    private UnsungMusic() {
    }

    /** One 8-beat block of one voice, as music everywhere at once; it can fade out and falter. */
    private static final class Block extends AbstractTickableSoundInstance {
        final Voice voice;
        final Singing how;
        final float level;
        private int fading = -1;
        float falter = 1f;

        Block(SoundEvent sound, Voice voice, Singing how, float level) {
            super(sound, SoundSource.MUSIC, RandomSource.create());
            this.voice = voice;
            this.how = how;
            this.level = level;
            this.looping = false;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.volume = level;
        }

        void fadeOut() {
            if (fading < 0) {
                fading = FADE;
            }
        }

        @Override
        public void tick() {
            float fade = 1f;
            if (fading >= 0) {
                fading--;
                fade = Math.max(0f, fading / (float) FADE);
                if (fading <= 0) {
                    stop();
                }
            }
            // a mask's own line over the choir pulls it down (1.1)
            volume = level * fade * falter * com.cosmicbreach.client.voice.BossVoiceClient.duckFactor(getLocation());
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }

    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            stopAll(true);
            return;
        }
        Unsung u = GuardianBarHud.musicWanted() != null && UnsungRegistry.MUSIC_MARKER.equals(GuardianBarHud.musicWanted()) ? choir(mc) : null;
        long now = mc.level.getGameTime();
        boolean singing = u != null && (u.state() == Unsung.State.FIGHT || u.state() == Unsung.State.INTRO && now >= u.fightStart());
        if (!singing) {
            if (active) {
                stopAll(false);
            }
            return;
        }
        if (!active) {
            active = true;
            mc.getMusicManager().stopPlaying();
        }
        long t = mc.level.getGameTime();
        long fightStart = u.fightStart();
        Set<Voice> living = Voice.livingFromBroken(u.brokenBits());
        // a broken mask's line leaves the music for good
        for (Voice v : Voice.values()) {
            Block b = PLAYING.get(v);
            if (b != null && !living.contains(v)) {
                b.fadeOut();
                PLAYING.remove(v);
            }
        }
        boolean breaking = u.isBroken();
        for (Block b : PLAYING.values()) {
            float want = breaking && b.how == Singing.SUNG ? FALTER : 1f;
            b.falter += (want - b.falter) * 0.25f;
        }
        // the line this tick belongs to: its blocks start on it, or up to a few ticks late if the client's clock
        // skipped it (the time sync can jump a tick); later than that they wait for the next line
        long line = fightStart + Math.floorDiv(t - fightStart, UnsungMoves.TURN_TICKS) * UnsungMoves.TURN_TICKS;
        if (t < fightStart || line == lastLine || t - line > LATE) {
            return;
        }
        lastLine = line;
        t = line;
        long beat = UnsungSong.fightBeat(t, fightStart);
        int chord = UnsungSong.chord(beat);
        boolean warning = UnsungSong.warning(beat);
        Voice singer = u.nextSinger();
        lastSinger = singer;
        for (Voice v : Voice.values()) {
            if (!living.contains(v)) {
                continue;
            }
            Singing how = v == singer ? Singing.SUNG : warning ? Singing.RISE : Singing.HUM;
            float level = how == Singing.SUNG ? 1f : how == Singing.RISE ? RISE : HUM;
            Block b = new Block(UnsungRegistry.music(v, how, chord), v, how, level);
            if (breaking && how == Singing.SUNG) {
                b.falter = FALTER;
            }
            Block old = PLAYING.put(v, b);
            if (old != null && old.how != how) {
                old.fadeOut(); // the last block's tail rings on unless the way of singing changed
            }
            mc.getSoundManager().play(b);
            blocks++;
        }
    }

    /** The nearest awake choir this client knows, or null. */
    private static @Nullable Unsung choir(Minecraft mc) {
        Unsung best = null;
        double bestD = 72.0 * 72.0;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof Unsung u && u.state() != Unsung.State.DORMANT) {
                double d = e.distanceToSqr(mc.player);
                if (d < bestD) {
                    best = u;
                    bestD = d;
                }
            }
        }
        return best;
    }

    /** While the choir sings, no other music starts. */
    static void onSelectMusic(SelectMusicEvent event) {
        if (active) {
            event.overrideMusic(null);
        }
    }

    private static void stopAll(boolean hard) {
        for (Block b : PLAYING.values()) {
            if (hard) {
                Minecraft.getInstance().getSoundManager().stop(b);
            } else {
                b.fadeOut();
            }
        }
        PLAYING.clear();
        active = false;
        lastLine = Long.MIN_VALUE;
    }

    /** True while the choir's music plays (for checks). */
    public static boolean active() {
        return active;
    }

    /** Blocks started so far (for checks). */
    public static int blocksStarted() {
        return blocks;
    }

    /** The voices whose lines are in the music now (for checks). */
    public static Set<Voice> voices() {
        return java.util.Collections.unmodifiableSet(PLAYING.keySet());
    }

    /** Who sang the last block (for checks). */
    public static @Nullable Voice lastSinger() {
        return lastSinger;
    }
}
