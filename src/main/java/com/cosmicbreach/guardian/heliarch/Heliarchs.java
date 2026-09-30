package com.cosmicbreach.guardian.heliarch;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.structure.sanctum.SanctumThrone;
import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.weather.EclipseSurge;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The Hollow Heliarch (G9a, GDD 7.3), wired up: its registrations and messages, the throne's summon
 * ({@link SanctumThrone#setSummoner}), the arena's rules, the player_down line, the seal's state for each player, the
 * arena put right after a fight the server cut short, and the debug commands. One fight at a time, on the Sanctum.
 * Client side: {@code client.guardian.heliarch.HeliarchClient}.
 */
public final class Heliarchs {
    private static final Map<ServerLevel, HollowHeliarch> ACTIVE = new WeakHashMap<>();
    private static int summons;
    private static int kills;

    private Heliarchs() {
    }

    public static void register(IEventBus modBus, IEventBus game) {
        HeliarchRegistry.register(modBus);
        modBus.addListener(RegisterPayloadHandlersEvent.class, HeliarchNet::register);
        installSummoner();
        HeliarchArenaRules.register(game);
        game.addListener(LivingDeathEvent.class, Heliarchs::onDeath);
        game.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer p) {
                syncSeal(p);
            }
        });
        game.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer p && event.getTo() == AetheriaWorld.LEVEL) {
                syncSeal(p);
            }
        });
        game.addListener(ServerTickEvent.Post.class, event -> onServerTick(event.getServer()));
        game.addListener(ServerStoppedEvent.class, event -> {
            synchronized (ACTIVE) {
                ACTIVE.clear();
            }
            HeliarchArenaRules.reset();
            summons = 0;
            kills = 0;
        });
        game.addListener(RegisterCommandsEvent.class, event -> HeliarchCommands.register(event.getDispatcher()));
    }

    /** The throne answers a Dying Star Heart with the Heliarch. */
    public static void installSummoner() {
        SanctumThrone.setSummoner((level, throne, player) -> {
            boolean ok = HollowHeliarch.summon(level, throne, player);
            if (ok) {
                summons++;
            }
            return ok;
        });
    }

    /** The fight in {@code level}, or null. */
    public static @Nullable HollowHeliarch active(Level level) {
        if (!(level instanceof ServerLevel server)) {
            return null;
        }
        synchronized (ACTIVE) {
            HollowHeliarch h = ACTIVE.get(server);
            if (h != null && (h.isRemoved() || !h.fighting())) {
                ACTIVE.remove(server);
                return null;
            }
            return h;
        }
    }

    static void started(HollowHeliarch h) {
        if (h.level() instanceof ServerLevel server) {
            synchronized (ACTIVE) {
                ACTIVE.put(server, h);
            }
        }
    }

    static void ended(HollowHeliarch h, boolean killed) {
        if (h.level() instanceof ServerLevel server) {
            synchronized (ACTIVE) {
                if (ACTIVE.get(server) == h) {
                    ACTIVE.remove(server);
                }
            }
        }
        if (killed) {
            kills++;
        }
    }

    /** "summons kills" since the server started (for checks). */
    public static String counters() {
        return summons + " " + kills;
    }

    private static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && p.level() instanceof ServerLevel level) {
            HollowHeliarch h = active(level);
            if (h != null && HeliarchArena.inFight(p.getX(), p.getY(), p.getZ())) {
                h.onPlayerDown(p);
            }
        }
    }

    private static void onServerTick(MinecraftServer server) {
        ServerLevel level = server.getLevel(AetheriaWorld.LEVEL);
        if (level == null || level.getGameTime() % 100 != 17 || active(level) != null) {
            return;
        }
        HeliarchData data = HeliarchData.get(level);
        if (data.dirty()) {
            int n = data.restore(level);
            CosmicBreach.LOGGER.debug("[cosmicbreach] an interrupted Heliarch fight's arena was put right ({} blocks)", n);
        }
        // a fight cut short without its end running (its chunks unloaded, or the server stopped): the Heart goes back
        if (data.summoner() != null) {
            returnHeart(level);
        }
    }

    /** Tells the one who set the Heart that it came back (the lang key). */
    public static final String HEART_BACK_KEY = "cosmicbreach.heliarch.heart_back";

    /**
     * A fight that ended without a kill: the Heart goes back to whoever set it on the throne, into their inventory now if
     * they are online and alive, else when they log in or respawn (GuardianPayouts). Once per fight: the record is cleared.
     */
    static void returnHeart(ServerLevel level) {
        HeliarchData data = HeliarchData.get(level);
        java.util.UUID summoner = data.summoner();
        if (summoner == null) {
            return;
        }
        data.clearSummoner();
        MinecraftServer server = level.getServer();
        com.cosmicbreach.guardian.GuardianPayouts.giveOrOwe(server, summoner,
                new net.minecraft.world.item.ItemStack(com.cosmicbreach.structure.sanctum.SanctumRegistry.DYING_STAR_HEART.get()));
        ServerPlayer p = server.getPlayerList().getPlayer(summoner);
        if (p != null && p.isAlive()) {
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable(HEART_BACK_KEY), true);
        }
        CosmicBreach.LOGGER.debug("[cosmicbreach] a summon ended without a kill: its item goes back to {}", summoner);
    }

    /** Tells {@code p} whether the Breach is sealed in this world. */
    public static void syncSeal(ServerPlayer p) {
        HeliarchNet.seal(p, EclipseSurge.heliarchFallen(p.server), false);
    }
}
