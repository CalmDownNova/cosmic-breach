package com.cosmicbreach.voice.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The triggers a fight raises on its own: thresholds, low health, falls, leaving and returning, riding, attack gaps. */
class FightWatchTest {
    static final UUID A = new UUID(0, 1);
    static final UUID B = new UUID(0, 2);
    static final Context BASE = Context.of(2, false, 0);

    static FightWatch.Sample in(UUID id, double health) {
        return new FightWatch.Sample(id, true, true, health, false, false);
    }

    static FightWatch.Sample out(UUID id) {
        return new FightWatch.Sample(id, false, true, 1.0, false, false);
    }

    static List<String> keys(List<FightWatch.Fired> fired) {
        List<String> out = new ArrayList<>();
        for (FightWatch.Fired f : fired) {
            out.add(f.trigger().key());
        }
        return out;
    }

    @Test
    void thresholdsFireOnceEachHighestFirstAndPhasesAreLeftToTheBoss() {
        FightWatch w = new FightWatch(Set.of(80, 60, 10), Set.of());
        assertEquals(List.of(), keys(w.tick(0, BASE, 0.85, List.of(in(A, 1)), Integer.MAX_VALUE)));
        assertEquals(List.of("hp_threshold:80", "hp_threshold:60"), keys(w.tick(1, BASE, 0.5, List.of(in(A, 1)), Integer.MAX_VALUE)));
        w.markThreshold(10);
        assertEquals(List.of(), keys(w.tick(2, BASE, 0.05, List.of(in(A, 1)), Integer.MAX_VALUE)), "the boss fired 10 itself");
        assertEquals(List.of(), keys(w.tick(3, BASE, 0.0, List.of(in(A, 1)), Integer.MAX_VALUE)));
    }

    @Test
    void lowHealthFiresAtAQuarterAndRearmsAfterHealingOrDying() {
        FightWatch w = new FightWatch(Set.of(), Set.of());
        List<FightWatch.Fired> low = w.tick(0, BASE, 1, List.of(in(A, 0.25)), Integer.MAX_VALUE);
        assertEquals(List.of("player_low_health"), keys(low));
        assertEquals(0.25, low.get(0).context().health(), 1e-9);
        assertEquals(List.of(), keys(w.tick(1, BASE, 1, List.of(in(A, 0.1)), Integer.MAX_VALUE)));
        assertEquals(List.of(), keys(w.tick(2, BASE, 1, List.of(in(A, 0.5)), Integer.MAX_VALUE)), "not healed past 60% yet");
        assertEquals(List.of(), keys(w.tick(3, BASE, 1, List.of(in(A, 0.6)), Integer.MAX_VALUE)));
        assertEquals(List.of("player_low_health"), keys(w.tick(4, BASE, 1, List.of(in(A, 0.2)), Integer.MAX_VALUE)));
        w.tick(5, BASE, 1, List.of(new FightWatch.Sample(A, true, false, 0, false, false)), Integer.MAX_VALUE);
        assertEquals(List.of("player_low_health"), keys(w.tick(6, BASE, 1, List.of(in(A, 0.2)), Integer.MAX_VALUE)), "a death rearms it");
    }

    @Test
    void aFallFiresOnceUntilTheyHaveStoodAgain() {
        FightWatch w = new FightWatch(Set.of(), Set.of());
        FightWatch.Sample falling = new FightWatch.Sample(A, true, true, 1, true, false);
        assertEquals(List.of("player_fell"), keys(w.tick(0, BASE, 1, List.of(falling), Integer.MAX_VALUE)));
        for (int t = 1; t < 10; t++) {
            assertEquals(List.of(), keys(w.tick(t, BASE, 1, List.of(falling), Integer.MAX_VALUE)));
        }
        for (int t = 10; t < 10 + FightWatch.FELL_REARM; t++) {
            w.tick(t, BASE, 1, List.of(in(A, 1)), Integer.MAX_VALUE);
        }
        assertEquals(List.of("player_fell"), keys(w.tick(50, BASE, 1, List.of(falling), Integer.MAX_VALUE)));
    }

    @Test
    void anEmptyArenaFiresEachAbsenceStepOnceAndAReturnSaysHowLongTheyWereAway() {
        FightWatch w = new FightWatch(Set.of(), Set.of(5));
        w.tick(0, BASE, 1, List.of(in(A, 1), in(B, 1)), Integer.MAX_VALUE);
        List<String> fired = new ArrayList<>();
        FightWatch.Fired left = null;
        for (int t = 1; t <= 120; t++) {
            for (FightWatch.Fired f : w.tick(t, BASE, 1, List.of(out(A), out(B)), Integer.MAX_VALUE)) {
                fired.add(f.trigger().key() + "@" + t);
                left = f;
            }
        }
        assertEquals(List.of("player_left@101"), fired, "5 s after the arena emptied, once");
        assertTrue(left.context().allGone());
        assertEquals(5, left.context().awaySeconds());
        List<FightWatch.Fired> back = w.tick(300, BASE, 1, List.of(in(A, 1), out(B)), Integer.MAX_VALUE);
        assertEquals(List.of("player_returned"), keys(back));
        assertEquals(15, back.get(0).context().awaySeconds());
        assertEquals(List.of(), keys(w.tick(301, BASE, 1, List.of(out(A), out(B)), Integer.MAX_VALUE)), "a new absence starts over");
        assertEquals(List.of(), keys(w.tick(310, BASE, 1, List.of(in(A, 1), out(B)), Integer.MAX_VALUE)), "ten ticks out is walking the edge");
    }

    @Test
    void ridingTauntsOnceASecondAndAGapFiresAsTheQuietReturns() {
        FightWatch w = new FightWatch(Set.of(), Set.of());
        FightWatch.Sample riding = new FightWatch.Sample(A, true, true, 1, false, true);
        int taunts = 0;
        for (int t = 1; t <= 40; t++) {
            for (FightWatch.Fired f : w.tick(t, BASE, 1, List.of(riding), 100)) {
                assertTrue(f.trigger().is(Trigger.TAUNT) && f.context().mounted());
                taunts++;
            }
        }
        assertEquals(2, taunts);
        assertEquals(List.of(), keys(w.tick(41, BASE, 1, List.of(in(A, 1)), 0)));
        assertEquals(List.of("attack_gap"), keys(w.tick(42, BASE, 1, List.of(in(A, 1)), 50)));
        assertEquals(List.of(), keys(w.tick(43, BASE, 1, List.of(in(A, 1)), 49)));
    }

    @Test
    void deathsAndHitsCarryTheirMoment() {
        FightWatch w = new FightWatch(Set.of(), Set.of());
        FightWatch.Fired death = w.death(BASE, true);
        assertEquals("player_death", death.trigger().key());
        assertTrue(death.context().lastAlive());
        FightWatch.Fired hit = w.hit(BASE, WeaponCategory.FORGE_TIER_3, "cosmicbreach:choir_astrolabe");
        assertEquals("weapon:forge_tier_3", hit.trigger().key());
        assertEquals("cosmicbreach:choir_astrolabe", hit.context().item());
    }
}
