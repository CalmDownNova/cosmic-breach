package com.cosmicbreach.world;

import com.cosmicbreach.CosmicBreach;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Gravity in Aetheria (GDD 2.5): the Drift runs at 0.4x. {@link #multiplier} is the one place that says how
 * strong gravity is at a height; everything reads it:
 *
 * <ul>
 *   <li>Living entities, through vanilla's gravity attribute: a transient modifier kept in step by
 *       {@link #update} every tick on the server (attributes sync to the client). While gravity is lowered
 *       they also get safe fall distance +10 and jump strength x1.3.</li>
 *   <li>Items, arrows, falling blocks and every other non-living entity, through {@code EntityGravityMixin},
 *       on both sides.</li>
 * </ul>
 *
 * <p>For the weather task: install a {@link Modifier} with {@link #setModifier} to change the multiplier
 * (a Gravity Tide makes the Drift 0.15x). It must give the same answer on client and server, so feed it
 * synced state.
 */
public final class AetheriaGravity {
    public static final double DRIFT = 0.4;
    public static final double LOW_GRAVITY_SAFE_FALL = 10.0;
    public static final double LOW_GRAVITY_JUMP = 0.3;
    public static final ResourceLocation GRAVITY_ID = CosmicBreach.id("aetheria_gravity");
    public static final ResourceLocation SAFE_FALL_ID = CosmicBreach.id("aetheria_safe_fall");
    public static final ResourceLocation JUMP_ID = CosmicBreach.id("aetheria_jump");

    /** Changes the multiplier: gets the level, the height and the multiplier so far; returns the new one. */
    @FunctionalInterface
    public interface Modifier {
        double apply(Level level, double y, double multiplier);
    }

    private static volatile @Nullable Modifier modifier;

    private AetheriaGravity() {
    }

    public static void setModifier(@Nullable Modifier m) {
        modifier = m;
    }

    /** Gravity multiplier at {@code pos}: 1 outside Aetheria and outside the Drift. Either side. */
    public static double multiplier(Level level, BlockPos pos) {
        return multiplier(level, pos.getY());
    }

    /** Gravity multiplier at height {@code y}. Either side. */
    public static double multiplier(Level level, double y) {
        if (!AetheriaWorld.is(level)) {
            return 1.0;
        }
        double m = Layer.at(y) == Layer.DRIFT ? DRIFT : 1.0;
        Modifier mod = modifier;
        return mod == null ? m : mod.apply(level, y, m);
    }

    /** Keeps a living entity's gravity, safe fall and jump modifiers in step with where it is. Server side. */
    public static void update(LivingEntity entity) {
        double m = multiplier(entity.level(), entity.getY());
        AttributeInstance gravity = entity.getAttribute(Attributes.GRAVITY);
        if (gravity == null) {
            return;
        }
        if (m == 1.0) {
            clear(entity);
            return;
        }
        AttributeModifier current = gravity.getModifier(GRAVITY_ID);
        if (current == null || current.amount() != m - 1.0) {
            gravity.addOrUpdateTransientModifier(new AttributeModifier(GRAVITY_ID, m - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        boolean low = m < 1.0;
        set(entity.getAttribute(Attributes.SAFE_FALL_DISTANCE), SAFE_FALL_ID, low ? LOW_GRAVITY_SAFE_FALL : 0, AttributeModifier.Operation.ADD_VALUE);
        set(entity.getAttribute(Attributes.JUMP_STRENGTH), JUMP_ID, low ? LOW_GRAVITY_JUMP : 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    /** Removes all of Aetheria's gravity modifiers (leaving the dimension, respawning elsewhere). */
    public static void clear(LivingEntity entity) {
        remove(entity.getAttribute(Attributes.GRAVITY), GRAVITY_ID);
        remove(entity.getAttribute(Attributes.SAFE_FALL_DISTANCE), SAFE_FALL_ID);
        remove(entity.getAttribute(Attributes.JUMP_STRENGTH), JUMP_ID);
    }

    private static void set(@Nullable AttributeInstance attribute, ResourceLocation id, double amount, AttributeModifier.Operation op) {
        if (attribute == null) {
            return;
        }
        if (amount == 0) {
            remove(attribute, id);
            return;
        }
        AttributeModifier current = attribute.getModifier(id);
        if (current == null || current.amount() != amount) {
            attribute.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
        }
    }

    private static void remove(@Nullable AttributeInstance attribute, ResourceLocation id) {
        if (attribute != null && attribute.hasModifier(id)) {
            attribute.removeModifier(id);
        }
    }
}
