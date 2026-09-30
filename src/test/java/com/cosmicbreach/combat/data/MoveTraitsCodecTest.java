package com.cosmicbreach.combat.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The optional behaviour fields of a move, the compound shape, a weapon's blade layout and second press. */
class MoveTraitsCodecTest {
    private static final String BASE = """
            "kind": "ability",
            "timing": {"startup": 8, "active": 31, "recovery": 12},
            "hit": {"mv": 2.0, "impact": 45},
            "hitbox": {"type": "sphere", "radius": 3},
            "animation": "cosmicbreach:maul_well"
            """;

    private static MoveDef parse(String extra) {
        JsonElement json = JsonParser.parseString("{" + BASE + (extra.isEmpty() ? "" : "," + extra) + "}");
        return MoveDef.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("cosmicbreach", path);
    }

    @Test
    void aMoveWithoutTheNewFieldsHasNoTraits() {
        assertEquals(MoveTraits.NONE, parse("").traits());
    }

    @Test
    void everyTraitParsesFromTheTopLevel() {
        MoveDef def = parse("""
                "hyper_armor": 30,
                "root": true,
                "movement": 0.5,
                "cancels": [{"by": "dash", "phase": "active", "from": 11}],
                "effects": [{"id": "cosmicbreach:gravity_well", "params": {"radius": 6, "pull": 0.12}}],
                "sound": {"swing": "cosmicbreach:maul/swing", "slam": "cosmicbreach:maul/slam"}
                """);
        MoveTraits t = def.traits();
        assertEquals(30.0, t.hyperArmor());
        assertTrue(t.root());
        assertEquals(0.5f, t.movement());
        assertEquals(0.0f, t.walkMultiplier(), "rooted beats the walk speed");
        assertEquals(List.of(new MoveTraits.CancelWindow(MoveTraits.CancelBy.DASH, MoveTraits.Stage.ACTIVE, 11)), t.cancels());
        MoveEffect well = t.effect(id("gravity_well")).orElseThrow();
        assertEquals(6.0, well.param("radius", 0));
        assertEquals(0.12, well.param("pull", 0));
        assertEquals(30, well.intParam("ticks", 30), "absent parameters take the handler's default");
        assertEquals(Optional.of(id("maul/swing")), t.sound().orElseThrow().swing());
        assertEquals(Optional.empty(), t.sound().orElseThrow().hit());
        assertTrue(t.cancelOpen(MoveTraits.CancelBy.DASH, MoveTraits.Stage.ACTIVE, 11));
        assertFalse(t.cancelOpen(MoveTraits.CancelBy.PARRY, MoveTraits.Stage.ACTIVE, 11));
    }

    @Test
    void badTraitsAreRejected() {
        assertTrue(MoveDef.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{" + BASE + ", \"movement\": 5}")).isError());
        assertTrue(MoveDef.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{" + BASE + ", \"cancels\": [{\"by\": \"jump\", \"phase\": \"active\"}]}")).isError());
        assertTrue(MoveDef.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{" + BASE + ", \"hyper_armor\": -1}")).isError());
    }

    @Test
    void traitsSurviveTheNetworkRoundTrip() {
        MoveDef def = parse("""
                "hyper_armor": 30, "movement": 0.35,
                "cancels": [{"by": "dash", "phase": "active", "from": 11}],
                "effects": [{"id": "cosmicbreach:crater", "params": {"ticks": 60, "slow": 0.3}}],
                "sound": {"charge": "cosmicbreach:maul/charge"}
                """);
        Tag nbt = MoveDef.CODEC.encodeStart(NbtOps.INSTANCE, def).getOrThrow();
        assertEquals(def, MoveDef.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow());
    }

    @Test
    void aCompoundShapeHitsWhatAnyOfItsShapesHits() {
        MoveDef def = MoveDef.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"kind": "light", "timing": {"startup": 7, "active": 2, "recovery": 10},
                 "hit": {"mv": 1.0, "impact": 22}, "animation": "cosmicbreach:maul_l1",
                 "hitbox": {"type": "compound", "shapes": [
                     {"type": "arc", "radius": 3, "angle": 70},
                     {"type": "sphere", "radius": 1.5, "offset": 2.4}]}}
                """)).getOrThrow();
        HitShape.Compound shape = assertInstanceOf(HitShape.Compound.class, def.hitbox());
        assertEquals(2, shape.shapes().size());
        assertEquals(3.9, shape.reach(), 1e-9);
        Vec3 origin = new Vec3(0, 1, 0);
        // yaw 0 faces +Z. Straight ahead at 2.8: the arc. At 3.7 ahead, past the arc's radius: only the ring.
        assertTrue(shape.hits(origin, 0f, box(0, 2.8)));
        assertTrue(shape.hits(origin, 0f, box(0, 3.7)));
        assertFalse(shape.shapes().get(0).hits(origin, 0f, box(0, 3.7)), "beyond the arc's 3 blocks");
        assertFalse(shape.hits(origin, 0f, box(0, -2.0)), "behind");
        assertFalse(shape.hits(origin, 0f, box(2.6, 1.0)), "beside, out of both");
        Tag nbt = MoveDef.CODEC.encodeStart(NbtOps.INSTANCE, def).getOrThrow();
        assertEquals(def, MoveDef.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow());
    }

    @Test
    void anEmptyCompoundIsRejected() {
        assertTrue(HitShape.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"type\": \"compound\", \"shapes\": []}")).isError());
    }

    @Test
    void aWeaponMayNameItsBladeAndASecondPress() {
        WeaponDef weapon = WeaponDef.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"base_damage": 3.8, "combo": ["cosmicbreach:twin/l1"],
                 "ability": {"move": "cosmicbreach:twin/throw", "cost": 25, "cooldown": 120,
                             "recast": {"move": "cosmicbreach:twin/blink", "window": 80}},
                 "blade": {"guard": [20, 12], "tip": [29, 3], "half_width": 5.5}}
                """)).getOrThrow();
        assertEquals(new WeaponDef.Recast(id("twin/blink"), 80), weapon.ability().orElseThrow().recast().orElseThrow());
        WeaponDef.Blade blade = weapon.bladeOrDefault();
        assertEquals(List.of(20f, 12f), blade.guard());
        assertEquals(5.5f, blade.halfWidth());
        Tag nbt = WeaponDef.CODEC.encodeStart(NbtOps.INSTANCE, weapon).getOrThrow();
        assertEquals(weapon, WeaponDef.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow());
    }

    @Test
    void aWeaponWithoutABladeFollowsMeridians() {
        WeaponDef weapon = new WeaponDef(5.0, Map.of(), 3.5, List.of(id("meridian/l1")), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), 1);
        assertEquals(WeaponDef.Blade.MERIDIAN, weapon.bladeOrDefault());
        assertTrue(WeaponDef.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"base_damage\": 1, \"combo\": [\"a:b\"], \"blade\": {\"guard\": [1], \"tip\": [2, 3], \"half_width\": 1}}")).isError());
    }

    private static AABB box(double x, double z) {
        return new AABB(x - 0.3, 0, z - 0.3, x + 0.3, 1.95, z + 0.3);
    }
}
