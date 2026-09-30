package com.cosmicbreach.structure.lens;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handmade Lens Array puzzles, the generator's fallback after {@link LensGenerator#TRIES} failed tries (GDD 6.2).
 * Each is drawn as text: the outer ring is the wall's ports ({@code #} bare wall, {@code E} the Warden Eye,
 * {@code w g t m} receptors wanting white, gold, teal or magenta light, corners blank), the inside the pedestals:
 *
 * <pre>
 *   .          empty                     ^ &gt; v &lt;    the source, facing north, east, south, west
 *   - \ | /    fixed mirrors (turns 0-3)   = ` ! ,    loose mirrors (turns 0-3)
 *   0 1 2 3    splitters accepting light heading north, east, south, west
 *   g t m      fixed filters             G T M      loose filters
 *   U          an Umbral block
 * </pre>
 *
 * Each template carries its scrambled start and its solution; the unit tests check both, and the minimum moves.
 * They are layouts picked by hand from the generator's output and frozen here, one or two per difficulty.
 */
public final class LensTemplates {
    /** One template: its difficulty, start and solution drawings. */
    record Template(LensDifficulty difficulty, String start, String solution) {}

    private static final List<Template> ALL = new ArrayList<>();

    static {
        // Hand-picked layouts, frozen: every fixed mirror and splitter turned wrong, loose pieces carried off,
        // one Umbral block on the path. LensTemplatesTest checks each start, solution and minimum.
        // easy_5, at least 5 moves
        add(LensDifficulty.EASY_5, rows(
                " ####w",
                "#....U#",
                "#.....#",
                "#!....#",
                "#U..\\.#",
                "#.|.^.#",
                " ####E"),
                rows(
                " ####w",
                "#...U.#",
                "#.....#",
                "#.....#",
                "#U../,#",
                "#.|.^.#",
                " ####E"));
        // easy_5, at least 4 moves
        add(LensDifficulty.EASY_5, rows(
                " ####w",
                "#.../.#",
                "#...!.#",
                "E.-...#",
                "#.U...#",
                "#.^..U#",
                " #####"),
                rows(
                " ####w",
                "#.../.#",
                "#.....#",
                "E./..,#",
                "#U....#",
                "#.^..U#",
                " #####"));
        // medium_5, at least 7 moves
        add(LensDifficulty.MEDIUM_5, rows(
                " #####",
                "w.v.\\.#",
                "#.....#",
                "#.U...#",
                "#/-U..#",
                "#..,..#",
                " E####"),
                rows(
                " #####",
                "w`v.\\.#",
                "#.....#",
                "#..U..#",
                "#\\/U..#",
                "#.....#",
                " E####"));
        // medium_5, at least 7 moves
        add(LensDifficulty.MEDIUM_5, rows(
                " #####",
                "#/...!#",
                "#..\\.<#",
                "#...U/E",
                "#.....#",
                "#/U...#",
                " ####w"),
                rows(
                " #####",
                "#/....#",
                "#../.<#",
                "#..`.\\E",
                "#...U.#",
                "#/U...#",
                " ####w"));
        // hard_5, at least 10 moves
        add(LensDifficulty.HARD_5, rows(
                " #####",
                "#U.../#",
                "#.\\g.|#",
                "#>2..!#",
                "E\\.`/Ug",
                "#.....#",
                " w####"),
                rows(
                " #####",
                "#U.../#",
                "#./g`|#",
                "#>1...#",
                "E/,.\\.g",
                "#....U#",
                " w####"));
        // hard_5, at least 10 moves
        add(LensDifficulty.HARD_5, rows(
                " ####E",
                "#\\vU..#",
                "#.U..|#",
                "#-3\\..#",
                "#/.g.t#",
                "#!.=..#",
                " ###gt"),
                rows(
                " ####E",
                "#\\vU..#",
                "#U.,.\\#",
                "#/2/..#",
                "#\\.g`t#",
                "#.....#",
                " ###gt"));
        // easy_7, at least 4 moves
        add(LensDifficulty.EASY_7, rows(
                " #######",
                "#....U..#",
                "#.......#",
                "#.......#",
                "#.......#",
                "#....-tUt",
                "#./....<#",
                "#=......#",
                " ####E##"),
                rows(
                " #######",
                "#....U..#",
                "#.......#",
                "#.......#",
                "#......U#",
                "#..../t.t",
                "#./..`.<#",
                "#.......#",
                " ####E##"));
        // easy_7, at least 5 moves
        add(LensDifficulty.EASY_7, rows(
                " ###E##m",
                "#.......#",
                "#\\.!....#",
                "#.......#",
                "#......m#",
                "#......\\#",
                "#...U.U.#",
                "#...^...#",
                " #######"),
                rows(
                " ###E##m",
                "#.......#",
                "#\\......#",
                "#.......#",
                "#......m#",
                "#...,../#",
                "#..U..U.#",
                "#...^...#",
                " #######"));
        // medium_7, at least 8 moves
        add(LensDifficulty.MEDIUM_7, rows(
                " ##E####",
                "#.......#",
                "#!......#",
                "#.......#",
                "#.|-m..Um",
                "#\\1-..U.#",
                "#.......#",
                "w.^.....#",
                " #######"),
                rows(
                " ##E####",
                "#.......#",
                "#.......#",
                "#......U#",
                "#.|/m...m",
                "#/0/..U.#",
                "#.......#",
                "w,^.....#",
                " #######"));
        // medium_7, at least 9 moves
        add(LensDifficulty.MEDIUM_7, rows(
                " #######",
                "#...-...#",
                "#,.....U#",
                "#.......#",
                "#.....||#",
                "#.....U.#",
                "#.T.-3-.#",
                "E..m|^..#",
                " #m####t"),
                rows(
                " #######",
                "#...-...#",
                "#......U#",
                "#.......#",
                "#...../\\#",
                "#....U..#",
                "#.../0/.#",
                "E.,m/^.T#",
                " #m####t"));
        // hard_7, at least 12 moves
        add(LensDifficulty.HARD_7, rows(
                " #g#####",
                "#-|U....E",
                "#.!.`!.g#",
                "#.U.U.32#",
                "#......t#",
                "#.......t",
                "#...../<#",
                "#.../-m.m",
                " #######"),
                rows(
                " #g#####",
                "#-\\....`E",
                "#..U...g#",
                "#.U.U,01#",
                "#......t#",
                "#......`t",
                "#.....\\<#",
                "#.../\\m.m",
                " #######"));
        // hard_7, at least 11 moves
        add(LensDifficulty.HARD_7, rows(
                " ####tE#",
                "#`-..t..#",
                "#,.../..#",
                "#...|2\\.#",
                "#G..U..U#",
                "#.......#",
                "m..m.0.<#",
                "#\\...|..#",
                " g######"),
                rows(
                " ####tE#",
                "#.-..t..#",
                "#....\\`.#",
                "#.../0/.#",
                "#..U...U#",
                "#.......#",
                "m..m,3.<#",
                "#/G../..#",
                " g######"));
    }

    private LensTemplates() {
    }

    private static void add(LensDifficulty difficulty, String start, String solution) {
        ALL.add(new Template(difficulty, start, solution));
    }

    private static String rows(String... rows) {
        return String.join("\n", rows);
    }

    static List<Template> all() {
        return List.copyOf(ALL);
    }

    /** A template of {@code diff}, picked by {@code seed}; if none was drawn for it, the nearest easier one. */
    public static LensPuzzle fallback(LensDifficulty diff, long seed) {
        List<Template> fits = new ArrayList<>();
        for (Template t : ALL) {
            if (t.difficulty() == diff) {
                fits.add(t);
            }
        }
        if (fits.isEmpty()) {
            for (Template t : ALL) {
                if (t.difficulty().size() == diff.size()) {
                    fits.add(t);
                }
            }
        }
        if (fits.isEmpty()) {
            fits.addAll(ALL);
        }
        Template t = fits.get((int) Math.floorMod(seed, (long) fits.size()));
        return parse(t);
    }

    static LensPuzzle parse(Template t) {
        int n = t.difficulty().size();
        int[] ports = new int[4 * n];
        int[] start = cells(t.start(), n, ports);
        int[] solution = cells(t.solution(), n, new int[4 * n]);
        int moves = LensSolver.solve(n, start, ports, 30).moves();
        return new LensPuzzle(n, start, ports, solution, moves, LensPuzzle.movesBetween(start, solution, n), true);
    }

    private static final Map<Character, Integer> PORTS = Map.of('#', Lens.PORT_WALL, 'E', Lens.PORT_EYE,
            'w', Lens.PORT_RECEPTOR + Lens.WHITE, 'g', Lens.PORT_RECEPTOR + Lens.GOLD,
            't', Lens.PORT_RECEPTOR + Lens.TEAL, 'm', Lens.PORT_RECEPTOR + Lens.MAGENTA);

    /** Parses a drawing into cells, filling {@code ports} from its ring. */
    static int[] cells(String drawing, int n, int[] ports) {
        String[] rows = drawing.split("\n");
        if (rows.length < n + 2) {
            throw new IllegalArgumentException("template needs " + (n + 2) + " rows");
        }
        int[] cells = new int[n * n];
        for (int x = 0; x < n; x++) {
            ports[Lens.NORTH * n + x] = port(rows[0].charAt(x + 1));
            ports[Lens.SOUTH * n + x] = port(rows[n + 1].charAt(x + 1));
        }
        for (int z = 0; z < n; z++) {
            String row = rows[z + 1];
            ports[Lens.WEST * n + z] = port(row.charAt(0));
            ports[Lens.EAST * n + z] = port(row.charAt(n + 1));
            for (int x = 0; x < n; x++) {
                cells[z * n + x] = cell(row.charAt(x + 1));
            }
        }
        return cells;
    }

    private static int port(char c) {
        if (c == '^' || c == '>' || c == 'v' || c == '<') {
            return Lens.PORT_WALL;
        }
        Integer p = PORTS.get(c);
        if (p == null) {
            throw new IllegalArgumentException("unknown port '" + c + "'");
        }
        return p;
    }

    private static int cell(char c) {
        return switch (c) {
            case '.' -> Lens.EMPTY;
            case '^' -> Lens.source(Lens.NORTH);
            case '>' -> Lens.source(Lens.EAST);
            case 'v' -> Lens.source(Lens.SOUTH);
            case '<' -> Lens.source(Lens.WEST);
            case '-' -> Lens.mirror(0, false);
            case '\\' -> Lens.mirror(1, false);
            case '|' -> Lens.mirror(2, false);
            case '/' -> Lens.mirror(3, false);
            case '=' -> Lens.mirror(0, true);
            case '`' -> Lens.mirror(1, true);
            case '!' -> Lens.mirror(2, true);
            case ',' -> Lens.mirror(3, true);
            case '0' -> Lens.splitter(Lens.NORTH);
            case '1' -> Lens.splitter(Lens.EAST);
            case '2' -> Lens.splitter(Lens.SOUTH);
            case '3' -> Lens.splitter(Lens.WEST);
            case 'g' -> Lens.filter(Lens.GOLD, false);
            case 't' -> Lens.filter(Lens.TEAL, false);
            case 'm' -> Lens.filter(Lens.MAGENTA, false);
            case 'G' -> Lens.filter(Lens.GOLD, true);
            case 'T' -> Lens.filter(Lens.TEAL, true);
            case 'M' -> Lens.filter(Lens.MAGENTA, true);
            case 'U' -> Lens.umbral();
            default -> throw new IllegalArgumentException("unknown cell '" + c + "'");
        };
    }
}
