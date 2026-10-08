package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.client.voice.EchoClient;
import com.cosmicbreach.voice.boss.BossCatalog;
import com.cosmicbreach.voice.boss.VoiceLine;
import java.util.ArrayList;
import java.util.List;

/**
 * Every take of one boss's catalog through the client, as the game plays it (Lane A, A5.3): the server's debug command sends
 * the take to the player ({@code /cosmicbreach debug voice say}), and the client must play it on the Voice channel to its end
 * (the sound engine reports it active, and it ends when the catalog says it does, give or take a few ticks), show its caption
 * while it plays (the words in quotes as the language file gives them, in pieces for a relayed sentence) and not be cut short
 * ({@link VoiceProbe}). Parts {@code boss-voice-takes-colossus}, {@code -leviathan}, {@code -unsung} and {@code -heliarch}. A
 * problem with one take is noted and the rest still play; the last step fails the run with them all. A few captions are
 * photographed (the first of each boss, a relayed sentence in its masks' colours) for a look.
 */
public final class BossVoiceTakesScenario implements Scenario {
    public enum Part {
        COLOSSUS("colossus"), LEVIATHAN("leviathan"), UNSUNG("unsung"), HELIARCH("heliarch");

        final String boss;

        Part(String boss) {
            this.boss = boss;
        }
    }

    private final Part part;
    private final List<String> problems = new ArrayList<>();
    private final List<String> heard = new ArrayList<>();

    public BossVoiceTakesScenario(Part part) {
        this.part = part;
    }

    @Override
    public int timeBudgetSeconds() {
        return part == Part.HELIARCH ? 600 : 420;
    }

    @Override
    public void steps(Steps steps) {
        steps.command("difficulty peaceful").command("gamemode creative")
                .run("let the sound engine play, inaudibly", () -> VoiceProbe.audible(true));
        // one voice with the guide: a boss line waits for her, and is dropped after 2 s
        steps.waitUntil("the guide is quiet", 900, () -> EchoClient.playing() == null && EchoClient.waiting().isEmpty())
                .waitTicks(20);
        BossCatalog catalog = BossCatalog.of(part.boss);
        boolean photographedRelay = false;
        for (int i = 0; i < catalog.lines().size(); i++) {
            VoiceLine line = catalog.lines().get(i);
            for (var e : line.variants().entrySet()) {
                VoiceProbe probe = new VoiceProbe(part.boss, line, e.getKey(), problems);
                boolean relay = e.getValue().fragments().size() == 3;
                boolean photo = i == 0 && e.getKey().equals(line.variants().keySet().iterator().next()) || !photographedRelay && relay;
                photographedRelay |= relay;
                steps.run("begin " + probe.name(), probe::begin)
                        .command("cosmicbreach debug voice say " + part.boss + " " + line.id() + " " + e.getKey());
                if (photo) {
                    // the caption is up after a few ticks: photographed once, then the take is followed to its end
                    steps.waitUntil("the caption of " + probe.name() + " is up", 30, probe::captionUp)
                            .screenshot("caption_" + part.boss + "_" + line.id() + "_" + e.getKey());
                }
                int length = e.getValue().lengthTicks();
                steps.waitUntil(probe.name() + " has played", length + 80, () -> probe.poll(length + 60))
                        .run("note " + probe.name(), () -> heard.add(probe.name() + " " + probe.ran() + "/" + length))
                        .waitTicks(2);
            }
        }
        steps.run("mute again", () -> VoiceProbe.audible(false));
        steps.log("takes", () -> heard.size() + " takes: " + String.join(", ", heard));
        steps.log("problems", () -> problems.size() + " problems: " + String.join("; ", problems));
        steps.check("every take played to its end on the Voice channel, with its caption, as its catalog says", problems::isEmpty);
    }
}
