package com.cosmicbreach.codex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cosmicbreach.world.Layer;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class LairMoteTest {
    private static final BlockPos HERE = new BlockPos(0, 360, 0);

    private static LairMote.Candidate colossus(BlockPos at, boolean reachable, boolean defeated) {
        return new LairMote.Candidate("colossus", at, Layer.REACH, 0, reachable, defeated);
    }

    private static LairMote.Candidate leviathan(BlockPos at, boolean reachable, boolean defeated) {
        return new LairMote.Candidate("leviathan", at, Layer.DRIFT, 1, reachable, defeated);
    }

    private static LairMote.Candidate unsung(BlockPos at, boolean reachable, boolean defeated) {
        return new LairMote.Candidate("unsung", at, Layer.DEEP, 2, reachable, defeated);
    }

    private static LairMote.Candidate sanctum(boolean defeated) {
        return new LairMote.Candidate("sanctum", new BlockPos(0, 64, 0), Layer.DEEP, LairMote.SANCTUM_RANK, true, defeated);
    }

    @Test
    void theNearestReachableUndefeatedLairWins() {
        List<LairMote.Candidate> lairs = List.of(
                colossus(new BlockPos(300, 360, 0), true, false),
                leviathan(new BlockPos(100, 230, 0), false, false),
                unsung(new BlockPos(50, 100, 0), true, true),
                colossus(new BlockPos(0, 360, -200), true, false));
        LairMote.Candidate pick = LairMote.choose(Layer.REACH, HERE, lairs).orElseThrow();
        assertEquals(new BlockPos(0, 360, -200), pick.centre());
    }

    @Test
    void aLairOfThePlayersOwnLayerBeatsANearerOneElsewhere() {
        // standing in the Deep with every lair open: the Deep's own guardian, however far, never one above
        BlockPos deep = new BlockPos(0, 120, 0);
        List<LairMote.Candidate> lairs = List.of(
                colossus(new BlockPos(20, 360, 0), true, false),
                leviathan(new BlockPos(30, 230, 0), true, false),
                unsung(new BlockPos(3000, 100, 0), true, false),
                sanctum(false));
        assertEquals("unsung", LairMote.choose(Layer.DEEP, deep, lairs).orElseThrow().name());
        // and each layer points at its own
        assertEquals("leviathan", LairMote.choose(Layer.DRIFT, new BlockPos(0, 230, 0), lairs).orElseThrow().name());
        assertEquals("colossus", LairMote.choose(Layer.REACH, HERE, lairs).orElseThrow().name());
    }

    @Test
    void inTheDeepTheSanctumComesOnlyAfterItsGuardian() {
        BlockPos deep = new BlockPos(500, 120, 0);
        assertEquals("unsung", LairMote.choose(Layer.DEEP, deep, List.of(sanctum(false),
                unsung(new BlockPos(2000, 100, 0), true, false))).orElseThrow().name());
        assertEquals("sanctum", LairMote.choose(Layer.DEEP, deep, List.of(sanctum(false),
                unsung(new BlockPos(2000, 100, 0), true, true))).orElseThrow().name());
    }

    @Test
    void withThisLayerDoneTheNextInOrderOfPlay() {
        // the Reach's guardian beaten: the Drift's, not a nearer lair further on
        List<LairMote.Candidate> lairs = List.of(
                colossus(new BlockPos(10, 360, 0), true, true),
                leviathan(new BlockPos(900, 230, 0), true, false),
                unsung(new BlockPos(40, 100, 0), true, false));
        assertEquals("leviathan", LairMote.choose(Layer.REACH, HERE, lairs).orElseThrow().name());
        // the Drift's beaten too: the Deep's
        List<LairMote.Candidate> later = List.of(
                colossus(new BlockPos(10, 360, 0), true, true),
                leviathan(new BlockPos(900, 230, 0), true, true),
                unsung(new BlockPos(4000, 100, 0), true, false));
        assertEquals("unsung", LairMote.choose(Layer.DRIFT, new BlockPos(0, 230, 0), later).orElseThrow().name());
    }

    @Test
    void nothingLeftIsEmpty() {
        assertTrue(LairMote.choose(Layer.REACH, HERE, List.of(colossus(HERE, true, true))).isEmpty());
        assertTrue(LairMote.choose(Layer.DEEP, HERE, List.of(sanctum(true))).isEmpty());
        assertTrue(LairMote.choose(Layer.REACH, HERE, List.of()).isEmpty());
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
