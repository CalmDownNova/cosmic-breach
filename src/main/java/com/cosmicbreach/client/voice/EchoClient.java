package com.cosmicbreach.client.voice;

import com.cosmicbreach.client.onboarding.FallUpClient;
import com.cosmicbreach.mixin.client.GuiSubtitleAccessor;
import com.cosmicbreach.voice.Echo;
import com.cosmicbreach.voice.EchoLine;
import com.cosmicbreach.voice.EchoQueue;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The Starfall's voice on the client: the lines the server says ({@link Echo.Say}) wait in an {@link EchoQueue} and
 * play one at a time, not positional ({@code SoundSource.VOICE}, at the listener, so nothing pans), with their
 * subtitle kept up for as long as the line plays (vanilla drops a subtitle 3 s after its sound starts; the lines
 * run 6 to 8 s) and placed just ahead of the camera, so it never shows a direction arrow. With the game muted a
 * line still holds the voice for its length ({@link EchoLine#lengthTicks()}), so the subtitles come one at a time.
 */
public final class EchoClient {
    /** A line that played: when it started and ended, in this class's client ticks (checks). */
    public record Played(EchoLine line, long start, long end) {}

    private static final EchoQueue QUEUE = new EchoQueue();
    private static final List<Played> HISTORY = new ArrayList<>();
    private static long ticks;
    private static @Nullable Voice playing;
    private static long startedAt;
    private static @Nullable ClientLevel startedIn;

    private EchoClient() {
    }

    public static void register(IEventBus modBus, IEventBus game) {
        game.addListener(ClientTickEvent.Post.class, event -> tick());
        game.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> reset());
    }

    /** The server said {@code line} to this player (client thread). */
    public static void onSay(EchoLine line) {
        QUEUE.offer(line, ticks);
    }

    private static void tick() {
        ticks++;
        Minecraft mc = Minecraft.getInstance();
        if (playing != null) {
            // a line lasts while its sound plays, and at least its length when the game is muted (the engine then
            // plays nothing); a world change stops every sound, so a line cut off by one ends there
            boolean sounding = mc.getSoundManager().isActive(playing);
            boolean cutOff = !sounding && mc.level != startedIn;
            if (cutOff || !sounding && ticks - startedAt >= playing.line.lengthTicks()) {
                HISTORY.add(new Played(playing.line, startedAt, ticks));
                playing = null;
                QUEUE.finished(ticks);
            } else {
                keepSubtitle(mc, playing);
            }
        }
        boolean blocked = mc.level == null || mc.player == null || FallUpClient.active() || mc.screen instanceof ReceivingLevelScreen;
        EchoLine next = QUEUE.next(ticks, blocked);
        if (next != null) {
            playing = new Voice(next);
            startedAt = ticks;
            startedIn = mc.level;
            mc.getSoundManager().play(playing);
            keepSubtitle(mc, playing);
        }
    }

    /**
     * Refreshes the line's subtitle at a point two blocks ahead of the camera: the overlay keeps a subtitle for 3 s
     * after its last refresh, and draws no arrow for a sound in front.
     */
    private static void keepSubtitle(Minecraft mc, Voice voice) {
        if (!mc.options.showSubtitles().get()) {
            return;
        }
        WeighedSoundEvents events = mc.getSoundManager().getSoundEvent(voice.getLocation());
        if (events == null || events.getSubtitle() == null) {
            return;
        }
        var camera = mc.gameRenderer.getMainCamera();
        Vec3 at = camera.getPosition().add(new Vec3(camera.getLookVector()).scale(2.0));
        SoundInstance here = new SimpleSoundInstance(voice.getLocation(), SoundSource.VOICE, 1.0f, 1.0f,
                SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, at.x, at.y, at.z, false);
        ((GuiSubtitleAccessor) mc.gui).cosmicbreach$subtitleOverlay().onPlaySound(here, events, Float.POSITIVE_INFINITY);
    }

    private static void reset() {
        if (playing != null) {
            Minecraft.getInstance().getSoundManager().stop(playing);
            playing = null;
        }
        startedIn = null;
        QUEUE.clear();
    }

    /** The line playing now, or null (checks). */
    public static @Nullable EchoLine playing() {
        return playing == null ? null : playing.line;
    }

    /** The lines waiting to play (checks). */
    public static List<EchoLine> waiting() {
        return QUEUE.waiting();
    }

    /** Every line that has played to its end (or was cut off) since the game started (checks). */
    public static List<Played> history() {
        return List.copyOf(HISTORY);
    }

    /** This class's client tick count (the clock of {@link #history()}). */
    public static long ticks() {
        return ticks;
    }

    /** A line: at the listener (relative, no attenuation), so it sounds the same wherever the player looks or goes. */
    private static final class Voice extends AbstractTickableSoundInstance {
        final EchoLine line;

        Voice(EchoLine line) {
            super(Echo.sound(line), SoundSource.VOICE, SoundInstance.createUnseededRandom());
            this.line = line;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.relative = true;
        }

        @Override
        public void tick() {
        }
    }
}
