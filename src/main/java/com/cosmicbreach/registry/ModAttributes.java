package com.cosmicbreach.registry;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.combat.core.Stat;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The four attributes (GDD section 3.3) as real attributes on every player, synced to clients. A
 * player's allocated points are the base value (at most 30, set by the progression code from its
 * saved allocation); gear adds to it with ordinary attribute modifiers (for example an item's
 * {@code ADD_VALUE} modifier of 3 on {@code cosmicbreach:power}), and the attribute's range caps
 * the total at 40. The effective stat is the attribute's value.
 */
public final class ModAttributes {
    public static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(Registries.ATTRIBUTE, CosmicBreach.MOD_ID);

    /** Allocated plus gear can't go above this. */
    public static final double MAX_POINTS = 40.0;

    public static final DeferredHolder<Attribute, Attribute> POWER = register(Stat.POWER);
    public static final DeferredHolder<Attribute, Attribute> AGILITY = register(Stat.AGILITY);
    public static final DeferredHolder<Attribute, Attribute> ARCANE = register(Stat.ARCANE);
    public static final DeferredHolder<Attribute, Attribute> RESILIENCE = register(Stat.RESILIENCE);

    private ModAttributes() {
    }

    private static DeferredHolder<Attribute, Attribute> register(Stat stat) {
        String name = stat.getSerializedName();
        return ATTRIBUTES.register(name, () -> new RangedAttribute("attribute.name." + CosmicBreach.MOD_ID + "." + name,
                0.0, 0.0, MAX_POINTS).setSyncable(true));
    }

    public static Holder<Attribute> of(Stat stat) {
        return switch (stat) {
            case POWER -> POWER;
            case AGILITY -> AGILITY;
            case ARCANE -> ARCANE;
            case RESILIENCE -> RESILIENCE;
        };
    }

    public static void register(IEventBus modBus) {
        ATTRIBUTES.register(modBus);
        modBus.addListener(EntityAttributeModificationEvent.class, ModAttributes::addToPlayers);
    }

    private static void addToPlayers(EntityAttributeModificationEvent event) {
        for (Stat stat : Stat.values()) {
            event.add(EntityType.PLAYER, of(stat));
        }
    }
}
