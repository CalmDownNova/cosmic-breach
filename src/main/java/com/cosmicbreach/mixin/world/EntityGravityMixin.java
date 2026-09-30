package com.cosmicbreach.mixin.world;

import com.cosmicbreach.world.AetheriaGravity;
import com.cosmicbreach.world.AetheriaWorld;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Aetheria's gravity for entities that don't read the gravity attribute (items, arrows, falling blocks,
 * thrown things; GDD 2.5): {@code Entity.getGravity()} is scaled by {@link AetheriaGravity#multiplier}.
 * Only in Aetheria, only for non-living entities (living ones get the attribute modifier instead).
 */
@Mixin(Entity.class)
public abstract class EntityGravityMixin {
    @Inject(method = "getGravity", at = @At("RETURN"), cancellable = true)
    private void cosmicbreach$aetheriaGravity(CallbackInfoReturnable<Double> callback) {
        Entity self = (Entity) (Object) this;
        if (self instanceof LivingEntity) {
            return;
        }
        Level level = self.level();
        if (!AetheriaWorld.is(level)) {
            return;
        }
        double gravity = callback.getReturnValueD();
        if (gravity != 0.0) {
            double m = AetheriaGravity.multiplier(level, self.getY());
            if (m != 1.0) {
                callback.setReturnValue(gravity * m);
            }
        }
    }
}
