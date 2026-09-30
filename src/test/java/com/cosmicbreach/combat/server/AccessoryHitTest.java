package com.cosmicbreach.combat.server;

import com.cosmicbreach.combat.core.Grade;
import com.cosmicbreach.combat.core.Stat;
import com.cosmicbreach.combat.core.StatBlock;
import com.cosmicbreach.combat.data.HitShape;
import com.cosmicbreach.combat.data.MoveDef;
import com.cosmicbreach.combat.data.MoveKind;
import com.cosmicbreach.combat.data.WeaponDef;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the accessories add to a hit (GDD 3.4, 5.2): a bonus to the crit multiplier's gear term (the Perihelion Loop's
 * +25% after a dash: a Meridian crit at zero Power goes from x1.5 to x1.75, still capped at 2.5) and plunges without
 * their height cap (the Gravity Loop: a 12-block Falling Star reaches motion value 2.6 instead of 2.4).
 */
class AccessoryHitTest {
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("cosmicbreach", path);
    }

    private static final WeaponDef MERIDIAN = new WeaponDef(5.0, Map.of(Stat.POWER, Grade.B, Stat.AGILITY, Grade.C), 3.5,
            List.of(id("meridian/l1")), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1);
    private static final MoveDef L1 = move(MoveKind.LIGHT, new MoveDef.Hit(1.0, 0, 0, 6, 6, 2, 1, 0));
    /** Falling Star: 1.4 + 0.1 a block, capped at 2.4. */
    private static final MoveDef.Hit FALLING_STAR = new MoveDef.Hit(1.4, 2.4, 0.1, 20, 6, 4, 1, 0);

    private static MoveDef move(MoveKind kind, MoveDef.Hit hit) {
        return new MoveDef(kind, new MoveDef.Timing(3, 2, 5), hit, new HitShape.Arc(3.5, 110, 2.5), Optional.empty(), id("anim"),
                0.0, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private List<HitModifiers.Modifier> registered = List.of();

    @BeforeEach
    void alone() {
        registered = HitModifiers.snapshotForTests();
        HitModifiers.restoreForTests(List.of());
    }

    @AfterEach
    void putBack() {
        HitModifiers.restoreForTests(registered);
    }

    @Test
    void theCritGearTermAddsToTheMultiplier() {
        assertEquals(7.5, HitResolver.damage(MERIDIAN, L1, 1.0, true, false, StatBlock.ZERO, 1.0, 0), 1e-9, "x1.5");
        assertEquals(8.75, HitResolver.damage(MERIDIAN, L1, 1.0, true, false, StatBlock.ZERO, 1.0, 0, 0.25), 1e-9,
                "x1.75 with the Perihelion Loop after a dash");
        assertEquals(5.0, HitResolver.damage(MERIDIAN, L1, 1.0, false, false, StatBlock.ZERO, 1.0, 0, 0.25), 1e-9,
                "no crit, no bonus");
        StatBlock power = new StatBlock(40, 0, 0, 0);
        double scaled = 5.0 * (1 + 0.55 * 40.0 / 70.0);
        assertEquals(scaled * 2.15, HitResolver.damage(MERIDIAN, L1, 1.0, true, false, power, 1.0, 0, 0.25), 1e-9,
                "1.5 + 0.40 + 0.25");
        assertEquals(scaled * 2.5, HitResolver.damage(MERIDIAN, L1, 1.0, true, false, power, 1.0, 0, 0.9), 1e-9,
                "capped at 2.5");
    }

    @Test
    void modifiersAddTheirCritBonusesAndAnyOneLiftsThePlungeCap() {
        assertEquals(0.0, HitModifiers.critMultiplierBonus(null, null), 1e-12);
        assertFalse(HitModifiers.uncapsPlunge(null));
        HitModifiers.register(new HitModifiers.Modifier() {
            @Override
            public double critMultiplierBonus(ServerPlayer attacker, LivingEntity target) {
                return 0.25;
            }
        });
        HitModifiers.register(new HitModifiers.Modifier() {
            @Override
            public double critMultiplierBonus(ServerPlayer attacker, LivingEntity target) {
                return 0.1;
            }

            @Override
            public boolean uncapsPlunge(ServerPlayer attacker) {
                return true;
            }
        });
        assertEquals(0.35, HitModifiers.critMultiplierBonus(null, null), 1e-12);
        assertTrue(HitModifiers.uncapsPlunge(null));
    }

    @Test
    void theGravityLoopTakesThePlungeCapAway() {
        assertEquals(2.4, HitResolver.plungeMv(FALLING_STAR, 12.0, false), 1e-9, "capped at 10 blocks");
        assertEquals(2.6, HitResolver.plungeMv(FALLING_STAR, 12.0, true), 1e-9, "1.4 + 0.1 x 12");
        assertEquals(4.4, HitResolver.plungeMv(FALLING_STAR, 30.0, true), 1e-9, "and it keeps growing");
        assertEquals(1.9, HitResolver.plungeMv(FALLING_STAR, 5.0, true), 1e-9, "under the cap nothing changes");
        assertEquals(1.4, HitResolver.plungeMv(FALLING_STAR, -3.0, true), 1e-9, "no negative fall");
    }
}
