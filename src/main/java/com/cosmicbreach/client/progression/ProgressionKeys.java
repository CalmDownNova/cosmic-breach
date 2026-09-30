package com.cosmicbreach.client.progression;

import com.cosmicbreach.client.combat.ModKeyMappings;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * The Attunement key (K) opens the allocation screen. K is free in vanilla 1.21.1, in Better Combat
 * (its two keys are unbound by default) and in Combat Roll (R).
 */
public final class ProgressionKeys {
    public static final KeyMapping ATTUNEMENT = new KeyMapping("key.cosmicbreach.attunement", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, ModKeyMappings.CATEGORY);

    private ProgressionKeys() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(ATTUNEMENT);
    }
}
