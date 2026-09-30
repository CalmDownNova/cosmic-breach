package com.cosmicbreach.client.dev;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.KeyMapping;

/** What a running step can do in the game. {@link AutoTest} implements it on the client thread. */
interface StepContext {
    void log(String message);

    /** Sends a command as the player, like typing it in chat (no leading slash needed). */
    void command(String command);

    /** Presses (with a click) or releases a key mapping through {@code KeyMapping.set/click}. */
    void key(KeyMapping key, boolean down);

    void look(float yaw, float pitch);

    /** Captures the next rendered frame; completes once captured, exceptionally if it failed or is blank. */
    CompletableFuture<Void> screenshot(String label);

    /** Every chat line received since the scenario started, oldest first. */
    List<String> chat();
}
