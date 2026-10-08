package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.voice.BossCaptionLayer;
import com.cosmicbreach.client.voice.BossVoiceClient;
import com.cosmicbreach.voice.boss.BossCatalog;
import com.cosmicbreach.voice.boss.VoiceLine;
import java.util.List;
import net.minecraft.sounds.SoundSource;

/**
 * One boss take watched on the client from the tick it is sent to the tick it ends (Lane A, A5.3): was it started once, did the
 * sound engine have it playing, did it run as long as its catalog says, on the Voice channel and not cut short, and was its
 * caption up while it played, reading as the language file gives it (the words in quotes). A problem is noted in the list it is
 * given, never thrown, so a scenario can play every take and report them all at the end.
 */
final class VoiceProbe {
    /** Ticks a take may run past its catalog length before it counts as longer than the catalog says. */
    static final int LENGTH_SLACK = 4;

    /**
     * The hidden test client has its master volume at 0, so the sound engine skips every sound and a take is never active. A
     * scenario that wants to know the engine plays a take gives the engine a volume it can start it at, an inaudible one
     * (-66 dB): {@code true} before the takes, {@code false} after.
     */
    static void audible(boolean on) {
        net.minecraft.client.Minecraft.getInstance().options.getSoundSourceOptionInstance(SoundSource.MASTER).set(on ? 0.0005 : 0.0);
    }

    final String boss;
    final VoiceLine line;
    final String key;
    final VoiceLine.Variant take;
    private final List<String> problems;
    private int historyBefore;
    private int startedBefore;
    private int waited;
    private boolean done;
    private boolean sounded;
    private boolean captioned;
    private String captionSeen = "";

    private final java.util.function.Supplier<String> server;

    VoiceProbe(String boss, VoiceLine line, String key, List<String> problems) {
        this(boss, line, key, problems, () -> "");
    }

    /** {@code server}: what the boss's voice has done on the server, appended to a problem about a take that never started. */
    VoiceProbe(String boss, VoiceLine line, String key, List<String> problems, java.util.function.Supplier<String> server) {
        this.server = server;
        this.boss = boss;
        this.line = line;
        this.key = key;
        this.take = line.variants().get(key);
        this.problems = problems;
    }

    String name() {
        return boss + "/" + line.id() + "/" + key;
    }

    /** Before the take is sent. */
    void begin() {
        historyBefore = BossVoiceClient.history().size();
        startedBefore = BossVoiceClient.count(boss, line.id());
        waited = 0;
        startWaited = 0;
        done = false;
        ran = -1;
    }

    /** True once the client has started the take (since {@link #begin}). */
    boolean started() {
        sample();
        return BossVoiceClient.count(boss, line.id()) > startedBefore;
    }

    private int startWaited;

    /**
     * Called every tick until it returns true: the take has started, or the boss never started it within {@code limit} ticks
     * (its gate held it for its whole wait, or the director dropped it): a problem, and the take is then not waited for.
     */
    boolean awaitStart(int limit) {
        if (started()) {
            return true;
        }
        if (++startWaited > limit) {
            done = true;
            problems.add(name() + ": never started within " + limit + " ticks of its moment" + server.get());
            return true;
        }
        return false;
    }

    /** What the client is doing now: the engine playing it, its caption up. */
    void sample() {
        sounded |= BossVoiceClient.sounding();
        if (BossVoiceClient.caption() == line && BossVoiceClient.captionTake() == take) {
            captioned = true;
            captionSeen = BossCaptionLayer.text(line, take, BossCatalog.of(boss).captionColor()).getString();
        }
    }

    /** True once the caption is fully up. */
    boolean captionUp() {
        sample();
        return captioned && BossVoiceClient.captionAlpha(0f) > 0.9f;
    }

    /** Called every tick until it returns true: the take has ended, or it will not within {@code limit} ticks. */
    boolean poll(int limit) {
        if (done) {
            return true;
        }
        sample();
        List<BossVoiceClient.Heard> history = BossVoiceClient.history();
        if (history.size() > historyBefore) {
            done = true;
            end(history.get(history.size() - 1));
            return true;
        }
        if (++waited > limit) {
            done = true;
            problems.add(name() + ": the take never ended within " + limit + " ticks");
            return true;
        }
        return false;
    }

    /** The tick count the take ran, once it has ended; -1 before. */
    private int ran = -1;

    int ran() {
        return ran;
    }

    private void end(BossVoiceClient.Heard h) {
        String expected = "\"" + line.words() + "\"";
        ran = (int) (h.end() - h.start());
        if (!h.line().equals(line.id()) || !h.variant().equals(key)) {
            problems.add(name() + ": the client played " + h.line() + "/" + h.variant());
        }
        int times = BossVoiceClient.count(boss, line.id()) - startedBefore;
        if (times != 1) {
            problems.add(name() + ": started " + times + " times");
        }
        if (h.source() != SoundSource.VOICE) {
            problems.add(name() + ": on the " + h.source() + " channel");
        }
        if (h.interrupted()) {
            problems.add(name() + ": stopped early by another line");
        }
        if (!sounded) {
            problems.add(name() + ": the sound engine never had it playing");
        }
        if (!h.cut() && (ran < take.lengthTicks() || ran > take.lengthTicks() + LENGTH_SLACK)) {
            problems.add(name() + ": ran " + ran + " ticks, the catalog says " + take.lengthTicks());
        }
        if (!captioned) {
            problems.add(name() + ": no caption while it played");
        } else if (!captionSeen.equals(expected)) {
            problems.add(name() + ": the caption reads " + captionSeen);
        }
    }
}
