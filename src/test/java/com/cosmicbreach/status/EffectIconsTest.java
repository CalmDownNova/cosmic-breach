package com.cosmicbreach.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every status effect the mod registers has its 18 by 18 icon and a name (playtest 3: three showed the missing texture in the
 * inventory and the HUD). The effects are read from the sources, so a new one without an icon fails here.
 */
class EffectIconsTest {
    private static final Pattern REGISTER = Pattern.compile("EFFECTS[.]register[(]\"([a-z0-9_]+)\"");

    /** The repo's root: the tests may run from a run folder inside it. */
    private static Path root() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null && !Files.isDirectory(p.resolve("src/main/java"))) {
            p = p.getParent();
        }
        if (p == null) {
            throw new IllegalStateException("no src/main/java above " + Path.of("").toAbsolutePath());
        }
        return p;
    }

    private static TreeSet<String> effects() throws IOException {
        TreeSet<String> ids = new TreeSet<>();
        try (Stream<Path> files = Files.walk(root().resolve("src/main/java"))) {
            for (Path p : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".java"))::iterator) {
                Matcher m = REGISTER.matcher(Files.readString(p));
                while (m.find()) {
                    ids.add(m.group(1));
                }
            }
        }
        return ids;
    }

    @Test
    void everyEffectHasItsIconAndItsName() throws IOException {
        TreeSet<String> ids = effects();
        assertTrue(ids.size() >= 9, "found the effects: " + ids);
        StringBuilder langs = new StringBuilder();
        try (Stream<Path> files = Files.walk(root().resolve("src/main/resources/assets"))) {
            for (Path p : (Iterable<Path>) files.filter(f -> f.toString().endsWith("en_us.json"))::iterator) {
                langs.append(Files.readString(p));
            }
        }
        List<String> missing = new ArrayList<>();
        for (String id : ids) {
            Path icon = root().resolve("src/main/resources/assets/cosmicbreach/textures/mob_effect/" + id + ".png");
            if (!Files.isRegularFile(icon)) {
                missing.add(id + " (icon)");
            } else {
                byte[] png = Files.readAllBytes(icon);
                int w = ((png[16] & 0xFF) << 24) | ((png[17] & 0xFF) << 16) | ((png[18] & 0xFF) << 8) | (png[19] & 0xFF);
                int h = ((png[20] & 0xFF) << 24) | ((png[21] & 0xFF) << 16) | ((png[22] & 0xFF) << 8) | (png[23] & 0xFF);
                if (w != 18 || h != 18) {
                    missing.add(id + " (icon is " + w + "x" + h + ")");
                }
            }
            if (!langs.toString().contains("\"effect.cosmicbreach." + id + "\"")) {
                missing.add(id + " (name)");
            }
        }
        assertEquals(List.of(), missing);
    }
}
