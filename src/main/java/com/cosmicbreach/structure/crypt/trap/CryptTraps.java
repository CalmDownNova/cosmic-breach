package com.cosmicbreach.structure.crypt.trap;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.structure.trap.KineticTripwires;
import com.cosmicbreach.structure.trap.MotionWindow;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The crypt traps' players (GDD 6.4). Once a tick per player, the block under their feet: on a Void Rift tile, whether
 * they are truly standing (on the ground, not dashing, barely moving over three ticks: {@link VoidRiftRules#standing})
 * goes to its cluster ({@link VoidRiftBlockEntity}); a Gravity Sigil wakes its plate
 * ({@link GravityPistonBlockEntity}). One block read per player per tick; the traps themselves only tick while
 * something is happening. Crushing gravity is three attribute modifiers held for 60 ticks: gravity x3, jump strength
 * to 0, speed -60% (attributes sync, so the client moves by them); the client halves dashes while the gravity
 * modifier is on ({@link #HEAVY_GRAVITY}).
 */
public final class CryptTraps {
    public static final ResourceLocation HEAVY_GRAVITY = CosmicBreach.id("crushing_gravity");
    public static final ResourceLocation HEAVY_JUMP = CosmicBreach.id("crushing_jump");
    public static final ResourceLocation HEAVY_SPEED = CosmicBreach.id("crushing_speed");

    private static final Map<UUID, Long> HEAVY_UNTIL = new HashMap<>();
    /** Each player's motion over the last three ticks, for the Void Rift's standing rule. */
    private static final Map<UUID, MotionWindow> MOTION = new HashMap<>();
    /** For tests: when each player last turned heavy. */
    private static final Map<UUID, Long> HEAVY_SINCE = new HashMap<>();

    private CryptTraps() {
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        Long until = HEAVY_UNTIL.get(player.getUUID());
        if (until != null && now >= until) {
            HEAVY_UNTIL.remove(player.getUUID());
            clearHeavy(player);
        }
        MotionWindow motion = MOTION.computeIfAbsent(player.getUUID(), id -> new MotionWindow());
        motion.endTick(player.getX(), player.getZ(), KineticTripwires.movePackets(player));
        if (player.isSpectator() || !player.isAlive()) {
            return;
        }
        BlockPos below = BlockPos.containing(player.getX(), player.getY() - 0.2, player.getZ());
        BlockState state = level.getBlockState(below);
        if (state.getBlock() instanceof VoidRiftTileBlock) {
            if (!state.getValue(VoidRiftTileBlock.OPEN) && !player.isCreative()
                    && level.getBlockEntity(VoidRiftTileBlock.middle(below, state)) instanceof VoidRiftBlockEntity rift) {
                PlayerCombat combat = PlayerCombat.existing(player);
                boolean dashing = combat != null && combat.machine().isDashing();
                rift.stand(level, player, VoidRiftRules.standing(player.onGround(), dashing, motion.average()), now);
            }
        } else if (state.getBlock() instanceof GravitySigilBlock && player.onGround() && !player.isCreative()) {
            GravityPistonBlockEntity piston = pistonOver(level, GravitySigilBlock.middle(below, state));
            if (piston != null) {
                piston.stepOn(level, player, now);
            }
        }
    }

    /** The Gravity Piston over a sigil's middle tile, up to 10 blocks up, or null. */
    public static GravityPistonBlockEntity pistonOver(ServerLevel level, BlockPos middle) {
        BlockPos.MutableBlockPos p = middle.mutable();
        for (int i = 1; i <= 10; i++) {
            p.move(0, 1, 0);
            if (level.getBlockEntity(p) instanceof GravityPistonBlockEntity piston) {
                return piston;
            }
        }
        return null;
    }

    /** {@code player} turns heavy for {@link GravityPlateRules#HEAVY_TICKS} ticks (if not already). */
    static void makeHeavy(ServerPlayer player, long now) {
        if (HEAVY_UNTIL.containsKey(player.getUUID())) {
            return;
        }
        HEAVY_UNTIL.put(player.getUUID(), now + GravityPlateRules.HEAVY_TICKS);
        HEAVY_SINCE.put(player.getUUID(), now);
        set(player.getAttribute(Attributes.GRAVITY), HEAVY_GRAVITY, GravityPlateRules.GRAVITY - 1.0);
        set(player.getAttribute(Attributes.JUMP_STRENGTH), HEAVY_JUMP, -1.0);
        set(player.getAttribute(Attributes.MOVEMENT_SPEED), HEAVY_SPEED, GravityPlateRules.SPEED - 1.0);
    }

    /** True while {@code entity} is under crushing gravity (either side: the gravity modifier syncs). */
    public static boolean heavy(LivingEntity entity) {
        AttributeInstance g = entity.getAttribute(Attributes.GRAVITY);
        return g != null && g.hasModifier(HEAVY_GRAVITY);
    }

    /** The tick {@code player} last turned heavy, or -1 (tests). */
    public static long heavySince(Player player) {
        return HEAVY_SINCE.getOrDefault(player.getUUID(), -1L);
    }

    private static void set(AttributeInstance attribute, ResourceLocation id, double amount) {
        if (attribute != null) {
            attribute.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    public static void clearHeavy(LivingEntity entity) {
        remove(entity.getAttribute(Attributes.GRAVITY), HEAVY_GRAVITY);
        remove(entity.getAttribute(Attributes.JUMP_STRENGTH), HEAVY_JUMP);
        remove(entity.getAttribute(Attributes.MOVEMENT_SPEED), HEAVY_SPEED);
    }

    private static void remove(AttributeInstance attribute, ResourceLocation id) {
        if (attribute != null && attribute.hasModifier(id)) {
            attribute.removeModifier(id);
        }
    }

    public static void forget(ServerPlayer player) {
        HEAVY_UNTIL.remove(player.getUUID());
        MOTION.remove(player.getUUID());
        clearHeavy(player);
    }

    public static void reset() {
        HEAVY_UNTIL.clear();
        MOTION.clear();
        HEAVY_SINCE.clear();
    }
}
