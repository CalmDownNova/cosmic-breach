package com.cosmicbreach.client.anim;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.combat.TwinBlades;
import com.mojang.logging.LogUtils;
import dev.kosmx.playerAnim.api.IPlayable;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Plays combat animations on players (the local one and everyone the server tells us about), with
 * playerAnimator: one {@link CombatAnimationLayer} per player, registered at priority
 * {@value #PRIORITY} (above Better Combat's attack layer at 2000), holding the first-person framing,
 * the walking legs, the hit-stop clock, cross-fades and the animation. Each animation's first person
 * and planted legs are in {@link AnimationStyle}.
 *
 * <p>Timing: the library ticks every player's animations at the very start of the player's tick, and
 * the combat runtime starts the local player's animations from {@code PlayerTickEvent.Pre}, which
 * comes right after. So an animation started with its move shows its tick 0 during the move's tick 0,
 * and its tick t during the move's tick t: the contact pose lands on the move's first active tick.
 *
 * <p>While an animation plays, the body faces where the player looks (its yaw follows the head's), so
 * a swing goes where the crosshair is; in first person the blade follows the camera frame by frame
 * ({@link FirstPersonView}). The vanilla first-person sword rises back into view when an animation
 * that hid it ends.
 */
public final class PlayerAnimations {
    /** The layer's id in each player's playerAnimator data. */
    public static final ResourceLocation LAYER_ID = CosmicBreach.id("combat");
    public static final int PRIORITY = 3000;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<ResourceLocation> REPORTED_MISSING = new HashSet<>();

    /** The local player's first person showed the animated model last tick. */
    private static boolean animatedFirstPerson;

    private PlayerAnimations() {
    }

    /** Hooks the layer factory (at client setup, before any player exists) and the per-tick upkeep. */
    public static void register(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(() ->
                PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER_ID, PRIORITY, CombatAnimationLayer::new)));
        gameBus.addListener(EventPriority.HIGHEST, ClientTickEvent.Pre.class, event -> {
            Minecraft mc = Minecraft.getInstance();
            AnimationTime.onClientTick(mc.level != null && !mc.isPaused());
        });
        gameBus.addListener(PlayerTickEvent.Post.class, PlayerAnimations::afterPlayerTick);
    }

    /**
     * Starts {@code animation} on {@code player} from its first frame, left-right mirrored if asked. A weapon
     * with a second blade plays its solo version while that blade is away ({@link TwinBlades#animationFor}).
     */
    public static void play(AbstractClientPlayer player, ResourceLocation requested, boolean mirrored) {
        ResourceLocation animation = TwinBlades.animationFor(player, requested);
        CombatAnimationLayer layer = layer(player);
        KeyframeAnimation data = animation(animation);
        if (layer != null && data != null) {
            layer.play(animation, data, mirrored);
        }
    }

    /** Stops whatever combat animation {@code player} is playing (a move was cancelled): it blends out. */
    public static void stop(AbstractClientPlayer player) {
        CombatAnimationLayer layer = layer(player);
        if (layer != null) {
            layer.stop();
        }
    }

    /**
     * Holds {@code player}'s current animation still for {@code ticks} ticks (a hit landed), then
     * plays it faster until it has caught up with the move's timing.
     */
    public static void hitStop(AbstractClientPlayer player, int ticks) {
        CombatAnimationLayer layer = layer(player);
        if (layer != null) {
            layer.hitStop(ticks);
        }
    }

    // ------------------------------------------------------------------ for tests and tools

    /** What a player's combat layer shows right now. */
    public record State(@Nullable ResourceLocation animation, float time, boolean mirrored, boolean layerActive,
                        boolean holding, double lag, float legWeight, FirstPersonMode firstPerson) {
        /** The animation's current whole tick, or -1 when none plays. */
        public int tick() {
            return animation == null ? -1 : (int) Math.floor(time + 1e-4f);
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "%s t=%.2f%s%s lag=%.2f legs=%.2f fp=%s", animation == null ? "none" : animation.getPath(),
                    time, mirrored ? " mirrored" : "", holding ? " holding" : "", lag, legWeight, firstPerson);
        }
    }

    /** The state of {@code player}'s combat layer (for tests and tools). */
    public static State state(AbstractClientPlayer player) {
        CombatAnimationLayer layer = layer(player);
        if (layer == null) {
            return new State(null, 0f, false, false, false, 0, 0f, FirstPersonMode.NONE);
        }
        CombatAnimationPlayer playing = layer.playing();
        float time = playing == null ? 0f : playing.getTick() + (float) layer.clock().fraction();
        return new State(playing == null ? null : playing.id(), time, layer.isMirrored(),
                layer.isActive(), layer.clock().isHolding(), layer.clock().lag(), layer.legs().weight(0f),
                layer.isActive() ? layer.getFirstPersonMode(0f) : FirstPersonMode.NONE);
    }

    /**
     * Shows {@code animation} on {@code player} held still at exactly {@code tick} (whole or fractional),
     * with no blend: for looking at key poses from any side (dev only).
     */
    public static void showPose(AbstractClientPlayer player, ResourceLocation animation, boolean mirrored, double tick) {
        CombatAnimationLayer layer = layer(player);
        KeyframeAnimation data = animation(animation);
        if (layer == null || data == null) {
            throw new IllegalStateException("cannot show " + animation + " on " + player.getName().getString());
        }
        int whole = (int) Math.floor(tick);
        layer.play(animation, data, mirrored, whole, false);
        layer.freeze(tick - whole);
    }

    /**
     * Dev only: show every animation's first person as {@code mode} with these arms and items shown,
     * instead of each animation's own choice; null restores the choices. For comparing them.
     */
    public static void overrideFirstPerson(@Nullable FirstPersonMode mode, boolean arms, boolean blade) {
        CombatAnimationPlayer.firstPersonOverride = mode == null ? null
                : new AnimationStyle(mode, new FirstPersonConfiguration(arms, arms, blade, false), 0f, 0f, Float.NaN, 0);
    }

    /**
     * Dev only: first-person framing for every animation (blocks ahead, blocks down, reference pitch or
     * NaN for none); all NaN restores each animation's own. For trying values.
     */
    public static void overrideFirstPersonView(float push, float drop, float referencePitch) {
        FirstPersonView.devPush = push;
        FirstPersonView.devDrop = drop;
        FirstPersonView.devPitch = referencePitch;
    }

    // ------------------------------------------------------------------ upkeep

    /**
     * After every client player's tick: while an animation plays the body faces where the head looks.
     * For the local player, when the animated first person ends the vanilla sword rises back into view.
     */
    private static void afterPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer player) || !player.level().isClientSide()) {
            return;
        }
        CombatAnimationLayer layer = layer(player);
        if (layer == null) {
            return;
        }
        boolean active = layer.isActive();
        if (active) {
            player.setYBodyRot(player.getYHeadRot());
        }
        Minecraft mc = Minecraft.getInstance();
        if (player == mc.player) {
            boolean animated = active && layer.getFirstPersonMode(0f) == FirstPersonMode.THIRD_PERSON_MODEL;
            if (animatedFirstPerson && !animated) {
                mc.getEntityRenderDispatcher().getItemInHandRenderer().itemUsed(InteractionHand.MAIN_HAND);
            }
            animatedFirstPerson = animated;
        }
    }

    // ------------------------------------------------------------------ helpers

    private static @Nullable CombatAnimationLayer layer(AbstractClientPlayer player) {
        IAnimation animation = PlayerAnimationAccess.getPlayerAssociatedData(player).get(LAYER_ID);
        return animation instanceof CombatAnimationLayer layer ? layer : null;
    }

    private static @Nullable KeyframeAnimation animation(ResourceLocation id) {
        IPlayable playable = PlayerAnimationRegistry.getAnimation(id);
        if (playable instanceof KeyframeAnimation keyframes) {
            return keyframes;
        }
        if (REPORTED_MISSING.add(id)) {
            LOGGER.error("[cosmicbreach] a player animation is missing or not a keyframe animation");
            LOGGER.debug("[cosmicbreach] missing player animation {} (found {})", id, playable);
        }
        return null;
    }
}
