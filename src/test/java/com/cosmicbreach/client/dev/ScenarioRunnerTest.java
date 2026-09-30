package com.cosmicbreach.client.dev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.KeyMapping;
import org.junit.jupiter.api.Test;

/** The step timing rules scenarios rely on (see {@link Steps}). */
class ScenarioRunnerTest {
    /** Records log lines as "tick:message" and hands out screenshot futures the test completes. */
    private static final class FakeContext implements StepContext {
        final List<String> lines = new ArrayList<>();
        final List<String> chat = new ArrayList<>();
        final List<CompletableFuture<Void>> shots = new ArrayList<>();
        ScenarioRunner runner;

        @Override
        public void log(String message) {
            lines.add(runner.ticks() + ":" + message);
        }

        @Override
        public void command(String command) {
            lines.add(runner.ticks() + ":/" + command);
        }

        @Override
        public void key(KeyMapping key, boolean down) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void look(float yaw, float pitch) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Void> screenshot(String label) {
            CompletableFuture<Void> shot = new CompletableFuture<>();
            shots.add(shot);
            return shot;
        }

        @Override
        public List<String> chat() {
            return chat;
        }
    }

    private static ScenarioRunner runner(Steps steps, FakeContext ctx) {
        ctx.runner = new ScenarioRunner(steps, ctx);
        return ctx.runner;
    }

    private static int ticksUntilDone(ScenarioRunner runner) throws Exception {
        for (int tick = 0; tick < 1000; tick++) {
            if (runner.tick()) {
                return tick;
            }
        }
        throw new AssertionError("scenario never finished");
    }

    @Test
    void waitTicksRunsTheNextStepThatManyTicksLater() throws Exception {
        FakeContext ctx = new FakeContext();
        ScenarioRunner runner = runner(new Steps().log("a").command("time set noon").waitTicks(3).log("b").waitTicks(0).log("c"), ctx);
        assertEquals(3, ticksUntilDone(runner));
        assertEquals(List.of("0:a", "0:/time set noon", "3:b", "3:c"), ctx.lines);
    }

    @Test
    void screenshotCostsOneTickAndWaitsForTheCapture() throws Exception {
        FakeContext ctx = new FakeContext();
        ScenarioRunner runner = runner(new Steps().screenshot("spawn").log("after"), ctx);
        assertFalse(runner.tick());
        assertEquals(1, ctx.shots.size());
        assertFalse(runner.tick()); // no frame rendered yet
        ctx.shots.get(0).complete(null);
        assertTrue(runner.tick());
        assertEquals(List.of("2:after"), ctx.lines);
    }

    @Test
    void aBlankScreenshotFailsTheStep() throws Exception {
        FakeContext ctx = new FakeContext();
        ScenarioRunner runner = runner(new Steps().screenshot("spawn"), ctx);
        runner.tick();
        ctx.shots.get(0).completeExceptionally(new Steps.Failure("screenshot spawn is blank"));
        Steps.Failure failure = assertThrows(Steps.Failure.class, runner::tick);
        assertEquals("screenshot spawn is blank", failure.getMessage());
    }

    @Test
    void checkFailsAtItsOwnStep() throws Exception {
        FakeContext ctx = new FakeContext();
        ScenarioRunner runner = runner(new Steps().log("a").check("the sky is green", () -> false).log("never"), ctx);
        Steps.Failure failure = assertThrows(Steps.Failure.class, runner::tick);
        assertEquals("check failed: the sky is green", failure.getMessage());
        assertEquals("step 2/3 (check the sky is green)", runner.describeCurrent());
    }

    @Test
    void waitUntilChecksEveryTickAndTimesOut() throws Exception {
        FakeContext ctx = new FakeContext();
        AtomicBoolean ready = new AtomicBoolean();
        ScenarioRunner passing = runner(new Steps().waitUntil("ready", 5, ready::get).log("done"), ctx);
        assertFalse(passing.tick());
        assertFalse(passing.tick());
        ready.set(true);
        assertTrue(passing.tick());
        assertEquals(List.of("2:ok: ready", "2:done"), ctx.lines);

        FakeContext ctx2 = new FakeContext();
        ScenarioRunner failing = runner(new Steps().waitUntil("never", 2, () -> false), ctx2);
        assertFalse(failing.tick());
        assertFalse(failing.tick());
        Steps.Failure failure = assertThrows(Steps.Failure.class, failing::tick);
        assertEquals("timed out after 2 ticks waiting until never", failure.getMessage());
    }

    @Test
    void waitForChatOnlyMatchesLinesReceivedAfterItStarted() throws Exception {
        FakeContext ctx = new FakeContext();
        ctx.chat.add("selftest PASS (old)");
        ScenarioRunner runner = runner(new Steps().waitForChat("selftest (PASS|FAIL)", 10), ctx);
        assertFalse(runner.tick());
        ctx.chat.add("unrelated");
        assertFalse(runner.tick());
        ctx.chat.add("selftest PASS");
        assertTrue(runner.tick());
    }

    @Test
    void hiddenWindowMixinsOnlyApplyInHiddenWindowMode() {
        String window = "com.cosmicbreach.mixin.client.WindowHiddenMixin";
        String dialogs = "com.cosmicbreach.mixin.client.MinecraftNoDialogMixin";
        String other = "com.cosmicbreach.mixin.client.SomeFutureMixin";
        assertFalse(HiddenWindowMixinPlugin.shouldApply(window, false));
        assertFalse(HiddenWindowMixinPlugin.shouldApply(dialogs, false));
        assertTrue(HiddenWindowMixinPlugin.shouldApply(window, true));
        assertTrue(HiddenWindowMixinPlugin.shouldApply(dialogs, true));
        assertTrue(HiddenWindowMixinPlugin.shouldApply(other, false));
    }
}
