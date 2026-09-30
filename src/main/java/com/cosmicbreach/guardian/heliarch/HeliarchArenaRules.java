package com.cosmicbreach.guardian.heliarch;

import com.cosmicbreach.structure.sanctum.SanctumLayout;
import com.cosmicbreach.structure.sanctum.Sanctums;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * The Sanctum's rules while the Heliarch fights (GDD 7.3): nothing can be placed within
 * {@value HeliarchMoves#NO_PLACE_RADIUS} blocks of the throne, blocks placed there before the fight are cleared at the
 * summon, and Ender Pearls don't work inside the arena. (Projectiles fired from more than 40 blocks away burn up in the
 * Heliarch's own hit checks, through the guardians' {@code ArenaRules}.) Server side.
 */
public final class HeliarchArenaRules {
    private static int refused;
    private static int pearls;

    private HeliarchArenaRules() {
    }

    public static void register(IEventBus game) {
        game.addListener(EventPriority.HIGH, BlockEvent.EntityPlaceEvent.class, HeliarchArenaRules::onPlace);
        game.addListener(EventPriority.HIGH, EntityTeleportEvent.EnderPearl.class, HeliarchArenaRules::onPearl);
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Player player)) {
            return;
        }
        HollowHeliarch h = Heliarchs.active(level);
        BlockPos pos = event.getPos();
        if (h != null && near(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5)) {
            event.setCanceled(true);
            refused++;
            if (player instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("cosmicbreach.heliarch.no_place"), true);
                // the client took the block from its stack when it predicted the placing; the server's stack never changed,
                // so nothing would tell it otherwise
                sp.containerMenu.sendAllDataToRemote();
            }
        }
    }

    private static void onPearl(EntityTeleportEvent.EnderPearl event) {
        ServerPlayer player = event.getPlayer();
        if (player == null || !(player.level() instanceof ServerLevel level) || Heliarchs.active(level) == null) {
            return;
        }
        boolean from = HeliarchArena.inFight(player.getX(), player.getY(), player.getZ());
        boolean to = HeliarchArena.inFight(event.getTargetX(), event.getTargetY(), event.getTargetZ());
        if (from || to) {
            event.setCanceled(true);
            pearls++;
            player.displayClientMessage(Component.translatable("cosmicbreach.heliarch.no_pearl"), true);
        }
    }

    /** True within the no-placing radius of the throne, from well under the floor to high over it. */
    public static boolean near(double x, double y, double z) {
        return HeliarchArena.radiusOf(x, z) <= HeliarchMoves.NO_PLACE_RADIUS && y >= HeliarchArena.FLOOR - 24 && y <= HeliarchArena.FLOOR + 48;
    }

    /**
     * Clears every block within {@value HeliarchMoves#NO_PLACE_RADIUS} of the throne, over the floor, where the Sanctum
     * itself has open air (dropping it as an item, so nothing is lost). Returns how many went.
     */
    public static int clearPlaced(ServerLevel level) {
        SanctumLayout layout = Sanctums.layout(level);
        int r = (int) Math.ceil(HeliarchMoves.NO_PLACE_RADIUS);
        int cleared = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (x * x + z * z > r * r) {
                    continue;
                }
                for (int y = (int) HeliarchArena.FLOOR; y <= HeliarchArena.FLOOR + 30; y++) {
                    p.set(x, y, z);
                    if (!level.isLoaded(p)) {
                        continue;
                    }
                    BlockState s = level.getBlockState(p);
                    if (s.isAir() || s.is(HeliarchRegistry.RELIQUARY.get())) {
                        continue;
                    }
                    if (layout.kind(x, y, z) == SanctumLayout.Kind.AIR) {
                        level.destroyBlock(p.immutable(), true);
                        cleared++;
                    }
                }
            }
        }
        return cleared;
    }

    /** "refused pearls" since the server started (for checks). */
    public static String counters() {
        return refused + " " + pearls;
    }

    public static void reset() {
        refused = 0;
        pearls = 0;
    }
}
