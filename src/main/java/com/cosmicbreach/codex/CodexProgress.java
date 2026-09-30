package com.cosmicbreach.codex;

import java.util.ArrayList;
import java.util.List;

/**
 * The chapters of the Starfall Codex one player has opened ({@link CodexChapter}), as bits. Pure: the rules that
 * decide them are {@link #derive}, the pages read them through {@link #satisfies}.
 *
 * <p>A condition, as pages write it: terms separated by commas must all hold; alternatives within a term are
 * separated by {@code |}; a leading {@code !} negates one. {@code "drift"}, {@code "met_colossus|drift"},
 * {@code "arrived,!drift"}. An unknown name never holds.
 */
public record CodexProgress(long bits) {
    public static final CodexProgress NONE = new CodexProgress(0L);

    public boolean has(CodexChapter chapter) {
        return (bits & chapter.bit()) != 0;
    }

    public CodexProgress with(CodexChapter chapter) {
        return new CodexProgress(bits | chapter.bit());
    }

    /** The chapters this holds that {@code before} did not. */
    public List<CodexChapter> newSince(CodexProgress before) {
        List<CodexChapter> out = new ArrayList<>();
        for (CodexChapter c : CodexChapter.values()) {
            if (has(c) && !before.has(c)) {
                out.add(c);
            }
        }
        return out;
    }

    /** True if the condition holds (see the class comment); a blank condition always holds. */
    public boolean satisfies(String condition) {
        if (condition == null || condition.isBlank()) {
            return true;
        }
        for (String term : condition.split(",")) {
            boolean any = false;
            for (String alt : term.split("\\|")) {
                String a = alt.trim();
                boolean negate = a.startsWith("!");
                String key = negate ? a.substring(1) : a;
                boolean known = CodexChapter.byKey(key).isPresent();
                boolean holds = known && has(CodexChapter.byKey(key).get());
                if (known && holds != negate) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                return false;
            }
        }
        return true;
    }

    /** True if every name in the condition is a chapter (for the page compiler's warnings). */
    public static boolean valid(String condition) {
        if (condition == null || condition.isBlank()) {
            return true;
        }
        for (String term : condition.split(",")) {
            for (String alt : term.split("\\|")) {
                String a = alt.trim();
                if (CodexChapter.byKey(a.startsWith("!") ? a.substring(1) : a).isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    /** What the world says about a player right now (read on the server). */
    public record Facts(boolean inAetheria, boolean drift, boolean deep, boolean sanctum, boolean colossusKilled,
                        boolean leviathanKilled, boolean unsungKilled, boolean sealed) {
    }

    /**
     * The chapters after looking at {@code facts}: what was opened stays open (a chapter is never taken back), and
     * each fact opens its own. Being attuned implies having arrived; a kill implies having met; a layer's attunement
     * implies having met the guardian that grants it.
     */
    public static CodexProgress derive(CodexProgress stored, Facts facts) {
        CodexProgress p = stored;
        if (facts.inAetheria() || facts.drift() || facts.deep() || facts.sanctum() || facts.sealed()) {
            p = p.with(CodexChapter.ARRIVED);
        }
        if (facts.drift()) {
            p = p.with(CodexChapter.DRIFT);
        }
        if (facts.deep()) {
            p = p.with(CodexChapter.DEEP);
        }
        if (facts.sanctum()) {
            p = p.with(CodexChapter.SANCTUM);
        }
        if (facts.colossusKilled() || facts.drift()) {
            p = p.with(CodexChapter.MET_COLOSSUS);
        }
        if (facts.leviathanKilled() || facts.deep()) {
            p = p.with(CodexChapter.MET_LEVIATHAN);
        }
        if (facts.unsungKilled() || facts.sanctum()) {
            p = p.with(CodexChapter.MET_UNSUNG);
        }
        if (facts.sealed()) {
            p = p.with(CodexChapter.MET_HELIARCH).with(CodexChapter.SEALED);
        }
        return p;
    }
}
