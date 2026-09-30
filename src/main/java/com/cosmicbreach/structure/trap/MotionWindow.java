package com.cosmicbreach.structure.trap;

import java.util.Arrays;

/**
 * How fast a player has really been moving on the server: horizontal blocks per tick of their own movement, over the
 * last {@link #TICKS} server ticks. A server tick is a poor clock for this when the server is busy: each movement
 * packet is one tick of the client's movement, and a long server tick makes them arrive bunched, two or three in one
 * tick after none in another. Divided by server ticks, a walker's bunch reads as a sprint (and a runner's stall as
 * standing); divided by the packets that carried it, the same movement reads true however it arrived. So each server
 * tick is weighed by the packets it brought ({@link #clientTicks}): a tick per packet, one for movement the server made
 * without any, none for a tick with neither.
 *
 * <p>Feed it the player's position and packet count at the end of every server tick ({@link #endTick}); read it at the
 * end of a tick ({@link #average}) or mid-tick, while the server applies this tick's packets ({@link #averageWith}: this
 * tick so far and the two before it). A jump of {@link KineticRules#TELEPORT} blocks or more in one tick is a teleport:
 * the window starts over. Pure.
 */
public final class MotionWindow {
    public static final int TICKS = 3;

    private final double[] step = new double[TICKS];
    private final int[] time = new int[TICKS];
    private int head;
    private double lastX;
    private double lastZ;
    private boolean started;

    /**
     * The end of a server tick: the player stands at (x, z), and their client sent {@code packets} movement packets
     * during it.
     */
    public void endTick(double x, double z, int packets) {
        double d = started ? Math.hypot(x - lastX, z - lastZ) : 0.0;
        if (d >= KineticRules.TELEPORT) {
            Arrays.fill(step, 0.0);
            Arrays.fill(time, 0);
            d = 0.0;
            packets = 0;
        }
        step[head] = d;
        time[head] = clientTicks(d, packets);
        head = (head + 1) % TICKS;
        lastX = x;
        lastZ = z;
        started = true;
    }

    /** Blocks per tick of the player's movement over the last {@link #TICKS} whole server ticks. */
    public double average() {
        double sum = 0;
        int ticks = 0;
        for (int i = 0; i < TICKS; i++) {
            sum += step[i];
            ticks += time[i];
        }
        return ticks == 0 ? 0.0 : sum / ticks;
    }

    /**
     * Blocks per tick of the player's movement over this server tick so far (the player now at (x, z), after
     * {@code packetsSoFar} packets) and the {@link #TICKS} - 1 whole server ticks before it.
     */
    public double averageWith(double x, double z, int packetsSoFar) {
        double now = started ? Math.hypot(x - lastX, z - lastZ) : 0.0;
        if (now >= KineticRules.TELEPORT) {
            now = 0.0;
            packetsSoFar = 0;
        }
        double sum = now;
        int ticks = clientTicks(now, packetsSoFar);
        for (int i = 1; i < TICKS; i++) {
            int k = Math.floorMod(head - i, TICKS);
            sum += step[k];
            ticks += time[k];
        }
        return ticks == 0 ? 0.0 : sum / ticks;
    }

    /**
     * The ticks of a player's movement one server tick held: one per movement packet, one if the server moved them
     * without a packet, none if nothing arrived and nothing moved (standing still sends nothing).
     */
    public static int clientTicks(double moved, int packets) {
        return packets > 0 ? packets : moved > 0 ? 1 : 0;
    }
}
