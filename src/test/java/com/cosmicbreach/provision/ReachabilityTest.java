package com.cosmicbreach.provision;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cosmicbreach.gear.forge.ForgeTiers;
import com.cosmicbreach.guardian.RewardTable;
import com.cosmicbreach.guardian.colossus.ColossusLoot;
import com.cosmicbreach.guardian.heliarch.HeliarchLoot;
import com.cosmicbreach.guardian.leviathan.LeviathanLoot;
import com.cosmicbreach.guardian.unsung.UnsungLoot;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The Twilight Forest rule, proved over the mod's own data (Aetheria 1.1 Design, section 1): starting empty handed at the
 * portal, everything any mod recipe, upgrade or gate needs can be found or made inside the levels.
 *
 * <p>The model. Each level's natural sources ({@link #SOURCES}: blocks with the pickaxe tier their drops need, creatures,
 * structure vaults) give what their loot tables give; bosses give their reward tables and open the next level; every
 * recipe in {@code data/cosmicbreach/recipe} (crafting, smelting, smoking, the Astral Forge) makes its result once its
 * station stands and its ingredients are in hand; vanilla's first tools and stations come from the seven entries of
 * {@link #vanillaRecipes()} (six vanilla recipes, and stripping a log). Tags are read from the mod's tag files (plus the few vanilla members that matter: a stick is a wooden rod). A
 * loot entry behind Silk Touch never counts (no enchanting inside); one behind shears counts once shears can be made.
 * Stonecutting and blasting need iron stations and never count. It runs until nothing new turns up.
 *
 * <p>Worlds explored before 1.1. A source that only a 1.1 world feature creates ({@link Source#via}) exists in chunks generated
 * after the update, not in the ones an existing world already has. Every test that closes over the levels therefore runs
 * twice, once with every source and once for an existing world ({@code existingWorld}), where those sources are left out and
 * everything must still come from the ones that are there (a drop, a creature, a craft). A world feature can be a second
 * source, never the only one.
 */
class ReachabilityTest {
    static final int HAND = 0;
    static final int WOOD = 1;
    static final int STONE = 2;
    static final int IRON = 3;
    static final int DIAMOND = 4;

    enum Kind { BLOCK, MOB, VAULT }

    /**
     * A natural source: in {@code level}, of {@code kind}, its id (a block, an entity, or a vault's loot table), the tier its
     * drops need, and whether what it gives comes back. {@code via} is the 1.1 world feature that alone puts it in the world
     * (a configured feature's id), or null if worlds explored before 1.1 have it too.
     * {@code ProvisionWorldgenTest} checks that each {@code via} feature is registered and placed in the source's level.
     */
    record Source(int level, Kind kind, String id, int tier, boolean renewable, String via) {
        Source(int level, Kind kind, String id, int tier, boolean renewable) {
            this(level, kind, id, tier, renewable, null);
        }
    }

    static final List<Source> SOURCES = List.of(
            // level 1, the Upper Reach
            new Source(1, Kind.BLOCK, "cosmicbreach:starfall_stone", WOOD, false),
            new Source(1, Kind.BLOCK, "cosmicbreach:glimmer_grass", WOOD, false),
            new Source(1, Kind.BLOCK, "cosmicbreach:spire_quartz", WOOD, false),
            new Source(1, Kind.BLOCK, "cosmicbreach:starsteel_ore", STONE, false),
            new Source(1, Kind.BLOCK, "cosmicbreach:meteorite", STONE, false),
            new Source(1, Kind.BLOCK, "cosmicbreach:halo_moss", HAND, true),
            new Source(1, Kind.BLOCK, "cosmicbreach:halo_moss_plant", HAND, true),
            new Source(1, Kind.BLOCK, "cosmicbreach:starbloom", HAND, true),
            new Source(1, Kind.BLOCK, "cosmicbreach:driftwood_log", HAND, false, "cosmicbreach:fallen_driftwood"),   // fallen Driftwood: new chunks only
            new Source(1, Kind.MOB, "cosmicbreach:shardling", HAND, true),
            new Source(1, Kind.MOB, "cosmicbreach:lumen_stag", HAND, true),
            new Source(1, Kind.VAULT, "cosmicbreach:vaults/reliquary", HAND, false),
            // level 2, the Drift
            new Source(2, Kind.BLOCK, "cosmicbreach:driftstone", WOOD, false),
            new Source(2, Kind.BLOCK, "cosmicbreach:nebulite_ore", IRON, false),
            new Source(2, Kind.BLOCK, "cosmicbreach:rimeglass", WOOD, false),
            new Source(2, Kind.BLOCK, "cosmicbreach:driftwood_log", HAND, false),
            new Source(2, Kind.MOB, "cosmicbreach:gyre_knight", HAND, true),
            new Source(2, Kind.MOB, "cosmicbreach:drift_manta", HAND, true),
            new Source(2, Kind.VAULT, "cosmicbreach:vaults/observatory", HAND, false),
            // level 3, the Deep
            new Source(3, Kind.BLOCK, "cosmicbreach:umbral_basalt", WOOD, false),
            new Source(3, Kind.BLOCK, "cosmicbreach:eclipsium_ore", DIAMOND, false),
            new Source(3, Kind.BLOCK, "cosmicbreach:rift_glass", WOOD, false),
            new Source(3, Kind.BLOCK, "cosmicbreach:magenta_neon_lichen", HAND, false),
            new Source(3, Kind.BLOCK, "cosmicbreach:teal_neon_lichen", HAND, false),
            new Source(3, Kind.BLOCK, "cosmicbreach:umbral_cap", HAND, true, "cosmicbreach:umbral_cap_patch"),   // new chunks only
            new Source(3, Kind.MOB, "cosmicbreach:hollow_stalker", HAND, true),
            new Source(3, Kind.MOB, "cosmicbreach:drift_jelly", HAND, true),
            new Source(3, Kind.VAULT, "cosmicbreach:vaults/crypt", HAND, false),
            // level 4, the Breach Sanctum: its wings' vaults; it stands in the Deep's dark, where the lichen and the Umbral Cap grow
            new Source(4, Kind.VAULT, "cosmicbreach:vaults/sanctum_choir", HAND, false),
            new Source(4, Kind.VAULT, "cosmicbreach:vaults/sanctum_lens", HAND, false),
            new Source(4, Kind.BLOCK, "cosmicbreach:magenta_neon_lichen", HAND, false),
            new Source(4, Kind.BLOCK, "cosmicbreach:teal_neon_lichen", HAND, false),
            new Source(4, Kind.BLOCK, "cosmicbreach:umbral_cap", HAND, true, "cosmicbreach:umbral_cap_patch"));

    /**
     * Foods that grow back once you have one: planted, they spread by themselves. A source that gives one feeds its level
     * for good even if the source itself runs out (Neon Lichen, cut, turns up caps that are then planted in the dark).
     */
    static final Set<String> SELF_GROWING_FOODS = Set.of("cosmicbreach:umbral_cap");

    /** A guardian: its level, what a kill gives, what summons it (null: its lair wakes it), the level it opens (0: none). */
    record Boss(int level, Set<String> rewards, String summon, int opens) {}

    /** The pickaxes and the tier each mines at (the mod's two are checked against their Tier objects by ProvisionsTest). */
    static final Map<String, Integer> PICKAXES = Map.of("minecraft:wooden_pickaxe", WOOD, "minecraft:stone_pickaxe", STONE,
            "cosmicbreach:starsteel_pickaxe", IRON, "cosmicbreach:nebulite_pickaxe", DIAMOND);

    /** Vanilla tag members the model needs (they come from vanilla or NeoForge, not the mod's files). */
    static final Map<String, Set<String>> VANILLA_MEMBERS = Map.of("c:rods/wooden", Set.of("minecraft:stick"));

    /** The portal is built in the Overworld by design: its frame's copper is the one Overworld need. */
    static final Set<String> OVERWORLD_BY_DESIGN = Set.of("cosmicbreach:breach_frame");

    enum Station { GRID, TABLE, FURNACE, FORGE, NONE }

    /** One ingredient: any of these items, or any member of these tags. */
    record Need(Set<String> items, Set<String> tags) {}

    record Recipe(String id, Station station, int forgeTier, List<Need> needs, String result) {}

    private static Path root;
    private static final Map<String, Set<String>> TAGS = new HashMap<>();
    private static final List<Recipe> RECIPES = new ArrayList<>();
    private static final Set<String> FUELS = new HashSet<>();
    private static final List<Boss> BOSSES = new ArrayList<>();

    @BeforeAll
    static void read() throws IOException {
        root = projectFile("src");
        for (String base : List.of("main/resources/data", "generated/resources/data")) {
            Path data = root.resolve(base);
            try (Stream<Path> files = Files.walk(data)) {
                for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                    String rel = data.relativize(f).toString().replace('\\', '/');
                    String ns = rel.substring(0, rel.indexOf('/'));
                    String path = rel.substring(ns.length() + 1, rel.length() - 5);
                    if (path.startsWith("tags/item/")) {
                        TAGS.computeIfAbsent(ns + ":" + path.substring("tags/item/".length()), k -> new LinkedHashSet<>()).addAll(values(json(f)));
                    } else if (path.startsWith("recipe/")) {
                        Recipe r = recipe(ns + ":" + path.substring("recipe/".length()), json(f));
                        if (r != null) {
                            RECIPES.add(r);
                        }
                    } else if (ns.equals("neoforge") && path.equals("data_maps/item/furnace_fuels")) {
                        FUELS.addAll(json(f).getAsJsonObject("values").keySet());
                    }
                }
            }
        }
        VANILLA_MEMBERS.forEach((tag, items) -> TAGS.computeIfAbsent(tag, k -> new LinkedHashSet<>()).addAll(items));
        RECIPES.addAll(vanillaRecipes());
        BOSSES.add(new Boss(1, ids(ColossusLoot.TABLE), null, 2));
        BOSSES.add(new Boss(2, ids(LeviathanLoot.TABLE), null, 3));
        BOSSES.add(new Boss(3, ids(UnsungLoot.TABLE), null, 4));
        BOSSES.add(new Boss(4, Set.of(HeliarchLoot.LAST_LIGHT.toString(), HeliarchLoot.CROWN.toString(), HeliarchLoot.SOLAR_HEART.toString(),
                HeliarchLoot.ECLIPSIUM.toString()), "cosmicbreach:dying_star_heart", 0));
    }

    // ------------------------------------------------------------------ the proof

    @ParameterizedTest(name = "{displayName} [existing world only: {0}]")
    @ValueSource(booleans = {false, true})
    void everyIngredientOfEveryModRecipeIsFoundOrMadeInsideTheLevels(boolean existingWorld) {
        World w = closure(4, true, true, existingWorld);
        Set<String> missing = new TreeSet<>();
        for (Recipe r : RECIPES) {
            if (!r.id().startsWith("cosmicbreach:") || OVERWORLD_BY_DESIGN.contains(r.result()) || anotherWayMakes(r, w.have)) {
                continue;
            }
            for (Need n : r.needs()) {
                if (!satisfied(n, w.have)) {
                    missing.add(r.id() + " needs " + describe(n));
                }
            }
        }
        assertTrue(missing.isEmpty(), "not provided inside the levels" + where(existingWorld) + ": " + missing);
    }

    @ParameterizedTest(name = "{displayName} [existing world only: {0}]")
    @ValueSource(booleans = {false, true})
    void everyForgeRecipeUpgradeAndGateIsReachable(boolean existingWorld) {
        World w = closure(4, true, true, existingWorld);
        Set<String> gates = new TreeSet<>(List.of("minecraft:crafting_table", "minecraft:furnace", "cosmicbreach:astral_forge",
                "cosmicbreach:starsteel_pickaxe", "cosmicbreach:nebulite_pickaxe", "cosmicbreach:starbloom", "cosmicbreach:resonance_chime",
                "cosmicbreach:astral_saddle", "cosmicbreach:drift_harness", "cosmicbreach:dying_star_heart", "cosmicbreach:guardian_echo",
                "minecraft:torch", "minecraft:glass_bottle", "minecraft:shears"));
        for (int tier = 2; tier <= 4; tier++) {
            ForgeTiers.upgradeItem(tier).ifPresent(id -> gates.add(id.toString()));
            ForgeTiers.reforgeCost(tier).ifPresent(cost -> gates.add(cost.item().toString()));
        }
        for (Recipe r : RECIPES) {
            if (r.station() == Station.FORGE) {
                gates.add(r.result());
            }
        }
        Set<String> missing = new TreeSet<>(gates);
        missing.removeAll(w.have);
        assertTrue(missing.isEmpty(), "out of reach" + where(existingWorld) + ": " + missing);
        assertTrue(w.level == 4, "every level opens" + where(existingWorld));
    }

    @ParameterizedTest(name = "{displayName} [existing world only: {0}]")
    @ValueSource(booleans = {false, true})
    void theTierThatMinesNebulaOreIsReachedInsideLevelOne(boolean existingWorld) {
        World w = closure(1, false, false, existingWorld);
        assertTrue(pickTier(w.have) >= IRON, "level 1 alone, no vault, no boss" + where(existingWorld) + ": the best pickaxe is tier "
                + pickTier(w.have) + "; has " + w.have);
        assertTrue(stationReady(Station.FURNACE, 1, w.have), "a furnace and fuel inside level 1" + where(existingWorld));
        assertTrue(w.have.contains("cosmicbreach:astral_forge"), "the Forge inside level 1" + where(existingWorld));
    }

    @ParameterizedTest(name = "{displayName} [existing world only: {0}]")
    @ValueSource(booleans = {false, true})
    void everyLaterOreIsMinedWithWhatTheLevelsBeforeItGive(boolean existingWorld) {
        World w = closure(2, false, true, existingWorld);
        assertTrue(pickTier(w.have) >= DIAMOND, "levels 1 and 2" + where(existingWorld) + ": the best pickaxe is tier " + pickTier(w.have));
    }

    @ParameterizedTest(name = "{displayName} [existing world only: {0}]")
    @ValueSource(booleans = {false, true})
    void everyLevelFeedsYouFromSomethingThatComesBack(boolean existingWorld) {
        Set<String> foods = members("c:foods");
        World w = closure(4, true, true, existingWorld);
        for (int level = 1; level <= 4; level++) {
            boolean fed = false;
            for (Source s : SOURCES) {
                if (s.level() == level && !(existingWorld && s.via() != null)) {
                    for (String item : loot(table(s), w.have, 0)) {
                        fed |= foods.contains(item) && (s.renewable() || SELF_GROWING_FOODS.contains(item));
                    }
                }
            }
            assertTrue(fed, "level " + level + " has no renewable food" + where(existingWorld));
        }
    }

    /** Planks, sticks, a crafting table and fuel from level 1's own sources alone, where no new chunk has put a fallen log. */
    @Test
    void anExistingWorldFindsWoodInsideLevelOne() {
        World w = closure(1, false, false, true);
        assertTrue(members("minecraft:planks").stream().anyMatch(w.have::contains), "level 1 of an existing world: no planks; has " + w.have);
        assertTrue(w.have.contains("minecraft:stick"), "level 1 of an existing world: no sticks");
        assertTrue(w.have.contains("minecraft:crafting_table"), "level 1 of an existing world: no crafting table");
        assertTrue(fuel(w.have), "level 1 of an existing world: nothing to burn");
    }

    /** The existing world flag really cuts: a fallen log is a new chunk's and never turns up for an existing world. */
    @Test
    void aWorldgenOnlySourceNeverCountsForAnExistingWorld() {
        assertTrue(closure(1, false, false, false).have.contains("cosmicbreach:driftwood_log"), "a new world's level 1 has fallen logs");
        assertFalse(closure(1, false, false, true).have.contains("cosmicbreach:driftwood_log"), "an existing world's level 1 has no fallen logs");
        for (Source s : SOURCES) {
            if (s.via() != null) {
                assertTrue(s.kind() == Kind.BLOCK, "a world feature places blocks: " + s);
            }
        }
    }

    private static String where(boolean existingWorld) {
        return existingWorld ? " (existing world: no source that only a new chunk has)" : "";
    }

    @Test
    void theDeclaredToolTiersAreTheTagsTiers() {
        Map<String, Integer> needs = new HashMap<>();
        for (String[] t : new String[][] {{"needs_stone_tool", "2"}, {"needs_iron_tool", "3"}, {"needs_diamond_tool", "4"}}) {
            Path f = root.resolve("generated/resources/data/minecraft/tags/block/" + t[0] + ".json");
            if (Files.exists(f)) {
                for (String b : values(json(f))) {
                    needs.merge(b, Integer.parseInt(t[1]), Math::max);
                }
            }
        }
        for (Source s : SOURCES) {
            if (s.kind() == Kind.BLOCK) {
                assertTrue(s.tier() >= needs.getOrDefault(s.id(), HAND), s.id() + " needs tier " + needs.get(s.id()));
            }
        }
    }

    // ------------------------------------------------------------------ the closure

    static final class World {
        final Set<String> have = new HashSet<>();
        int level = 1;
    }

    /** What a player holds once nothing more turns up. {@code existingWorld}: leave out the sources only a new chunk has. */
    static World closure(int maxLevel, boolean vaults, boolean bosses, boolean existingWorld) {
        World w = new World();
        boolean changed = true;
        while (changed) {
            changed = false;
            int tier = pickTier(w.have);
            int open = Math.min(w.level, maxLevel);
            for (Source s : SOURCES) {
                if (s.level() > open || s.kind() == Kind.VAULT && !vaults || s.kind() == Kind.BLOCK && s.tier() > tier
                        || existingWorld && s.via() != null) {
                    continue;
                }
                changed |= w.have.addAll(loot(table(s), w.have, 0));
            }
            if (bosses) {
                for (Boss b : BOSSES) {
                    if (b.level() <= open && (b.summon() == null || w.have.contains(b.summon()))) {
                        changed |= w.have.addAll(b.rewards());
                        if (b.opens() > w.level) {
                            w.level = b.opens();
                            changed = true;
                        }
                    }
                }
            }
            for (Recipe r : RECIPES) {
                if (!w.have.contains(r.result()) && stationReady(r.station(), r.forgeTier(), w.have)
                        && r.needs().stream().allMatch(n -> satisfied(n, w.have))) {
                    w.have.add(r.result());
                    changed = true;
                }
            }
        }
        return w;
    }

    /** True if another recipe for the same result works (an ore smelted beside its raw metal, a blasted copy of a smelt). */
    static boolean anotherWayMakes(Recipe r, Set<String> have) {
        for (Recipe o : RECIPES) {
            if (o != r && o.result().equals(r.result()) && stationReady(o.station(), o.forgeTier(), have)
                    && o.needs().stream().allMatch(n -> satisfied(n, have))) {
                return true;
            }
        }
        return false;
    }

    static int pickTier(Set<String> have) {
        int best = HAND;
        for (Map.Entry<String, Integer> e : PICKAXES.entrySet()) {
            if (have.contains(e.getKey())) {
                best = Math.max(best, e.getValue());
            }
        }
        return best;
    }

    static boolean stationReady(Station station, int forgeTier, Set<String> have) {
        return switch (station) {
            case GRID -> true;
            case TABLE -> have.contains("minecraft:crafting_table");
            case FURNACE -> have.contains("minecraft:furnace") && fuel(have);
            case FORGE -> have.contains("cosmicbreach:astral_forge") && forgeTier(have) >= forgeTier;
            case NONE -> false;
        };
    }

    static boolean fuel(Set<String> have) {
        for (String item : have) {
            if (FUELS.contains(item) || members("minecraft:logs").contains(item) || members("minecraft:planks").contains(item)) {
                return true;
            }
        }
        return false;
    }

    static int forgeTier(Set<String> have) {
        int tier = 1;
        while (tier < 4 && ForgeTiers.upgradeItem(tier + 1).map(id -> have.contains(id.toString())).orElse(false)) {
            tier++;
        }
        return tier;
    }

    static boolean satisfied(Need n, Set<String> have) {
        for (String item : n.items()) {
            if (have.contains(item)) {
                return true;
            }
        }
        for (String tag : n.tags()) {
            for (String item : members(tag)) {
                if (have.contains(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    static String describe(Need n) {
        return n.items().isEmpty() ? "#" + String.join(" or #", n.tags()) : String.join(" or ", n.items());
    }

    // ------------------------------------------------------------------ the data

    static Set<String> members(String tag) {
        Set<String> out = new LinkedHashSet<>();
        expand(tag, out, new HashSet<>());
        return out;
    }

    private static void expand(String tag, Set<String> out, Set<String> seen) {
        if (!seen.add(tag)) {
            return;
        }
        for (String v : TAGS.getOrDefault(tag, Set.of())) {
            if (v.startsWith("#")) {
                expand(v.substring(1), out, seen);
            } else {
                out.add(v);
            }
        }
    }

    static String table(Source s) {
        String path = s.id().substring(s.id().indexOf(':') + 1);
        return switch (s.kind()) {
            case BLOCK -> "cosmicbreach:blocks/" + path;
            case MOB -> "cosmicbreach:entities/" + path;
            case VAULT -> s.id();
        };
    }

    /** What a loot table can give, with {@code have} deciding tool conditions. */
    static Set<String> loot(String tableId, Set<String> have, int depth) {
        Set<String> out = new LinkedHashSet<>();
        if (depth > 8) {
            return out;
        }
        String ns = tableId.substring(0, tableId.indexOf(':'));
        String path = tableId.substring(ns.length() + 1);
        for (String base : List.of("main/resources/data/", "generated/resources/data/")) {
            Path f = root.resolve(base + ns + "/loot_table/" + path + ".json");
            if (Files.exists(f)) {
                JsonObject t = json(f);
                if (t.has("pools")) {
                    for (JsonElement pool : t.getAsJsonArray("pools")) {
                        if (allowed(pool.getAsJsonObject(), have)) {
                            entries(pool.getAsJsonObject().getAsJsonArray("entries"), have, depth, out);
                        }
                    }
                }
            }
        }
        return out;
    }

    private static void entries(JsonArray entries, Set<String> have, int depth, Set<String> out) {
        for (JsonElement e : entries) {
            JsonObject o = e.getAsJsonObject();
            if (!allowed(o, have)) {
                continue;
            }
            String type = o.get("type").getAsString();
            switch (type) {
                case "minecraft:item" -> out.add(o.get("name").getAsString());
                case "minecraft:tag" -> out.addAll(members(o.get("name").getAsString()));
                case "minecraft:loot_table" -> out.addAll(loot(o.get("value").getAsString(), have, depth + 1));
                case "minecraft:alternatives", "minecraft:group", "minecraft:sequence" -> entries(o.getAsJsonArray("children"), have, depth, out);
                default -> {
                }
            }
        }
    }

    /** Conditions: a Silk Touch match never passes; a match on an item passes once that item is in hand; the rest pass. */
    private static boolean allowed(JsonObject o, Set<String> have) {
        if (!o.has("conditions")) {
            return true;
        }
        for (JsonElement c : o.getAsJsonArray("conditions")) {
            if (!condition(c.getAsJsonObject(), have)) {
                return false;
            }
        }
        return true;
    }

    private static boolean condition(JsonObject c, Set<String> have) {
        String type = c.get("condition").getAsString();
        if (type.equals("minecraft:any_of")) {
            for (JsonElement t : c.getAsJsonArray("terms")) {
                if (condition(t.getAsJsonObject(), have)) {
                    return true;
                }
            }
            return false;
        }
        if (type.equals("minecraft:match_tool")) {
            JsonObject p = c.getAsJsonObject("predicate");
            if (p.has("predicates")) {
                return false;
            }
            if (p.has("items")) {
                JsonElement items = p.get("items");
                List<String> ids = new ArrayList<>();
                if (items.isJsonArray()) {
                    items.getAsJsonArray().forEach(i -> ids.add(i.getAsString()));
                } else {
                    ids.add(items.getAsString());
                }
                for (String id : ids) {
                    if (id.startsWith("#") ? members(id.substring(1)).stream().anyMatch(have::contains) : have.contains(id)) {
                        return true;
                    }
                }
                return false;
            }
        }
        return true;
    }

    private static Recipe recipe(String id, JsonObject j) {
        String type = j.get("type").getAsString();
        JsonObject result = j.getAsJsonObject("result");
        if (result == null || !result.has("id")) {
            return null;
        }
        String out = result.get("id").getAsString();
        List<Need> needs = new ArrayList<>();
        Station station;
        int tier = 1;
        switch (type) {
            case "minecraft:crafting_shaped" -> {
                JsonObject key = j.getAsJsonObject("key");
                Set<Character> used = new TreeSet<>();
                int rows = 0;
                int width = 0;
                for (JsonElement row : j.getAsJsonArray("pattern")) {
                    String r = row.getAsString();
                    rows++;
                    width = Math.max(width, r.length());
                    for (char ch : r.toCharArray()) {
                        if (ch != ' ') {
                            used.add(ch);
                        }
                    }
                }
                for (char ch : used) {
                    needs.add(need(key.get(String.valueOf(ch))));
                }
                station = rows <= 2 && width <= 2 ? Station.GRID : Station.TABLE;
            }
            case "minecraft:crafting_shapeless" -> {
                JsonArray ingredients = j.getAsJsonArray("ingredients");
                ingredients.forEach(i -> needs.add(need(i)));
                station = ingredients.size() <= 4 ? Station.GRID : Station.TABLE;
            }
            case "minecraft:smelting", "minecraft:smoking" -> {
                needs.add(need(j.get("ingredient")));
                station = Station.FURNACE;
            }
            case "cosmicbreach:astral_forge" -> {
                j.getAsJsonArray("ingredients").forEach(i -> needs.add(need(i)));
                station = Station.FORGE;
                tier = j.has("tier") ? j.get("tier").getAsInt() : 1;
            }
            default -> {
                // stonecutting and blasting need iron for their stations; a campfire needs coal
                if (j.has("ingredient")) {
                    needs.add(need(j.get("ingredient")));
                }
                station = Station.NONE;
            }
        }
        return new Recipe(id, station, tier, needs, out);
    }

    private static Need need(JsonElement e) {
        Set<String> items = new LinkedHashSet<>();
        Set<String> tags = new LinkedHashSet<>();
        List<JsonElement> options = new ArrayList<>();
        if (e.isJsonArray()) {
            e.getAsJsonArray().forEach(options::add);
        } else {
            options.add(e);
        }
        for (JsonElement o : options) {
            JsonObject obj = o.getAsJsonObject();
            if (obj.has("item")) {
                items.add(obj.get("item").getAsString());
            }
            if (obj.has("tag")) {
                tags.add(obj.get("tag").getAsString());
            }
        }
        return new Need(items, tags);
    }

    /**
     * Vanilla's recipes the way in leans on (1.21.1's own JSON, checked against the game's jar), plus stripping a log with
     * an axe, which is an in-world action rather than a recipe.
     */
    static List<Recipe> vanillaRecipes() {
        Need planks = new Need(Set.of(), Set.of("minecraft:planks"));
        Need stick = new Need(Set.of("minecraft:stick"), Set.of());
        Need axe = new Need(Set.of("minecraft:wooden_axe"), Set.of());
        return List.of(
                new Recipe("minecraft:crafting_table", Station.GRID, 1, List.of(planks), "minecraft:crafting_table"),
                new Recipe("minecraft:stick", Station.GRID, 1, List.of(planks), "minecraft:stick"),
                new Recipe("minecraft:wooden_axe", Station.TABLE, 1, List.of(planks, stick), "minecraft:wooden_axe"),
                new Recipe("strip:driftwood_log", Station.GRID, 1, List.of(new Need(Set.of("cosmicbreach:driftwood_log"), Set.of()), axe),
                        "cosmicbreach:stripped_driftwood_log"),
                new Recipe("minecraft:wooden_pickaxe", Station.TABLE, 1, List.of(planks, stick), "minecraft:wooden_pickaxe"),
                new Recipe("minecraft:stone_pickaxe", Station.TABLE, 1, List.of(new Need(Set.of(), Set.of("minecraft:stone_tool_materials")), stick),
                        "minecraft:stone_pickaxe"),
                new Recipe("minecraft:furnace", Station.TABLE, 1, List.of(new Need(Set.of(), Set.of("minecraft:stone_crafting_materials"))),
                        "minecraft:furnace"));
    }

    private static Set<String> ids(RewardTable table) {
        Set<String> out = new LinkedHashSet<>();
        table.lines().forEach(l -> out.add(l.item().toString()));
        return out;
    }

    private static List<String> values(JsonObject tag) {
        List<String> out = new ArrayList<>();
        for (JsonElement v : tag.getAsJsonArray("values")) {
            out.add(v.isJsonObject() ? v.getAsJsonObject().get("id").getAsString() : v.getAsString());
        }
        return out;
    }

    private static JsonObject json(Path f) {
        try {
            return JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path projectFile(String path) {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            if (Files.exists(dir.resolve(path).resolve("main/resources/data/cosmicbreach"))) {
                return dir.resolve(path);
            }
        }
        throw new AssertionError("no " + path + " above " + Path.of("").toAbsolutePath());
    }
}
