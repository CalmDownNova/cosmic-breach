package com.cosmicbreach.lift;

import com.cosmicbreach.guardian.GuardianLairs;
import com.cosmicbreach.guardian.GuardianTypes;
import com.cosmicbreach.guardian.leviathan.RiftLayout;
import com.cosmicbreach.world.AetheriaWorld;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The rescue lift for things (playtest 3): an item dropped, thrown or spilled off a platform in a Leviathan Rift used to
 * fall about 50 blocks onto the bowl, where a player can only follow by holding sneak the whole way down (the lift catches
 * everyone else), so to a player it was lost. Now an item that sinks {@link RiftLift#CATCH_BELOW} blocks under the nearest
 * platform's standing height (the line the lift catches players at: nothing under it is reachable on foot) is set down
 * on that platform's outer landing (the same spot the lift sets players on), with a soft chime.
 */
public final class RiftItemReturn {
    /** How often the Rifts are swept for sinking items. */
    public static final int EVERY = 10;
    /** Rifts with no player this close (level) are not swept: nobody is there to drop anything. */
    private static final double NEAR = 160.0;

    private RiftItemReturn() {
    }

    public static void register(IEventBus game) {
        game.addListener(LevelTickEvent.Post.class, event -> {
            if (event.getLevel() instanceof ServerLevel level && AetheriaWorld.is(level) && level.getGameTime() % EVERY == 0) {
                sweep(level);
            }
        });
    }

    /**
     * Pure: where an item at {@code pos} in Rift {@code l} goes back to, or null to leave it (outside the sphere, or above
     * the line).
     */
    public static @Nullable Vec3 returnTo(RiftLayout l, Vec3 pos) {
        if (!l.inside(pos)) {
            return null;
        }
        RiftLayout.Platform nearest = l.nearestPlatform(RiftLift.angle(l, pos));
        double below = RiftLift.standY(nearest) - pos.y;
        if (below <= RiftLift.CATCH_BELOW) {
            return null;
        }
        return RiftLift.landing(l, new RiftLift.Ride(nearest.index(), true, 1)).add(0.0, 0.25, 0.0);
    }

    private static void sweep(ServerLevel level) {
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) {
            return;
        }
        for (GuardianLairs.Lair lair : GuardianLairs.get(level).all().values()) {
            if (!lair.guardian().equals(GuardianTypes.LEVIATHAN.name())) {
                continue;
            }
            BlockPos c = lair.arenaCentre();
            boolean someone = false;
            for (ServerPlayer p : players) {
                if (Math.hypot(p.getX() - c.getX() - 0.5, p.getZ() - c.getZ() - 0.5) <= NEAR) {
                    someone = true;
                    break;
                }
            }
            if (!someone) {
                continue;
            }
            RiftLayout l = Lifts.layout(level, c);
            AABB box = new AABB(c.getX() - RiftLayout.RX, c.getY() - RiftLayout.RY, c.getZ() - RiftLayout.RX,
                    c.getX() + RiftLayout.RX + 1, c.getY() + RiftLayout.RY + 1, c.getZ() + RiftLayout.RX + 1);
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box, e -> e.isAlive() && !e.getItem().isEmpty())) {
                Vec3 to = returnTo(l, item.position());
                if (to == null) {
                    continue;
                }
                item.teleportTo(to.x, to.y, to.z);
                item.setDeltaMovement(Vec3.ZERO);
                item.resetFallDistance();
                level.sendParticles(ParticleTypes.END_ROD, to.x, to.y + 0.3, to.z, 6, 0.2, 0.3, 0.2, 0.02);
                level.playSound(null, to.x, to.y, to.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.6f, 1.4f);
            }
        }
    }
}
