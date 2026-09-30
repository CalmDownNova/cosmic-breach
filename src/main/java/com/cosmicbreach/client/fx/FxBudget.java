package com.cosmicbreach.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleGroup;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Keeps our particles inside the budget ({@link FxRates}): scales every effect's count by the
 * vanilla particle setting and the distance to the camera, and never has more than
 * {@value FxRates#LIMIT} alive. The particle engine enforces the limit itself too (every
 * {@link FxParticle} is in {@link #GROUP}); the list here is our own count, for the scaling and the
 * logs. A particle leaves it when it dies, or when the engine hasn't ticked it for a few ticks (the
 * engine drops everything on a level change or resource reload without telling anyone).
 */
public final class FxBudget {
    public static final ParticleGroup GROUP = new ParticleGroup(FxRates.LIMIT);
    /** Ticks without an engine tick before a particle counts as dropped. New ones wait 2 to be ticked. */
    private static final int STALE_TICKS = 3;

    private static final List<FxParticle> LIVE = new ArrayList<>();
    private static int peak;

    private FxBudget() {
    }

    /** Vanilla's particle setting as a rate. */
    public static double settingRate(Minecraft mc) {
        return switch (mc.options.particles().get()) {
            case ALL -> FxRates.ALL;
            case DECREASED -> FxRates.DECREASED;
            case MINIMAL -> FxRates.MINIMAL;
        };
    }

    /** How many of {@code wanted} particles an effect at {@code at} gets. */
    public static int count(int wanted, Vec3 at, boolean important) {
        Minecraft mc = Minecraft.getInstance();
        double distance = mc.gameRenderer.getMainCamera().getPosition().distanceTo(at);
        double rate = settingRate(mc) * FxRates.distanceFactor(distance, important);
        return FxRates.count(wanted, rate, FxRates.LIMIT - LIVE.size(), Math.random(), important);
    }

    /** Adds a particle if there is room. */
    public static boolean spawn(FxParticle particle) {
        if (LIVE.size() >= FxRates.LIMIT) {
            return false;
        }
        Minecraft.getInstance().particleEngine.add(particle);
        LIVE.add(particle);
        peak = Math.max(peak, LIVE.size());
        return true;
    }

    /** Called once per client tick after the particle engine ticked. */
    static void tick() {
        long now = FxClock.ticks();
        Iterator<FxParticle> it = LIVE.iterator();
        while (it.hasNext()) {
            FxParticle particle = it.next();
            if (!particle.isAlive() || now - particle.lastTicked > STALE_TICKS) {
                it.remove();
            }
        }
    }

    static void clear() {
        LIVE.clear();
    }

    /** Our particles alive now. */
    public static int live() {
        return LIVE.size();
    }

    /** The most of our particles alive at once since the last {@link #resetPeak}. */
    public static int peak() {
        return peak;
    }

    public static void resetPeak() {
        peak = LIVE.size();
    }
}
