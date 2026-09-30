package com.cosmicbreach.combat.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.cosmicbreach.combat.data.CombatDataTestAccess.id;
import static com.cosmicbreach.combat.data.CombatDataTestAccess.meridian;
import static com.cosmicbreach.combat.data.CombatDataTestAccess.resource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatDataTest {

    @Test
    void theShippedMeridianDataLoadsCompletely() {
        CombatData data = meridian();
        assertEquals(7, data.moves().size());
        WeaponDef weapon = data.weapon(id("meridian"));
        assertNotNull(weapon);
        for (ResourceLocation move : weapon.combo()) {
            assertNotNull(data.move(move), "combo move " + move);
        }
        assertNotNull(data.move(weapon.charged().orElseThrow()));
        assertNotNull(data.move(weapon.plunge().orElseThrow()));
        assertNotNull(data.move(weapon.dashAttack().orElseThrow()));
        assertNotNull(data.move(weapon.ability().orElseThrow().move()));
    }

    @Test
    void aBrokenFileIsSkippedAndTheRestLoad() {
        Map<ResourceLocation, JsonElement> files = new LinkedHashMap<>();
        files.put(id("good"), resource("data/cosmicbreach/combat/moves/meridian/l1.json"));
        files.put(id("no_timing"), JsonParser.parseString("{\"kind\": \"light\"}"));
        files.put(id("bad_kind"), JsonParser.parseString("{\"kind\": \"spin\"}"));
        Map<ResourceLocation, MoveDef> parsed = CombatData.parseAll(files, MoveDef.CODEC, "move", LogUtils.getLogger());
        assertEquals(1, parsed.size());
        assertTrue(parsed.containsKey(id("good")));
    }

    @Test
    void eachSideHasItsOwnCopy() {
        assertSame(CombatData.server(), CombatData.forSide(false));
        assertSame(CombatData.client(), CombatData.forSide(true));
        assertNotSame(CombatData.server(), CombatData.client());
    }

    @Test
    void storedMapsAreImmutableCopies() {
        CombatData data = meridian();
        Map<ResourceLocation, MoveDef> moves = new LinkedHashMap<>(data.moves());
        data.setMoves(moves);
        moves.clear();
        assertEquals(7, data.moves().size());
        assertThrows(UnsupportedOperationException.class, () -> data.moves().clear());
        assertNull(data.move(id("nope")));
    }
}
