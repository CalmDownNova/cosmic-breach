package com.cosmicbreach.guardian.colossus;

import com.cosmicbreach.guardian.Participants;
import com.cosmicbreach.guardian.colossus.CrownSpireLayout.Kind;
import com.cosmicbreach.world.Layer;
import com.cosmicbreach.world.gen.AetheriaTerrain;
import com.cosmicbreach.world.gen.ReachIslands;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Crown Spire's shape and where it stands, from the real terrain model; and who takes part in a fight. */
class CrownSpireLayoutTest {
    private static final long SALT = 0x5EEDL;
    private static final long SEED = 12345L;
    private final AetheriaTerrain terrain = AetheriaTerrain.forSalt(SALT);
    private static final CrownSpireLayout L = new CrownSpireLayout(0, 0, 450, 362, 324);

    @Test
    void theCrownIsAFlatBrickDiscWithBrassRingsAndABattlementedRim() {
        assertEquals(Kind.BRICKS, L.kind(3, 449, 0));
        assertEquals(Kind.GOLD, L.kind(-6, 449, -2), "a brass inlay ring at radius 6");
        assertEquals(Kind.AIR, L.kind(8, 450, 3), "open floor");
        // a merlon on the east bearing: crown quartz 4 high
        for (int y = 450; y < 454; y++) {
            assertEquals(Kind.CROWN_QUARTZ, L.kind(18, y, 0), "a merlon at y " + y);
        }
        assertEquals(Kind.AIR, L.kind(18, 454, 0));
        // a gap between merlons (7.7 degrees): the quartz band, then clear glass, 3 high in all
        assertEquals(Kind.CROWN_QUARTZ, L.kind(18, 450, 2), "the rim's band");
        assertEquals(Kind.GLASS, L.kind(18, 451, 2));
        assertEquals(Kind.GLASS, L.kind(18, 452, 2));
        assertEquals(Kind.AIR, L.kind(18, 453, 2));
        assertEquals(Kind.PILLAR, L.kind(0, 450, 0), "the pillar's stump at the centre");
    }

    @Test
    void crownPointsRiseTallAndShortInTurnOutsideTheRim() {
        // the tall point at 15 degrees and the short one at 45, each leaning out from under the crown's edge
        int tallTop = 0;
        int shortTop = 0;
        for (int y = 440; y <= 470; y++) {
            for (int r = 19; r <= 24; r++) {
                if (L.kind((int) Math.floor(r * Math.cos(Math.toRadians(15))), y, (int) Math.floor(r * Math.sin(Math.toRadians(15)))) == Kind.CROWN_QUARTZ) {
                    tallTop = Math.max(tallTop, y);
                }
                if (L.kind((int) Math.floor(r * Math.cos(Math.toRadians(45))), y, (int) Math.floor(r * Math.sin(Math.toRadians(45)))) == Kind.CROWN_QUARTZ) {
                    shortTop = Math.max(shortTop, y);
                }
            }
        }
        assertTrue(tallTop >= L.floorY() + CrownSpireLayout.POINT_TALL - 2, "the tall point reaches " + tallTop);
        assertTrue(shortTop >= L.floorY() + CrownSpireLayout.POINT_SHORT - 2 && shortTop < tallTop - 2, "the short point reaches " + shortTop);
        for (int y = L.floorY(); y < L.floorY() + 14; y++) {
            for (int bx = -18; bx <= 18; bx++) {
                for (int bz = -18; bz <= 18; bz++) {
                    if (Math.hypot(bx + 0.5, bz + 0.5) <= CrownSpireLayout.ARENA_RADIUS) {
                        assertTrue(L.kind(bx, y, bz) != Kind.CROWN_QUARTZ || isWellPost(bx, bz), "no point over the floor at " + bx + " " + y + " " + bz);
                    }
                }
            }
        }
    }

    private static boolean isWellPost(int bx, int bz) {
        int[] fall = L.fallColumn();
        return Math.abs(bx - fall[0]) == 2 && Math.abs(bz - fall[1]) == 2;
    }

    @Test
    void theFallingWellIsFlushWithABrassBorderAndFourLampPosts() {
        int[] fall = L.fallColumn();
        int y = L.floorY();
        assertEquals(Kind.FALLING, L.kind(fall[0], y - 1, fall[1]), "the well is open in the floor");
        assertEquals(Kind.GOLD, L.kind(fall[0] + 2, y - 1, fall[1]), "a brass border round it");
        assertEquals(Kind.AIR, L.kind(fall[0] + 2, y, fall[1]), "no curb: flush with the floor");
        assertEquals(Kind.AIR, L.kind(fall[0], y, fall[1] - 2));
        for (int sx = -2; sx <= 2; sx += 4) {
            for (int sz = -2; sz <= 2; sz += 4) {
                assertEquals(Kind.CROWN_QUARTZ, L.kind(fall[0] + sx, y, fall[1] + sz), "a post at a corner");
                assertEquals(Kind.LAMP, L.kind(fall[0] + sx, y + 1, fall[1] + sz), "its lamp");
            }
        }
    }

    @Test
    void theDoorPassageIsLinedWithBrickAndQuartzRibs() {
        // through the shell on the rising door's bearing, at the height of a player's head: walls either side
        double a = Math.toRadians(CrownSpireLayout.RISE_ANGLE);
        int y = L.baseY() + 1;
        int lined = 0;
        int ribs = 0;
        for (double r = L.innerRadius(y) + 0.5; r < L.outerRadius(y) - 0.5; r += 1.0) {
            for (int s = -1; s <= 1; s += 2) {
                double lateral = s * (CrownSpireLayout.DOOR_WIDTH / 2.0 + 0.9) * r / 13.0;
                int bx = (int) Math.floor(L.x() + r * Math.cos(a) - lateral * Math.sin(a));
                int bz = (int) Math.floor(L.z() + r * Math.sin(a) + lateral * Math.cos(a));
                Kind k = L.kind(bx, y, bz);
                if (k == Kind.BRICKS || k == Kind.CROWN_QUARTZ) {
                    lined++;
                }
                if (k == Kind.CROWN_QUARTZ) {
                    ribs++;
                }
            }
        }
        assertTrue(lined >= 6, "the passage's walls are built: " + lined + " lined blocks");
        assertTrue(ribs >= 1, "with quartz ribs: " + ribs);
        int[] door = L.riseDoorOutside();
        assertEquals(Kind.AIR, L.kind(door[0], L.baseY(), door[2]), "and the way through is open");
    }

    @Test
    void eachDoorHasAGateAcrossItsWalkway() {
        // the rising door opens at 30 degrees, so its gate faces east: pillars either side of a 5-wide opening
        double a = Math.toRadians(CrownSpireLayout.RISE_ANGLE);
        int n0 = (int) Math.floor(L.x() + CrownSpireLayout.GATE_ALONG * Math.cos(a));
        int l0 = (int) Math.floor(L.z() + CrownSpireLayout.GATE_ALONG * Math.sin(a));
        int base = L.baseY();
        for (int h = 0; h < CrownSpireLayout.GATE_OPENING; h++) {
            assertEquals(Kind.AIR, L.kind(n0, base + h, l0), "the opening at height " + h);
            assertEquals(h == 0 ? Kind.GOLD : Kind.CROWN_QUARTZ, L.kind(n0, base + h, l0 + 3), "a pillar at height " + h);
            assertEquals(h == 0 ? Kind.GOLD : Kind.CROWN_QUARTZ, L.kind(n0 + 1, base + h, l0 - 4), "the other pillar at height " + h);
        }
        assertEquals(Kind.BRICKS, L.kind(n0, base - 1, l0), "its threshold");
        assertEquals(Kind.GOLD, L.kind(n0, base + CrownSpireLayout.GATE_OPENING, l0), "a brass band over the opening");
        assertEquals(Kind.CROWN_QUARTZ, L.kind(n0, base + CrownSpireLayout.GATE_OPENING + 1, l0 + 4), "the lintel");
        assertEquals(Kind.LAMP, L.kind(n0 + 1, base + CrownSpireLayout.GATE_OPENING + 2, l0), "a light at the prism's heart");
        assertEquals(Kind.LAMP, L.kind(n0 + 1, base + CrownSpireLayout.GATE_OPENING + 2, l0 + 4), "a lamp over a pillar");
        assertEquals(Kind.CROWN_QUARTZ, L.kind(n0 + 1, base + CrownSpireLayout.GATE_OPENING + 5, l0), "the prism's apex");
    }

    @Test
    void sixCrystalsStandOnTheFloorAtRadiusFourteen() {
        CrownArena arena = L.arena();
        for (int k = 0; k < Refraction.CRYSTALS; k++) {
            BlockPos base = arena.crystalBase(k);
            for (int dy = 0; dy < CrownArena.CRYSTAL_HEIGHT; dy++) {
                assertEquals(Kind.CRYSTAL, L.kind(base.getX() + 1, base.getY() + dy, base.getZ() + 1), "crystal " + k);
            }
            assertEquals(Kind.AIR, L.kind(base.getX(), base.getY() + CrownArena.CRYSTAL_HEIGHT, base.getZ()));
        }
    }

    @Test
    void theLiftRunsFromTheFootToTheFloorBesideTheAltar() {
        int[] rise = L.riseColumn();
        int[] fall = L.fallColumn();
        assertEquals(Kind.RISING, L.kind(rise[0], L.baseY(), rise[1]));
        assertEquals(Kind.RISING, L.kind(rise[0], 420, rise[1]));
        assertEquals(Kind.RISING_TOP, L.kind(rise[0], L.floorY() - 1, rise[1]), "it arrives through the floor");
        assertEquals(Kind.FALLING, L.kind(fall[0], 400, fall[1]));
        int[] altar = L.altar();
        assertEquals(Kind.ALTAR, L.kind(altar[0], altar[1], altar[2]));
        double d = Math.hypot(altar[0] - rise[0], altar[2] - rise[1]);
        assertTrue(d >= 2.0 && d <= 5.0, "the altar stands beside the arrival, " + d + " blocks off");
        assertTrue(L.arena().distance(rise[0] + 0.5, rise[1] + 0.5) < CrownArena.CRYSTAL_RADIUS - 2, "the lift arrives inside the crystals");
    }

    @Test
    void theShaftIsHollowWithDoorsAtTheFoot() {
        assertEquals(Kind.AIR, L.kind(0, 400, 0), "the hollow core");
        double wall = L.outerRadius(400) - 1.0;
        assertTrue(isSolid(L.kind((int) Math.floor(wall), 400, 0)), "the shell at radius " + wall);
        int[] door = L.riseDoorOutside();
        assertEquals(Kind.BRICKS, L.kind(door[0], L.baseY() - 1, door[2]), "a walkway flush with the floor outside the door");
        assertEquals(Kind.AIR, L.kind(door[0], L.baseY(), door[2]));
    }

    @Test
    void theSpireIsOneHundredTenToOneHundredFortyTallAboveTheShearBand() {
        for (int cx = -40; cx <= 40; cx += 4) {
            for (int cz = -40; cz <= 40; cz += 4) {
                Optional<CrownSpireLayout> site = CrownSpireStructure.site(terrain, SEED, new ChunkPos(cx, cz));
                if (site.isEmpty()) {
                    continue;
                }
                CrownSpireLayout l = site.get();
                assertTrue(l.floorY() >= 441 && l.floorY() <= 460, "the crown floor " + (l.floorY() - 1) + " is between Y 440 and 459");
                assertTrue(l.rootY() > Layer.REACH.rockMinY, "the root stays above Shear band A: " + l.rootY());
                int height = l.floorY() - l.rootY();
                assertTrue(height <= 140 && height >= 100, "tall " + height);
                assertTrue(l.floorY() + CrownSpireLayout.CLEAR_HEIGHT < 480, "under the build limit");
            }
        }
    }

    @Test
    void mostStartsFindAShatteredSpiresIslandAndStandWellInsideIt() {
        int found = 0;
        int tried = 0;
        ReachIslands.Column col = new ReachIslands.Column();
        List<String> bad = new ArrayList<>();
        for (int cx = -60; cx <= 60; cx += 6) {
            for (int cz = -60; cz <= 60; cz += 6) {
                tried++;
                Optional<CrownSpireLayout> site = CrownSpireStructure.site(terrain, SEED, new ChunkPos(cx, cz));
                if (site.isEmpty()) {
                    continue;
                }
                found++;
                CrownSpireLayout l = site.get();
                terrain.reach.sample(l.x() + 0.5, l.z() + 0.5, col);
                if (!col.island || col.isle == null || col.isle.sunfield || col.edge < CrownSpireStructure.MIN_EDGE) {
                    bad.add(l.x() + "," + l.z());
                }
                assertEquals((int) Math.floor(col.top) + 1, l.baseY(), "the foot sits on the island's top");
            }
        }
        assertTrue(bad.isEmpty(), "every spire stands on a Shattered Spires island: " + bad);
        assertTrue(found >= tried * 0.7, "a start usually finds room (" + found + " of " + tried + "), so the nearest lair is near");
    }

    @Test
    void theNearestLairIsUsuallyWithinAbout450Blocks() {
        // the structure set's placement (data/cosmicbreach/worldgen/structure_set/crown_spire.json)
        var placement = new net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement(40, 16,
                net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType.LINEAR, 1847361029);
        java.util.Random random = new java.util.Random(7);
        List<Double> nearest = new ArrayList<>();
        for (int n = 0; n < 40; n++) {
            int px = random.nextInt(6000) - 3000;
            int pz = random.nextInt(6000) - 3000;
            double best = Double.MAX_VALUE;
            int rx = Math.floorDiv(px >> 4, 40);
            int rz = Math.floorDiv(pz >> 4, 40);
            for (int i = -2; i <= 2; i++) {
                for (int j = -2; j <= 2; j++) {
                    ChunkPos cp = placement.getPotentialStructureChunk(SEED, (rx + i) * 40, (rz + j) * 40);
                    Optional<CrownSpireLayout> site = CrownSpireStructure.site(terrain, SEED, cp);
                    if (site.isPresent()) {
                        best = Math.min(best, Math.hypot(site.get().x() - px, site.get().z() - pz));
                    }
                }
            }
            nearest.add(best);
        }
        nearest.sort(Double::compare);
        double median = nearest.get(nearest.size() / 2);
        System.out.println("median distance to the nearest Crown Spire: " + Math.round(median) + " blocks");
        assertTrue(median <= 480, "median distance to the nearest lair " + median);
    }

    @Test
    void participantsAreThoseInsideWhoHitOrWereTargeted() {
        Participants p = new Participants();
        UUID a = new UUID(0, 1);
        UUID b = new UUID(0, 2);
        UUID c = new UUID(0, 3);
        assertFalse(p.dealtDamage(a, false), "hitting it from outside the arena doesn't count");
        assertTrue(p.dealtDamage(b, true));
        assertFalse(p.dealtDamage(b, true), "once each");
        assertTrue(p.targeted(c));
        assertEquals(List.of(b, c), List.copyOf(p.all()), "in the order they joined");
        assertFalse(p.contains(a));
    }

    private static boolean isSolid(Kind kind) {
        return kind == Kind.STONE || kind == Kind.QUARTZ || kind == Kind.POLISHED || kind == Kind.BRICKS;
    }
}
