package com.cosmicbreach.client.lift;

import java.util.List;
import java.util.Map;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The trails behind the players the rescue lift carries, as a client keeps them: who the server listed, where each one's feet were in
 * the last ticks, and the trail to draw at a frame between ticks. Pure, so a pause, a long ride and leaving the Drift can be run here.
 */
class RiderTrailsTest {
    private static final Vec3 ORIGIN = new Vec3(0, 0, 0);

    /** The points of the trail of rider 1 as they would be drawn in a frame after game tick {@code gameTime} has passed (all recorded points are behind the rider by then). */
    private static List<Vec3> recorded(RiderTrails trails, long gameTime) {
        List<RiderTrails.Trail> drawn = trails.drawn(gameTime + 1, 0.0, Map.of());
        return drawn.isEmpty() ? List.of() : drawn.get(0).points();
    }

    @Test
    void aTrailGrowsByOnePointForEachTickOfTheGameClock() {
        RiderTrails trails = new RiderTrails();
        for (long t = 100; t < 110; t++) {
            trails.note(t, Map.of(1, new Vec3(0, t - 100, 0)));
        }
        assertEquals(10, recorded(trails, 109).size());
    }

    @Test
    void aPausedGameDoesNotGrowTheHistory() {
        // the client keeps ticking while the level's clock stands still (a paused singleplayer game, /tick freeze): one point a tick of the
        // game's clock, not one a tick of the client's, or a pause mid-rescue would add twenty points a second to every rider
        RiderTrails trails = new RiderTrails();
        for (int i = 0; i < 5_000; i++) {
            trails.note(100, Map.of(1, new Vec3(0, 5, 0)));
        }
        assertEquals(1, recorded(trails, 100).size());
        trails.note(101, Map.of(1, new Vec3(0, 6, 0)));
        assertEquals(2, recorded(trails, 101).size(), "and it picks up again when the clock does");
    }

    @Test
    void aPointIsForgottenOnceItIsOlderThanATrail() {
        RiderTrails trails = new RiderTrails();
        trails.note(100, Map.of(1, ORIGIN));
        trails.note(110, Map.of(1, new Vec3(0, 7, 0)));
        assertEquals(2, recorded(trails, 110).size());
        trails.note(100 + StreamLook.TRAIL_TICKS + 1, Map.of(1, new Vec3(0, 14, 0)));
        assertEquals(2, recorded(trails, 121).size(), "the point of tick 100 is gone, the other two stay");
        trails.note(500, Map.of());
        assertTrue(trails.drawn(501, 0.0, Map.of()).isEmpty(), "and a trail whose rider is no longer carried fades away entirely");
    }

    @Test
    void leavingTheDriftForgetsWhoWasListedAndEveryTrail() {
        // a client that left while someone was carried must not keep drawing a trail behind them when it comes back
        RiderTrails trails = new RiderTrails();
        trails.list(List.of(5, 8));
        trails.note(100, Map.of(5, ORIGIN, 8, ORIGIN));
        assertEquals(java.util.Set.of(5, 8), trails.listed());
        trails.clear();
        assertTrue(trails.listed().isEmpty());
        assertTrue(trails.drawn(101, 0.0, Map.of(5, ORIGIN)).isEmpty());
    }

    @Test
    void theFrameShowsTheRiderWhereTheyAreWithTheTrailBehindThem() {
        RiderTrails trails = new RiderTrails();
        trails.note(99, Map.of(1, new Vec3(0, 0.0, 0)));
        trails.note(100, Map.of(1, new Vec3(0, 0.7, 0)));
        // a frame after tick 100, half way to the next: the rider is drawn half way from where they were after tick 99 to where they are
        // after tick 100 (the game clock still reads 100, the stamp of the point just noted)
        RiderTrails.Trail trail = trails.drawn(100, 0.5, Map.of(1, new Vec3(0, 0.35, 0))).get(0);
        assertEquals(List.of(new Vec3(0, 0.0, 0), new Vec3(0, 0.35, 0)), trail.points(), "the point noted this tick lies ahead of the drawn rider and is left out");
        assertEquals(0.5, trail.ages().get(0), 1e-9);
        assertEquals(0.0, trail.ages().get(1), 1e-9);
    }

    @Test
    void aRiderNoLongerCarriedHasNoHeadButTheTrailStillFades() {
        RiderTrails trails = new RiderTrails();
        trails.note(99, Map.of(1, ORIGIN));
        trails.note(100, Map.of(1, new Vec3(0, 0.7, 0)));
        RiderTrails.Trail trail = trails.drawn(105, 0.25, Map.of()).get(0);
        assertEquals(2, trail.points().size());
        assertTrue(trail.ages().get(0) > trail.ages().get(1), "older toward the tail");
    }

    @Test
    void aTrailsHistoryNeverReachesAheadOfTheInterpolatedRider() {
        // the rider drawn between two ticks is the state after the previous tick, partial of the way to the state after this one; a point
        // recorded at the end of this tick is that second state, which they have not reached, and is left out (kept, it made the trail
        // double back under them and turned its head ramp upside down)
        long now = 1000;
        assertTrue(RiderTrails.notReached(1000, now), "recorded this very tick");
        assertFalse(RiderTrails.notReached(999, now), "recorded a tick ago: the rider's own starting point");
        for (double partial = 0.0; partial < 1.0; partial += 0.25) {
            assertEquals(partial, RiderTrails.trailAge(999, now, partial), 1e-9, "the previous state is partial ticks behind the drawn rider");
            assertEquals(partial + 1.0, RiderTrails.trailAge(998, now, partial), 1e-9, "and each tick further back is a tick older");
        }
    }
}
