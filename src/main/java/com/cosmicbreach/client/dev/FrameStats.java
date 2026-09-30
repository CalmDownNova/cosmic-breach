package com.cosmicbreach.client.dev;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GLCapabilities;

/**
 * What a whole frame costs while {@link #enabled} (the stress scenario turns it on): the CPU time of the frame's
 * render (from NeoForge's frame start to its end, so the frame limiter's sleep is not counted), the GPU time of
 * everything drawn in it (a pair of GL timestamp queries, read a few frames late; timestamps, not an elapsed-time
 * query, so {@code SkyStats}'s query can run inside the frame), and the interval between frames. Dev only.
 */
public final class FrameStats {
    public static volatile boolean enabled;
    private static boolean installed;

    private static final int RING = 6;
    private static int[] queries;
    private static final boolean[] pending = new boolean[RING];
    private static int next;
    private static boolean gpuTiming;
    private static boolean checked;
    private static boolean open;
    private static long start;
    private static long lastStart;
    private static final List<Long> CPU = new ArrayList<>();
    private static final List<Long> GPU = new ArrayList<>();
    private static final List<Long> INTERVAL = new ArrayList<>();

    private FrameStats() {
    }

    /** Hooks the frame events (once). */
    public static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        NeoForge.EVENT_BUS.addListener(RenderFrameEvent.Pre.class, e -> begin());
        NeoForge.EVENT_BUS.addListener(RenderFrameEvent.Post.class, e -> end());
    }

    private static void begin() {
        if (!enabled) {
            open = false;
            return;
        }
        if (!checked) {
            checked = true;
            GLCapabilities caps = GL.getCapabilities();
            gpuTiming = caps.OpenGL33 || caps.GL_ARB_timer_query;
            if (gpuTiming) {
                queries = new int[2 * RING];
                GL15.glGenQueries(queries);
            }
        }
        start = System.nanoTime();
        if (lastStart != 0) {
            INTERVAL.add(start - lastStart);
        }
        lastStart = start;
        if (gpuTiming) {
            int q = next % RING;
            if (pending[q]) {
                if (GL15.glGetQueryObjecti(queries[2 * q + 1], GL15.GL_QUERY_RESULT_AVAILABLE) != 0) {
                    long a = GL33.glGetQueryObjecti64(queries[2 * q], GL15.GL_QUERY_RESULT);
                    long b = GL33.glGetQueryObjecti64(queries[2 * q + 1], GL15.GL_QUERY_RESULT);
                    GPU.add(b - a);
                }
                pending[q] = false;
            }
            GL33.glQueryCounter(queries[2 * q], GL33.GL_TIMESTAMP);
        }
        open = true;
    }

    private static void end() {
        if (!open) {
            return;
        }
        open = false;
        CPU.add(System.nanoTime() - start);
        if (gpuTiming) {
            int q = next % RING;
            GL33.glQueryCounter(queries[2 * q + 1], GL33.GL_TIMESTAMP);
            pending[q] = true;
            next++;
        }
    }

    public static void reset() {
        CPU.clear();
        GPU.clear();
        INTERVAL.clear();
        lastStart = 0;
    }

    public static int samples() {
        return CPU.size();
    }

    public static double cpuMedianMs() {
        return percentile(CPU, 0.5);
    }

    public static double cpuP95Ms() {
        return percentile(CPU, 0.95);
    }

    public static double gpuMedianMs() {
        return percentile(GPU, 0.5);
    }

    public static double gpuP95Ms() {
        return percentile(GPU, 0.95);
    }

    /** Frames a second from the median interval between frames (capped by the client's frame limit). */
    public static double fps() {
        double ms = percentile(INTERVAL, 0.5);
        return Double.isNaN(ms) || ms <= 0 ? Double.NaN : 1000.0 / ms;
    }

    public static String report() {
        return String.format(Locale.ROOT, "frames over %d: CPU median %.2f ms, p95 %.2f; GPU (%d samples) median %.2f ms, p95 %.2f; "
                        + "%.0f fps from the median interval (the test client caps at 60)", CPU.size(), cpuMedianMs(), cpuP95Ms(),
                GPU.size(), gpuMedianMs(), gpuP95Ms(), fps());
    }

    private static double percentile(List<Long> samples, double p) {
        if (samples.isEmpty()) {
            return Double.NaN;
        }
        List<Long> sorted = new ArrayList<>(samples);
        sorted.sort(null);
        return sorted.get(Math.min(sorted.size() - 1, (int) Math.floor(p * sorted.size()))) / 1.0e6;
    }
}
