package com.cosmicbreach.client.sky;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GLCapabilities;

/**
 * What the sky costs, measured while {@link #enabled} (the sky scenario turns it on): the CPU time of
 * {@link AetheriaSkyRenderer#render} (building quads, setting uniforms, issuing draws) and the GPU time of
 * its draws from GL timer queries, read a few frames late so the CPU never waits for them.
 */
public final class SkyStats {
    public static volatile boolean enabled;

    private static final int RING = 6;
    private static int[] queries;
    private static final boolean[] pending = new boolean[RING];
    private static int next;
    private static boolean gpuTiming;
    private static boolean checked;
    private static final List<Long> CPU = new ArrayList<>();
    private static final List<Long> GPU = new ArrayList<>();

    private SkyStats() {
    }

    static void begin() {
        if (!enabled) {
            return;
        }
        if (!checked) {
            checked = true;
            GLCapabilities caps = GL.getCapabilities();
            gpuTiming = caps.OpenGL33 || caps.GL_ARB_timer_query;
            if (gpuTiming) {
                queries = new int[RING];
                GL15.glGenQueries(queries);
            }
        }
        if (!gpuTiming) {
            return;
        }
        int q = next % RING;
        if (pending[q]) {
            if (GL15.glGetQueryObjecti(queries[q], GL15.GL_QUERY_RESULT_AVAILABLE) != 0) {
                GPU.add(GL33.glGetQueryObjecti64(queries[q], GL15.GL_QUERY_RESULT));
            }
            pending[q] = false;
        }
        GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, queries[q]);
        pending[q] = true;
    }

    static void end(long cpuNanos) {
        if (!enabled) {
            return;
        }
        if (gpuTiming) {
            GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
            next++;
        }
        CPU.add(cpuNanos);
    }

    /** Clears the samples (start of a measurement). */
    public static void reset() {
        CPU.clear();
        GPU.clear();
    }

    public static int samples() {
        return CPU.size();
    }

    /** Median CPU milliseconds per frame, or NaN with no samples. */
    public static double cpuMedianMs() {
        return percentile(CPU, 0.5);
    }

    public static double gpuMedianMs() {
        return percentile(GPU, 0.5);
    }

    public static String report() {
        return String.format(Locale.ROOT, "sky cost over %d frames: CPU median %.3f ms, mean %.3f, p95 %.3f; GPU (%d timer samples) median %.3f ms, mean %.3f, p95 %.3f",
                CPU.size(), percentile(CPU, 0.5), mean(CPU), percentile(CPU, 0.95),
                GPU.size(), percentile(GPU, 0.5), mean(GPU), percentile(GPU, 0.95));
    }

    private static double percentile(List<Long> samples, double p) {
        if (samples.isEmpty()) {
            return Double.NaN;
        }
        List<Long> sorted = new ArrayList<>(samples);
        sorted.sort(null);
        return sorted.get(Math.min(sorted.size() - 1, (int) Math.floor(p * sorted.size()))) / 1.0e6;
    }

    private static double mean(List<Long> samples) {
        if (samples.isEmpty()) {
            return Double.NaN;
        }
        long sum = 0;
        for (long s : samples) {
            sum += s;
        }
        return sum / (double) samples.size() / 1.0e6;
    }
}
