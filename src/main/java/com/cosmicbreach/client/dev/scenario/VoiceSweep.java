package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.voice.boss.BossCatalog;
import com.cosmicbreach.voice.boss.BossVoices;
import com.cosmicbreach.voice.boss.Trigger;
import com.cosmicbreach.voice.boss.VoiceLine;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.entity.Entity;

/**
 * Every line of a boss met in a running fight, one after another (Lane A, A5.3): the voice forgets what it said, the line's own
 * moment is raised the way the fight raises its free triggers ({@code /cosmicbreach debug voice meet}), and the boss's gate
 * and the director's rules decide. The line must start on the client, play through to its end on the Voice channel with its
 * caption ({@link VoiceProbe}), and not once sound over a telegraph. The openers are met as a free raise (the fight parts check
 * the real, placed ones), the kill lines are the fights' own (they have their own checks in the fight parts), and a line that
 * has a take only for some of the masks needs a fight in that state; the takes scenario plays those.
 */
final class VoiceSweep {
    private VoiceSweep() {
    }

    /** True if the sweep meets {@code line}. */
    static boolean meets(VoiceLine line) {
        return !line.trigger().is(Trigger.BOSS_KILL) && line.variants().containsKey(VoiceLine.ALL);
    }

    /** The lines the sweep meets for {@code boss}. */
    static List<VoiceLine> lines(String boss) {
        List<VoiceLine> out = new ArrayList<>();
        for (VoiceLine l : BossCatalog.of(boss).lines()) {
            if (meets(l)) {
                out.add(l);
            }
        }
        return out;
    }

    /**
     * Adds the steps: each line in turn, problems noted in {@code problems}, the lines heard in {@code heard}. Lines that take
     * turns on one trigger (a second death gets the second death line: equal priorities go in the script's order, and a line is
     * said once) are met in that order on one voice that only has its global gap run between them; any other line is met on a
     * voice made fresh. {@code server}: what the boss's voice has done, for a problem's note.
     */
    static void sweep(Steps steps, String boss, List<String> problems, List<String> heard, Supplier<String> server) {
        String previous = "";
        for (VoiceLine line : lines(boss)) {
            String key = line.trigger().key();
            meet(steps, boss, line, !key.equals(previous), problems, heard, server);
            previous = key;
        }
    }

    /**
     * Adds the steps that raise {@code line}'s moment once and follow it to its end: with the voice made fresh first if
     * {@code fresh}, else with only its global gap run. The client is quiet first, the guide too (a line from the fight itself
     * would otherwise be cut by the next, and one that arrives as she speaks is dropped).
     */
    static void meet(Steps steps, String boss, VoiceLine line, boolean fresh, List<String> problems, List<String> heard,
            Supplier<String> server) {
        BossCatalog catalog = BossCatalog.of(boss);
        VoiceProbe probe = new VoiceProbe(boss, line, VoiceLine.ALL, problems, server);
        int take = line.variants().get(VoiceLine.ALL).lengthTicks();
        // the boss's gate may hold a line for its whole wait (the Leviathan 12 s) before it starts
        int startLimit = catalog.waitTicks() + 60;
        // one voice with the guide: a boss line that arrives while she speaks is held, and dropped after 2 s
        steps.waitUntil("the client is quiet before " + probe.name(), 600, () -> !com.cosmicbreach.client.voice.BossVoiceClient.speaking()
                && com.cosmicbreach.client.voice.EchoClient.playing() == null && com.cosmicbreach.client.voice.EchoClient.waiting().isEmpty());
        steps.command("cosmicbreach debug voice " + (fresh ? "fresh" : "calm"));
        steps.run("begin " + probe.name(), probe::begin)
                .command("cosmicbreach debug voice meet " + line.id())
                .waitUntil(probe.name() + " starts or is dropped", startLimit + 10, () -> probe.awaitStart(startLimit))
                .waitUntil(probe.name() + " has played", take + 80, () -> probe.poll(take + 60))
                .run("note " + probe.name(), () -> heard.add(probe.name() + (probe.ran() < 0 ? " not heard" : " " + probe.ran() + "/" + take)))
                .waitTicks(2);
    }

    /** True if none of the takes {@code boss}'s fight started was flagged as words over a telegraph (the voice flags them live). */
    static boolean noneOver(Entity boss) {
        return BossVoices.takes(boss).stream().noneMatch(t -> t.endsWith(":over"));
    }
}
