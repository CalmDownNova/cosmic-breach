package com.cosmicbreach.gear.set;

import com.cosmicbreach.combat.core.Stat;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import org.jetbrains.annotations.Nullable;

/**
 * One armor set (GDD 5.1): its four pieces with their armor and stat bonuses, its tier, the toughness and
 * knockback resistance every piece has, its accent colour, its bonuses ({@link SetBehavior}) and its active
 * ability ({@link SetAbility}, optional). The pieces' stat bonuses are ordinary {@code ADD_VALUE} modifiers on
 * the progression attributes, so they count toward the 40-point cap like any gear.
 *
 * <p>To add a set: define it (see {@code StarfallVanguard.SET}), register it with {@link ArmorSets#register},
 * give {@code GearRegistry.registerSet} its four items, and give it a GeckoLib model named after its id
 * ({@code geo/armor/<id>.geo.json}, {@code textures/armor/<id>.png} and {@code _glowmask.png}).
 */
public final class ArmorSet {
    public static final int TWO_PIECES = 2;
    public static final int FULL_SET = 4;

    /** One piece: its slot, item name, armor points and stat bonuses. */
    public record Piece(ArmorItem.Type type, String name, int armor, Map<Stat, Integer> stats) {
        public Piece {
            stats = Map.copyOf(stats);
        }
    }

    private final ResourceLocation id;
    private final int tier;
    private final List<Piece> pieces;
    private final float toughness;
    private final float knockbackResistance;
    private final int durabilityMultiplier;
    private final int color;
    private final SetBehavior behavior;
    private final @Nullable SetAbility ability;

    /**
     * @param color the set's accent (0xRRGGBB): its ability pip on the HUD
     * @param durabilityMultiplier as vanilla's armor materials (iron 15, diamond 33, netherite 37)
     */
    public ArmorSet(ResourceLocation id, int tier, List<Piece> pieces, float toughness, float knockbackResistance,
                    int durabilityMultiplier, int color, SetBehavior behavior, @Nullable SetAbility ability) {
        if (pieces.size() != FULL_SET) {
            throw new IllegalArgumentException("a set has four pieces, " + id + " has " + pieces.size());
        }
        this.id = id;
        this.tier = tier;
        this.pieces = List.copyOf(pieces);
        this.toughness = toughness;
        this.knockbackResistance = knockbackResistance;
        this.durabilityMultiplier = durabilityMultiplier;
        this.color = color;
        this.behavior = behavior;
        this.ability = ability;
    }

    public ResourceLocation id() {
        return id;
    }

    public int tier() {
        return tier;
    }

    public List<Piece> pieces() {
        return pieces;
    }

    public Optional<Piece> piece(ArmorItem.Type type) {
        return pieces.stream().filter(p -> p.type() == type).findFirst();
    }

    public float toughness() {
        return toughness;
    }

    public float knockbackResistance() {
        return knockbackResistance;
    }

    public int durabilityMultiplier() {
        return durabilityMultiplier;
    }

    public int color() {
        return color;
    }

    public SetBehavior behavior() {
        return behavior;
    }

    public Optional<SetAbility> ability() {
        return Optional.ofNullable(ability);
    }

    /** Armor points by piece, for the armor material. */
    public Map<ArmorItem.Type, Integer> defense() {
        Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
        for (Piece piece : pieces) {
            defense.put(piece.type(), piece.armor());
        }
        return defense;
    }

    /** The full set's armor points. */
    public int totalArmor() {
        return pieces.stream().mapToInt(Piece::armor).sum();
    }

    /** The full set's stat bonuses, added up. */
    public Map<Stat, Integer> fullSetStats() {
        Map<Stat, Integer> total = new EnumMap<>(Stat.class);
        for (Piece piece : pieces) {
            piece.stats().forEach((stat, points) -> total.merge(stat, points, Integer::sum));
        }
        return total;
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
