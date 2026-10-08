package com.cosmicbreach.voice.boss;

import com.cosmicbreach.CosmicBreach;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * What a boss line pulls down while it plays (1.1, "Voice over music"): its own boss's music, by about 6 dB, in over
 * {@value #ATTACK} ticks and out over {@value #RELEASE} after the line ends. Never another boss's, never a sound effect and
 * never the voices. The boss's loud wake and phase sounds are waited out instead ({@link BossVoiceSounds}): the engine clamps
 * an effect played at volume 2 to 6 to a gain of 1, so no factor can lower it. Names are the start of a sound event's path
 * ({@code music/unsung/} covers the choir's stems). Pure; the client applies it ({@code BossVoiceClient}).
 */
public final class BossVoiceDuck {
    /** The music's level while a line plays: about -6 dB (the loops play at 1 or less, so the engine's clamp never hides it). */
    public static final float MUSIC = 0.5f;
    public static final int ATTACK = 3;
    public static final int RELEASE = 10;

    private static final Map<String, List<String>> OWN = Map.of(
            "colossus", List.of("music/colossus"),
            "leviathan", List.of("music/leviathan"),
            "unsung", List.of("music/unsung/"),
            "heliarch", List.of("music/heliarch_"));

    private BossVoiceDuck() {
    }

    /** True if a line of {@code boss} pulls {@code event} down. */
    public static boolean ducks(String boss, ResourceLocation event) {
        if (!event.getNamespace().equals(CosmicBreach.MOD_ID)) {
            return false;
        }
        for (String name : OWN.getOrDefault(boss, List.of())) {
            if (event.getPath().startsWith(name)) {
                return true;
            }
        }
        return false;
    }

    /** Every name above (checks). */
    public static List<String> names() {
        List<String> out = new ArrayList<>();
        OWN.values().forEach(out::addAll);
        return out;
    }

    /** How far into the duck {@code now} is, 0 to 1: in over {@value #ATTACK} ticks from {@code start}, out over {@value #RELEASE} after {@code end}. */
    public static float envelope(long now, long start, long end) {
        double in = (now - start) / (double) ATTACK;
        double out = (end + RELEASE - now) / (double) RELEASE;
        return (float) Math.max(0.0, Math.min(1.0, Math.min(in, out)));
    }

    /** The factor to multiply {@code event}'s volume by at {@code now} while {@code boss} speaks from {@code start} to {@code end}. */
    public static float factor(String boss, ResourceLocation event, long now, long start, long end) {
        if (!ducks(boss, event)) {
            return 1f;
        }
        return 1f - (1f - MUSIC) * envelope(now, start, end);
    }
}
