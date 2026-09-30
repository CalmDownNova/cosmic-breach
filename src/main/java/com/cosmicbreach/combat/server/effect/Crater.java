package com.cosmicbreach.combat.server.effect;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.combat.data.HitShape;
import com.cosmicbreach.combat.server.HitResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The Impact Crater's aftermath (GDD 4.2): where the charged slam lands ({@code offset} blocks ahead of the
 * player, on its first active tick, or at the feet of a plunge that carries it), the ground stays Cratered
 * for {@code ticks}: every enemy standing in it (on the ground, within {@code radius}) moves {@code slow}
 * slower, through a movement speed modifier that goes as soon as it steps out or the crater closes. The
 * player who made it is never slowed; other players only where PvP lets them be hit.
 *
 * <p>"30% slower" is the speed things really move at. A player's walk is linear in its movement speed
 * attribute, but a mob's is not: its move control feeds the attribute into both the forward input and the
 * friction-scaled acceleration ({@code Mob.setSpeed}), so its ground speed goes with the attribute squared,
 * and an attribute cut of 30% would slow it by 51%. Mobs get the square root instead ({@link #attributeFactor}).
 *
 * <p>Tells the clients {@link #OPENED} (at the centre, value the radius, ticks the duration) for the decal.
 */
public final class Crater implements ServerMoveEffect {
    public static final ResourceLocation ID = CosmicBreach.id("crater");
    public static final int OPENED = 0;
    public static final ResourceLocation SLOW_ID = CosmicBreach.id("cratered");

    public static final double RADIUS = 4.0;
    public static final int TICKS = 60;
    public static final double SLOW = 0.3;
    public static final double OFFSET = 1.0;
    /** Feet this far above or below the crater's floor still stand in it. */
    private static final double FLOOR_BAND = 1.25;

    /** One open crater. */
    private record Zone(ServerLevel level, Vec3 centre, double radius, double slow, long until, UUID owner) {
        boolean holds(LivingEntity e) {
            if (!e.onGround() || Math.abs(e.getY() - centre.y) > FLOOR_BAND) {
                return false;
            }
            double dx = e.getX() - centre.x;
            double dz = e.getZ() - centre.z;
            return dx * dx + dz * dz <= radius * radius;
        }
    }

    private static final List<Zone> ZONES = new ArrayList<>();
    /** Everything slowed by a crater last tick, so it can be let go. */
    private static final Set<LivingEntity> SLOWED = new HashSet<>();

    @Override
    public void activeTick(Use use, int activeTick) {
        if (activeTick == 0) {
            ServerPlayer player = use.player();
            open(use, player.position().add(HitShape.forward(player.getYRot()).scale(use.param("offset", OFFSET))));
        }
    }

    @Override
    public void landed(Use use, double fallBlocks) {
        open(use, use.player().position());
    }

    private static void open(Use use, Vec3 at) {
        double radius = use.param("radius", RADIUS);
        int ticks = use.intParam("ticks", TICKS);
        Vec3 centre = new Vec3(at.x, floorBelow(use.level(), at), at.z);
        ZONES.add(new Zone(use.level(), centre, radius, use.param("slow", SLOW), use.now() + ticks, use.player().getUUID()));
        use.send(OPENED, centre, (float) radius, ticks);
    }

    /** The top of the ground under {@code at} (within 3 blocks down), else its own height. */
    private static double floorBelow(ServerLevel level, Vec3 at) {
        BlockPos pos = BlockPos.containing(at.x, at.y + 0.2, at.z);
        for (int i = 0; i < 4; i++, pos = pos.below()) {
            if (!level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()) {
                return pos.getY();
            }
        }
        return at.y;
    }

    /** Every server tick: slow what stands in a crater, free what left one, close the old ones. */
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ZONES.isEmpty() && SLOWED.isEmpty()) {
            return;
        }
        Set<LivingEntity> now = new HashSet<>();
        Iterator<Zone> it = ZONES.iterator();
        while (it.hasNext()) {
            Zone zone = it.next();
            if (zone.level().getGameTime() >= zone.until()) {
                it.remove();
                continue;
            }
            Player owner = zone.level().getPlayerByUUID(zone.owner());
            AABB area = new AABB(zone.centre(), zone.centre()).inflate(zone.radius(), FLOOR_BAND + 1.0, zone.radius());
            for (LivingEntity e : zone.level().getEntitiesOfClass(LivingEntity.class, area, LivingEntity::isAlive)) {
                if (e.getUUID().equals(zone.owner()) || !zone.holds(e) || !isEnemy(owner, e)) {
                    continue;
                }
                if (now.add(e)) {
                    slow(e, zone.slow());
                }
            }
        }
        for (LivingEntity e : SLOWED) {
            if (!now.contains(e)) {
                release(e);
            }
        }
        SLOWED.clear();
        SLOWED.addAll(now);
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        SLOWED.forEach(Crater::release);
        SLOWED.clear();
        ZONES.clear();
    }

    /** True while {@code e} stands slowed in a crater (for tests). */
    public static boolean isSlowed(LivingEntity e) {
        AttributeInstance speed = e.getAttribute(Attributes.MOVEMENT_SPEED);
        return speed != null && speed.hasModifier(SLOW_ID);
    }

    /** Open craters (for tests). */
    public static int openCount() {
        return ZONES.size();
    }

    /** The centres of the open craters, oldest first (for tests). */
    public static List<Vec3> centres() {
        return ZONES.stream().map(Zone::centre).toList();
    }

    /** What the crater's maker could hit; with the maker gone, any mob. */
    private static boolean isEnemy(@Nullable Player owner, LivingEntity e) {
        if (owner instanceof ServerPlayer maker) {
            return HitResolver.isValidTarget(maker, e);
        }
        return !(e instanceof Player);
    }

    /**
     * The movement speed attribute's multiplier that makes something move {@code slow} slower: {@code 1 - slow}
     * for a walk linear in the attribute (a player), its square root for one that goes with the square (a mob).
     */
    public static double attributeFactor(double slow, boolean speedSquared) {
        double keep = Math.max(0.0, Math.min(1.0, 1.0 - slow));
        return speedSquared ? Math.sqrt(keep) : keep;
    }

    private static void slow(LivingEntity e, double slow) {
        AttributeInstance speed = e.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && !speed.hasModifier(SLOW_ID)) {
            double factor = attributeFactor(slow, !(e instanceof Player));
            speed.addOrUpdateTransientModifier(new AttributeModifier(SLOW_ID, factor - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static void release(LivingEntity e) {
        AttributeInstance speed = e.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SLOW_ID);
        }
    }
}
