package com.cosmicbreach.combat.data;

import com.cosmicbreach.combat.core.CombatEvent;
import com.cosmicbreach.combat.core.CombatStateMachine;
import com.cosmicbreach.combat.core.CombatStateMachine.Context;
import com.cosmicbreach.combat.core.Grade;
import com.cosmicbreach.combat.core.MoveInstance;
import com.cosmicbreach.combat.core.Stat;
import com.cosmicbreach.relic.cantor.CantorRules;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.cosmicbreach.combat.data.CombatDataTestAccess.id;
import static com.cosmicbreach.combat.data.CombatDataTestAccess.resource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped Umbra Cantor data against GDD 7.3: a T4 longbow graded Agility A and Arcane C, the 20-tick charged shot
 * that leaves a note, Cadence's three shots in 12 ticks for 30 Resonance on a 7 s cooldown, all through the machine.
 */
class UmbraCantorDataTest {
    private static final List<String> MOVES = List.of("l1", "l2", "l3", "fermata", "grace_note", "syncopation", "cadence");
    private static final Context G = Context.GROUNDED;

    static CombatData data() {
        Map<ResourceLocation, JsonElement> files = new LinkedHashMap<>();
        for (String name : MOVES) {
            files.put(id("umbra_cantor/" + name), resource("data/cosmicbreach/combat/moves/umbra_cantor/" + name + ".json"));
        }
        CombatData data = new CombatData();
        data.replace(CombatData.parseAll(files, MoveDef.CODEC, "move", LogUtils.getLogger()),
                CombatData.parseAll(Map.of(id("umbra_cantor"), resource("data/cosmicbreach/combat/weapons/umbra_cantor.json")),
                        WeaponDef.CODEC, "weapon", LogUtils.getLogger()));
        return data;
    }

    private static MoveDef move(CombatData data, String name) {
        MoveDef def = data.move(id("umbra_cantor/" + name));
        assertNotNull(def, name);
        return def;
    }

    @Test
    void theWeaponIsTheGddsLongbow() {
        CombatData data = data();
        assertEquals(MOVES.size(), data.moves().size());
        WeaponDef bow = data.weapon(id("umbra_cantor"));
        assertNotNull(bow);
        assertEquals(Map.of(Stat.AGILITY, Grade.A, Stat.ARCANE, Grade.C), bow.grades());
        assertEquals(4, bow.tier());
        assertTrue(bow.plunge().isEmpty(), "a bow has no plunge: any attack in the air is its Grace Note");
        assertEquals(id("umbra_cantor/grace_note"), bow.aerial().orElseThrow());
        WeaponDef.Ability ability = bow.ability().orElseThrow();
        assertEquals(id("umbra_cantor/cadence"), ability.move());
        assertEquals(30, ability.cost());
        assertEquals(140, ability.cooldown(), "7 s");
        for (String name : MOVES) {
            assertTrue(move(data, name).traits().effects().stream().anyMatch(e -> e.id().getPath().equals("umbra_shot")
                    || e.id().getPath().equals("cadence")), name + " looses arrows (the effect deals the hits)");
        }
    }

    @Test
    void theChainLoopsInFortyTwoTicksForThirteenDps() {
        CombatData data = data();
        WeaponDef bow = data.weapon(id("umbra_cantor"));
        int ticks = 0;
        double mv = 0.0;
        for (ResourceLocation id : bow.combo()) {
            MoveDef def = data.move(id);
            ticks += def.timing().total();
            mv += def.hit().mv();
        }
        assertEquals(42, ticks);
        assertEquals(3.8, mv, 1e-9);
        assertEquals(13.0, bow.baseDamage() * mv / (ticks / 20.0), 0.05, "about 15% under Last Light, for range");
    }

    @Test
    void theChargedShotIsATwentyTickDrawThatLeavesANote() {
        CombatData data = data();
        MoveDef fermata = move(data, "fermata");
        assertEquals(MoveKind.CHARGED, fermata.kind());
        assertEquals(CantorRules.DRAW_TICKS, fermata.charge().orElseThrow().min());
        assertEquals(CantorRules.DRAW_TICKS, fermata.charge().orElseThrow().full());
        MoveEffect shot = fermata.traits().effects().get(0);
        assertEquals(1, shot.intParam("note", 0), "the charged shot's arrow leaves a note");
        for (String quick : List.of("l1", "l2", "l3", "grace_note", "syncopation")) {
            assertEquals(0, move(data, quick).traits().effects().get(0).intParam("note", 0), quick + " leaves none");
        }

        CombatStateMachine m = new CombatStateMachine(data::move);
        m.setWeapon(data.weapon(id("umbra_cantor")));
        List<ResourceLocation> started = new ArrayList<>();
        m.pressAttack(G);
        for (int t = 0; t < 19; t++) {
            m.tick(G).forEach(e -> {
                if (e instanceof CombatEvent.MoveStarted s) {
                    started.add(s.move().id());
                }
            });
        }
        m.releaseAttack(G);
        m.tick(G).forEach(e -> {
            if (e instanceof CombatEvent.MoveStarted s) {
                started.add(s.move().id());
            }
        });
        assertFalse(started.contains(id("umbra_cantor/fermata")), "let go at 19 ticks: no charged shot");
        m.pressAttack(G);
        MoveInstance fired = null;
        for (int t = 0; t < 80 && fired == null; t++) {
            if (t == 20) {
                m.releaseAttack(G);
            }
            for (CombatEvent e : m.tick(G)) {
                if (e instanceof CombatEvent.MoveStarted s && s.move().def().kind() == MoveKind.CHARGED) {
                    fired = s.move();
                }
            }
        }
        assertNotNull(fired, "held 20 ticks: the charged shot");
    }

    @Test
    void cadenceIsThreeShotsInTwelveTicks() {
        CombatData data = data();
        MoveDef cadence = move(data, "cadence");
        assertEquals(MoveKind.ABILITY, cadence.kind());
        List<Integer> shots = new ArrayList<>();
        for (int t = 0; t < cadence.timing().active(); t++) {
            if (CantorRules.cadenceShot(t)) {
                shots.add(t);
            }
        }
        assertEquals(List.of(0, 5, 10), shots, "every shot inside the active ticks");

        CombatStateMachine m = new CombatStateMachine(data::move);
        m.setWeapon(data.weapon(id("umbra_cantor")));
        m.syncFromServer(100.0, 2, 0);
        m.pressAbility(G);
        assertEquals(70.0, m.resonance(), 1e-9, "30 to cast");
        List<Integer> activeAt = new ArrayList<>();
        for (int t = 0; t < 40; t++) {
            for (CombatEvent e : m.tick(G)) {
                if (e instanceof CombatEvent.ActiveTick a && CantorRules.cadenceShot(a.activeTick())) {
                    activeAt.add(t);
                }
            }
        }
        assertEquals(3, activeAt.size());
        assertTrue(activeAt.get(2) - activeAt.get(0) < 12, "three quick shots in 12 ticks: " + activeAt);
        assertEquals(140 - 40, m.abilityCooldown(), "7 s from the cast");
    }
}
