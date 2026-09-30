package com.cosmicbreach.combat.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The data fields the Binary Edges added: a weapon's Weave ({@code dash_follow}), its off-hand blade, an
 * ability whose cooldown waits for the second press and a second press an effect opens; a move's i-frames,
 * which blades a slash trails, and hits spread over the active ticks.
 */
class EdgesCodecTest {
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("cosmicbreach", path);
    }

    private static WeaponDef weapon(String json) {
        return WeaponDef.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    private static <T> T roundTrip(com.mojang.serialization.Codec<T> codec, T value) {
        Tag nbt = codec.encodeStart(NbtOps.INSTANCE, value).getOrThrow();
        return codec.parse(NbtOps.INSTANCE, nbt).getOrThrow();
    }

    // ------------------------------------------------------------------ the weapon

    @Test
    void theNewWeaponFieldsParseAndSyncWhole() {
        WeaponDef w = weapon("""
                {"base_damage": 3.8, "combo": ["cosmicbreach:edges/l1"],
                 "dash_follow": {"move": "cosmicbreach:edges/l4", "window": 10},
                 "ability": {"move": "cosmicbreach:edges/tether", "cost": 25, "cooldown": 120, "cooldown_after_recast": true,
                             "recast": {"move": "cosmicbreach:edges/blink", "window": 80, "auto": false}},
                 "off_hand": {"item": "cosmicbreach:binary_edges_left",
                              "blade": {"guard": [10, 21], "tip": [27, 3], "half_width": 4.0},
                              "solo": {"cosmicbreach:edges_l1": "cosmicbreach:edges_l2"}}}
                """);
        assertEquals(new WeaponDef.DashFollow(id("edges/l4"), 10), w.dashFollow().orElseThrow());
        WeaponDef.Ability ability = w.ability().orElseThrow();
        assertTrue(ability.cooldownAfterRecast());
        assertEquals(new WeaponDef.Recast(id("edges/blink"), 80, false), ability.recast().orElseThrow());
        WeaponDef.OffHand off = w.offHand().orElseThrow();
        assertEquals(id("binary_edges_left"), off.item());
        assertEquals(List.of(27f, 3f), off.blade().tip());
        assertEquals(id("edges_l2"), off.soloAnimation(id("edges_l1")));
        assertEquals(id("edges_l3"), off.soloAnimation(id("edges_l3")), "an animation without a solo version stays");
        assertEquals(w, roundTrip(WeaponDef.CODEC, w));
    }

    @Test
    void leftOutTheyKeepTheOldBehaviour() {
        WeaponDef w = weapon("""
                {"base_damage": 5.0, "combo": ["cosmicbreach:meridian/l1"],
                 "ability": {"move": "cosmicbreach:meridian/zenith", "cost": 30, "cooldown": 100,
                             "recast": {"move": "cosmicbreach:meridian/zenith", "window": 20}}}
                """);
        assertTrue(w.dashFollow().isEmpty());
        assertTrue(w.offHand().isEmpty());
        assertFalse(w.ability().orElseThrow().cooldownAfterRecast(), "the cooldown starts at the cast");
        assertTrue(w.ability().orElseThrow().recast().orElseThrow().auto(), "the ability's first active tick opens it");
        assertEquals(w, roundTrip(WeaponDef.CODEC, w));
    }

    @Test
    void theWeavesWindowMustBePositive() {
        assertTrue(WeaponDef.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"base_damage": 3.8, "combo": ["cosmicbreach:edges/l1"],
                 "dash_follow": {"move": "cosmicbreach:edges/l4", "window": 0}}
                """)).isError());
    }

    // ------------------------------------------------------------------ the move

    private static final String BASE = """
            "kind": "light",
            "timing": {"startup": 3, "active": 3, "recovery": 8},
            "hit": {"mv": 0.5, "impact": 2.5, "hits": 4},
            "hitbox": {"type": "sphere", "radius": 3},
            "animation": "cosmicbreach:edges_l5"
            """;

    private static MoveDef move(String extra) {
        JsonElement json = JsonParser.parseString("{" + BASE + (extra.isEmpty() ? "" : "," + extra) + "}");
        return MoveDef.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    @Test
    void aMoveMayBeInvulnerableThroughAPhase() {
        MoveDef def = move("\"invulnerable\": \"startup\"");
        assertEquals(Optional.of(MoveTraits.Stage.STARTUP), def.traits().invulnerable());
        assertTrue(move("").traits().invulnerable().isEmpty());
        assertEquals(def, roundTrip(MoveDef.CODEC, def));
    }

    @Test
    void aSlashSaysWhichBladesTrail() {
        MoveDef both = move("\"slash\": {\"from\": 90, \"to\": -90, \"radius\": 2.5, \"color\": \"#50E6FF\", "
                + "\"blades\": \"both\", \"off_color\": \"#B070FF\"}");
        MoveDef.Slash slash = both.slash().orElseThrow();
        assertEquals(MoveDef.Slash.Blades.BOTH, slash.blades());
        assertTrue(slash.blades().main() && slash.blades().off());
        assertEquals(0x50E6FF, slash.color());
        assertEquals(0xB070FF, slash.offColorOrMain());
        assertEquals(both, roundTrip(MoveDef.CODEC, both));

        MoveDef.Slash plain = move("\"slash\": {\"from\": 90, \"to\": -90, \"radius\": 2.5, \"color\": \"#FFFFFF\"}").slash().orElseThrow();
        assertEquals(MoveDef.Slash.Blades.MAIN, plain.blades(), "the main blade by default");
        assertFalse(plain.blades().off());
        assertEquals(0xFFFFFF, plain.offColorOrMain(), "the off blade takes the main colour when it has none");

        MoveDef.Slash off = move("\"slash\": {\"from\": 90, \"to\": -90, \"radius\": 2.5, \"blades\": \"off\"}").slash().orElseThrow();
        assertFalse(off.blades().main());
        assertTrue(off.blades().off());
    }

    // ------------------------------------------------------------------ spread hits

    private static List<Integer> schedule(int hits, int active) {
        MoveDef.Hit hit = new MoveDef.Hit(0.5, 0, 0, 2.5, 2, 1, hits, 0);
        List<Integer> out = new ArrayList<>();
        for (int t = 0; t < active; t++) {
            out.add(hit.hitsAt(t, active));
        }
        return out;
    }

    @Test
    void hitsSpreadEvenlyFromTheFirstActiveTickToTheLast() {
        assertEquals(List.of(1, 1), schedule(2, 2), "Cross Cut: one blade a tick");
        assertEquals(List.of(1, 2, 1), schedule(4, 3), "Gyre: four hits in three ticks");
        assertEquals(List.of(1, 0, 1, 0, 1, 1, 0, 1, 0, 1), schedule(6, 10), "Binary Orbit: six in ten");
        assertEquals(List.of(3), schedule(3, 1), "more hits than ticks share them");
        for (int hits = 2; hits <= 8; hits++) {
            for (int active = 1; active <= 12; active++) {
                List<Integer> s = schedule(hits, active);
                assertEquals(hits, s.stream().mapToInt(Integer::intValue).sum(), hits + " hits over " + active);
                assertTrue(s.get(0) > 0, "the first lands on the first active tick");
                assertTrue(s.get(active - 1) > 0, "the last on the last");
            }
        }
    }

    @Test
    void onlyHitsWithoutAnIntervalSpread() {
        MoveDef.Hit single = new MoveDef.Hit(1.0, 0, 0, 3, 2, 1, 1, 0);
        assertFalse(single.spread());
        assertEquals(1, single.hitsAt(0, 1));
        assertEquals(1, single.hitsAt(2, 3), "a single hit may land on any active tick (the ledger counts it)");
        MoveDef.Hit paced = new MoveDef.Hit(1.0, 0, 0, 3, 2, 1, 3, 4);
        assertFalse(paced.spread(), "an interval keeps the ledger's pacing");
        assertEquals(1, paced.hitsAt(1, 10));
        assertTrue(move("").hit().spread());
    }
}
