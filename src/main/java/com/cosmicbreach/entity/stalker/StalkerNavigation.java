package com.cosmicbreach.entity.stalker;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.jetbrains.annotations.Nullable;

/**
 * Shadow routing (GDD 7.1): walking paths priced by light. Every node the walker accepts is judged by the light where
 * the Stalker's feet would be ({@link StalkerLight}): block light of 12 or more is no node at all, lit ground (8 or
 * more) costs {@value StalkerRules#LIT_COST} times a step ({@link StalkerRules#pathMalus}), so it always comes at you
 * through the dark and never through a torch's circle. A target standing in bright light is reached as near as the
 * dark allows (the path finder's closest node).
 */
public class StalkerNavigation extends GroundPathNavigation {
    public StalkerNavigation(Mob mob, Level level) {
        super(mob, level);
        setMaxVisitedNodesMultiplier(3.0f); // light's prices make the search wider: room to find the dark way round
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new ShadowNodes();
        this.nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }

    /** What a bright node costs while the Stalker is walking out of bright light (otherwise it is no node at all). */
    static final float ESCAPE_MALUS = 20.0f;

    /** The walker, with light's prices on every node it accepts. */
    static final class ShadowNodes extends WalkNodeEvaluator {
        @Override
        protected @Nullable Node findAcceptedNode(int x, int y, int z, int verticalDeltaLimit, double nodeFloorLevel,
                                                  Direction direction, PathType pathType) {
            Node node = super.findAcceptedNode(x, y, z, verticalDeltaLimit, nodeFloorLevel, direction, pathType);
            if (node == null || node.costMalus < 0.0f) {
                return node;
            }
            BlockPos at = new BlockPos(node.x, node.y, node.z);
            Level level = mob.level();
            int block = StalkerLight.block(level, at);
            float base = mob.getPathfindingMalus(node.type);
            float malus = StalkerRules.pathMalus(StalkerLight.effective(level, at), block, base);
            if (malus < 0.0f) {
                if (!(mob instanceof HollowStalker s && s.escapingLight()) || base < 0.0f) {
                    return null;
                }
                malus = base + ESCAPE_MALUS; // already standing in bright light: the quickest way out, through it
            }
            node.costMalus = Math.max(node.costMalus, malus);
            return node;
        }
    }
}
