package com.cosmicbreach.gear.regalia;

import com.cosmicbreach.gear.GearSets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The Hymn of Alignment's rings on each side: the server's, and the ones the server told this client about (the
 * client draws them and predicts its player's Haste and Resonance with them). A ring is its level, centre, radius
 * and end (game time). Allies are players: every player inside a ring gets +30 Haste and double Resonance gain
 * (through the Regalia's engine hook, {@link #inside}); every enemy inside (a living thing that is neither a player
 * nor a player's pet) is Aligned, renewed every server tick it stays.
 */
public final class HymnRings {
    /** One ring. */
    public record Ring(ResourceKey<Level> level, Vec3 centre, double radius, long until, int ownerId) {
        public boolean contains(Vec3 feet) {
            return RegaliaRules.insideRing(feet.x - centre.x, feet.y - centre.y, feet.z - centre.z, radius);
        }
    }

    private static final List<Ring> SERVER = new CopyOnWriteArrayList<>();
    private static final List<Ring> CLIENT = new CopyOnWriteArrayList<>();

    private HymnRings() {
    }

    /** The rings on {@code level}'s side. */
    public static List<Ring> on(Level level) {
        return level.isClientSide() ? CLIENT : SERVER;
    }

    /** A new ring on {@code level}'s side (the server's when it is cast, a client's when it hears of it). */
    public static void add(Level level, Ring ring) {
        on(level).add(ring);
    }

    /** True while {@code player} stands inside a ring in its level (either side). */
    public static boolean inside(Player player) {
        List<Ring> rings = on(player.level());
        if (rings.isEmpty()) {
            return false;
        }
        long now = player.level().getGameTime();
        for (Ring ring : rings) {
            if (ring.level() == player.level().dimension() && now < ring.until() && ring.contains(player.position())) {
                return true;
            }
        }
        return false;
    }

    /** True for what a ring Aligns: alive, not a player, not a player's pet. */
    public static boolean enemy(LivingEntity entity) {
        return entity.isAlive() && !(entity instanceof Player) && !entity.isSpectator()
                && !(entity instanceof OwnableEntity owned && owned.getOwnerUUID() != null);
    }

    /** Everything a ring Aligns now. */
    public static List<LivingEntity> enemiesIn(Level level, Ring ring) {
        double r = ring.radius();
        AABB box = new AABB(ring.centre().x - r, ring.centre().y - RegaliaRules.HYMN_BELOW, ring.centre().z - r,
                ring.centre().x + r, ring.centre().y + RegaliaRules.HYMN_ABOVE, ring.centre().z + r);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, HymnRings::enemy)) {
            if (ring.contains(e.position())) {
                out.add(e);
            }
        }
        return out;
    }

    /** Every server tick: rings run out, and every enemy inside one is Aligned for a little longer. */
    public static void onServerTick(ServerTickEvent.Post event) {
        for (Ring ring : SERVER) {
            ServerLevel level = event.getServer().getLevel(ring.level());
            if (level == null || level.getGameTime() >= ring.until()) {
                SERVER.remove(ring);
                continue;
            }
            for (LivingEntity enemy : enemiesIn(level, ring)) {
                MobEffectInstance current = enemy.getEffect(GearSets.ALIGNED);
                if (current == null || current.getDuration() < RegaliaRules.ALIGNED_TICKS - 2) {
                    enemy.addEffect(new MobEffectInstance(GearSets.ALIGNED, RegaliaRules.ALIGNED_TICKS, 0, false, false, true));
                }
            }
        }
    }

    /** Client: drops rings that ran out (by the client level's clock). */
    public static void clientTick(Level level) {
        long now = level.getGameTime();
        CLIENT.removeIf(ring -> now >= ring.until() || ring.level() != level.dimension());
    }

    /** Client: forget every ring (leaving a world). */
    public static void clearClient() {
        CLIENT.clear();
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        SERVER.clear();
    }
}
