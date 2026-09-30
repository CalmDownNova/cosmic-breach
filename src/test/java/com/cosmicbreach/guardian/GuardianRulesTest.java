package com.cosmicbreach.guardian;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.guardian.RewardTable.Drop;
import com.cosmicbreach.guardian.RewardTable.Reward;
import com.cosmicbreach.guardian.colossus.ColossusLoot;
import com.cosmicbreach.guardian.colossus.ColossusMoves;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The reusable guardian rules: health scaling, targeting, attack choice, the Break gauge, rewards and the lair clock. */
class GuardianRulesTest {
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);

    // ------------------------------------------------------------------ health

    @Test
    void healthScalesWithPlayersCappedAtFour() {
        assertEquals(420.0, GuardianHealth.scaled(ColossusMoves.BASE_HEALTH, 1), 1e-9);
        assertEquals(672.0, GuardianHealth.scaled(ColossusMoves.BASE_HEALTH, 2), 1e-9);
        assertEquals(1176.0, GuardianHealth.scaled(ColossusMoves.BASE_HEALTH, 4), 1e-9);
        assertEquals(1176.0, GuardianHealth.scaled(ColossusMoves.BASE_HEALTH, 7), 1e-9, "capped at four");
        assertEquals(420.0, GuardianHealth.scaled(ColossusMoves.BASE_HEALTH, 0), 1e-9, "never less than one player");
        assertEquals(40.0, GuardianHealth.scaled(ColossusMoves.SHARD_HEALTH, 1), 1e-9);
        assertEquals(88.0, GuardianHealth.scaled(ColossusMoves.SHARD_HEALTH, 3), 1e-9, "shards scale like the Colossus");
    }

    @Test
    void theFourPlayerColossusPoolHoldsWhatVanillaCannot() {
        GuardianHealth.Pool pool = GuardianHealth.Pool.scaled(ColossusMoves.BASE_HEALTH, 4);
        assertEquals(1176.0, pool.max(), 1e-9);
        assertTrue(pool.pastVanillaCap(), "1,176 is past vanilla's 1,024");
        assertEquals(GuardianHealth.Pool.MIRROR, pool.mirror(), 1e-3, "full: the whole mirror");
        pool.set(pool.left() - 100.0);
        assertEquals(1076.0, pool.left(), 1e-9, "a hit of 100 takes 100 real health");
        assertEquals(1000.0 * 1076.0 / 1176.0, pool.mirror(), 1e-3, "clients see its share of the mirror");
        pool.set(pool.max() * 0.5);
        assertEquals(588.0, pool.left(), 1e-9, "the Fracture line in real health");
        pool.set(5000.0);
        assertEquals(1176.0, pool.left(), 1e-9, "never past its full measure");
        pool.set(0.001);
        assertTrue(pool.mirror() > 0f, "alive: the mirror is never zero");
        pool.set(-3.0);
        assertEquals(0.0, pool.left(), 1e-9);
        assertEquals(0f, pool.mirror(), 0f, "dead: zero");
        pool.reset(ColossusMoves.BASE_HEALTH);
        assertEquals(420.0, pool.left(), 1e-9, "back to a dormant statue's 420");
        assertFalse(pool.pastVanillaCap());
    }

    @Test
    void theGuardiansThatCanPassVanillasCapUseAPool() {
        assertTrue(GuardianHealth.needsPool(ColossusMoves.BASE_HEALTH), "the Colossus, 1,176 for four");
        assertTrue(GuardianHealth.needsPool(com.cosmicbreach.guardian.heliarch.HeliarchMoves.BASE_HEALTH), "every Heliarch");
        assertFalse(GuardianHealth.needsPool(com.cosmicbreach.guardian.leviathan.LeviathanMoves.BASE_HEALTH), "the Leviathan, 616");
        assertFalse(GuardianHealth.needsPool(com.cosmicbreach.guardian.unsung.UnsungMoves.MASK_HEALTH), "an Unsung mask, 700");
    }

    // ------------------------------------------------------------------ targeting

    @Test
    void theTargetIsWhoeverDealtTheMostInTheLastTenSeconds() {
        BossTargeting t = new BossTargeting();
        t.recordDamage(A, 10, 0);
        t.recordDamage(B, 6, 100);
        t.recordDamage(B, 6, 150);
        List<BossTargeting.Candidate> both = List.of(new BossTargeting.Candidate(A, 4), new BossTargeting.Candidate(B, 100));
        assertEquals(Optional.of(B), t.choose(both, 160), "B dealt 12 against A's 10");
        assertEquals(Optional.of(B), t.choose(both, 200), "A's hit at tick 0 has aged out");
        assertEquals(0.0, t.recentDamage(A, 200), 1e-9);
        assertEquals(Optional.of(A), t.choose(both, 400), "nobody's damage is recent: the nearest");
        assertEquals(Optional.empty(), t.choose(List.of(), 400));
    }

    @Test
    void theTargetIsReCheckedEveryFortyTicks() {
        assertTrue(BossTargeting.checkDue(140, 100));
        assertFalse(BossTargeting.checkDue(139, 100));
        assertEquals(40, BossTargeting.CHECK_TICKS);
    }

    // ------------------------------------------------------------------ attack choice

    private enum Move { SLAM, SWEEP, BEAM, BURST }

    @Test
    void attacksWaitForTheirCooldownsAndDoNotRepeatExceptTheSlam() {
        AttackPicker<Move> p = new AttackPicker<>();
        List<AttackPicker.Option<Move>> options = List.of(
                AttackPicker.Option.weighted(Move.SLAM, 1, 80).asRepeatable(),
                AttackPicker.Option.weighted(Move.SWEEP, 1, 200));
        p.used(Move.SWEEP, 200, 0);
        assertEquals(Move.SLAM, p.pick(options, 10, () -> 0.99), "the sweep is cooling down");
        p.used(Move.SLAM, 80, 10);
        assertNull(p.pick(options, 50, () -> 0.0), "both cooling down");
        assertEquals(Move.SLAM, p.pick(List.of(options.get(0)), 90, () -> 0.0), "the slam may follow itself");
        p.used(Move.SWEEP, 200, 300);
        assertNull(p.pick(List.of(options.get(1)), 600, () -> 0.0), "the sweep never twice in a row");
    }

    @Test
    void aReadyPriorityAttackGoesFirst() {
        AttackPicker<Move> p = new AttackPicker<>();
        List<AttackPicker.Option<Move>> options = List.of(
                AttackPicker.Option.weighted(Move.SLAM, 3, 80).asRepeatable(),
                AttackPicker.Option.weighted(Move.BEAM, 0, 280).asPriority());
        assertEquals(Move.BEAM, p.pick(options, 0, () -> 0.0));
        p.used(Move.BEAM, 280, 0);
        assertEquals(Move.SLAM, p.pick(options, 100, () -> 0.0));
        p.used(Move.SLAM, 80, 100);
        assertEquals(Move.BEAM, p.pick(options, 280, () -> 0.0), "every 14 s");
    }

    @Test
    void weightsShareTheDraw() {
        AttackPicker<Move> p = new AttackPicker<>();
        List<AttackPicker.Option<Move>> options = List.of(
                AttackPicker.Option.weighted(Move.SLAM, 3, 80),
                AttackPicker.Option.weighted(Move.SWEEP, 1, 200));
        assertEquals(Move.SLAM, p.pick(options, 0, () -> 0.74));
        assertEquals(Move.SWEEP, p.pick(options, 0, () -> 0.76));
    }

    // ------------------------------------------------------------------ the Break gauge

    @Test
    void aParryAndABeamIntoTheCoreBreakIt() {
        BreakGauge g = new BreakGauge(ColossusMoves.BREAK_POISE);
        assertFalse(g.add(0, ColossusMoves.PARRY_GAUGE));
        assertEquals(0.4, g.fraction(0), 1e-9);
        assertTrue(g.add(80, ColossusMoves.CORE_HIT_GAUGE), "60 + 100 within 5 s");
        assertEquals(0.0, g.total(80), 1e-9, "a Break empties it");
    }

    @Test
    void theGaugeForgetsImpactAfterFiveSeconds() {
        BreakGauge g = new BreakGauge(150);
        g.add(0, 60);
        g.add(50, 26);
        assertEquals(86, g.total(99), 1e-9);
        assertEquals(26, g.total(100), 1e-9, "the parry's 60 ages out after 100 ticks");
        assertFalse(g.add(100, ColossusMoves.CORE_HIT_GAUGE), "26 + 100 is not yet a Break");
        assertTrue(g.add(110, 26), "one more combo is");
    }

    @Test
    void steadyHitsAloneNeverBreakIt() {
        // Meridian's combo at full uptime: Impact 6, 6 and 14 every 37 ticks
        BreakGauge g = new BreakGauge(ColossusMoves.BREAK_POISE);
        boolean broke = false;
        for (long t = 0; t < 2_000; t += 37) {
            broke |= g.add(t, 6) | g.add(t + 10, 6) | g.add(t + 21, 14);
        }
        assertFalse(broke);
    }

    // ------------------------------------------------------------------ rewards

    @Test
    void aFirstKillGivesTheHeartsTheXpAndAStatPoint() {
        Reward r = ColossusLoot.TABLE.roll(true, () -> 0.99);
        Map<String, Integer> drops = counts(r.drops());
        assertEquals(Map.of("prism_heart", 1, "heart_of_a_dying_star", 1), drops);
        assertEquals(3_000, r.xp());
        assertEquals(1, r.statPoints());
        assertTrue(r.firstKill());
    }

    @Test
    void repeatKillsGiveQuartzXpAndChances() {
        Reward none = ColossusLoot.TABLE.roll(false, () -> 0.99);
        assertEquals(600, none.xp());
        assertEquals(0, none.statPoints());
        assertEquals(Map.of("spire_quartz", 8), counts(none.drops()), "unlucky rolls still give quartz (at 0.99, the most)");
        Reward lucky = ColossusLoot.TABLE.roll(false, sequence(0.0, 0.0, 0.0, 0.0, 0.0));
        assertEquals(Map.of("prism_heart", 1, "heart_of_a_dying_star", 1, "spire_quartz", 4, "heartstone", 1), counts(lucky.drops()));
        // the Prism Heart comes 35% of the time: a roll of 0.34 gives it, 0.36 does not
        assertTrue(counts(ColossusLoot.TABLE.roll(false, sequence(0.34, 0.99, 0.0, 0.5, 0.99)).drops()).containsKey("prism_heart"));
        assertFalse(counts(ColossusLoot.TABLE.roll(false, sequence(0.36, 0.99, 0.0, 0.5, 0.99)).drops()).containsKey("prism_heart"));
        for (int i = 0; i < 200; i++) {
            java.util.Random random = new java.util.Random(i);
            int quartz = counts(ColossusLoot.TABLE.roll(false, random::nextDouble).drops()).getOrDefault("spire_quartz", 0);
            assertTrue(quartz >= 4 && quartz <= 8, "4 to 8 Spire Quartz, got " + quartz);
        }
    }

    // ------------------------------------------------------------------ the lair clock

    @Test
    void aKillRestsTheLairForTwentyMinutes() {
        LairClock clock = new LairClock();
        assertTrue(clock.armed(0), "a new lair is armed");
        clock.killed(1_000);
        assertFalse(clock.armed(1_000 + 23_999));
        assertTrue(clock.armed(1_000 + 24_000), "20 minutes later");
        assertEquals(12_000, clock.remaining(13_000));
    }

    @Test
    void itWakesForAnEchoOrAnUnattunedPlayerOnlyWhenArmed() {
        assertTrue(LairClock.wakes(true, false, true));
        assertTrue(LairClock.wakes(true, true, false));
        assertFalse(LairClock.wakes(true, false, false), "an attuned player walking in doesn't wake it");
        assertFalse(LairClock.wakes(false, true, true), "nothing wakes it while it rests");
    }

    private static Map<String, Integer> counts(List<Drop> drops) {
        return drops.stream().collect(Collectors.toMap(d -> d.item().getPath(), Drop::count));
    }

    private static DoubleSupplier sequence(double... values) {
        List<Double> list = new ArrayList<>();
        for (double v : values) {
            list.add(v);
        }
        int[] i = {0};
        return () -> list.get(Math.min(i[0]++, list.size() - 1));
    }

    @SuppressWarnings("unused")
    private static String ns() {
        return CosmicBreach.MOD_ID;
    }
}
