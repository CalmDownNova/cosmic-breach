package com.cosmicbreach.client.anim;

import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.api.layered.modifier.MirrorModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * One player's combat animation layer. Its chain, outside in: {@link FirstPersonView} (where the model
 * sits in front of the camera in first person), {@link WalkingLegs} (on real time, so a hit-stop never
 * freezes walking), {@link CombatClock} (game time bent by hit-stop), the cross-fades
 * {@link #play} adds, then the animation, wrapped in its own {@link MirrorModifier} when mirrored. The
 * mirror sits on the animation rather than on the layer so that a cross-fade from an unmirrored pose
 * into a mirrored one (the ready guard into a left dash) blends between the two as they are, instead
 * of flipping the pose being faded out.
 */
final class CombatAnimationLayer extends ModifierLayer<IAnimation> {
    /** A new animation blends in from whatever was showing over this many ticks. */
    static final int PLAY_FADE_TICKS = 2;
    /** A stopped animation blends out to vanilla over this many ticks. */
    static final int STOP_FADE_TICKS = 3;

    /** Modifiers before the cross-fades: the view, the legs and the clock. */
    private static final int FIXED_MODIFIERS = 3;

    private final FirstPersonView view;
    private final WalkingLegs legs;
    private final CombatClock clock = new CombatClock();
    private final AbstractClientPlayer player;
    private @Nullable CombatAnimationPlayer playing;
    private boolean mirrored;

    CombatAnimationLayer(AbstractClientPlayer player) {
        super(null);
        this.player = player;
        this.view = new FirstPersonView(player);
        this.legs = new WalkingLegs(player);
        addModifierLast(view);
        addModifierLast(legs);
        addModifierLast(clock);
    }

    void play(ResourceLocation id, KeyframeAnimation data, boolean mirror) {
        play(id, data, mirror, 0, true);
    }

    /**
     * Starts {@code data} at {@code startTick}, blending in from whatever shows now (vanilla's pose if
     * nothing plays) unless {@code fade} is false.
     */
    void play(ResourceLocation id, KeyframeAnimation data, boolean mirror, int startTick, boolean fade) {
        CombatAnimationPlayer next = new CombatAnimationPlayer(player, id, data, startTick);
        IAnimation shown = next;
        if (mirror) {
            MirrorModifier mirrorModifier = new MirrorModifier(true);
            mirrorModifier.setAnim(next);
            shown = mirrorModifier;
        }
        boolean showing = isActive();
        clock.restart();
        view.style(next.style(), !showing);
        legs.start(next.style().plantedTicks(), !showing);
        if (fade) {
            replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(PLAY_FADE_TICKS, Ease.INOUTSINE), shown, true);
        } else {
            while (size() > FIXED_MODIFIERS) {
                removeModifier(FIXED_MODIFIERS); // drop any fade still running
            }
            setAnimation(shown);
        }
        playing = next;
        mirrored = mirror;
    }

    /** Blends whatever plays out to vanilla (at normal speed: a hit-stop never holds a blend-out). */
    void stop() {
        IAnimation current = getAnimation();
        clock.clearHold();
        if (current != null && current.isActive()) {
            replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(STOP_FADE_TICKS, Ease.INOUTSINE), null);
        }
        playing = null;
    }

    void hitStop(int ticks) {
        if (playing != null && playing.isActive()) {
            clock.hold(ticks);
        }
    }

    /** Dev only: hold the current animation still, {@code fraction} into its current tick. */
    void freeze(double fraction) {
        clock.freeze(fraction);
    }

    /** The animation playing now (not one blending out), or null. */
    @Nullable CombatAnimationPlayer playing() {
        return playing != null && playing.isActive() ? playing : null;
    }

    boolean isMirrored() {
        return mirrored;
    }

    CombatClock clock() {
        return clock;
    }

    WalkingLegs legs() {
        return legs;
    }
}
