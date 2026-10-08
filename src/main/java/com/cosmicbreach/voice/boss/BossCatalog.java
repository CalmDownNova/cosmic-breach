package com.cosmicbreach.voice.boss;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * A boss's voice lines and how it speaks them (1.1, data driven): {@code assets/cosmicbreach/boss_voice/<boss>.json}, read
 * from the mod's own files on both sides, so the server picks a line by id and the client finds its take by the same id.
 * Written by {@code tools/sound/voice_catalogs.py} from the voice script and the production manifest
 * ({@code tools/sound/voice_lines.json}); the boss's settings stay in the file: {@code global_gap_seconds} (from one line's
 * end to the next start, for lines under priority 90), {@code wait_seconds} (how long a line may wait to start before it
 * is dropped), {@code caption_color} and {@code reference} (the gear check's loadout). Everything is checked on load: a bad
 * catalog fails loudly, naming the line.
 */
public record BossCatalog(String boss, int globalGapTicks, int waitTicks, int captionColor, GearCheck.Reference reference,
        List<VoiceLine> lines) {
    public static final List<String> BOSSES = List.of("colossus", "leviathan", "unsung", "heliarch");

    private static final Map<String, BossCatalog> LOADED = new ConcurrentHashMap<>();

    public BossCatalog {
        lines = List.copyOf(lines);
    }

    /** The catalog of {@code boss} (one of {@link #BOSSES}), read once. */
    public static BossCatalog of(String boss) {
        if (!BOSSES.contains(boss)) {
            throw new IllegalArgumentException("no boss voice " + boss);
        }
        return LOADED.computeIfAbsent(boss, BossCatalog::load);
    }

    public @Nullable VoiceLine line(String id) {
        for (VoiceLine l : lines) {
            if (l.id().equals(id)) {
                return l;
            }
        }
        return null;
    }

    /** The percents its hp_threshold lines use, highest first. */
    public SortedSet<Integer> thresholds() {
        SortedSet<Integer> out = new TreeSet<>(Comparator.reverseOrder());
        for (VoiceLine l : lines) {
            if (l.trigger().is(Trigger.HP_THRESHOLD)) {
                out.add(l.trigger().percent());
            }
        }
        return out;
    }

    /** The absences its player_left lines wait for, in seconds ({@code away>=Ns}, 0 without one). */
    public SortedSet<Integer> awaySteps() {
        SortedSet<Integer> out = new TreeSet<>();
        for (VoiceLine l : lines) {
            if (l.trigger().is(Trigger.PLAYER_LEFT)) {
                int step = 0;
                for (Condition c : l.conditions()) {
                    if (c.kind().equals("away")) {
                        step = Integer.parseInt(c.arg());
                    }
                }
                out.add(step);
            }
        }
        return out;
    }

    private static BossCatalog load(String boss) {
        String path = "/assets/cosmicbreach/boss_voice/" + boss + ".json";
        try (InputStream in = BossCatalog.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("missing " + path);
            }
            return parse(boss, JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Reads and checks a catalog; any problem is an IllegalArgumentException naming the line. */
    public static BossCatalog parse(String boss, JsonObject json) {
        if (!boss.equals(json.get("boss").getAsString())) {
            throw new IllegalArgumentException(boss + ".json says it is " + json.get("boss").getAsString());
        }
        int gap = json.get("global_gap_seconds").getAsInt();
        int wait = json.get("wait_seconds").getAsInt();
        if (gap < 0 || wait < 1) {
            throw new IllegalArgumentException(boss + ": a gap of 0 or more and a wait of 1 or more");
        }
        int color = Integer.parseInt(json.get("caption_color").getAsString().replace("#", ""), 16);
        JsonObject r = json.getAsJsonObject("reference");
        GearCheck.Reference reference = new GearCheck.Reference(r.get("weapon_tier").getAsInt(), r.get("armor").getAsDouble(),
                r.get("toughness").getAsDouble(), r.get("resilience").getAsDouble(), r.get("level_min").getAsInt(),
                r.get("level_max").getAsInt(), r.get("typical_hit").getAsDouble());
        List<VoiceLine> lines = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        JsonArray array = json.getAsJsonArray("lines");
        for (int n = 0; n < array.size(); n++) {
            JsonObject l = array.get(n).getAsJsonObject();
            String id = l.get("id").getAsString();
            String where = boss + " line " + id + ": ";
            try {
                lines.add(line(boss, id, l, n, ids));
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(where + e.getMessage(), e);
            }
        }
        return new BossCatalog(boss, gap * 20, wait * 20, color, reference, lines);
    }

    private static VoiceLine line(String boss, String id, JsonObject l, int order, Set<String> ids) {
        if (!id.matches("[a-z0-9_]+") || !ids.add(id)) {
            throw new IllegalArgumentException("ids are lower case, digits and underscores, and unique");
        }
        Trigger trigger = Trigger.parse(l.get("trigger").getAsString());
        List<Condition> conditions = Condition.parseAll(l.get("condition").getAsString());
        int priority = l.get("priority").getAsInt();
        if (priority < 1 || priority > 100) {
            throw new IllegalArgumentException("priority is 1 to 100");
        }
        int cooldown = repeat(l.get("repeat").getAsString());
        String words = l.get("words").getAsString();
        noDashes(words);
        if (words.isBlank()) {
            throw new IllegalArgumentException("no words");
        }
        Map<String, VoiceLine.Variant> variants = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : l.getAsJsonObject("variants").entrySet()) {
            String key = e.getKey();
            if (!key.equals(VoiceLine.ALL) && !key.matches("1?2?3?") || key.isEmpty()) {
                throw new IllegalArgumentException("variant keys are \"all\" or the living masks in order, like \"13\"");
            }
            variants.put(key, variant(e.getValue().getAsJsonObject()));
        }
        if (variants.isEmpty()) {
            throw new IllegalArgumentException("no take");
        }
        return new VoiceLine(boss, id, trigger, conditions, priority, cooldown, words, variants, order);
    }

    /** {@code once per fight} is 0; {@code cooldown N s} is N. */
    static int repeat(String rule) {
        String r = rule.trim();
        if (r.equals("once per fight")) {
            return 0;
        }
        if (r.matches("cooldown [1-9][0-9]* s")) {
            return Integer.parseInt(r.substring(9, r.length() - 2));
        }
        throw new IllegalArgumentException("repeat is \"once per fight\" or \"cooldown N s\", not \"" + rule + "\"");
    }

    private static VoiceLine.Variant variant(JsonObject v) {
        ResourceLocation sound = ResourceLocation.parse(v.get("event").getAsString());
        String subtitle = v.get("subtitle").getAsString();
        int length = v.get("length_ticks").getAsInt();
        int start = v.has("speech_start_ticks") ? v.get("speech_start_ticks").getAsInt() : 0;
        int end = v.has("speech_ticks") ? v.get("speech_ticks").getAsInt() : length;
        if (length < 1 || start < 0 || end < start || end > length) {
            throw new IllegalArgumentException("a length, and the words inside it");
        }
        List<VoiceLine.Fragment> fragments = new ArrayList<>();
        if (v.has("fragments")) {
            int last = -1;
            for (JsonElement f : v.getAsJsonArray("fragments")) {
                JsonObject o = f.getAsJsonObject();
                int mask = o.get("mask").getAsInt();
                int at = o.get("start_ticks").getAsInt();
                String text = o.get("text").getAsString();
                noDashes(text);
                if (mask < 1 || mask > 3 || at < last || at > length || text.isBlank()) {
                    throw new IllegalArgumentException("fragments: masks 1 to 3, in order inside the take, with words");
                }
                last = at;
                fragments.add(new VoiceLine.Fragment(mask, at, text));
            }
        }
        return new VoiceLine.Variant(sound, subtitle, length, start, end, fragments);
    }

    private static void noDashes(String text) {
        if (text.indexOf((char) 0x2014) >= 0 || text.indexOf((char) 0x2013) >= 0) {
            throw new IllegalArgumentException("no dashes in the words");
        }
    }
}
