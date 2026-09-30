package com.cosmicbreach.structure.lens;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Makes Lens Array puzzles that are always solvable (GDD 6.2), working backwards from a solution:
 *
 * <ol>
 *   <li>Put the source on the grid's edge facing in, the receptors (1 to 3) on the walls, and pick their
 *       colours.</li>
 *   <li>Walk a path from the source to each receptor through free pedestals, with a mirror at every turn, a
 *       splitter where paths fork and a filter on the stretch before a coloured receptor. The Warden Eye goes
 *       where a careless turn would send the light.</li>
 *   <li>Scramble: every fixed mirror and splitter to a wrong angle, 1 to 3 solution mirrors (and in harder
 *       rooms a filter) made loose and set on random empty pedestals, decoy mirrors and Umbral blocks off the
 *       solution, and one Umbral block on the solution path, which has to be knocked aside.</li>
 *   <li>Run {@link LensSolver}: keep the layout only if no solution takes fewer than the difficulty's minimum
 *       moves. Otherwise try again; after {@link #TRIES} tries, a handmade template ({@link LensTemplates}).</li>
 * </ol>
 * The same seed always gives the same puzzle.
 */
public final class LensGenerator {
    public static final int TRIES = 50;
    /** Steps one routing attempt may take before it starts over. */
    private static final int ROUTE_BUDGET = 600;

    private final Random random;
    private final LensDifficulty diff;
    private final int n;
    private int[] cells;
    private boolean[] used;
    private int[] ports;
    // the log of cells set while building, for undoing a failed branch
    private final int[] log;
    private int logSize;
    // per receptor port: the pedestals of its own last stretch, where a filter may go
    private final List<int[]> stretches = new ArrayList<>();
    private int steps;

    LensGenerator(Random random, LensDifficulty diff) {
        this.random = random;
        this.diff = diff;
        this.n = diff.size();
        this.log = new int[n * n * 2];
    }

    /** Statistics of one {@link #generate} call, for tests. */
    public record Stats(int tries, boolean template) {}

    private static final ThreadLocal<Stats> LAST = new ThreadLocal<>();

    /** The stats of this thread's last {@link #generate}. */
    public static Stats lastStats() {
        return LAST.get();
    }

    /** A puzzle of {@code diff} from {@code seed}. */
    public static LensPuzzle generate(long seed, LensDifficulty diff) {
        Random random = new Random(seed);
        for (int attempt = 1; attempt <= TRIES; attempt++) {
            LensPuzzle p = new LensGenerator(random, diff).attempt();
            if (p == null) {
                continue;
            }
            Boolean easier = LensSolver.solvableWithin(p.size(), p.start(), p.ports(), diff.minMoves() - 1);
            if (easier == null || easier) {
                continue;
            }
            LAST.set(new Stats(attempt, false));
            return p.withMinMoves(diff.minMoves());
        }
        LAST.set(new Stats(TRIES, true));
        return LensTemplates.fallback(diff, seed);
    }

    /** One try: a built and scrambled puzzle, or null if this try went wrong. Not yet checked by the solver. */
    LensPuzzle attempt() {
        cells = new int[n * n];
        used = new boolean[n * n];
        ports = new int[4 * n];
        logSize = 0;
        stretches.clear();
        // the source, on the edge, facing in
        int side = random.nextInt(4);
        int idx = 1 + random.nextInt(n - 2);
        int sx = switch (side) {
            case Lens.NORTH, Lens.SOUTH -> idx;
            case Lens.EAST -> n - 1;
            default -> 0;
        };
        int sz = switch (side) {
            case Lens.EAST, Lens.WEST -> idx;
            case Lens.SOUTH -> n - 1;
            default -> 0;
        };
        int facing = Lens.opposite(side);
        int src = sz * n + sx;
        cells[src] = Lens.source(facing);
        used[src] = true;
        int behind = side * n + idx;
        // the receptors and their colours
        int count = diff.minReceptors() + random.nextInt(diff.maxReceptors() - diff.minReceptors() + 1);
        List<Integer> free = new ArrayList<>();
        for (int p = 0; p < 4 * n; p++) {
            if (p != behind) {
                free.add(p);
            }
        }
        int[] targets = new int[count];
        int[] colors = colors(count);
        for (int r = 0; r < count; r++) {
            int p = free.remove(random.nextInt(free.size()));
            targets[r] = p;
            ports[p] = Lens.PORT_RECEPTOR + colors[r];
        }
        List<Integer> group = new ArrayList<>();
        for (int t : targets) {
            group.add(t);
        }
        steps = 0;
        if (!tree(sx, sz, facing, group)) {
            return null;
        }
        // filters on each coloured receptor's own stretch
        for (int[] s : stretches) {
            int port = s[0];
            int color = Lens.receptorColor(ports[port]);
            if (color == Lens.WHITE) {
                continue;
            }
            List<Integer> straight = new ArrayList<>();
            for (int k = 1; k < s.length; k++) {
                if (Lens.kind(cells[s[k]]) == Lens.EMPTY) {
                    straight.add(s[k]);
                }
            }
            if (straight.isEmpty()) {
                return null;
            }
            cells[straight.get(random.nextInt(straight.size()))] = Lens.filter(color, false);
        }
        placeEye();
        int[] built = cells.clone();
        if (!BeamTrace.trace(n, built, ports).solves(ports)) {
            return null;
        }
        return scramble(built);
    }

    private int[] colors(int count) {
        int[] out = new int[count];
        if (!diff.colors()) {
            return out;
        }
        List<Integer> palette = new ArrayList<>(List.of(Lens.GOLD, Lens.TEAL, Lens.MAGENTA));
        for (int r = 0; r < count; r++) {
            out[r] = palette.remove(random.nextInt(palette.size()));
        }
        // sometimes one receptor wants plain sunlight
        if (random.nextInt(4) == 0) {
            out[random.nextInt(count)] = Lens.WHITE;
        }
        return out;
    }

    // ------------------------------------------------------------------ the solution tree

    /** Light leaves (x, z) with heading {@code h} and must reach every port of {@code group}. */
    private boolean tree(int x, int z, int h, List<Integer> group) {
        if (group.size() == 1) {
            int mark = logSize;
            if (route(x, z, h, group.get(0), 0)) {
                int[] s = new int[1 + logSize - mark];
                s[0] = group.get(0);
                for (int k = mark; k < logSize; k++) {
                    s[1 + k - mark] = log[k];
                }
                stretches.add(s);
                return true;
            }
            return false;
        }
        for (int tries = 0; tries < 12; tries++) {
            int mark = logSize;
            int stretchMark = stretches.size();
            int[] fork = walkToFork(x, z, h);
            if (fork != null) {
                int f = fork[0];
                int arrive = fork[1];
                cells[f] = Lens.splitter(arrive);
                used[f] = true;
                log[logSize++] = f;
                List<Integer> shuffled = new ArrayList<>(group);
                java.util.Collections.shuffle(shuffled, random);
                List<Integer> a = shuffled.subList(0, 1);
                List<Integer> b = shuffled.subList(1, shuffled.size());
                boolean swap = random.nextBoolean();
                int fx = f % n;
                int fz = f / n;
                if (tree(fx, fz, swap ? Lens.cw(arrive) : Lens.ccw(arrive), new ArrayList<>(a))
                        && tree(fx, fz, swap ? Lens.ccw(arrive) : Lens.cw(arrive), new ArrayList<>(b))) {
                    return true;
                }
            }
            undo(mark);
            while (stretches.size() > stretchMark) {
                stretches.remove(stretches.size() - 1);
            }
        }
        return false;
    }

    /** A short walk (at most one turn) from (x, z) to a free pedestal a splitter can stand on; {cell, heading}. */
    private int[] walkToFork(int x, int z, int h) {
        int length = 1 + random.nextInt(Math.max(1, n - 3));
        int turnAt = random.nextInt(3) == 0 ? -1 : random.nextInt(length);
        int cx = x;
        int cz = z;
        int d = h;
        for (int k = 0; k < length; k++) {
            int nx = cx + Lens.DX[d];
            int nz = cz + Lens.DZ[d];
            if (nx < 0 || nx >= n || nz < 0 || nz >= n || used[nz * n + nx]) {
                return null;
            }
            int i = nz * n + nx;
            cx = nx;
            cz = nz;
            if (k == length - 1) {
                // the fork: both outputs must lead into the grid
                if (!open(cx, cz, Lens.cw(d)) || !open(cx, cz, Lens.ccw(d))) {
                    return null;
                }
                return new int[] {i, d};
            }
            used[i] = true;
            log[logSize++] = i;
            if (k == turnAt) {
                int out = random.nextBoolean() ? Lens.cw(d) : Lens.ccw(d);
                cells[i] = Lens.mirror(Lens.mirrorTurnFor(d, out), false);
                d = out;
            }
        }
        return null;
    }

    private boolean open(int x, int z, int d) {
        int nx = x + Lens.DX[d];
        int nz = z + Lens.DZ[d];
        return nx >= 0 && nx < n && nz >= 0 && nz < n && !used[nz * n + nx];
    }

    /** A random path from (x, z) with heading {@code h} to {@code target}, with mirrors at its turns. */
    private boolean route(int x, int z, int h, int target, int turns) {
        if (++steps > ROUTE_BUDGET) {
            return false;
        }
        int nx = x + Lens.DX[h];
        int nz = z + Lens.DZ[h];
        if (nx < 0 || nx >= n || nz < 0 || nz >= n) {
            return Lens.portLeaving(n, x, z, h) == target && turns >= diff.minTurns();
        }
        int i = nz * n + nx;
        if (used[i]) {
            return false;
        }
        used[i] = true;
        int mark = logSize;
        log[logSize++] = i;
        // straight on, or a turn either way, in random order (straight a little more often)
        int[] order = random.nextInt(5) < 2 ? new int[] {0, 1, 2} : random.nextBoolean() ? new int[] {1, 2, 0} : new int[] {2, 1, 0};
        if (order[0] != 0 && random.nextBoolean()) {
            int t = order[0];
            order[0] = order[1];
            order[1] = t;
        }
        for (int option : order) {
            if (option == 0) {
                cells[i] = Lens.EMPTY;
                if (route(nx, nz, h, target, turns)) {
                    return true;
                }
            } else if (turns < diff.maxTurns()) {
                int out = option == 1 ? Lens.cw(h) : Lens.ccw(h);
                cells[i] = Lens.mirror(Lens.mirrorTurnFor(h, out), false);
                if (route(nx, nz, out, target, turns + 1)) {
                    return true;
                }
            }
            undo(mark + 1);
        }
        undo(mark);
        return false;
    }

    private void undo(int mark) {
        while (logSize > mark) {
            int i = log[--logSize];
            used[i] = false;
            cells[i] = Lens.EMPTY;
        }
    }

    /** The Warden Eye: where a mirror left along the beam would send the light, else any bare wall. */
    private void placeEye() {
        List<Integer> careless = new ArrayList<>();
        for (int i = 0; i < n * n; i++) {
            int c = cells[i];
            if (Lens.kind(c) != Lens.MIRROR) {
                continue;
            }
            for (int d = 0; d < 4; d++) {
                int out = Lens.mirrorOut(Lens.turn(c), d);
                if (out == d || out == Lens.opposite(d)) {
                    continue;
                }
                // the light arrives with heading d; straight on instead of turning:
                int x = i % n;
                int z = i / n;
                while (true) {
                    int nx = x + Lens.DX[d];
                    int nz = z + Lens.DZ[d];
                    if (nx < 0 || nx >= n || nz < 0 || nz >= n) {
                        int port = Lens.portLeaving(n, x, z, d);
                        if (ports[port] == Lens.PORT_WALL) {
                            careless.add(port);
                        }
                        break;
                    }
                    if (Lens.kind(cells[nz * n + nx]) != Lens.EMPTY) {
                        break;
                    }
                    x = nx;
                    z = nz;
                }
            }
        }
        if (careless.isEmpty()) {
            for (int p = 0; p < ports.length; p++) {
                if (ports[p] == Lens.PORT_WALL) {
                    careless.add(p);
                }
            }
        }
        ports[careless.get(random.nextInt(careless.size()))] = Lens.PORT_EYE;
    }

    // ------------------------------------------------------------------ the scramble

    private LensPuzzle scramble(int[] built) {
        for (int tries = 0; tries < 8; tries++) {
            LensPuzzle p = scrambleOnce(built);
            if (p != null) {
                return p;
            }
        }
        return null;
    }

    private LensPuzzle scrambleOnce(int[] built) {
        int[] start = built.clone();
        int[] solution = built.clone();
        boolean[] onPath = used.clone();
        int moves = 0;
        List<Integer> mirrors = new ArrayList<>();
        List<Integer> filters = new ArrayList<>();
        for (int i = 0; i < n * n; i++) {
            int k = Lens.kind(built[i]);
            if (k == Lens.MIRROR) {
                mirrors.add(i);
            } else if (k == Lens.FILTER) {
                filters.add(i);
            }
        }
        java.util.Collections.shuffle(mirrors, random);
        int loose = Math.min(mirrors.size(), diff.minLoose() + random.nextInt(diff.maxLoose() - diff.minLoose() + 1));
        // loose mirrors carried off to random empty pedestals, at a random turn
        for (int k = 0; k < loose; k++) {
            int m = mirrors.get(k);
            int f = freeCell(start, onPath);
            if (f < 0) {
                return null;
            }
            int t = random.nextInt(4);
            start[m] = Lens.EMPTY;
            start[f] = Lens.mirror(t, true);
            solution[m] = Lens.mirror(Lens.turn(built[m]), true);
            moves += 1 + Lens.turnDistance(t, Lens.turn(built[m]));
        }
        // the rest turned to a wrong angle
        for (int k = loose; k < mirrors.size(); k++) {
            int m = mirrors.get(k);
            int t = Lens.turn(built[m]);
            int wrong = (t + 1 + random.nextInt(3)) & 3;
            start[m] = Lens.mirror(wrong, false);
            moves += Lens.turnDistance(wrong, t);
        }
        for (int i = 0; i < n * n; i++) {
            if (Lens.kind(built[i]) == Lens.SPLITTER) {
                int t = Lens.turn(built[i]);
                int wrong = (t + 1 + random.nextInt(3)) & 3;
                start[i] = Lens.splitter(wrong);
                moves += Lens.turnDistance(wrong, t);
            }
        }
        // in the harder rooms a filter may be loose too
        if (diff.looseFilter() && !filters.isEmpty() && random.nextBoolean()) {
            int fi = filters.get(random.nextInt(filters.size()));
            int f = freeCell(start, onPath);
            if (f < 0) {
                return null;
            }
            int color = Lens.color(built[fi]);
            start[fi] = Lens.EMPTY;
            start[f] = Lens.filter(color, true);
            solution[fi] = Lens.filter(color, true);
            moves += 1;
        }
        // decoys off the solution
        int decoys = 1 + random.nextInt(Math.max(1, diff.decoyMirrors()));
        for (int k = 0; k < decoys; k++) {
            int f = freeCell(start, onPath);
            if (f >= 0) {
                start[f] = Lens.mirror(random.nextInt(4), false);
                solution[f] = start[f];
            }
        }
        int shadows = 1 + random.nextInt(Math.max(1, diff.decoyUmbral()));
        for (int k = 0; k < shadows; k++) {
            int f = freeCell(start, onPath);
            if (f >= 0) {
                start[f] = Lens.umbral();
                solution[f] = start[f];
            }
        }
        // one Umbral block on the path, with a free pedestal beside it to be knocked onto
        List<int[]> spots = new ArrayList<>();
        for (int i = 0; i < n * n; i++) {
            if (!onPath[i] || Lens.kind(built[i]) != Lens.EMPTY || Lens.kind(start[i]) != Lens.EMPTY) {
                continue;
            }
            for (int d = 0; d < 4; d++) {
                int mx = i % n + Lens.DX[d];
                int mz = i / n + Lens.DZ[d];
                if (mx >= 0 && mx < n && mz >= 0 && mz < n && !onPath[mz * n + mx]
                        && Lens.kind(start[mz * n + mx]) == Lens.EMPTY && Lens.kind(solution[mz * n + mx]) == Lens.EMPTY) {
                    spots.add(new int[] {i, mz * n + mx});
                }
            }
        }
        if (spots.isEmpty()) {
            return null;
        }
        int[] spot = spots.get(random.nextInt(spots.size()));
        start[spot[0]] = Lens.umbral();
        solution[spot[1]] = Lens.umbral();
        moves += 1;
        // fair start: the Eye is dark and the puzzle is not already solved
        boolean[] scratch = new boolean[n * n * 16];
        int lit = BeamTrace.litMask(n, start, ports, scratch);
        if ((lit & 1 << 31) != 0 || BeamTrace.trace(n, start, ports).solves(ports)) {
            return null;
        }
        BeamTrace.Result solved = BeamTrace.trace(n, solution, ports);
        moves = LensPuzzle.movesBetween(start, solution, n);
        if (!solved.solves(ports) || solved.eye() || moves < diff.minMoves() || moves > diff.maxMoves()) {
            return null;
        }
        return new LensPuzzle(n, start, ports.clone(), solution, -1, moves, false);
    }

    /** A random empty pedestal off the solution path, or -1. */
    private int freeCell(int[] grid, boolean[] onPath) {
        int[] free = new int[n * n];
        int count = 0;
        for (int i = 0; i < n * n; i++) {
            if (!onPath[i] && Lens.kind(grid[i]) == Lens.EMPTY) {
                free[count++] = i;
            }
        }
        return count == 0 ? -1 : free[random.nextInt(count)];
    }

    @Override
    public String toString() {
        return "LensGenerator" + Arrays.toString(cells);
    }
}
