package com.cosmicbreach.progression;

import com.cosmicbreach.combat.core.Stat;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Attunement XP, levels and stat points (GDD sections 3.2 and 3.3). */
class AttunementTest {

    // ------------------------------------------------------------------ the curve

    @Test
    void xpForTheNextLevelMatchesTheTable() {
        int[][] table = {{2, 191}, {5, 609}, {10, 1_631}, {15, 2_955}, {20, 4_522}, {25, 6_300}, {30, 8_266}, {40, 12_699}};
        for (int[] row : table) {
            assertEquals(row[1], Attunement.xpToNext(row[0]), "XP from level " + row[0]);
        }
        assertEquals(100, Attunement.xpToNext(1));
        assertEquals(0, Attunement.xpToNext(50), "50 is the cap");
    }

    @Test
    void totalXpToReachMatchesTheTable() {
        assertEquals(0, Attunement.totalXpToReach(1));
        assertEquals(100, Attunement.totalXpToReach(2));
        assertEquals(1_051, Attunement.totalXpToReach(5));
        assertEquals(6_002, Attunement.totalXpToReach(10));
        assertEquals(16_698, Attunement.totalXpToReach(15));
        assertEquals(34_517, Attunement.totalXpToReach(20));
        assertEquals(60_604, Attunement.totalXpToReach(25));
        assertEquals(95_964, Attunement.totalXpToReach(30));
        assertEquals(198_049, Attunement.totalXpToReach(40));
    }

    /**
     * The GDD's table says 347,207 for level 50, but that is the unrounded sum (347,207.48). The
     * formula it states rounds every level, round(50 x L^1.5 + 50), and every other row of the table
     * uses those rounded steps (level 30's 95,964 would be 95,965 unrounded), so the rounded total is
     * the one that counts: 347,206.
     */
    @Test
    void totalXpToTheCapIsTheSumOfTheRoundedSteps() {
        assertEquals(347_206, Attunement.totalXpToReach(50));
        double unrounded = 0;
        for (int level = 1; level < 50; level++) {
            unrounded += 50 * Math.pow(level, 1.5) + 50;
        }
        assertEquals(347_207, (long) Math.floor(unrounded), "the GDD's figure is the unrounded sum");
    }

    @Test
    void xpCarriesThroughLevels() {
        Attunement tenth = Attunement.START.withXp(6_002);
        assertEquals(10, tenth.level());
        assertEquals(0, tenth.xp());
        assertEquals(6_002, tenth.totalXp());

        Attunement almost = Attunement.START.withXp(6_001);
        assertEquals(9, almost.level());
        assertEquals(Attunement.xpToNext(9) - 1, almost.xp());

        Attunement split = Attunement.START.withXp(60).withXp(60);
        assertEquals(2, split.level(), "two gifts of 60 pass 100");
        assertEquals(20, split.xp());
    }

    @Test
    void theCapStopsXp() {
        Attunement capped = Attunement.START.withXp(Integer.MAX_VALUE);
        assertEquals(50, capped.level());
        assertEquals(0, capped.xp());
        assertSame(capped, capped.withXp(1_000), "XP past the cap is dropped");
        assertEquals(50, Attunement.START.withXp(347_206).level());
        assertEquals(49, Attunement.START.withXp(347_205).level());
    }

    @Test
    void nothingOrLessIsNoChange() {
        assertSame(Attunement.START, Attunement.START.withXp(0));
        assertSame(Attunement.START, Attunement.START.withXp(-5));
    }

    // ------------------------------------------------------------------ points

    @Test
    void onePointPerLevelAboveOne() {
        assertEquals(0, Attunement.START.earnedPoints());
        assertEquals(9, Attunement.START.atLevel(10).earnedPoints());
        assertEquals(49, Attunement.START.atLevel(50).earnedPoints());
        for (int level = 1; level <= 50; level++) {
            assertEquals(level - 1, Attunement.START.atLevel(level).unspent());
        }
    }

    @Test
    void guardiansAndTheHeliarchBringTheTotalTo54() {
        Attunement a = Attunement.START.atLevel(50);
        for (int guardian = 0; guardian < 3; guardian++) {
            a = a.withBonusPoints(XpSource.GUARDIAN_FIRST_KILL_POINTS);
        }
        a = a.withBonusPoints(XpSource.HELIARCH_FIRST_KILL_POINTS);
        assertEquals(54, a.earnedPoints());
        assertEquals(54, a.unspent());
    }

    @Test
    void levelsGainedGiveTheirPoints() {
        Attunement a = Attunement.START.withXp(Attunement.totalXpToReach(20));
        assertEquals(20, a.level());
        assertEquals(19, a.unspent());
    }

    // ------------------------------------------------------------------ allocation

    @Test
    void allocationSpendsPoints() {
        Attunement a = Attunement.START.atLevel(21);
        Attunement spent = a.allocate(new Allocation(5, 10, 0, 3));
        assertNotNull(spent);
        assertEquals(new Allocation(5, 10, 0, 3), spent.spent());
        assertEquals(2, spent.unspent());
        Attunement more = spent.allocate(Allocation.of(Stat.ARCANE, 2));
        assertNotNull(more);
        assertEquals(0, more.unspent());
    }

    @Test
    void allocationRefusesWhatIsNotAllowed() {
        Attunement a = Attunement.START.atLevel(11); // 10 points
        assertNull(a.allocate(Allocation.ZERO), "nothing");
        assertNull(a.allocate(new Allocation(3, -1, 0, 0)), "a negative part");
        assertNull(a.allocate(Allocation.of(Stat.POWER, 11)), "more than there is to spend");
        assertNull(a.allocate(new Allocation(5, 5, 1, 0)), "more than there is to spend, spread");
        assertNotNull(a.allocate(new Allocation(5, 5, 0, 0)), "exactly everything");
    }

    @Test
    void thirtyIsTheMostOneAttributeTakes() {
        Attunement a = Attunement.START.atLevel(50);
        Attunement thirty = a.allocate(Allocation.of(Stat.RESILIENCE, 30));
        assertNotNull(thirty);
        assertNull(thirty.allocate(Allocation.of(Stat.RESILIENCE, 1)), "31 Resilience");
        assertNull(a.allocate(Allocation.of(Stat.POWER, 31)), "31 at once");
        assertNotNull(thirty.allocate(Allocation.of(Stat.POWER, 19)), "the rest elsewhere");
        assertNull(a.allocate(new Allocation(Integer.MAX_VALUE, Integer.MAX_VALUE, 2, 2)), "overflowing parts");
    }

    // ------------------------------------------------------------------ respec

    @Test
    void respecRefundsEveryPoint() {
        Attunement a = Attunement.START.atLevel(40).withBonusPoints(1).allocate(new Allocation(20, 10, 5, 4));
        assertNotNull(a);
        assertEquals(1, a.unspent());
        Attunement fresh = a.respec();
        assertEquals(Allocation.ZERO, fresh.spent());
        assertEquals(40, fresh.unspent());
        assertEquals(a.level(), fresh.level());
        assertEquals(a.xp(), fresh.xp());
        assertEquals(a.bonusPoints(), fresh.bonusPoints());
    }

    @Test
    void theFreeDraughtIsDueOnceAtLevelTen() {
        assertFalse(Attunement.START.atLevel(9).freeDraughtDue());
        Attunement ten = Attunement.START.withXp(6_002);
        assertTrue(ten.freeDraughtDue());
        assertFalse(ten.withFreeDraughtGiven().freeDraughtDue());
        assertFalse(ten.withFreeDraughtGiven().withXp(50_000).freeDraughtDue(), "never a second time");
    }

    // ------------------------------------------------------------------ test tools and validity

    @Test
    void loweringTheLevelBelowWhatIsSpentRefundsIt() {
        Attunement a = Attunement.START.atLevel(30).allocate(Allocation.of(Stat.AGILITY, 20));
        assertNotNull(a);
        assertEquals(Allocation.of(Stat.AGILITY, 20), a.atLevel(21).spent(), "20 of 20 still fits");
        assertEquals(Allocation.ZERO, a.atLevel(20).spent(), "20 of 19 doesn't");
    }

    @Test
    void settingStatsAddsBonusPointsWhenNeeded() {
        Attunement a = Attunement.START.withSpent(new Allocation(30, 20, 0, 30));
        assertEquals(new Allocation(30, 20, 0, 30), a.spent());
        assertEquals(80, a.bonusPoints());
        assertEquals(0, a.unspent());
        Attunement clamped = Attunement.START.atLevel(50).withSpent(new Allocation(45, -3, 0, 0));
        assertEquals(new Allocation(30, 0, 0, 0), clamped.spent());
        assertEquals(19, clamped.unspent());
    }

    @Test
    void impossibleValuesAreMadeValid() {
        Attunement a = new Attunement(0, -4, -2, new Allocation(40, -1, 0, 0), false);
        assertEquals(1, a.level());
        assertEquals(0, a.xp());
        assertEquals(0, a.bonusPoints());
        assertEquals(Allocation.ZERO, a.spent(), "30 spent of 0 earned goes back");
        Attunement high = new Attunement(99, 5, 0, Allocation.ZERO, false);
        assertEquals(50, high.level());
        assertEquals(0, high.xp());
        assertEquals(99, new Attunement(1, 150, 0, Allocation.ZERO, false).xp(), "XP stays under the next level's need");
    }

    // ------------------------------------------------------------------ persistence and sync

    @Test
    void savedStateComesBackTheSame() {
        Attunement a = Attunement.START.withXp(20_000).withBonusPoints(2).withFreeDraughtGiven();
        a = a.allocate(new Allocation(4, 3, 2, 1));
        assertNotNull(a);
        JsonElement json = Attunement.CODEC.encodeStart(JsonOps.INSTANCE, a).getOrThrow();
        assertEquals(a, Attunement.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void anEmptySaveIsAFreshStart() {
        assertEquals(Attunement.START, Attunement.CODEC.parse(JsonOps.INSTANCE, new com.google.gson.JsonObject()).getOrThrow());
    }

    @Test
    void theSyncCarriesEverything() {
        Attunement a = Attunement.START.atLevel(33).withXp(1_234).withBonusPoints(1).allocate(new Allocation(10, 12, 0, 5));
        assertNotNull(a);
        ByteBuf buf = Unpooled.buffer();
        Attunement.STREAM_CODEC.encode(buf, a);
        assertEquals(a, Attunement.STREAM_CODEC.decode(buf));
        assertFalse(buf.isReadable());
    }
}
