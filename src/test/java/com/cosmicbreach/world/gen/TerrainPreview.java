package com.cosmicbreach.world.gen;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Offline pictures of the terrain model, for tuning shapes without starting the game. Runs only with the
 * environment variable {@code CB_PREVIEW} set (to a salt, or "1"): {@code CB_PREVIEW=1 ./gradlew test
 * --tests '*TerrainPreview*'}. Writes PNGs into {@code build/terrain-preview/}.
 */
class TerrainPreview {
    private static final File OUT = new File("build/terrain-preview");

    @Test
    void render() throws IOException {
        String env = System.getenv("CB_PREVIEW");
        Assumptions.assumeTrue(env != null && !env.isBlank());
        long salt = env.equals("1") ? 20260927L : Long.parseLong(env);
        AetheriaTerrain t = AetheriaTerrain.forSalt(salt);
        OUT.mkdirs();
        String only = System.getenv().getOrDefault("CB_PREVIEW_ONLY", "reach,slice,drift,deep,view");
        if (only.contains("reach")) {
            reachMap(t, 0, 0, 1536, "reach_map.png");
        }
        if (only.contains("slice")) {
            slice(t, 230, "slice_z230.png");
        }
        if (only.contains("drift")) {
            driftMap(t, 1536, "drift_map.png");
        }
        if (only.contains("deep")) {
            deepMap(t, 1536, "deep_map.png");
        }
        if (only.contains("view")) {
            view(t, "view_reach.png", 260, 372, -190, -35, 12, 800, 450);
            view(t, "view_deep.png", 300, 132, 300, -60, 15, 800, 450);
            view(t, "view_drift.png", 250, 240, -320, 20, 5, 800, 450);
        }
    }

    // ------------------------------------------------------------------ top-down Reach

    private static void reachMap(AetheriaTerrain t, int cx, int cz, int size, String name) throws IOException {
        int half = size / 2;
        int[] tops = new int[size * size];
        byte[] kind = new byte[size * size];
        IntStream.range(0, size).parallel().forEach(row -> {
            AetheriaTerrain.Column col = new AetheriaTerrain.Column();
            for (int i = 0; i < size; i++) {
                int x = cx - half + i;
                int z = cz - half + row;
                t.reach.sample(x + 0.5, z + 0.5, col.reach);
                int top = -1;
                if (col.reach.island || col.reach.neck) {
                    for (int y = Math.min(400, col.reach.maxY); y >= ReachIslands.FLOOR_Y; y--) {
                        if (t.reach.density(col.reach, x, y, z) > 0) {
                            top = y;
                            break;
                        }
                    }
                }
                tops[row * size + i] = top;
                ReachIslands.Isle isle = col.reach.isle;
                kind[row * size + i] = (byte) (top < 0 ? 0 : (col.reach.neckness > 0.5 && col.reach.edge < 0 ? 3 : (isle != null && isle.sunfield ? 2 : 1)));
            }
        });
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                int top = tops[z * size + x];
                if (top < 0) {
                    img.setRGB(x, z, 0x1B2A44);
                    continue;
                }
                int left = x > 0 ? tops[z * size + x - 1] : top;
                int up = z > 0 ? tops[(z - 1) * size + x] : top;
                if (left < 0) left = top;
                if (up < 0) up = top;
                double shade = 0.78 + 0.09 * ((top - left) + (top - up));
                double h = (top - 340) / 45.0;
                int base = switch (kind[z * size + x]) {
                    case 2 -> 0xE8C66A;
                    case 3 -> 0xC9A0E0;
                    default -> 0xC8D8A0;
                };
                img.setRGB(x, z, tint(base, shade * (0.75 + 0.35 * h)));
            }
        }
        ImageIO.write(img, "png", new File(OUT, name));
    }

    // ------------------------------------------------------------------ side slice through all layers

    private static void slice(AetheriaTerrain t, int z, String name) throws IOException {
        int w = 1536;
        int h = 480;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        IntStream.range(0, w).parallel().forEach(i -> {
            int x = i - w / 2;
            AetheriaTerrain.Column col = new AetheriaTerrain.Column();
            t.sampleColumn(x, z, col);
            for (int y = 0; y < h; y++) {
                double d = t.blocks(col, x, y, z);
                int c;
                if (d > 0) {
                    c = y >= 300 ? 0xE9E4D6 : (y >= 160 ? 0x7F97A8 : 0x2A2433);
                } else if ((y >= 300 && y < 320) || (y >= 145 && y < 160)) {
                    c = 0x3A2A2A;
                } else {
                    c = 0x0E1626;
                }
                synchronized (img) {
                    img.setRGB(i, h - 1 - y, c);
                }
            }
        });
        ImageIO.write(img, "png", new File(OUT, name));
    }

    // ------------------------------------------------------------------ Drift: rock per column

    private static void driftMap(AetheriaTerrain t, int size, String name) throws IOException {
        int half = size / 2;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        IntStream.range(0, size).parallel().forEach(row -> {
            AetheriaTerrain.Column col = new AetheriaTerrain.Column();
            for (int i = 0; i < size; i++) {
                int x = i - half;
                int z = row - half;
                t.drift.candidates(x, z, col.asteroids);
                int count = 0;
                int topY = -1;
                for (int y = DriftBelts.FLOOR_Y; y <= DriftBelts.CEIL_Y; y++) {
                    double best = -8;
                    for (DriftBelts.Asteroid a : col.asteroids) {
                        best = Math.max(best, t.drift.density(a, x, y, z));
                    }
                    if (best > 0) {
                        count++;
                        topY = y;
                    }
                }
                double belt = t.drift.belt(x, z);
                int bg = tint(0x101826, 1.0 + belt * 1.5);
                int c = count == 0 ? bg : tint(0x9FB7C8, 0.45 + Math.min(1.0, count / 20.0) * 0.4 + (topY - 160) / 280.0);
                synchronized (img) {
                    img.setRGB(i, row, c);
                }
            }
        });
        ImageIO.write(img, "png", new File(OUT, name));
    }

    // ------------------------------------------------------------------ Deep: top of the rock

    private static void deepMap(AetheriaTerrain t, int size, String name) throws IOException {
        int half = size / 2;
        int[] tops = new int[size * size];
        IntStream.range(0, size).parallel().forEach(row -> {
            AetheriaTerrain.Column col = new AetheriaTerrain.Column();
            for (int i = 0; i < size; i++) {
                int x = i - half;
                int z = row - half;
                t.deep.sample(x, z, col.deep);
                int top = -1;
                if (!col.deep.empty()) {
                    for (int y = Math.min(DeepSpans.CEIL_Y, col.deep.maxY); y >= Math.max(1, col.deep.minY); y--) {
                        if (t.deep.density(col.deep, x, y, z) > 0) {
                            top = y;
                            break;
                        }
                    }
                }
                tops[row * size + i] = top;
            }
        });
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                int top = tops[z * size + x];
                if (top < 0) {
                    img.setRGB(x, z, 0x07050C);
                    continue;
                }
                int left = x > 0 && tops[z * size + x - 1] >= 0 ? tops[z * size + x - 1] : top;
                int up = z > 0 && tops[(z - 1) * size + x] >= 0 ? tops[(z - 1) * size + x] : top;
                double shade = 0.8 + 0.05 * ((top - left) + (top - up));
                img.setRGB(x, z, tint(0x6B5A8A, shade * (0.4 + top / 160.0)));
            }
        }
        ImageIO.write(img, "png", new File(OUT, name));
    }

    // ------------------------------------------------------------------ a ray-marched perspective view

    /**
     * A simple perspective render: rays march in 0.5-block steps through the density, hit points are lit
     * by a sun from the south-west with a sky term, fog by distance. Colours by layer.
     */
    private static void view(AetheriaTerrain t, String name, double ex, double ey, double ez, double yawDeg, double pitchDeg,
            int w, int h) throws IOException {
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        // forward: yaw 0 looks toward +z (south), like Minecraft
        double fx = -Math.sin(yaw) * Math.cos(pitch);
        double fy = -Math.sin(pitch);
        double fz = Math.cos(yaw) * Math.cos(pitch);
        // screen right = f x worldUp, camera up = right x f
        double rl = Math.hypot(fz, fx);
        double rx = -fz / rl;
        double rz = fx / rl;
        double[] upv = {0 * fz - rz * fy, rz * fx - rx * fz, rx * fy - 0 * fx};
        double fov = Math.toRadians(70);
        double scale = Math.tan(fov / 2);
        double aspect = w / (double) h;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        double sunX = -0.45;
        double sunY = 0.8;
        double sunZ = 0.4;
        double sl = Math.sqrt(sunX * sunX + sunY * sunY + sunZ * sunZ);
        final double lx = sunX / sl;
        final double ly = sunY / sl;
        final double lz = sunZ / sl;
        ThreadLocal<ThreadLocalColumns> caches = ThreadLocal.withInitial(() -> new ThreadLocalColumns(t));
        IntStream.range(0, h).parallel().forEach(py -> {
            ThreadLocalColumns cache = caches.get();
            for (int px = 0; px < w; px++) {
                double sx = (2 * (px + 0.5) / w - 1) * scale * aspect;
                double sy = (1 - 2 * (py + 0.5) / h) * scale;
                double dx = fx + sx * rx + sy * upv[0];
                double dy = fy + sy * upv[1];
                double dz = fz + sx * rz + sy * upv[2];
                double dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
                dx /= dl;
                dy /= dl;
                dz /= dl;
                int color = sky(dy);
                double maxDist = 520;
                for (double s = 0.5; s < maxDist; s += 0.6) {
                    double x = ex + dx * s;
                    double y = ey + dy * s;
                    double z = ez + dz * s;
                    if (y < 0 || y > 479) {
                        break;
                    }
                    int bx = (int) Math.floor(x);
                    int by = (int) Math.floor(y);
                    int bz = (int) Math.floor(z);
                    if (cache.solid(bx, by, bz)) {
                        // normal from neighbours (voxel faces)
                        double nx = (cache.solid(bx - 1, by, bz) ? 1 : 0) - (cache.solid(bx + 1, by, bz) ? 1 : 0);
                        double ny = (cache.solid(bx, by - 1, bz) ? 1 : 0) - (cache.solid(bx, by + 1, bz) ? 1 : 0);
                        double nz = (cache.solid(bx, by, bz - 1) ? 1 : 0) - (cache.solid(bx, by, bz + 1) ? 1 : 0);
                        double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
                        if (nl == 0) {
                            ny = 1;
                            nl = 1;
                        }
                        nx /= nl;
                        ny /= nl;
                        nz /= nl;
                        double diff = Math.max(0, nx * lx + ny * ly + nz * lz);
                        // shadow: march toward the sun a little
                        boolean shadow = false;
                        for (double k = 1.5; k < 60; k += 1.5) {
                            if (cache.solid((int) Math.floor(x + lx * k), (int) Math.floor(y + ly * k), (int) Math.floor(z + lz * k))) {
                                shadow = true;
                                break;
                            }
                        }
                        double light = 0.35 + 0.25 * (ny * 0.5 + 0.5) + (shadow ? 0 : 0.6 * diff);
                        int base;
                        if (by >= 300) {
                            boolean grass = !cache.solid(bx, by + 1, bz);
                            base = grass ? 0xD9C46E : 0xE9E4D6;
                        } else if (by >= 160) {
                            base = 0x8FA6B8;
                        } else {
                            base = 0x3A3148;
                        }
                        int lit = tint(base, light);
                        double fog = Math.min(1.0, Math.max(0, (s - 200) / 320.0));
                        color = mix(lit, sky(dy), fog);
                        break;
                    }
                }
                synchronized (img) {
                    img.setRGB(px, py, color);
                }
            }
        });
        ImageIO.write(img, "png", new File(OUT, name));
    }

    /** Column cache for the ray marcher (per thread): each column as a 480-bit rock mask. */
    private static final class ThreadLocalColumns {
        private final AetheriaTerrain t;
        private final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<long[]> map = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
        private final AetheriaTerrain.Column scratch = new AetheriaTerrain.Column();

        ThreadLocalColumns(AetheriaTerrain t) {
            this.t = t;
        }

        boolean solid(int x, int y, int z) {
            if (y < 0 || y > 479 || !AetheriaTerrain.mayHaveRock(y)) {
                return false;
            }
            long key = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
            long[] bits = map.get(key);
            if (bits == null) {
                if (map.size() > 60_000) {
                    map.clear();
                }
                bits = new long[8];
                t.sampleColumn(x, z, scratch);
                for (int yy = 0; yy < 480; yy++) {
                    if (AetheriaTerrain.mayHaveRock(yy) && t.blocks(scratch, x, yy, z) > 0) {
                        bits[yy >> 6] |= 1L << (yy & 63);
                    }
                }
                map.put(key, bits);
            }
            return (bits[y >> 6] & (1L << (y & 63))) != 0;
        }
    }

    private static int sky(double dy) {
        return mix(0xBFD9F2, 0x6FA3D8, Math.max(0, Math.min(1, dy * 1.5 + 0.2)));
    }

    private static int tint(int rgb, double f) {
        int r = (int) Math.max(0, Math.min(255, ((rgb >> 16) & 255) * f));
        int g = (int) Math.max(0, Math.min(255, ((rgb >> 8) & 255) * f));
        int b = (int) Math.max(0, Math.min(255, (rgb & 255) * f));
        return (r << 16) | (g << 8) | b;
    }

    private static int mix(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
