package com.cosmicbreach.guardian;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The guardian fights under way, for the rules that watch the whole arena rather than the guardian (blocks placed
 * during a fight crumble; see {@link ArenaRules}). A guardian adds itself when it wakes and removes itself when the
 * fight ends; entries of removed entities drop out on their own. Server thread.
 */
public final class GuardianFights {
    /** A fight: the guardian, and its arena as a horizontal radius round a centre. */
    public interface Fight {
        Entity guardian();

        /** True if {@code point} is inside this fight's arena. */
        boolean inArena(Vec3 point);

        /** A block placed in the arena at {@code pos} during the fight: it crumbles soon. */
        void blockPlaced(BlockPos pos);
    }

    private static final Set<Fight> FIGHTS = Collections.newSetFromMap(new WeakHashMap<>());

    private GuardianFights() {
    }

    public static synchronized void start(Fight fight) {
        FIGHTS.add(fight);
    }

    public static synchronized void end(Fight fight) {
        FIGHTS.remove(fight);
    }

    /** The fight whose arena holds {@code point} in {@code level}, or null. */
    public static synchronized @Nullable Fight at(Level level, Vec3 point) {
        for (Fight fight : new ArrayList<>(FIGHTS)) {
            Entity g = fight.guardian();
            if (g.isRemoved()) {
                FIGHTS.remove(fight);
                continue;
            }
            if (g.level() == level && fight.inArena(point)) {
                return fight;
            }
        }
        return null;
    }

    public static synchronized List<Fight> all() {
        return new ArrayList<>(FIGHTS);
    }
}
