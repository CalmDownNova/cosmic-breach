package com.cosmicbreach.guardian.heliarch;

import net.minecraft.world.phys.Vec3;

/**
 * The four void tendrils of phase 2 (GDD 7.3), anchored in floor cracks round the dais. Each lashes on its own
 * every {@value HeliarchMoves#LASH_EVERY} ticks: {@value HeliarchMoves#LASH_TELL} ticks of magenta cracks along the
 * line (1.5 wide, 12 long, from its anchor toward its mark), then the lash (16 and a stack of Rift, parryable). Each
 * has {@value HeliarchMoves#TENDRIL_HEALTH} health: cutting one gives its cutter +15 Resonance and silences it for
 * {@value HeliarchMoves#TENDRIL_SILENCE} ticks (it sinks into its crack and grows back whole). Pure.
 */
public final class TendrilRules {
    private TendrilRules() {
    }

    /** One tendril. */
    public static final class Tendril {
        private final int index;
        private double health = HeliarchMoves.TENDRIL_HEALTH;
        private long silencedUntil = Long.MIN_VALUE;
        private long nextLash;
        private int cuts;

        public Tendril(int index, long firstLash) {
            this.index = index;
            this.nextLash = firstLash;
        }

        public int index() {
            return index;
        }

        public double health() {
            return health;
        }

        public int cuts() {
            return cuts;
        }

        public long silencedUntil() {
            return silencedUntil;
        }

        public boolean silenced(long now) {
            return now < silencedUntil;
        }

        /**
         * A hit of {@code amount} at {@code now}. Returns true if it cut the tendril: it is silenced, its health comes
         * back whole, and its next lash waits until it has risen again. A silenced tendril can't be hit.
         */
        public boolean hit(double amount, long now) {
            if (silenced(now) || amount <= 0) {
                return false;
            }
            health -= amount;
            if (health > 1e-6) {
                return false;
            }
            cuts++;
            health = HeliarchMoves.TENDRIL_HEALTH;
            silencedUntil = now + HeliarchMoves.TENDRIL_SILENCE;
            nextLash = Math.max(nextLash, silencedUntil + HeliarchMoves.LASH_TELL);
            return true;
        }

        /**
         * Drawn back up whole (Nova's channel gathers its roots, so its shield can always be struck through them): no
         * longer silenced, its health full.
         */
        public void regrow() {
            health = HeliarchMoves.TENDRIL_HEALTH;
            silencedUntil = Long.MIN_VALUE;
        }

        /** True when it should start a lash at {@code now}. */
        public boolean lashDue(long now) {
            return !silenced(now) && now >= nextLash;
        }

        /** A lash started at {@code now}: the next one comes a full cycle later. */
        public void lashed(long now) {
            nextLash = now + HeliarchMoves.LASH_EVERY;
        }

        public long nextLash() {
            return nextLash;
        }
    }

    /** The four, their first lashes staggered so they come one after another. */
    public static Tendril[] create(long now) {
        Tendril[] out = new Tendril[HeliarchMoves.TENDRIL_COUNT];
        for (int i = 0; i < out.length; i++) {
            out[i] = new Tendril(i, now + 60 + (long) i * HeliarchMoves.LASH_EVERY / HeliarchMoves.TENDRIL_COUNT);
        }
        return out;
    }

    /** The far end of a lash from {@code anchor} toward {@code mark} ({@link HeliarchMoves#LASH_LENGTH} long, flat). */
    public static Vec3 lashEnd(Vec3 anchor, Vec3 mark) {
        Vec3 d = new Vec3(mark.x - anchor.x, 0.0, mark.z - anchor.z);
        if (d.lengthSqr() < 1e-6) {
            d = new Vec3(HeliarchArena.CX - anchor.x, 0.0, HeliarchArena.CZ - anchor.z);
        }
        return anchor.add(d.normalize().scale(HeliarchMoves.LASH_LENGTH));
    }

    /**
     * True if a player whose middle stands at (x, z) with feet {@code feet} over the floor is on the lash line from
     * {@code anchor} to {@code end}: within half its width (plus the player's half width) of the line, not higher
     * than a jump.
     */
    public static boolean onLash(double x, double z, double feet, Vec3 anchor, Vec3 end) {
        if (feet > 1.6) {
            return false;
        }
        double ax = anchor.x;
        double az = anchor.z;
        double dx = end.x - ax;
        double dz = end.z - az;
        double len2 = dx * dx + dz * dz;
        double t = len2 < 1e-9 ? 0.0 : Math.max(0.0, Math.min(1.0, ((x - ax) * dx + (z - az) * dz) / len2));
        double nx = ax + dx * t - x;
        double nz = az + dz * t - z;
        double reach = HeliarchMoves.LASH_WIDTH / 2.0 + 0.3;
        return nx * nx + nz * nz <= reach * reach;
    }
}
