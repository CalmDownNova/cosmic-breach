package com.cosmicbreach.voice.boss;

import com.cosmicbreach.guardian.GuardianPart;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * A guardian that speaks (1.1): what its voice ({@link BossVoices}) reads from it each tick of a fight. Implemented by the
 * four bosses next to their own state. A boss fires what only it knows (its opener, its phases, its kill, its events)
 * through {@link BossVoices#fire} and {@link BossVoices#event} on the tick the script places them.
 */
public interface VoicedBoss {
    /** The boss as an entity (the key its watched sounds are kept under). */
    default Entity voiceEntity() {
        return (Entity) this;
    }

    /** Its catalog: colossus, leviathan, unsung or heliarch. */
    String voiceBoss();

    /** Its lair's centre: the key for line cooldowns that outlive one fight. */
    BlockPos voiceHome();

    /** The fair players fighting it now (inside its arena). */
    List<ServerPlayer> voiceFighters();

    /** Players in the fight when it woke (its health scaling's count). */
    int voicePlayers();

    /** Its health this fight: 1 full, 0 at the kill. */
    double voiceHealth();

    /** The advancement a first kill gives (a fighter holding it makes the party a returning one). */
    ResourceLocation voiceKillAdvancement();

    /** True if a Guardian Echo woke it (a returning party too). */
    default boolean voiceWokenByEcho() {
        return false;
    }

    /** The hp_threshold percents it fires itself, at its phases; the voice watches the others. */
    default Set<Integer> voicePhaseThresholds() {
        return Set.of();
    }

    /** Ticks from now with nothing its voice must not cover ({@link VoiceDirector.Gate#quietTicks}). */
    default int voiceQuietTicks(long now) {
        return Integer.MAX_VALUE;
    }

    /** Quiet ticks wanted after a line's last word before the thing it must not cover ({@link VoiceDirector.Gate#marginTicks}). */
    default int voiceMarginTicks() {
        return VoiceDirector.QUIET_MARGIN;
    }

    /** True if a line may start on this tick ({@link VoiceDirector.Gate#mayStart}). */
    default boolean voiceMayStart(long now) {
        return true;
    }

    /** The living masks as a variant key ({@code "13"}), or {@link VoiceLine#ALL}. */
    default String voiceLiving() {
        return VoiceLine.ALL;
    }

    /** Masks still whole, or 0 for a boss without masks. */
    default int voiceMasks() {
        return 0;
    }

    /** True if a hit on {@code hit} is a hit on it: itself or one of its parts (a mask, a shard). */
    default boolean voiceOwns(Entity hit) {
        return hit == this || hit instanceof GuardianPart part && part.owner() == this;
    }

    /** True if {@code player} has fallen from the fight's footing (the lift catches them). */
    default boolean voiceFell(ServerPlayer player) {
        return false;
    }

    /** True if its target is out of its reach for now ({@code target_far}). */
    default boolean voiceTargetFar() {
        return false;
    }
}
