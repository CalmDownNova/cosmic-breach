package com.cosmicbreach.world.light;

import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatEvent;
import com.cosmicbreach.combat.data.HitShape;
import com.cosmicbreach.combat.data.MoveDef;
import com.cosmicbreach.combat.data.MoveEffect;
import com.cosmicbreach.combat.data.MoveKind;
import com.cosmicbreach.combat.server.CombatHooks;
import com.cosmicbreach.combat.server.HitResolver;
import com.cosmicbreach.combat.server.effect.GravityWell;
import com.cosmicbreach.combat.server.effect.ServerMoveEffects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * GDD 7.1: "every weapon ability leaves light 12 behind for 40 ticks where it hits". Where an ability's hit lands on
 * an enemy, and where the ability itself strikes: the middle of its own hitbox on its first active tick (Zenith's arc),
 * or the well a Gravity Well collapses at. The light is {@link TempLights}' real block light, so a Hollow Stalker
 * struck by an ability stands in light it won't stay in.
 */
public final class AbilityLight {
    public static final int LEVEL = 12;
    public static final int TICKS = 40;

    private AbilityLight() {
    }

    public static void register() {
        HitResolver.onStrike((player, move, target, point) -> {
            if (move.def().kind() == MoveKind.ABILITY) {
                at(player.serverLevel(), target.position().add(0, 0.2, 0));
            }
        });
        CombatHooks.register(new CombatHooks.Hook() {
            @Override
            public void onCombatEvent(ServerPlayer player, PlayerCombat combat, CombatEvent event) {
                if (event instanceof CombatEvent.ActiveTick active && active.move().def().kind() == MoveKind.ABILITY) {
                    struck(player, active.move().def(), active.activeTick());
                }
            }
        });
    }

    /** Light {@value #LEVEL} for {@value #TICKS} ticks at {@code point} (the cell there, or the one above). */
    public static void at(ServerLevel level, Vec3 point) {
        TempLights.place(level, BlockPos.containing(point), LEVEL, TICKS);
    }

    private static void struck(ServerPlayer player, MoveDef def, int activeTick) {
        Optional<MoveEffect> well = def.traits().effect(GravityWell.ID);
        if (well.isPresent()) {
            if (activeTick == well.get().intParam("pull_ticks", GravityWell.PULL_TICKS)) {
                at(player.serverLevel(), GravityWell.centre(player.position(), player.getYRot(), well.get().param("offset",
                        GravityWell.OFFSET)).add(0, 0.2, 0));
            }
            return;
        }
        if (activeTick == 0 && !ServerMoveEffects.dealsItsOwnHits(def)) {
            at(player.serverLevel(), focal(player, def.hitbox()));
        }
    }

    /** The middle of a move's own hitbox, at the feet's height: where it strikes. */
    static Vec3 focal(ServerPlayer player, HitShape shape) {
        double ahead = switch (shape) {
            case HitShape.Sphere s -> s.offset();
            default -> Math.min(3.0, shape.reach() / 2.0);
        };
        return player.position().add(HitShape.forward(player.getYRot()).scale(ahead)).add(0, 0.2, 0);
    }
}
