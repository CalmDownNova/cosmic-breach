package com.cosmicbreach.guardian.leviathan;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.guardian.AttackPicker;
import com.cosmicbreach.guardian.RewardTable;
import com.cosmicbreach.guardian.leviathan.LeviathanTactics.Attack;
import com.cosmicbreach.guardian.leviathan.LeviathanTactics.Situation;
import java.util.List;
import java.util.Random;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Leviathan's rules: attack choice, the Song of Pulling, the Moorage's clock and lines, the rewards. */
class LeviathanRulesTest {
    // ------------------------------------------------------------------ attack choice

    private static List<Attack> kinds(Situation s) {
        return LeviathanTactics.options(s).stream().map(AttackPicker.Option::attack).toList();
    }

    @Test
    void itDivesOnlyAtATargetAheadSingsOnlyAtOneInFrontAndFlicksAtATailHugger() {
        assertEquals(List.of(Attack.DIVE), kinds(new Situation(false, Math.toRadians(80), false, false)));
        assertEquals(List.of(), kinds(new Situation(false, Math.toRadians(10), false, false)), "not a target just behind the head");
        assertEquals(List.of(Attack.SONG), kinds(new Situation(false, Math.toRadians(250), true, false)));
        assertEquals(List.of(Attack.FLICK), kinds(new Situation(false, Double.NaN, false, true)));
        assertEquals(List.of(Attack.SHED), kinds(new Situation(true, Double.NaN, false, false)), "Scale Shed joins in phase 2");
        AttackPicker<Attack> picker = new AttackPicker<>();
        Attack first = picker.pick(LeviathanTactics.options(new Situation(false, Math.toRadians(80), true, true)), 0, () -> 0.5);
        assertEquals(Attack.FLICK, first, "the flick answers a player at the tail first");
        assertEquals(240, LeviathanTactics.cooldown(Attack.DIVE, false));
        assertEquals(200, LeviathanTactics.cooldown(Attack.DIVE, true), "every 10 s in phase 2");
        assertEquals(320, LeviathanTactics.cooldown(Attack.SONG, false));
        assertEquals(120, LeviathanTactics.cooldown(Attack.FLICK, false));
    }

    @Test
    void neverTheSameAttackTwiceInARow() {
        AttackPicker<Attack> picker = new AttackPicker<>();
        Random r = new Random(5);
        Attack last = null;
        int picked = 0;
        for (long now = 0; now < 20_000; now += 60) {
            Situation s = new Situation(now > 10_000, Math.toRadians(r.nextInt(360)), r.nextBoolean(), r.nextInt(4) == 0);
            List<AttackPicker.Option<Attack>> options = LeviathanTactics.options(s);
            Attack a = picker.pick(options, now, r::nextDouble);
            if (a == null) {
                continue;
            }
            assertNotEquals(last, a, "at " + now);
            picker.used(a, LeviathanTactics.cooldown(a, s.phaseTwo()), now);
            last = a;
            picked++;
        }
        assertTrue(picked > 100, "picked " + picked);
    }

    // ------------------------------------------------------------------ the Song of Pulling

    @Test
    void theSongPullsTheConeTowardTheMouthRisingFromSixToFourteenHundredths() {
        SongPull.Cone cone = new SongPull.Cone(new Vec3(0, 0, 0), new Vec3(0, 0, 2));
        assertTrue(SongPull.inCone(cone, new Vec3(0, 0, 20)));
        assertTrue(SongPull.inCone(cone, new Vec3(Math.tan(Math.toRadians(29)) * 10, 0, 10)), "just inside 30 degrees");
        assertFalse(SongPull.inCone(cone, new Vec3(Math.tan(Math.toRadians(31)) * 10, 0, 10)), "just outside");
        assertFalse(SongPull.inCone(cone, new Vec3(0, 0, 31)), "past 30 blocks");
        assertFalse(SongPull.inCone(cone, new Vec3(0, 0, -5)), "behind the mouth");
        assertEquals(0.06, SongPull.strength(0), 1e-9);
        assertEquals(0.10, SongPull.strength(20), 1e-9);
        assertEquals(0.14, SongPull.strength(40), 1e-9);
        assertEquals(0.14, SongPull.strength(59), 1e-9);
        Vec3 step = SongPull.step(cone, new Vec3(0, 0, 10), 0.1, false, false, false);
        assertEquals(-0.1, step.z, 1e-9);
        assertEquals(-0.2, SongPull.step(cone, new Vec3(0, 0, 10), 0.1, true, false, false).z, 1e-9, "elytra fliers twice as hard");
        assertEquals(Vec3.ZERO, SongPull.step(cone, new Vec3(0, 0, 10), 0.1, false, true, false), "a dash breaks it");
        assertEquals(Vec3.ZERO, SongPull.step(cone, new Vec3(0, 0, 10), 0.1, false, false, true), "rock between blocks it");
        assertEquals(Vec3.ZERO, SongPull.step(cone, new Vec3(20, 0, 2), 0.1, false, false, false), "nothing outside the cone");
        Vec3 close = new Vec3(0, 0, LeviathanMoves.BITE_REACH * 0.5 + 0.05);
        assertTrue(SongPull.step(cone, close, 0.14, false, false, false).length() <= 0.05 + 1e-9, "never past the bite");
        assertTrue(SongPull.bites(new Vec3(0, 1, 0), new AABB(-0.3, 0, 2.0, 0.3, 1.8, 2.6)));
        assertFalse(SongPull.bites(new Vec3(0, 1, 0), new AABB(-0.3, 0, 3.0, 0.3, 1.8, 3.6)));
        assertTrue(SongPull.pulling(LeviathanMoves.SONG_TELL) && !SongPull.pulling(LeviathanMoves.SONG_TELL - 1)
                && !SongPull.pulling(LeviathanMoves.SONG_TELL + LeviathanMoves.SONG_PULL));
    }

    // ------------------------------------------------------------------ Moorage

    @Test
    void aMoorageHoldsThreeHundredTicksAndShuddersEveryEighty() {
        StringBuilder shudders = new StringBuilder();
        StringBuilder ripples = new StringBuilder();
        for (int t = 0; t < Moorage.COILED; t++) {
            if (Moorage.shudders(t)) {
                shudders.append(t).append(' ');
            }
            if (Moorage.rippleStarts(t)) {
                ripples.append(t).append(' ');
            }
        }
        assertEquals("80 160 240 ", shudders.toString());
        assertEquals("64 144 224 ", ripples.toString(), "each told 16 ticks ahead");
        assertEquals(3, Moorage.shudderCount());
        assertEquals(0.5, Moorage.ripple(72), 1e-9);
        assertEquals(-1.0, Moorage.ripple(40), 1e-9);
        assertFalse(Moorage.tearsFree(299));
        assertTrue(Moorage.tearsFree(300));
        assertTrue(Moorage.SHUDDER_TELL >= LeviathanMoves.MIN_TELEGRAPH);
    }

    @Test
    void healthCantPassAMoorageLineInOneHit() {
        assertEquals(350f, Moorage.clamp(400f, 300f, 700f, 0), 1e-4, "held at half");
        assertEquals(300f, Moorage.clamp(349f, 300f, 700f, 1), 1e-4, "the first line is behind it");
        assertEquals(140f, Moorage.clamp(200f, 100f, 700f, 1), 1e-4, "held at a fifth");
        assertEquals(50f, Moorage.clamp(120f, 50f, 700f, 2), 1e-4, "both done: nothing held");
        assertEquals(0.5, Moorage.nextLine(0), 1e-9);
        assertEquals(0.2, Moorage.nextLine(1), 1e-9);
        assertEquals(-1, Moorage.nextLine(2), 1e-9);
    }

    @Test
    void theFirstMoorageClosesItsPlatesOnceItHasLostAFifth() {
        assertEquals(210f, Moorage.hold(350f, 150f, 700f, 1), 1e-4, "held at 30%");
        assertEquals(300f, Moorage.hold(350f, 300f, 700f, 1), 1e-4, "above the floor: untouched");
        assertTrue(Moorage.platesClose(350f, 210f, 700f, 1));
        assertFalse(Moorage.platesClose(350f, 220f, 700f, 1));
        assertEquals(50f, Moorage.hold(140f, 50f, 700f, 2), 1e-4, "the last one holds nothing back");
        assertFalse(Moorage.platesClose(140f, 0f, 700f, 2));
    }

    // ------------------------------------------------------------------ rewards

    @Test
    void eachParticipantGetsThePearlTheHaloThreeScalesAndNineThousandOnAFirstKill() {
        RewardTable.Reward first = LeviathanLoot.TABLE.roll(true, () -> 0.99);
        assertEquals(9000, first.xp());
        assertEquals(1, first.statPoints());
        assertTrue(first.firstKill());
        assertEquals(List.of(new RewardTable.Drop(CosmicBreach.id("leviathan_pearl"), 1), new RewardTable.Drop(CosmicBreach.id("halo_of_nine"), 1),
                new RewardTable.Drop(CosmicBreach.id("leviathan_scale"), 3)), first.drops());
        RewardTable.Reward repeat = LeviathanLoot.TABLE.roll(false, () -> 0.99);
        assertEquals(1800, repeat.xp());
        assertEquals(0, repeat.statPoints());
        assertEquals(List.of(new RewardTable.Drop(CosmicBreach.id("leviathan_scale"), 3)), repeat.drops(), "unlucky: only scales");
        RewardTable.Reward lucky = LeviathanLoot.TABLE.roll(false, () -> 0.05);
        assertEquals(3, lucky.drops().size(), "a lucky repeat: the pearl (35%) and the halo (10%) too");
        int pearls = 0;
        Random r = new Random(9);
        for (int i = 0; i < 20_000; i++) {
            for (RewardTable.Drop d : LeviathanLoot.TABLE.roll(false, r::nextDouble).drops()) {
                if (d.item().getPath().equals("leviathan_pearl")) {
                    pearls++;
                }
            }
        }
        assertEquals(0.35, pearls / 20_000.0, 0.02);
    }
}
