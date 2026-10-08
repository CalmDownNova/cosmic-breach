package com.cosmicbreach.mount;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.guardian.GuardianPayouts;
import com.cosmicbreach.world.AetheriaWorld;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import org.jetbrains.annotations.Nullable;

/**
 * A Stable Crystal's item lying in the world (quality review, Important 1, and its re-review): while it holds a mount its
 * item entity is protected from the moment it joins a level (invulnerable, never despawning; fire and lava are refused by the
 * item itself); if it falls out of the world, is killed or is destroyed the mount is not lost with it ({@link CrystalFate}):
 * the crystal goes to its owner, into their pack at once if they are online and alive, else owed to them (the ledger the
 * guardians' rewards use: {@link GuardianPayouts#giveOrOwe}) until they log in or respawn, which is what a player who dies
 * in the void needs, as their drops fall while they are on the death screen. The crystal's own click handling, stowing
 * and setting down, is {@link StableCrystalItem}.
 */
final class CrystalGuard {
    private CrystalGuard() {
    }

    /** Protects the item entity of a full crystal; true if it is one. */
    private static boolean protect(ItemEntity entity) {
        if (StableCrystalItem.stowed(entity.getItem()) == null) {
            return false;
        }
        entity.setInvulnerable(true); // fire and lava are refused by the item; this is a cactus, an explosion, anything else
        if (entity.getAge() != Short.MIN_VALUE) {
            entity.setUnlimitedLifetime(); // and it does not despawn
        }
        return true;
    }

    /** An item entity joins a level (spilled from a chest, dropped, loaded): protected from this very moment, before its first tick. */
    static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof ItemEntity item) {
            protect(item);
        }
    }

    /** Every tick of the item entity, from the item. True if the entity is gone and the tick is over. */
    static boolean onTick(ItemStack stack, ItemEntity entity) {
        StowedMount s = StableCrystalItem.stowed(stack);
        if (s == null || !(entity.level() instanceof ServerLevel level)) {
            return false;
        }
        protect(entity);
        if (CrystalFate.fellOut(entity.getY(), level.getMinBuildHeight())) {
            fallenOut(level, entity, s);
        }
        return entity.isRemoved();
    }

    /** The item fell out of the world, below the line where the world deletes items: to its owner, or held where it is. */
    private static void fallenOut(ServerLevel level, ItemEntity entity, StowedMount s) {
        UUID owner = s.ownerId();
        if (CrystalFate.whenFallen(owner != null) == CrystalFate.Fate.HAND_OVER) {
            handOver(level.getServer(), owner, entity.getItem().copy(), entity);
        } else {
            entity.setPos(entity.getX(), CrystalFate.holdHeight(level.getMinBuildHeight()), entity.getZ());
            entity.setDeltaMovement(Vec3.ZERO);
            entity.setNoGravity(true);
        }
    }

    /**
     * An item entity that was killed (a command). This trusts that nothing else uses {@code kill()} to take an item's stack:
     * vanilla and this mod empty the stack and {@code discard()} for pickups, hoppers and merges, which this ignores.
     */
    static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof ItemEntity item && event.getLevel() instanceof ServerLevel level
                && item.getRemovalReason() == Entity.RemovalReason.KILLED) {
            lost(level, item);
        }
    }

    /** An item entity destroyed by damage: the item's hook, just before the entity goes (a creative player's blow bypasses invulnerability). */
    static void destroyed(ItemEntity item) {
        if (item.level() instanceof ServerLevel level) {
            lost(level, item);
        }
    }

    /** An item entity that is going for good: its owner is given the crystal, or its mount is set down where it was, or it is owed. */
    private static void lost(ServerLevel level, ItemEntity item) {
        StowedMount s = StableCrystalItem.stowed(item.getItem());
        if (s == null) {
            return;
        }
        MinecraftServer server = level.getServer();
        UUID owner = s.ownerId();
        boolean ownerHere = ownerHere(server, owner);
        Vec3 spot = AetheriaWorld.is(level) ? s.safeSpot() : null;
        // an item lost under the world's floor sets nothing down there: the mount's own last safe spot, if it has one
        BlockPos near = !CrystalFate.fellOut(item.getY(), level.getMinBuildHeight()) ? item.blockPosition() : spot != null ? BlockPos.containing(spot) : null;
        StableCrystalItem.Placed placed = !ownerHere && near != null ? StableCrystalItem.place(level, s, near, 0f) : null;
        switch (CrystalFate.whenKilled(ownerHere, placed != null && placed.mount() != null, owner != null)) {
            case HAND_OVER -> handOver(server, owner, item.getItem().copy(), null);
            case FREE_HERE -> { }
            default -> CosmicBreach.LOGGER.warn("A Stable Crystal was lost at {} and its mount could not be set down ({}): it is lost",
                    item.position(), placed == null ? "no place to try" : placed.problem());
        }
    }

    /** True if the mount's owner is online and alive (a player on the death screen cannot take a crystal). */
    private static boolean ownerHere(MinecraftServer server, @Nullable UUID owner) {
        ServerPlayer player = owner == null ? null : server.getPlayerList().getPlayer(owner);
        return player != null && player.isAlive();
    }

    /**
     * The crystal goes to its owner: into their pack if they are about, else owed to them until they log in or respawn. The
     * stack is a copy and the item entity's own stack, if it is still there, is emptied first and the entity discarded: the
     * mount is handed over once, whatever happens next.
     */
    private static void handOver(MinecraftServer server, UUID owner, ItemStack crystal, @Nullable ItemEntity entity) {
        if (entity != null) {
            entity.setItem(ItemStack.EMPTY);
            entity.discard();
        }
        GuardianPayouts.giveOrOwe(server, owner, crystal);
        ServerPlayer here = server.getPlayerList().getPlayer(owner);
        if (here != null && here.isAlive()) {
            here.displayClientMessage(Component.translatable("cosmicbreach.stable.came_back"), true);
        }
    }
}
