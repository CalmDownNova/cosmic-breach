package com.cosmicbreach.entity.shardling;

import com.cosmicbreach.entity.shardling.ShardlingMoves.Event;
import com.cosmicbreach.entity.shardling.ShardlingMoves.Phase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShardlingMovesTest {
    /** The phase at the end of each server tick, starting with the tick the attack began on. */
    private static List<Phase> run(ShardlingMoves moves, int ticks) {
        List<Phase> phases = new ArrayList<>();
        phases.add(moves.phase());
        for (int i = 1; i < ticks; i++) {
            moves.tick();
            phases.add(moves.phase());
        }
        return phases;
    }

    private static long count(List<Phase> phases, Phase phase) {
        return phases.stream().filter(p -> p == phase).count();
    }

    @Test
    void splinterLungeTimeline() {
        ShardlingMoves moves = new ShardlingMoves();
        assertTrue(moves.startLunge());
        List<Phase> phases = run(moves, 40);
        assertEquals(12, count(phases, Phase.TELL), "12 ticks of telegraph");
        assertEquals(4, count(phases, Phase.LUNGE), "4 active ticks");
        assertEquals(16, count(phases, Phase.RECOVER), "16 ticks of recovery");
        assertEquals(Phase.TELL, phases.get(11));
        assertEquals(Phase.LUNGE, phases.get(12), "it launches 12 ticks after the telegraph began");
        assertEquals(Phase.NONE, phases.get(32));
    }

    @Test
    void onlyTheActiveTicksAreParryableAndOnlyRecoveryTakesExtraDamage() {
        ShardlingMoves moves = new ShardlingMoves();
        moves.startLunge();
        int parryable = 0;
        int vulnerable = 0;
        List<Event> events = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            Event e = moves.tick();
            if (e != Event.NONE) {
                events.add(e);
            }
            if (moves.parryable()) {
                parryable++;
                assertEquals(Phase.LUNGE, moves.phase());
            }
            if (moves.damageTakenMultiplier() > 1f) {
                vulnerable++;
                assertEquals(1.25f, moves.damageTakenMultiplier());
                assertEquals(Phase.RECOVER, moves.phase());
            }
        }
        assertEquals(4, parryable);
        assertEquals(16, vulnerable);
        assertEquals(List.of(Event.LAUNCHED, Event.RECOVERING, Event.ENDED), events);
    }

    @Test
    void shardSpitTimeline() {
        ShardlingMoves moves = new ShardlingMoves();
        assertTrue(moves.startSpit());
        List<Phase> phases = run(moves, 30);
        assertEquals(10, count(phases, Phase.SPIT_TELL), "10 ticks of telegraph");
        assertEquals(12, count(phases, Phase.SPIT), "a 12 tick punish window");
        assertEquals(Phase.NONE, phases.get(22));
        ShardlingMoves again = new ShardlingMoves();
        again.startSpit();
        for (int i = 0; i < 40; i++) {
            again.tick();
            assertFalse(again.parryable(), "the spit is never parryable");
            assertEquals(1f, again.damageTakenMultiplier());
        }
    }

    @Test
    void theNeedlesFlyWhenTheTelegraphEnds() {
        ShardlingMoves moves = new ShardlingMoves();
        moves.startSpit();
        int firedOn = -1;
        for (int i = 1; i <= 20 && firedOn < 0; i++) {
            if (moves.tick() == Event.FIRED) {
                firedOn = i;
            }
        }
        assertEquals(ShardlingMoves.SPIT_TELL_TICKS, firedOn);
    }

    @Test
    void aNeedleCannotLandDuringItsShardlingsNextLunge() {
        // A needle lives ShardNeedle.MAX_AGE ticks; its owner's next lunge can only be active after the spit's
        // punish window and a whole lunge telegraph, so the lunge's parry flag never covers a needle hit.
        assertTrue(ShardNeedle.MAX_AGE < ShardlingMoves.SPIT_TICKS + ShardlingMoves.TELL_TICKS);
    }

    @Test
    void staggerBreaksOffAnythingForItsLength() {
        for (boolean spit : new boolean[] {false, true}) {
            for (int into = 0; into < 25; into++) {
                ShardlingMoves moves = new ShardlingMoves();
                if (spit) {
                    moves.startSpit();
                } else {
                    moves.startLunge();
                }
                for (int i = 0; i < into; i++) {
                    moves.tick();
                }
                moves.stagger(20);
                assertTrue(moves.staggered());
                assertFalse(moves.attacking());
                assertFalse(moves.parryable());
                List<Phase> phases = run(moves, 30);
                assertEquals(20, count(phases, Phase.STAGGER));
                assertEquals(0, count(phases, Phase.LUNGE) + count(phases, Phase.SPIT), "the attack never resumes");
                assertEquals(Phase.NONE, moves.phase());
            }
        }
    }

    @Test
    void restsAfterAttacksAndStaggersAndTheWarmUpPace() {
        assertEquals(40, ShardlingMoves.restAfter(false, 1f, 0));
        assertEquals(70, ShardlingMoves.restAfter(false, 1f, ShardlingMoves.REST_SPREAD));
        assertEquals(70, ShardlingMoves.restAfter(false, 1f, 999), "the roll is clamped");
        assertEquals(20, ShardlingMoves.restAfter(true, 1f, 17), "half after a stagger, whatever the roll");
        assertEquals(60, ShardlingMoves.restAfter(false, 1.5f, 0), "the warm-up rests 50% longer");
        assertEquals(105, ShardlingMoves.restAfter(false, 1.5f, ShardlingMoves.REST_SPREAD));
        assertEquals(30, ShardlingMoves.restAfter(true, 1.5f, 0));
    }

    @Test
    void attacksOnlyStartFromIdle() {
        ShardlingMoves moves = new ShardlingMoves();
        assertTrue(moves.startLunge());
        assertFalse(moves.startSpit());
        assertFalse(moves.startLunge());
        moves.stagger(5);
        assertFalse(moves.startLunge(), "not while staggered");
        for (int i = 0; i < 5; i++) {
            moves.tick();
        }
        assertTrue(moves.idle());
        assertTrue(moves.startSpit());
    }
}
