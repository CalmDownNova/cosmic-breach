package com.cosmicbreach.combat.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Test fixture: the shipped Meridian data, parsed the way the loader does it, in a fresh CombatData. */
public final class CombatDataTestAccess {
    public static final List<String> MERIDIAN_MOVES = List.of("l1", "l2", "l3", "line", "falling_star", "pass", "zenith");

    private CombatDataTestAccess() {
    }

    public static JsonElement resource(String path) {
        try (InputStream in = CombatDataTestAccess.class.getResourceAsStream("/" + path)) {
            assertNotNull(in, "missing resource " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("cosmicbreach", path);
    }

    public static CombatData meridian() {
        Map<ResourceLocation, JsonElement> moveFiles = new LinkedHashMap<>();
        for (String name : MERIDIAN_MOVES) {
            moveFiles.put(id("meridian/" + name), resource("data/cosmicbreach/combat/moves/meridian/" + name + ".json"));
        }
        CombatData data = new CombatData();
        data.replace(CombatData.parseAll(moveFiles, MoveDef.CODEC, "move", LogUtils.getLogger()),
                CombatData.parseAll(Map.of(id("meridian"), resource("data/cosmicbreach/combat/weapons/meridian.json")),
                        WeaponDef.CODEC, "weapon", LogUtils.getLogger()));
        return data;
    }
}
