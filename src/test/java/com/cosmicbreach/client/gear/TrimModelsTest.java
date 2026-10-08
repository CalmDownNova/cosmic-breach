package com.cosmicbreach.client.gear;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every tier trim texture is used only as a layer above the base, so {@link TrimLayers} (and the generator mixin that
 * applies it) covers every weapon and set piece: a new trim with another name or as layer0 would bring the edge
 * z-fighting back. Two more things the fix rests on are guarded too: the mixin acts in the item model generator, so a
 * trim built by another loader would keep its edge walls; and a faces only trim leaves no wall under a trim texel that
 * has no base texel beneath it.
 */
class TrimModelsTest {
    private static final List<String> MODEL_DIRS = List.of("src/main/resources/assets/cosmicbreach/models/item",
            "src/generated/resources/assets/cosmicbreach/models/item");
    private static final List<String> MODEL_ROOTS = List.of("src/main/resources/assets/cosmicbreach/models",
            "src/generated/resources/assets/cosmicbreach/models");
    private static final List<String> TEXTURE_ROOTS = List.of("src/main/resources/assets/cosmicbreach/textures",
            "src/generated/resources/assets/cosmicbreach/textures");
    private static final String TEXTURES = "src/main/resources/assets/cosmicbreach/textures/item";
    /** The only custom loader a trimmed model may sit under: its children are plain generated models. */
    private static final String SEPARATE_TRANSFORMS = "neoforge:separate_transforms";

    private static Path projectFile(String path) {
        Path found = projectFileOrNull(path);
        if (found == null) {
            throw new AssertionError("no " + path + " above " + Path.of("").toAbsolutePath());
        }
        return found;
    }

    @Test
    void everyTrimIsALayerThatKeepsItsFacesOnly() throws IOException {
        Set<String> trimTextures = new TreeSet<>();
        try (Stream<Path> files = Files.list(projectFile(TEXTURES))) {
            files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith("_trim.png"))
                    .forEach(n -> trimTextures.add("cosmicbreach:item/" + n.substring(0, n.length() - 4)));
        }
        Set<String> used = new TreeSet<>();
        for (String dir : MODEL_DIRS) {
            try (Stream<Path> files = Files.list(projectFile(dir))) {
                for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                    JsonObject model = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
                    if (!model.has("textures")) {
                        continue;
                    }
                    JsonObject tex = model.getAsJsonObject("textures");
                    for (String key : tex.keySet()) {
                        String value = tex.get(key).getAsString();
                        if (!value.endsWith("_trim")) {
                            continue;
                        }
                        assertTrue(key.matches("layer[1-4]"), f.getFileName() + ": a trim sits above the base layer, not as " + key);
                        String[] id = value.split(":", 2);
                        assertTrue(TrimLayers.facesOnly(Integer.parseInt(key.substring(5)), id[0], id[1]), f.getFileName() + " " + value);
                        used.add(value);
                    }
                }
            }
        }
        assertEquals(trimTextures, used, "every trim texture is used as a trim layer, and every trim layer has its texture");
        assertTrue(used.size() >= 16, "three weapons' and three sets' trims, found " + used.size());
    }

    /**
     * The mixin wraps {@code ItemModelGenerator.generateBlockModel}, which builds every plain generated model and the
     * children of {@code neoforge:separate_transforms}. A model under another loader (for example
     * {@code neoforge:item_layers}, which builds its layers through {@code UnbakedGeometryHelper} and calls
     * {@code processFrames} itself) would keep the trim's edge walls and bring the edge fight back, silently.
     */
    @Test
    void noTrimIsBuiltByALoaderTheGeneratorMixinCannotReach() throws IOException {
        Map<String, JsonObject> models = models();
        List<String> bad = new ArrayList<>();
        int trimmed = 0;
        for (String id : models.keySet()) {
            if (!id.startsWith("item/")) {
                continue;
            }
            Resolved resolved = resolve(models, id);
            if (trimLayers(resolved.textures()).isEmpty()) {
                continue;
            }
            trimmed++;
            if (resolved.loader() != null && !resolved.loader().equals(SEPARATE_TRANSFORMS)) {
                bad.add(id + " (" + resolved.loader() + ")");
            }
        }
        assertTrue(bad.isEmpty(), "a tier trim under a loader the mixin cannot reach: " + bad);
        assertTrue(trimmed >= 28, "every trimmed model was looked at, found " + trimmed);
    }

    /**
     * A faces only trim has no edge walls of its own: the base layer's walls stand under it. That holds only while every
     * opaque trim texel has an opaque base texel at the same place (and the two sprites are the same size); a trim texel
     * with nothing under it would be paper thin from the side.
     */
    @Test
    void everyTrimTexelSitsOnABaseTexel() throws IOException {
        Map<String, JsonObject> models = models();
        List<String> bad = new ArrayList<>();
        int checked = 0;
        for (String id : models.keySet()) {
            if (!id.startsWith("item/")) {
                continue;
            }
            Map<String, String> textures = resolve(models, id).textures();
            String base = textures.get("layer0");
            if (base == null) {
                continue; // an abstract parent: its children bring the base layer
            }
            for (String key : trimLayers(textures)) {
                BufferedImage under = texture(base);
                BufferedImage trim = texture(textures.get(key));
                if (under.getWidth() != trim.getWidth() || under.getHeight() != trim.getHeight()) {
                    bad.add(id + ": the trim is " + trim.getWidth() + "x" + trim.getHeight() + ", the base " + under.getWidth() + "x" + under.getHeight());
                    continue;
                }
                int outside = 0;
                for (int y = 0; y < trim.getHeight(); y++) {
                    for (int x = 0; x < trim.getWidth(); x++) {
                        if (opaque(trim, x, y) && !opaque(under, x, y)) {
                            outside++;
                        }
                    }
                }
                if (outside > 0) {
                    bad.add(id + ": " + outside + " trim texels with no base texel under them (" + base + ")");
                }
                checked++;
            }
        }
        assertTrue(bad.isEmpty(), "trim texels the base layer's walls do not stand under: " + bad);
        assertTrue(checked >= 28, "every trimmed model with a base layer was looked at, found " + checked);
    }

    /** Every model json under both model roots, by id path such as {@code item/meridian}. */
    private static Map<String, JsonObject> models() throws IOException {
        Map<String, JsonObject> models = new TreeMap<>();
        for (String root : MODEL_ROOTS) {
            Path dir = projectFile(root);
            try (Stream<Path> files = Files.walk(dir)) {
                for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                    String id = dir.relativize(f).toString().replace('\\', '/');
                    models.put(id.substring(0, id.length() - ".json".length()),
                            JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject());
                }
            }
        }
        return models;
    }

    /** A model's textures as the game resolves them (a child's over its parents') and the loader it ends up with (its own, else the nearest parent's). */
    private record Resolved(Map<String, String> textures, String loader) {
    }

    private static Resolved resolve(Map<String, JsonObject> models, String id) {
        List<JsonObject> chain = new ArrayList<>();
        String current = id;
        while (current != null && models.containsKey(current) && chain.size() < 50) {
            JsonObject model = models.get(current);
            chain.add(model);
            String parent = model.has("parent") ? model.get("parent").getAsString() : null;
            current = parent != null && parent.startsWith("cosmicbreach:") ? parent.substring("cosmicbreach:".length()) : null;
        }
        Map<String, String> textures = new TreeMap<>();
        for (int i = chain.size() - 1; i >= 0; i--) { // the root first, so a child's entry wins
            if (chain.get(i).has("textures")) {
                JsonObject own = chain.get(i).getAsJsonObject("textures");
                for (String key : own.keySet()) {
                    textures.put(key, own.get(key).getAsString());
                }
            }
        }
        String loader = null;
        for (JsonObject model : chain) {
            if (model.has("loader")) {
                loader = model.get("loader").getAsString();
                break;
            }
        }
        return new Resolved(textures, loader);
    }

    /** The layers (layer1 to layer4) of these textures that are tier trims. */
    private static List<String> trimLayers(Map<String, String> textures) {
        List<String> layers = new ArrayList<>();
        for (Map.Entry<String, String> e : textures.entrySet()) {
            if (e.getKey().matches("layer[1-4]") && e.getValue().endsWith("_trim")) {
                layers.add(e.getKey());
            }
        }
        return layers;
    }

    private static BufferedImage texture(String location) throws IOException {
        String[] id = location.split(":", 2);
        assertEquals("cosmicbreach", id[0], "a texture outside the mod cannot be checked here: " + location);
        for (String root : TEXTURE_ROOTS) {
            Path file = projectFileOrNull(root + "/" + id[1] + ".png");
            if (file != null) {
                BufferedImage image = ImageIO.read(file.toFile());
                assertTrue(image != null, "not a readable image: " + file);
                return image;
            }
        }
        throw new AssertionError("no texture file for " + location);
    }

    private static Path projectFileOrNull(String path) {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            if (Files.exists(dir.resolve(path))) {
                return dir.resolve(path);
            }
        }
        return null;
    }

    /** Vanilla builds an edge wall wherever a texel's alpha is above 0. */
    private static boolean opaque(BufferedImage image, int x, int y) {
        return (image.getRGB(x, y) >>> 24) != 0;
    }
}
