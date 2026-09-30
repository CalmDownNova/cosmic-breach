package com.cosmicbreach.client.dev.scenario;

import static com.cosmicbreach.client.dev.scenario.CryptKit.player;
import static com.cosmicbreach.client.dev.scenario.CryptKit.server;

import com.cosmicbreach.client.dev.DevCamera;
import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.progression.Attunements;
import com.cosmicbreach.structure.choir.ChoirDifficulty;
import com.cosmicbreach.structure.choir.ChoirJudge;
import com.cosmicbreach.structure.choir.ChoirPhrase;
import com.cosmicbreach.structure.choir.ChoirRules;
import com.cosmicbreach.structure.choir.ChoirSession;
import com.cosmicbreach.structure.choir.ConductorBlockEntity;
import com.cosmicbreach.structure.crypt.CryptCommands;
import com.cosmicbreach.structure.vault.VaultBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Choir Floor (W6, GDD 6.3) played through by a bot with real input only (it turns and walks the player; it
 * never moves them by command once the floor is awake): a room built by {@code debug place choir}, woken by walking
 * onto the ring; round 1 with a deliberate wrong pad (the Discord: damage and push measured, the phrase replays);
 * round 2 first with a phrase nobody steps into (no Discord: the floor falls idle), then answered past the window
 * (misses: the third rests the floor, measured), then four ticks late with Relaxed on; round 3 with the ring turned;
 * the vault opened. Screenshots of the sleeping room, the call mid-phrase, the ring after the turn, the solved floor.
 *
 * <p>Built to hold on a busy machine. The bot times its steps by the integrated server's tick and the Vesper beat,
 * never by client frames, and the Conductor logs every step with the tick it landed on. After each stretch the
 * scenario checks every verdict against where its step actually landed ({@link #judged}) and fails only when the
 * judge got one wrong. A step a hiccup moves is judged like any other; when that leaves a check unshown (no step at
 * +4 under Relaxed, no step past the window), the phrase is played again rather than waiting out a timeout.
 */
public final class ChoirScenario implements Scenario {
    static final long SEED = 7L;
    /** How late the bot steps to show a miss: past the strict window and a little latency grace. */
    static final int LATE_MISS = ChoirRules.WINDOW + 3;
    /** How late the bot steps to show the Relaxed window: past the strict window, inside the Relaxed one. */
    static final int LATE_RELAXED = ChoirRules.WINDOW + 1;
    /** Answers the bot gets to show a check before a busy machine counts as a failure. */
    static final int TRIES = 4;

    @Override
    public int timeBudgetSeconds() {
        return 420;
    }

    private static final class State {
        BlockPos conductor;
        Vec3 centre;
        Vec3 door;
        long xpBefore;
        Vec3 atDiscord;
        long restStart;
        long restEnd;
        long nextCallAfterRest;
        /** Step log entries already checked. */
        int logMark;
        /** Offsets of the strict-window steps that counted, all rounds. */
        final List<Long> strictHits = new ArrayList<>();
        final List<String> results = new ArrayList<>();
        DevCamera camera;
        float litWhileSung;
    }

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        State s = new State();
        Bot bot = new Bot(mc);
        steps.command("gamerule sendCommandFeedback false")
                .command("gamerule doDaylightCycle false")
                .command("time set 6000")
                .command("weather clear")
                .command("difficulty normal")
                .command("gamemode creative")
                .look(0, 0)
                .waitTicks(5)
                .run("build a Choir Floor ahead", () -> {
                    long t0 = System.nanoTime();
                    s.conductor = server(srv -> CryptCommands.placeChoirAhead(player(srv), SEED, ChoirDifficulty.CRYPT));
                    s.centre = Vec3.atBottomCenterOf(s.conductor);
                    s.door = s.centre.add(0, 0, -8.0);
                    bot.conductor = s.conductor;
                    s.results.add("choir room built in " + (System.nanoTime() - t0) / 1_000_000 + " ms, Conductor at " + s.conductor.toShortString());
                })
                .waitUntil("the Conductor reached this client", 100, () -> bot.be() != null)
                .run("inside the door, off the floor", () -> CryptKit.tp(mc, s.door, s.centre.add(0, 1, 0)))
                .command("gamemode survival")
                .run("full health", CryptKit::heal)
                .waitUntil("the room is drawn", 600, CryptKit.settled(mc, 400))
                .check("the floor sleeps until someone steps onto it", () -> server(srv -> floor(srv, s).phase()) == ChoirSession.Phase.IDLE)
                .run("a view from above the door", () -> camera(mc, s, s.centre.add(-6.5, 5.2, -7.5)))
                .screenshot("choir_room_asleep")
                .run("back to the player", () -> uncamera(s))
                .run("remember the XP", () -> s.xpBefore = server(srv -> Attunements.of(player(srv)).totalXp()))
                .hold(mc.options.keyUp)
                .waitUntil("walking onto the ring wakes the floor", 200, () -> server(srv -> floor(srv, s).phase()) == ChoirSession.Phase.CALL)
                .release(mc.options.keyUp)
                .run("result", () -> {
                    ConductorBlockEntity f = server(srv -> floor(srv, s));
                    s.results.add("woke: solo song " + describe(f));
                })
                .waitUntil("the call's second note sounds", 400, () -> {
                    bot.tick();
                    ConductorBlockEntity be = bot.be();
                    ChoirPhrase p = be == null ? null : be.phrase();
                    // the pad's light is drawn on the client's clock, so this one wait reads it
                    boolean now = p != null && be.phase() == ChoirSession.Phase.CALL
                            && mc.level.getGameTime() >= be.callStart() + p.onsets()[1] + 1;
                    if (now) {
                        s.litWhileSung = com.cosmicbreach.client.crypt.ConductorRenderer.litness(be, p.melody(1), mc.level.getGameTime(),
                                be.phase());
                        CryptKit.releaseAll(mc);
                    }
                    return now;
                })
                .run("the camera over the ring", () -> camera(mc, s, s.centre.add(-5.5, 5.4, -6.5)))
                .screenshot("choir_call_midphrase")
                .run("back to the player", () -> uncamera(s))
                .check("the pad being sung was lit on this client", () -> s.litWhileSung > 0.3f)
                .run("result", () -> s.results.add(String.format(Locale.ROOT, "the second note's pad lit %.2f as it sounded",
                        s.litWhileSung)));
        roundOne(steps, mc, s, bot);
        roundTwo(steps, mc, s, bot);
        roundThree(steps, mc, s, bot);
        steps.log("results", () -> String.join(System.lineSeparator(), s.results));
    }

    // ------------------------------------------------------------------ the rounds

    private void roundOne(Steps steps, Minecraft mc, State s, Bot bot) {
        int[] before = {0};
        steps.run("the bot will step a wrong pad for the second note", () -> {
                    bot.mode = Bot.Mode.WRONG;
                    bot.wrongNote = 1;
                    before[0] = server(srv -> floor(srv, s).discords());
                    CryptKit.heal();
                })
                .waitUntil("round 1: the wrong pad brings a Discord", 1500, () -> {
                    bot.tick();
                    boolean discord = server(srv -> floor(srv, s).discords()) > before[0];
                    if (discord && s.atDiscord == null) {
                        s.atDiscord = mc.player.position();
                        CryptKit.releaseAll(mc);
                    }
                    return discord;
                })
                .waitTicks(6)
                .run("measure the Discord", () -> {
                    float health = CryptKit.health();
                    Vec3 now = mc.player.position();
                    double pushed = horizontal(now, s.centre) - horizontal(s.atDiscord, s.centre);
                    ConductorBlockEntity f = server(srv -> floor(srv, s));
                    // a hiccup can make a beat go by before the planned wrong pad: the Discord is the same, so name it right
                    boolean wrong = logged(s, ChoirJudge.Verdict.WRONG);
                    s.results.add(String.format(Locale.ROOT,
                            "Discord (%s): %.1f damage, pushed %.2f blocks away from the Conductor in 6 ticks, mistakes %d, replaying: %s",
                            wrong ? "wrong pad" : "a beat let go by", 20.0f - health, pushed, f.mistakes(), f.replaying()));
                    if (Math.abs(20.0f - health - ChoirRules.DISCORD_DAMAGE) > 0.01f) {
                        throw new Steps.Failure("a Discord should cost 4 health, cost " + (20.0f - health));
                    }
                    if (!f.replaying() || f.phase() != ChoirSession.Phase.CALL) {
                        throw new Steps.Failure("the phrase should replay after a Discord");
                    }
                    CryptKit.heal();
                })
                .waitUntil("round 1 answered", 3000, () -> {
                    bot.tick();
                    healIfLow(mc);
                    return server(srv -> floor(srv, s).round()) >= 1;
                })
                .run("the judge against where each step landed, round 1", () -> s.strictHits.addAll(judged(s, "round 1 (strict)").strictHits));
    }

    private void roundTwo(Steps steps, Minecraft mc, State s, Bot bot) {
        int[] before = {0, 0, 0};
        int[] tries = {1, 1};
        List<Long> lateMisses = new ArrayList<>();
        steps.run("the bot stands on the floor and steps on nothing", () -> {
                    bot.mode = Bot.Mode.IDLE;
                    CryptKit.heal();
                    before[0] = server(srv -> floor(srv, s).discords());
                    before[1] = server(srv -> floor(srv, s).idles());
                })
                .waitUntil("a whole phrase nobody steps into: the floor falls idle", 1500, () -> {
                    bot.tick();
                    return server(srv -> floor(srv, s).idles()) > before[1];
                })
                .run("result", () -> {
                    ConductorBlockEntity f = server(srv -> floor(srv, s));
                    float health = CryptKit.health();
                    s.results.add(String.format(Locale.ROOT,
                            "a silent phrase: %d Discords, health %.1f, the floor idle and waiting for a step: %s", f.discords() - before[0],
                            health, f.hushed()));
                    if (f.discords() != before[0] || health < 20.0f || !f.hushed()) {
                        throw new Steps.Failure("nobody stepped, so nobody is playing: no Discord, no damage, the floor idle");
                    }
                })
                .waitTicks(40)
                .check("it stays idle while nobody steps", () -> server(srv -> floor(srv, s).phase()) == ChoirSession.Phase.IDLE)
                .run("the bot wakes the floor with a step, then steps every note past the window", () -> {
                    bot.mode = Bot.Mode.PLAY;
                    bot.late = LATE_MISS;
                    before[2] = server(srv -> floor(srv, s).rests());
                })
                .waitUntil("round 2: steps past the window are misses, and the third rests the floor", 4000, () -> {
                    bot.tick();
                    healIfLow(mc);
                    int rests = server(srv -> floor(srv, s).rests());
                    if (rests <= before[2]) {
                        return false;
                    }
                    Judged j = judged(s, "round 2, " + bot.late + " ticks late (strict)");
                    s.strictHits.addAll(j.strictHits);
                    lateMisses.addAll(j.lateMisses);
                    if (!lateMisses.isEmpty()) {
                        return true;
                    }
                    // every mistake was a beat let go by, none a step landing late: go again, later still
                    if (++tries[0] > 3) {
                        throw new Steps.Failure("three rests and not one step landed past the window to be judged");
                    }
                    bot.late = LATE_MISS + j.maxGrace;
                    s.results.add("no step landed past the window: three more misses, the bot " + bot.late + " ticks late");
                    before[2] = rests;
                    return false;
                })
                .run("measure the rest", () -> {
                    ConductorBlockEntity f = server(srv -> floor(srv, s));
                    s.restStart = f.lastRestAt();
                    s.restEnd = f.restUntil();
                    s.results.add("steps landing past the window at " + lateMisses + " were misses; the floor rests " + (s.restEnd - s.restStart)
                            + " ticks (phase " + f.phase() + ")");
                    if (s.restEnd - s.restStart != ChoirRules.REST_TICKS || f.phase() != ChoirSession.Phase.REST) {
                        throw new Steps.Failure("the rest should be 200 ticks");
                    }
                    CryptKit.heal();
                })
                .command("cosmicbreach debug choir relaxed on")
                .waitUntil("the Relaxed window reached the floor", 60, () -> bot.be() != null && bot.be().window() == ChoirRules.RELAXED_WINDOW)
                .run("the bot answers four ticks late", () -> {
                    bot.mode = Bot.Mode.PLAY;
                    bot.late = LATE_RELAXED;
                })
                .waitUntil("after the rest the Conductor calls again: never a lockout", 600, () -> {
                    bot.tick();
                    if (server(srv -> floor(srv, s).phase()) == ChoirSession.Phase.CALL) {
                        s.nextCallAfterRest = Bot.serverTick(mc);
                        return true;
                    }
                    return false;
                })
                .run("result", () -> s.results.add("the call came back " + (s.nextCallAfterRest - s.restEnd) + " ticks after the rest ended"))
                .waitUntil("round 2 answered late, with a step only the Relaxed window counts", 3600, () -> {
                    bot.tick();
                    healIfLow(mc);
                    int[] at = server(srv -> {
                        ConductorBlockEntity f = floor(srv, s);
                        return new int[] {f.round(), f.phase().ordinal()};
                    });
                    boolean answered = at[0] >= 2;
                    boolean rested = at[1] == ChoirSession.Phase.REST.ordinal();
                    if (!answered && !rested) {
                        return false;
                    }
                    Judged j = judged(s, "round 2 with Relaxed, answer " + tries[1]);
                    if (answered && !j.relaxedOnly.isEmpty()) {
                        return true;
                    }
                    // a hiccup moved every step inside the strict window, or past even the Relaxed one: the phrase again
                    if (++tries[1] > TRIES) {
                        throw new Steps.Failure(TRIES + " Relaxed answers and not one step landed where only the Relaxed window counts it");
                    }
                    bot.late = LATE_RELAXED + j.maxGrace;
                    s.results.add((answered ? "every step of that answer landed inside the strict window"
                            : "three misses past even the Relaxed window") + ": round 2 again at once, the bot " + bot.late + " ticks late");
                    server(srv -> {
                        floor(srv, s).debugSetRound(srv.getLevel(player(srv).level().dimension()), 1);
                        return null;
                    });
                    return false;
                })
                .command("cosmicbreach debug choir relaxed off")
                .run("on the beat again", () -> bot.late = 0);
    }

    private void roundThree(Steps steps, Minecraft mc, State s, Bot bot) {
        steps.waitUntil("round 3: the call, then the ring turns a slot", 2000, () -> {
                    bot.tick();
                    ConductorBlockEntity be = bot.be();
                    return be != null && be.round() == 2 && be.rotation() >= 1 && Bot.serverTick(mc) >= be.rotatedAt() + ChoirRules.BEAT;
                })
                .run("the camera over the ring", () -> camera(mc, s, s.centre.add(6.5, 5.4, -6.5)))
                .screenshot("choir_round3_turned")
                .run("back to the player", () -> uncamera(s))
                .run("result", () -> {
                    ConductorBlockEntity f = server(srv -> floor(srv, s));
                    s.results.add("round 3: " + describe(f));
                })
                .waitUntil("round 3 answered: the floor is solved", 3000, () -> {
                    bot.tick();
                    healIfLow(mc);
                    return server(srv -> floor(srv, s).phase()) == ChoirSession.Phase.SOLVED;
                })
                .run("stop", () -> CryptKit.releaseAll(mc))
                .run("the judge against where each step landed, round 3", () -> {
                    s.strictHits.addAll(judged(s, "round 3 (strict)").strictHits);
                    if (s.strictHits.stream().noneMatch(o -> o >= -1 && o <= ChoirRules.WINDOW)) {
                        throw new Steps.Failure("no strict step counted between -1 and +3: " + s.strictHits);
                    }
                })
                .run("result", () -> {
                    ConductorBlockEntity f = server(srv -> floor(srv, s));
                    s.results.add("solved after " + f.discords() + " Discords and " + f.rests() + " rest(s)");
                    long gained = server(srv -> Attunements.of(player(srv)).totalXp()) - s.xpBefore;
                    s.results.add("Puzzle Solved XP: " + gained);
                    if (gained < 1) {
                        throw new Steps.Failure("solving should pay XP");
                    }
                })
                .check("the vault unsealed", () -> server(srv -> srv.getLevel(player(srv).level().dimension())
                        .getBlockEntity(floor(srv, s).vault()) instanceof VaultBlockEntity v && v.ready()))
                .waitTicks(10)
                .run("the camera over the solved ring", () -> camera(mc, s, s.centre.add(-6.5, 5.4, 6.5)))
                .screenshot("choir_solved")
                .run("back to the player", () -> uncamera(s))
                .run("to the vault", () -> {
                    BlockPos v = server(srv -> floor(srv, s).vault());
                    Vec3 front = Vec3.atBottomCenterOf(v).add(0, 0, -1.6);
                    CryptKit.tp(mc, front, Vec3.atCenterOf(v));
                })
                .waitTicks(10)
                .run("aim at the vault", () -> CryptKit.aim(mc, Vec3.atCenterOf(server(srv -> floor(srv, s).vault()))))
                .press(mc.options.keyUse)
                .waitUntil("the vault gave its loot", 60, () -> server(srv -> {
                    ServerLevel level = srv.getLevel(player(srv).level().dimension());
                    BlockPos v = floor(srv, s).vault();
                    return level.getEntitiesOfClass(ItemEntity.class, new AABB(v).inflate(4)).size() + player(srv).getInventory().items.stream()
                            .mapToInt(net.minecraft.world.item.ItemStack::getCount).sum();
                }) > 0)
                .run("result", () -> s.results.add("vault opened: " + server(srv -> {
                    ServerLevel level = srv.getLevel(player(srv).level().dimension());
                    BlockPos v = floor(srv, s).vault();
                    List<String> items = new ArrayList<>();
                    level.getEntitiesOfClass(ItemEntity.class, new AABB(v).inflate(4)).forEach(e -> items.add(e.getItem().getCount() + " "
                            + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(e.getItem().getItem()).getPath()));
                    return String.join(", ", items);
                })));
    }

    // ------------------------------------------------------------------ the judge, checked

    /** What the step log showed for one stretch of play. */
    private static final class Judged {
        /** Offsets of the steps that counted in the strict window. */
        final List<Long> strictHits = new ArrayList<>();
        /** Offsets of the steps that counted in the Relaxed window. */
        final List<Long> relaxedHits = new ArrayList<>();
        /** Of those, the late ones the strict window (with the step's grace) would not have counted. */
        final List<Long> relaxedOnly = new ArrayList<>();
        /** Offsets of the steps on the note's pad landing after its window closed: misses. */
        final List<Long> lateMisses = new ArrayList<>();
        /** Offsets of the steps on the note's pad landing before its window opened (ignored). */
        final List<Long> early = new ArrayList<>();
        int wrong;
        int steps;
        int maxGrace;
    }

    /**
     * Checks every step the Conductor logged since the last check against the offset it actually landed at, on the
     * server's tick: on the note's pad inside the window (with the stepper's grace) it counts; on the note's pad
     * outside it, or on the pad of the note before or after, it is ignored; any other pad is wrong; any step after a
     * beat went by with nobody stepping is late (ignored, and the Discord follows). A busy machine may land a step
     * somewhere other than where the bot meant it; that is fine. Fails only if a verdict disagrees with where its step
     * landed. Adds what it saw to the results.
     */
    private static Judged judged(State s, String what) {
        List<long[]> log = server(srv -> floor(srv, s).stepLog());
        Judged j = new Judged();
        for (int i = s.logMark; i < log.size(); i++) {
            long[] e = log.get(i);
            long offset = e[0] - e[4];
            ChoirJudge.Verdict v = ChoirJudge.Verdict.values()[(int) e[5]];
            int w = (int) e[6];
            int g = (int) e[7];
            boolean right = e[8] == 1;
            boolean near = e[9] == 1;
            boolean silent = e[10] == 1;
            boolean hit = v == ChoirJudge.Verdict.HIT || v == ChoirJudge.Verdict.DONE;
            boolean agrees;
            if (silent) {
                agrees = v == ChoirJudge.Verdict.IGNORED;
            } else if (right && ChoirJudge.within(offset, w, g)) {
                agrees = hit;
            } else if (right || near) {
                agrees = v == ChoirJudge.Verdict.IGNORED;
            } else {
                agrees = v == ChoirJudge.Verdict.WRONG;
            }
            if (!agrees) {
                throw new Steps.Failure(what + ": the judge said " + v + " for a step on " + (right ? "the note's pad"
                        : near ? "a neighbouring note's pad" : "another pad") + " landing " + offset + " ticks from its beat (window "
                        + w + ", grace " + g + (silent ? ", after a beat went by unanswered" : "") + ")");
            }
            j.steps++;
            j.maxGrace = Math.max(j.maxGrace, g);
            if (hit) {
                if (w > ChoirRules.WINDOW) {
                    j.relaxedHits.add(offset);
                    if (offset > ChoirRules.WINDOW + g) {
                        j.relaxedOnly.add(offset);
                    }
                } else {
                    j.strictHits.add(offset);
                }
            } else if (v == ChoirJudge.Verdict.WRONG) {
                j.wrong++;
            } else if (right && offset > w + g) {
                j.lateMisses.add(offset);
            } else if (right && offset < -w) {
                j.early.add(offset);
            }
        }
        s.logMark = log.size();
        s.results.add(what + ": " + j.steps + " steps, every verdict matching where its step landed; counted at " + j.strictHits
                + " (strict) " + j.relaxedHits + " (Relaxed, " + j.relaxedOnly + " only Relaxed counts), past the window " + j.lateMisses
                + " (misses), early " + j.early + " (ignored), wrong pads " + j.wrong + (j.maxGrace > 0 ? ", grace up to " + j.maxGrace : ""));
        return j;
    }

    /** True if the Conductor logged a step judged {@code v} since the last check. */
    private static boolean logged(State s, ChoirJudge.Verdict v) {
        List<long[]> log = server(srv -> floor(srv, s).stepLog());
        for (int i = s.logMark; i < log.size(); i++) {
            if (log.get(i)[5] == v.ordinal()) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ helpers

    private static ConductorBlockEntity floor(MinecraftServer srv, State s) {
        ServerLevel level = srv.getLevel(player(srv).level().dimension());
        if (level.getBlockEntity(s.conductor) instanceof ConductorBlockEntity be) {
            return be;
        }
        throw new Steps.Failure("no Conductor at " + s.conductor);
    }

    private static String describe(ConductorBlockEntity f) {
        return "round " + (f.round() + 1) + ", phrase " + f.phrase() + ", window " + f.window() + ", ring turned " + f.rotation();
    }

    private static void healIfLow(Minecraft mc) {
        if (mc.player != null && mc.player.getHealth() < 10) {
            CryptKit.heal();
        }
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    private static void camera(Minecraft mc, State s, Vec3 eye) {
        s.camera = DevCamera.create(mc.level);
        s.camera.place(eye, s.centre.add(0, 0.2, 0));
        s.camera.use();
        mc.options.hideGui = true;
    }

    private static void uncamera(State s) {
        Minecraft.getInstance().options.hideGui = false;
        if (s.camera != null) {
            s.camera.remove();
            s.camera = null;
        }
    }

    // ------------------------------------------------------------------ the bot

    /**
     * Plays the floor through the keyboard and the mouse: waits on the clear disc round the Conductor, walks out onto
     * each note's pad so its feet cross the pad's edge on the beat (a hop for a note sounded twice), and back. It keeps
     * time by the integrated server's tick, the clock the Conductor judges by, never the client's, so a busy machine
     * that stalls one thread or the other does not shift its steps; it learns its own lag from the server's accepted
     * steps as it goes.
     */
    static final class Bot {
        enum Mode { PLAY, WRONG, IDLE }

        static final boolean TRACE = Boolean.getBoolean("cosmicbreach.choirbot.trace");

        final Minecraft mc;
        BlockPos conductor;
        Mode mode = Mode.PLAY;
        int wrongNote = -1;
        boolean wrongDone;
        int late;
        private long answerKey = Long.MIN_VALUE;
        private int next;
        private int prevPad = -1;
        private double bias;
        private long lastHit = Long.MIN_VALUE;
        private int lastOn = -1;

        Bot(Minecraft mc) {
            this.mc = mc;
        }

        ConductorBlockEntity be() {
            return mc.level != null && conductor != null && mc.level.getBlockEntity(conductor) instanceof ConductorBlockEntity c ? c : null;
        }

        /** The integrated server's tick (the Conductor's clock), read without waiting on the server thread. */
        static long serverTick(Minecraft mc) {
            MinecraftServer srv = mc.getSingleplayerServer();
            ServerLevel level = srv == null || mc.level == null ? null : srv.getLevel(mc.level.dimension());
            return level != null ? level.getGameTime() : mc.level.getGameTime();
        }

        void tick() {
            ConductorBlockEntity be = be();
            LocalPlayer pl = mc.player;
            if (be == null || pl == null) {
                return;
            }
            Vec3 c = be.centre();
            long now = serverTick(mc);
            int rot = be.rotation();
            ChoirPhrase p = be.phrase();
            ChoirSession.Phase ph = be.phase();
            if (be.answerStart() != answerKey) {
                answerKey = be.answerStart();
                next = 0;
            }
            learn(be, p);
            boolean countIn = ph == ChoirSession.Phase.CALL && now >= be.answerStart() - (long) ChoirRules.COUNT_IN_BEATS * ChoirRules.BEAT;
            boolean answering = ph == ChoirSession.Phase.ANSWER || countIn;
            int under = pl.onGround() ? ChoirRules.padAt(pl.getX() - c.x, pl.getZ() - c.z, rot) : -1;
            if (answering && p != null && next < p.size() && under >= 0 && under != prevPad && under == padFor(p, next)) {
                if (mode == Mode.WRONG && next == wrongNote) {
                    wrongDone = true;
                }
                next++;
            }
            prevPad = under;
            if (under >= 0) {
                lastOn = under;
            }
            if (ph == ChoirSession.Phase.IDLE && mode != Mode.IDLE) {
                // a floor fallen idle wakes for a step on a pad: onto the one in front of the waiting spot
                goTo(c.add(0, 0, -2.9), c.add(0, 0, -4));
                return;
            }
            if (p == null || !answering || mode == Mode.IDLE || next >= p.size()) {
                goTo(c.add(0, 0, -1.25), c.add(0, 0, -3));
                return;
            }
            int pad = padFor(p, next);
            long target = be.answerStart() + p.onsets()[next] + late;
            long left = target - now;
            if (under == pad) {
                // a note sounded twice: hop, landing on the beat
                stop();
                CryptKit.set(mc.options.keyJump, left <= 13 + Math.round(bias) && left > 0 && pl.onGround());
                return;
            }
            CryptKit.set(mc.options.keyJump, false);
            if (pl.getHealth() < 9) {
                CryptKit.heal();
            }
            Vec3 stage;
            Vec3 entry;
            double edge;
            double a0 = ChoirRules.slotAngle(ChoirRules.slotOf(pad, rot));
            double rNow = Math.hypot(pl.getX() - c.x, pl.getZ() - c.z);
            int from = under >= 0 ? under : rNow >= ChoirRules.R_IN - 0.25 ? lastOn : -1;
            if (from >= 0 && ChoirRules.ringDistance(from, pad) == 1) {
                // a neighbour: straight across the seam, from just short of it
                double aCur = ChoirRules.slotAngle(ChoirRules.slotOf(from, rot));
                double dir = Math.signum(Math.IEEEremainder(a0 - aCur, 2 * Math.PI));
                double seam = aCur + dir * Math.toRadians(ChoirRules.SLOT_DEGREES / 2);
                double rr = 2.4;
                stage = c.add(Math.cos(seam - dir * Math.asin(0.55 / rr)) * rr, 0, Math.sin(seam - dir * Math.asin(0.55 / rr)) * rr);
                entry = c.add(Math.cos(seam + dir * Math.asin(0.6 / rr)) * rr, 0, Math.sin(seam + dir * Math.asin(0.6 / rr)) * rr);
                double across = Math.abs(Math.IEEEremainder(Math.atan2(pl.getZ() - c.z, pl.getX() - c.x) - seam, 2 * Math.PI))
                        * Math.hypot(pl.getX() - c.x, pl.getZ() - c.z);
                edge = across + ChoirRules.DIVIDER / 2;
            } else {
                double ap = Math.atan2(pl.getZ() - c.z, pl.getX() - c.x);
                double off = Math.IEEEremainder(ap - a0, 2 * Math.PI);
                double lean = Math.max(-Math.toRadians(13), Math.min(Math.toRadians(13), off));
                // a pair next: come in on the side of the pad the next note lies
                if (next + 1 < p.size() && p.onsets()[next + 1] - p.onsets()[next] <= ChoirRules.EIGHTH
                        && ChoirRules.ringDistance(pad, p.melody(next + 1)) == 1) {
                    double aNext = ChoirRules.slotAngle(ChoirRules.slotOf(p.melody(next + 1), rot));
                    lean = Math.signum(Math.IEEEremainder(aNext - a0, 2 * Math.PI)) * Math.toRadians(12);
                }
                double ae = a0 + lean;
                stage = c.add(Math.cos(ae) * 1.45, 0, Math.sin(ae) * 1.45);
                entry = c.add(Math.cos(ae) * 2.5, 0, Math.sin(ae) * 2.5);
                if (Math.abs(off) > Math.toRadians(125)) {
                    // across the statue: round it on the nearer side first
                    double mid = ap - Math.signum(off) * Math.toRadians(70);
                    stage = c.add(Math.cos(mid) * 1.3, 0, Math.sin(mid) * 1.3);
                }
                edge = ChoirRules.R_IN - Math.hypot(pl.getX() - c.x, pl.getZ() - c.z);
            }
            double dStage = Math.hypot(pl.getX() - stage.x, pl.getZ() - stage.z);
            double lead = ticksToCover(Math.max(0.05, edge)) + bias;
            boolean go = left <= Math.round(lead) + Math.max(0, dStage - 0.25) / 0.22;
            Vec3 goal = go && dStage < 0.9 ? entry : stage;
            double dGoal = Math.hypot(pl.getX() - goal.x, pl.getZ() - goal.z);
            if (TRACE) {
                com.cosmicbreach.CosmicBreach.LOGGER.info(String.format(Locale.ROOT,
                        "[choirbot] t=%d drift=%d note=%d pad=%d left=%d under=%d r=%.2f ang=%.0f stage=%.2f edge=%.2f lead=%.1f go=%s goal=%s bias=%.2f ph=%s",
                        now, now - mc.level.getGameTime(), next, pad, left, under, Math.hypot(pl.getX() - c.x, pl.getZ() - c.z),
                        Math.toDegrees(Math.atan2(pl.getZ() - c.z, pl.getX() - c.x)), dStage, edge, lead, go,
                        goal == entry ? "entry" : "stage", bias, ph));
            }
            if (!go && dStage < 0.12) {
                stop();
                CryptKit.face(mc, entry, 10f);
                return;
            }
            CryptKit.face(mc, goal, 10f);
            CryptKit.set(mc.options.keyUp, dGoal > 0.06);
            boolean hurry = (dStage + 0.8) / Math.max(1.0, left) > 0.19;
            CryptKit.set(mc.options.keySprint, hurry);
            if (!hurry) {
                pl.setSprinting(false);
            }
        }

        /** Ticks to walk {@code blocks} from a standstill (measured: 0.13, 0.32, 0.56, 0.81, then 0.25 a tick). */
        static double ticksToCover(double blocks) {
            double[] cum = {0.13, 0.32, 0.56, 0.81};
            for (int i = 0; i < cum.length; i++) {
                if (blocks <= cum[i]) {
                    return i + 1;
                }
            }
            return cum.length + (blocks - cum[cum.length - 1]) / 0.25;
        }

        /** The pad to step for note {@code i}: the note's melody pad, or on the planned mistake, one that isn't near. */
        int padFor(ChoirPhrase p, int i) {
            if (mode == Mode.WRONG && !wrongDone && i == wrongNote) {
                int right = p.melody(i);
                for (int d : new int[] {1, -1, 2, -2, 3, -3, 4}) {
                    int cand = Math.floorMod(right + d, ChoirRules.PADS);
                    boolean near = p.sounds(i, cand) || i > 0 && p.sounds(i - 1, cand) || i + 1 < p.size() && p.sounds(i + 1, cand);
                    if (!near) {
                        return cand;
                    }
                }
            }
            return p.melody(i);
        }

        /** Updates the bot's lag from the steps the server has accepted. */
        private void learn(ConductorBlockEntity be, ChoirPhrase p) {
            long[] h = be.hits();
            if (p == null || h.length < 2 || h[h.length - 1] == lastHit) {
                return;
            }
            lastHit = h[h.length - 1];
            if (next > 0 && next - 1 < p.size()) {
                long target = be.answerStart() + p.onsets()[next - 1] + late;
                long offset = lastHit - target;
                if (Math.abs(offset) <= 6) {
                    bias = 0.6 * bias + 0.4 * offset;
                }
            }
        }

        private void goTo(Vec3 spot, Vec3 look) {
            CryptKit.set(mc.options.keyJump, false);
            double d = Math.hypot(mc.player.getX() - spot.x, mc.player.getZ() - spot.z);
            if (d < 0.2) {
                stop();
                CryptKit.face(mc, look, 10f);
                return;
            }
            CryptKit.face(mc, spot, 10f);
            CryptKit.set(mc.options.keyUp, true);
            CryptKit.set(mc.options.keySprint, false);
            mc.player.setSprinting(false);
        }

        private void stop() {
            CryptKit.set(mc.options.keyUp, false);
            CryptKit.set(mc.options.keySprint, false);
            mc.player.setSprinting(false);
        }
    }

}
