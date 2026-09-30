package com.cosmicbreach.entity.shardling;

import com.cosmicbreach.entity.shardling.ShardlingPack.Attack;
import com.cosmicbreach.entity.shardling.ShardlingPack.Order;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * A Shardling follows its pack's orders while the pack hunts: it goes to the spot the last plan gave it
 * at the plan's pace, watches the target, and hands a granted attack to the Shardling to start. During
 * an attack or a stagger it holds still; the Shardling's own timeline moves it then. Holds the move
 * and look controls, so the idle goals below it (wandering, looking round) only run while there is
 * nothing to hunt.
 *
 * <p>It stops on its spot, not near it: vanilla's {@code moveTo(x, y, z, speed)} paths to within one
 * block of the target and then halts at that block's centre, which left the Taunter a block or more
 * short of its band (at 3.5 blocks, coming back from the player) and Flankers inside their 4 to 5. So
 * it paths exactly (accuracy 0), walks the last stretch straight to the point at a speed that falls
 * off as it arrives, and holds once there until the spot moves away.
 */
final class PackHuntGoal extends Goal {
    /** Within this of its spot it stops and holds... */
    private static final double SETTLED = 0.25;
    /** ...until the spot is this far away again (the player moved, or was knocked back by a hit). */
    private static final double DRIFT = 0.45;
    /** Within this of its spot, with nothing in the way, it walks straight there instead of pathfinding. */
    private static final double DIRECT = 2.5;
    /** On that last stretch its speed is this much per block still to go, at least {@value #SLOWEST}. */
    private static final double SLOW_PER_BLOCK = 0.6;
    private static final double SLOWEST = 0.35;
    /** Re-path when the spot moved at least this much, or after {@value #REPATH_TICKS} ticks. */
    private static final double MOVED = 0.6;
    private static final int REPATH_TICKS = 10;

    private final Shardling shardling;
    private double pathX = Double.NaN;
    private double pathZ = Double.NaN;
    private int sincePath;
    private boolean holding;

    PackHuntGoal(Shardling shardling) {
        this.shardling = shardling;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        return shardling.huntTarget() != null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        shardling.getNavigation().stop();
        pathX = Double.NaN;
        holding = false;
    }

    @Override
    public void tick() {
        Player target = shardling.huntTarget();
        ShardlingPack pack = shardling.pack();
        if (target == null || pack == null) {
            return;
        }
        if (!shardling.moves().idle()) {
            pathX = Double.NaN; // the attack or stagger owns the body
            holding = false;
            return;
        }
        shardling.getLookControl().setLookAt(target, 30.0f, 30.0f);
        Attack attack = pack.takeAttack(shardling.getId());
        if (attack != Attack.NONE) {
            shardling.getNavigation().stop();
            pathX = Double.NaN;
            holding = false;
            shardling.attackWhenFree(attack);
            return;
        }
        Order order = pack.order(shardling.getId());
        if (order == null) {
            return;
        }
        double away = Math.hypot(order.x() - shardling.getX(), order.z() - shardling.getZ());
        if (away < SETTLED || (holding && away < DRIFT)) {
            holding = true;
            shardling.getNavigation().stop();
            pathX = Double.NaN;
            return;
        }
        holding = false;
        double speed = switch (order.pace()) {
            case DART -> Shardling.DART;
            case RUN -> Shardling.RUN;
            case WALK -> Shardling.WALK;
        };
        if (away <= DIRECT && clearWalk(order)) {
            shardling.getNavigation().stop();
            pathX = Double.NaN;
            double slowing = Math.max(SLOWEST, away * SLOW_PER_BLOCK);
            shardling.getMoveControl().setWantedPosition(order.x(), shardling.getY(), order.z(), Math.min(speed, slowing));
            return;
        }
        sincePath++;
        boolean moved = Double.isNaN(pathX) || Math.hypot(order.x() - pathX, order.z() - pathZ) > MOVED;
        if (moved || sincePath >= REPATH_TICKS || (shardling.getNavigation().isDone() && sincePath >= 3)) {
            Path path = shardling.getNavigation().createPath(order.x(), shardling.getY(), order.z(), 0);
            shardling.getNavigation().moveTo(path, speed);
            pathX = order.x();
            pathZ = order.z();
            sincePath = 0;
        }
    }

    /** Nothing solid between the Shardling and the spot at knee height (a pillar, the rim). */
    private boolean clearWalk(Order order) {
        Vec3 from = shardling.position().add(0.0, 0.4, 0.0);
        Vec3 to = new Vec3(order.x(), from.y, order.z());
        return shardling.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shardling))
                .getType() == HitResult.Type.MISS;
    }
}
