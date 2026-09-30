package com.cosmicbreach.structure.lens;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Finds the fewest moves that solve a Lens Array (GDD 6.2): a move is one press on a mirror or splitter (a
 * 45 or 90 degree turn), carrying a loose mirror or filter to another pedestal, or knocking an Umbral block one
 * tile. Every receptor must be lit with its colour at once.
 *
 * <p><b>How.</b> A breadth-first deepening over rotations, loose-piece placements and pushes: for a move budget
 * of 0, 1, 2 and so on it searches every way to spend at most that budget, and the first budget with a solution
 * is the minimum. Moves only matter where light goes, and moves on different pieces can be made in any order,
 * so the search follows the beam from the source and settles each pedestal's final state the first time light
 * reaches it: keep it, turn it, take a loose piece off it, set a loose piece on it, or knock its Umbral block
 * aside. A pedestal light has passed is settled and nothing may change it. Settling in beam order reaches every
 * final layout a player can build (a later move on a pedestal the beam already crossed would change the beam
 * there), so the minimum is exact. Useless states are skipped: a mirror square to the beam only sends it back
 * along its own path (to the source or a prism's back, where it dies), and a prism turned away from the beam only
 * absorbs it, so neither can light anything that blocking would not.
 *
 * <p>Cost: each piece's moves are counted once, from where the scramble left it, whatever the order.
 */
public final class LensSolver {
    /** Search nodes one call may spend before it gives up (the answer is then "unknown"). */
    public static final long NODE_LIMIT = 4_000_000L;

    private static final int HOME = 0;
    private static final int TAKEN = 1;
    private static final int PLACED = 2;

    private final int n;
    private final int[] ports;
    private final int want;
    private final int[] cur;
    private final boolean[] decided;
    private final boolean[] seen;
    // loose pieces: where each started, what it is (with its starting turn), and where it is now
    private final int looseCount;
    private final int[] looseHome;
    private final int[] loosePiece;
    private final int[] looseStatus;
    private final int[] looseAt;
    // pending beams: x, z, heading, colour
    private final int[] fronts = new int[4 * 128];
    private int top;
    private int lit;
    private int bound;
    private long nodes;
    private boolean aborted;
    private int[] found;
    // the stretch bound: per receptor port, its entry line (border cell first) and which cells it is charged for
    private final int[] receptorPorts;
    private final int[][] lines;
    private final boolean[][] charged;
    /** Per cell, the receptor (index into {@link #receptorPorts}) whose line claims it, or -1. */
    private final int[] claim;

    private LensSolver(int n, int[] start, int[] ports) {
        this.n = n;
        this.ports = ports;
        int w = 0;
        for (int p = 0; p < ports.length; p++) {
            if (Lens.isReceptor(ports[p])) {
                w |= 1 << p;
            }
        }
        this.want = w;
        this.cur = start.clone();
        this.decided = new boolean[n * n];
        this.seen = new boolean[n * n * 16];
        int count = 0;
        for (int c : start) {
            if (Lens.loose(c)) {
                count++;
            }
        }
        this.looseCount = count;
        this.looseHome = new int[count];
        this.loosePiece = new int[count];
        this.looseStatus = new int[count];
        this.looseAt = new int[count];
        int j = 0;
        for (int i = 0; i < start.length; i++) {
            if (Lens.loose(start[i])) {
                looseHome[j] = i;
                loosePiece[j] = start[i];
                looseAt[j] = i;
                j++;
            }
            if (Lens.kind(start[i]) == Lens.SOURCE) {
                decided[i] = true;
            }
        }
        // each receptor's entry line; a cell on two lines is charged to the first only, so the sum stays a bound
        this.receptorPorts = new int[Integer.bitCount(w)];
        this.lines = new int[receptorPorts.length][];
        this.charged = new boolean[receptorPorts.length][];
        boolean[] claimed = new boolean[n * n];
        this.claim = new int[n * n];
        Arrays.fill(claim, -1);
        int r = 0;
        for (int p = 0; p < ports.length; p++) {
            if (!Lens.isReceptor(ports[p])) {
                continue;
            }
            receptorPorts[r] = p;
            int in = Lens.opposite(Lens.portHeading(n, p));
            int cell = Lens.portCell(n, p);
            lines[r] = new int[n];
            charged[r] = new boolean[n];
            int x = cell % n;
            int z = cell / n;
            for (int k = 0; k < n; k++) {
                int c = (z + Lens.DZ[in] * k) * n + x + Lens.DX[in] * k;
                lines[r][k] = c;
                charged[r][k] = !claimed[c];
                if (!claimed[c]) {
                    claim[c] = r;
                }
                claimed[c] = true;
            }
            r++;
        }
    }

    /** The outcome of one search. */
    public record Answer(int moves, boolean known, long nodes, int[] layout) {
        /** Fewest moves, or -1 if there is no solution within the limit asked (or the search gave up). */
        public int moves() {
            return moves;
        }
    }

    /**
     * The fewest moves solving {@code start}, searching budgets up to {@code limit}. {@link Answer#moves} is -1 if
     * none is within {@code limit}; {@link Answer#known} is false if the node limit cut the search short.
     */
    public static Answer solve(int n, int[] start, int[] ports, int limit) {
        long total = 0;
        for (int b = 0; b <= limit; b++) {
            LensSolver s = new LensSolver(n, start, ports);
            s.bound = b;
            boolean ok = s.run();
            total += s.nodes;
            if (s.aborted) {
                return new Answer(-1, false, total, null);
            }
            if (ok) {
                return new Answer(b, true, total, s.found);
            }
        }
        return new Answer(-1, true, total, null);
    }

    /**
     * True if some solution needs at most {@code budget} moves; false if none does. Null if the search gave up.
     * One pass at the full budget (cheaper than {@link #solve} when only the threshold matters).
     */
    public static Boolean solvableWithin(int n, int[] start, int[] ports, int budget) {
        LensSolver s = new LensSolver(n, start, ports);
        s.bound = budget;
        boolean ok = s.run();
        if (s.aborted) {
            return null;
        }
        return ok;
    }

    public static Answer solve(LensPuzzle p, int limit) {
        return solve(p.size(), p.start(), p.ports(), limit);
    }

    private boolean run() {
        if (want == 0) {
            return false;
        }
        int src = BeamTrace.sourceOf(cur);
        if (src < 0) {
            return false;
        }
        push(src % n, src / n, Lens.turn(cur[src]), Lens.WHITE);
        try {
            return next(0);
        } catch (Abort e) {
            aborted = true;
            return false;
        }
    }

    private static final class Abort extends RuntimeException {
        Abort() {
            super(null, null, false, false);
        }
    }

    private void push(int x, int z, int d, int color) {
        fronts[top * 4] = x;
        fronts[top * 4 + 1] = z;
        fronts[top * 4 + 2] = d;
        fronts[top * 4 + 3] = color;
        top++;
    }

    /** Walks the next pending beam, or checks the receptors when none is left. */
    private boolean next(int cost) {
        if (top == 0) {
            if ((lit & want) == want) {
                found = finalLayout();
                return true;
            }
            return false;
        }
        top--;
        int o = top * 4;
        int x = fronts[o];
        int z = fronts[o + 1];
        int d = fronts[o + 2];
        int c = fronts[o + 3];
        boolean r = step(x, z, d, c, cost);
        fronts[o] = x;
        fronts[o + 1] = z;
        fronts[o + 2] = d;
        fronts[o + 3] = c;
        top++;
        return r;
    }

    /** The beam at (x, z) moves one cell on with heading {@code d}. */
    private boolean step(int x, int z, int d, int color, int cost) {
        if (++nodes > NODE_LIMIT) {
            throw new Abort();
        }
        int nx = x + Lens.DX[d];
        int nz = z + Lens.DZ[d];
        if (nx < 0 || nx >= n || nz < 0 || nz >= n) {
            int port = Lens.portLeaving(n, x, z, d);
            int p = ports[port];
            int saved = lit;
            if (Lens.isReceptor(p) && Lens.receptorColor(p) == color) {
                lit |= 1 << port;
            }
            boolean r = next(cost);
            lit = saved;
            return r;
        }
        int i = nz * n + nx;
        int key = (i * 4 + d) * 4 + color;
        if (seen[key]) {
            return next(cost);
        }
        seen[key] = true;
        boolean r = decided[i] ? follow(cur[i], nx, nz, d, color, cost) : settle(i, nx, nz, d, color, cost);
        seen[key] = false;
        return r;
    }

    /** What a settled pedestal does to the beam. */
    private boolean follow(int c, int x, int z, int d, int color, int cost) {
        switch (Lens.kind(c)) {
            case Lens.EMPTY:
                return step(x, z, d, color, cost);
            case Lens.FILTER:
                return step(x, z, d, Lens.color(c), cost);
            case Lens.MIRROR: {
                int out = Lens.mirrorOut(Lens.turn(c), d);
                if (out == Lens.opposite(d)) {
                    return next(cost);
                }
                return step(x, z, out, color, cost);
            }
            case Lens.SPLITTER: {
                if (Lens.turn(c) != d) {
                    return next(cost);
                }
                push(x, z, Lens.cw(d), color);
                boolean r = step(x, z, Lens.ccw(d), color, cost);
                top--;
                return r;
            }
            default:
                return next(cost);
        }
    }

    /** The beam reaches a pedestal nobody has settled: try each final state it could be given. */
    private boolean settle(int i, int x, int z, int d, int color, int cost) {
        int lower = stretchBound();
        if (top == 0 && lower < (1 << 20)) {
            lower += frontBound(i, d);
        }
        if (cost + lower > bound) {
            return false;
        }
        int c = cur[i];
        int k = Lens.kind(c);
        if (k == Lens.EMPTY) {
            return empty(i, x, z, d, color, cost);
        }
        if (k == Lens.UMBRAL) {
            // leave it (the beam dies here), or knock it off: onto a free, unsettled pedestal, one knock per
            // tile, passing over pedestals the light already crossed empty (it may not stay on those)
            if (keep(i, c, x, z, d, color, cost)) {
                return true;
            }
            if (cost + 1 > bound) {
                return false;
            }
            // where it can end: {destination, knocks, what makes room there (-1, a loose piece, or an Umbral
            // block knocked on to that pedestal)}
            List<int[]> ends = knockEnds(i);
            for (int want = 1; want <= bound - cost; want++) {
                for (int[] e : ends) {
                    if (e[1] != want) {
                        continue;
                    }
                    int m = e[0];
                    int was = cur[m];
                    int room = e[2];
                    int j2 = -1;
                    if (room >= 0 && Lens.kind(was) == Lens.UMBRAL) {
                        cur[room] = was;
                    } else if (Lens.loose(was)) {
                        j2 = looseAt(m);
                        looseStatus[j2] = TAKEN;
                        looseAt[j2] = -1;
                    }
                    cur[m] = c;
                    cur[i] = 0;
                    boolean r = empty(i, x, z, d, color, cost + want);
                    cur[i] = c;
                    cur[m] = was;
                    if (room >= 0 && Lens.kind(was) == Lens.UMBRAL) {
                        cur[room] = 0;
                    }
                    if (j2 >= 0) {
                        looseStatus[j2] = HOME;
                        looseAt[j2] = m;
                    }
                    if (r) {
                        return true;
                    }
                }
            }
            return false;
        }
        int j = Lens.loose(c) ? looseAt(i) : -1;
        // With no other beam pending, ways that only end this beam are all alike (nothing else will ever
        // cross this pedestal), so only the cheapest is tried. With beams pending, every state is tried: a
        // later beam may cross here and need the piece set for it.
        boolean alone = top == 0;
        if (k == Lens.MIRROR) {
            int t = Lens.turn(c);
            boolean deadTried = false;
            for (int want = 0; want <= 2; want++) {
                for (int tt = 0; tt < 4; tt++) {
                    int add = Lens.turnDistance(t, tt);
                    if (add != want || cost + add > bound) {
                        continue;
                    }
                    int out = Lens.mirrorOut(tt, d);
                    if (alone && (out == Lens.opposite(d) || !leadsSomewhere(x, z, out))) {
                        if (deadTried) {
                            continue;
                        }
                        deadTried = true;
                    }
                    if (keep(i, Lens.withTurn(c, tt), x, z, d, color, cost + add)) {
                        return true;
                    }
                }
            }
        } else if (k == Lens.SPLITTER) {
            int t = Lens.turn(c);
            for (int want = 0; want <= 2; want++) {
                for (int acc = 0; acc < 4; acc++) {
                    int add = Lens.turnDistance(t, acc);
                    if (add != want || cost + add > bound || (alone && acc != t && acc != d)) {
                        continue;
                    }
                    if (keep(i, Lens.withTurn(c, acc), x, z, d, color, cost + add)) {
                        return true;
                    }
                }
            }
        } else if (keep(i, c, x, z, d, color, cost)) {
            return true;
        }
        // a loose piece may also be taken away (one move), leaving an empty pedestal
        if (j >= 0 && cost + 1 <= bound) {
            looseStatus[j] = TAKEN;
            looseAt[j] = -1;
            cur[i] = 0;
            boolean r = empty(i, x, z, d, color, cost + 1);
            cur[i] = c;
            looseStatus[j] = HOME;
            looseAt[j] = i;
            return r;
        }
        return false;
    }

    /**
     * Where the Umbral block at {@code from} can be knocked to, and for how many moves: over pedestals the light
     * already crossed empty (only passed over), onto an unsettled empty pedestal; or onto one holding a loose
     * piece that is carried off first (one move more), or another Umbral block knocked on first (one more).
     * Each entry is {destination, moves, the pedestal the other Umbral block goes to or -1}.
     */
    private List<int[]> knockEnds(int from) {
        List<int[]> out = new ArrayList<>();
        int[] queue = new int[n * n];
        int[] steps = new int[n * n];
        boolean[] visited = new boolean[n * n];
        int head = 0;
        int tail = 0;
        queue[tail++] = from;
        visited[from] = true;
        while (head < tail) {
            int at = queue[head++];
            for (int e = 0; e < 4; e++) {
                int mx = at % n + Lens.DX[e];
                int mz = at / n + Lens.DZ[e];
                if (mx < 0 || mx >= n || mz < 0 || mz >= n) {
                    continue;
                }
                int m = mz * n + mx;
                if (visited[m]) {
                    continue;
                }
                int c = cur[m];
                int k = Lens.kind(c);
                int dist = steps[at] + 1;
                if (decided[m]) {
                    if (k == Lens.EMPTY) {
                        visited[m] = true;
                        steps[m] = dist;
                        queue[tail++] = m;
                    }
                    continue;
                }
                visited[m] = true;
                if (k == Lens.EMPTY) {
                    out.add(new int[] {m, dist, -1});
                } else if (Lens.loose(c) && looseAt(m) >= 0) {
                    out.add(new int[] {m, dist + 1, -1});
                } else if (k == Lens.UMBRAL) {
                    for (int f = 0; f < 4; f++) {
                        int qx = mx + Lens.DX[f];
                        int qz = mz + Lens.DZ[f];
                        int q = qz * n + qx;
                        if (qx >= 0 && qx < n && qz >= 0 && qz < n && q != from && !decided[q] && Lens.kind(cur[q]) == Lens.EMPTY) {
                            out.add(new int[] {m, dist + 1, q});
                        }
                    }
                }
            }
        }
        return out;
    }

    /** Settles pedestal {@code i} as {@code c} and lets the beam meet it. */
    private boolean keep(int i, int c, int x, int z, int d, int color, int cost) {
        int was = cur[i];
        cur[i] = c;
        decided[i] = true;
        boolean r = follow(c, x, z, d, color, cost);
        decided[i] = false;
        cur[i] = was;
        return r;
    }

    /** An empty, unsettled pedestal: leave it empty, or set a loose piece on it. */
    private boolean empty(int i, int x, int z, int d, int color, int cost) {
        if (keep(i, 0, x, z, d, color, cost)) {
            return true;
        }
        for (int j = 0; j < looseCount; j++) {
            int status = looseStatus[j];
            if (status == PLACED || (status == HOME && (decided[looseHome[j]] || looseHome[j] == i))) {
                continue;
            }
            int move = status == HOME ? 1 : 0;
            int piece = loosePiece[j];
            if (Lens.kind(piece) == Lens.MIRROR) {
                for (int side = 0; side < 2; side++) {
                    int out = side == 0 ? Lens.ccw(d) : Lens.cw(d);
                    if (top == 0 && !leadsSomewhere(x, z, out)) {
                        continue;
                    }
                    int tt = Lens.mirrorTurnFor(d, out);
                    int add = move + Lens.turnDistance(Lens.turn(piece), tt);
                    if (cost + add > bound) {
                        continue;
                    }
                    if (place(j, i, Lens.withTurn(piece, tt), x, z, d, color, cost + add)) {
                        return true;
                    }
                }
            } else if (Lens.kind(piece) == Lens.FILTER && (Lens.color(piece) != color || top > 0) && cost + move <= bound) {
                if (place(j, i, piece, x, z, d, color, cost + move)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean place(int j, int i, int piece, int x, int z, int d, int color, int cost) {
        int status = looseStatus[j];
        int home = looseHome[j];
        int homeWas = cur[home];
        if (status == HOME) {
            cur[home] = 0;
        }
        looseStatus[j] = PLACED;
        looseAt[j] = i;
        boolean r = keep(i, piece, x, z, d, color, cost);
        looseStatus[j] = status;
        looseAt[j] = status == HOME ? home : -1;
        cur[home] = homeWas;
        return r;
    }

    private int looseAt(int cell) {
        for (int j = 0; j < looseCount; j++) {
            if (looseAt[j] == cell && looseStatus[j] == HOME) {
                return j;
            }
        }
        return -1;
    }

    /**
     * False if light leaving (x, z) with heading {@code d} can reach nothing that could still matter: its line
     * ends at a wall that is not an unlit receptor, and nothing on the way can turn it (no settled mirror or
     * splitter that would, no unsettled piece, no empty pedestal a loose mirror could still be set on). Turning a
     * beam that way only ends it, which leaving it alone does as well or better.
     */
    private boolean leadsSomewhere(int x, int z, int d) {
        boolean spare = placeCost() != Integer.MAX_VALUE;
        while (true) {
            int nx = x + Lens.DX[d];
            int nz = z + Lens.DZ[d];
            if (nx < 0 || nx >= n || nz < 0 || nz >= n) {
                int port = Lens.portLeaving(n, x, z, d);
                return Lens.isReceptor(ports[port]) && (lit & 1 << port) == 0;
            }
            int i = nz * n + nx;
            int c = cur[i];
            int k = Lens.kind(c);
            if (!decided[i]) {
                if (k != Lens.EMPTY || spare) {
                    return true;
                }
            } else if (k == Lens.MIRROR) {
                int out = Lens.mirrorOut(Lens.turn(c), d);
                if (out != d) {
                    return out != Lens.opposite(d);
                }
            } else if (k == Lens.SPLITTER) {
                return Lens.turn(c) == d;
            } else if (k == Lens.UMBRAL || k == Lens.SOURCE) {
                return false;
            }
            x = nx;
            z = nz;
        }
    }

    /**
     * A lower bound on the moves still needed: light reaches a receptor only along its entry line, travelling
     * out, after entering the line at some pedestal (turned there by a mirror or splitter, or born there). So
     * each unlit receptor needs, for some entry pedestal on its line, that pedestal able to turn light outward
     * and every pedestal between it and the wall able to let light pass. Their cheapest cost from the current
     * state, summed over unlit receptors with each pedestal counted for one receptor only, never exceeds the
     * true remainder. Colour and the way light gets to the entry pedestal are ignored (that only lowers it).
     */
    private int stretchBound() {
        int total = 0;
        int place = placeCost();
        for (int r = 0; r < receptorPorts.length; r++) {
            if ((lit & 1 << receptorPorts[r]) != 0) {
                continue;
            }
            int out = Lens.portHeading(n, receptorPorts[r]);
            int best = Integer.MAX_VALUE;
            int passing = 0;
            int[] line = lines[r];
            for (int k = 0; k < n && passing < best; k++) {
                int i = line[k];
                int entry = entryCost(i, out, place);
                if (entry != Integer.MAX_VALUE) {
                    int cost = passing + (charged[r][k] ? entry : 0);
                    best = Math.min(best, cost);
                }
                int pass = passCost(i, out);
                if (pass == Integer.MAX_VALUE) {
                    break;
                }
                passing += charged[r][k] ? pass : 0;
            }
            if (best == Integer.MAX_VALUE) {
                return 1 << 20;
            }
            total += best;
        }
        return total;
    }

    /**
     * With no other beam left and receptors still dark, the beam now at pedestal {@code i} (heading {@code d})
     * must light them: on its line, at {@code i} or beyond, it must be turned or split, or reach a dark receptor
     * at the line's end. The cheapest way, counting only pedestals no dark receptor's line already counts.
     */
    private int frontBound(int i, int d) {
        if ((lit & want) == want) {
            return 0;
        }
        int place = placeCost();
        int best = Integer.MAX_VALUE;
        int passing = 0;
        int x = i % n;
        int z = i / n;
        while (passing < best) {
            int c = z * n + x;
            boolean counts = claim[c] < 0 || (lit & 1 << receptorPorts[claim[c]]) != 0;
            int turn = turnCost(c, d, place);
            if (turn != Integer.MAX_VALUE) {
                best = Math.min(best, passing + (counts ? turn : 0));
            }
            int pass = passCost(c, d);
            if (pass == Integer.MAX_VALUE) {
                break;
            }
            passing += counts ? pass : 0;
            int nx = x + Lens.DX[d];
            int nz = z + Lens.DZ[d];
            if (nx < 0 || nx >= n || nz < 0 || nz >= n) {
                int port = Lens.portLeaving(n, x, z, d);
                if (Lens.isReceptor(ports[port]) && (lit & 1 << port) == 0) {
                    best = Math.min(best, passing);
                }
                break;
            }
            x = nx;
            z = nz;
        }
        return best == Integer.MAX_VALUE ? 1 << 20 : best;
    }

    /** Cheapest moves for pedestal {@code i} to turn or split light arriving with heading {@code d}. */
    private int turnCost(int i, int d, int place) {
        int c = cur[i];
        int k = Lens.kind(c);
        if (decided[i]) {
            return switch (k) {
                case Lens.MIRROR -> {
                    int out = Lens.mirrorOut(Lens.turn(c), d);
                    yield out != d && out != Lens.opposite(d) ? 0 : Integer.MAX_VALUE;
                }
                case Lens.SPLITTER -> Lens.turn(c) == d ? 0 : Integer.MAX_VALUE;
                default -> Integer.MAX_VALUE;
            };
        }
        return switch (k) {
            case Lens.MIRROR -> {
                int t = Lens.turn(c);
                int rot = Math.min(Lens.turnDistance(t, 1), Lens.turnDistance(t, 3));
                yield Lens.loose(c) ? Math.min(rot, place) : rot;
            }
            case Lens.SPLITTER -> Lens.turnDistance(Lens.turn(c), d);
            case Lens.EMPTY -> place;
            case Lens.UMBRAL -> place == Integer.MAX_VALUE ? Integer.MAX_VALUE : 1 + place;
            case Lens.FILTER -> Lens.loose(c) ? place : Integer.MAX_VALUE;
            default -> Integer.MAX_VALUE;
        };
    }

    /** Cheapest moves for pedestal {@code i} to let light heading {@code out} straight through. */
    private int passCost(int i, int out) {
        int c = cur[i];
        int k = Lens.kind(c);
        if (decided[i]) {
            return switch (k) {
                case Lens.EMPTY, Lens.FILTER -> 0;
                case Lens.MIRROR -> Lens.mirrorOut(Lens.turn(c), out) == out ? 0 : Integer.MAX_VALUE;
                default -> Integer.MAX_VALUE;
            };
        }
        return switch (k) {
            case Lens.EMPTY, Lens.FILTER -> 0;
            case Lens.UMBRAL -> 1;
            case Lens.MIRROR -> {
                // a loose one could be carried off; that move is counted where it is set down (if anywhere
                // counted), so here it is free: charging it twice would break the bound
                int par = (out == Lens.EAST || out == Lens.WEST) ? 0 : 2;
                yield Lens.loose(c) ? 0 : Lens.turnDistance(Lens.turn(c), par);
            }
            default -> Integer.MAX_VALUE;
        };
    }

    /** Cheapest moves for pedestal {@code i} to send light out with heading {@code out} (from some side). */
    private int entryCost(int i, int out, int place) {
        int c = cur[i];
        int k = Lens.kind(c);
        if (decided[i]) {
            return switch (k) {
                case Lens.MIRROR -> (Lens.turn(c) & 1) == 1 ? 0 : Integer.MAX_VALUE;
                case Lens.SPLITTER -> Lens.turn(c) == Lens.cw(out) || Lens.turn(c) == Lens.ccw(out) ? 0 : Integer.MAX_VALUE;
                case Lens.SOURCE -> Lens.turn(c) == out ? 0 : Integer.MAX_VALUE;
                default -> Integer.MAX_VALUE;
            };
        }
        return switch (k) {
            case Lens.MIRROR -> {
                int t = Lens.turn(c);
                int rot = Math.min(Lens.turnDistance(t, 1), Lens.turnDistance(t, 3));
                yield Lens.loose(c) ? Math.min(rot, place) : rot;
            }
            case Lens.SPLITTER -> Math.min(Lens.turnDistance(Lens.turn(c), Lens.cw(out)), Lens.turnDistance(Lens.turn(c), Lens.ccw(out)));
            case Lens.SOURCE -> Lens.turn(c) == out ? 0 : Integer.MAX_VALUE;
            case Lens.EMPTY -> place;
            case Lens.UMBRAL -> place == Integer.MAX_VALUE ? Integer.MAX_VALUE : 1 + place;
            default -> Integer.MAX_VALUE;
        };
    }

    /** Cheapest moves to set some loose mirror on an empty pedestal as a turn, or MAX_VALUE if none is free. */
    private int placeCost() {
        int best = Integer.MAX_VALUE;
        for (int j = 0; j < looseCount; j++) {
            if (Lens.kind(loosePiece[j]) != Lens.MIRROR || looseStatus[j] == PLACED
                    || (looseStatus[j] == HOME && decided[looseHome[j]])) {
                continue;
            }
            int t = Lens.turn(loosePiece[j]);
            int rot = Math.min(Lens.turnDistance(t, 1), Lens.turnDistance(t, 3));
            best = Math.min(best, (looseStatus[j] == HOME ? 1 : 0) + rot);
        }
        return best;
    }

    /** The grid as the search left it when it found a solution (unsettled pedestals as they are now). */
    private int[] finalLayout() {
        int[] out = cur.clone();
        // a loose piece taken off the light's way still stands somewhere: on a pedestal the light never crosses
        for (int j = 0; j < looseCount; j++) {
            if (looseStatus[j] != TAKEN) {
                continue;
            }
            for (int i = 0; i < out.length; i++) {
                if (!decided[i] && Lens.kind(out[i]) == Lens.EMPTY && i != looseHome[j]) {
                    out[i] = loosePiece[j];
                    break;
                }
            }
        }
        return out;
    }

    @Override
    public String toString() {
        return "LensSolver" + Arrays.toString(cur);
    }
}
