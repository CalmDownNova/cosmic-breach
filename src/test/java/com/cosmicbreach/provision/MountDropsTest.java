package com.cosmicbreach.provision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import org.junit.jupiter.api.Test;

/**
 * The rideable creatures' meat and hide (Aetheria 1.1 plan, "Decisions during planning": tamed mounts never drop meat or
 * hide, only their saddle and gear). A wild Lumen Stag or Drift Manta drops food and Starhide; a tamed one drops none of
 * it, because every pool of its loot table is skipped for an animal whose saved data says {@code Tame:1b} (a horse's own
 * flag, written by {@code AbstractHorse}). Its saddle, barding and tack drop from its equipment, not from the table.
 */
class MountDropsTest {
    private static final String DIR = "main/resources/data/cosmicbreach/loot_table/entities/";

    private static JsonObject table(String mount) {
        Path file = projectFile("src").resolve(DIR + mount + ".json");
        assertTrue(Files.exists(file), "a wild " + mount + " drops food and Starhide: no table at " + file);
        try {
            return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Set<String> items(JsonObject table) {
        Set<String> out = new HashSet<>();
        for (JsonElement pool : table.getAsJsonArray("pools")) {
            for (JsonElement entry : pool.getAsJsonObject().getAsJsonArray("entries")) {
                out.add(entry.getAsJsonObject().get("name").getAsString());
            }
        }
        return out;
    }

    @Test
    void aWildStagAndAWildMantaDropFoodAndStarhide() {
        assertEquals(Set.of("cosmicbreach:lumen_venison", "cosmicbreach:starhide"), items(table("lumen_stag")));
        assertEquals(Set.of("cosmicbreach:manta_fillet", "cosmicbreach:starhide"), items(table("drift_manta")));
    }

    @Test
    void everyPoolIsSkippedForATamedMount() throws Exception {
        CompoundTag tame = TagParser.parseTag("{Tame:1b}");
        assertTrue(tame.getBoolean("Tame") && tame.size() == 1, "the flag a tamed horse saves");
        for (String mount : List.of("lumen_stag", "drift_manta")) {
            JsonArray pools = table(mount).getAsJsonArray("pools");
            assertFalse(pools.isEmpty(), mount + " has pools");
            for (JsonElement p : pools) {
                JsonObject pool = p.getAsJsonObject();
                boolean skipped = false;
                if (pool.has("conditions")) {
                    for (JsonElement c : pool.getAsJsonArray("conditions")) {
                        skipped |= isNotTame(c.getAsJsonObject(), tame);
                    }
                }
                assertTrue(skipped, mount + ": a pool still drops for a tamed mount: " + pool);
            }
        }
    }

    /** {@code inverted(entity_properties(this, nbt Tame:1b))}. */
    private static boolean isNotTame(JsonObject c, CompoundTag tame) {
        if (!"minecraft:inverted".equals(c.get("condition").getAsString()) || !c.has("term")) {
            return false;
        }
        JsonObject term = c.getAsJsonObject("term");
        if (!"minecraft:entity_properties".equals(term.get("condition").getAsString()) || !"this".equals(term.get("entity").getAsString())) {
            return false;
        }
        JsonObject predicate = term.getAsJsonObject("predicate");
        try {
            return predicate.size() == 1 && predicate.has("nbt") && TagParser.parseTag(predicate.get("nbt").getAsString()).equals(tame);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            return false;
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
