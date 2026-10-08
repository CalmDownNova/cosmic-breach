package com.cosmicbreach.entity.gyre;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cosmicbreach.entity.gyre.GyreModes.Mode;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * The Gyre Knight's fair window for a player on foot (1.1, Aetheria 1.1 Design section 3): the dive's telegraphed commit
 * and the two seconds it hangs in reach, and the slow stunned sink that never drops it into the gap.
 */
class GyreDiveTest {
    @Test
    void theDiveTellsCommitsAndHangsInReach() {
        assertTrue(GyreModes.DIVE_TELL >= 18 && GyreModes.DIVE_TELL <= 22, "about a second of tell");
        assertTrue(GyreModes.DIVE_HANG >= 36 && GyreModes.DIVE_HANG <= 44, "about two seconds in reach");
        assertTrue(GyreModes.DIVE_STAND_OFF + 0.45 < 3.0, "its body within a sword's 3 blocks while it hangs");
        long end = 30;
        assertFalse(GyreModes.done(Mode.DIVE, end + GyreModes.DIVE_STRIKE + GyreModes.DIVE_HANG - 1, end));
        assertTrue(GyreModes.done(Mode.DIVE, end + GyreModes.DIVE_STRIKE + GyreModes.DIVE_HANG, end));
        assertTrue(GyreModes.diveStriking(end, end) && !GyreModes.diveHanging(end, end));
        assertTrue(GyreModes.diveHanging(end + GyreModes.DIVE_STRIKE, end));
        assertFalse(GyreModes.done(Mode.DIVE, 500, -1), "still dropping: not over");
        assertTrue(GyreModes.ringRadius(Mode.DIVE, GyreModes.DIVE_TELL, -1) > GyreModes.SHIELD_RADIUS + 0.9, "the rings swell through the tell");
        assertTrue(GyreModes.humPitch(Mode.DIVE, 2, -1) < GyreModes.humPitch(Mode.DIVE, 18, -1), "a rising call");
        assertEquals(GyreModes.SHIELD_RADIUS, GyreModes.ringRadius(Mode.DIVE, end + GyreModes.DIVE_STRIKE + 5, end), 1e-9,
                "tight and slow while it hangs");
    }

    @Test
    void itDivesAfterTwoOrThreeVolleysAtAPlayerOnFoot() {
        assertEquals(2, GyreModes.VOLLEYS_MIN);
        assertEquals(3, GyreModes.VOLLEYS_MAX);
        assertFalse(GyreModes.diveDue(1, 2, true, 10));
        assertTrue(GyreModes.diveDue(2, 2, true, 10));
        assertFalse(GyreModes.diveDue(3, 3, false, 10), "never at a rider: the mount stays the stronger option");
        assertFalse(GyreModes.diveDue(3, 3, true, 30), "too far");
        Vec3 spot = GyreModes.standOff(new Vec3(0, 200, 0), new Vec3(5, 208, 0));
        assertEquals(GyreModes.DIVE_STAND_OFF, Math.hypot(spot.x, spot.z), 1e-9);
        assertEquals(200.0, spot.y, 1e-9, "level with the target's feet");
        assertTrue(spot.x > 0, "on the side it came from");
    }

    @Test
    void aPlayerShovedOrDodgingStillHasItHangingInReach() {
        // found in the first run in the game: the cut's own knockback (0.4 a tick away, 0.91 a tick of air drag, and in the
        // Drift's low gravity the player stays up) carries the player out of reach before the hang even begins, so the hang
        // follows its target. A player shoved by the cut:
        Vec3 player = new Vec3(0, 200, 0);
        Vec3 knight = GyreModes.standOff(player, new Vec3(5, 208, 0));
        double worst = 0;
        double v = 0.4;
        for (int t = 0; t < GyreModes.DIVE_STRIKE + GyreModes.DIVE_HANG; t++) {
            player = player.add(-v, 0, 0);
            v *= 0.91;
            Vec3 step = GyreModes.hangVelocity(knight, player);
            assertTrue(step.length() <= GyreModes.DIVE_SPEED + 1e-9, "never faster than its dive: " + step.length());
            knight = knight.add(step);
            worst = Math.max(worst, Math.hypot(knight.x - player.x, knight.z - player.z));
        }
        assertTrue(worst - 0.45 <= 3.0, "its body never more than a sword's 3 blocks off while it is shoved: " + worst);
        assertEquals(GyreModes.DIVE_STAND_OFF, Math.hypot(knight.x - player.x, knight.z - player.z), 0.15, "and it is back at its stand-off by the end");
        assertEquals(player.y, knight.y, 1e-9, "level with them");
        // a player who dashes five blocks off and keeps going (0.28 a tick): it keeps up
        player = new Vec3(0, 200, 0);
        knight = GyreModes.standOff(player, new Vec3(0, 208, 5));
        for (int t = 0; t < GyreModes.DIVE_HANG; t++) {
            player = player.add(0.28, 0, 0);
            knight = knight.add(GyreModes.hangVelocity(knight, player));
        }
        assertTrue(Math.hypot(knight.x - player.x, knight.z - player.z) - 0.45 <= 3.0, "still in reach of one walking away");
        // knocked up as well, or standing lower: it comes level with them
        assertTrue(GyreModes.hangVelocity(new Vec3(2.2, 195, 0), new Vec3(0, 200, 0)).y > 0, "up to a player who rose");
        assertTrue(GyreModes.hangVelocity(new Vec3(2.2, 205, 0), new Vec3(0, 200, 0)).y < 0, "down to one who is lower");
        assertEquals(0.0, GyreModes.hangVelocity(GyreModes.standOff(new Vec3(0, 200, 0), new Vec3(3, 203, 1)), new Vec3(0, 200, 0)).length(), 1e-9,
                "at its stand-off it holds still");
    }

    @Test
    void aBlockedLineToTheStandOffSkipsTheDiveAndKeepsItDue() {
        assertTrue(GyreModes.diveDue(2, 2, true, 10, true));
        assertFalse(GyreModes.diveDue(2, 2, true, 10, false), "rock between the Knight and the spot beside the player: no dive now");
        assertFalse(GyreModes.diveDue(1, 2, true, 10, true), "the other rules still hold");
        // the count is the caller's and is not spent by a skipped dive: still due on the next pick, whatever came between
        assertTrue(GyreModes.diveDue(3, 2, true, 10, true), "a lance volley later the dive is still owed");
    }

    @Test
    void theStrikeCueSoundsOnceAboutFiveTicksBeforeTheCutAndNeverLaterThanTheCut() {
        for (double drop : new double[] {3.0, 5.0, 8.0, 12.0, 20.0}) {
            double left = drop;
            boolean given = false;
            int cueTick = -1;
            int cutTick = -1;
            for (int t = 0; t < 80 && cutTick < 0; t++) {
                if (GyreModes.strikeCueDue(left, given)) {
                    assertEquals(-1, cueTick, "once");
                    cueTick = t;
                    given = true;
                }
                left -= Math.min(left, GyreModes.DIVE_SPEED);        // the dive's steering, in a straight line
                if (left < 0.6) {
                    cutTick = t;
                }
            }
            assertTrue(cueTick >= 0 && cueTick <= cutTick, "the cue comes before the cut: drop " + drop);
            if (drop >= 6.0) {
                assertTrue(cutTick - cueTick >= 3 && cutTick - cueTick <= GyreModes.DIVE_CUE_TICKS, "3 to 5 ticks of warning on a long drop (" + (cutTick - cueTick) + ")");
            }
        }
        assertFalse(GyreModes.strikeCueDue(1.0, true), "never twice");
        assertFalse(GyreModes.strikeCueDue(GyreModes.DIVE_SPEED * GyreModes.DIVE_CUE_TICKS + 0.01, false), "not while it is still far");
    }

    @Test
    void aDescentThatMakesNoProgressEndsEarlyButOneThatFliesDoesNot() {
        assertTrue(GyreModes.diveStuck(0.0), "pressed against rock");
        assertTrue(GyreModes.diveStuck(GyreModes.DIVE_STUCK_PROGRESS - 0.01));
        assertFalse(GyreModes.diveStuck(GyreModes.DIVE_SPEED * GyreModes.DIVE_STUCK_TICKS * 0.5), "even a slowed descent flies on");
        assertFalse(GyreModes.diveStuck(GyreModes.DIVE_SPEED * GyreModes.DIVE_STUCK_TICKS), "full speed");
        assertTrue(GyreModes.DIVE_STUCK_TICKS * GyreModes.DIVE_STUCK_TICKS < GyreModes.DIVE_DESCENT_MAX, "it gives up well inside the descent's own cap");
    }

    @Test
    void aStunnedKnightSinksSlowlyToItsTargetsLevelNotIntoTheGap() {
        Vec3 target = new Vec3(0, 200, 0);
        Vec3 pos = new Vec3(5, 208.5, 0);
        Vec3 anchor = GyreModes.stunAnchor(pos, target);
        double lowest = pos.y;
        for (int t = 0; t < GyreModes.STUN; t++) {
            Vec3 v = GyreModes.stunVelocity(pos, anchor);
            assertTrue(-v.y <= GyreModes.STUN_SINK + 1e-9 && v.y <= 1e-9, "it sinks no faster than the cap and never rises");
            pos = pos.add(v);
            lowest = Math.min(lowest, pos.y);
        }
        assertTrue(pos.distanceTo(anchor) < 0.6, "beside its target before the stun ends: " + pos);
        assertTrue(lowest >= target.y - 1e-6, "never below its target's level");
        assertEquals(new Vec3(3, 230, 3), GyreModes.stunAnchor(new Vec3(3, 230, 3), null), "alone: it hangs where it is");
        assertEquals(230.0, GyreModes.stunAnchor(new Vec3(3, 230, 3), new Vec3(0, 240, 0)).y, 1e-9, "never up to a target above it");
    }
}
