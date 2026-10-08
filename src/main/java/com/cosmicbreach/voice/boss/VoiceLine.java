package com.cosmicbreach.voice.boss;

import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * One line a boss can say, as its catalog holds it ({@code assets/cosmicbreach/boss_voice/<boss>.json}, made from the voice
 * script and the production manifest): what fires it and when it may play (conditions, priority 1 to 100, a cooldown in
 * seconds or 0 for once a fight), its words (the caption), and its takes. Most lines have one take ({@link #ALL}); the
 * Unsung's break lines have one per set of living masks ({@code "12"}, {@code "3"}). {@code order} is its row in the
 * script, the tiebreak between equal priorities.
 */
public record VoiceLine(String boss, String id, Trigger trigger, List<Condition> conditions, int priority, int cooldownSeconds,
        String words, Map<String, Variant> variants, int order) {
    /** The variant key of a line with one take. */
    public static final String ALL = "all";

    /** A piece of a relayed sentence: the mask that speaks it (1 Alto, 2 Tenor, 3 Bass), when in the take, its words. */
    public record Fragment(int mask, int startTicks, String text) {}

    /**
     * One take: its sound event and subtitle key, its length in ticks (the .ogg rounded up), where the words start and
     * end inside it, and for a relayed sentence its fragments.
     */
    public record Variant(ResourceLocation sound, String subtitle, int lengthTicks, int speechStartTicks, int speechTicks,
            List<Fragment> fragments) {
        public Variant {
            fragments = List.copyOf(fragments);
        }
    }

    public VoiceLine {
        conditions = List.copyOf(conditions);
        variants = Map.copyOf(variants);
    }

    /** True if {@code fired} is this line's trigger, or a weapon its {@code also:} conditions add. */
    public boolean answers(Trigger fired) {
        if (!trigger.kind().equals(fired.kind())) {
            return false;
        }
        if (trigger.arg().equals(fired.arg())) {
            return true;
        }
        if (fired.is(Trigger.WEAPON)) {
            for (Condition c : conditions) {
                if (c.kind().equals("also") && c.arg().equals(fired.arg())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** True if every condition holds at {@code c}. */
    public boolean fits(Context c) {
        for (Condition k : conditions) {
            if (!k.test(c)) {
                return false;
            }
        }
        return true;
    }

    /** A cooldown line may come back in the same fight; any other plays once a fight. */
    public boolean repeats() {
        return cooldownSeconds > 0;
    }

    /** The take for these living masks: its own variant, or the line's only one. */
    public @Nullable Variant variant(String living) {
        Variant v = variants.get(living);
        return v != null ? v : variants.get(ALL);
    }

    /** The key of the take {@link #variant} picks for {@code living}. */
    public String variantKey(String living) {
        return variants.containsKey(living) ? living : ALL;
    }

    /** The whole masks the line needs ({@code masks:N}), or 0. */
    public int masksNeeded() {
        for (Condition c : conditions) {
            if (c.kind().equals("masks")) {
                return Integer.parseInt(c.arg());
            }
        }
        return 0;
    }
}
