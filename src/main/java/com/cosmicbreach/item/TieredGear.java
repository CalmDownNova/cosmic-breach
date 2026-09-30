package com.cosmicbreach.item;

/** Gear with a fixed unlock tier that the Astral Forge can reforge (the armor set pieces). Weapons take theirs from their data. */
public interface TieredGear {
    /** The tier it is crafted at, 1 to 4. */
    int unlockTier();
}
