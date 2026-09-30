package com.cosmicbreach.combat.data;

import com.cosmicbreach.combat.core.Grade;
import com.cosmicbreach.combat.core.Stat;
import com.cosmicbreach.combat.core.StatBlock;
import com.cosmicbreach.combat.server.HitResolver;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.cosmicbreach.combat.data.CombatDataTestAccess.id;
import static com.cosmicbreach.combat.data.CombatDataTestAccess.resource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shipped Binary Edges data against GDD 4.2: stats, the frame table, the Weave, the Tether and the blink. */
class BinaryEdgesDataTest {
    private static final List<String> MOVES = List.of("l1", "l2", "l3", "l4", "l5", "orbit", "twin_meteor", "scissor",
            "tether", "blink");

    private static CombatData edges() {
        Map<ResourceLocation, JsonElement> files = new LinkedHashMap<>();
        for (String name : MOVES) {
            files.put(id("binary_edges/" + name), resource("data/cosmicbreach/combat/moves/binary_edges/" + name + ".json"));
        }
        CombatData data = new CombatData();
        data.replace(CombatData.parseAll(files, MoveDef.CODEC, "move", LogUtils.getLogger()),
                CombatData.parseAll(Map.of(id("binary_edges"), resource("data/cosmicbreach/combat/weapons/binary_edges.json")),
                        WeaponDef.CODEC, "weapon", LogUtils.getLogger()));
        return data;
    }

    private static MoveDef move(CombatData data, String name) {
        MoveDef def = data.move(id("binary_edges/" + name));
        assertNotNull(def, name);
        return def;
    }

    @Test
    void everyMoveAndTheWeaponLoad() {
        CombatData data = edges();
        assertEquals(MOVES.size(), data.moves().size());
        WeaponDef edges = data.weapon(id("binary_edges"));
        assertNotNull(edges);
        assertEquals(3.8, edges.baseDamage());
        assertEquals(Map.of(Stat.AGILITY, Grade.A, Stat.POWER, Grade.D), edges.grades());
        assertEquals(2.8, edges.reach());
        assertEquals(2, edges.tier());
        assertEquals(List.of(id("binary_edges/l1"), id("binary_edges/l2"), id("binary_edges/l3"), id("binary_edges/l4"),
                id("binary_edges/l5")), edges.combo());
        assertEquals(Optional.of(id("binary_edges/orbit")), edges.charged());
        assertEquals(Optional.of(id("binary_edges/twin_meteor")), edges.plunge());
        assertEquals(Optional.of(id("binary_edges/scissor")), edges.dashAttack());
        assertEquals(new WeaponDef.DashFollow(id("binary_edges/l4"), 10), edges.dashFollow().orElseThrow(), "the Weave");
        WeaponDef.Ability tether = edges.ability().orElseThrow();
        assertEquals(id("binary_edges/tether"), tether.move());
        assertEquals(25, tether.cost());
        assertEquals(120, tether.cooldown(), "6 s");
        assertTrue(tether.cooldownAfterRecast(), "from the blade's return");
        assertEquals(new WeaponDef.Recast(id("binary_edges/blink"), 80, false), tether.recast().orElseThrow(),
                "the blink opens when the blade sticks, for its 80 ticks");
        WeaponDef.OffHand off = edges.offHand().orElseThrow();
        assertEquals(id("binary_edges_left"), off.item());
        assertEquals(id("edges_l2"), off.soloAnimation(id("edges_l1")), "one-bladed, the left hook is the right one");
        assertEquals(id("edges_dash_forward"), edges.animationFor(id("combat_dash_forward")));
        assertEquals(id("edges_parry"), edges.animationFor(id("combat_parry")));
        assertEquals(id("edges_stagger"), edges.animationFor(id("combat_stagger")));
        net.minecraft.nbt.Tag nbt = WeaponDef.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, edges).getOrThrow();
        assertEquals(edges, WeaponDef.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, nbt).getOrThrow(), "synced whole");
    }

    @Test
    void theFrameTableMatchesTheGdd() {
        CombatData data = edges();
        record Row(String name, MoveKind kind, int startup, int active, int recovery, double mvTotal, int hits, double impactTotal) {
        }
        List<Row> table = List.of(
                new Row("l1", MoveKind.LIGHT, 2, 1, 3, 1.0, 1, 3),
                new Row("l2", MoveKind.LIGHT, 2, 1, 3, 1.0, 1, 3),
                new Row("l3", MoveKind.LIGHT, 2, 2, 4, 1.2, 2, 4),
                new Row("l4", MoveKind.LIGHT, 3, 1, 4, 1.2, 1, 5),
                new Row("l5", MoveKind.LIGHT, 3, 3, 8, 2.0, 4, 10),
                new Row("orbit", MoveKind.CHARGED, 2, 10, 8, 3.0, 6, 18),
                new Row("twin_meteor", MoveKind.PLUNGE, 2, 0, 6, 1.0, 1, 12),
                new Row("scissor", MoveKind.DASH_ATTACK, 1, 2, 5, 1.3, 1, 6),
                new Row("tether", MoveKind.ABILITY, 3, 1, 7, 0.8, 1, 4),
                new Row("blink", MoveKind.ABILITY, 6, 2, 6, 2.0, 1, 12));
        for (Row row : table) {
            MoveDef def = move(data, row.name());
            assertEquals(row.kind(), def.kind(), row.name());
            assertEquals(new MoveDef.Timing(row.startup(), row.active(), row.recovery()), def.timing(), row.name());
            assertEquals(row.hits(), def.hit().hits(), row.name() + " hits");
            assertEquals(row.mvTotal(), def.hit().mv() * def.hit().hits(), 1e-9, row.name() + " motion value in all");
            assertEquals(row.impactTotal(), def.hit().impact() * def.hit().hits(), 1e-9, row.name() + " Impact in all");
            if (row.kind() != MoveKind.ABILITY) {
                assertEquals(2, def.hit().resonance(), row.name() + ": Resonance 2 a hit");
            }
            if (row.hits() > 1) {
                assertTrue(def.hit().spread(), row.name() + ": its hits spread over its active ticks");
                int sum = 0;
                for (int t = 0; t < row.active(); t++) {
                    sum += def.hit().hitsAt(t, row.active());
                }
                assertEquals(row.hits(), sum, row.name());
            }
        }
        assertInstanceOf(HitShape.Arc.class, move(data, "l1").hitbox());
        assertEquals(new HitShape.Arc(2.8, 90, 2.5), move(data, "l2").hitbox());
        assertEquals(new HitShape.Arc(3.0, 140, 2.5), move(data, "l3").hitbox());
        assertEquals(new HitShape.Line(3.5, 1.0, 2.5), move(data, "l4").hitbox());
        assertEquals(2.0, move(data, "l4").lunge(), "the Lunge moves you 2 blocks");
        assertEquals(new HitShape.Sphere(3.0, 0.0), move(data, "l5").hitbox());
        assertEquals(new HitShape.Sphere(3.5, 0.0), move(data, "orbit").hitbox());
        assertEquals(new HitShape.Sphere(2.0, 0.0), move(data, "twin_meteor").hitbox());
        assertEquals(new HitShape.Line(3.0, 1.5, 2.5), move(data, "scissor").hitbox());
        assertEquals(3.0, ((HitShape.Arc) move(data, "blink").hitbox()).radius(), "the arrival slash: arc, radius 3");
    }

    @Test
    void theLoopIs42TicksOf24Point3Damage() {
        CombatData data = edges();
        WeaponDef edges = data.weapon(id("binary_edges"));
        int ticks = 0;
        double damage = 0;
        for (ResourceLocation id : edges.combo()) {
            MoveDef def = data.move(id);
            ticks += def.timing().total();
            damage += def.hit().hits() * HitResolver.damage(edges, def, def.hit().mv(), false, false, StatBlock.ZERO);
        }
        assertEquals(42, ticks);
        assertEquals(24.32, damage, 1e-9);
        assertEquals(11.6, damage / (ticks / 20.0), 0.05, "DPS at zero stats");
    }

    @Test
    void theOrbitTheMeteorTheTetherAndTheBlink() {
        CombatData data = edges();
        MoveDef orbit = move(data, "orbit");
        assertEquals(12, orbit.charge().orElseThrow().min(), "hold 12 ...");
        assertEquals(20, orbit.charge().orElseThrow().full(), "... to 20");
        assertEquals(0.6f, orbit.traits().walkMultiplier(), "moving with you at 60%");
        MoveDef meteor = move(data, "twin_meteor");
        assertEquals(1.0, meteor.hit().mv());
        assertEquals(0.08, meteor.hit().mvPerBlock());
        assertEquals(1.8, meteor.hit().mvCap());
        MoveEffect tether = move(data, "tether").traits().effect(id("tether")).orElseThrow();
        assertEquals(16.0, tether.param("range", 0));
        assertEquals(80, tether.intParam("stick", 0), "sticks for 80 ticks");
        assertEquals(0.6, tether.param("solo", 0), "the right blade alone at 60%");
        assertEquals(60, tether.intParam("mark_ticks", 0), "Marked for 60 ticks");
        assertEquals(0.2, tether.param("mark_crit", 0), "+20% crit chance");
        MoveDef blink = move(data, "blink");
        assertEquals(Optional.of(MoveTraits.Stage.STARTUP), blink.traits().invulnerable(), "invulnerable through the travel");
        assertEquals(6, blink.traits().effect(id("blink")).orElseThrow().intParam("travel", 0), "6 ticks of travel");
        assertEquals(6, blink.timing().startup());
        assertFalse(blink.slash().isEmpty());
    }
}
