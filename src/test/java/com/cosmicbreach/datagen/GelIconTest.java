package com.cosmicbreach.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** The gel icons are flat 16x16 generated sprites with a transparent background, not a filled square. */
class GelIconTest {
    @Test
    void gelIconsAreFlatSpritesWithTransparency() throws Exception {
        for (String id : new String[] {"drift_gel", "candied_gel"}) {
            try (InputStream in = getClass().getResourceAsStream("/assets/cosmicbreach/models/item/" + id + ".json")) {
                assertNotNull(in, "item model " + id);
                JsonObject model = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                assertEquals("minecraft:item/generated", model.get("parent").getAsString(), id + " model parent");
                assertTrue(!model.has("elements"), id + " must not carry its own geometry");
            }
            try (InputStream in = getClass().getResourceAsStream("/assets/cosmicbreach/textures/item/" + id + ".png")) {
                assertNotNull(in, "texture " + id);
                BufferedImage img = ImageIO.read(in);
                assertEquals(16, img.getWidth());
                assertEquals(16, img.getHeight());
                int clear = 0;
                int corners = 0;
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        boolean transparent = (img.getRGB(x, y) >>> 24) == 0;
                        if (transparent) {
                            clear++;
                        }
                        if (transparent && (x == 0 || x == 15) && (y == 0 || y == 15)) {
                            corners++;
                        }
                    }
                }
                assertEquals(4, corners, id + " corners must be transparent");
                assertTrue(clear >= 60, id + " needs a transparent surround, had " + clear + " clear pixels");
            }
        }
    }
}
