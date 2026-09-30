package com.cosmicbreach.codex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class LairMoteTest {
    private static final BlockPos HERE = new BlockPos(0, 360, 0);

    @Test
    void theNearestReachableUndefeatedLairWins() {
        List<LairMote.Candidate> lairs = List.of(
                new LairMote.Candidate("colossus", new BlockPos(300, 360, 0), true, false),
                new LairMote.Candidate("leviathan", new BlockPos(100, 230, 0), false, false),
                new LairMote.Candidate("unsung", new BlockPos(50, 100, 0), true, true),
                new LairMote.Candidate("colossus", new BlockPos(0, 360, -200), true, false));
        LairMote.Candidate pick = LairMote.choose(HERE, lairs).orElseThrow();
        assertEquals(new BlockPos(0, 360, -200), pick.centre());
    }

    @Test
    void distanceIsHorizontal() {
        // the layers stack: a lair far below but close across is the nearer one
        List<LairMote.Candidate> lairs = List.of(
                new LairMote.Candidate("leviathan", new BlockPos(40, 200, 0), true, false),
                new LairMote.Candidate("colossus", new BlockPos(60, 360, 0), true, false));
        assertEquals("leviathan", LairMote.choose(HERE, lairs).orElseThrow().name());
    }

    @Test
    void nothingLeftIsEmpty() {
        assertTrue(LairMote.choose(HERE, List.of(new LairMote.Candidate("colossus", HERE, true, true))).isEmpty());
        assertTrue(LairMote.choose(HERE, List.of()).isEmpty());
    }

    @Test
    void itLiftsFliesAndHovers() {
        Vec3 start = new Vec3(0, 100, 0);
        Vec3 east = new Vec3(1, 0, 0);
        assertEquals(start, LairMote.position(start, east, 0));
        Vec3 end = LairMote.position(start, east, LairMote.FLIGHT_TICKS);
        assertEquals(LairMote.REACH, end.x, 1e-9);
        assertEquals(101.2, end.y, 1e-9);
        // it keeps its distance while it hovers, and never goes past it
        for (int t = LairMote.FLIGHT_TICKS; t <= LairMote.LIFE_TICKS; t++) {
            assertEquals(LairMote.REACH, LairMote.position(start, east, t).x, 1e-9);
        }
        double last = -1;
        for (int t = 0; t <= LairMote.FLIGHT_TICKS; t++) {
            double x = LairMote.position(start, east, t).x;
            assertTrue(x >= last, "it only ever flies forward");
            last = x;
        }
    }

    @Test
    void directionFollowsTheTarget() {
        Vec3 d = LairMote.direction(new Vec3(0, 360, 0), new Vec3(0, 160, 0));
        assertEquals(-1.0, d.y, 1e-9);
        Vec3 same = LairMote.direction(Vec3.ZERO, Vec3.ZERO);
        assertEquals(1.0, same.y, 1e-9);
        Vec3 diag = LairMote.direction(Vec3.ZERO, new Vec3(3, 0, 4));
        assertEquals(0.6, diag.x, 1e-9);
        assertEquals(0.8, diag.z, 1e-9);
    }
}
