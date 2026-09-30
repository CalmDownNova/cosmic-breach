package com.cosmicbreach.client.familiar;

import com.cosmicbreach.client.combat.ModKeyMappings;
import com.cosmicbreach.familiar.FamiliarKeyPayload;
import com.cosmicbreach.familiar.FamiliarRules;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * The familiar key, H by default: held {@value FamiliarRules#HOLD_TICKS} ticks it summons or dismisses, a shorter press
 * cycles Attack, Guard and Passive. The GDD asks for G, but G opens the Curios inventory (Curios is a dependency, and its
 * default is G), so both would fire; H is free in vanilla, in Better Combat (its two keys are unbound), in Combat Roll
 * (it rolls on R), in Curios and among this mod's keys (Left Alt, V, K, Z).
 */
public final class FamiliarKeys {
    public static final KeyMapping KEY = new KeyMapping("key.cosmicbreach.familiar", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, ModKeyMappings.CATEGORY);

    private static int held;
    private static boolean fired;

    private FamiliarKeys() {
    }

    static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        boolean clicked = false;
        while (KEY.consumeClick()) {
            clicked = true;
        }
        if (mc.player == null || mc.screen != null) {
            held = 0;
            fired = false;
            return;
        }
        if (KEY.isDown()) {
            held++;
            if (held >= FamiliarRules.HOLD_TICKS && !fired) {
                fired = true;
                PacketDistributor.sendToServer(new FamiliarKeyPayload(FamiliarKeyPayload.TOGGLE));
            }
            return;
        }
        if ((held > 0 || clicked) && !fired) {
            PacketDistributor.sendToServer(new FamiliarKeyPayload(FamiliarKeyPayload.CYCLE));
        }
        held = 0;
        fired = false;
    }
}
