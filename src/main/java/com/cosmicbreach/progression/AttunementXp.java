package com.cosmicbreach.progression;

import com.cosmicbreach.progression.net.XpGainedPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToIntFunction;

/**
 * The Attunement API for the rest of the mod (server side). Amounts come from {@link XpSource}:
 *
 * <pre>{@code
 * AttunementXp.award(player, XpSource.STRUCTURE_FOUND.at(LayerTier.DRIFT));     // one player
 * AttunementXp.awardKill(gyreKnight, XpSource.ELITE.at(LayerTier.DRIFT));        // everyone who took part
 * AttunementXp.awardKill(colossus, p -> firstKill(p) ? 3_000 : 600);             // per player
 * AttunementXp.awardStatPoints(player, XpSource.GUARDIAN_FIRST_KILL_POINTS);    // points on top of levels
 * }</pre>
 *
 * A kill's participants are the players who damaged the victim (tracked automatically for every
 * creature players hurt) and every player within 24 blocks of it when it died; each gets the full
 * amount. Creatures with a fixed reward are simply registered in {@link KillRewards} instead.
 */
public final class AttunementXp {
    private AttunementXp() {
    }

    /** Gives {@code amount} Attunement XP. Returns how many levels it gained (their feedback plays). */
    public static int award(ServerPlayer player, int amount) {
        if (amount <= 0) {
            return 0;
        }
        Attunement before = Attunements.of(player);
        Attunement after = before.withXp(amount);
        if (after.equals(before)) {
            return 0;
        }
        Attunements.set(player, after);
        PacketDistributor.sendToPlayer(player, new XpGainedPayload(amount));
        return after.level() - before.level();
    }

    /** Gives {@code points} stat points on top of the ones levels give (guardians, the Heliarch). */
    public static void awardStatPoints(ServerPlayer player, int points) {
        if (points > 0) {
            Attunements.set(player, Attunements.of(player).withBonusPoints(points));
        }
    }

    /** Gives every participant of {@code victim}'s kill the full {@code amount}. Returns who got it. */
    public static Set<ServerPlayer> awardKill(LivingEntity victim, int amount) {
        return awardKill(victim, player -> amount);
    }

    /** Gives every participant of {@code victim}'s kill its own amount. Returns who took part. */
    public static Set<ServerPlayer> awardKill(LivingEntity victim, ToIntFunction<ServerPlayer> amountFor) {
        Set<ServerPlayer> participants = participants(victim);
        for (ServerPlayer player : participants) {
            award(player, amountFor.applyAsInt(player));
        }
        return participants;
    }

    /**
     * Who took part in killing {@code victim}: players still online who damaged it (wherever they
     * are), and living, non-spectating players in its level within {@link KillCredit#RADIUS} blocks.
     */
    public static Set<ServerPlayer> participants(LivingEntity victim) {
        if (!(victim.level() instanceof ServerLevel level)) {
            return Set.of();
        }
        MinecraftServer server = level.getServer();
        KillCredit credit = victim.getExistingDataOrNull(ProgressionRegistry.KILL_CREDIT);
        List<UUID> damagers = credit == null ? List.of() : List.copyOf(credit.damagers());
        List<KillCredit.Bystander> bystanders = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            bystanders.add(new KillCredit.Bystander(player.getUUID(), player.distanceTo(victim),
                    player.isAlive() && !player.isSpectator()));
        }
        Set<ServerPlayer> result = new LinkedHashSet<>();
        for (UUID id : KillCredit.participants(damagers, bystanders)) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                result.add(player);
            }
        }
        return result;
    }
}
