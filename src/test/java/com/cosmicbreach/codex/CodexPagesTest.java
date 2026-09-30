package com.cosmicbreach.codex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The Starfall Codex's pages, read as files (F1): plain ASCII with no dashes, frontmatter a navigation title and
 * position, parents and links that exist, every unlock and Gate condition a real chapter, a guardian's page sealed
 * both in the navigation and on the page, and the chapters the plan asks for all present.
 */
class CodexPagesTest {
    private static final String DIR = "src/main/resources/assets/cosmicbreach/guides/cosmicbreach/codex";
    private static final Map<String, String> PAGES = new HashMap<>();
    private static final Pattern GATE = Pattern.compile("<Gate when=\"([^\"]*)\"");
    private static final Pattern LINK = Pattern.compile("\\]\\(([a-z_]+)\\.md\\)");
    private static final Pattern KEY = Pattern.compile("<KeyBind id=\"([^\"]+)\"");
    private static final Set<String> KEYS = Set.of("key.attack", "key.use", "key.cosmicbreach.dash", "key.cosmicbreach.parry",
            "key.cosmicbreach.set_ability", "key.cosmicbreach.attunement", "key.cosmicbreach.familiar", "key.curios.open.desc");

    @BeforeAll
    static void read() throws IOException {
        Path dir = projectFile(DIR);
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".md")).toList()) {
                String name = f.getFileName().toString();
                PAGES.put(name.substring(0, name.length() - 3), Files.readString(f, StandardCharsets.UTF_8));
            }
        }
    }

    private static Path projectFile(String path) {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            if (Files.exists(dir.resolve(path))) {
                return dir.resolve(path);
            }
        }
        throw new AssertionError("no " + path + " above " + Path.of("").toAbsolutePath());
    }

    private static String front(String text, String key) {
        String fm = text.split("---\n", 3)[1];
        for (String line : fm.split("\n")) {
            String t = line.trim();
            if (t.startsWith(key + ":")) {
                return t.substring(key.length() + 1).trim();
            }
        }
        return null;
    }

    @Test
    void theChaptersThePlanAsksForAreAllHere() {
        for (String page : List.of("index", "way_up", "starfall", "build_a_ring", "fall_up", "landing", "reach", "shardlings",
                "reliquary", "lens_array", "guardians", "prism_colossus", "drift", "gyre_knight", "observatory",
                "thalassine_leviathan", "deep", "stalker", "crypt", "traps", "choir_floor", "unsung", "breach_sanctum", "heliarch",
                "combat", "strikes", "dash_parry", "resonance", "telegraphs", "attunement", "attributes", "forge", "reforging",
                "arms", "meridian", "comet_maul", "binary_edges", "astrolabe", "relics", "armor", "vanguard", "driftweave",
                "regalia", "accessories", "companions", "lumen_stag", "drift_manta", "familiars", "weather")) {
            assertTrue(PAGES.containsKey(page), "missing page " + page);
        }
    }

    @Test
    void plainAsciiWithoutDashes() {
        PAGES.forEach((name, text) -> {
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                assertTrue(c == '\n' || c >= 32 && c < 127, name + ": character " + (int) c + " at " + i);
            }
            assertFalse(text.contains("\r"), name + " has CR line ends");
            String body = text.split("---\n", 3)[2];
            assertFalse(body.contains("--"), name + ": a double hyphen reads as a dash");
            assertFalse(body.matches("(?s).*\\w - \\w.*"), name + ": a spaced hyphen reads as a dash");
        });
    }

    @Test
    void frontmatterParentsLinksAndKeys() {
        PAGES.forEach((name, text) -> {
            assertTrue(text.startsWith("---\nnavigation:\n"), name + " starts with its navigation");
            assertTrue(front(text, "title") != null && front(text, "position") != null && front(text, "icon") != null, name);
            String parent = front(text, "parent");
            if (parent != null) {
                assertTrue(PAGES.containsKey(parent.replace(".md", "")), name + ": no parent " + parent);
                assertEquals(null, front(PAGES.get(parent.replace(".md", "")), "parent"), name + ": two levels only (GuideME shows two)");
            }
            Matcher link = LINK.matcher(text);
            while (link.find()) {
                assertTrue(PAGES.containsKey(link.group(1)), name + ": link to missing " + link.group(1));
            }
            Matcher key = KEY.matcher(text);
            while (key.find()) {
                assertTrue(KEYS.contains(key.group(1)), name + ": unknown key " + key.group(1));
            }
        });
    }

    @Test
    void everyConditionNamesAChapter() {
        PAGES.forEach((name, text) -> {
            String unlock = front(text, "unlock");
            assertTrue(CodexProgress.valid(unlock), name + ": unlock " + unlock);
            Matcher gate = GATE.matcher(text);
            while (gate.find()) {
                assertTrue(CodexProgress.valid(gate.group(1)), name + ": Gate " + gate.group(1));
            }
            assertEquals(count(text, "<Gate"), count(text, "</Gate>"), name + ": every Gate closed");
        });
    }

    @Test
    void aGuardiansPageIsSealedOnThePageToo() {
        List<String> sealed = new ArrayList<>();
        PAGES.forEach((name, text) -> {
            String title = front(text, "sealed_title");
            if (title == null) {
                return;
            }
            sealed.add(name);
            String unlock = front(text, "unlock");
            String body = text.split("---\n", 3)[2].trim();
            // nothing before the gates: the heading and the words wait for the meeting
            assertTrue(body.startsWith("<Gate when=\"!" + unlock + "\""), name + " opens with its sealed side");
            assertTrue(body.contains("<Gate when=\"" + unlock + "\" sealed=\"none\">"), name + " keeps its words behind " + unlock);
        });
        assertEquals(Set.of("prism_colossus", "thalassine_leviathan", "unsung", "heliarch"), Set.copyOf(sealed));
    }

    @Test
    void theLaterChaptersWaitForTheirLayer() {
        assertEquals("drift", front(PAGES.get("drift"), "unlock"));
        assertEquals("deep", front(PAGES.get("deep"), "unlock"));
        assertEquals("sanctum", front(PAGES.get("breach_sanctum"), "unlock"));
        assertEquals("arrived", front(PAGES.get("reach"), "unlock"));
        assertEquals(null, front(PAGES.get("way_up"), "unlock"));
        assertTrue(PAGES.get("lens_array").contains("<LensHint />"), "the Lens Array page offers its hint");
        assertTrue(PAGES.get("attunement").contains("<AllocationLink>"), "the Attunement page links to the allocation screen");
        for (String scene : List.of("build_a_ring", "lens_array", "choir_floor")) {
            assertTrue(PAGES.get(scene).contains("<GameScene"), scene + " teaches with a scene");
        }
    }

    private static int count(String text, String what) {
        int n = 0;
        for (int i = text.indexOf(what); i >= 0; i = text.indexOf(what, i + 1)) {
            n++;
        }
        return n;
    }
}
