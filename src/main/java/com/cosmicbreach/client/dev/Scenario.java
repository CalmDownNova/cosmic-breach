package com.cosmicbreach.client.dev;

/**
 * One autotest scenario: an ordered list of steps run in a fresh superflat creative world
 * (cheats on, noon, clear weather, no natural mob spawning), starting on the tick the player
 * has spawned. The scenario passes when every step finishes. Register new scenarios in
 * {@link Scenarios}; run one with {@code ./gradlew runTestClient -Pautotest=<name>}.
 */
public interface Scenario {
    /** Adds this scenario's steps, in order. Called once, in game, when the player has spawned. */
    void steps(Steps steps);

    /** Wall clock seconds the steps may take, counted from the first step. Taking longer is a FAIL. */
    default int timeBudgetSeconds() {
        return 120;
    }

    /** True for the default terrain (with structures) instead of the superflat world. */
    default boolean normalWorld() {
        return false;
    }

    /** True to create the test world with structures (off by default: the flat preset would place villages). */
    default boolean generateStructures() {
        return false;
    }

    /**
     * True if the scenario drives everything through the client (commands, key presses, what the client sees), so it
     * can run against a real server: {@code -Pjoin=<host:port>} ({@link AutoTest#JOIN_PROPERTY}). Scenarios that reach
     * into the built-in server can't, and join mode refuses them.
     */
    default boolean multiplayer() {
        return false;
    }
}
