package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.client.voice.BossVoiceClient;
import com.cosmicbreach.guardian.colossus.CrownArena;
import com.cosmicbreach.guardian.colossus.PrismColossus;
import com.cosmicbreach.guardian.leviathan.LeviathanMoves;
import com.cosmicbreach.guardian.leviathan.ThalassineLeviathan;
import com.cosmicbreach.guardian.unsung.Unsung;
import com.cosmicbreach.guardian.unsung.UnsungMoves;
import com.cosmicbreach.voice.boss.BossCatalog;
import com.cosmicbreach.voice.boss.BossVoices;
import com.cosmicbreach.world.AetheriaWorld;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * The bosses' voices in a real fight (Lane A, A4.8; the lines in A5.3), parts {@code boss-voice-colossus},
 * {@code boss-voice-leviathan} and {@code boss-voice-unsung}: each boss raises its own moments on the ticks the voice script
 * places them (the opener once a fight however often the player crosses the arena's edge, the phase lines, the kill), read from
 * the voice's trigger log ({@link BossVoices#raised}) against the boss's own clock, and the lines that answer them are heard
 * on the client: the opener once, the phase and kill lines, the Colossus and the Leviathan in the lulls and windows the script
 * gives them and never over a telegraph, the Leviathan's song in full before anything of hers. Every other line of the boss is
 * then met in the fight ({@link VoiceSweep}): raised as its own moment, heard to its end on the Voice channel with its caption.
 */
public final class BossVoiceScenario implements Scenario {
    public enum Part { COLOSSUS, LEVIATHAN, UNSUNG }

    private final Part part;
    private final List<String> summary = new ArrayList<>();
    private final List<String> problems = new ArrayList<>();
    private final List<String> heard = new ArrayList<>();
    private final long[] mark = new long[5];

    public BossVoiceScenario(Part part) {
        this.part = part;
    }

    @Override
    public int timeBudgetSeconds() {
        return 1100;
    }

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        steps.command("difficulty normal")
                .command("time set noon")
                .command("gamerule doDaylightCycle false")
                .command("gamerule doWeatherCycle false")
                .run("let the sound engine play, inaudibly", () -> VoiceProbe.audible(true));
        switch (part) {
            case COLOSSUS -> colossus(steps, mc);
            case LEVIATHAN -> leviathan(steps, mc);
            case UNSUNG -> unsung(steps, mc);
        }
        steps.run("mute again", () -> VoiceProbe.audible(false));
        steps.log("summary", () -> String.join("; ", summary));
        steps.log("heard", () -> heard.size() + " lines met: " + String.join(", ", heard));
        steps.log("problems", () -> problems.size() + " problems: " + String.join("; ", problems));
        steps.check("every line met was heard to its end with its caption, on the Voice channel, as its catalog says", problems::isEmpty);
        steps.check("no line was cut short but by the rules, all on the Voice channel", () ->
                BossVoiceClient.interruptions() == 0 && BossVoiceClient.history().stream()
                        .allMatch(h -> h.source() == net.minecraft.sounds.SoundSource.VOICE));
    }

    /** Tough enough to stand through every attack while the voice is watched. */
    private static void sturdy(Steps steps) {
        steps.command("gamemode survival")
                .command("effect give @s minecraft:resistance 100000 4 true")
                .command("effect give @s minecraft:regeneration 100000 2 true")
                .command("effect give @s minecraft:saturation 100000 0 true");
    }

    /** {@code boss}'s raised triggers that start with {@code key + ":"}. */
    private static List<String> raised(Entity boss, String key) {
        List<String> out = new ArrayList<>();
        for (String r : BossVoices.raised(boss)) {
            if (r.startsWith(key + ":") || r.startsWith(key + "@")) {
                out.add(r);
            }
        }
        return out;
    }

    /** The tick of the one raised {@code key} that the boss placed, or -1 if it did not place exactly one (free ones are the checks' own). */
    private static long placedOnce(Entity boss, String key) {
        List<String> r = new ArrayList<>(raised(boss, key));
        r.removeIf(e -> !e.contains(":placed:"));
        if (r.size() != 1) {
            return -1;
        }
        String[] f = r.get(0).split(":", -1);
        return Long.parseLong(f[f.length - 3]);
    }

    /** The tick of the one raised {@code key} (placed or not), or -1 if it was not raised exactly once. */
    private static long raisedOnce(Entity boss, String key) {
        List<String> r = raised(boss, key);
        if (r.size() != 1) {
            return -1;
        }
        String[] f = r.get(0).split(":", -1);
        return Long.parseLong(f[f.length - 3]);
    }

    /** The takes {@code boss}'s fight started, as line ids. */
    private static List<String> lines(Entity boss) {
        List<String> out = new ArrayList<>();
        for (String t : BossVoices.takes(boss)) {
            out.add(t.substring(0, t.indexOf('/')));
        }
        return out;
    }

    /** How many takes of {@code line} {@code boss}'s fight started on game tick {@code tick} or later. */
    private static int saidSince(Entity boss, String line, long tick) {
        int n = 0;
        for (String t : BossVoices.takes(boss)) {
            String[] f = t.split(":");
            if (f[0].startsWith(line + "/") && Long.parseLong(f[1]) >= tick) {
                n++;
            }
        }
        return n;
    }

    /** What the voice of {@code boss}'s fight has done, for the note on a line that never started. */
    static String note(Entity boss) {
        List<String> raised = BossVoices.raised(boss);
        return " [server: raised " + raised.subList(Math.max(0, raised.size() - 3), raised.size()) + ", takes " + BossVoices.takes(boss) + "]";
    }

    /** How many openers (open_solo, open_group, open_again) {@code boss}'s fight started on game tick {@code tick} or later. */
    private static int openersSince(Entity boss, long tick) {
        return saidSince(boss, "open_solo", tick) + saidSince(boss, "open_group", tick) + saidSince(boss, "open_again", tick);
    }

    /** The end tick of the words of the first take of {@code line} started on {@code tick} or later, or -1. */
    private static long wordsEndSince(Entity boss, String bossId, String line, long tick) {
        for (String t : BossVoices.takes(boss)) {
            String[] f = t.split(":");
            String[] key = f[0].split("/", 2);
            if (key[0].equals(line) && Long.parseLong(f[1]) >= tick) {
                return Long.parseLong(f[1]) + BossCatalog.of(bossId).line(key[0]).variant(key[1]).speechTicks();
            }
        }
        return -1;
    }

    /** The end tick of the words of the first opener started on {@code tick} or later, or Long.MAX_VALUE. */
    private static long wordsEndOfOpenerSince(Entity boss, String bossId, long tick) {
        for (String id : new String[] {"open_solo", "open_group", "open_again"}) {
            long end = wordsEndSince(boss, bossId, id, tick);
            if (end > 0) {
                return end;
            }
        }
        return Long.MAX_VALUE;
    }

    /** One line of {@code openers} said, once. */
    private static boolean oneOpener(Entity boss, String... openers) {
        List<String> said = lines(boss);
        int n = 0;
        for (String o : openers) {
            n += Collections.frequency(said, o);
        }
        return n == 1;
    }

    /** True if {@code line}'s words in the fight's takes sound inside game ticks {@code from} (inclusive) to {@code to} (exclusive). */
    private static boolean wordsIn(Entity boss, String bossId, String line, long from, long to) {
        for (String t : BossVoices.takes(boss)) {
            String[] f = t.split(":");
            String[] key = f[0].split("/", 2);
            if (!key[0].equals(line)) {
                continue;
            }
            var take = BossCatalog.of(bossId).line(key[0]).variant(key[1]);
            long start = Long.parseLong(f[1]);
            if (start + take.speechStartTicks() < to && start + take.speechTicks() > from) {
                return true;
            }
        }
        return false;
    }

    /** True if any take of {@code boss}'s fight has words sounding in game ticks {@code from} to {@code to}. */
    private static boolean anyWordsIn(Entity boss, String bossId, long from, long to) {
        for (String id : lines(boss)) {
            if (wordsIn(boss, bossId, id, from, to)) {
                return true;
            }
        }
        return false;
    }

    /** The end tick of the words of {@code line}'s take in the fight, or -1. */
    private static long wordsEnd(Entity boss, String bossId, String line) {
        for (String t : BossVoices.takes(boss)) {
            String[] f = t.split(":");
            String[] key = f[0].split("/", 2);
            if (key[0].equals(line)) {
                return Long.parseLong(f[1]) + BossCatalog.of(bossId).line(key[0]).variant(key[1]).speechTicks();
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ the Colossus

    /** True if the Colossus's fight has started a take of {@code line} on game tick {@code since} or later. */
    private static boolean colossusSays(String line, long since) {
        return ServerQuery.ask(p -> saidSince(ColossusScenario.colossusOf(p), line, since) > 0);
    }

    private static boolean colossusQuiet() {
        return ServerQuery.ask(p -> VoiceSweep.noneOver(ColossusScenario.colossusOf(p)));
    }

    private void colossus(Steps steps, Minecraft mc) {
        steps.command("cosmicbreach debug goto reach")
                .waitUntil("in Aetheria", 600, () -> mc.level != null && AetheriaWorld.is(mc.level))
                .waitUntil("the island is drawn", 1600, LeviathanScenario.settled(mc, 1200))
                .command("cosmicbreach weather clear")
                .command("cosmicbreach debug lair colossus")
                .waitUntil("the lair is built and its Colossus stands", 200, () -> ServerQuery.ask(p -> ColossusScenario.colossusOrNull(p) != null));
        sturdy(steps);
        steps.run("onto the crown", () -> {
                    CrownArena a = ColossusScenario.arena();
                    ColossusScenario.tp(mc, a.x() + 0.5, a.floorY(), a.z() + 8.5, a.x(), a.floorY() + 3, a.z());
                })
                .waitUntil("it wakes", 80, () -> ServerQuery.ask(p -> ColossusScenario.colossusOf(p).state() == PrismColossus.State.INTRO))
                .run("mark the wake", () -> mark[0] = ServerQuery.ask(p -> ColossusScenario.colossusOf(p).stateStart()));
        for (int k = 0; k < 3; k++) {
            int lap = k;
            steps.run("off the crown " + (lap + 1), () -> {
                        CrownArena a = ColossusScenario.arena();
                        ColossusScenario.tp(mc, a.x() + 40.5, a.floorY() + 20, a.z() + 0.5, a.x(), a.floorY(), a.z());
                    })
                    .waitTicks(30)
                    .run("back onto the crown " + (lap + 1), () -> {
                        CrownArena a = ColossusScenario.arena();
                        ColossusScenario.tp(mc, a.x() + 0.5, a.floorY(), a.z() + 8.5, a.x(), a.floorY() + 3, a.z());
                    })
                    .waitTicks(30);
        }
        steps.waitUntil("the fight is on", 300, () -> ServerQuery.ask(p -> ColossusScenario.colossusOf(p).state() == PrismColossus.State.FIGHT))
                .log("raised", () -> ServerQuery.ask(p -> String.join(" ", BossVoices.raised(ColossusScenario.colossusOf(p)))))
                .check("the opener's moment was raised once, placed at intro tick 120, edge crossings or not", () -> ServerQuery.ask(p ->
                        placedOnce(ColossusScenario.colossusOf(p), "fight_start") == mark[0] + 120))
                .check("one opener was said, its words over before the fight began", () -> ServerQuery.ask(p -> {
                    PrismColossus c = ColossusScenario.colossusOf(p);
                    return oneOpener(c, "open_solo", "open_group", "open_again") && wordsEnd(c, "colossus", lines(c).get(0)) <= c.fightStart();
                }))
                // in the live fight, with its attacks running: a line that needs a lull is held for its wait (4 s) and then dropped, and
                // never sounds over a telegraph; the Colossus's lulls are short, so a line of two seconds and more finds none
                .waitTicks(100)
                .run("mark the live fight", () -> mark[4] = mc.level.getGameTime())
                .command("cosmicbreach debug voice fresh")
                .command("cosmicbreach debug voice meet breathe")
                .waitTicks(120)
                .check("in the live fight no word sounded over a telegraph", BossVoiceScenario::colossusQuiet)
                .run("note the live fight", () -> summary.add("colossus live: breathe "
                        + (colossusSays("breathe", mark[4]) ? "said in a lull" : "held and dropped, no lull long enough")))
                // a Break is a lull of a hundred ticks and more: the same line is said in it
                .waitUntil("the client is quiet", 400, () -> !BossVoiceClient.speaking())
                .command("cosmicbreach debug voice fresh")
                .run("mark the Break", () -> mark[4] = mc.level.getGameTime())
                .command("cosmicbreach debug colossus break")
                .waitUntil("it is broken", 20, () -> ServerQuery.ask(p -> {
                    PrismColossus c = ColossusScenario.colossusOf(p);
                    return c.broken(c.level().getGameTime());
                }))
                .command("cosmicbreach debug voice meet breathe")
                .waitUntil("the line is said in the Break", 100, () -> colossusSays("breathe", mark[4]))
                .waitTicks(90)
                .check("a Break gave it its lull, and no word sounded over a telegraph", BossVoiceScenario::colossusQuiet)
                // the Fracture: its line waits for the Fracture's sound, then for a lull; attacks are held so there is one
                .waitUntil("the client is quiet again", 400, () -> !BossVoiceClient.speaking())
                .command("cosmicbreach debug colossus hold 1000000")
                .command("cosmicbreach debug voice fresh")
                .command("cosmicbreach debug colossus phase2")
                .waitUntil("the Fracture", 40, () -> ServerQuery.ask(p -> ColossusScenario.colossusOf(p).state() == PrismColossus.State.FRACTURE))
                .run("mark the Fracture", () -> mark[3] = ServerQuery.ask(p -> ColossusScenario.colossusOf(p).stateStart()))
                .waitUntil("phase 2", 120, () -> ServerQuery.ask(p -> ColossusScenario.colossusOf(p).state() == PrismColossus.State.FIGHT))
                .waitUntil("the Fracture line is said", 300, () -> colossusSays("fracture", mark[3]))
                .waitTicks(100)
                .check("its first word came after the Fracture's sound, over no telegraph", () -> ServerQuery.ask(p -> {
                    PrismColossus c = ColossusScenario.colossusOf(p);
                    return !wordsIn(c, "colossus", "fracture", mark[3], mark[3] + 48) && VoiceSweep.noneOver(c);
                }));
        // every other line, met in the fight with its attacks held (a lull that does not end)
        VoiceSweep.sweep(steps, "colossus", problems, heard,
                () -> ServerQuery.ask(p -> note(ColossusScenario.colossusOf(p))));
        steps.waitUntil("the client is quiet", 400, () -> !BossVoiceClient.speaking())
                .command("cosmicbreach debug voice fresh")
                .command("cosmicbreach debug colossus shatter")
                .waitUntil("it shatters", 40, () -> ServerQuery.ask(p -> ColossusScenario.colossusOf(p).state() == PrismColossus.State.SHATTERED))
                .run("mark the Shatter", () -> mark[1] = ServerQuery.ask(p -> ColossusScenario.colossusOf(p).stateStart()))
                .waitTicks(20)
                .check("the first Shatter raised its threshold, placed at Shatter tick 10", () -> ServerQuery.ask(p ->
                        placedOnce(ColossusScenario.colossusOf(p), "hp_threshold:0") == mark[1] + 10))
                .waitUntil("the Shatter line is said", 300, () -> colossusSays("shatter", mark[1]))
                .check("its first word came after the Shatter's sound", () -> ServerQuery.ask(p ->
                        !wordsIn(ColossusScenario.colossusOf(p), "colossus", "shatter", mark[1], mark[1] + 58)))
                // the shards re-merge a countdown after the first one dies; the voice's global gap (25 s) has run by then
                .waitUntil("240 ticks into the Shatter", 400, () -> ServerQuery.ask(p -> {
                    PrismColossus c = ColossusScenario.colossusOf(p);
                    return c.level().getGameTime() - c.stateStart() >= 240;
                }))
                .command("kill @e[type=cosmicbreach:prism_shard,limit=1,sort=random]")
                .waitUntil("it re-forms", 600, () -> ServerQuery.ask(p -> ColossusScenario.colossusOf(p).state() == PrismColossus.State.REFORMING))
                .waitUntil("the reform line is said", 200, () -> colossusSays("reform", mark[1]))
                .waitUntil("it fights again", 200, () -> ServerQuery.ask(p -> ColossusScenario.colossusOf(p).state() == PrismColossus.State.FIGHT))
                .check("its taunt on the re-forming was said once and over no telegraph", () -> ServerQuery.ask(p -> {
                    PrismColossus c = ColossusScenario.colossusOf(p);
                    return saidSince(c, "reform", mark[1]) == 1 && VoiceSweep.noneOver(c);
                }))
                // the kill: the Shatter again, and all three shards cut down
                .waitUntil("the client is quiet", 400, () -> !BossVoiceClient.speaking())
                .command("cosmicbreach debug colossus shatter")
                .waitUntil("it shatters again", 40, () -> ServerQuery.ask(p -> ColossusScenario.colossusOf(p).state() == PrismColossus.State.SHATTERED))
                .waitTicks(10)
                .command("kill @e[type=cosmicbreach:prism_shard]")
                .waitUntil("it is dying", 60, () -> ServerQuery.ask(p -> ColossusScenario.colossusOf(p).state() == PrismColossus.State.DYING))
                .run("mark the death", () -> mark[2] = ServerQuery.ask(p -> ColossusScenario.colossusOf(p).stateStart()))
                .waitTicks(30)
                .check("the kill was raised at death tick 5, placed", () -> ServerQuery.ask(p -> {
                    PrismColossus c = ColossusScenario.colossusOf(p);
                    return placedOnce(c, "boss_kill") == mark[2] + 5;
                }))
                .check("the kill line was said and its words end by death tick 60, when the rewards and the guide follow", () -> ServerQuery.ask(p -> {
                    PrismColossus c = ColossusScenario.colossusOf(p);
                    long end = wordsEnd(c, "colossus", "kill");
                    return end > 0 && end <= mark[2] + 60;
                }))
                .run("note", () -> summary.add("colossus: " + ServerQuery.ask(p -> String.join(" ", BossVoices.raised(ColossusScenario.colossusOf(p)))
                        + " TAKES " + BossVoices.takes(ColossusScenario.colossusOf(p)))));
    }

    // ------------------------------------------------------------------ the Leviathan

    private void leviathan(Steps steps, Minecraft mc) {
        steps.command("cosmicbreach debug goto drift")
                .waitUntil("in Aetheria", 600, () -> mc.level != null && AetheriaWorld.is(mc.level))
                .waitUntil("the asteroid is drawn", 1600, LeviathanScenario.settled(mc, 1200))
                .command("cosmicbreach weather clear")
                .command("cosmicbreach debug lair leviathan")
                .waitUntil("the Rift is built and its Leviathan sleeps", 400, () -> LeviathanScenario.layout() != null
                        && ServerQuery.ask(p -> LeviathanScenario.lev(p) != null && LeviathanScenario.lev(p).state() == ThalassineLeviathan.State.DORMANT));
        sturdy(steps);
        steps.run("onto a platform", () -> {
                    var l = LeviathanScenario.layout();
                    var pl = l.platforms().get(0);
                    ColossusScenario.tp(mc, pl.x() + 0.5, pl.top() + 1, pl.z() + 0.5, l.centre().x, l.centre().y, l.centre().z);
                })
                .waitTicks(10)
                .command("cosmicbreach debug leviathan awaken")
                .waitUntil("it rises", 60, () -> LeviathanScenario.is(ThalassineLeviathan.State.INTRO))
                .run("mark the wake", () -> mark[0] = LeviathanScenario.ask(ThalassineLeviathan::stateStart))
                .waitUntil("the fight is on", 300, () -> LeviathanScenario.is(ThalassineLeviathan.State.FIGHT))
                .check("no opener was raised in the intro: her song fills it, the opener waits for her first Moorage", () -> LeviathanScenario.ask(le ->
                        raised(le, "fight_start").isEmpty()))
                .check("her intro song played once, in full, and no word of hers sounded over it", () -> LeviathanScenario.ask(le ->
                        le.introSongs() == 1 && !anyWordsIn(le, "leviathan", mark[0] + LeviathanMoves.INTRO_SONG, mark[0] + LeviathanMoves.introSongOver())));
        // in the live fight, a line of hers is said in a quiet window between attacks and never over a swell
        VoiceSweep.meet(steps, "leviathan", BossCatalog.of("leviathan").line("crab"), true, problems, heard,
                () -> LeviathanScenario.ask(BossVoiceScenario::note));
        steps.check("in the live fight no word sounded over a telegraph", () -> LeviathanScenario.ask(VoiceSweep::noneOver))
                // every other line, met with her attacks held
                .command("cosmicbreach debug leviathan hold 100000");
        VoiceSweep.sweep(steps, "leviathan", problems, heard, () -> LeviathanScenario.ask(BossVoiceScenario::note));
        steps.waitUntil("the client is quiet", 400, () -> !BossVoiceClient.speaking())
                .command("cosmicbreach debug voice fresh")
                .command("cosmicbreach debug leviathan health " + (int) (LeviathanMoves.BASE_HEALTH * 0.49))
                .waitUntil("it swims into its Moorage", 60, () -> LeviathanScenario.is(ThalassineLeviathan.State.MOORAGE))
                .run("mark the Moorage", () -> mark[1] = LeviathanScenario.ask(ThalassineLeviathan::stateStart))
                .waitTicks(40)
                .check("the first Moorage raised her opener once, free, as it began", () -> LeviathanScenario.ask(le -> {
                    List<String> r = new ArrayList<>(raised(le, "fight_start"));
                    r.removeIf(e -> !e.contains(":free:") || Long.parseLong(e.split(":", -1)[1]) < mark[1]);
                    return r.size() == 1 && Long.parseLong(r.get(0).split(":", -1)[1]) == mark[1];
                }))
                .waitUntil("it coils", 600, () -> LeviathanScenario.ask(ThalassineLeviathan::holdsMoorage))
                .run("note the swim in", () -> summary.add("leviathan swim in " + (LeviathanScenario.ask(ThalassineLeviathan::moorStart) - mark[1]) + " ticks"))
                .waitUntil("her opening line was said", 300, () -> LeviathanScenario.ask(le -> openersSince(le, mark[1]) >= 1))
                .waitUntil("its Moorage line was said", 400, () -> LeviathanScenario.ask(le -> saidSince(le, "moor_first", mark[1]) == 1))
                .check("her opener was said once, in the swim in or the first pause of the coil, over no ripple, and her Moorage line after it", () ->
                        LeviathanScenario.ask(le -> openersSince(le, mark[1]) == 1 && VoiceSweep.noneOver(le)
                                && wordsEndSince(le, "leviathan", "moor_first", mark[1]) > wordsEndOfOpenerSince(le, "leviathan", mark[1])))
                .waitTicks(120)
                .command("cosmicbreach debug leviathan kill")
                .waitUntil("it sinks", 200, () -> LeviathanScenario.is(ThalassineLeviathan.State.DYING))
                .run("mark the sink", () -> mark[2] = LeviathanScenario.ask(ThalassineLeviathan::stateStart))
                .waitTicks(30)
                .check("the kill was raised at death tick 20, placed", () -> LeviathanScenario.ask(le -> placedOnce(le, "boss_kill") == mark[2] + 20))
                .waitUntil("the kill line is said", 100, () -> LeviathanScenario.ask(le -> saidSince(le, "kill", mark[2]) == 1))
                .check("none of her words sounded over a telegraph", () -> LeviathanScenario.ask(VoiceSweep::noneOver))
                .run("note", () -> summary.add("leviathan: " + LeviathanScenario.ask(le -> String.join(" ", BossVoices.raised(le)) + " TAKES " + BossVoices.takes(le))));
    }

    // ------------------------------------------------------------------ the Unsung

    private void unsung(Steps steps, Minecraft mc) {
        steps.command("cosmicbreach debug goto deep")
                .waitUntil("in Aetheria", 600, () -> mc.level != null && AetheriaWorld.is(mc.level))
                .waitUntil("the Deep is drawn", 1600, UnsungScenario.settled(mc, 1200))
                .command("cosmicbreach weather clear")
                .command("cosmicbreach debug lair unsung")
                .waitUntil("the Nave is built and its choir sleeps", 200, () -> UnsungScenario.layout() != null
                        && ServerQuery.ask(p -> UnsungScenario.choirOrNull(p) != null && UnsungScenario.choirOrNull(p).masks().size() == 3));
        sturdy(steps);
        steps.run("onto the choir floor", () -> {
                    var c = UnsungScenario.arena().centre();
                    var at = UnsungScenario.fromLocal(8.0, 0.0, 0.0);
                    ColossusScenario.tp(mc, at.x, at.y, at.z, c.x, c.y + 2, c.z);
                })
                .waitTicks(10)
                .command("cosmicbreach debug unsung awaken")
                .waitUntil("the masks lift", 100, () -> UnsungScenario.ask(u -> u.state() == Unsung.State.INTRO))
                .run("mark the first line", () -> mark[0] = UnsungScenario.get(Unsung::fightStart))
                .waitUntil("the fight is on", 400, () -> UnsungScenario.ask(u -> u.state() == Unsung.State.FIGHT))
                .check("its opener's moment was raised once, placed a beat after the Alto's lift", () -> UnsungScenario.get(u ->
                        placedOnce(u, "fight_start") == mark[0] - (long) UnsungMoves.INTRO_BEATS * UnsungMoves.BEAT + UnsungMoves.BEAT))
                .check("one opener was said, in three voices, before the fight began", () -> UnsungScenario.get(u ->
                        oneOpener(u, "open_solo", "open_group", "open_again")))
                .command("cosmicbreach debug unsung hold 100000");
        // every line she has a take for with all three masks singing, met on a beat with her attacks held
        VoiceSweep.sweep(steps, "unsung", problems, heard, () -> UnsungScenario.get(BossVoiceScenario::note));
        steps.waitUntil("the client is quiet", 400, () -> !BossVoiceClient.speaking())
                .command("cosmicbreach debug voice fresh")
                .run("mark", () -> mark[1] = mc.level.getGameTime())
                .command("cosmicbreach debug unsung shatter alto")
                .waitUntil("a mask broke", 40, () -> UnsungScenario.ask(u -> u.brokenBits() != 0))
                .waitTicks(UnsungMoves.BEAT + 4)
                .check("the first break raised 67, from a beat after the crack, within two beats", () -> UnsungScenario.get(u -> {
                    long t = raisedOnce(u, "hp_threshold:67");
                    return t >= mark[1] && t <= mark[1] + 2L * UnsungMoves.BEAT;
                }))
                .check("the survivors are the Tenor and the Bass", () -> UnsungScenario.get(u -> u.voiceLiving().equals("23")))
                .waitUntil("the first break line was said, in the survivors' take", 400, () -> UnsungScenario.get(u ->
                        BossVoices.takes(u).stream().anyMatch(t -> t.startsWith("one_gone/23:"))))
                .waitUntil("the client is quiet", 400, () -> !BossVoiceClient.speaking())
                .command("cosmicbreach debug unsung shatter tenor")
                .waitUntil("a second mask broke", 40, () -> UnsungScenario.ask(u -> Integer.bitCount(u.brokenBits()) == 2))
                .waitTicks(UnsungMoves.BEAT + 4)
                .check("the second break raised 33", () -> UnsungScenario.get(u -> raisedOnce(u, "hp_threshold:33") > 0))
                .check("the Bass sings alone", () -> UnsungScenario.get(u -> u.voiceLiving().equals("3")))
                .waitUntil("the Bass line was said, in her take", 400, () -> UnsungScenario.get(u ->
                        BossVoices.takes(u).stream().anyMatch(t -> t.startsWith("alone/3:"))))
                .waitUntil("the client is quiet", 400, () -> !BossVoiceClient.speaking())
                .command("cosmicbreach debug unsung shatter bass")
                .waitUntil("the last mask broke", 40, () -> UnsungScenario.ask(u -> u.state() == Unsung.State.DYING))
                .run("mark the death", () -> mark[2] = UnsungScenario.get(Unsung::stateStart))
                .waitTicks(50)
                .check("the kill was raised at death tick 36, placed", () -> UnsungScenario.get(u -> placedOnce(u, "boss_kill") == mark[2] + 36))
                .waitUntil("the kill line is said", 100, () -> UnsungScenario.get(u -> lines(u).contains("finished")))
                .check("the break lines and the kill line were said, each once since the first break", () -> UnsungScenario.get(u ->
                        saidSince(u, "one_gone", mark[1]) == 1 && saidSince(u, "alone", mark[1]) == 1 && saidSince(u, "finished", mark[1]) == 1))
                .run("note", () -> {
                    String raised = UnsungScenario.get(u -> String.join(" ", BossVoices.raised(u)) + " TAKES " + BossVoices.takes(u));
                    summary.add("unsung: " + raised);
                });
    }
}
