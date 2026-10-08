package com.cosmicbreach.client.voice;

import com.cosmicbreach.voice.boss.BossCatalog;
import com.cosmicbreach.voice.boss.BossVoiceDuck;
import com.cosmicbreach.voice.boss.BossVoiceNet;
import com.cosmicbreach.voice.boss.VoiceLine;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.jetbrains.annotations.Nullable;

/**
 * The bosses' voices on the client (1.1): a line the server says plays at once on the Voice channel, at the listener (no
 * panning, no fall-off: the whole fight hears it alike), with its caption on screen for its length and a moment more
 * ({@link BossCaptionLayer}; vanilla subtitles are off by default). One voice at a time with the Starfall's guide: a boss
 * line that arrives while the guide speaks waits for it (up to {@value #GUIDE_WAIT} ticks, then it is dropped), and the
 * guide waits while a boss line plays or waits ({@code EchoClient}). A line the server sends as a cut fades the playing
 * one out over {@value #CUT_FADE} ticks; any other early arrival stops the playing one and counts as an interruption,
 * which the checks expect never to see. With the game muted a line still holds the voice for its length.
 *
 * <p>Voice over music: while a line plays, its boss's music is pulled down ({@link BossVoiceDuck}): the music classes ask
 * {@link #duckFactor} for their loop's volume every tick. The boss's loud wake and phase sounds are not ducked (the engine
 * clamps their gain): the server holds a line's first word until they are over ({@code BossVoiceSounds}). The factor never
 * touches the voice. A take's own subtitle entry is hidden from vanilla's overlay (the caption layer shows it, with its mask
 * colours and no direction arrow toward the world's origin).
 */
public final class BossVoiceClient {
    /** A line heard: its take, when it started and ended (this class's ticks), its channel, cut by a later line or not. */
    public record Heard(String boss, String line, String variant, long start, long end, SoundSource source, boolean cut,
            boolean interrupted) {}

    /** Ticks the caption stays up after the line ends. */
    static final int CAPTION_HOLD = 20;
    static final int FADE_IN = 5;
    static final int FADE_OUT = 10;
    /** A cut line fades out over this many ticks (150 ms). */
    static final int CUT_FADE = 3;
    /** The longest a boss line waits for the guide to finish. */
    static final int GUIDE_WAIT = 40;

    private record Held(BossVoiceNet.Say say, long since) {}

    private static final List<Heard> HISTORY = new ArrayList<>();
    private static final List<String> STARTED = new ArrayList<>();
    private static long ticks;
    private static @Nullable Voice playing;
    private static long startedAt;
    private static @Nullable ClientLevel startedIn;
    private static @Nullable Held held;
    private static @Nullable VoiceLine shown;
    private static @Nullable VoiceLine.Variant shownTake;
    private static long shownFrom;
    private static long shownUntil;
    private static @Nullable String duckBoss;
    private static long duckStart;
    private static long duckEnd;

    private BossVoiceClient() {
    }

    public static void register(IEventBus modBus, IEventBus game) {
        game.addListener(ClientTickEvent.Post.class, event -> tick());
        game.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> reset());
        modBus.addListener(RegisterGuiLayersEvent.class, event -> event.registerAbove(VanillaGuiLayers.SUBTITLE_OVERLAY,
                BossCaptionLayer.ID, BossCaptionLayer::render));
    }

    /** The server says a line (client thread). */
    public static void onSay(BossVoiceNet.Say say) {
        if (EchoClient.playing() != null) {
            held = new Held(say, ticks);
            return;
        }
        start(say);
    }

    private static void start(BossVoiceNet.Say say) {
        if (!BossCatalog.BOSSES.contains(say.boss())) {
            return;
        }
        VoiceLine line = BossCatalog.of(say.boss()).line(say.line());
        VoiceLine.Variant take = line == null ? null : line.variant(say.variant());
        if (take == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (playing != null) {
            if (say.cut()) {
                playing.fadeOut();
                finish(true, false);
            } else {
                mc.getSoundManager().stop(playing);
                finish(false, true);
            }
        }
        playing = new Voice(line, say.variant(), take);
        startedAt = ticks;
        startedIn = mc.level;
        STARTED.add(say.boss() + "/" + say.line());
        mc.getSoundManager().play(playing);
        shown = line;
        shownTake = take;
        shownFrom = ticks;
        shownUntil = ticks + take.lengthTicks() + CAPTION_HOLD;
        duckBoss = say.boss();
        duckStart = ticks;
        duckEnd = ticks + take.lengthTicks();
    }

    private static void tick() {
        ticks++;
        if (held != null) {
            if (EchoClient.playing() == null) {
                BossVoiceNet.Say say = held.say();
                held = null;
                start(say);
            } else if (ticks - held.since() > GUIDE_WAIT) {
                com.cosmicbreach.CosmicBreach.LOGGER.debug("[cosmicbreach] boss line {}/{} dropped: the guide spoke for more than {} ticks",
                        held.say().boss(), held.say().line(), GUIDE_WAIT);
                held = null;
            }
        }
        if (playing != null) {
            Minecraft mc = Minecraft.getInstance();
            boolean sounding = mc.getSoundManager().isActive(playing);
            boolean cutOff = !sounding && mc.level != startedIn;
            if (cutOff || !sounding && ticks - startedAt >= playing.take.lengthTicks()) {
                finish(false, false);
            }
        }
        if (shown != null && ticks > shownUntil) {
            shown = null;
            shownTake = null;
        }
    }

    private static void finish(boolean cut, boolean interrupted) {
        if (playing != null) {
            HISTORY.add(new Heard(playing.line.boss(), playing.line.id(), playing.variant, startedAt, ticks, playing.getSource(), cut,
                    interrupted && ticks - startedAt < playing.take.lengthTicks()));
            playing = null;
        }
    }

    private static void reset() {
        if (playing != null) {
            Minecraft.getInstance().getSoundManager().stop(playing);
            playing = null;
        }
        held = null;
        shown = null;
        shownTake = null;
        startedIn = null;
        duckBoss = null;
    }

    /**
     * What to multiply the volume of {@code event} by now: 1 unless a boss line is playing (or has just ended) whose boss's
     * music it is ({@link BossVoiceDuck}). The boss music's loops read it every tick.
     */
    public static float duckFactor(ResourceLocation event) {
        String boss = duckBoss;
        return boss == null ? 1f : BossVoiceDuck.factor(boss, event, ticks, duckStart, duckEnd);
    }

    /** True while the sound engine is playing the take of a boss line (checks; false with the game muted or no audio device). */
    public static boolean sounding() {
        Voice v = playing;
        return v != null && Minecraft.getInstance().getSoundManager().isActive(v);
    }

    /** True while a boss line plays or waits for the guide (the guide then waits). */
    public static boolean speaking() {
        return playing != null || held != null;
    }

    /** The line whose caption is up, or null. */
    public static @Nullable VoiceLine caption() {
        return shown;
    }

    /** The take whose caption is up, or null. */
    public static @Nullable VoiceLine.Variant captionTake() {
        return shownTake;
    }

    /** The caption's opacity now: in over {@value #FADE_IN} ticks, out over the last {@value #FADE_OUT}. */
    public static float captionAlpha(float partial) {
        if (shown == null) {
            return 0f;
        }
        double t = ticks + partial;
        double in = Math.min(1.0, (t - shownFrom) / FADE_IN);
        double out = Math.min(1.0, (shownUntil - t) / FADE_OUT);
        return (float) Math.max(0.0, Math.min(in, out));
    }

    /** Every line heard to its end, or cut, oldest first (checks). */
    public static List<Heard> history() {
        return List.copyOf(HISTORY);
    }

    /** True if {@code boss}'s line {@code line} has started since the game started (checks). */
    public static boolean heard(String boss, String line) {
        return STARTED.contains(boss + "/" + line);
    }

    /** How many times it has started (checks). */
    public static int count(String boss, String line) {
        return (int) STARTED.stream().filter((boss + "/" + line)::equals).count();
    }

    /** Every line started, as {@code boss/line}, oldest first (checks). */
    public static List<String> started() {
        return List.copyOf(STARTED);
    }

    /** Lines stopped early by a line that was not a cut (the server should never cause one) (checks). */
    public static int interruptions() {
        return (int) HISTORY.stream().filter(Heard::interrupted).count();
    }

    /** This class's tick count (the clock of {@link #history()}). */
    public static long ticks() {
        return ticks;
    }

    /** A take at the listener: relative, no attenuation, the Voice channel; fades out when cut. */
    private static final class Voice extends AbstractTickableSoundInstance {
        final VoiceLine line;
        final String variant;
        final VoiceLine.Variant take;
        private int fade = -1;

        Voice(VoiceLine line, String variant, VoiceLine.Variant take) {
            super(SoundEvent.createVariableRangeEvent(take.sound()), SoundSource.VOICE, SoundInstance.createUnseededRandom());
            this.line = line;
            this.variant = variant;
            this.take = take;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.relative = true;
            this.volume = 1.0f;
        }

        void fadeOut() {
            if (fade < 0) {
                fade = CUT_FADE;
            }
        }

        /** The engine tells vanilla's subtitle overlay about a sound from what this returns: nothing, so no entry and no arrow. */
        @Override
        public net.minecraft.client.sounds.@Nullable WeighedSoundEvents resolve(net.minecraft.client.sounds.SoundManager manager) {
            net.minecraft.client.sounds.WeighedSoundEvents events = super.resolve(manager);
            return events == null ? null : new net.minecraft.client.sounds.WeighedSoundEvents(getLocation(), null);
        }

        @Override
        public void tick() {
            if (fade == 0) {
                stop();
            } else if (fade > 0) {
                volume = (float) fade / (CUT_FADE + 1);
                fade--;
            }
        }
    }
}
