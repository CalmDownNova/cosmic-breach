package com.cosmicbreach.structure.crypt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * A Hollow Crypt's labyrinth (GDD 6.1), all from its seed: {@link #levels} (2 or 3) levels of a
 * {@value #GRID} by {@value #GRID} grid of cells, {@value #CELL} blocks apart (walls shared), each level
 * {@value #STOREY} blocks under the one above. Between 12 and 25 rooms, all connected:
 *
 * <ul>
 *   <li>The {@link Kind#ENTRANCE} on the top level: a stair up to a gatehouse on the surface.</li>
 *   <li>A main path winds through each level to a stair down ({@link Kind#STAIR_DOWN} over {@link Kind#STAIR_UP})
 *       and, on the bottom level, to the {@link Kind#CHOIR} Floor, a 2 by 2 block of cells holding the Conductor
 *       and the vault.</li>
 *   <li>On the main path, one of each trap: a {@link Kind#RIFT} corridor (Void Rift tiles) over its sealed
 *       {@link Kind#POCKET} (whose way out opens into a room of the level below), a {@link Kind#GRAVITY} room, a
 *       {@link Kind#CHUTES} corridor and a {@link Kind#TRIPWIRE} corridor. Corridors run straight through their cell
 *       between exactly two doors.</li>
 *   <li>Side rooms ({@link Kind#HALL}s) branch off the halls until the room count is reached, a loop or two join
 *       neighbours, and some halls hold a Hollow Stalker's spot.</li>
 * </ul>
 * Sides are numbered 0 east (+x), 1 south (+z), 2 west (-x), 3 north (-z). Pure: {@link #check} names any broken
 * rule, and {@link #generate} only returns a layout that has none.
 */
public final class CryptLayout {
    public static final int GRID = 4;
    public static final int CELL = 10;
    public static final int STOREY = 8;
    /** Blocks across the crypt's grid, walls included. */
    public static final int SIZE = GRID * CELL + 1;
    public static final int MIN_ROOMS = 12;
    public static final int MAX_ROOMS = 25;
    public static final int[] DX = {1, 0, -1, 0};
    public static final int[] DZ = {0, 1, 0, -1};
    private static final int ATTEMPTS = 2000;

    public enum Kind {
        NONE, ENTRANCE, HALL, STAIR_DOWN, STAIR_UP, RIFT, POCKET, GRAVITY, CHUTES, TRIPWIRE, CHOIR;

        /** A corridor running straight through its cell between two doors. */
        public boolean corridor() {
            return this == RIFT || this == CHUTES || this == TRIPWIRE;
        }

        public boolean stair() {
            return this == STAIR_DOWN || this == STAIR_UP || this == ENTRANCE;
        }
    }

    public final long seed;
    public final int levels;
    public final int choirX;
    public final int choirZ;
    public final int entranceX;
    public final int entranceZ;
    /** Attempts it took (for tests). */
    public final int attempts;
    private final Kind[] kinds;
    private final int[] doors;
    private final int[] side;
    private final int[] pattern;
    private final boolean[] stalker;
    private final List<int[]> path;

    private CryptLayout(long seed, Draft d, int attempts) {
        this.seed = seed;
        this.levels = d.levels;
        this.choirX = d.choirX;
        this.choirZ = d.choirZ;
        this.entranceX = d.entX;
        this.entranceZ = d.entZ;
        this.attempts = attempts;
        this.kinds = d.kinds;
        this.doors = d.doors;
        this.side = d.side;
        this.pattern = d.pattern;
        this.stalker = d.stalker;
        this.path = List.copyOf(d.path);
    }

    // ------------------------------------------------------------------ reading

    public static int index(int level, int x, int z) {
        return (level * GRID + z) * GRID + x;
    }

    public static boolean inGrid(int x, int z) {
        return x >= 0 && z >= 0 && x < GRID && z < GRID;
    }

    public Kind kind(int level, int x, int z) {
        return level < 0 || level >= levels || !inGrid(x, z) ? Kind.NONE : kinds[index(level, x, z)];
    }

    /** True if cell (level, x, z) has a door on side {@code s}. */
    public boolean door(int level, int x, int z, int s) {
        return kind(level, x, z) != Kind.NONE && (doors[index(level, x, z)] >> s & 1) != 0;
    }

    public int doorCount(int level, int x, int z) {
        return Integer.bitCount(doors[index(level, x, z)] & 15);
    }

    /**
     * For stairs and the entrance: the side the stair runs along. For corridors: the side their first door is on
     * (0 or 1: the axis). For pockets: the side of the sealed way out.
     */
    public int side(int level, int x, int z) {
        return side[index(level, x, z)];
    }

    /** A chute corridor's pattern ({@code ChuteRules.Pattern} ordinal). */
    public int pattern(int level, int x, int z) {
        return pattern[index(level, x, z)];
    }

    public boolean stalker(int level, int x, int z) {
        return stalker[index(level, x, z)];
    }

    public int bottom() {
        return levels - 1;
    }

    /** True if (x, z) on the bottom level is part of the Choir Floor's 2 by 2 block. */
    public boolean inChoir(int x, int z) {
        return x >= choirX && x <= choirX + 1 && z >= choirZ && z <= choirZ + 1;
    }

    /** The main path from the entrance to the cell before the Choir Floor: {level, x, z} in order. */
    public List<int[]> mainPath() {
        return path;
    }

    /** Rooms: every cell in use, the Choir Floor's four counting as one. */
    public int rooms() {
        int n = 0;
        for (Kind k : kinds) {
            n += k == Kind.NONE ? 0 : 1;
        }
        return n - 3;
    }

    public int count(Kind kind) {
        int n = 0;
        for (Kind k : kinds) {
            n += k == kind ? 1 : 0;
        }
        return n;
    }

    // ------------------------------------------------------------------ generation

    /** The crypt of a seed: 2 or 3 levels, 12 to 25 rooms, every rule of {@link #check} kept. */
    public static CryptLayout generate(long seed) {
        Random r = new Random(seed * 0x9E3779B97F4A7C15L + 0x0C2797L);
        int levels = 2 + r.nextInt(2);
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            Draft d = Draft.attempt(r, levels);
            if (d != null) {
                CryptLayout layout = new CryptLayout(seed, d, attempt);
                String broken = layout.check();
                if (broken == null) {
                    return layout;
                }
            }
        }
        throw new IllegalStateException("no crypt layout for seed " + seed);
    }

    /** A plan being drawn; {@link #attempt} returns null when this try can't keep a rule. */
    private static final class Draft {
        final Random r;
        final int levels;
        final Kind[] kinds;
        final int[] doors;
        final int[] side;
        final int[] pattern;
        final boolean[] stalker;
        final List<int[]> path = new ArrayList<>();
        final boolean[] keep;
        int choirX;
        int choirZ;
        int entX;
        int entZ;

        Draft(Random r, int levels) {
            this.r = r;
            this.levels = levels;
            int n = levels * GRID * GRID;
            kinds = new Kind[n];
            java.util.Arrays.fill(kinds, Kind.NONE);
            doors = new int[n];
            side = new int[n];
            pattern = new int[n];
            stalker = new boolean[n];
            keep = new boolean[n];
        }

        Kind kind(int l, int x, int z) {
            return inGrid(x, z) ? kinds[index(l, x, z)] : null;
        }

        void set(int l, int x, int z, Kind k) {
            kinds[index(l, x, z)] = k;
        }

        void connect(int l, int x, int z, int s) {
            doors[index(l, x, z)] |= 1 << s;
            doors[index(l, x + DX[s], z + DZ[s])] |= 1 << ((s + 2) & 3);
        }

        static int sideTo(int x, int z, int tx, int tz) {
            for (int s = 0; s < 4; s++) {
                if (x + DX[s] == tx && z + DZ[s] == tz) {
                    return s;
                }
            }
            return -1;
        }

        static Draft attempt(Random r, int levels) {
            Draft d = new Draft(r, levels);
            int bottom = levels - 1;
            d.choirX = r.nextInt(GRID - 1);
            d.choirZ = r.nextInt(GRID - 1);
            for (int dx = 0; dx < 2; dx++) {
                for (int dz = 0; dz < 2; dz++) {
                    d.set(bottom, d.choirX + dx, d.choirZ + dz, Kind.CHOIR);
                }
            }
            d.entX = 1 + r.nextInt(GRID - 2);
            d.entZ = 1 + r.nextInt(GRID - 2);
            d.set(0, d.entX, d.entZ, Kind.ENTRANCE);
            int cx = d.entX;
            int cz = d.entZ;
            for (int l = 0; l < bottom; l++) {
                List<int[]> stairs = new ArrayList<>();
                for (int x = 0; x < GRID; x++) {
                    for (int z = 0; z < GRID; z++) {
                        boolean far = Math.abs(x - cx) + Math.abs(z - cz) >= 2;
                        if (far && d.kind(l, x, z) == Kind.NONE && d.kind(l + 1, x, z) == Kind.NONE) {
                            stairs.add(new int[] {x, z});
                        }
                    }
                }
                Collections.shuffle(stairs, r);
                List<int[]> walk = null;
                int[] stair = null;
                for (int[] s : stairs) {
                    walk = d.walk(l, cx, cz, s[0], s[1]);
                    if (walk != null) {
                        stair = s;
                        break;
                    }
                }
                if (walk == null) {
                    return null;
                }
                d.lay(l, walk);
                d.set(l, stair[0], stair[1], Kind.STAIR_DOWN);
                d.set(l + 1, stair[0], stair[1], Kind.STAIR_UP);
                cx = stair[0];
                cz = stair[1];
            }
            // the bottom level: to a cell next to the Choir Floor
            List<int[]> ends = new ArrayList<>();
            for (int x = 0; x < GRID; x++) {
                for (int z = 0; z < GRID; z++) {
                    Kind k = d.kind(bottom, x, z);
                    if ((k == Kind.NONE || x == cx && z == cz) && d.choirSide(x, z) >= 0) {
                        ends.add(new int[] {x, z});
                    }
                }
            }
            Collections.shuffle(ends, r);
            List<int[]> walk = null;
            for (int[] e : ends) {
                walk = e[0] == cx && e[1] == cz ? List.of(new int[] {cx, cz}) : d.walk(bottom, cx, cz, e[0], e[1]);
                if (walk != null) {
                    break;
                }
            }
            if (walk == null) {
                return null;
            }
            d.lay(bottom, walk);
            int[] last = walk.get(walk.size() - 1);
            d.connect(bottom, last[0], last[1], d.choirSide(last[0], last[1]));
            if (!d.traps()) {
                return null;
            }
            d.branches();
            d.loops();
            if (!d.stairSides()) {
                return null;
            }
            for (int i = 0; i < d.kinds.length; i++) {
                if (d.kinds[i] == Kind.HALL && r.nextDouble() < 0.4) {
                    d.stalker[i] = true;
                }
                if (d.kinds[i] == Kind.CHUTES) {
                    d.pattern[i] = r.nextInt(4);
                }
            }
            return d;
        }

        /** The side of (x, z) on the bottom level that faces the Choir Floor, or -1 if it isn't beside it. */
        int choirSide(int x, int z) {
            if (x >= choirX && x <= choirX + 1 && z >= choirZ && z <= choirZ + 1) {
                return -1;
            }
            for (int s = 0; s < 4; s++) {
                int nx = x + DX[s];
                int nz = z + DZ[s];
                if (nx >= choirX && nx <= choirX + 1 && nz >= choirZ && nz <= choirZ + 1) {
                    return s;
                }
            }
            return -1;
        }

        /** A winding path of free cells on level {@code l} from (fx, fz) to (tx, tz), both ends included, or null. */
        List<int[]> walk(int l, int fx, int fz, int tx, int tz) {
            for (int t = 0; t < 30; t++) {
                List<int[]> out = new ArrayList<>();
                boolean[] seen = new boolean[GRID * GRID];
                out.add(new int[] {fx, fz});
                seen[fz * GRID + fx] = true;
                int x = fx;
                int z = fz;
                while ((x != tx || z != tz) && out.size() <= 9) {
                    List<int[]> next = new ArrayList<>();
                    List<Integer> weight = new ArrayList<>();
                    int total = 0;
                    for (int s = 0; s < 4; s++) {
                        int nx = x + DX[s];
                        int nz = z + DZ[s];
                        if (!inGrid(nx, nz) || seen[nz * GRID + nx]) {
                            continue;
                        }
                        boolean target = nx == tx && nz == tz;
                        if (!target && kind(l, nx, nz) != Kind.NONE) {
                            continue;
                        }
                        int w = target ? 8 : Math.abs(nx - tx) + Math.abs(nz - tz) < Math.abs(x - tx) + Math.abs(z - tz) ? 3 : 1;
                        next.add(new int[] {nx, nz});
                        weight.add(w);
                        total += w;
                    }
                    if (next.isEmpty()) {
                        break;
                    }
                    int pick = r.nextInt(total);
                    int k = 0;
                    while (pick >= weight.get(k)) {
                        pick -= weight.get(k);
                        k++;
                    }
                    x = next.get(k)[0];
                    z = next.get(k)[1];
                    seen[z * GRID + x] = true;
                    out.add(new int[] {x, z});
                }
                if (x == tx && z == tz) {
                    return out;
                }
            }
            return null;
        }

        /** Marks a level's walk as halls joined by doors, and adds it to the main path. */
        void lay(int l, List<int[]> walk) {
            for (int i = 0; i < walk.size(); i++) {
                int[] c = walk.get(i);
                if (kind(l, c[0], c[1]) == Kind.NONE) {
                    set(l, c[0], c[1], Kind.HALL);
                }
                path.add(new int[] {l, c[0], c[1]});
                if (i > 0) {
                    int[] p = walk.get(i - 1);
                    connect(l, p[0], p[1], sideTo(p[0], p[1], c[0], c[1]));
                }
            }
        }

        /** The side of a main-path cell its path comes in by and leaves by: {in, out}, or null at the ends of a level. */
        int[] through(int i) {
            int[] c = path.get(i);
            if (i == 0 || i == path.size() - 1 && c[0] != levels - 1) {
                return null;
            }
            int[] p = path.get(i - 1);
            if (p[0] != c[0]) {
                return null;
            }
            int in = sideTo(c[1], c[2], p[1], p[2]);
            int out;
            if (i == path.size() - 1) {
                out = choirSide(c[1], c[2]);
            } else {
                int[] n = path.get(i + 1);
                if (n[0] != c[0]) {
                    return null;
                }
                out = sideTo(c[1], c[2], n[1], n[2]);
            }
            return new int[] {in, out};
        }

        /** One of each trap on the main path; false if this path has no room for them. */
        boolean traps() {
            List<Integer> straight = new ArrayList<>();
            List<Integer> any = new ArrayList<>();
            for (int i = 0; i < path.size(); i++) {
                int[] c = path.get(i);
                if (kind(c[0], c[1], c[2]) != Kind.HALL) {
                    continue;
                }
                int[] io = through(i);
                if (io == null) {
                    continue;
                }
                any.add(i);
                if (io[0] == ((io[1] + 2) & 3)) {
                    straight.add(i);
                }
            }
            Collections.shuffle(straight, r);
            Collections.shuffle(any, r);
            // the Void Rift first: its pocket needs the cell below and a way out into a room there
            Integer rift = null;
            for (int i : straight) {
                int[] c = path.get(i);
                if (c[0] < levels - 1 && kind(c[0] + 1, c[1], c[2]) == Kind.NONE && pocketExit(c[0] + 1, c[1], c[2]) >= 0) {
                    rift = i;
                    break;
                }
            }
            if (rift == null) {
                return false;
            }
            int[] rc = path.get(rift);
            corridor(rift, Kind.RIFT);
            int pl = rc[0] + 1;
            set(pl, rc[1], rc[2], Kind.POCKET);
            int exit = pocketExit(pl, rc[1], rc[2]);
            side[index(pl, rc[1], rc[2])] = exit;
            connect(pl, rc[1], rc[2], exit);
            keep[index(pl, rc[1] + DX[exit], rc[2] + DZ[exit])] = true;
            straight.remove(rift);
            any.remove(rift);
            for (Kind k : new Kind[] {Kind.CHUTES, Kind.TRIPWIRE}) {
                Integer pick = null;
                for (int i : straight) {
                    int[] c = path.get(i);
                    if (!keep[index(c[0], c[1], c[2])]) {
                        pick = i;
                        break;
                    }
                }
                if (pick == null) {
                    return false;
                }
                corridor(pick, k);
                straight.remove(pick);
                any.remove(pick);
            }
            if (any.isEmpty()) {
                return false;
            }
            int[] g = path.get(any.get(0));
            set(g[0], g[1], g[2], Kind.GRAVITY);
            return true;
        }

        private void corridor(int i, Kind k) {
            int[] c = path.get(i);
            set(c[0], c[1], c[2], k);
            side[index(c[0], c[1], c[2])] = through(i)[0] & 1;
        }

        /** A side of a would-be pocket at (l, x, z) opening into a room there, or -1. */
        int pocketExit(int l, int x, int z) {
            List<Integer> sides = new ArrayList<>(List.of(0, 1, 2, 3));
            Collections.shuffle(sides, r);
            for (int s : sides) {
                Kind k = kind(l, x + DX[s], z + DZ[s]);
                if (k == Kind.HALL || k == Kind.STAIR_UP) {
                    return s;
                }
            }
            return -1;
        }

        int rooms() {
            int n = 0;
            for (Kind k : kinds) {
                n += k == Kind.NONE ? 0 : 1;
            }
            return n - 3;
        }

        /** Side rooms off the halls until the room count drawn for this crypt. */
        void branches() {
            int want = MIN_ROOMS + r.nextInt(MAX_ROOMS - MIN_ROOMS + 1);
            while (rooms() < want) {
                List<int[]> can = new ArrayList<>();
                for (int l = 0; l < levels; l++) {
                    for (int x = 0; x < GRID; x++) {
                        for (int z = 0; z < GRID; z++) {
                            Kind k = kind(l, x, z);
                            if (k != Kind.HALL && k != Kind.ENTRANCE) {
                                continue;
                            }
                            for (int s = 0; s < 4; s++) {
                                if (kind(l, x + DX[s], z + DZ[s]) == Kind.NONE) {
                                    can.add(new int[] {l, x, z, s});
                                }
                            }
                        }
                    }
                }
                if (can.isEmpty()) {
                    return;
                }
                int[] c = can.get(r.nextInt(can.size()));
                set(c[0], c[1] + DX[c[3]], c[2] + DZ[c[3]], Kind.HALL);
                connect(c[0], c[1], c[2], c[3]);
            }
        }

        /** Up to two extra doors between neighbouring halls, so the labyrinth has a loop or two. */
        void loops() {
            int extra = r.nextInt(3);
            List<int[]> can = new ArrayList<>();
            for (int l = 0; l < levels; l++) {
                for (int x = 0; x < GRID; x++) {
                    for (int z = 0; z < GRID; z++) {
                        for (int s = 0; s < 2; s++) {
                            Kind a = kind(l, x, z);
                            Kind b = kind(l, x + DX[s], z + DZ[s]);
                            if ((a == Kind.HALL || a == Kind.ENTRANCE) && (b == Kind.HALL || b == Kind.ENTRANCE)
                                    && (doors[index(l, x, z)] >> s & 1) == 0) {
                                can.add(new int[] {l, x, z, s});
                            }
                        }
                    }
                }
            }
            Collections.shuffle(can, r);
            for (int i = 0; i < Math.min(extra, can.size()); i++) {
                int[] c = can.get(i);
                connect(c[0], c[1], c[2], c[3]);
            }
        }

        /** Every stair runs along a side with no door on either level; false if one can't. */
        boolean stairSides() {
            for (int l = 0; l < levels; l++) {
                for (int x = 0; x < GRID; x++) {
                    for (int z = 0; z < GRID; z++) {
                        Kind k = kind(l, x, z);
                        if (k != Kind.STAIR_DOWN && k != Kind.ENTRANCE) {
                            continue;
                        }
                        int used = doors[index(l, x, z)] | (k == Kind.STAIR_DOWN ? doors[index(l + 1, x, z)] : 0);
                        List<Integer> free = new ArrayList<>();
                        for (int s = 0; s < 4; s++) {
                            if ((used >> s & 1) == 0) {
                                free.add(s);
                            }
                        }
                        if (free.isEmpty()) {
                            return false;
                        }
                        int s = free.get(r.nextInt(free.size()));
                        side[index(l, x, z)] = s;
                        if (k == Kind.STAIR_DOWN) {
                            side[index(l + 1, x, z)] = s;
                        }
                    }
                }
            }
            return true;
        }
    }

    // ------------------------------------------------------------------ rules

    /** The first rule this layout breaks, or null if it keeps them all. */
    public String check() {
        if (levels < 2 || levels > 3) {
            return "levels " + levels;
        }
        int rooms = rooms();
        if (rooms < MIN_ROOMS || rooms > MAX_ROOMS) {
            return "rooms " + rooms;
        }
        if (kind(0, entranceX, entranceZ) != Kind.ENTRANCE || count(Kind.ENTRANCE) != 1) {
            return "entrance";
        }
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                if (kind(bottom(), choirX + dx, choirZ + dz) != Kind.CHOIR) {
                    return "choir block";
                }
            }
        }
        if (count(Kind.CHOIR) != 4) {
            return "choir count";
        }
        for (Kind k : new Kind[] {Kind.RIFT, Kind.GRAVITY, Kind.CHUTES, Kind.TRIPWIRE}) {
            if (count(k) < 1) {
                return "no " + k;
            }
        }
        for (int l = 0; l < levels; l++) {
            for (int x = 0; x < GRID; x++) {
                for (int z = 0; z < GRID; z++) {
                    String bad = checkCell(l, x, z);
                    if (bad != null) {
                        return bad + " at " + l + "," + x + "," + z;
                    }
                }
            }
        }
        return connected() ? null : "not connected";
    }

    private String checkCell(int l, int x, int z) {
        Kind k = kind(l, x, z);
        int mask = k == Kind.NONE ? 0 : doors[index(l, x, z)];
        for (int s = 0; s < 4; s++) {
            if ((mask >> s & 1) == 0) {
                continue;
            }
            int nx = x + DX[s];
            int nz = z + DZ[s];
            if (!inGrid(nx, nz) || kind(l, nx, nz) == Kind.NONE || !door(l, nx, nz, (s + 2) & 3)) {
                return "door to nowhere";
            }
        }
        if (k.corridor()) {
            int a = side(l, x, z);
            if (Integer.bitCount(mask) != 2 || (mask >> a & 1) == 0 || (mask >> (a + 2) & 1) == 0) {
                return "corridor doors";
            }
        }
        if (k == Kind.RIFT && kind(l + 1, x, z) != Kind.POCKET) {
            return "rift without pocket";
        }
        if (k == Kind.POCKET) {
            if (kind(l - 1, x, z) != Kind.RIFT) {
                return "pocket without rift";
            }
            int e = side(l, x, z);
            if (Integer.bitCount(mask) != 1 || (mask >> e & 1) == 0) {
                return "pocket exit";
            }
            Kind out = kind(l, x + DX[e], z + DZ[e]);
            if (out.corridor() || out == Kind.POCKET || out == Kind.NONE || out == Kind.CHOIR) {
                return "pocket exit into " + out;
            }
        }
        if (k == Kind.STAIR_DOWN && (kind(l + 1, x, z) != Kind.STAIR_UP || side(l + 1, x, z) != side(l, x, z))) {
            return "stair below";
        }
        if (k == Kind.STAIR_UP && kind(l - 1, x, z) != Kind.STAIR_DOWN) {
            return "stair above";
        }
        if (k.stair() && (mask >> side(l, x, z) & 1) != 0) {
            return "door onto the stair";
        }
        if (k.stair() && mask == 0 && k != Kind.STAIR_UP) {
            return "stair with no door";
        }
        if (k == Kind.CHOIR && (l != bottom() || !inChoir(x, z))) {
            return "choir cell";
        }
        return null;
    }

    /** True if every room is reached from the entrance: through doors, down stairs, and by falling into pockets. */
    public boolean connected() {
        boolean[] seen = new boolean[kinds.length];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[] {0, entranceX, entranceZ});
        seen[index(0, entranceX, entranceZ)] = true;
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            int l = c[0];
            int x = c[1];
            int z = c[2];
            Kind k = kind(l, x, z);
            List<int[]> next = new ArrayList<>();
            for (int s = 0; s < 4; s++) {
                int nx = x + DX[s];
                int nz = z + DZ[s];
                boolean inside = k == Kind.CHOIR && inGrid(nx, nz) && kind(l, nx, nz) == Kind.CHOIR;
                if (door(l, x, z, s) || inside) {
                    next.add(new int[] {l, nx, nz});
                }
            }
            if (k == Kind.STAIR_DOWN || k == Kind.RIFT) {
                next.add(new int[] {l + 1, x, z});
            }
            if (k == Kind.STAIR_UP) {
                next.add(new int[] {l - 1, x, z});
            }
            for (int[] n : next) {
                int i = index(n[0], n[1], n[2]);
                if (!seen[i] && kind(n[0], n[1], n[2]) != Kind.NONE) {
                    seen[i] = true;
                    queue.add(n);
                }
            }
        }
        for (int i = 0; i < kinds.length; i++) {
            if (kinds[i] != Kind.NONE && !seen[i]) {
                return false;
            }
        }
        return true;
    }
}
