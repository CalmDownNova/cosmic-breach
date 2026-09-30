package com.cosmicbreach.world;

import com.cosmicbreach.entity.shardling.Shardling;
import com.cosmicbreach.registry.ModBlocks;
import com.cosmicbreach.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * The rules that run in Aetheria, server side: Shear bands ({@link ShearBands}), Drift gravity on living
 * entities ({@link AetheriaGravity}), natural Shardling packs in the Shattered Spires, the night skip
 * reaching the Overworld's clock (Aetheria shares it), and the debug commands.
 */
public final class AetheriaRules {
    /** Shardlings spawn as packs of up to 3; a pack keeps others this far away. */
    static final double PACK_SPACING = 64.0;
    /** A new pack starts on one spawn attempt in this many (members joining a starting pack always may). */
    static final int NEW_PACK_ODDS = 3;

    private AetheriaRules() {
    }

    static void register(IEventBus modBus, IEventBus game) {
        modBus.addListener(RegisterSpawnPlacementsEvent.class, AetheriaRules::onSpawnPlacements);
        game.addListener(EntityTickEvent.Post.class, AetheriaRules::onEntityTick);
        game.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, event -> leftOrArrived(event.getEntity()));
        game.addListener(PlayerEvent.PlayerRespawnEvent.class, event -> leftOrArrived(event.getEntity()));
        game.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> ShearBands.forget(event.getEntity()));
        game.addListener(ServerStartingEvent.class, event -> ShearBands.resetCounters());
        game.addListener(ServerStoppedEvent.class, event -> ShearBands.resetCounters());
        game.addListener(MobSpawnEvent.PositionCheck.class, AetheriaRules::onPositionCheck);
        game.addListener(SleepFinishedTimeEvent.class, AetheriaRules::onSleepFinished);
        game.addListener(RegisterCommandsEvent.class, event -> WorldCommands.register(event.getDispatcher()));
    }

    private static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide || !AetheriaWorld.is(entity.level())) {
            return;
        }
        if (entity instanceof LivingEntity living) {
            AetheriaGravity.update(living);
        }
        if (entity instanceof ServerPlayer player) {
            ShearBands.onPlayerTick(player);
        } else if (entity instanceof Mob mob) {
            ShearBands.onMobTick(mob);
        }
    }

    private static void leftOrArrived(net.minecraft.world.entity.player.Player player) {
        if (!AetheriaWorld.is(player.level())) {
            AetheriaGravity.clear(player);
            ShearBands.forget(player);
        }
    }

    // ------------------------------------------------------------------ Shardling packs

    private static void onSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(ModEntities.SHARDLING.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                AetheriaRules::canShardlingSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    /**
     * Natural Shardling spawns: on Starfall Stone or Glimmer Grass in any light (the Reach is daylight), not on
     * Peaceful, and only where no other pack roams: at most three (one pack, spawned together) within 64
     * blocks, and none of them farther than 14 from this spot. A new pack starts on one attempt in three, so
     * packs trickle in rather than appear all at once. Other spawn reasons (eggs, spawners, commands) follow
     * the usual monster rules.
     */
    public static boolean canShardlingSpawn(EntityType<Shardling> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            return false;
        }
        if (reason != MobSpawnType.NATURAL && reason != MobSpawnType.CHUNK_GENERATION) {
            return Monster.checkAnyLightMonsterSpawnRules(type, level, reason, pos, random);
        }
        BlockState ground = level.getBlockState(pos.below());
        if (!ground.is(ModBlocks.STARFALL_STONE.get()) && !ground.is(ModBlocks.GLIMMER_GRASS.get())) {
            return false;
        }
        int near = 0;
        for (Shardling other : level.getEntitiesOfClass(Shardling.class, new AABB(pos).inflate(PACK_SPACING, 24, PACK_SPACING))) {
            if (other.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) > 14 * 14) {
                return false;
            }
            near++;
        }
        return near < 3 && (near > 0 || random.nextInt(NEW_PACK_ODDS) == 0);
    }

    /** Monsters normally only spawn where it is dark; in Aetheria the Shardling hunts in daylight. */
    private static void onPositionCheck(MobSpawnEvent.PositionCheck event) {
        Mob mob = event.getEntity();
        if (event.getSpawnType() == MobSpawnType.NATURAL && mob instanceof Shardling
                && event.getLevel() instanceof ServerLevel level && AetheriaWorld.is(level)) {
            event.setResult(mob.checkSpawnObstruction(level) ? MobSpawnEvent.PositionCheck.Result.SUCCEED
                    : MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }

    // ------------------------------------------------------------------ the shared clock

    /** Sleeping through the eclipse in Aetheria moves the Overworld's clock (Aetheria's own time is derived). */
    private static void onSleepFinished(SleepFinishedTimeEvent event) {
        if (event.getLevel() instanceof ServerLevel level && AetheriaWorld.is(level)) {
            level.getServer().overworld().setDayTime(event.getNewTime());
        }
    }
}
