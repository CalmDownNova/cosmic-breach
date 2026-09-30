package com.cosmicbreach.structure.lens;

import java.util.Arrays;

/**
 * Follows the light through a grid (GDD 6.2): from the source, straight from cell to cell, turned by mirrors,
 * split by prisms, tinted by filters, stopped by Umbral blocks and the wall. Loops end where a beam would
 * repeat itself, and a trace draws at most {@link Lens#MAX_SEGMENTS} segments. The server runs it only when a
 * piece changes.
 *
 * <p>A segment runs from one point to another in grid units (cell centres; ports one cell outside the border),
 * in one colour. {@link Result#closest} gives, per port, how close any beam came to it, for the receptors' hum.
 */
public final class BeamTrace {
    /** How a segment ended. */
    public static final int END_PIECE = 0;
    public static final int END_PORT = 1;

    /**
     * One trace.
     *
     * @param segments  {@code count} segments of 6 ints each: x0, z0, x1, z1, colour, end
     * @param count     how many segments
     * @param litMask   ports whose receptor was reached by a beam of its colour
     * @param reached   ports any beam reached (receptor, Eye or bare wall), whatever its colour
     * @param eye       true if a beam reached the Warden Eye
     * @param closest   per port, the distance in grid units from the port to the nearest beam (0 if reached);
     *                  {@link Float#POSITIVE_INFINITY} with no beams
     * @param truncated true if the trace stopped at the segment limit
     */
    public record Result(int[] segments, int count, int litMask, int reached, boolean eye, float[] closest, boolean truncated) {
        public int x0(int i) {
            return segments[i * 6];
        }

        public int z0(int i) {
            return segments[i * 6 + 1];
        }

        public int x1(int i) {
            return segments[i * 6 + 2];
        }

        public int z1(int i) {
            return segments[i * 6 + 3];
        }

        public int color(int i) {
            return segments[i * 6 + 4];
        }

        public int end(int i) {
            return segments[i * 6 + 5];
        }

        /** True if every receptor of {@code ports} is lit. */
        public boolean solves(int[] ports) {
            int want = 0;
            for (int p = 0; p < ports.length; p++) {
                if (Lens.isReceptor(ports[p])) {
                    want |= 1 << p;
                }
            }
            return want != 0 && (litMask & want) == want;
        }
    }

    private BeamTrace() {
    }

    /** Traces {@code cells} (an {@code n} by {@code n} grid) against {@code ports}. */
    public static Result trace(int n, int[] cells, int[] ports) {
        int[] seg = new int[Lens.MAX_SEGMENTS * 6];
        int count = 0;
        boolean truncated = false;
        boolean[] seen = new boolean[n * n * 16];
        // pending beams: start x, z, heading, colour
        int[] stack = new int[4 * 64];
        int top = 0;
        int lit = 0;
        int reached = 0;
        boolean eye = false;
        int src = sourceOf(cells);
        if (src >= 0) {
            stack[top++] = src % n;
            stack[top++] = src / n;
            stack[top++] = Lens.turn(cells[src]);
            stack[top++] = Lens.WHITE;
        }
        beams:
        while (top > 0) {
            int color = stack[--top];
            int d = stack[--top];
            int sz = stack[--top];
            int sx = stack[--top];
            int x = sx;
            int z = sz;
            while (true) {
                if (count >= Lens.MAX_SEGMENTS) {
                    truncated = true;
                    break beams;
                }
                int nx = x + Lens.DX[d];
                int nz = z + Lens.DZ[d];
                if (nx < 0 || nx >= n || nz < 0 || nz >= n) {
                    int port = Lens.portLeaving(n, x, z, d);
                    count = put(seg, count, sx, sz, nx, nz, color, END_PORT);
                    reached |= 1 << port;
                    int p = ports[port];
                    if (p == Lens.PORT_EYE) {
                        eye = true;
                    } else if (Lens.isReceptor(p) && Lens.receptorColor(p) == color) {
                        lit |= 1 << port;
                    }
                    break;
                }
                int i = nz * n + nx;
                int key = (i * 4 + d) * 4 + color;
                if (seen[key]) {
                    count = put(seg, count, sx, sz, nx, nz, color, END_PIECE);
                    break;
                }
                seen[key] = true;
                int c = cells[i];
                int k = Lens.kind(c);
                if (k == Lens.EMPTY) {
                    x = nx;
                    z = nz;
                    continue;
                }
                if (k == Lens.FILTER) {
                    int fc = Lens.color(c);
                    if (fc != color) {
                        count = put(seg, count, sx, sz, nx, nz, color, END_PIECE);
                        sx = nx;
                        sz = nz;
                        color = fc;
                    }
                    x = nx;
                    z = nz;
                    continue;
                }
                if (k == Lens.MIRROR) {
                    int out = Lens.mirrorOut(Lens.turn(c), d);
                    if (out == d) {
                        x = nx;
                        z = nz;
                        continue;
                    }
                    count = put(seg, count, sx, sz, nx, nz, color, END_PIECE);
                    if (out != Lens.opposite(d) && top + 4 <= stack.length) {
                        stack[top++] = nx;
                        stack[top++] = nz;
                        stack[top++] = out;
                        stack[top++] = color;
                    }
                    break;
                }
                count = put(seg, count, sx, sz, nx, nz, color, END_PIECE);
                if (k == Lens.SPLITTER && Lens.turn(c) == d && top + 8 <= stack.length) {
                    stack[top++] = nx;
                    stack[top++] = nz;
                    stack[top++] = Lens.cw(d);
                    stack[top++] = color;
                    stack[top++] = nx;
                    stack[top++] = nz;
                    stack[top++] = Lens.ccw(d);
                    stack[top++] = color;
                }
                break;
            }
        }
        float[] closest = new float[ports.length];
        Arrays.fill(closest, Float.POSITIVE_INFINITY);
        for (int p = 0; p < ports.length; p++) {
            if ((reached & 1 << p) != 0) {
                closest[p] = 0f;
                continue;
            }
            float px = Lens.portX(n, p);
            float pz = Lens.portZ(n, p);
            for (int s = 0; s < count; s++) {
                closest[p] = Math.min(closest[p], distance(px, pz, seg[s * 6], seg[s * 6 + 1], seg[s * 6 + 2], seg[s * 6 + 3]));
            }
        }
        return new Result(Arrays.copyOf(seg, count * 6), count, lit, reached, eye, closest, truncated);
    }

    /**
     * The same rules without drawing: the ports lit (bits 0 to 27) and bit 31 set if the Eye was reached. No
     * allocation beyond {@code scratch} (at least {@code n * n * 16} long). For the generator and the solver.
     */
    public static int litMask(int n, int[] cells, int[] ports, boolean[] scratch) {
        Arrays.fill(scratch, 0, n * n * 16, false);
        int[] stack = new int[4 * 64];
        int top = 0;
        int lit = 0;
        int segments = 0;
        int src = sourceOf(cells);
        if (src < 0) {
            return 0;
        }
        stack[top++] = src % n;
        stack[top++] = src / n;
        stack[top++] = Lens.turn(cells[src]);
        stack[top++] = Lens.WHITE;
        while (top > 0 && segments < Lens.MAX_SEGMENTS) {
            int color = stack[--top];
            int d = stack[--top];
            int z = stack[--top];
            int x = stack[--top];
            segments++;
            while (true) {
                int nx = x + Lens.DX[d];
                int nz = z + Lens.DZ[d];
                if (nx < 0 || nx >= n || nz < 0 || nz >= n) {
                    int port = Lens.portLeaving(n, x, z, d);
                    int p = ports[port];
                    if (p == Lens.PORT_EYE) {
                        lit |= 1 << 31;
                    } else if (Lens.isReceptor(p) && Lens.receptorColor(p) == color) {
                        lit |= 1 << port;
                    }
                    break;
                }
                int i = nz * n + nx;
                int key = (i * 4 + d) * 4 + color;
                if (scratch[key]) {
                    break;
                }
                scratch[key] = true;
                int c = cells[i];
                int k = Lens.kind(c);
                x = nx;
                z = nz;
                if (k == Lens.EMPTY) {
                    continue;
                }
                if (k == Lens.FILTER) {
                    if (Lens.color(c) != color) {
                        color = Lens.color(c);
                        segments++;
                    }
                    continue;
                }
                if (k == Lens.MIRROR) {
                    int out = Lens.mirrorOut(Lens.turn(c), d);
                    if (out == d) {
                        continue;
                    }
                    if (out != Lens.opposite(d) && top + 4 <= stack.length) {
                        stack[top++] = x;
                        stack[top++] = z;
                        stack[top++] = out;
                        stack[top++] = color;
                    }
                    break;
                }
                if (k == Lens.SPLITTER && Lens.turn(c) == d && top + 8 <= stack.length) {
                    stack[top++] = x;
                    stack[top++] = z;
                    stack[top++] = Lens.cw(d);
                    stack[top++] = color;
                    stack[top++] = x;
                    stack[top++] = z;
                    stack[top++] = Lens.ccw(d);
                    stack[top++] = color;
                }
                break;
            }
        }
        return lit;
    }

    static int sourceOf(int[] cells) {
        for (int i = 0; i < cells.length; i++) {
            if (Lens.kind(cells[i]) == Lens.SOURCE) {
                return i;
            }
        }
        return -1;
    }

    private static int put(int[] seg, int count, int x0, int z0, int x1, int z1, int color, int end) {
        int o = count * 6;
        seg[o] = x0;
        seg[o + 1] = z0;
        seg[o + 2] = x1;
        seg[o + 3] = z1;
        seg[o + 4] = color;
        seg[o + 5] = end;
        return count + 1;
    }

    /**
     * The nearest any of {@code segments} (6 ints each, as {@link Result#segments}) comes to (px, pz), in grid
     * units, or infinity with none. What a receptor's hum rises with.
     */
    public static float closest(int[] segments, float px, float pz) {
        float best = Float.POSITIVE_INFINITY;
        for (int s = 0; s + 5 < segments.length; s += 6) {
            best = Math.min(best, distance(px, pz, segments[s], segments[s + 1], segments[s + 2], segments[s + 3]));
        }
        return best;
    }

    /** Distance from (px, pz) to the axis-aligned segment (x0, z0) to (x1, z1). */
    static float distance(float px, float pz, int x0, int z0, int x1, int z1) {
        float cx = Math.max(Math.min(x0, x1), Math.min(Math.max(x0, x1), px));
        float cz = Math.max(Math.min(z0, z1), Math.min(Math.max(z0, z1), pz));
        float dx = px - cx;
        float dz = pz - cz;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }
}
