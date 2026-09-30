package com.cosmicbreach.combat.data;

import com.cosmicbreach.combat.MoveSound;
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

import static com.cosmicbreach.combat.data.CombatDataTestAccess.id;
import static com.cosmicbreach.combat.data.CombatDataTestAccess.resource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shipped Comet Maul data against GDD 4.2: stats, the frame table, hyper armor, the Gravity Well. */
class CometMaulDataTest {
    private static final List<String> MOVES = List.of("l1", "l2", "l3", "crater", "meteorfall", "ram", "gravity_well");

    private static CombatData maul() {
        Map<ResourceLocation, JsonElement> files = new LinkedHashMap<>();
        for (String name : MOVES) {
            files.put(id("comet_maul/" + name), resource("data/cosmicbreach/combat/moves/comet_maul/" + name + ".json"));
        }
        CombatData data = new CombatData();
        data.replace(CombatData.parseAll(files, MoveDef.CODEC, "move", LogUtils.getLogger()),
                CombatData.parseAll(Map.of(id("comet_maul"), resource("data/cosmicbreach/combat/weapons/comet_maul.json")),
                        WeaponDef.CODEC, "weapon", LogUtils.getLogger()));
        return data;
    }

    private static MoveDef move(CombatData data, String name) {
        MoveDef def = data.move(id("comet_maul/" + name));
        assertNotNull(def, name);
        return def;
    }

    @Test
    void everyMoveAndTheWeaponLoad() {
        CombatData data = maul();
        assertEquals(MOVES.size(), data.moves().size());
        WeaponDef maul = data.weapon(id("comet_maul"));
        assertNotNull(maul);
        assertEquals(11.0, maul.baseDamage());
        assertEquals(Map.of(Stat.POWER, Grade.A, Stat.RESILIENCE, Grade.C), maul.grades());
        assertEquals(3.2, maul.reach());
        assertEquals(2, maul.tier());
        assertEquals(List.of(id("comet_maul/l1"), id("comet_maul/l2"), id("comet_maul/l3")), maul.combo());
        WeaponDef.Ability ability = maul.ability().orElseThrow();
        assertEquals(id("comet_maul/gravity_well"), ability.move());
        assertEquals(40, ability.cost());
        assertEquals(180, ability.cooldown(), "9 s");
        assertTrue(maul.blade().isPresent(), "the effects follow the head, not Meridian's blade");
        assertEquals(id("maul_dash_forward"), maul.animationFor(id("combat_dash_forward")), "its own dash");
        assertEquals(id("maul_stagger"), maul.animationFor(id("combat_stagger")));
        assertEquals(id("combat_parry"), maul.animationFor(id("combat_parry")), "the engine's parry stays");
        net.minecraft.nbt.Tag nbt = WeaponDef.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, maul).getOrThrow();
        assertEquals(maul, WeaponDef.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, nbt).getOrThrow(), "synced whole");
        for (ResourceLocation m : List.of(maul.charged().orElseThrow(), maul.plunge().orElseThrow(), maul.dashAttack().orElseThrow())) {
            assertNotNull(data.move(m), m.toString());
        }
    }

    @Test
    void theFrameTableMatchesTheGdd() {
        CombatData data = maul();
        record Row(String name, MoveKind kind, int startup, int active, int recovery, double mv, double impact) {
        }
        List<Row> table = List.of(
                new Row("l1", MoveKind.LIGHT, 7, 2, 10, 1.0, 22),
                new Row("l2", MoveKind.LIGHT, 6, 3, 11, 1.1, 20),
                new Row("l3", MoveKind.LIGHT, 9, 4, 14, 1.6, 35),
                new Row("crater", MoveKind.CHARGED, 5, 3, 16, 2.5, 60),
                new Row("meteorfall", MoveKind.PLUNGE, 3, 0, 14, 1.8, 45),
                new Row("ram", MoveKind.DASH_ATTACK, 3, 4, 10, 1.3, 30));
        for (Row row : table) {
            MoveDef def = move(data, row.name());
            assertEquals(row.kind(), def.kind(), row.name());
            assertEquals(new MoveDef.Timing(row.startup(), row.active(), row.recovery()), def.timing(), row.name());
            assertEquals(row.mv(), def.hit().mv(), 1e-9, row.name());
            assertEquals(row.impact(), def.hit().impact(), 1e-9, row.name());
            assertEquals(12, def.hit().resonance(), row.name() + ": 12 Resonance a hit");
            assertEquals(30.0, def.traits().hyperArmor(), row.name() + ": hyper armor on every attack");
        }
        MoveDef crater = move(data, "crater");
        assertEquals(3.5, crater.hit().mvCap(), 1e-9);
        assertEquals(16, crater.charge().orElseThrow().min());
        assertEquals(30, crater.charge().orElseThrow().full());
        MoveDef fall = move(data, "meteorfall");
        assertEquals(0.15, fall.hit().mvPerBlock(), 1e-9);
        assertEquals(3.3, fall.hit().mvCap(), 1e-9);
        assertInstanceOf(HitShape.Compound.class, move(data, "l1").hitbox(), "a cone plus a ring at the head");
        assertEquals(new HitShape.Arc(3.5, 180, 3.0), move(data, "l2").hitbox());
        assertEquals(new HitShape.Sphere(3.5, 0.0), move(data, "l3").hitbox());
        assertEquals(4.0, ((HitShape.Sphere) crater.hitbox()).radius());
        assertEquals(new HitShape.Sphere(3.5, 0.0), fall.hitbox());
        assertEquals(new HitShape.Line(2.5, 2.0, 2.5), move(data, "ram").hitbox());
        assertTrue(crater.traits().effect(id("crater")).isPresent(), "the Cratered ground");
    }

    @Test
    void theLoopIsSixtySixTicksFortyPointSevenDamageTwelvePointThreeDps() {
        CombatData data = maul();
        WeaponDef maul = data.weapon(id("comet_maul"));
        int ticks = 0;
        double damage = 0.0;
        for (ResourceLocation id : maul.combo()) {
            MoveDef def = data.move(id);
            ticks += def.timing().total();
            damage += HitResolver.damage(maul, def, def.hit().mv(), false, false, StatBlock.ZERO);
        }
        assertEquals(66, ticks);
        assertEquals(40.7, damage, 1e-9);
        assertEquals(12.3, damage / (ticks / 20.0), 0.05);
    }

    @Test
    void theGravityWellIsRootedAndDashCancellableFromItsTwelfthTick() {
        MoveDef well = move(maul(), "gravity_well");
        assertEquals(MoveKind.ABILITY, well.kind());
        assertEquals(8, well.timing().startup());
        assertEquals(31, well.timing().active(), "30 ticks of pull, then the Collapse");
        assertEquals(2.0, well.hit().mv(), 1e-9);
        assertEquals(45.0, well.hit().impact(), 1e-9);
        assertTrue(well.traits().root());
        assertEquals(List.of(new MoveTraits.CancelWindow(MoveTraits.CancelBy.DASH, MoveTraits.Stage.ACTIVE, 11)), well.traits().cancels());
        MoveEffect effect = well.traits().effect(id("gravity_well")).orElseThrow();
        assertEquals(6.0, effect.param("radius", 0));
        assertEquals(0.12, effect.param("pull", 0));
        assertEquals(30, effect.intParam("pull_ticks", 0));
        assertEquals(3.0, effect.param("collapse_radius", 0));
        assertEquals(0.6, effect.param("early_scale", 0));
    }

    @Test
    void aFullerChargeSwingsDeeper() {
        MoveDef crater = move(maul(), "crater");
        assertEquals(MoveSound.CHARGE_PITCH_LOW, MoveSound.swingPitch(crater, 2.5), 1e-6);
        assertEquals(MoveSound.CHARGE_PITCH_FULL, MoveSound.swingPitch(crater, 3.5), 1e-6);
        float mid = MoveSound.swingPitch(crater, 3.0);
        assertTrue(mid < MoveSound.CHARGE_PITCH_LOW && mid > MoveSound.CHARGE_PITCH_FULL);
        assertEquals(1.0f, MoveSound.swingPitch(move(maul(), "l1"), 1.0));
    }
}
