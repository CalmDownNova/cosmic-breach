package com.cosmicbreach.combat;

import org.jetbrains.annotations.Nullable;

/** One combat input edge, as the client sends it and as {@link PlayerCombat#apply} takes it. */
public enum CombatAction {
    ATTACK_PRESS,
    ATTACK_RELEASE,
    ABILITY_PRESS,
    ABILITY_RELEASE,
    DASH,
    PARRY;

    private static final CombatAction[] BY_ID = values();

    /** The byte that goes over the network. */
    public byte id() {
        return (byte) ordinal();
    }

    public static @Nullable CombatAction byId(int id) {
        return id >= 0 && id < BY_ID.length ? BY_ID[id] : null;
    }

    /** Attack and ability presses need a combat weapon in hand; releases, dashes and parries don't. */
    public boolean needsWeapon() {
        return this == ATTACK_PRESS || this == ABILITY_PRESS;
    }
}
