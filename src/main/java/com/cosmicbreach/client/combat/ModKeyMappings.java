package com.cosmicbreach.client.combat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * The combat keys: Dash (Left Alt, under the thumb while moving) and Parry (V), in their own category,
 * active in game only. Attack and the ability use vanilla's attack and use keys.
 *
 * <p>Both defaults are free in vanilla and in the comparison mods (Combat Roll rolls on R). C and X
 * were avoided because vanilla uses them for the creative hotbar activators.
 */
public final class ModKeyMappings {
    public static final String CATEGORY = "key.categories.cosmicbreach";

    public static final KeyMapping DASH = new KeyMapping("key.cosmicbreach.dash", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, CATEGORY);
    public static final KeyMapping PARRY = new KeyMapping("key.cosmicbreach.parry", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);

    private ModKeyMappings() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(DASH);
        event.register(PARRY);
    }
}
