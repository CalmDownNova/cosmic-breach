package com.cosmicbreach.client.combat;

/**
 * One button's held state, sampled once per tick, turned into the press and release edges the combat
 * machine takes. A press only counts when it is allowed at that moment (a combat weapon in hand, no
 * screen open); a release always follows a press that went out, so neither side is left holding a
 * button that is up. A button that went down while a press wasn't allowed has to be let go and
 * pressed again. Pure, no game access.
 */
public final class ButtonEdges {
    public enum Edge { NONE, PRESS, RELEASE }

    private boolean wasDown;
    private boolean pressSent;

    /** The edge for this tick's sample. */
    public Edge update(boolean down, boolean pressAllowed) {
        Edge edge = Edge.NONE;
        if (down && !wasDown && pressAllowed) {
            pressSent = true;
            edge = Edge.PRESS;
        } else if (!down && pressSent) {
            pressSent = false;
            edge = Edge.RELEASE;
        }
        wasDown = down;
        return edge;
    }

    /** Forgets any press, taking {@code down} as the button's state now (a held button needs a fresh press). */
    public void reset(boolean down) {
        wasDown = down;
        pressSent = false;
    }

    /** True between a press that went out and its release. */
    public boolean isPressed() {
        return pressSent;
    }
}
