package com.cosmicbreach.shrine;

import com.cosmicbreach.guardian.colossus.CrownSpireLayout;
import com.cosmicbreach.guardian.heliarch.HeliarchArena;
import com.cosmicbreach.guardian.leviathan.RiftLayout;
import com.cosmicbreach.guardian.unsung.NaveLayout;
import com.cosmicbreach.structure.sanctum.SanctumLayout;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each shrine at the approach to its arena (1.1 design section 9): on solid ground with room, in sight of the entrance,
 * outside every attack's reach and off the path in. Read straight from the lairs' own layouts.
 */
class ShrineSitesTest {
    private static boolean rock(RiftLayout l, int x, int y, int z) {
        RiftLayout.Kind k = l.kind(l.slice(x, z, x, z), x, y, z);
        return k != RiftLayout.Kind.AIR && k != RiftLayout.Kind.KEEP && k != RiftLayout.Kind.UPDRAFT && k != RiftLayout.Kind.UPDRAFT_TOP;
    }

    @Test
    void bossTwosShrineStandsOnTheLedgeOutsideTheSphereBesideTheWalkway() {
        for (long seed : new long[] {1L, 1234L, 99L}) {
            RiftLayout l = new RiftLayout(400, RiftLayout.CENTRE_Y, -900, seed);
            ShrineSites.Site s = ShrineSites.rift(l);
            BlockPos p = s.pos();
            assertEquals(ShrineKind.LEVIATHAN, s.kind());
            assertTrue(rock(l, p.getX(), p.getY() - 1, p.getZ()), "ledge rock under it");
            for (int dy = 0; dy <= 2; dy++) {
                assertFalse(rock(l, p.getX(), p.getY() + dy, p.getZ()), "room above, " + dy);
            }
            assertTrue(l.shellFraction(p.getX(), p.getY(), p.getZ()) > 1.02, "outside the sphere, beyond every attack");
            double ex = Math.cos(l.entranceAngle());
            double ez = Math.sin(l.entranceAngle());
            double lateral = Math.abs(-(p.getX() + 0.5 - l.centre().x) * ez + (p.getZ() + 0.5 - l.centre().z) * ex);
            assertTrue(lateral >= 2.4, "off the walkway (half width 1.5): " + lateral);
            assertTrue(s.facing().getStepX() * -ex + s.facing().getStepZ() * -ez > 0.6, "faces the gap");
        }
    }

    @Test
    void bossThreesShrineStandsOnTheLandingBesideTheDoorFacingOut() {
        for (int facing = 0; facing < 4; facing++) {
            NaveLayout l = new NaveLayout(100, 200, 80, facing, 5L);
            ShrineSites.Site s = ShrineSites.nave(l);
            BlockPos p = s.pos();
            NaveLayout.Kind under = l.kind(p.getX(), p.getY() - 1, p.getZ());
            assertTrue(under == NaveLayout.Kind.FLOOR_BAND || under == NaveLayout.Kind.FLOOR, "the landing under it: " + under);
            for (int dy = 0; dy <= 2; dy++) {
                assertEquals(NaveLayout.Kind.AIR, l.kind(p.getX(), p.getY() + dy, p.getZ()), "room above, " + dy);
            }
            double[] uv = l.local(p.getX(), p.getZ());
            assertTrue(uv[0] > NaveLayout.DOOR_WALL && uv[0] <= NaveLayout.LANDING, "outside the door, on the landing: u " + uv[0]);
            assertEquals(50.5, uv[0], 1e-9, "the landing's middle row, facing " + facing);
            assertEquals(3.5, Math.abs(uv[1]), 1e-9, "the landing's outer column, a free column from the doorway, facing " + facing);
            int[] a = l.axis();
            assertEquals(Direction.getNearest((double) a[0], 0.0, (double) a[1]), s.facing(), "faces out to whoever arrives");
        }
    }

    @Test
    void theNavesFacingComesBackFromItsLairEntry() {
        for (int facing = 0; facing < 4; facing++) {
            NaveLayout l = new NaveLayout(-300, 40, 70, facing, 9L);
            int[] alt = l.altar();
            NaveLayout back = ShrineSites.naveOf(l.arena().centreBlock(), new BlockPos(alt[0], alt[1], alt[2]));
            assertEquals(facing, back.facing());
            assertEquals(l.floorY(), back.floorY());
        }
    }

    @Test
    void bossFoursShrineStandsInTheAntechamberFarFromTheFight() {
        for (int side : new int[] {-1, 1}) {
            SanctumLayout l = new SanctumLayout(side, 0, 70, side * 130, 3L);
            ShrineSites.Site s = ShrineSites.sanctum(l);
            BlockPos p = s.pos();
            SanctumLayout.Kind under = l.kind(p.getX(), p.getY() - 1, p.getZ());
            assertTrue(under != SanctumLayout.Kind.AIR && under != SanctumLayout.Kind.KEEP, "floor under it: " + under);
            for (int dy = 0; dy <= 2; dy++) {
                assertEquals(SanctumLayout.Kind.AIR, l.kind(p.getX(), p.getY() + dy, p.getZ()), "room above, " + dy);
            }
            assertTrue(Math.hypot(p.getX() + 0.5, p.getZ() + 0.5) > HeliarchArena.FIGHT_RADIUS + 10, "far outside the fight");
            assertTrue(Math.abs(p.getX()) >= 3, "off the aisle");
        }
    }

    @Test
    void bossOnesShrineStandsThreeInFromTheIslandsRimAlongTheDoorsBearing() {
        CrownSpireLayout l = new CrownSpireLayout(0, 0, 450, 340, 334);
        ShrineSites.IslandTop disc = (x, z) -> Math.hypot(x, z) <= 45.0 ? OptionalDouble.of(339.0) : OptionalDouble.empty();
        Optional<ShrineSites.Site> site = ShrineSites.crown(l, disc);
        assertTrue(site.isPresent());
        BlockPos p = site.get().pos();
        assertEquals(340, p.getY(), "standing on the island's top");
        double a = Math.toRadians(CrownSpireLayout.RISE_ANGLE);
        assertTrue(disc.at(p.getX() + 0.5, p.getZ() + 0.5).isPresent(), "on the island");
        assertTrue(disc.at(p.getX() + 0.5 + Math.cos(a) * 4.5, p.getZ() + 0.5 + Math.sin(a) * 4.5).isEmpty(), "the rim within a few blocks outward");
        assertEquals(Direction.WEST, site.get().facing(), "faces back toward the door at 30 degrees");
        ShrineSites.IslandTop huge = (x, z) -> OptionalDouble.of(339.0);
        Optional<ShrineSites.Site> near = ShrineSites.crown(l, huge);
        assertTrue(near.isPresent(), "no rim in reach: a spot past the walkway's end");
        assertTrue(Math.hypot(near.get().pos().getX(), near.get().pos().getZ()) < 40.0);
    }

    @Test
    void onASmallIslandBossOnesShrineStepsBackTowardTheDoorRatherThanLeaveIt() {
        CrownSpireLayout l = new CrownSpireLayout(0, 0, 450, 340, 334);
        ShrineSites.IslandTop small = (x, z) -> Math.hypot(x, z) <= 26.0 ? OptionalDouble.of(339.0) : OptionalDouble.empty();
        Optional<ShrineSites.Site> site = ShrineSites.crown(l, small);
        assertTrue(site.isPresent());
        BlockPos p = site.get().pos();
        assertTrue(small.at(p.getX() + 0.5, p.getZ() + 0.5).isPresent(), "still on the island");
        double a = Math.toRadians(CrownSpireLayout.RISE_ANGLE);
        double lateral = Math.abs(-(p.getX() + 0.5) * Math.sin(a) + (p.getZ() + 0.5) * Math.cos(a));
        assertTrue(lateral > 2.5, "never on the walkway (half width 1.5): " + lateral);
    }
}
