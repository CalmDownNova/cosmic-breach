package com.cosmicbreach.structure.lens;

import java.util.Arrays;

/**
 * One Lens Array puzzle: the grid as a player first finds it ({@link #start}), the wall's ports (receptors and
 * the Warden Eye), and the solution it was built from ({@link #solution}, for hints). Cells and ports are
 * encoded as in {@link Lens}.
 *
 * @param size          grid side (5 or 7)
 * @param start         the scrambled grid, row by row
 * @param ports         the {@code 4 * size} wall sockets
 * @param solution      the grid the generator built before scrambling: every receptor lit, the Eye dark
 * @param minMoves      the fewest moves that can solve {@link #start}, as proven by {@link LensSolver} (at least
 *                      the difficulty's minimum), or -1 if the solver was not asked
 * @param solutionMoves moves from {@link #start} to {@link #solution}
 * @param template      true if this is a handmade fallback ({@link LensTemplates})
 */
public record LensPuzzle(int size, int[] start, int[] ports, int[] solution, int minMoves, int solutionMoves, boolean template) {
    public LensPuzzle {
        if (start.length != size * size || solution.length != size * size || ports.length != 4 * size) {
            throw new IllegalArgumentException("bad puzzle dimensions");
        }
    }

    /** Bit mask of the ports holding receptors. */
    public int receptorMask() {
        int mask = 0;
        for (int p = 0; p < ports.length; p++) {
            if (Lens.isReceptor(ports[p])) {
                mask |= 1 << p;
            }
        }
        return mask;
    }

    public int receptorCount() {
        return Integer.bitCount(receptorMask());
    }

    /** The source cell (the focus under the aperture). */
    public int sourceCell() {
        for (int i = 0; i < start.length; i++) {
            if (Lens.kind(start[i]) == Lens.SOURCE) {
                return i;
            }
        }
        throw new IllegalStateException("puzzle without a source");
    }

    /**
     * Moves from layout {@code from} to layout {@code to}: presses to turn each fixed mirror and splitter, one per
     * loose piece set somewhere else plus its presses, one per tile an Umbral block is knocked (loose pieces and
     * Umbral blocks matched the cheapest way). -1 if {@code to} can't be reached (a fixed piece differs in kind).
     */
    public static int movesBetween(int[] from, int[] to, int n) {
        int moves = 0;
        java.util.List<int[]> looseFrom = new java.util.ArrayList<>();
        java.util.List<int[]> looseTo = new java.util.ArrayList<>();
        java.util.List<Integer> umbralFrom = new java.util.ArrayList<>();
        java.util.List<Integer> umbralTo = new java.util.ArrayList<>();
        for (int i = 0; i < from.length; i++) {
            int a = from[i];
            int b = to[i];
            if (Lens.loose(a)) {
                looseFrom.add(new int[] {i, a});
            }
            if (Lens.loose(b)) {
                looseTo.add(new int[] {i, b});
            }
            if (Lens.kind(a) == Lens.UMBRAL) {
                umbralFrom.add(i);
            }
            if (Lens.kind(b) == Lens.UMBRAL) {
                umbralTo.add(i);
            }
            boolean fixedA = !Lens.loose(a) && Lens.kind(a) != Lens.EMPTY && Lens.kind(a) != Lens.UMBRAL;
            boolean fixedB = !Lens.loose(b) && Lens.kind(b) != Lens.EMPTY && Lens.kind(b) != Lens.UMBRAL;
            if (fixedA != fixedB) {
                return -1;
            }
            if (fixedA) {
                if (Lens.kind(a) != Lens.kind(b) || Lens.color(a) != Lens.color(b)) {
                    return -1;
                }
                if (Lens.rotatable(a)) {
                    moves += Lens.turnDistance(Lens.turn(a), Lens.turn(b));
                } else if (Lens.turn(a) != Lens.turn(b)) {
                    return -1;
                }
            }
        }
        if (looseFrom.size() != looseTo.size() || umbralFrom.size() != umbralTo.size()) {
            return -1;
        }
        int loose = cheapestMatch(looseFrom.size(), (p, q) -> {
            int[] a = looseFrom.get(p);
            int[] b = looseTo.get(q);
            if (Lens.kind(a[1]) != Lens.kind(b[1]) || Lens.color(a[1]) != Lens.color(b[1])) {
                return -1;
            }
            int turn = Lens.kind(a[1]) == Lens.MIRROR ? Lens.turnDistance(Lens.turn(a[1]), Lens.turn(b[1])) : 0;
            return (a[0] == b[0] ? 0 : 1) + turn;
        });
        int shadows = cheapestMatch(umbralFrom.size(), (p, q) -> {
            int a = umbralFrom.get(p);
            int b = umbralTo.get(q);
            return Math.abs(a % n - b % n) + Math.abs(a / n - b / n);
        });
        return loose < 0 || shadows < 0 ? -1 : moves + loose + shadows;
    }

    private interface PairCost {
        int cost(int p, int q);
    }

    /** The cheapest pairing of {@code k} items with {@code k} items (brute force; k is at most a handful). */
    private static int cheapestMatch(int k, PairCost cost) {
        int[] perm = new int[k];
        for (int i = 0; i < k; i++) {
            perm[i] = i;
        }
        int best = -1;
        do {
            int sum = 0;
            for (int i = 0; i < k && sum >= 0; i++) {
                int c = cost.cost(i, perm[i]);
                sum = c < 0 ? -1 : sum + c;
            }
            if (sum >= 0 && (best < 0 || sum < best)) {
                best = sum;
            }
        } while (nextPermutation(perm));
        return best;
    }

    private static boolean nextPermutation(int[] a) {
        int i = a.length - 2;
        while (i >= 0 && a[i] >= a[i + 1]) {
            i--;
        }
        if (i < 0) {
            return false;
        }
        int j = a.length - 1;
        while (a[j] <= a[i]) {
            j--;
        }
        int t = a[i];
        a[i] = a[j];
        a[j] = t;
        for (int l = i + 1, r = a.length - 1; l < r; l++, r--) {
            t = a[l];
            a[l] = a[r];
            a[r] = t;
        }
        return true;
    }

    public LensPuzzle withMinMoves(int moves) {
        return new LensPuzzle(size, start, ports, solution, moves, solutionMoves, template);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof LensPuzzle p && size == p.size && Arrays.equals(start, p.start) && Arrays.equals(ports, p.ports)
                && Arrays.equals(solution, p.solution) && minMoves == p.minMoves && solutionMoves == p.solutionMoves
                && template == p.template;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(start) * 31 + Arrays.hashCode(ports);
    }

    @Override
    public String toString() {
        return render(start);
    }

    /** A text picture of {@code cells} with this puzzle's ports, for logs and test failures. */
    public String render(int[] cells) {
        StringBuilder b = new StringBuilder();
        b.append(' ');
        for (int x = 0; x < size; x++) {
            b.append(portChar(ports[Lens.NORTH * size + x]));
        }
        b.append('\n');
        for (int z = 0; z < size; z++) {
            b.append(portChar(ports[Lens.WEST * size + z]));
            for (int x = 0; x < size; x++) {
                b.append(cellChar(cells[z * size + x]));
            }
            b.append(portChar(ports[Lens.EAST * size + z])).append('\n');
        }
        b.append(' ');
        for (int x = 0; x < size; x++) {
            b.append(portChar(ports[Lens.SOUTH * size + x]));
        }
        return b.toString();
    }

    private static char portChar(int port) {
        if (port == Lens.PORT_EYE) {
            return 'E';
        }
        if (Lens.isReceptor(port)) {
            return "wgtm".charAt(Lens.receptorColor(port));
        }
        return '#';
    }

    private static char cellChar(int c) {
        return switch (Lens.kind(c)) {
            case Lens.SOURCE -> "^>v<".charAt(Lens.turn(c));
            // loose mirrors: = ` ! , for the turns of - \ | /
            case Lens.MIRROR -> (Lens.loose(c) ? "=`!," : "-\\|/").charAt(Lens.turn(c));
            case Lens.SPLITTER -> "0123".charAt(Lens.turn(c));
            case Lens.FILTER -> Lens.loose(c) ? "WGTM".charAt(Lens.color(c)) : "wgtm".charAt(Lens.color(c));
            case Lens.UMBRAL -> 'U';
            default -> '.';
        };
    }
}
