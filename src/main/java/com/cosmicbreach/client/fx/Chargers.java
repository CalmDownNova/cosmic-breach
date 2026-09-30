package com.cosmicbreach.client.fx;

import com.cosmicbreach.combat.MoveSound;
import com.cosmicbreach.combat.core.CombatRules;
import com.cosmicbreach.combat.data.CombatData;
import com.cosmicbreach.combat.data.MoveDef;
import com.cosmicbreach.combat.data.MoveTraits;
import com.cosmicbreach.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Players holding a charge, the local one (from its own machine) and others (from the server's
 * CHARGE_START and CHARGE_STOP): each gets the looping choir ({@link ChargeLoopSound}), light gathering
 * on its blade ({@link ChargeGlowEffect}) and motes drawn in to it, until its charge ends. A charge also
 * ends when the player leaves, dies, starts a move or has held it for {@value #MAX_TICKS} ticks without
 * word (a lost packet can't leave it singing).
 */
public final class Chargers {
    private static final int MAX_TICKS = 600;
    /** The charge the state machine assumes for a charged move that doesn't say. */
    private static final MoveDef.Charge DEFAULT_CHARGE = new MoveDef.Charge(12, 24);

    private static final class Charge {
        final Player player;
        final BooleanSupplier holding;
        final int rampTicks;
        final @Nullable ChargeLoopSound sound;
        /** The charge's light colour, or -1 for Meridian's gold. */
        int color = -1;
        @Nullable ChargeGlowEffect glow;
        int ticks;
        boolean stopped;

        Charge(Player player, BooleanSupplier holding, int rampTicks, @Nullable ChargeLoopSound sound) {
            this.player = player;
            this.holding = holding;
            this.rampTicks = rampTicks;
            this.sound = sound;
        }

        double progress() {
            return Math.min(1.0, ticks / (double) rampTicks);
        }
    }

    private static final Map<Integer, Charge> CHARGES = new HashMap<>();

    private Chargers() {
    }

    /**
     * {@code player} started charging {@code chargedMove}; {@code holding} says whether it still is
     * (null for another player: then only {@link #stop} or a timeout ends it).
     */
    public static void start(Player player, @Nullable ResourceLocation chargedMove, @Nullable BooleanSupplier holding) {
        stop(player.getId());
        MoveDef.Charge charge = DEFAULT_CHARGE;
        MoveDef def = chargedMove == null ? null : CombatData.client().move(chargedMove);
        if (def != null && def.charge().isPresent()) {
            charge = def.charge().get();
        }
        // The charge starts once the button has been held CHARGE_HOLD_THRESHOLD ticks; full is counted from the press.
        int ramp = Math.max(1, charge.full() - CombatRules.CHARGE_HOLD_THRESHOLD);
        SoundEvent loop = def == null ? ModSounds.CHARGE_LOOP.get() : def.traits().sound().flatMap(MoveTraits.Sounds::charge)
                .map(id -> MoveSound.resolve(id).value()).orElse(ModSounds.CHARGE_LOOP.get());
        ChargeLoopSound sound = new ChargeLoopSound(player, ramp, loop);
        Minecraft.getInstance().getSoundManager().play(sound);
        Charge entry = new Charge(player, holding == null ? () -> true : holding, ramp, sound);
        entry.color = charge.color().orElse(-1);
        entry.glow = new ChargeGlowEffect(player, entry::progress, entry.color);
        WorldFx.add(entry.glow);
        CHARGES.put(player.getId(), entry);
    }

    /** The charge of the entity with this id ended (released, cancelled, or the player did something else). */
    public static void stop(int entityId) {
        Charge charge = CHARGES.remove(entityId);
        if (charge != null) {
            end(charge);
        }
    }

    public static boolean isCharging(int entityId) {
        return CHARGES.containsKey(entityId);
    }

    /** The colour of this entity's charge, or -1 (Meridian's gold) if it isn't charging or has none. */
    static int color(int entityId) {
        Charge charge = CHARGES.get(entityId);
        return charge == null ? -1 : charge.color;
    }

    static void tick() {
        Iterator<Charge> it = CHARGES.values().iterator();
        while (it.hasNext()) {
            Charge charge = it.next();
            Player player = charge.player;
            if (player.isRemoved() || !player.isAlive() || !charge.holding.getAsBoolean() || ++charge.ticks > MAX_TICKS) {
                end(charge);
                it.remove();
                continue;
            }
            CombatEffects.chargeMote(player, charge.progress(), charge.color);
        }
    }

    static void clear() {
        for (Charge charge : CHARGES.values()) {
            end(charge);
        }
        CHARGES.clear();
    }

    private static void end(Charge charge) {
        if (!charge.stopped) {
            charge.stopped = true;
            if (charge.sound != null) {
                charge.sound.release();
            }
            if (charge.glow != null) {
                charge.glow.end();
            }
        }
    }
}
