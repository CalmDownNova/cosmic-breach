package com.cosmicbreach;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class ModItemIdsTest {
    @Test
    void findsTheModsItems() throws Exception {
        var paths = ModItemIds.paths();
        assertTrue(paths.contains("meridian") && paths.contains("satchel") && paths.contains("starsteel_ingot") && paths.contains("astral_forge"),
                "found " + paths.size());
        Files.writeString(Paths.get(System.getProperty("java.io.tmpdir"), "cb-item-ids.txt"), String.join("\n", paths));
    }
}
