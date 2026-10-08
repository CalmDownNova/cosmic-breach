package com.cosmicbreach.voice.boss;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

/**
 * The triggers a fight raises on its own, read once a tick from what the voice samples (the boss fires its phases, its
 * opener, its kill and its events itself). Pure.
 *
 * <ul>
 *   <li>{@code hp_threshold:<p>} once each as health falls to {@code p}% (a big hit fires every threshold it passes,
 *       highest first), for the thresholds the boss does not fire itself.</li>
 *   <li>{@code player_low_health} when a fighter inside falls to {@value #LOW} of their health (again after healing past
 *       {@value #LOW_REARM}, or after a death).</li>
 *   <li>{@code player_fell} when the boss says a fighter fell (again once they have been back up {@value #FELL_REARM}
 *       ticks).</li>
 *   <li>{@code player_left}, with {@code all_gone}, as the arena has stood empty of living fighters for each absence its
 *       lines wait for; {@code player_returned} as a fighter comes back inside after more than {@value #RETURN_MIN} ticks
 *       away (dead or outside), with how long they were away. Walking along the edge says nothing.</li>
 *   <li>A {@code taunt} with {@code mounted} once a second while a fighter inside rides.</li>
 *   <li>{@code attack_gap} as the boss's quiet window opens again (its attack ended).</li>
 * </ul>
 */
public final class FightWatch {
    public static final double LOW = 0.25;
    public static final double LOW_REARM = 0.6;
    public static final int FELL_REARM = 40;
    public static final int RETURN_MIN = 20;
    public static final int MOUNT_EVERY = 20;

    /** One fighter this tick: inside the arena, alive, health as a share of their maximum, fallen, riding. */
    public record Sample(UUID id, boolean inside, boolean alive, double health, boolean fell, boolean mounted) {}

    /** A trigger with the moment it fired in. */
    public record Fired(Trigger trigger, Context context) {}

    private static final Trigger LOW_HEALTH = Trigger.parse(Trigger.PLAYER_LOW_HEALTH);
    private static final Trigger FELL = Trigger.parse(Trigger.PLAYER_FELL);
    private static final Trigger LEFT = Trigger.parse(Trigger.PLAYER_LEFT);
    private static final Trigger RETURNED = Trigger.parse(Trigger.PLAYER_RETURNED);
    private static final Trigger TAUNT = Trigger.parse(Trigger.TAUNT);
    private static final Trigger GAP = Trigger.parse(Trigger.ATTACK_GAP);

    private static final class Fighter {
        boolean low;
        boolean fell;
        int steady;
        long lastInside = Long.MIN_VALUE;
    }

    private final NavigableSet<Integer> thresholds = new TreeSet<>(Comparator.reverseOrder());
    private final SortedSet<Integer> awaySteps = new TreeSet<>();
    private final Set<Integer> awayFired = new HashSet<>();
    private final Map<UUID, Fighter> fighters = new HashMap<>();
    private long emptySince = Long.MIN_VALUE;
    private int lastQuiet = Integer.MAX_VALUE;

    /** {@code thresholds}: the percents to watch; {@code awaySteps}: the seconds of absence the player_left lines need. */
    public FightWatch(Collection<Integer> thresholds, Collection<Integer> awaySteps) {
        this.thresholds.addAll(thresholds);
        this.awaySteps.addAll(awaySteps);
    }

    /** One tick: the triggers that fired, in order. {@code quiet}: the boss's quiet window now ({@link VoiceDirector.Gate}). */
    public List<Fired> tick(long now, Context base, double health, List<Sample> samples, int quiet) {
        List<Fired> out = new ArrayList<>();
        while (!thresholds.isEmpty() && health <= thresholds.first() / 100.0) {
            out.add(new Fired(Trigger.parse(Trigger.HP_THRESHOLD + ":" + thresholds.pollFirst()), base));
        }
        boolean anyoneInside = false;
        boolean riding = false;
        for (Sample s : samples) {
            Fighter f = fighters.computeIfAbsent(s.id(), k -> new Fighter());
            if (!s.alive()) {
                f.low = false;
                continue;
            }
            if (!s.inside()) {
                continue;
            }
            anyoneInside = true;
            if (f.lastInside != Long.MIN_VALUE && now - f.lastInside > RETURN_MIN) {
                out.add(new Fired(RETURNED, base.withAway((int) ((now - f.lastInside) / 20), false)));
            }
            f.lastInside = now;
            if (!f.low && s.health() <= LOW) {
                f.low = true;
                out.add(new Fired(LOW_HEALTH, base.withHealth(s.health())));
            } else if (f.low && s.health() >= LOW_REARM) {
                f.low = false;
            }
            if (s.fell()) {
                f.steady = 0;
                if (!f.fell) {
                    f.fell = true;
                    out.add(new Fired(FELL, base));
                }
            } else if (f.fell && ++f.steady >= FELL_REARM) {
                f.fell = false;
            }
            riding |= s.mounted();
        }
        if (anyoneInside) {
            emptySince = Long.MIN_VALUE;
            awayFired.clear();
        } else {
            if (emptySince == Long.MIN_VALUE) {
                emptySince = now;
            }
            long away = (now - emptySince) / 20;
            for (int step : awaySteps) {
                if (away >= step && awayFired.add(step)) {
                    out.add(new Fired(LEFT, base.withAway(step, true)));
                }
            }
        }
        if (riding && now % MOUNT_EVERY == 0) {
            out.add(new Fired(TAUNT, base.withMounted(true)));
        }
        if (quiet > 0 && lastQuiet <= 0) {
            out.add(new Fired(GAP, base));
        }
        lastQuiet = quiet;
        return out;
    }

    /** The boss fired a threshold itself (a phase): it is not watched for any more. */
    public void markThreshold(int percent) {
        thresholds.remove(percent);
    }

    /** A fighter died in the fight; {@code lastAlive}: a group fight now has one fighter standing. */
    public Fired death(Context base, boolean lastAlive) {
        return new Fired(Trigger.parse(Trigger.PLAYER_DEATH), base.withLastAlive(lastAlive));
    }

    /** A hit on the boss with {@code category}, by item {@code item} (its id, or empty). */
    public Fired hit(Context base, WeaponCategory category, String item) {
        return new Fired(Trigger.parse(Trigger.WEAPON + ":" + category.id()), base.withItem(item));
    }
}
