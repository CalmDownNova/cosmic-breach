package com.cosmicbreach.client.lift;

import com.cosmicbreach.lift.LiftRegistry;
import com.cosmicbreach.world.AetheriaWorld;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Each stream within {@value #RANGE} blocks plays the rising-air loop at the point of its column nearest the listener,
 * so a vent or a current is heard before it is seen (sounds.json gives it an attenuation distance of 48). A loop the sound
 * engine never started (no free channel, the master volume at zero, a mod cancelling it) or dropped (a level change stops every
 * sound) is queued again once {@value #GRACE} ticks have shown the engine does not have it.
 */
public final class StreamSound extends AbstractTickableSoundInstance {
    public static final double RANGE = 48.0;
    /** A loop the engine has started is active at once; one that is not this many game ticks after it was queued is lost. */
    static final int GRACE = 20;
    private static final Map<StreamRenderer.Stream, StreamSound> PLAYING = new HashMap<>();
    private final StreamRenderer.Stream stream;
    private final long startedAt;
    private boolean done;

    private StreamSound(StreamRenderer.Stream stream, long startedAt) {
        super(LiftRegistry.STREAM.get(), SoundSource.AMBIENT, RandomSource.create());
        this.stream = stream;
        this.startedAt = startedAt;
        this.looping = true;
        this.delay = 0;
        this.volume = 1.0f;
        this.pitch = 1.0f;
        follow();
    }

    private void follow() {
        Minecraft mc = Minecraft.getInstance();
        double ly = mc.player == null ? stream.bottom() : mc.player.getEyeY();
        x = stream.x();
        y = Mth.clamp(ly, stream.bottom(), stream.top());
        z = stream.z();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        if (done) {
            stop();
            return;
        }
        follow();
    }

    /** True for a loop the engine does not have ({@code active} false) {@value #GRACE} game ticks or more after it was queued. */
    static boolean lost(long now, long startedAt, boolean active) {
        return !active && now - startedAt >= GRACE;
    }

    /** Once a client tick: starts the loop of every stream that has come within range, stops those that have left it and queues again those the engine lost. */
    static void update() {
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level == null ? 0L : mc.level.getGameTime();
        Set<StreamRenderer.Stream> near = new HashSet<>();
        if (mc.player != null && mc.level != null && AetheriaWorld.is(mc.level)) {
            for (StreamRenderer.Stream s : LiftClient.streams()) {
                if (Math.hypot(s.x() - mc.player.getX(), s.z() - mc.player.getZ()) <= RANGE) {
                    near.add(s);
                }
            }
        }
        PLAYING.entrySet().removeIf(e -> {
            StreamSound sound = e.getValue();
            if (!near.contains(e.getKey()) || sound.isStopped() || lost(now, sound.startedAt, mc.getSoundManager().isActive(sound))) {
                sound.done = true;
                return true;
            }
            return false;
        });
        for (StreamRenderer.Stream s : near) {
            if (!PLAYING.containsKey(s)) {
                StreamSound sound = new StreamSound(s, now);
                PLAYING.put(s, sound);
                mc.getSoundManager().play(sound);
            }
        }
    }

    static void stopAll() {
        PLAYING.values().forEach(s -> s.done = true);
        PLAYING.clear();
    }

    /** How many stream loops this client keeps going (tests; the test client is muted). */
    public static int playing() {
        return PLAYING.size();
    }
}
