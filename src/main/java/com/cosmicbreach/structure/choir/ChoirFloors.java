package com.cosmicbreach.structure.choir;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Every loaded Choir Floor, per level (both sides), and the hook for structures that react to one being solved (the
 * Breach Sanctum's east wing lights an Eclipse Lock):
 *
 * <pre>{@code
 * ChoirFloors.onSolved((level, conductor) -> {
 *     if (conductor.getBlockPos().equals(eastWingConductor)) lightEclipseLock(level);
 * });
 * }</pre>
 */
public final class ChoirFloors {
    private static final Map<Level, Set<ConductorBlockEntity>> BY_LEVEL = new HashMap<>();
    private static final List<BiConsumer<ServerLevel, ConductorBlockEntity>> SOLVED = new CopyOnWriteArrayList<>();

    private ChoirFloors() {
    }

    /** Called on the server thread each time a floor is solved, after its vault opened. */
    public static void onSolved(BiConsumer<ServerLevel, ConductorBlockEntity> listener) {
        SOLVED.add(listener);
    }

    static void solved(ServerLevel level, ConductorBlockEntity conductor) {
        for (BiConsumer<ServerLevel, ConductorBlockEntity> l : SOLVED) {
            l.accept(level, conductor);
        }
    }

    static synchronized void add(ConductorBlockEntity be) {
        if (be.getLevel() != null) {
            BY_LEVEL.computeIfAbsent(be.getLevel(), l -> new LinkedHashSet<>()).add(be);
        }
    }

    static synchronized void remove(ConductorBlockEntity be) {
        if (be.getLevel() != null) {
            Set<ConductorBlockEntity> set = BY_LEVEL.get(be.getLevel());
            if (set != null) {
                set.remove(be);
            }
        }
    }

    /** Every loaded floor in {@code level}. */
    public static synchronized List<ConductorBlockEntity> all(Level level) {
        Set<ConductorBlockEntity> set = BY_LEVEL.get(level);
        return set == null ? List.of() : new ArrayList<>(set);
    }

    /** Loaded floors within {@code radius} of {@code at}, nearest first. */
    public static List<ConductorBlockEntity> near(Level level, Vec3 at, double radius) {
        List<ConductorBlockEntity> out = new ArrayList<>();
        for (ConductorBlockEntity be : all(level)) {
            if (!be.isRemoved() && be.centre().distanceTo(at) <= radius) {
                out.add(be);
            }
        }
        out.sort(Comparator.comparingDouble(be -> be.centre().distanceToSqr(at)));
        return out;
    }

    public static synchronized void reset() {
        BY_LEVEL.clear();
    }

    /** Drops a level's floors when it unloads. */
    public static synchronized void unload(Level level) {
        BY_LEVEL.remove(level);
    }
}
