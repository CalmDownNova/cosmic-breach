package com.cosmicbreach.voice;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The lines of the Starfall's voice (the Choir's last hum, the player's guide), each heard once per player. The
 * sound is {@code cosmicbreach:echo/<name>} (made by {@code tools/sound/voice.py} from the recorded takes), its
 * subtitle the words in quotes. When each one is said:
 *
 * <ul>
 *   <li>{@link #FIRST_SHARD}: the first Starfall Shard the player picks up (unless they have already opened a ring
 *       or reached Aetheria, when "take me home" would come too late).</li>
 *   <li>{@link #RING_OPEN}: the first Breach the player opens.</li>
 *   <li>{@link #ARRIVAL}: their first arrival in Aetheria, once the white of falling up has cleared.</li>
 *   <li>{@link #DRIFT}, {@link #DEEP}, {@link #SANCTUM}: a guardian's first kill ({@code GuardianType.echo()}: the
 *       Colossus, the Leviathan, the Unsung), after the rewards.</li>
 *   <li>{@link #SEALED}: the Heliarch's death (G9 says it).</li>
 * </ul>
 * Pure: no game state.
 *
 * @see Echo#say
 */
public enum EchoLine {
    FIRST_SHARD(12, false, 133, "You can hear it... can't you? Take me home."),
    RING_OPEN(36, false, 146, "The ground remembers the sky. Now... fall up."),
    ARRIVAL(10, true, 138, "Home. Oh... it's all still singing."),
    DRIFT(80, false, 151, "Do you hear that? One more voice. The Drift will carry you now."),
    DEEP(80, false, 138, "Below us, the song goes silent. Bring your light."),
    SANCTUM(80, false, 170, "They remember how to sing. He is waiting, at the bottom of the Breach."),
    SEALED(80, false, 114, "The Breach is sealed. Sing with us.");

    private final int leadTicks;
    private final boolean afterFallUp;
    private final int lengthTicks;
    private final String words;

    EchoLine(int leadTicks, boolean afterFallUp, int lengthTicks, String words) {
        this.leadTicks = leadTicks;
        this.afterFallUp = afterFallUp;
        this.lengthTicks = lengthTicks;
        this.words = words;
    }

    /** Its name in files, commands and saves: {@code first_shard}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The sound event's path under {@code cosmicbreach}: {@code echo/first_shard}. */
    public String soundPath() {
        return "echo/" + id();
    }

    public String subtitleKey() {
        return "subtitles.cosmicbreach.echo." + id();
    }

    /**
     * Client ticks between the line being said and its start: room for what the moment itself sounds like first
     * (the pickup, the ring's chord, a guardian's fall and the advancement's fanfare). For {@link #afterFallUp}
     * lines, counted from when the white of falling up has cleared.
     */
    public int leadTicks() {
        return leadTicks;
    }

    /**
     * How long the line's sound lasts, in ticks (its .ogg, rounded up; a test checks them). The client holds the next
     * line back this long even when the sound can't be heard (the game muted), so subtitles never pile up.
     */
    public int lengthTicks() {
        return lengthTicks;
    }

    /** True if it waits until the white of falling up has cleared (the arrival). */
    public boolean afterFallUp() {
        return afterFallUp;
    }

    /** The spoken words (the subtitle is these in quotes). */
    public String words() {
        return words;
    }

    /** Lines that, once heard, make this one pointless: the shard's "take me home" after the way home is open. */
    public Set<EchoLine> supersededBy() {
        return this == FIRST_SHARD ? EnumSet.of(RING_OPEN, ARRIVAL) : EnumSet.noneOf(EchoLine.class);
    }

    public static Optional<EchoLine> byId(String id) {
        for (EchoLine line : values()) {
            if (line.id().equals(id)) {
                return Optional.of(line);
            }
        }
        return Optional.empty();
    }
}
