package com.cosmicbreach.onboarding;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;

/**
 * One player's way in (saved on the player, kept through death):
 *
 * @param nextFall    the Overworld day time their next Starfall is due, or -1 before their first is planned
 * @param firstFallen their first Starfall has fallen (later ones come nightly)
 * @param codexGiven  they have been given the Starfall Codex (on their first shard pickup, or a torn page)
 * @param codexOpened they have opened it once (the first opening goes to "Build a ring")
 * @param ring        the centre of the Overworld Breach they last fell up through: the way home lands beside it
 */
public record OnboardingState(long nextFall, boolean firstFallen, boolean codexGiven, boolean codexOpened, Optional<BlockPos> ring) {
    public static final OnboardingState NEW = new OnboardingState(-1L, false, false, false, Optional.empty());

    public static final Codec<OnboardingState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.optionalFieldOf("next_fall", -1L).forGetter(OnboardingState::nextFall),
            Codec.BOOL.optionalFieldOf("first_fallen", false).forGetter(OnboardingState::firstFallen),
            Codec.BOOL.optionalFieldOf("codex_given", false).forGetter(OnboardingState::codexGiven),
            Codec.BOOL.optionalFieldOf("codex_opened", false).forGetter(OnboardingState::codexOpened),
            BlockPos.CODEC.optionalFieldOf("ring").forGetter(OnboardingState::ring)
    ).apply(i, OnboardingState::new));

    public OnboardingState withNextFall(long time) {
        return new OnboardingState(time, firstFallen, codexGiven, codexOpened, ring);
    }

    public OnboardingState withFirstFallen() {
        return new OnboardingState(nextFall, true, codexGiven, codexOpened, ring);
    }

    public OnboardingState withCodexGiven() {
        return new OnboardingState(nextFall, firstFallen, true, codexOpened, ring);
    }

    public OnboardingState withCodexOpened() {
        return new OnboardingState(nextFall, firstFallen, codexGiven, true, ring);
    }

    public OnboardingState withRing(BlockPos centre) {
        return new OnboardingState(nextFall, firstFallen, codexGiven, codexOpened, Optional.of(centre.immutable()));
    }
}
