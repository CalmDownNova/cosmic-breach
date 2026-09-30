package com.cosmicbreach.voice;

import com.mojang.serialization.Codec;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The Echo lines one player has been told (saved on the player, kept through death), by id, so a line added or
 * renamed later never breaks an old save. Pure.
 */
public record EchoMemory(Set<String> heard) {
    public static final EchoMemory NEW = new EchoMemory(Set.of());

    public static final Codec<EchoMemory> CODEC = Codec.STRING.listOf()
            .xmap(list -> new EchoMemory(new TreeSet<>(list)), memory -> List.copyOf(memory.heard()));

    public EchoMemory {
        heard = Collections.unmodifiableSet(new TreeSet<>(heard));
    }

    public boolean heard(EchoLine line) {
        return heard.contains(line.id());
    }

    /** True if {@code line} should be said now: not heard yet, and nothing that makes it pointless has been. */
    public boolean shouldSay(EchoLine line) {
        if (heard(line)) {
            return false;
        }
        for (EchoLine later : line.supersededBy()) {
            if (heard(later)) {
                return false;
            }
        }
        return true;
    }

    public EchoMemory with(EchoLine line) {
        if (heard(line)) {
            return this;
        }
        Set<String> more = new TreeSet<>(heard);
        more.add(line.id());
        return new EchoMemory(more);
    }
}
