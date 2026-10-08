package com.cosmicbreach.guardian.colossus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cosmicbreach.guardian.AttackPicker;
import com.cosmicbreach.voice.boss.BossCatalog;
import com.cosmicbreach.voice.boss.Trigger;
import com.cosmicbreach.voice.boss.VoiceLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * How many of the Colossus's triggered lines actually play (A5 follow-up): simulated fights with the boss's own attack scheduler
 * (the picker, its cooldowns, the attack lengths and the gap of {@link ColossusMoves}, a player who steps in and out of the sweep's
 * band, a Break now and then), and the voice's rule that a line starts only into a lull that covers its words and a margin,
 * within the Colossus's wait. Rules compared on the same fights:
 * <ul>
 *   <li>old: no gate (the A4 behaviour): every line at once;</li>
 *   <li>strict: the first A5 pass: no word over any part of a telegraph, every attack it could ever choose counted, a 6 tick margin;</li>
 *   <li>schedule: the rule now: no word over the readable part of a telegraph, the attacks it could choose now, a 3 tick margin
 *       (production code: {@link ColossusMoves#quietTicks}, {@link ColossusMoves#nextReadableIn}).</li>
 * </ul>
 * Every line that starts is checked against the telegraphs the fight really had.
 */
class ColossusVoiceSimTest {
    static final int FIGHT_TICKS = 12_000;
    static final int TAIL = 400;

    /** One simulated Colossus fight: its attack starts, and each tick's lull under the strict and the schedule rule. */
    static final class Fight {
        final List<long[]> telegraphs = new ArrayList<>();
        final int[] strict = new int[FIGHT_TICKS + TAIL];
        final int[] schedule = new int[FIGHT_TICKS + TAIL];

        Fight(long seed, boolean phase2) {
            Random rnd = new Random(seed);
            AttackPicker<String> picker = new AttackPicker<>();
            picker.holdUntil("refraction", 200);
            String slamName = phase2 ? "double" : "slam";
            long nextActionAt = 16;
            String action = "none";
            long actionStart = 0;
            ColossusMoves.Shape shape = null;
            long breakUntil = Long.MIN_VALUE;
            boolean band = rnd.nextBoolean();
            for (int now = 0; now < FIGHT_TICKS + TAIL; now++) {
                if (rnd.nextInt(120) == 0) {
                    band = !band;
                }
                if (now < breakUntil) {
                    int until = (int) (breakUntil - now) + ColossusMoves.GAP;
                    strict[now] = until;
                    schedule[now] = (int) ColossusMoves.nextReadableIn(until, picker.cooldownLeft(slamName, now), picker.cooldownLeft("sweep", now), band,
                            picker.cooldownLeft("refraction", now), true, 0, false);
                    continue;
                }
                if (breakUntil != Long.MIN_VALUE && now == breakUntil) {
                    breakUntil = Long.MIN_VALUE;
                    nextActionAt = now + ColossusMoves.GAP;
                }
                if (!action.equals("none") && now - actionStart >= shape.minLength()) {
                    action = "none";
                    nextActionAt = now + ColossusMoves.GAP;
                }
                if (action.equals("none") && rnd.nextInt(1500) == 0) {
                    breakUntil = now + 100;
                    continue;
                }
                if (action.equals("none") && now >= nextActionAt) {
                    List<AttackPicker.Option<String>> options = new ArrayList<>();
                    options.add(AttackPicker.Option.weighted("refraction", 0, ColossusMoves.REFRACTION_COOLDOWN).asPriority());
                    options.add(AttackPicker.Option.weighted(slamName, ColossusMoves.SLAM_WEIGHT, ColossusMoves.SLAM_COOLDOWN).asRepeatable());
                    if (band) {
                        options.add(AttackPicker.Option.weighted("sweep", ColossusMoves.SWEEP_WEIGHT, ColossusMoves.SWEEP_COOLDOWN));
                    }
                    String chosen = picker.pick(options, now, rnd::nextDouble);
                    if (chosen != null) {
                        int cooldown = options.stream().filter(o -> o.attack().equals(chosen)).findFirst().get().cooldownTicks();
                        picker.used(chosen, cooldown, now);
                        action = chosen;
                        actionStart = now;
                        shape = switch (chosen) {
                            case "slam" -> ColossusMoves.SLAM_SHAPE;
                            case "double" -> ColossusMoves.DOUBLE_SLAM_SHAPE;
                            case "sweep" -> ColossusMoves.SWEEP_SHAPE;
                            default -> ColossusMoves.REFRACTION_SHAPE;
                        };
                        telegraphs.add(new long[] {now, now + shape.telegraph(), now + shape.readableFrom()});
                    }
                }
                // what the voice sees this tick
                long slamLeft = picker.cooldownLeft(slamName, now);
                long sweepLeft = picker.cooldownLeft("sweep", now);
                long refLeft = picker.cooldownLeft("refraction", now);
                long untilGap = action.equals("none") ? nextActionAt - now : actionStart + shape.minLength() + ColossusMoves.GAP - now;
                long strictReady = Math.min(slamLeft, Math.min(sweepLeft, refLeft));
                long next = ColossusMoves.nextReadableIn(untilGap, slamLeft, sweepLeft, band, refLeft, true, 0, false);
                if (action.equals("none")) {
                    // the first A5 pass: until the next attack may begin (a telegraph is protected from its first tick)
                    strict[now] = (int) Math.max(0, Math.max(untilGap, strictReady));
                    schedule[now] = ColossusMoves.idleQuietTicks(next);
                } else {
                    strict[now] = now - actionStart < shape.telegraph() ? 0 : (int) Math.max(0, Math.max(untilGap, strictReady));
                    schedule[now] = ColossusMoves.quietTicks(shape, now - actionStart, next);
                }
            }
        }

        /** True if {@code from} to {@code to} runs over a telegraph: the whole of it, or only its readable part. */
        boolean over(long from, long to, boolean readableOnly) {
            for (long[] t : telegraphs) {
                long begins = readableOnly ? t[2] : t[0];
                if (from < t[1] && to > begins) {
                    return true;
                }
            }
            return false;
        }
    }

    /** The Colossus's lines a fight's events trigger on their own (not its phases, its opener, its taunt or its kill). */
    static List<VoiceLine> triggered() {
        List<VoiceLine> out = new ArrayList<>();
        for (VoiceLine l : BossCatalog.of("colossus").lines()) {
            if (!l.trigger().is(Trigger.FIGHT_START) && !l.trigger().is(Trigger.BOSS_KILL) && !l.trigger().is(Trigger.HP_THRESHOLD)
                    && !l.trigger().is(Trigger.TAUNT)) {
                out.add(l);
            }
        }
        return out;
    }

    record Result(int triggered, int played, int overWhole, int overReadable) {
        double share() {
            return triggered == 0 ? 0 : (double) played / triggered;
        }

        double overWholeShare() {
            return played == 0 ? 0 : (double) overWhole / played;
        }

        double overReadableShare() {
            return played == 0 ? 0 : (double) overReadable / played;
        }
    }

    /** {@code rule}: 0 old, 1 strict (6 tick margin), 2 schedule (the voice margin); {@code wait}: ticks a line may wait. */
    static Result run(int rule, int wait, boolean phase2) {
        int margin = rule == 1 ? 6 : ColossusMoves.VOICE_MARGIN;
        int triggered = 0;
        int played = 0;
        int overWhole = 0;
        int overReadable = 0;
        List<VoiceLine> lines = triggered();
        for (long seed = 1; seed <= 60; seed++) {
            Fight f = new Fight(seed, phase2);
            for (int t = 300; t < FIGHT_TICKS; t += 41) {
                for (VoiceLine line : lines) {
                    VoiceLine.Variant take = line.variants().get(VoiceLine.ALL);
                    triggered++;
                    int start = -1;
                    for (int u = t; u <= t + wait; u++) {
                        int[] quiet = rule == 1 ? f.strict : f.schedule;
                        if (rule == 0 || quiet[u] >= take.speechTicks() + margin) {
                            start = u;
                            break;
                        }
                    }
                    if (start >= 0) {
                        played++;
                        long from = start + take.speechStartTicks();
                        long to = start + take.speechTicks();
                        overWhole += f.over(from, to, false) ? 1 : 0;
                        overReadable += f.over(from, to, true) ? 1 : 0;
                    }
                }
            }
        }
        return new Result(triggered, played, overWhole, overReadable);
    }

    @Test
    void theScheduleRulePlaysMostTriggeredLinesAndStaysOffTheReadablePartOfEveryTelegraph() {
        StringBuilder report = new StringBuilder();
        Result[] chosen = new Result[2];
        for (int wait : new int[] {80, 160}) {
            report.append("wait ").append(wait).append(" ticks").append((char) 10);
            for (int p = 0; p < 2; p++) {
                boolean phase2 = p == 1;
                Result old = run(0, wait, phase2);
                Result strict = run(1, wait, phase2);
                Result schedule = run(2, wait, phase2);
                if (wait == BossCatalog.of("colossus").waitTicks()) {
                    chosen[p] = schedule;
                }
                report.append(String.format("phase %d: old %.0f%% played, %.0f%% of them over a telegraph; strict rule %.0f%% played (%.1f%% over "
                                + "a telegraph); schedule rule %.0f%% played (%.1f%% over the readable part, %.1f%% over any part)%n", p + 1,
                        old.share() * 100, old.overWholeShare() * 100, strict.share() * 100, strict.overWholeShare() * 100,
                        schedule.share() * 100, schedule.overReadableShare() * 100, schedule.overWholeShare() * 100));
            }
        }
        System.out.println(report);
        assertEquals(7, triggered().size());
        // phase 1 (the first half of a fight): most lines play; phase 2 (the double slam leaves a window 12 ticks shorter): fewer, the
        // long ones wait for a Break; and almost none over the readable part of a telegraph (a player who steps into the sweep's band
        // while a line plays is the exception the schedule cannot know)
        assertTrue(chosen[0].share() >= 0.6, "phase 1: " + chosen[0].share());
        assertTrue(chosen[1].share() >= 0.3, "phase 2: " + chosen[1].share());
        assertTrue((chosen[0].share() + chosen[1].share()) / 2 >= 0.5, "most lines play over a whole fight");
        for (Result r : chosen) {
            assertTrue(r.overReadableShare() <= 0.08, "almost none over the readable part of a telegraph: " + r.overReadableShare());
        }
    }
}
