package com.cosmicbreach.world.weather;

import com.cosmicbreach.world.Layer;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Drift's currents: the same tubes from the same salt on every side, inside the Drift, 0.05 a tick. */
class DriftCurrentsTest {
    private static final long SALT = DriftCurrents.salt(1234567L);

    @Test
    void theSaltHidesTheSeedAndDependsOnIt() {
        assertNotEquals(1234567L, SALT);
        assertNotEquals(SALT, DriftCurrents.salt(1234568L));
        assertEquals(SALT, DriftCurrents.salt(1234567L));
    }

    @Test
    void theSameSaltGivesTheSameTubes() {
        for (int cx = -5; cx <= 5; cx++) {
            for (int cz = -5; cz <= 5; cz++) {
                assertEquals(DriftCurrents.zone(SALT, cx, cz), DriftCurrents.zone(SALT, cx, cz));
            }
        }
    }

    @Test
    void aboutTwoCellsInThreeHoldOneTubeInsideTheDrift() {
        int with = 0;
        int cells = 0;
        for (int cx = -30; cx < 30; cx++) {
            for (int cz = -30; cz < 30; cz++) {
                cells++;
                DriftCurrents.Zone z = DriftCurrents.zone(SALT, cx, cz);
                if (z == null) {
                    continue;
                }
                with++;
                assertTrue(z.y() - z.radius() >= Layer.DRIFT.rockMinY && z.y() + z.radius() <= Layer.DRIFT.rockMaxY,
                        "tube inside the Drift: " + z);
                assertTrue(z.halfLength() >= 40 && z.halfLength() <= 80 && z.radius() >= 5 && z.radius() <= 8, z.toString());
                assertTrue(Math.floorDiv((int) z.x(), DriftCurrents.CELL) == cx && Math.floorDiv((int) z.z(), DriftCurrents.CELL) == cz,
                        "centred in its own cell");
            }
        }
        double share = with / (double) cells;
        assertTrue(share > 0.6 && share < 0.76, "share of cells with a current: " + share);
    }

    @Test
    void aPointOnTheAxisIsInsideAndCarriedAlongTheHeading() {
        DriftCurrents.Zone z = firstZone();
        double t = z.halfLength() * 0.5;
        double x = z.x() + z.dirX() * t;
        double zz = z.z() + z.dirZ() * t;
        assertEquals(z, DriftCurrents.at(SALT, x, z.y() + z.radius() * 0.5, zz));
        double[] push = DriftCurrents.push(SALT, x, z.y(), zz, Double.NaN);
        assertEquals(DriftCurrents.PUSH, Math.hypot(push[0], push[1]), 1e-9);
        assertEquals(z.dirX() * DriftCurrents.PUSH, push[0], 1e-9);
        assertEquals(z.dirZ() * DriftCurrents.PUSH, push[1], 1e-9);
        double[] tide = DriftCurrents.push(SALT, x, z.y(), zz, Math.PI / 2);
        assertEquals(0.0, tide[0], 1e-9);
        assertEquals(DriftCurrents.PUSH, tide[1], 1e-9, "a Tide turns every tube to its heading");
    }

    @Test
    void outsideATubeNothingPushes() {
        DriftCurrents.Zone z = firstZone();
        assertNull(DriftCurrents.at(SALT, z.x(), z.y() + z.radius() + 1.0, z.z()));
        assertNull(DriftCurrents.at(SALT, z.x() + z.dirX() * (z.halfLength() + z.radius() + 1.0), z.y(),
                z.z() + z.dirZ() * (z.halfLength() + z.radius() + 1.0)));
        double[] push = DriftCurrents.push(SALT, z.x(), 400, z.z(), Double.NaN);
        assertEquals(0.0, push[0]);
        assertEquals(0.0, push[1]);
    }

    @Test
    void nearFindsTheTubesAroundAPoint() {
        DriftCurrents.Zone z = firstZone();
        List<DriftCurrents.Zone> near = DriftCurrents.near(SALT, z.x(), z.z(), 10);
        assertTrue(near.contains(z));
        for (DriftCurrents.Zone other : near) {
            double t = other.along(z.x(), z.z());
            double dx = other.x() + other.dirX() * t - z.x();
            double dz = other.z() + other.dirZ() * t - z.z();
            assertTrue(Math.hypot(dx, dz) <= 10 + other.radius() + 1e-9);
        }
    }

    private static DriftCurrents.Zone firstZone() {
        for (int cx = 0; cx < 20; cx++) {
            DriftCurrents.Zone z = DriftCurrents.zone(SALT, cx, 0);
            if (z != null) {
                return z;
            }
        }
        throw new AssertionError("no current in 20 cells");
    }

    @Test
    void zonesAreFoundFromNeighbouringCells() {
        DriftCurrents.Zone z = firstZone();
        // the tube's far end can lie in the next cell; the point is still found
        double x = z.x() + z.dirX() * z.halfLength();
        double zz = z.z() + z.dirZ() * z.halfLength();
        assertNotNull(DriftCurrents.at(SALT, x, z.y(), zz));
    }
}
