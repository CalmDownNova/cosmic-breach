package com.cosmicbreach.guardian;

import com.cosmicbreach.CosmicBreach;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Who gets paid for a guardian kill, and when (multiplayer): a participant who is online and alive at the kill is paid at
 * once ({@link GuardianRewards#give}); one who is offline, or dead on the respawn screen, is owed the kill in
 * {@link OwedRewards} and paid the next time they log in or respawn, so a dropped connection or a death just before the
 * end never costs anyone their share (their first-kill rewards included). The same holds for items handed back
 * ({@link #giveOrOwe}). Server side.
 *
 * <p>Each guardian registers how it pays ({@link #register(GuardianType, RewardTable, Consumer)}) at mod construction, so
 * an owed kill can be paid after a restart.
 */
public final class GuardianPayouts {
    public static final String PAID_KEY = "cosmicbreach.guardian.owed_paid";

    private record Payout(GuardianType type, RewardTable table, @Nullable Consumer<ServerPlayer> firstKill) {
    }

    private static final Map<String, Payout> PAYOUTS = new ConcurrentHashMap<>();

    private GuardianPayouts() {
    }

    /**
     * How a kill of {@code type} pays: its reward table, and {@code firstKill} (or null) run for a player's first kill
     * after the table's rewards. One call per guardian, at mod construction.
     */
    public static void register(GuardianType type, RewardTable table, @Nullable Consumer<ServerPlayer> firstKill) {
        PAYOUTS.put(type.name(), new Payout(type, table, firstKill));
    }

    /** The login and respawn hooks that pay what is owed. */
    public static void register(IEventBus game) {
        game.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                settle(player);
            }
        });
        game.addListener(PlayerEvent.PlayerRespawnEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                settle(player);
            }
        });
    }

    /** True if {@code player} can be handed things right now: online (the given entity is theirs) and alive. */
    static boolean canReceive(@Nullable ServerPlayer player) {
        return player != null && player.isAlive() && !player.hasDisconnected();
    }

    /** One participant's share of a kill of {@code type}: paid now if they are online and alive, else owed until they are. */
    public static void payOrOwe(MinecraftServer server, UUID participant, GuardianType type) {
        ServerPlayer player = server.getPlayerList().getPlayer(participant);
        if (canReceive(player)) {
            pay(player, type.name());
            return;
        }
        OwedRewards.get(server).oweKill(participant, type.name());
        CosmicBreach.LOGGER.debug("[cosmicbreach] kill reward for {} held until they are back", participant);
    }

    /** Hands {@code stack} to {@code player}: into their inventory now if they are online and alive, else kept until they are. */
    public static void giveOrOwe(MinecraftServer server, UUID player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ServerPlayer p = server.getPlayerList().getPlayer(player);
        if (canReceive(p)) {
            GuardianRewards.deliver(p, stack);
            return;
        }
        Tag saved = stack.save(server.registryAccess());
        if (saved instanceof CompoundTag tag) {
            OwedRewards.get(server).oweItem(player, tag);
        }
        CosmicBreach.LOGGER.debug("[cosmicbreach] an item for {} held until they are back", player);
    }

    /** Pays everything owed to {@code player} if they are alive (login, respawn); a dead player waits for their respawn. */
    public static void settle(ServerPlayer player) {
        if (!player.isAlive()) {
            return;
        }
        OwedRewards owed = OwedRewards.get(player.server);
        UUID id = player.getUUID();
        if (!owed.owes(id)) {
            return;
        }
        List<String> kills = owed.takeKills(id);
        List<CompoundTag> items = owed.takeItems(id);
        for (String guardian : kills) {
            pay(player, guardian);
        }
        for (CompoundTag tag : items) {
            ItemStack.parse(player.server.registryAccess(), tag).ifPresent(stack -> GuardianRewards.deliver(player, stack));
        }
        player.displayClientMessage(Component.translatable(PAID_KEY), false);
    }

    private static void pay(ServerPlayer player, String guardian) {
        Payout payout = PAYOUTS.get(guardian);
        if (payout == null) {
            CosmicBreach.LOGGER.warn("[cosmicbreach] an owed reward names an encounter with no payout; skipped");
            return;
        }
        boolean first = !GuardianRewards.killedBefore(player, payout.type());
        RewardTable.Reward reward = payout.table().roll(first, player.getRandom()::nextDouble);
        List<ItemStack> given = GuardianRewards.give(player, payout.type(), reward);
        if (first && payout.firstKill() != null) {
            payout.firstKill().accept(player);
        }
        CosmicBreach.LOGGER.debug("[cosmicbreach] {} reward for {} ({} kill): {} XP, {} stat point(s), {}", guardian,
                player.getGameProfile().getName(), first ? "first" : "repeat", reward.xp(), reward.statPoints(), given);
    }
}
