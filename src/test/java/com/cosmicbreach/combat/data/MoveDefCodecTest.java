package com.cosmicbreach.combat.data;

import com.cosmicbreach.combat.core.Grade;
import com.cosmicbreach.combat.core.Stat;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shipped Meridian data parses and matches the GDD's frame table. */
class MoveDefCodecTest {
    private static final List<String> MERIDIAN_MOVES = List.of("l1", "l2", "l3", "line", "falling_star", "pass", "zenith");

    private static JsonElement load(String path) {
        try (InputStream in = MoveDefCodecTest.class.getResourceAsStream("/" + path)) {
            assertNotNull(in, "missing resource " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static MoveDef move(String name) {
        return MoveDef.CODEC.parse(JsonOps.INSTANCE, load("data/cosmicbreach/combat/moves/meridian/" + name + ".json")).getOrThrow();
    }

    @Test
    void everyMeridianMoveParses() {
        for (String name : MERIDIAN_MOVES) {
            assertNotNull(move(name), name);
        }
    }

    @Test
    void risingCutMatchesTheFrameTable() {
        MoveDef l1 = move("l1");
        assertEquals(MoveKind.LIGHT, l1.kind());
        assertEquals(new MoveDef.Timing(3, 2, 5), l1.timing());
        assertEquals(1.0, l1.hit().mv());
        assertEquals(6.0, l1.hit().impact());
        assertEquals(6, l1.hit().resonance());
        assertInstanceOf(HitShape.Arc.class, l1.hitbox());
        assertEquals(Optional.of(ResourceLocation.parse("cosmicbreach:meridian/l2")), l1.next());
        assertEquals(0xFFE8B0, l1.slash().orElseThrow().color());
    }

    @Test
    void theChainLoopsAndLastsThirtySevenTicks() {
        int ticks = move("l1").timing().total() + move("l2").timing().total() + move("l3").timing().total();
        assertEquals(37, ticks);
        assertEquals(Optional.of(ResourceLocation.parse("cosmicbreach:meridian/l1")), move("l3").next());
    }

    @Test
    void chargedLineScalesFromTwoPointTwoToThree() {
        MoveDef line = move("line");
        assertEquals(new MoveDef.Charge(12, 24, Optional.of(ResourceLocation.parse("cosmicbreach:meridian_charge"))),
                line.charge().orElseThrow());
        assertEquals(3.0, line.hit().mvCap());
        assertEquals(8.0, line.wave().orElseThrow().length());
    }

    @Test
    void zenithLaunches() {
        MoveDef.Launch launch = move("zenith").launch().orElseThrow();
        assertEquals(40.0, launch.maxPoise());
        assertEquals(30, launch.suspendTicks());
        assertEquals(3.0, launch.rise());
    }

    @Test
    void meridianWeaponParses() {
        WeaponDef w = WeaponDef.CODEC.parse(JsonOps.INSTANCE, load("data/cosmicbreach/combat/weapons/meridian.json")).getOrThrow();
        assertEquals(5.0, w.baseDamage());
        assertEquals(3, w.combo().size());
        assertEquals(Grade.B, w.grades().get(Stat.POWER));
        assertEquals(Grade.C, w.grades().get(Stat.AGILITY));
        assertEquals(30, w.ability().orElseThrow().cost());
        assertEquals(100, w.ability().orElseThrow().cooldown());
    }

    @Test
    void animationsStayDataDriven() {
        assertEquals(Optional.of(ResourceLocation.parse("cosmicbreach:meridian_charge")),
                move("line").charge().orElseThrow().animation());
        assertEquals(Optional.of(ResourceLocation.parse("cosmicbreach:meridian_falling_star_land")),
                move("falling_star").landAnimation());
        assertEquals(Optional.empty(), move("l1").landAnimation(), "only a plunge lands");
    }

    @Test
    void theOptionalAnimationFieldsRoundTrip() {
        MoveDef.Charge charge = new MoveDef.Charge(10, 20, Optional.of(ResourceLocation.parse("cosmicbreach:hold")));
        MoveDef withBoth = new MoveDef(MoveKind.PLUNGE, new MoveDef.Timing(2, 0, 10),
                new MoveDef.Hit(1.4, 2.4, 0.1, 20, 6, 4, 1, 0), new HitShape.Sphere(2.5, 0), Optional.empty(),
                ResourceLocation.parse("cosmicbreach:dive"), Optional.of(ResourceLocation.parse("cosmicbreach:land")), 0.0,
                Optional.of(charge), Optional.empty(), Optional.empty(), Optional.empty());
        JsonElement json = MoveDef.CODEC.encodeStart(JsonOps.INSTANCE, withBoth).getOrThrow();
        assertEquals("cosmicbreach:land", json.getAsJsonObject().get("land_animation").getAsString());
        assertEquals("cosmicbreach:hold", json.getAsJsonObject().getAsJsonObject("charge").get("animation").getAsString());
        assertEquals(withBoth, MoveDef.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());

        MoveDef withNeither = new MoveDef(MoveKind.LIGHT, new MoveDef.Timing(3, 2, 5),
                new MoveDef.Hit(1.0, 0, 0, 6, 6, 2, 1, 0), new HitShape.Arc(3.5, 110, 2.5), Optional.empty(),
                ResourceLocation.parse("cosmicbreach:swing"), 0.0, Optional.of(new MoveDef.Charge(12, 24)),
                Optional.empty(), Optional.empty(), Optional.empty());
        JsonElement plain = MoveDef.CODEC.encodeStart(JsonOps.INSTANCE, withNeither).getOrThrow();
        assertTrue(!plain.getAsJsonObject().has("land_animation"), "an absent landing animation is not written");
        assertTrue(!plain.getAsJsonObject().getAsJsonObject("charge").has("animation"), "an absent hold pose is not written");
        MoveDef back = MoveDef.CODEC.parse(JsonOps.INSTANCE, plain).getOrThrow();
        assertEquals(withNeither, back);
        assertEquals(Optional.empty(), back.landAnimation());
        assertEquals(Optional.empty(), back.charge().orElseThrow().animation());
    }

    @Test
    void badInputIsRejected() {
        JsonElement badColor = JsonParser.parseString(
                "{\"from\": 0, \"to\": 10, \"radius\": 1, \"color\": \"#GG0000\"}");
        assertTrue(MoveDef.Slash.CODEC.parse(JsonOps.INSTANCE, badColor).isError());
        JsonElement badShape = JsonParser.parseString("{\"type\": \"cone\", \"radius\": 1}");
        assertTrue(HitShape.CODEC.parse(JsonOps.INSTANCE, badShape).isError());
        JsonElement emptyCombo = JsonParser.parseString("{\"base_damage\": 1, \"combo\": []}");
        assertTrue(WeaponDef.CODEC.parse(JsonOps.INSTANCE, emptyCombo).isError());
        JsonElement badHoldPose = JsonParser.parseString("{\"min\": 12, \"full\": 24, \"animation\": \"Not A Location\"}");
        assertTrue(MoveDef.Charge.CODEC.parse(JsonOps.INSTANCE, badHoldPose).isError());
    }
}
