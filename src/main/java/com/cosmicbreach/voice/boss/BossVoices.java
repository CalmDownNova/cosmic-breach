package com.cosmicbreach.voice.boss;

import com.cosmicbreach.guardian.GuardianFights;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The bosses' voices (1.1, Aetheria 1.1 Design section 7 and Voice Script v1), on the server. Every tick it finds the
 * fights under way ({@link GuardianFights}) whose guardian is a {@link VoicedBoss}, gives each a {@link FightVoice}, feeds
 * it, and sends the lines its director picks to every player in the fight (the client plays them on the Voice channel, at
 * the listener, with a caption). Hits on a boss fire its {@code weapon:} triggers and a fighter's death
 * {@code player_death}; the rest the fight's watch reads off the boss each tick, and the boss fires its own moments
 * through {@link #fire}, {@link #fireAt}, {@link #event} and {@link #eventAt}. Debug:
 * {@code /cosmicbreach debug voice status|say|trigger} ({@link BossVoiceCommands}).
 */
public final class BossVoices {
    /** Players who fought within this many blocks of the lair still hear it after they leave the arena. */
    public static final double AUDIENCE_RADIUS = 160.0;
    /**
     * A moment the boss places with {@link #fire} or {@link #event} has its first word {@value} ticks later: the take starts up
     * to that many ticks before its words (a file leads into them by 1 to 6 ticks), so the boss raises the moment a little
     * ahead. {@link #fireAt} and {@link #eventAt} name the tick of the first word themselves.
     */
    public static final int LEAD = TriggerSchedule.LOOKAHEAD - 2;

    private static final Map<GuardianFights.Fight, FightVoice> FIGHTS = new IdentityHashMap<>();
    /** The tick each boss's last watched sound is over by ({@link BossVoiceSounds}); the entry goes with the boss. */
    private static final Map<Entity, Long> SOUNDS_OVER = new java.util.WeakHashMap<>();
    /** Per lair (boss and home), when each cooldown line last ended: cooldowns outlive a fight. */
    private static final Map<String, Map<String, Long>> MEMORY = new HashMap<>();

    private BossVoices() {
    }

    public static void register(IEventBus modBus, IEventBus game) {
        BossVoiceRegistry.register(modBus);
        modBus.addListener(RegisterPayloadHandlersEvent.class, BossVoiceNet::register);
        game.addListener(ServerTickEvent.Post.class, event -> tick(event.getServer()));
        game.addListener(LivingDamageEvent.Post.class, BossVoices::onDamage);
        game.addListener(LivingDeathEvent.class, BossVoices::onDeath);
        game.addListener(ServerStoppingEvent.class, event -> {
            FIGHTS.clear();
            MEMORY.clear();
            SOUNDS_OVER.clear();
        });
        game.addListener(RegisterCommandsEvent.class, event -> BossVoiceCommands.register(event.getDispatcher()));
    }

    /**
     * {@code boss}'s fight has begun for its voice, but its opener is not said now: the armor and gear checks start from here and
     * the party is known; the opener comes later, raised by {@link #raiseOpener} where the boss has a window for it.
     */
    public static void begin(Entity boss) {
        FightVoice v = voiceOf(boss);
        if (v != null) {
            v.begin(v.level.getGameTime());
        }
    }

    /**
     * {@code boss} raises its opener now, as a free line: it starts in the first quiet window long enough for its words (the boss's
     * own gate), within the boss's wait, and is dropped otherwise; no attack waits for it. Once a fight (a line is said once).
     */
    public static void raiseOpener(Entity boss) {
        FightVoice v = voiceOf(boss);
        if (v != null) {
            v.raiseOpener();
        }
    }

    /** {@code boss} places {@code trigger} (its opener, a phase, its kill) with its first word {@value #LEAD} ticks from now. */
    public static void fire(Entity boss, String trigger) {
        fire(boss, trigger, VoiceDirector.NO_LIMIT);
    }

    /** The same, with the last game tick the words may end on (see {@link #fireAt(Entity, String, long, long)}). */
    public static void fire(Entity boss, String trigger, long latestEnd) {
        FightVoice v = voiceOf(boss);
        if (v != null) {
            v.hook(Trigger.parse(trigger), "", v.level.getGameTime(), latestEnd);
        }
    }

    /** {@code boss} places {@code trigger} with its first word on game tick {@code cue} (a tick it can name ahead). */
    public static void fireAt(Entity boss, String trigger, long cue) {
        fireAt(boss, trigger, cue, VoiceDirector.NO_LIMIT);
    }

    /**
     * The same, with the last game tick the words may end on (an opener before the fight starts, before the first attack's
     * swell): the first word waits for the boss's own sound to be over, and a line that would then end later is skipped.
     */
    public static void fireAt(Entity boss, String trigger, long cue, long latestEnd) {
        FightVoice v = voiceOf(boss);
        if (v != null) {
            v.placeAt(Trigger.parse(trigger), "", cue, latestEnd);
        }
    }

    /** {@code boss} raises {@code trigger} on game tick {@code at} and lets the director play it when the rules allow. */
    public static void raiseAt(Entity boss, String trigger, long at) {
        FightVoice v = voiceOf(boss);
        if (v != null) {
            v.raiseAt(Trigger.parse(trigger), "", at);
        }
    }

    /** {@code boss}'s event happened now: its {@code taunt} lines with {@code on:<event>}, first word {@value #LEAD} ticks on. */
    public static void event(Entity boss, String event) {
        FightVoice v = voiceOf(boss);
        if (v != null) {
            v.hook(Trigger.parse(Trigger.TAUNT), event, v.level.getGameTime(), VoiceDirector.NO_LIMIT);
        }
    }

    /** {@code boss}'s event, placed with its first word on game tick {@code cue}. */
    public static void eventAt(Entity boss, String event, long cue) {
        FightVoice v = voiceOf(boss);
        if (v != null) {
            v.placeAt(Trigger.parse(Trigger.TAUNT), event, cue, VoiceDirector.NO_LIMIT);
        }
    }

    /** {@code boss}'s event, raised on game tick {@code at} and played when the rules allow. */
    public static void raiseEventAt(Entity boss, String event, long at) {
        FightVoice v = voiceOf(boss);
        if (v != null) {
            v.raiseAt(Trigger.parse(Trigger.TAUNT), event, at);
        }
    }

    /** {@code boss} has just started {@code sound}: if it is one of its loud wake or phase sounds, its lines wait for it to be over. */
    public static void soundPlayed(Entity boss, net.minecraft.sounds.SoundEvent sound) {
        int ticks = BossVoiceSounds.clearTicks(sound.getLocation().getPath());
        if (ticks > 0 && boss.level() instanceof ServerLevel level) {
            SOUNDS_OVER.merge(boss, level.getGameTime() + ticks, Math::max);
        }
    }

    /**
     * {@code boss} will start {@code sound} on game tick {@code startTick}: the lines wait for it as for one it has started, from
     * now, so an opener raised before it plays does not run into it (the Leviathan's song in her intro).
     */
    public static void soundWillPlay(Entity boss, net.minecraft.sounds.SoundEvent sound, long startTick) {
        int ticks = BossVoiceSounds.clearTicks(sound.getLocation().getPath());
        if (ticks > 0) {
            SOUNDS_OVER.merge(boss, startTick + ticks, Math::max);
        }
    }

    /** The first tick a line of {@code boss} may sound its first word: its last watched sound's end plus the margin. */
    static long wordsMayStartAt(Entity boss) {
        Long over = SOUNDS_OVER.get(boss);
        return over == null ? Long.MIN_VALUE : over + VoiceDirector.QUIET_MARGIN;
    }

    /** {@code player} fell from a fight's footing and was caught (the Breach's throw back to the rim). */
    public static void playerFell(ServerPlayer player) {
        FightVoice v = fightOf(player);
        if (v != null) {
            v.fell();
        }
    }

    /** True while {@code boss} is saying a line (its idle calls wait). */
    public static boolean speaking(Entity boss) {
        FightVoice v = FIGHTS.get(boss instanceof GuardianFights.Fight f ? f : null);
        return v != null && v.director.speaking(v.level.getGameTime());
    }

    private static @Nullable FightVoice voiceOf(Entity boss) {
        if (!(boss instanceof GuardianFights.Fight fight) || !(boss instanceof VoicedBoss voiced) || !(boss.level() instanceof ServerLevel level)) {
            return null;
        }
        FightVoice v = FIGHTS.get(fight);
        if (v == null && GuardianFights.all().contains(fight)) {
            v = start(fight, voiced, level);
        }
        return v;
    }

    private static FightVoice start(GuardianFights.Fight fight, VoicedBoss boss, ServerLevel level) {
        String lair = boss.voiceBoss() + "@" + boss.voiceHome().toShortString();
        FightVoice v = new FightVoice(fight, boss, level, MEMORY.computeIfAbsent(lair, k -> new HashMap<>()));
        FIGHTS.put(fight, v);
        return v;
    }

    static void tick(MinecraftServer server) {
        List<GuardianFights.Fight> now = GuardianFights.all();
        for (GuardianFights.Fight f : now) {
            if (!FIGHTS.containsKey(f) && f.guardian() instanceof VoicedBoss boss && f.guardian().level() instanceof ServerLevel level) {
                start(f, boss, level);
            }
        }
        Iterator<Map.Entry<GuardianFights.Fight, FightVoice>> it = FIGHTS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<GuardianFights.Fight, FightVoice> e = it.next();
            if (!now.contains(e.getKey()) || e.getKey().guardian().isRemoved()) {
                it.remove();
            } else {
                e.getValue().tick();
            }
        }
    }

    private static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        for (FightVoice v : FIGHTS.values()) {
            if (v.owns(event.getEntity())) {
                v.hit(player, event.getSource(), event.getNewDamage());
                return;
            }
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FightVoice v = fightOf(player);
            if (v != null) {
                v.death(player);
            }
        }
    }

    /** The fight {@code player} is in: its arena holds them, or they fought in it and are near. */
    static @Nullable FightVoice fightOf(ServerPlayer player) {
        for (FightVoice v : FIGHTS.values()) {
            if (v.level != player.level()) {
                continue;
            }
            if (v.fight.inArena(player.position())
                    || v.seen.contains(player.getUUID()) && player.position().distanceTo(Vec3.atCenterOf(v.boss.voiceHome())) <= AUDIENCE_RADIUS) {
                return v;
            }
        }
        return null;
    }

    /** The fights with a voice now (debug, checks). */
    static List<FightVoice> all() {
        return new ArrayList<>(FIGHTS.values());
    }

    /** The lines {@code boss}'s fight has said, in order, or an empty list (checks). */
    public static List<String> said(Entity boss) {
        FightVoice v = FIGHTS.get(boss instanceof GuardianFights.Fight f ? f : null);
        return v == null ? List.of() : v.saidIds();
    }

    /**
     * The triggers {@code boss}'s fight raised, in order, as {@code key[@event]:tick:placed|free:queued line} (checks;
     * a test compares ticks with the boss's own clock).
     */
    public static List<String> raised(Entity boss) {
        FightVoice v = FIGHTS.get(boss instanceof GuardianFights.Fight f ? f : null);
        List<String> out = new ArrayList<>();
        if (v != null) {
            for (FightVoice.Raised r : v.raised) {
                out.add(r.trigger() + (r.event().isEmpty() ? "" : "@" + r.event()) + ":" + r.tick() + ":" + (r.placed() ? "placed" : "free")
                        + ":" + r.queued());
            }
        }
        return out;
    }

    /** True if {@code boss}'s fight is quiet with its opener's checks done, so a new trigger's line plays at once (checks). */
    public static boolean settled(Entity boss) {
        FightVoice v = FIGHTS.get(boss instanceof GuardianFights.Fight f ? f : null);
        return v != null && v.settled(v.level.getGameTime());
    }

    /** Every take {@code boss}'s fight started: {@code line/variant:start:end} plus flags cut, placed, over (checks). */
    public static List<String> takes(Entity boss) {
        FightVoice v = FIGHTS.get(boss instanceof GuardianFights.Fight f ? f : null);
        List<String> out = new ArrayList<>();
        if (v != null) {
            for (FightVoice.Said s : v.said) {
                out.add(s.line + "/" + s.variant + ":" + s.start + ":" + s.end + (s.cut ? ":cut" : "") + (s.onCue ? ":placed" : "")
                        + (s.overTelegraph ? ":over" : ""));
            }
        }
        return out;
    }
}
