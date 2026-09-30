package com.cosmicbreach.client.relic;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.fx.WeaponGlow;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatStateMachine;
import com.cosmicbreach.combat.core.MoveInstance;
import com.cosmicbreach.relic.Relics;
import com.cosmicbreach.relic.cantor.Cantor;
import com.cosmicbreach.relic.cantor.CantorRules;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The Heliarch's relics on the client: the renderers (the Cantor's arrows, notes and chords; the Crown's sun), the item
 * properties their models read ({@code cosmicbreach:resonance} for both weapons' glow, {@code cosmicbreach:sunlight} for
 * Last Light's gems, {@code cosmicbreach:draw} for the Cantor's string) and their effects ({@link LastLightFx},
 * {@link CantorFx}).
 */
public final class RelicsClient {
    public static final ResourceLocation SUNLIGHT = CosmicBreach.id("sunlight");
    public static final ResourceLocation DRAW = CosmicBreach.id("draw");
    /** Other players' draws, by entity id: the client tick their charge began (the server says when it starts and stops). */
    private static final Map<Integer, Long> DRAWS = new HashMap<>();
    private static long ticks;

    private RelicsClient() {
    }

    public static void register(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerEntityRenderer(Relics.UMBRA_ARROW.get(), UmbraArrowRenderer::new);
            event.registerEntityRenderer(Relics.RESONANT_NOTE.get(), ResonantNoteRenderer::new);
            event.registerEntityRenderer(Relics.CHORD.get(), ChordRenderer::new);
            event.registerBlockEntityRenderer(Relics.CROWN.get(), CrownRenderer::new);
        });
        modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(RelicsClient::registerItemProperties));
        gameBus.addListener(ClientTickEvent.Post.class, event -> tick());
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
            LastLightFx.clear();
            DRAWS.clear();
        });
        LastLightFx.register();
        CantorFx.register();
    }

    private static void registerItemProperties() {
        ClampedItemPropertyFunction resonance = RelicsClient::resonance;
        ItemProperties.register(Relics.LAST_LIGHT.get(), WeaponGlow.PROPERTY, resonance);
        ItemProperties.register(Relics.UMBRA_CANTOR.get(), WeaponGlow.PROPERTY, resonance);
        ItemProperties.register(Relics.LAST_LIGHT.get(), SUNLIGHT, (ClampedItemPropertyFunction) RelicsClient::sunlight);
        ItemProperties.register(Relics.UMBRA_CANTOR.get(), DRAW, (ClampedItemPropertyFunction) RelicsClient::draw);
    }

    /** The local player's Resonance fraction while the stack is in its main hand (the engine's glow stages), else 0. */
    private static float resonance(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || entity != player || player.getMainHandItem() != stack) {
            return 0f;
        }
        PlayerCombat combat = PlayerCombat.existing(player);
        if (combat == null) {
            return 0f;
        }
        CombatStateMachine m = combat.machine();
        int max = m.maxResonance();
        return max <= 0 ? 0f : m.resonance() >= max - 1e-6 ? 1f : (float) Math.max(0.0, Math.min(1.0, m.resonance() / max));
    }

    /** A third of a step per Sunlight charge its holder holds (any player), on the stack in that player's main hand. */
    private static float sunlight(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
        if (!(entity instanceof Player player) || player.getMainHandItem() != stack) {
            return 0f;
        }
        return LastLightFx.charges(player) / 3f;
    }

    /**
     * How far the Cantor's string is drawn: the local player's from its own machine (a quick shot's startup draws it
     * part way, the charged shot's hold all the way), another player's from its charge's start; 0 at rest.
     */
    private static float draw(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
        if (!(entity instanceof Player player) || player.getMainHandItem() != stack) {
            return 0f;
        }
        LocalPlayer me = Minecraft.getInstance().player;
        if (player == me) {
            PlayerCombat combat = PlayerCombat.existing(me);
            if (combat == null) {
                return 0f;
            }
            CombatStateMachine m = combat.machine();
            if (m.phase() == CombatStateMachine.Phase.CHARGING) {
                return 0.3f + 0.7f * Math.min(1f, m.attackHeldTicks() / (float) CantorRules.DRAW_TICKS);
            }
            MoveInstance move = m.current();
            if (move != null && m.phase() == CombatStateMachine.Phase.STARTUP
                    && (move.def().traits().effect(Cantor.UMBRA_SHOT).isPresent() || move.def().traits().effect(Cantor.CADENCE).isPresent())) {
                return 0.3f + 0.4f * (m.phaseTick() + 1) / (float) Math.max(1, move.def().timing().startup());
            }
            if (move != null && m.phase() == CombatStateMachine.Phase.ACTIVE && move.def().traits().effect(Cantor.CADENCE).isPresent()) {
                return 0.65f;
            }
            return 0f;
        }
        Long since = DRAWS.get(player.getId());
        return since == null ? 0f : 0.3f + 0.7f * Math.min(1f, (ticks - since) / 14f);
    }

    private static void tick() {
        ticks++;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            DRAWS.clear();
            return;
        }
        for (Player player : mc.level.players()) {
            if (player == mc.player) {
                continue;
            }
            boolean charging = com.cosmicbreach.client.fx.Chargers.isCharging(player.getId())
                    && player.getMainHandItem().is(Relics.UMBRA_CANTOR.get());
            if (charging) {
                DRAWS.putIfAbsent(player.getId(), ticks);
            } else {
                DRAWS.remove(player.getId());
            }
        }
    }
}
