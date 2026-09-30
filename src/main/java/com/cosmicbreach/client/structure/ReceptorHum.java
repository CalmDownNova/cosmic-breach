package com.cosmicbreach.client.structure;

import com.cosmicbreach.structure.StructureRegistry;
import com.cosmicbreach.structure.array.LensArrays;
import com.cosmicbreach.structure.array.LensCoreBlockEntity;
import com.cosmicbreach.structure.lens.BeamTrace;
import com.cosmicbreach.structure.lens.Lens;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The receptors' hum (GDD 6.2): every receptor of an unsolved array within {@link #RANGE} blocks hums, a soft
 * crystal loop whose pitch rises as the light passes closer to it: {@link #pitchFor} of the nearest beam's
 * distance, from the segments the server sent. Lit, it sings an octave up. All client side; the server sends
 * nothing per tick.
 */
public final class ReceptorHum {
    public static final double RANGE = 24.0;
    /** The hum's pitch with no beam within this many grid steps. */
    public static final float FAR = 6.0f;

    private record Key(BlockPos core, int port) {}

    private static final Map<Key, Hum> PLAYING = new HashMap<>();
    private static final Map<Key, Float> PITCH = new HashMap<>();

    private ReceptorHum() {
    }

    /** Pitch for the nearest beam {@code distance} grid steps away (infinite for none), or lit. */
    public static float pitchFor(float distance, boolean lit) {
        if (lit) {
            return 2.0f;
        }
        float d = Math.min(distance, FAR);
        return 0.5f + (1.0f - d / FAR);
    }

    /** The pitch the hum of {@code core}'s receptor at {@code port} is heading for now, or NaN if silent (tests). */
    public static float pitch(BlockPos core, int port) {
        Float p = PITCH.get(new Key(core, port));
        return p == null ? Float.NaN : p;
    }

    static void tick(Minecraft mc) {
        Set<Key> wanted = new HashSet<>();
        if (mc.player != null && mc.level != null) {
            for (LensCoreBlockEntity core : LensArrays.near(mc.level, mc.player.position(), RANGE)) {
                if (core.phase() != LensCoreBlockEntity.ACTIVE) {
                    continue;
                }
                int[] ports = core.ports();
                int n = core.size();
                for (int p = 0; p < ports.length; p++) {
                    if (!Lens.isReceptor(ports[p])) {
                        continue;
                    }
                    Key key = new Key(core.getBlockPos(), p);
                    wanted.add(key);
                    boolean lit = (core.litMask() & 1 << p) != 0;
                    float pitch = pitchFor(BeamTrace.closest(core.segments(), Lens.portX(n, p), Lens.portZ(n, p)), lit);
                    PITCH.put(key, pitch);
                    Hum hum = PLAYING.get(key);
                    if (hum == null || hum.isStopped()) {
                        hum = new Hum(Vec3.atCenterOf(core.portPos(p)), mc.player.getRandom(), pitch);
                        PLAYING.put(key, hum);
                        mc.getSoundManager().play(hum);
                    }
                    hum.target = pitch;
                    hum.alive = true;
                }
            }
        }
        PLAYING.entrySet().removeIf(e -> {
            if (!wanted.contains(e.getKey())) {
                e.getValue().alive = false;
                PITCH.remove(e.getKey());
                return true;
            }
            return false;
        });
    }

    static void reset() {
        PLAYING.values().forEach(h -> h.alive = false);
        PLAYING.clear();
        PITCH.clear();
    }

    /** One receptor's looping hum, gliding to its target pitch. */
    private static final class Hum extends AbstractTickableSoundInstance {
        float target;
        boolean alive = true;

        Hum(Vec3 at, RandomSource random, float pitch) {
            super(StructureRegistry.RECEPTOR_HUM.get(), SoundSource.BLOCKS, random);
            this.x = at.x;
            this.y = at.y;
            this.z = at.z;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.5f;
            this.pitch = pitch;
            this.target = pitch;
        }

        @Override
        public void tick() {
            if (!alive) {
                stop();
                return;
            }
            pitch += (target - pitch) * 0.2f;
        }
    }
}
