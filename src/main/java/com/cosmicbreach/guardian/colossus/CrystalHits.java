package com.cosmicbreach.guardian.colossus;

import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatEvent;
import com.cosmicbreach.combat.core.MoveInstance;
import com.cosmicbreach.combat.server.CombatHooks;
import com.cosmicbreach.combat.server.HitResolver;
import com.cosmicbreach.guardian.ArenaRules;
import com.cosmicbreach.guardian.GuardianFights;
import com.cosmicbreach.guardian.GuardianRegistry;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Hits on crown crystals (Refraction: "any hit on a lit crystal turns it one step clockwise"). A weapon's swing
 * turns every lit crystal its hitbox reaches (a combat hook sees each active tick; one swing turns a crystal
 * once), a plain punch or tool on the crystal turns it, and so does a projectile, unless it was fired from
 * farther than the arena allows. The crystal only turns while its Refraction charges.
 */
public final class CrystalHits {
    private CrystalHits() {
    }

    public static void register(IEventBus game) {
        CombatHooks.register(new CombatHooks.Hook() {
            @Override
            public void onCombatEvent(ServerPlayer player, PlayerCombat combat, CombatEvent event) {
                if (event instanceof CombatEvent.ActiveTick active) {
                    swing(player, active.move());
                }
            }
        });
        game.addListener(PlayerInteractEvent.LeftClickBlock.class, CrystalHits::onLeftClick);
        game.addListener(ProjectileImpactEvent.class, CrystalHits::onProjectile);
    }

    private static void swing(ServerPlayer player, MoveInstance move) {
        GuardianFights.Fight fight = GuardianFights.at(player.level(), player.position());
        if (!(fight instanceof PrismColossus colossus) || colossus.arena() == null) {
            return;
        }
        CrownArena arena = colossus.arena();
        Vec3 origin = player.position().add(0, HitResolver.CHEST * player.getBbHeight(), 0);
        for (int k = 0; k < Refraction.CRYSTALS; k++) {
            if (move.def().hitbox().hits(origin, player.getYRot(), arena.crystalBox(k))) {
                turn((ServerLevel) player.level(), arena.crystalBase(k), player.getUUID(), move.serial());
            }
        }
    }

    private static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide() || event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) {
            return;
        }
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (state.is(GuardianRegistry.CROWN_CRYSTAL.get()) && event.getLevel() instanceof ServerLevel level) {
            turn(level, CrownCrystalBlock.controllerOf(event.getPos(), state), event.getEntity().getUUID(), -1);
        }
    }

    private static void onProjectile(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (projectile.level().isClientSide() || event.getRayTraceResult().getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos pos = ((BlockHitResult) event.getRayTraceResult()).getBlockPos();
        BlockState state = projectile.level().getBlockState(pos);
        if (!state.is(GuardianRegistry.CROWN_CRYSTAL.get()) || !(projectile.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos base = CrownCrystalBlock.controllerOf(pos, state);
        if (level.getBlockEntity(base) instanceof CrownCrystalBlockEntity crystal
                && ArenaRules.burnsUp(projectile, Vec3.atCenterOf(crystal.arenaCentre()), PrismColossus.PROJECTILE_RANGE)) {
            event.setCanceled(true);
            return;
        }
        Entity owner = projectile.getOwner();
        turn(level, base, owner == null ? null : owner.getUUID(), -1);
    }

    /** A hit on the crystal whose controller is at {@code base}. True if it turned. */
    public static boolean turn(ServerLevel level, BlockPos base, @Nullable UUID attacker, int serial) {
        if (!(level.getBlockEntity(base) instanceof CrownCrystalBlockEntity crystal)) {
            return false;
        }
        if (!crystal.tryTurn(level.getGameTime(), attacker, serial)) {
            return false;
        }
        Vec3 at = Vec3.atCenterOf(base).add(0.5, 1.5, 0.5);
        level.playSound(null, at.x, at.y, at.z, GuardianRegistry.COLOSSUS_CRYSTAL_TURN.get(), SoundSource.HOSTILE, 1.5f, 1.0f);
        if (crystal.owner() != null && level.getEntity(crystal.owner()) instanceof PrismColossus colossus) {
            colossus.onCrystalTurned(crystal.index());
        }
        return true;
    }
}
