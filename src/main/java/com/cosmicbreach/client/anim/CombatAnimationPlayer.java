package com.cosmicbreach.client.anim;

import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One play of one animation on one player, with its {@link AnimationStyle}'s first person. Only the
 * player the camera looks out of has a first person: for everyone else this reports the animated model,
 * because playerAnimator's first-person pass also filters the layers of every other player drawn
 * before the camera's own, and a remote player's charge hold would otherwise vanish for those frames.
 */
final class CombatAnimationPlayer extends KeyframeAnimationPlayer {
    /** Dev only: every animation's first person, instead of its style's (null: the style's). */
    static @Nullable AnimationStyle firstPersonOverride;

    private final AbstractClientPlayer player;
    private final ResourceLocation id;
    private final AnimationStyle style;

    CombatAnimationPlayer(AbstractClientPlayer player, ResourceLocation id, KeyframeAnimation data, int startTick) {
        super(data, startTick);
        this.player = player;
        this.id = id;
        this.style = AnimationStyle.of(id);
        setFirstPersonConfiguration(style.firstPersonConfig());
    }

    ResourceLocation id() {
        return id;
    }

    AnimationStyle style() {
        return style;
    }

    @Override
    public @NotNull FirstPersonMode getFirstPersonMode(float tickDelta) {
        if (Minecraft.getInstance().getCameraEntity() != player) {
            return FirstPersonMode.THIRD_PERSON_MODEL;
        }
        return firstPersonStyle().firstPerson();
    }

    @Override
    public @NotNull FirstPersonConfiguration getFirstPersonConfiguration(float tickDelta) {
        return firstPersonStyle().firstPersonConfig();
    }

    private AnimationStyle firstPersonStyle() {
        AnimationStyle override = firstPersonOverride;
        return override != null ? override : style;
    }
}
