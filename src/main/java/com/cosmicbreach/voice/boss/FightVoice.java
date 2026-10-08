package com.cosmicbreach.voice.boss;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.guardian.GuardianFights;
import com.cosmicbreach.guardian.GuardianRewards;
import com.cosmicbreach.mount.DriftManta;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * One fight's voice (server): its {@link FightWatch} and {@link VoiceDirector}, who has fought in it, and what it said.
 * Lives while the fight is under way ({@link GuardianFights}). The gear verdict is decided 20 s after the opener's trigger
 * or at the first HP threshold, whichever comes first, and the armor check once the opener is over; both are spoken at
 * the first quiet moment the director allows (nothing playing or waiting, the global gap run), the gear first.
 */
final class FightVoice {
    /** Ticks after fight_start when the gear verdict is decided, if no HP threshold came first. */
    static final int GEAR_AFTER = 400;

    /** A line it started, for the debug status and the checks. */
    static final class Said {
        final String line;
        final String variant;
        final long start;
        final long end;
        final long wordsStart;
        final long wordsEnd;
        final boolean cut;
        final boolean onCue;
        final int quietAtStart;
        /** Set if the boss's quiet window closed while the words still played (a swell or a warning under them). */
        boolean overTelegraph;

        Said(VoiceDirector.Start s, long now, int quiet) {
            this.line = s.line().id();
            this.variant = s.variantKey();
            this.start = now;
            this.end = now + s.variant().lengthTicks();
            this.wordsStart = now + s.variant().speechStartTicks();
            this.wordsEnd = now + s.variant().speechTicks();
            this.cut = s.cut();
            this.onCue = s.onCue();
            this.quietAtStart = quiet;
        }
    }

    /**
     * A trigger the fight raised: its key and event, the tick (a placed one: the tick its first word is due), placed or not,
     * and the line it queued (or empty).
     */
    record Raised(String trigger, String event, long tick, boolean placed, String queued) {}

    final GuardianFights.Fight fight;
    final VoicedBoss boss;
    final ServerLevel level;
    final BossCatalog catalog;
    final FightWatch watch;
    /** Renewed by {@link #fresh} (the debug command), otherwise one for the whole fight. */
    VoiceDirector director;
    final Set<UUID> seen = new LinkedHashSet<>();
    final List<Said> said = new ArrayList<>();
    final List<Raised> raised = new ArrayList<>();
    private final VoiceDirector.Gate gate;
    private final Map<UUID, Map<Integer, Double>> damageByTier = new HashMap<>();
    private final TriggerSchedule schedule = new TriggerSchedule();
    private final int players;
    /** True if the catalog has any line for an attack gap (only the Leviathan's do). */
    private final boolean gapLines;
    private boolean returning;
    private boolean started;
    private long startedAt;
    private @Nullable GearCheck.Verdict gear;
    private boolean gearSaid;
    private boolean armorSaid;

    FightVoice(GuardianFights.Fight fight, VoicedBoss boss, ServerLevel level, Map<String, Long> lairMemory) {
        this.fight = fight;
        this.boss = boss;
        this.level = level;
        this.catalog = BossCatalog.of(boss.voiceBoss());
        Set<Integer> watched = new TreeSet<>(catalog.thresholds());
        watched.removeAll(boss.voicePhaseThresholds());
        this.watch = new FightWatch(watched, catalog.awaySteps());
        this.director = new VoiceDirector(catalog, lairMemory);
        this.players = Math.max(1, boss.voicePlayers());
        this.gapLines = catalog.lines().stream().anyMatch(l -> l.trigger().is(Trigger.ATTACK_GAP));
        this.gate = new VoiceDirector.Gate() {
            @Override
            public boolean mayStart(long now) {
                return boss.voiceMayStart(now);
            }

            @Override
            public int quietTicks(long now) {
                return boss.voiceQuietTicks(now);
            }

            @Override
            public int marginTicks() {
                return boss.voiceMarginTicks();
            }

            @Override
            public String living() {
                return boss.voiceLiving();
            }

            @Override
            public int masks() {
                return boss.voiceMasks();
            }

            @Override
            public long wordsMayStartAt(long now) {
                return BossVoices.wordsMayStartAt(boss.voiceEntity());
            }
        };
    }

    /** The fight's facts now, for a trigger's context. */
    Context base() {
        return Context.of(players, returning, boss.voiceMasks()).withTargetFar(boss.voiceTargetFar());
    }

    void tick() {
        long now = level.getGameTime();
        List<ServerPlayer> inside = boss.voiceFighters();
        for (ServerPlayer p : inside) {
            seen.add(p.getUUID());
        }
        for (TriggerSchedule.Due due : schedule.due(now)) {
            fire(due.trigger(), base().withEvent(due.event()), now, due.cue(), due.latestEnd());
        }
        List<FightWatch.Sample> samples = new ArrayList<>();
        for (UUID id : seen) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p == null || p.level() != level) {
                continue;
            }
            samples.add(new FightWatch.Sample(id, inside.contains(p), p.isAlive(), p.getHealth() / p.getMaxHealth(), boss.voiceFell(p),
                    p.getVehicle() instanceof DriftManta));
        }
        int quiet = boss.voiceQuietTicks(now);
        for (FightWatch.Fired f : watch.tick(now, base(), boss.voiceHealth(), samples, quiet)) {
            if (f.trigger().is(Trigger.ATTACK_GAP) && (!gapLines || !director.pending().isEmpty())) {
                continue; // a gap is a trigger only for a boss with lines for it, and not while something already waits
            }
            fire(f.trigger(), f.context(), now, VoiceDirector.NOT_PLACED);
        }
        if (started && gear == null && now - startedAt >= GEAR_AFTER) {
            gear = verdict(inside);
        }
        if (started && director.quiet(now)) {
            if (gear != null && !gearSaid) {
                gearSaid = true;
                fire(Trigger.parse(Trigger.GEAR + ":" + gear.id()), base(), now, VoiceDirector.NOT_PLACED);
            } else if (!armorSaid) {
                armorSaid = true;
                Set<ArmorCategory.Worn> worn = new LinkedHashSet<>();
                for (ServerPlayer p : inside) {
                    worn.add(ArmorCategory.of(p));
                }
                for (ArmorCategory.Worn w : worn) {
                    fire(Trigger.parse(Trigger.ARMOR + ":" + w.category().id()), base().withArmorSet(w.set()), now, VoiceDirector.NOT_PLACED);
                }
            }
        }
        Said playing = said.isEmpty() ? null : said.get(said.size() - 1);
        if (playing != null && now >= playing.wordsStart && now < playing.wordsEnd && quiet <= 0) {
            playing.overTelegraph = true;
        }
        speak(now, quiet);
    }

    /** The fight has begun for the voice: the gear and armor checks run from here, and the party is known (a returning one or not). */
    void begin(long now) {
        if (started) {
            return;
        }
        started = true;
        startedAt = now;
        returning = boss.voiceWokenByEcho();
        for (ServerPlayer p : boss.voiceFighters()) {
            returning |= GuardianRewards.hasAdvancement(p, boss.voiceKillAdvancement());
        }
    }

    /** The boss raises its opener free, now, after the fight began without one (see {@link BossVoices#raiseOpener}). */
    void raiseOpener() {
        long now = level.getGameTime();
        VoiceLine queued = director.trigger(Trigger.parse(Trigger.FIGHT_START), base(), now, VoiceDirector.NOT_PLACED);
        raised.add(new Raised(Trigger.FIGHT_START, "", now, false, queued == null ? "" : queued.id()));
    }

    /** A trigger the fight raised ({@code cue}: the tick its first word is due if the boss placed it, else NOT_PLACED). */
    void fire(Trigger t, Context c, long now, long cue) {
        fire(t, c, now, cue, VoiceDirector.NO_LIMIT);
    }

    void fire(Trigger t, Context c, long now, long cue, long latestEnd) {
        if (t.is(Trigger.FIGHT_START)) {
            if (started) {
                return;
            }
            begin(now);
            c = base().withEvent(c.event());
        }
        if (t.is(Trigger.HP_THRESHOLD)) {
            watch.markThreshold(t.percent());
            if (started && gear == null) {
                gear = verdict(boss.voiceFighters());
            }
        }
        VoiceLine queued = director.trigger(t, c, now, cue, latestEnd);
        boolean placed = cue != VoiceDirector.NOT_PLACED;
        raised.add(new Raised(t.key(), c.event(), placed ? cue : now, placed, queued == null ? "" : queued.id()));
    }

    /**
     * Debug: the moment of {@code line} itself, raised as the fight would raise it on its own (free: the boss's gate decides),
     * for a check that every line is reached in a running fight.
     */
    void meet(VoiceLine line) {
        if (line.trigger().is(Trigger.FIGHT_START)) {
            // an opener's moment as a free raise: the real one is placed by the boss once a fight, and builds its facts from the fight
            VoiceLine queued = director.trigger(line.trigger(), Context.meeting(line), level.getGameTime(), VoiceDirector.NOT_PLACED);
            raised.add(new Raised(line.trigger().key(), "", level.getGameTime(), false, queued == null ? "" : queued.id()));
            return;
        }
        fire(line.trigger(), Context.meeting(line), level.getGameTime(), VoiceDirector.NOT_PLACED);
    }

    /** Debug: forgets what was said, the global gap and the cooldowns, as a new fight would (the opener stays said). */
    void fresh() {
        director = new VoiceDirector(catalog, new HashMap<>());
    }

    /** Debug: the global gap has run, with what was said still said (lines that take turns on one trigger need it). */
    void calm() {
        director.endGap();
    }

    /** The boss placed {@code t} with its first word due {@code BossVoices.LEAD} ticks from now ({@code event}: a taunt's, or empty). */
    void hook(Trigger t, String event, long now, long latestEnd) {
        fire(t, base().withEvent(event), now, now + BossVoices.LEAD, latestEnd);
    }

    /** The boss places {@code t} for tick {@code cue}: the first word is due then. */
    void placeAt(Trigger t, String event, long cue, long latestEnd) {
        schedule.placed(t, event, cue, latestEnd);
    }

    /** The boss raises {@code t} on tick {@code at}, to play when the rules allow. */
    void raiseAt(Trigger t, String event, long at) {
        schedule.free(t, event, at);
    }

    void hit(ServerPlayer player, DamageSource source, float amount) {
        ItemStack weapon = source.getWeaponItem() == null ? ItemStack.EMPTY : source.getWeaponItem();
        damageByTier.computeIfAbsent(player.getUUID(), k -> new HashMap<>()).merge(GearCheck.weaponTier(weapon), (double) amount, Double::sum);
        WeaponCategory category = WeaponCategory.classify(source);
        if (category != null) {
            FightWatch.Fired f = watch.hit(base(), category, WeaponCategory.itemId(source));
            fire(f.trigger(), f.context(), level.getGameTime(), VoiceDirector.NOT_PLACED);
        }
    }

    void death(ServerPlayer player) {
        int alive = 0;
        for (UUID id : seen) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p != null && p != player && p.level() == level && p.isAlive()) {
                alive++;
            }
        }
        FightWatch.Fired f = watch.death(base(), players >= 2 && alive == 1);
        fire(f.trigger(), f.context(), level.getGameTime(), VoiceDirector.NOT_PLACED);
    }

    void fell() {
        fire(Trigger.parse(Trigger.PLAYER_FELL), base(), level.getGameTime(), VoiceDirector.NOT_PLACED);
    }

    private GearCheck.Verdict verdict(List<ServerPlayer> fighters) {
        List<GearCheck.Verdict> each = new ArrayList<>();
        for (ServerPlayer p : fighters) {
            int tier = -1;
            double most = 0.0;
            for (Map.Entry<Integer, Double> e : damageByTier.getOrDefault(p.getUUID(), Map.of()).entrySet()) {
                if (e.getValue() > most) {
                    most = e.getValue();
                    tier = e.getKey();
                }
            }
            each.add(GearCheck.verdict(GearCheck.score(GearCheck.of(p, tier), catalog.reference())));
        }
        return GearCheck.party(each);
    }

    private void speak(long now, int quiet) {
        VoiceDirector.Start s = director.next(now, gate);
        if (s == null) {
            return;
        }
        BossVoiceNet.say(audience(), catalog.boss(), s.line().id(), s.variantKey(), s.cut());
        said.add(new Said(s, now, quiet));
        CosmicBreach.LOGGER.debug("[cosmicbreach] {} says {} ({}, {} ticks{})", catalog.boss(), s.line().id(), s.variantKey(),
                s.variant().lengthTicks(), s.cut() ? ", cutting in" : "");
    }

    /** Everyone in the arena, and everyone who fought in it and is still within {@value BossVoices#AUDIENCE_RADIUS} blocks. */
    List<ServerPlayer> audience() {
        List<ServerPlayer> out = new ArrayList<>();
        Vec3 home = Vec3.atCenterOf(boss.voiceHome());
        for (ServerPlayer p : level.players()) {
            if (fight.inArena(p.position()) || seen.contains(p.getUUID()) && p.position().distanceTo(home) <= BossVoices.AUDIENCE_RADIUS) {
                out.add(p);
            }
        }
        return out;
    }

    /** True once the opener's deferred checks have run and the director is quiet: a fresh line will play (checks). */
    boolean settled(long now) {
        return director.quiet(now) && (!started || gearSaid && armorSaid);
    }

    /** True if {@code entity} is this fight's boss or one of its parts. */
    boolean owns(Entity entity) {
        return boss.voiceOwns(entity);
    }

    /** The lines said so far, ids only, in order (checks). */
    List<String> saidIds() {
        List<String> out = new ArrayList<>();
        for (Said s : said) {
            out.add(s.line);
        }
        return out;
    }
}
