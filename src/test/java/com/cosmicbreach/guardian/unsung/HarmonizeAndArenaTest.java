package com.cosmicbreach.guardian.unsung;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cosmicbreach.guardian.GuardianHealth;
import com.cosmicbreach.guardian.RewardTable;
import com.cosmicbreach.guardian.unsung.HarmonizeRules.Circle;
import com.cosmicbreach.guardian.unsung.HarmonizeRules.Spot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Harmonize's circles, the choir floor's geometry, health scaling and the rewards (Unsung design v1). */
class HarmonizeAndArenaTest {
    private static final ChoirArena ARENA = new ChoirArena(100, 90, -40);

    // ------------------------------------------------------------------ Harmonize

    @Test
    void threeCirclesLightTwoForTheLastMaskAndOneMorePerTwoPlayersBeyondTheSecond() {
        assertEquals(3, HarmonizeRules.litCount(3, 1));
        assertEquals(3, HarmonizeRules.litCount(2, 1));
        assertEquals(2, HarmonizeRules.litCount(1, 1), "the last mask lights only two");
        assertEquals(3, HarmonizeRules.litCount(3, 2));
        assertEquals(3, HarmonizeRules.litCount(3, 3), "the third player alone is not two beyond the second");
        assertEquals(4, HarmonizeRules.litCount(3, 4));
        assertEquals(4, HarmonizeRules.litCount(3, 5));
        assertEquals(5, HarmonizeRules.litCount(3, 6));
        assertEquals(3, HarmonizeRules.litCount(1, 4), "two for the last mask, plus one for four players");
        assertEquals(8, HarmonizeRules.litCount(3, 40), "never more than eight");
    }

    @Test
    void litCirclesAreSpreadRoundTheRing() {
        Random random = new Random(11);
        for (int n = 1; n <= 8; n++) {
            for (int trial = 0; trial < 200; trial++) {
                int[] lit = HarmonizeRules.choose(n, random::nextDouble);
                assertEquals(n, lit.length);
                assertEquals(n, Arrays.stream(lit).distinct().count(), "distinct circles");
                for (int k : lit) {
                    assertTrue(k >= 0 && k < 8);
                }
                if (n <= 4) {
                    for (int i = 0; i < n; i++) {
                        for (int j = i + 1; j < n; j++) {
                            int gap = Math.floorMod(lit[j] - lit[i], 8);
                            assertTrue(gap >= 2 && gap <= 6, "no two neighbours: " + Arrays.toString(lit));
                        }
                    }
                }
            }
        }
    }

    @Test
    void aloneAnyLitCircleSheltersYouWithMoreEachShelterTwo() {
        Vec3 c = ARENA.circleCentre(3);
        List<Circle> lit = ARENA.circles(new int[] {3});
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c3 = UUID.randomUUID();
        assertEquals(Set.of(a), HarmonizeRules.sheltered(List.of(new Spot(a, c.x + 2.2, c.z)), lit),
                "a player whose edge is in the circle is inside");
        assertTrue(HarmonizeRules.sheltered(List.of(new Spot(a, c.x + 2.4, c.z)), lit).isEmpty(), "just outside is outside");
        List<Spot> three = List.of(new Spot(a, c.x + 1.5, c.z), new Spot(b, c.x, c.z + 0.2), new Spot(c3, c.x - 0.5, c.z));
        assertEquals(Set.of(b, c3), HarmonizeRules.sheltered(three, lit), "three in one circle: the two nearest its centre");
        List<Circle> two = ARENA.circles(new int[] {3, 6});
        Vec3 d = ARENA.circleCentre(6);
        List<Spot> spread = new ArrayList<>(three);
        spread.set(0, new Spot(a, d.x, d.z));
        assertEquals(Set.of(a, b, c3), HarmonizeRules.sheltered(spread, two), "two circles, three players: all safe");
    }

    // ------------------------------------------------------------------ the choir floor

    @Test
    void theEightCirclesSitOnTheRingInsideTheFloorApartFromTheDais() {
        for (int k = 0; k < 8; k++) {
            Vec3 c = ARENA.circleCentre(k);
            assertEquals(ChoirArena.RING_RADIUS, ARENA.distance(c.x, c.z), 1e-9);
            assertEquals(k, ARENA.circleAt(c.x, c.z));
            assertTrue(ARENA.distance(c.x, c.z) + ChoirArena.CIRCLE_BLOCKS < ChoirArena.FLOOR_RADIUS, "inside the walls");
            assertTrue(ARENA.distance(c.x, c.z) - ChoirArena.CIRCLE_BLOCKS > ChoirArena.STEP_RADIUS, "clear of the dais");
            int blocks = 0;
            for (int bx = ARENA.x() - 20; bx <= ARENA.x() + 20; bx++) {
                for (int bz = ARENA.z() - 20; bz <= ARENA.z() + 20; bz++) {
                    if (ARENA.circleColumn(bx, bz) == k) {
                        blocks++;
                    }
                }
            }
            assertTrue(blocks >= 12 && blocks <= 21, "a disc of radius 2: " + blocks + " blocks");
        }
        assertEquals(-1, ARENA.circleAt(ARENA.x(), ARENA.z()), "no circle on the dais");
    }

    @Test
    void theMasksCircleTheDaisInATriangle() {
        for (double t : new double[] {0, 37.5, 500}) {
            Vec3 a = ARENA.orbit(Voice.ALTO, t);
            Vec3 b = ARENA.orbit(Voice.TENOR, t);
            Vec3 c = ARENA.orbit(Voice.BASS, t);
            for (Vec3 p : List.of(a, b, c)) {
                assertEquals(UnsungMoves.ORBIT_RADIUS, ARENA.distance(p.x, p.z), 1e-9);
                assertEquals(ARENA.floorY() + UnsungMoves.FACE_HEIGHT, p.y, 1e-9);
                assertTrue(ARENA.distance(p.x, p.z) > ChoirArena.STEP_RADIUS, "over the floor beside the dais");
            }
            double side = UnsungMoves.ORBIT_RADIUS * Math.sqrt(3.0);
            assertEquals(side, a.distanceTo(b), 1e-9);
            assertEquals(side, b.distanceTo(c), 1e-9);
        }
        Vec3 face = ARENA.orbit(Voice.BASS, 0).subtract(0, UnsungMoves.SINGER_DIP, 0);
        double bottom = ChoirArena.maskBox(face).minY - ARENA.floorY();
        assertTrue(bottom < 2.2, "a singing mask's shroud is within a swing from the floor: " + bottom);
    }

    @Test
    void theWaveCatchesThoseOnTheGroundAsItsFrontPassesAndAJumpClearsIt() {
        double x = ARENA.x() + 8.0;
        double z = ARENA.z();
        double before = ChoirArena.waveFront(3);
        double after = ChoirArena.waveFront(4);
        assertTrue(before < 8.0 && after >= 8.0 - 0.3, "the front reaches 8 blocks on its fourth tick");
        assertTrue(ARENA.waveCatches(x, z, ARENA.floorY(), 0.3, before, after));
        assertFalse(ARENA.waveCatches(x, z, ARENA.floorY() + 0.75, 0.3, before, after), "two ticks into a jump clears it");
        assertFalse(ARENA.waveCatches(x, z, ARENA.floorY(), 0.3, ChoirArena.waveFront(1), ChoirArena.waveFront(2)), "not yet");
        assertTrue(ARENA.waveCatches(ARENA.x() + 2.0, z, ARENA.floorY() + 1.0, 0.3, 1.0, 3.0), "on the dais the wave runs over its top");
        // from the release, the front runs to the wall in under nine ticks: a jump on the beat is in the air the whole way
        assertTrue(ChoirArena.waveFront(9) > ChoirArena.FLOOR_RADIUS);
    }

    // ------------------------------------------------------------------ health and rewards

    @Test
    void eachMaskHas250ScaledForThePlayersInTheApseCappedAtFour() {
        assertEquals(750.0, 3 * GuardianHealth.scaled(UnsungMoves.MASK_HEALTH, 1), 1e-9);
        assertEquals(400.0, GuardianHealth.scaled(UnsungMoves.MASK_HEALTH, 2), 1e-9);
        assertEquals(700.0, GuardianHealth.scaled(UnsungMoves.MASK_HEALTH, 4), 1e-9);
        assertEquals(700.0, GuardianHealth.scaled(UnsungMoves.MASK_HEALTH, 9), 1e-9, "capped at four players");
    }

    @Test
    void aSilentSigilOnEveryKillThePendantFirstThenOneInTen() {
        RewardTable.Reward first = UnsungLoot.TABLE.roll(true, () -> 0.99);
        assertEquals(List.of(new RewardTable.Drop(UnsungLoot.SILENT_SIGIL, 1), new RewardTable.Drop(UnsungLoot.CHOIR_PENDANT, 1)),
                first.drops());
        assertEquals(25_000, first.xp());
        assertEquals(1, first.statPoints());
        RewardTable.Reward unlucky = UnsungLoot.TABLE.roll(false, () -> 0.5);
        assertEquals(List.of(new RewardTable.Drop(UnsungLoot.SILENT_SIGIL, 1)), unlucky.drops(), "a Sigil every kill");
        assertEquals(5_000, unlucky.xp());
        assertEquals(0, unlucky.statPoints());
        RewardTable.Reward lucky = UnsungLoot.TABLE.roll(false, () -> 0.05);
        assertTrue(lucky.drops().contains(new RewardTable.Drop(UnsungLoot.CHOIR_PENDANT, 1)), "a Pendant one time in ten");
    }
}
