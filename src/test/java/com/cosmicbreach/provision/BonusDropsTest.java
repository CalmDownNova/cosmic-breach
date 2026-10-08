package com.cosmicbreach.provision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Halo Moss gives a Halo Berry and Neon Lichen gives an Umbral Cap (1.1: the food of levels 1, 3 and 4 in the chunks no world
 * feature has reached) when something cuts them, but never together with the block itself: shears (and Silk Touch, for the
 * moss) take the block whole and the bonus pool is skipped, as vanilla's leaves skip their sticks and apples. Otherwise a
 * Halo Moss or a Neon Lichen can be placed and cut with shears forever, a quarter of a food each time, which sidesteps the
 * food that grows back (A1 Quality Review, Minor 3). By hand, a pick or a sword the bonus is still there.
 *
 * <p>What this does not close: a Halo Moss strand cut with a Fortune III tool still returns itself on every cut (the flat
 * chance reaches 1.0) and rolls the berry, so with that tool a placed strand can still be farmed for berries, slowly. At
 * Fortune 0 to II the strand comes back 33, 55 or 77 times in 100 and the loop runs dry; the lichen loop is closed, since the
 * lichen comes back only with shears (A2 spec review, Minor 3). This test asks what a cut can give, so it does not model that
 * edge; it is accepted.
 *
 * <p>The tables are read as shipped (the generated data) and every pool's conditions are evaluated for one tool in hand.
 * Chance conditions pass: this asks what a cut can give, not how often.
 */
class BonusDropsTest {
    /** What is in the hand that cuts the block. */
    private record Tool(String name, boolean shears, boolean silkTouch) {}

    private static final Tool HAND = new Tool("bare hand", false, false);
    private static final Tool PICKAXE = new Tool("a pickaxe", false, false);
    private static final Tool SHEARS = new Tool("shears", true, false);
    private static final Tool SILK_TOUCH = new Tool("a Silk Touch pickaxe", false, true);

    private static final String BERRIES = "cosmicbreach:halo_berries";
    private static final String CAP = "cosmicbreach:umbral_cap";

    @Test
    void shearsAndSilkTouchTakeHaloMossWholeAndGiveNoBerry() {
        for (String table : new String[] {"halo_moss", "halo_moss_plant"}) {
            for (Tool tool : new Tool[] {HAND, PICKAXE}) {
                assertEquals(Set.of("cosmicbreach:halo_moss", BERRIES), gives(table, tool), table + " cut with " + tool.name());
            }
            for (Tool tool : new Tool[] {SHEARS, SILK_TOUCH}) {
                assertEquals(Set.of("cosmicbreach:halo_moss"), gives(table, tool), table + " cut with " + tool.name() + " is taken whole, no berry");
            }
        }
    }

    @Test
    void shearsTakeNeonLichenWholeAndGiveNoCap() {
        for (String lichen : new String[] {"magenta_neon_lichen", "teal_neon_lichen"}) {
            for (Tool tool : new Tool[] {HAND, PICKAXE, SILK_TOUCH}) {
                assertEquals(Set.of(CAP), gives(lichen, tool), lichen + " cut with " + tool.name() + " turns up a cap and nothing else");
            }
            assertEquals(Set.of("cosmicbreach:" + lichen), gives(lichen, SHEARS), lichen + " cut with shears is taken whole, no cap");
        }
    }

    // ------------------------------------------------------------------ what a table can give for a tool

    private static Set<String> gives(String block, Tool tool) {
        Set<String> out = new LinkedHashSet<>();
        JsonObject table = json("generated/resources/data/cosmicbreach/loot_table/blocks/" + block + ".json");
        for (JsonElement pool : table.getAsJsonArray("pools")) {
            JsonObject p = pool.getAsJsonObject();
            if (passes(p, tool)) {
                entries(p.getAsJsonArray("entries"), tool, out);
            }
        }
        assertTrue(!out.isEmpty(), block + " gives nothing for " + tool.name());
        return out;
    }

    private static void entries(JsonArray entries, Tool tool, Set<String> out) {
        for (JsonElement e : entries) {
            JsonObject entry = e.getAsJsonObject();
            if (!passes(entry, tool)) {
                continue;
            }
            switch (entry.get("type").getAsString()) {
                case "minecraft:item" -> out.add(entry.get("name").getAsString());
                // the first child whose conditions pass is the one that drops
                case "minecraft:alternatives" -> {
                    for (JsonElement child : entry.getAsJsonArray("children")) {
                        if (passes(child.getAsJsonObject(), tool)) {
                            entries(single(child), tool, out);
                            break;
                        }
                    }
                }
                default -> fail("an entry type this test does not read: " + entry);
            }
        }
    }

    private static JsonArray single(JsonElement e) {
        JsonArray a = new JsonArray();
        a.add(e);
        return a;
    }

    private static boolean passes(JsonObject holder, Tool tool) {
        if (!holder.has("conditions")) {
            return true;
        }
        for (JsonElement c : holder.getAsJsonArray("conditions")) {
            if (!condition(c.getAsJsonObject(), tool)) {
                return false;
            }
        }
        return true;
    }

    private static boolean condition(JsonObject c, Tool tool) {
        String type = c.get("condition").getAsString();
        switch (type) {
            case "minecraft:any_of" -> {
                for (JsonElement t : c.getAsJsonArray("terms")) {
                    if (condition(t.getAsJsonObject(), tool)) {
                        return true;
                    }
                }
                return false;
            }
            case "minecraft:all_of" -> {
                for (JsonElement t : c.getAsJsonArray("terms")) {
                    if (!condition(t.getAsJsonObject(), tool)) {
                        return false;
                    }
                }
                return true;
            }
            case "minecraft:inverted" -> {
                return !condition(c.getAsJsonObject("term"), tool);
            }
            case "minecraft:match_tool" -> {
                JsonObject predicate = c.getAsJsonObject("predicate");
                if (predicate.has("items") && predicate.size() == 1) {
                    return predicate.get("items").getAsString().equals("minecraft:shears") ? tool.shears() : fail("a tool item this test does not read: " + c);
                }
                if (predicate.has("predicates") && predicate.size() == 1 && predicate.getAsJsonObject("predicates").toString().contains("minecraft:silk_touch")) {
                    return tool.silkTouch();
                }
                return fail("a tool predicate this test does not read: " + c);
            }
            // chances, explosions, fortune tables and block states do not depend on the tool
            case "minecraft:random_chance", "minecraft:survives_explosion", "minecraft:table_bonus", "minecraft:block_state_property" -> {
                return true;
            }
            default -> {
                return fail("a loot condition this test does not read: " + c);
            }
        }
    }

    private static JsonObject json(String file) {
        Path f = srcRoot().resolve(file);
        assertTrue(Files.exists(f), "no file " + f);
        try {
            return JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path srcRoot() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            if (Files.exists(dir.resolve("src").resolve("main/resources/data/cosmicbreach"))) {
                return dir.resolve("src");
            }
        }
        throw new AssertionError("no src above " + Path.of("").toAbsolutePath());
    }
}
