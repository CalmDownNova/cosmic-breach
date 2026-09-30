package com.cosmicbreach.combat.data;

import com.cosmicbreach.combat.core.CombatEvent;
import com.cosmicbreach.combat.core.CombatStateMachine;
import com.cosmicbreach.combat.core.CombatStateMachine.Context;
import com.cosmicbreach.combat.core.Grade;
import com.cosmicbreach.combat.core.MoveInstance;
import com.cosmicbreach.combat.core.Stat;
import com.cosmicbreach.combat.core.StatBlock;
import com.cosmicbreach.combat.server.HitResolver;
import com.cosmicbreach.relic.lastlight.LastLightRules;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped Last Light data against GDD 7.3 and 4.3: its stats, the three thrusts' frame data and their 15.5 DPS
 * loop (through the real state machine), the charged Sunspear, the Sunfall plunge, the Sunstreak, and Dawnguard as the
 * machine runs it: 35 Resonance, a 30-tick stance, 9 s of cooldown.
 */
class LastLightDataTest {
    private static final List<String> MOVES = List.of("l1", "l2", "l3", "sunspear", "sunfall", "sunstreak", "dawnguard");
    private static final Context G = Context.GROUNDED;

    static CombatData data() {
        Map<ResourceLocation, JsonElement> files = new LinkedHashMap<>();
        for (String name : MOVES) {
            files.put(id("last_light/" + name), resource("data/cosmicbreach/combat/moves/last_light/" + name + ".json"));
        }
        CombatData data = new CombatData();
        data.replace(CombatData.parseAll(files, MoveDef.CODEC, "move", LogUtils.getLogger()),
                CombatData.parseAll(Map.of(id("last_light"), resource("data/cosmicbreach/combat/weapons/last_light.json")),
                        WeaponDef.CODEC, "weapon", LogUtils.getLogger()));
        return data;
    }

    private static MoveDef move(CombatData data, String name) {
        MoveDef def = data.move(id("last_light/" + name));
        assertNotNull(def, name);
        return def;
    }

    @Test
    void theWeaponIsTheGddsSolarGlaive() {
        CombatData data = data();
        assertEquals(MOVES.size(), data.moves().size());
        WeaponDef glaive = data.weapon(id("last_light"));
        assertNotNull(glaive);
        assertEquals(9.0, glaive.baseDamage());
        assertEquals(Map.of(Stat.RESILIENCE, Grade.A, Stat.POWER, Grade.B), glaive.grades());
        assertEquals(4.2, glaive.reach());
        assertEquals(4, glaive.tier());
        assertEquals(List.of(id("last_light/l1"), id("last_light/l2"), id("last_light/l3")), glaive.combo());
        WeaponDef.Ability ability = glaive.ability().orElseThrow();
        assertEquals(id("last_light/dawnguard"), ability.move());
        assertEquals(35, ability.cost());
        assertEquals(180, ability.cooldown(), "9 s");
        assertTrue(glaive.blade().isPresent(), "the effects follow the glaive's head");
        for (ResourceLocation m : List.of(glaive.charged().orElseThrow(), glaive.plunge().orElseThrow(), glaive.dashAttack().orElseThrow())) {
            assertNotNull(data.move(m), m.toString());
        }
    }

    @Test
    void theThreeThrustsAreTheGddsFrameTable() {
        CombatData data = data();
        record Row(String name, int startup, int active, int recovery, double mv) {
        }
        for (Row row : List.of(new Row("l1", 4, 2, 6, 1.0), new Row("l2", 4, 2, 7, 1.1), new Row("l3", 6, 3, 10, 1.7))) {
            MoveDef def = move(data, row.name());
            assertEquals(MoveKind.LIGHT, def.kind(), row.name());
            assertEquals(new MoveDef.Timing(row.startup(), row.active(), row.recovery()), def.timing(), row.name());
            assertEquals(row.mv(), def.hit().mv(), 1e-9, row.name());
            assertTrue(def.hitbox() instanceof HitShape.Line, row.name() + " is a thrust");
            assertEquals(1, def.hit().hits(), row.name() + " strikes each target once");
        }
        assertEquals(4.2, ((HitShape.Line) move(data, "l1").hitbox()).length(), 1e-9, "the glaive's reach");
    }

    @Test
    void theLoopIsFortyFourTicksAndFifteenPointFiveDpsAtZeroStats() {
        CombatData data = data();
        WeaponDef glaive = data.weapon(id("last_light"));
        CombatStateMachine m = new CombatStateMachine(data::move);
        m.setWeapon(glaive);
        // Attack buffered in every recovery: the chain runs back to back, as a player tapping in rhythm plays it.
        List<Long> starts = new ArrayList<>();
        double damage = 0.0;
        long tick = 0;
        int loops = 0;
        m.pressAttack(G);
        m.releaseAttack(G);
        while (loops < 4 && tick < 400) {
            for (CombatEvent e : m.tick(G)) {
                if (e instanceof CombatEvent.MoveStarted s) {
                    starts.add(tick);
                    if (s.move().id().equals(id("last_light/l3"))) {
                        loops++;
                    }
                }
                if (e instanceof CombatEvent.ActiveTick a && a.activeTick() == 0) {
                    MoveInstance mv = a.move();
                    damage += HitResolver.damage(glaive, mv.def(), mv.mv(), false, false, StatBlock.ZERO);
                }
            }
            if (m.phase() == CombatStateMachine.Phase.RECOVERY) {
                m.pressAttack(G);
                m.releaseAttack(G);
            }
            tick++;
        }
        assertEquals(12, starts.size(), "four loops of three thrusts");
        // (the first press lands between ticks, a tick before the buffered ones, so time the later loops)
        for (int i = 3; i + 3 < starts.size(); i += 3) {
            assertEquals(44L, starts.get(i + 3) - starts.get(i), "one loop: 12 + 13 + 19 ticks");
        }
        double perLoop = 9.0 * (1.0 + 1.1 + 1.7);
        assertEquals(34.2, perLoop, 1e-9);
        double dps = perLoop / (44 / 20.0);
        assertEquals(15.5, dps, 0.05, "GDD 4.3's 15.5");
        assertTrue(damage >= 3 * perLoop - 1e-6, "every thrust landed its motion value");
    }

    @Test
    void theSunspearChargesFromFourteenToTwentyEightTicks() {
        CombatData data = data();
        MoveDef spear = move(data, "sunspear");
        assertEquals(MoveKind.CHARGED, spear.kind());
        assertEquals(14, spear.charge().orElseThrow().min());
        assertEquals(28, spear.charge().orElseThrow().full());
        assertEquals(2.2, spear.hit().mv(), 1e-9);
        assertEquals(3.0, spear.hit().mvCap(), 1e-9);
        assertTrue(spear.traits().effect(id("sunlight")).isPresent(), "it spends the Sunlight charges");

        CombatStateMachine m = new CombatStateMachine(data::move);
        m.setWeapon(data.weapon(id("last_light")));
        m.pressAttack(G);
        MoveInstance released = null;
        for (int t = 0; t < 80 && released == null; t++) {
            if (t == 30) {
                m.releaseAttack(G);
            }
            for (CombatEvent e : m.tick(G)) {
                if (e instanceof CombatEvent.MoveStarted s && s.move().def().kind() == MoveKind.CHARGED) {
                    released = s.move();
                }
            }
        }
        assertNotNull(released, "a held attack flows from L1 into the charge, and the release fires the Sunspear");
        assertEquals(3.0, released.mv(), 1e-9, "held past full: the full motion value");
    }

    @Test
    void thePlungeAndDashAttackAreInItsSpirit() {
        CombatData data = data();
        MoveDef fall = move(data, "sunfall");
        assertEquals(MoveKind.PLUNGE, fall.kind());
        assertEquals(new HitShape.Sphere(3.0, 0.0), fall.hitbox());
        assertEquals(2.8, fall.hit().mvCap(), 1e-9);
        MoveDef streak = move(data, "sunstreak");
        assertEquals(MoveKind.DASH_ATTACK, streak.kind());
        assertTrue(streak.lunge() > 2.0, "a running thrust through the target");
    }

    @Test
    void dawnguardIsAThirtyTickStanceForThirtyFiveResonanceOnANineSecondCooldown() {
        CombatData data = data();
        MoveDef guard = move(data, "dawnguard");
        assertEquals(MoveKind.ABILITY, guard.kind());
        assertEquals(LastLightRules.STANCE_TICKS, guard.timing().active());
        assertEquals(new HitShape.Arc(LastLightRules.DAYBREAK_RADIUS, LastLightRules.DAYBREAK_ANGLE, 3.0), guard.hitbox(),
                "its hitbox is Daybreak's");
        assertEquals(LastLightRules.DAYBREAK_MV, guard.hit().mv(), 1e-9);
        assertEquals(LastLightRules.PARRY_COST, guard.traits().effect(id("dawnguard")).orElseThrow().param("parry_cost", 0), 1e-9);

        CombatStateMachine m = new CombatStateMachine(data::move);
        m.setWeapon(data.weapon(id("last_light")));
        m.syncFromServer(100.0, 2, 0);
        m.pressAbility(G);
        assertEquals(65.0, m.resonance(), 1e-9, "35 to cast");
        int active = 0;
        int first = -1;
        for (int t = 0; t < 60; t++) {
            m.tick(G);
            if (m.phase() == CombatStateMachine.Phase.ACTIVE) {
                if (first < 0) {
                    first = t;
                }
                active++;
            }
        }
        assertEquals(30, active, "the stance holds 30 ticks");
        assertEquals(guard.timing().startup(), first + 1, "after the guard is raised");
        assertTrue(m.abilityCooldown() > 0, "on cooldown");
        m.syncFromServer(100.0, 2, m.abilityCooldown());
        m.pressAbility(G);
        assertEquals(100.0, m.resonance(), 1e-9, "a second cast during the cooldown is refused");
        m.drainResonance(LastLightRules.PARRY_COST);
        assertEquals(90.0, m.resonance(), 1e-9, "a parry's price comes out of the bar");
    }
}
