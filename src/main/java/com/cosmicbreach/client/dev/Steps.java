package com.cosmicbreach.client.dev;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import net.minecraft.client.KeyMapping;

/**
 * The step list of a {@link Scenario}, built with chained calls:
 *
 * <pre>{@code
 * steps.command("give @s minecraft:iron_sword")
 *      .look(0, 10)
 *      .press(mc.options.keyAttack)
 *      .waitTicks(5)
 *      .screenshot("after_swing");
 * }</pre>
 *
 * <p>Timing, in client ticks (20 per second):
 * <ul>
 *   <li>Steps run at the start of each client tick, before vanilla handles key mappings and
 *       ticks entities, so a key pressed by a step is seen in that same tick.</li>
 *   <li>Instant steps ({@code command, hold, release, look, log, check, run}) take no time:
 *       the next step runs in the same tick.</li>
 *   <li>{@code waitTicks(n)}: the next step runs n ticks later.</li>
 *   <li>{@code press(key)} holds the key for exactly one tick, {@code press(key, n)} for n.</li>
 *   <li>{@code screenshot} captures the frame rendered right after the current tick and
 *       costs one tick: the next step runs on the following tick. A blank (single colour)
 *       frame fails the scenario.</li>
 *   <li>{@code waitUntil} and {@code waitForChat} check every tick, starting with the current
 *       one, and fail after the timeout.</li>
 * </ul>
 * A failed check, a timeout or any exception fails the scenario with the step's description.
 */
public final class Steps {
    private static final Pattern LABEL = Pattern.compile("[A-Za-z0-9_-]+");

    /** Ends a scenario with {@code FAIL <message>}. Throw it from {@link #run} for a clean reason. */
    public static final class Failure extends RuntimeException {
        public Failure(String message) {
            super(message);
        }
    }

    /** One step: called once per client tick while it is current; returns true when finished. */
    @FunctionalInterface
    interface Action {
        boolean tick(StepContext ctx) throws Exception;
    }

    record Step(String description, Action action) {}

    private final List<Step> steps = new ArrayList<>();

    /** Does nothing for {@code ticks} client ticks. */
    public Steps waitTicks(int ticks) {
        requireNonNegative(ticks, "ticks");
        int[] elapsed = {-1};
        return add("wait " + ticks + " ticks", ctx -> ++elapsed[0] >= ticks);
    }

    /** Runs a command as the player, exactly like typing it in chat. The leading slash is optional. */
    public Steps command(String command) {
        String bare = command.startsWith("/") ? command.substring(1) : command;
        return add("command /" + bare, ctx -> {
            ctx.command(bare);
            return true;
        });
    }

    /** Puts the key down (one click, like a real key press) and keeps it down. */
    public Steps hold(KeyMapping key) {
        return add("hold " + key.getName(), ctx -> {
            ctx.key(key, true);
            return true;
        });
    }

    /** Lets the key go. */
    public Steps release(KeyMapping key) {
        return add("release " + key.getName(), ctx -> {
            ctx.key(key, false);
            return true;
        });
    }

    /** A tap: holds the key for one tick, then releases it. */
    public Steps press(KeyMapping key) {
        return press(key, 1);
    }

    /** Holds the key for {@code ticks} ticks (at least 1), then releases it. */
    public Steps press(KeyMapping key, int ticks) {
        if (ticks < 1) {
            throw new IllegalArgumentException("press needs at least 1 tick, got " + ticks);
        }
        return hold(key).waitTicks(ticks).release(key);
    }

    /**
     * Turns the player to face {@code yaw} (0 south, 90 west, 180 north, -90 east) and
     * {@code pitch} (-90 straight up, 0 level, 90 straight down), with no interpolation.
     */
    public Steps look(float yaw, float pitch) {
        return add(String.format(Locale.ROOT, "look yaw %.1f pitch %.1f", yaw, pitch), ctx -> {
            ctx.look(yaw, pitch);
            return true;
        });
    }

    /** Saves the next rendered frame as {@code run-test/autotest/<scenario>/<label>.png}. */
    public Steps screenshot(String label) {
        if (!LABEL.matcher(label).matches()) {
            throw new IllegalArgumentException("screenshot labels use letters, digits, _ and -: " + label);
        }
        CompletableFuture<?>[] capture = {null};
        return add("screenshot " + label, ctx -> {
            if (capture[0] == null) {
                capture[0] = ctx.screenshot(label);
                return false;
            }
            if (!capture[0].isDone()) {
                return false;
            }
            try {
                capture[0].get();
            } catch (ExecutionException e) {
                throw e.getCause() instanceof Exception cause ? cause : e;
            }
            return true;
        });
    }

    /** Writes a line to the game log and to {@code log.txt}. */
    public Steps log(String message) {
        return add("log " + message, ctx -> {
            ctx.log(message);
            return true;
        });
    }

    /** Writes a line made when the step runs (measurements, recorded values), described as {@code what}. */
    public Steps log(String what, Supplier<String> message) {
        return add("log " + what, ctx -> {
            ctx.log(message.get());
            return true;
        });
    }

    /** Fails the scenario unless {@code condition} holds right now. */
    public Steps check(String what, BooleanSupplier condition) {
        return add("check " + what, ctx -> {
            if (!condition.getAsBoolean()) {
                throw new Failure("check failed: " + what);
            }
            ctx.log("ok: " + what);
            return true;
        });
    }

    /** Waits until {@code condition} holds, checking every tick; fails after {@code timeoutTicks}. */
    public Steps waitUntil(String what, int timeoutTicks, BooleanSupplier condition) {
        requireNonNegative(timeoutTicks, "timeoutTicks");
        int[] elapsed = {-1};
        return add("wait until " + what, ctx -> {
            if (condition.getAsBoolean()) {
                ctx.log("ok: " + what);
                return true;
            }
            if (++elapsed[0] >= timeoutTicks) {
                throw new Failure("timed out after " + timeoutTicks + " ticks waiting until " + what);
            }
            return false;
        });
    }

    /**
     * Waits for a chat line received after this step started that contains a match for
     * {@code regex}; fails after {@code timeoutTicks}. Put it right after the command that
     * causes the message.
     */
    public Steps waitForChat(String regex, int timeoutTicks) {
        requireNonNegative(timeoutTicks, "timeoutTicks");
        Pattern pattern = Pattern.compile(regex);
        int[] state = {-1, -1}; // first unread chat line, ticks waited
        return add("wait for chat /" + regex + "/", ctx -> {
            List<String> chat = ctx.chat();
            if (state[0] < 0) {
                state[0] = chat.size();
            }
            for (int i = state[0]; i < chat.size(); i++) {
                if (pattern.matcher(chat.get(i)).find()) {
                    ctx.log("ok: chat matched /" + regex + "/");
                    return true;
                }
            }
            state[0] = chat.size();
            if (++state[1] >= timeoutTicks) {
                throw new Failure("timed out after " + timeoutTicks + " ticks waiting for chat /" + regex + "/");
            }
            return false;
        });
    }

    /** Runs any client-side code (on the client thread). Throw {@link Failure} to fail with a message. */
    public Steps run(String what, Runnable action) {
        return add(what, ctx -> {
            action.run();
            return true;
        });
    }

    List<Step> build() {
        return List.copyOf(steps);
    }

    private Steps add(String description, Action action) {
        steps.add(new Step(description, action));
        return this;
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative, got " + value);
        }
    }
}
