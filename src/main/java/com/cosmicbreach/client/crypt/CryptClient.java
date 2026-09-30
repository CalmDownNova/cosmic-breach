package com.cosmicbreach.client.crypt;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.combat.BodyMotion;
import com.cosmicbreach.structure.crypt.CryptRegistry;
import com.cosmicbreach.structure.crypt.trap.CryptTraps;
import com.cosmicbreach.structure.crypt.trap.GravityPlateRules;
import com.cosmicbreach.structure.trap.KineticRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * The crypt's client side (W6): the Choir Floor drawn from its Conductor ({@link ConductorRenderer}), the traps' tells
 * for careful players and their motion ({@link RiftTileRenderer}, {@link GravityPlateRenderer},
 * {@link ChuteRenderer}), and half-length dashes under crushing gravity.
 */
public final class CryptClient {
    static final ResourceLocation FILL = CosmicBreach.id("textures/fx/choir_fill.png");
    static final ResourceLocation GLYPHS = CosmicBreach.id("textures/fx/choir_glyphs.png");
    static final ResourceLocation RING = CosmicBreach.id("textures/fx/choir_ring.png");
    static final ResourceLocation CRACKS = CosmicBreach.id("textures/fx/rift_cracks.png");
    static final ResourceLocation RINGS = CosmicBreach.id("textures/fx/gravity_rings.png");
    static final ResourceLocation GLINT = CosmicBreach.id("textures/fx/chute_glint.png");
    static final ResourceLocation GLOW = CosmicBreach.id("textures/fx/glow.png");

    private CryptClient() {
    }

    public static void register(IEventBus modBus, IEventBus game) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerBlockEntityRenderer(CryptRegistry.CONDUCTOR_ENTITY.get(), context -> new ConductorRenderer());
            event.registerBlockEntityRenderer(CryptRegistry.VOID_RIFT_ENTITY.get(), context -> new RiftTileRenderer());
            event.registerBlockEntityRenderer(CryptRegistry.GRAVITY_PISTON_ENTITY.get(), context -> new GravityPlateRenderer());
            event.registerBlockEntityRenderer(CryptRegistry.STARFALL_CHUTE_ENTITY.get(), context -> new ChuteRenderer());
        });
        BodyMotion.addDashScale(() -> {
            LocalPlayer p = Minecraft.getInstance().player;
            return p != null && CryptTraps.heavy(p) ? GravityPlateRules.DASH_SCALE : 1.0;
        });
    }

    /**
     * True if the local player moves carefully (sneaking or walking, not sprinting or dashing): the tells show. A
     * Sunshard Compass wearer sees them at any speed ({@link com.cosmicbreach.accessory.TrapSight}).
     */
    public static boolean careful(LocalPlayer player) {
        double speed = Math.hypot(player.getX() - player.xo, player.getZ() - player.zo);
        return KineticRules.careful(player.isSprinting(), speed) || com.cosmicbreach.accessory.TrapSight.anySpeed(player);
    }

    /** Pad colours, 0xRRGGBB: gold, orange, rose, magenta, violet, azure, teal, lime (D4 up to F#5). */
    public static final int[] PAD_RGB = {0xFFC23A, 0xFF7A2E, 0xFF4F7B, 0xE65CFF, 0x8F6BFF, 0x3FA9FF, 0x2FE0C8, 0xA6F04A};
}
