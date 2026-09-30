package com.cosmicbreach.client.dev;

import java.util.List;

/** Walks a scenario's steps, one client tick per {@link #tick} call. Knows nothing about Minecraft. */
final class ScenarioRunner {
    private final List<Steps.Step> steps;
    private final StepContext ctx;
    private int index;
    private int ticks = -1;

    ScenarioRunner(Steps steps, StepContext ctx) {
        this.steps = steps.build();
        this.ctx = ctx;
    }

    /**
     * Runs one client tick: finishes as many steps as possible and stops at the first one
     * that needs more ticks. Returns true once every step has finished. Throws on failure.
     */
    boolean tick() throws Exception {
        ticks++;
        while (index < steps.size()) {
            if (!steps.get(index).action().tick(ctx)) {
                return false;
            }
            index++;
        }
        return true;
    }

    /**
     * The scenario's clock: 0 during the first tick and the frame rendered after it, 1 during
     * the second, and so on (-1 before the first tick).
     */
    int ticks() {
        return ticks;
    }

    int size() {
        return steps.size();
    }

    /** "step 3/7 (wait 40 ticks)", for failure messages. */
    String describeCurrent() {
        if (index >= steps.size()) {
            return "the end of the scenario";
        }
        return "step " + (index + 1) + "/" + steps.size() + " (" + steps.get(index).description() + ")";
    }
}
