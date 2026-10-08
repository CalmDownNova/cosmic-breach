package com.cosmicbreach.world.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cosmicbreach.structure.gen.GyreObservatoryStructure;
import com.cosmicbreach.world.gen.DriftReach.Crossing;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The Drift's layout against its 1.1 targets (Aetheria 1.1 Design, section 2), measured on the terrain model itself: three
 * worlds, a 900 by 900 block survey of each well away from the Breach, every rock whose centre lies in a belt. Rocks are
 * rounded to balls of their mean radius. Prints the distributions (the harness), then asserts:
 *
 * <ul>
 *   <li>the rocks: a typical rock 15 to 22 across; the typical gap to the nearest rock beside it (not stacked above or below)
 *       5 to 10 blocks with tops within 5; some wide gaps left (the 90th percentile at least 12). These gaps are surface to
 *       surface along the line between centres, and a voxel check marches the real rock along a sample of those lines to keep
 *       the rounding honest;</li>
 *   <li>the gaps as a player meets them: of the pairs of straight neighbours (one cell along an axis) of one layer, with the
 *       stepping stone in the gap if it has one, about three in four are crossed by running jumps and about one in four stays a
 *       dash gap or a mount gap ({@link DriftReach}: too wide to jump but within the air dash's reach outside the low gravity
 *       window, or only for the window and a mount). The combined share also holds on real block surfaces; the split between
 *       dash and mount does not (about 4 and 23 percent there). The diagonal neighbours lie about 40 percent farther and are
 *       reported, not pinned; so is the nearest gap of each rock (most rocks keep a near neighbour a running jump away);</li>
 *   <li>the stones: none in a gap the dash alone crosses, and in any other wide gap one only where both hops it leaves are
 *       comfortable ({@link DriftReach#COMFORT}); a gap too wide for that stays open;</li>
 *   <li>the way through: a walkable route runs through every belt on foot alone (a belt is a 3D cloud with many ways round), so
 *       the dash and the mounts are for the gaps that stay open, never for getting across;</li>
 *   <li>the column search the chunk generator uses finds every rock and stone the model holds, and the stones are rock in the
 *       generated blocks; the landmarks still find rock (the observatory's real site rule, no rarer than under 1.0.3).</li>
 * </ul>
 */
class DriftLayoutTest {
    private static final long[] SALTS = {20260927L, 77L, 4242L};
    private static final int SIZE = 900;
    private static final int OFFSET = 1500;
    /** The four directions a cell lays stepping stones toward (and so the links the shares count). */
    private static final int[][] DIRECTIONS = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};

    private record Rock(DriftBelts.Asteroid a, int i, int j, int k) {}

    private static List<Rock> survey(AetheriaTerrain t) {
        List<Rock> out = new ArrayList<>();
        int c = DriftBelts.CELL;
        for (int i = Math.floorDiv(OFFSET, c); i <= Math.floorDiv(OFFSET + SIZE, c); i++) {
            for (int j = Math.floorDiv(OFFSET, c); j <= Math.floorDiv(OFFSET + SIZE, c); j++) {
                for (int k = DriftBelts.K_MIN; k <= DriftBelts.K_MAX; k++) {
                    DriftBelts.Asteroid a = t.drift.asteroid(i, j, k);
                    if (a != null) {
                        out.add(new Rock(a, i, j, k));
                    }
                }
            }
        }
        return out;
    }

    /** Surface to surface along the line between the centres, the rocks taken as balls of their mean radius. */
    private static double gap(DriftBelts.Asteroid a, DriftBelts.Asteroid b) {
        double d = Math.sqrt(TMath.sq(a.cx - b.cx) + TMath.sq(a.cy - b.cy) + TMath.sq(a.cz - b.cz));
        return d - a.meanRadius() - b.meanRadius();
    }

    private static double pct(List<Double> v, double p) {
        double[] s = v.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        return s.length == 0 ? Double.NaN : s[Math.min(s.length - 1, (int) Math.floor(p / 100.0 * s.length))];
    }

    // ------------------------------------------------------------------ the rocks

    @Test
    void theDriftMatchesItsTargets() {
        List<Double> diameters = new ArrayList<>();
        List<Double> gaps = new ArrayList<>();
        List<Double> tops = new ArrayList<>();
        for (long salt : SALTS) {
            AetheriaTerrain t = AetheriaTerrain.forSalt(salt);
            List<Rock> rocks = survey(t);
            for (Rock r : rocks) {
                if (t.drift.belt(r.a().cx, r.a().cz) < 0.25) {
                    continue;
                }
                diameters.add(2 * r.a().r);
                Rock best = nearestBeside(r, rocks);
                if (best != null) {
                    gaps.add(gap(r.a(), best.a()));
                    tops.add(Math.abs(best.a().top() - r.a().top()));
                }
            }
        }
        System.out.printf(Locale.ROOT, "Drift layout: %d belt rocks; across median %.1f (10th %.1f, 90th %.1f); gap to the nearest rock beside "
                        + "median %.1f (25th %.1f, 75th %.1f, 90th %.1f); tops apart median %.1f (75th %.1f)%n",
                diameters.size(), pct(diameters, 50), pct(diameters, 10), pct(diameters, 90), pct(gaps, 50), pct(gaps, 25), pct(gaps, 75),
                pct(gaps, 90), pct(tops, 50), pct(tops, 75));
        assertTrue(diameters.size() > 1000, "rocks surveyed: " + diameters.size());
        assertTrue(pct(diameters, 50) >= 15 && pct(diameters, 50) <= 22, "a typical rock is about 18 across");
        assertTrue(pct(gaps, 50) >= 5 && pct(gaps, 50) <= 10, "the typical gap is a running jump");
        assertTrue(pct(tops, 50) <= 5, "neighbours' tops within 5");
        assertTrue(pct(gaps, 90) >= 12, "some wide gaps stay, for the dash, the Drift and the Manta");
    }

    /** The nearest rock beside {@code r} (not stacked above or below it), of any layer. */
    private static Rock nearestBeside(Rock r, List<Rock> rocks) {
        Rock best = null;
        double bestGap = Double.MAX_VALUE;
        for (Rock o : rocks) {
            if (o == r || Math.hypot(o.a().cx - r.a().cx, o.a().cz - r.a().cz) <= Math.max(o.a().meanRadius(), r.a().meanRadius())) {
                continue;
            }
            double g = gap(r.a(), o.a());
            if (g < bestGap) {
                bestGap = g;
                best = o;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ the gaps and the stones in them

    /**
     * Two neighbouring rocks of one layer, the open ground between them (edge to edge across the ground, the rocks taken as balls
     * of their mean radius), how far their tops differ, which of the four directions joins them, and the stone laid in the gap (or null).
     */
    private record Link(Rock a, DriftBelts.Asteroid b, double open, double drop, int direction, DriftBelts.Asteroid stone) {}

    private static double open(double ax, double az, double ar, double bx, double bz, double br) {
        return Math.hypot(bx - ax, bz - az) - ar - br;
    }

    private static List<Link> links(AetheriaTerrain t, List<Rock> rocks) {
        List<Link> out = new ArrayList<>();
        for (Rock r : rocks) {
            DriftBelts.Asteroid a = r.a();
            DriftBelts.Asteroid[] stones = t.drift.stones(r.i(), r.j(), r.k());
            for (int d = 0; d < DIRECTIONS.length; d++) {
                DriftBelts.Asteroid b = t.drift.asteroid(r.i() + DIRECTIONS[d][0], r.j() + DIRECTIONS[d][1], r.k());
                if (b == null) {
                    continue;
                }
                // the stone this link holds, if it is laid: the generator names the one it would lay (stoneCandidate), and the
                // cell's laid stones say whether it did
                DriftBelts.Asteroid candidate = t.drift.stoneCandidate(r.i(), r.j(), r.k(), d);
                DriftBelts.Asteroid stone = null;
                if (candidate != null) {
                    for (DriftBelts.Asteroid s : stones) {
                        if (s.seed == candidate.seed) {
                            stone = s;
                        }
                    }
                }
                out.add(new Link(r, b, open(a.cx, a.cz, a.meanRadius(), b.cx, b.cz, b.meanRadius()), Math.abs(a.top() - b.top()), d, stone));
            }
        }
        return out;
    }

    private static Crossing worse(Crossing x, Crossing y) {
        return x.ordinal() >= y.ordinal() ? x : y;
    }

    private static Crossing better(Crossing x, Crossing y) {
        return x.ordinal() <= y.ordinal() ? x : y;
    }

    /** What the hop from a rock onto the stone beside it needs, taken from the higher of the two tops. */
    private static Crossing hopOnto(DriftBelts.Asteroid rock, DriftBelts.Asteroid stone) {
        return DriftReach.crossing(open(rock.cx, rock.cz, rock.meanRadius(), stone.cx, stone.cz, stone.r), Math.abs(rock.top() - (stone.cy + stone.r)));
    }

    /** What the bare gap needs. */
    private static Crossing bare(Link l) {
        return DriftReach.crossing(l.open(), l.drop());
    }

    /** What a player meets in a link: the easier of the bare gap and the two hops through its stone (the worse of them). */
    private static Crossing met(Link l) {
        if (l.stone() == null) {
            return bare(l);
        }
        return better(bare(l), worse(hopOnto(l.a().a(), l.stone()), hopOnto(l.b(), l.stone())));
    }

    private record Pair(long x, long y) {}

    @Test
    void theGapsSplitIntoJumpDashAndMount() {
        int[] met = new int[3];
        int[] open = new int[3];
        int[] nearest = new int[3];
        int[][] byDirection = new int[2][3];
        for (long salt : SALTS) {
            AetheriaTerrain t = AetheriaTerrain.forSalt(salt);
            List<Rock> rocks = survey(t);
            List<Link> links = links(t, rocks);
            Map<Pair, Link> byPair = new HashMap<>();
            for (Link l : links) {
                byPair.put(new Pair(Math.min(l.a().a().seed, l.b().seed), Math.max(l.a().a().seed, l.b().seed)), l);
                if (t.drift.belt(l.a().a().cx, l.a().a().cz) >= 0.25) {
                    met[met(l).ordinal()]++;
                    open[bare(l).ordinal()]++;
                    byDirection[l.direction() >= 2 ? 1 : 0][met(l).ordinal()]++;
                }
            }
            for (Rock r : rocks) {
                if (t.drift.belt(r.a().cx, r.a().cz) < 0.25) {
                    continue;
                }
                Rock n = nearestBeside(r, rocks);
                if (n == null) {
                    continue;
                }
                Link l = byPair.get(new Pair(Math.min(r.a().seed, n.a().seed), Math.max(r.a().seed, n.a().seed)));
                nearest[(l != null ? met(l)
                        : DriftReach.crossing(open(r.a().cx, r.a().cz, r.a().meanRadius(), n.a().cx, n.a().cz, n.a().meanRadius()),
                                Math.abs(r.a().top() - n.a().top()))).ordinal()]++;
            }
        }
        int all = met[0] + met[1] + met[2];
        int allOpen = open[0] + open[1] + open[2];
        int near = nearest[0] + nearest[1] + nearest[2];
        int straight = byDirection[0][0] + byDirection[0][1] + byDirection[0][2];
        int diagonal = byDirection[1][0] + byDirection[1][1] + byDirection[1][2];
        System.out.printf(Locale.ROOT, "Gaps as met (%d pairs of neighbouring rocks of one layer, stones counted): jump %.1f%%, dash %.1f%%, mount %.1f%%; "
                        + "straight pairs %d: jump %.1f%%, dash %.1f%%, mount %.1f%%; diagonal pairs %d: jump %.1f%%, dash %.1f%%, mount %.1f%%; "
                        + "the same without stones: jump %.1f%%, dash %.1f%%, mount %.1f%%; each rock's nearest gap as met: jump %.1f%%, dash %.1f%%, mount %.1f%%%n",
                all, 100.0 * met[0] / all, 100.0 * met[1] / all, 100.0 * met[2] / all,
                straight, 100.0 * byDirection[0][0] / straight, 100.0 * byDirection[0][1] / straight, 100.0 * byDirection[0][2] / straight,
                diagonal, 100.0 * byDirection[1][0] / diagonal, 100.0 * byDirection[1][1] / diagonal, 100.0 * byDirection[1][2] / diagonal,
                100.0 * open[0] / allOpen, 100.0 * open[1] / allOpen, 100.0 * open[2] / allOpen,
                100.0 * nearest[0] / near, 100.0 * nearest[1] / near, 100.0 * nearest[2] / near);
        double far = (byDirection[0][1] + byDirection[0][2]) / (double) straight;
        assertTrue(all > 1500 && straight > 700, "gaps surveyed: " + all + ", straight " + straight);
        assertTrue(far >= 0.22 && far <= 0.32, "about one straight gap in four stays a dash gap or a mount gap: " + far);
        assertTrue(byDirection[0][0] / (double) straight >= 0.65, "most straight gaps are running jumps: " + byDirection[0][0] / (double) straight);
        // The split between dash and mount holds on the rounded model only. On real block surfaces (the follow-up's probe over 878
        // straight pairs) the same pairs read jump 73%, dash 4%, mount 23%: the combined share above is what holds on both, so
        // the two minimums below guard the rule's two cases (a dash band left open, wide gaps left open), not a real share.
        assertTrue(byDirection[0][1] / (double) straight >= 0.06, "the dash band is left open: " + byDirection[0][1] / (double) straight);
        assertTrue(byDirection[0][2] / (double) straight >= 0.10, "wide gaps are left open: " + byDirection[0][2] / (double) straight);
    }

    /**
     * The rule in words: no stone in a gap the dash alone crosses; in any other gap of at least {@link DriftBelts#STONE_GAP} a stone
     * only where each hop it leaves is comfortable ({@link DriftReach#COMFORT}); a gap that is too wide for that stays open. The
     * stone a link would hold comes from {@code stoneCandidate}, so the stones that are not laid are judged too.
     */
    @Test
    void aStoneIsLaidOnlyWhereItLeavesComfortableHops() {
        int wide = 0;
        int laidStones = 0;
        int dashGaps = 0;
        int tooWide = 0;
        int unfit = 0;
        for (long salt : SALTS) {
            AetheriaTerrain t = AetheriaTerrain.forSalt(salt);
            for (Rock r : survey(t)) {
                DriftBelts.Asteroid a = r.a();
                DriftBelts.Asteroid[] laid = t.drift.stones(r.i(), r.j(), r.k());
                int wanted = 0;
                for (int d = 0; d < DIRECTIONS.length; d++) {
                    DriftBelts.Asteroid b = t.drift.asteroid(r.i() + DIRECTIONS[d][0], r.j() + DIRECTIONS[d][1], r.k());
                    if (b == null) {
                        continue;
                    }
                    double open = open(a.cx, a.cz, a.meanRadius(), b.cx, b.cz, b.meanRadius());
                    if (open < DriftBelts.STONE_GAP) {
                        continue;
                    }
                    wide++;
                    DriftBelts.Asteroid s = t.drift.stoneCandidate(r.i(), r.j(), r.k(), d);
                    if (s == null) {
                        unfit++;
                        continue;
                    }
                    boolean dash = DriftReach.crossing(open, Math.abs(a.top() - b.top())) == Crossing.DASH;
                    double stoneTop = s.cy + s.r;
                    boolean easy = DriftReach.comfortable(open(a.cx, a.cz, a.meanRadius(), s.cx, s.cz, s.r), Math.abs(a.top() - stoneTop))
                            && DriftReach.comfortable(open(b.cx, b.cz, b.meanRadius(), s.cx, s.cz, s.r), Math.abs(b.top() - stoneTop));
                    boolean wants = !dash && easy;
                    boolean isLaid = Arrays.stream(laid).anyMatch(x -> x.seed == s.seed);
                    assertEquals(wants, isLaid, String.format(Locale.ROOT, "a gap of %.1f between tops %.1f apart %s", open, Math.abs(a.top() - b.top()),
                            dash ? "needs the dash and holds a stone" : wants ? "lacks its stone" : "is too wide for comfortable hops and holds a stone"));
                    wanted += wants ? 1 : 0;
                    dashGaps += dash ? 1 : 0;
                    tooWide += !dash && !easy ? 1 : 0;
                }
                assertEquals(wanted, laid.length, "a cell lays the stones of the rule and no others");
                laidStones += laid.length;
            }
        }
        System.out.printf(Locale.ROOT, "Stones: %d gaps of %.0f blocks or more; %d stones laid, %d gaps left open for the dash, %d left open as too wide, %d without room%n",
                wide, DriftBelts.STONE_GAP, laidStones, dashGaps, tooWide, unfit);
        assertTrue(wide > 1500, "wide gaps judged: " + wide);
        assertTrue(laidStones > 500, "stones laid: " + laidStones);
        assertTrue(dashGaps > 50, "gaps left for the dash: " + dashGaps);
        assertTrue(tooWide > 200, "gaps left as too wide: " + tooWide);
        assertTrue(unfit <= wide / 100, "wide gaps whose stone has no place to go (between the layers, clear of the Breach): " + unfit);
    }

    // ------------------------------------------------------------------ the way through

    /** A pad to stand on: a rock (with the belt it belongs to, 0 for none, and whether it lies in the survey) or a stepping stone. */
    private record Pad(double x, double y, double z, double radius, double top, boolean stone, long belt, boolean inside) {}

    /** The belts tell themselves apart by their height and thickness: one belt, one pair of values. */
    private static long beltOf(AetheriaTerrain t, DriftBelts.Asteroid a) {
        DriftBelts.BeltPoint bp = t.drift.beltAt(a.cx, a.cz);
        return bp.strength() < 0.25 ? 0 : 31 * Double.doubleToLongBits(bp.centreY()) + Double.doubleToLongBits(bp.halfThickness());
    }

    private static List<Pad> pads(AetheriaTerrain t, int margin) {
        List<Pad> out = new ArrayList<>();
        int c = DriftBelts.CELL;
        for (int i = Math.floorDiv(OFFSET, c) - margin; i <= Math.floorDiv(OFFSET + SIZE, c) + margin; i++) {
            for (int j = Math.floorDiv(OFFSET, c) - margin; j <= Math.floorDiv(OFFSET + SIZE, c) + margin; j++) {
                for (int k = DriftBelts.K_MIN; k <= DriftBelts.K_MAX; k++) {
                    DriftBelts.Asteroid a = t.drift.asteroid(i, j, k);
                    if (a == null) {
                        continue;
                    }
                    boolean inside = a.cx >= OFFSET && a.cx < OFFSET + SIZE && a.cz >= OFFSET && a.cz < OFFSET + SIZE;
                    out.add(new Pad(a.cx, a.cy, a.cz, a.meanRadius(), a.top(), false, beltOf(t, a), inside));
                    for (DriftBelts.Asteroid s : t.drift.stones(i, j, k)) {
                        out.add(new Pad(s.cx, s.cy, s.cz, s.r, s.cy + s.r, true, 0, false));
                    }
                }
            }
        }
        return out;
    }

    private static final double GRID = 64.0;

    /** The pads a player on pad p can hop to: a running jump, plus the air dash if {@code dash}. */
    private static List<List<Integer>> hops(List<Pad> pads, boolean dash) {
        Map<Long, List<Integer>> grid = new HashMap<>();
        for (int n = 0; n < pads.size(); n++) {
            grid.computeIfAbsent(cell(pads.get(n).x(), pads.get(n).z()), k -> new ArrayList<>()).add(n);
        }
        List<List<Integer>> next = new ArrayList<>();
        for (int n = 0; n < pads.size(); n++) {
            Pad p = pads.get(n);
            List<Integer> to = new ArrayList<>();
            int cx = (int) Math.floor(p.x() / GRID);
            int cz = (int) Math.floor(p.z() / GRID);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int m : grid.getOrDefault(key(cx + dx, cz + dz), List.of())) {
                        if (m == n) {
                            continue;
                        }
                        Pad q = pads.get(m);
                        double hop = open(p.x(), p.z(), p.radius(), q.x(), q.z(), q.radius());
                        if (hop <= DriftReach.jump(q.top() - p.top()) + (dash ? DriftReach.DASH_BONUS : 0.0)) {
                            to.add(m);
                        }
                    }
                }
            }
            next.add(to);
        }
        return next;
    }

    private static long cell(double x, double z) {
        return key((int) Math.floor(x / GRID), (int) Math.floor(z / GRID));
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    /** What a walk from one pad reaches: the rocks of its own belt, and the farthest pad, in blocks. */
    private record Reach(int rocks, double far) {}

    private static Reach walk(List<Pad> pads, List<List<Integer>> next, int start) {
        boolean[] seen = new boolean[pads.size()];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(start);
        seen[start] = true;
        int rocks = 0;
        double far = 0;
        Pad s = pads.get(start);
        while (!queue.isEmpty()) {
            int p = queue.poll();
            if (pads.get(p).inside() && pads.get(p).belt() == s.belt()) {
                rocks++;
            }
            far = Math.max(far, Math.hypot(pads.get(p).x() - s.x(), pads.get(p).z() - s.z()));
            for (int q : next.get(p)) {
                if (!seen[q]) {
                    seen[q] = true;
                    queue.add(q);
                }
            }
        }
        return new Reach(rocks, far);
    }

    @Test
    void aWalkableRouteRunsThroughEveryBelt() {
        double worstShare = 1;
        double worstSpan = 1;
        int belts = 0;
        int starts = 0;
        int foot100 = 0;
        int dash100 = 0;
        List<Double> footFar = new ArrayList<>();
        List<Double> dashFar = new ArrayList<>();
        for (long salt : SALTS) {
            List<Pad> pads = pads(AetheriaTerrain.forSalt(salt), 6);
            List<List<Integer>> onFoot = hops(pads, false);
            List<List<Integer>> withDash = hops(pads, true);
            Map<Long, List<Integer>> members = new HashMap<>();
            for (int i = 0; i < pads.size(); i++) {
                if (pads.get(i).belt() != 0 && pads.get(i).inside()) {
                    members.computeIfAbsent(pads.get(i).belt(), k -> new ArrayList<>()).add(i);
                }
            }
            for (List<Integer> belt : members.values()) {
                if (belt.size() < 25) {
                    continue;
                }
                double extent = 0;
                for (int a : belt) {
                    for (int b : belt) {
                        extent = Math.max(extent, Math.hypot(pads.get(a).x() - pads.get(b).x(), pads.get(a).z() - pads.get(b).z()));
                    }
                }
                int best = 0;
                double bestFar = 0;
                for (int s : belt) {
                    Reach r = walk(pads, onFoot, s);
                    Reach d = walk(pads, withDash, s);
                    best = Math.max(best, r.rocks());
                    bestFar = Math.max(bestFar, r.far());
                    starts++;
                    foot100 += r.far() >= 100 ? 1 : 0;
                    dash100 += d.far() >= 100 ? 1 : 0;
                    footFar.add(r.far());
                    dashFar.add(d.far());
                }
                belts++;
                worstShare = Math.min(worstShare, best / (double) belt.size());
                worstSpan = Math.min(worstSpan, bestFar / Math.max(1.0, extent));
            }
        }
        System.out.printf(Locale.ROOT, "Walking: %d belts of 25 rocks or more; the best start reaches at least %.0f%% of a belt's rocks and, at its farthest, "
                        + "at least %.0f%% of the belt's extent; from %.0f%% of %d belt rocks a walk of 100 blocks or more (with the air dash %.0f%%); "
                        + "the farthest walk from a rock, median %.0f blocks (with the air dash %.0f)%n",
                belts, 100 * worstShare, 100 * worstSpan, 100.0 * foot100 / starts, starts, 100.0 * dash100 / starts, pct(footFar, 50), pct(dashFar, 50));
        assertTrue(belts >= 10, "belts surveyed: " + belts);
        assertTrue(worstShare >= 0.80, "a walk from one rock reaches most of every belt: " + worstShare);
        assertTrue(worstSpan >= 0.70, "a walkable route runs across every belt: " + worstSpan);
        assertTrue(foot100 / (double) starts >= 0.90, "from nearly every rock a walk of 100 blocks: " + foot100 / (double) starts);
    }

    // ------------------------------------------------------------------ the search window, the rounding, the landmarks

    /**
     * The terrain asks {@code candidates()} for every column, so a rock or stone the model holds that the search misses is never
     * generated (A2 quality review, Minor 2: delete the stone loop of {@code candidates()} and the other tests still passed).
     * For every rock and stone of the survey: it is found from its own centre column and from the four columns a block inside
     * its bounding circle, the farthest columns its footprint can reach; and a stone's centre is rock through {@code blocksAt},
     * the path the chunk generator takes.
     */
    @Test
    void theColumnSearchFindsEveryRockAndStoneTheModelHolds() {
        int rocks = 0;
        int stones = 0;
        int solid = 0;
        for (long salt : SALTS) {
            AetheriaTerrain t = AetheriaTerrain.forSalt(salt);
            List<DriftBelts.Asteroid> found = new ArrayList<>();
            for (Rock r : survey(t)) {
                assertTrue(r.a().bound <= 1.2 * DriftBelts.CELL, "a rock reaching past its neighbours' cells: " + r.a().bound);
                assertFound(t, r.a(), found, "rock");
                rocks++;
                for (DriftBelts.Asteroid s : t.drift.stones(r.i(), r.j(), r.k())) {
                    assertFound(t, s, found, "stone");
                    stones++;
                    solid += rockNear(t, s) ? 1 : 0;
                }
            }
        }
        System.out.printf(Locale.ROOT, "Column search: %d rocks and %d stones found from five columns each; %d stones are rock where they sit%n", rocks, stones,
                solid);
        assertTrue(rocks > 1000 && stones > 500, "rocks and stones checked: " + rocks + " and " + stones);
        assertEquals(stones, solid, "every stone that is laid is rock in the generated blocks");
    }

    private static void assertFound(AetheriaTerrain t, DriftBelts.Asteroid a, List<DriftBelts.Asteroid> found, String what) {
        int reach = (int) Math.floor(a.bound) - 1;
        int cx = (int) Math.floor(a.cx);
        int cz = (int) Math.floor(a.cz);
        int[][] columns = {{0, 0}, {reach, 0}, {-reach, 0}, {0, reach}, {0, -reach}};
        for (int[] c : columns) {
            t.drift.candidates(cx + c[0], cz + c[1], found);
            assertTrue(found.stream().anyMatch(x -> x.seed == a.seed), String.format(Locale.ROOT, "a %s at (%.0f, %.0f, %.0f) is missed from the column %d, %d blocks "
                    + "from its centre", what, a.cx, a.cy, a.cz, c[0], c[1]));
        }
    }

    /** Some block within one of a stone's centre is rock: the chunk generator reads the density there through the same search. */
    private static boolean rockNear(AetheriaTerrain t, DriftBelts.Asteroid s) {
        int cx = (int) Math.floor(s.cx);
        int cy = (int) Math.floor(s.cy);
        int cz = (int) Math.floor(s.cz);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (t.blocksAt(cx + dx, cy + dy, cz + dz) > 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Test
    void theRoundedGapsMatchTheRealRock() {
        AetheriaTerrain t = AetheriaTerrain.forSalt(SALTS[1]);
        List<Rock> rocks = survey(t);
        Random random = new Random(5);
        List<Double> errors = new ArrayList<>();
        for (int n = 0; n < 4000 && errors.size() < 150; n++) {
            Rock r = rocks.get(random.nextInt(rocks.size()));
            DriftBelts.Asteroid b = t.drift.asteroid(r.i() + 1, r.j(), r.k());
            if (b == null || t.drift.belt(r.a().cx, r.a().cz) < 0.25) {
                continue;
            }
            DriftBelts.Asteroid a = r.a();
            double len = Math.sqrt(TMath.sq(b.cx - a.cx) + TMath.sq(b.cy - a.cy) + TMath.sq(b.cz - a.cz));
            int steps = (int) Math.ceil(len / 0.25);
            boolean[] rock = new boolean[steps + 1];
            for (int s = 0; s <= steps; s++) {
                double f = s / (double) steps;
                int x = (int) Math.floor(a.cx + (b.cx - a.cx) * f);
                int y = (int) Math.floor(a.cy + (b.cy - a.cy) * f);
                int z = (int) Math.floor(a.cz + (b.cz - a.cz) * f);
                rock[s] = Math.max(t.drift.density(a, x, y, z), t.drift.density(b, x, y, z)) > 0;
            }
            if (!rock[0] || !rock[steps]) {
                continue;
            }
            int out = 0;
            while (out < steps && rock[out]) {
                out++;
            }
            int in = steps;
            while (in > 0 && rock[in]) {
                in--;
            }
            double real = Math.max(0, (in - out + 1) * len / steps);
            errors.add(Math.abs(real - Math.max(0, gap(a, b))));
        }
        double median = pct(errors, 50);
        System.out.printf(Locale.ROOT, "Rounded gaps against the real rock: %d lines, median error %.1f, 90th %.1f%n", errors.size(), median,
                pct(errors, 90));
        assertTrue(errors.size() >= 100, "lines checked: " + errors.size());
        assertTrue(median <= 3.0, "the rounding is close enough to judge jumps by");
    }

    /**
     * The colored-lights puzzle's tower and the landing rocks stay reachable (A2 quality review, Minor 3: this test used to count a
     * copy of the site rule without its height check, and could not fail if the real rule starved). It asks the real rule,
     * {@code GyreObservatoryStructure.site}, at random belt points of strength 0.5 or more and wants it to find a site at least as
     * often as 1.0.3 did there (58%, the A2 spec review's figure on the same kind of points; the rule finds about 70% now).
     */
    @Test
    void theLandmarksStillFindRock() {
        int sites = 0;
        int tries = 0;
        int landings = 0;
        for (long salt : SALTS) {
            AetheriaTerrain t = AetheriaTerrain.forSalt(salt);
            Random random = new Random(salt);
            for (int n = 0; n < 2000; n++) {
                int x = OFFSET + random.nextInt(SIZE);
                int z = OFFSET + random.nextInt(SIZE);
                if (t.drift.belt(x, z) < 0.5) {
                    continue;
                }
                tries++;
                sites += GyreObservatoryStructure.site(t, x, z).isPresent() ? 1 : 0;
            }
            for (Rock r : survey(t)) {
                if (r.a().r >= 7 && !r.a().shard && r.a().flatTop < Double.POSITIVE_INFINITY) {
                    landings++;
                }
            }
        }
        double share = sites / (double) tries;
        System.out.printf(Locale.ROOT, "Observatory sites (the real rule) at %.0f%% of %d belt points; %d flat landing rocks in %d surveys%n", 100 * share, tries,
                landings, SALTS.length);
        assertTrue(tries > 600, "belt points sampled: " + tries);
        assertTrue(share >= 0.58, "the Observatory is found at least as often as under 1.0.3 (58% of belt points): " + share);
        assertTrue(landings >= 100, "flat-topped rocks to land on");
    }
}
