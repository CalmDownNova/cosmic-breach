package com.cosmicbreach.combat.data;

import com.cosmicbreach.combat.core.Grade;
import com.cosmicbreach.combat.core.Stat;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A weapon's stats and moveset, loaded from {@code data/<ns>/combat/weapons/<name>.json}.
 * The id matches the item id that uses it. {@code blade} says where the striking part is on the item's
 * sprite, for the effects that follow it (optional; Meridian's blade when left out). {@code animations}
 * swaps the engine's own animations (dashes, parry, stagger) for the weapon's versions, by id.
 * {@code dash_follow} is a passive like the Binary Edges' Weave: an attack soon after a dash starts the
 * chain at a later move ({@link DashFollow}). {@code off_hand} is a second blade drawn in the off hand
 * ({@link OffHand}): the Binary Edges are a pair of sickles. {@code aerial} is a timed move an attack starts while
 * airborne (GDD 4.1: "attack while airborne for the aerial move"), for a weapon whose aerial is not a plunge (the
 * Choir Astrolabe's Starfall); a weapon with both plunges when aiming down and fires its aerial otherwise.
 */
public record WeaponDef(
        double baseDamage,
        Map<Stat, Grade> grades,
        double reach,
        List<ResourceLocation> combo,
        Optional<ResourceLocation> charged,
        Optional<ResourceLocation> plunge,
        Optional<ResourceLocation> dashAttack,
        Optional<Ability> ability,
        int tier,
        Optional<Blade> blade,
        Map<ResourceLocation, ResourceLocation> animations,
        Optional<DashFollow> dashFollow,
        Optional<OffHand> offHand,
        Optional<ResourceLocation> aerial
) {
    public static final Codec<WeaponDef> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.fieldOf("base_damage").forGetter(WeaponDef::baseDamage),
            Codec.unboundedMap(Stat.CODEC, Grade.CODEC).optionalFieldOf("grades", Map.of()).forGetter(WeaponDef::grades),
            Codec.DOUBLE.optionalFieldOf("reach", 3.0).forGetter(WeaponDef::reach),
            ResourceLocation.CODEC.listOf().validate(WeaponDef::nonEmpty).fieldOf("combo").forGetter(WeaponDef::combo),
            ResourceLocation.CODEC.optionalFieldOf("charged").forGetter(WeaponDef::charged),
            ResourceLocation.CODEC.optionalFieldOf("plunge").forGetter(WeaponDef::plunge),
            ResourceLocation.CODEC.optionalFieldOf("dash_attack").forGetter(WeaponDef::dashAttack),
            Ability.CODEC.optionalFieldOf("ability").forGetter(WeaponDef::ability),
            Codec.intRange(1, 4).optionalFieldOf("tier", 1).forGetter(WeaponDef::tier),
            Blade.CODEC.optionalFieldOf("blade").forGetter(WeaponDef::blade),
            Codec.unboundedMap(ResourceLocation.CODEC, ResourceLocation.CODEC).optionalFieldOf("animations", Map.of())
                    .forGetter(WeaponDef::animations),
            DashFollow.CODEC.optionalFieldOf("dash_follow").forGetter(WeaponDef::dashFollow),
            OffHand.CODEC.optionalFieldOf("off_hand").forGetter(WeaponDef::offHand),
            ResourceLocation.CODEC.optionalFieldOf("aerial").forGetter(WeaponDef::aerial)
    ).apply(i, WeaponDef::new));

    public WeaponDef {
        animations = Map.copyOf(animations);
    }

    /** A weapon whose aerial, if any, is its plunge (every melee weapon). */
    public WeaponDef(double baseDamage, Map<Stat, Grade> grades, double reach, List<ResourceLocation> combo,
                     Optional<ResourceLocation> charged, Optional<ResourceLocation> plunge,
                     Optional<ResourceLocation> dashAttack, Optional<Ability> ability, int tier, Optional<Blade> blade,
                     Map<ResourceLocation, ResourceLocation> animations, Optional<DashFollow> dashFollow,
                     Optional<OffHand> offHand) {
        this(baseDamage, grades, reach, combo, charged, plunge, dashAttack, ability, tier, blade, animations, dashFollow,
                offHand, Optional.empty());
    }

    /** A weapon with its own blade layout and animations, and no passive or second blade. */
    public WeaponDef(double baseDamage, Map<Stat, Grade> grades, double reach, List<ResourceLocation> combo,
                     Optional<ResourceLocation> charged, Optional<ResourceLocation> plunge,
                     Optional<ResourceLocation> dashAttack, Optional<Ability> ability, int tier, Optional<Blade> blade,
                     Map<ResourceLocation, ResourceLocation> animations) {
        this(baseDamage, grades, reach, combo, charged, plunge, dashAttack, ability, tier, blade, animations,
                Optional.empty(), Optional.empty());
    }

    /** A weapon with its own blade layout and the engine's animations. */
    public WeaponDef(double baseDamage, Map<Stat, Grade> grades, double reach, List<ResourceLocation> combo,
                     Optional<ResourceLocation> charged, Optional<ResourceLocation> plunge,
                     Optional<ResourceLocation> dashAttack, Optional<Ability> ability, int tier, Optional<Blade> blade) {
        this(baseDamage, grades, reach, combo, charged, plunge, dashAttack, ability, tier, blade, Map.of());
    }

    /** The animation to play for the engine's {@code engineAnimation} with this weapon: its own version, or that one. */
    public ResourceLocation animationFor(ResourceLocation engineAnimation) {
        return animations.getOrDefault(engineAnimation, engineAnimation);
    }

    /** A weapon whose effects follow Meridian's blade layout. */
    public WeaponDef(double baseDamage, Map<Stat, Grade> grades, double reach, List<ResourceLocation> combo,
                     Optional<ResourceLocation> charged, Optional<ResourceLocation> plunge,
                     Optional<ResourceLocation> dashAttack, Optional<Ability> ability, int tier) {
        this(baseDamage, grades, reach, combo, charged, plunge, dashAttack, ability, tier, Optional.empty());
    }

    private static DataResult<List<ResourceLocation>> nonEmpty(List<ResourceLocation> combo) {
        return combo.isEmpty() ? DataResult.error(() -> "A weapon needs at least one combo move") : DataResult.success(combo);
    }

    /**
     * The right-click ability: its move, Resonance cost and base cooldown in ticks, and optionally a
     * second press ({@link Recast}). With {@code cooldown_after_recast} the cooldown does not start at the
     * cast but once the second press is over: its window closed unused, an effect disarmed it (the thrown
     * blade came back), or its move reached its first active tick (the blink arrived). Until then the
     * ability can't be cast again.
     */
    public record Ability(ResourceLocation move, int cost, int cooldown, Optional<Recast> recast, boolean cooldownAfterRecast) {
        public static final Codec<Ability> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("move").forGetter(Ability::move),
                Codec.INT.fieldOf("cost").forGetter(Ability::cost),
                Codec.INT.fieldOf("cooldown").forGetter(Ability::cooldown),
                Recast.CODEC.optionalFieldOf("recast").forGetter(Ability::recast),
                Codec.BOOL.optionalFieldOf("cooldown_after_recast", false).forGetter(Ability::cooldownAfterRecast)
        ).apply(i, Ability::new));

        /** An ability pressed once. */
        public Ability(ResourceLocation move, int cost, int cooldown) {
            this(move, cost, cooldown, Optional.empty());
        }

        /** An ability whose cooldown starts at the cast. */
        public Ability(ResourceLocation move, int cost, int cooldown, Optional<Recast> recast) {
            this(move, cost, cooldown, recast, false);
        }
    }

    /**
     * The ability's second press: from the ability move's first active tick, for {@code window} ticks
     * (or until its effect disarms it, say a thrown blade that stuck in nothing), pressing the ability
     * again starts {@code move} instead, with no Resonance cost and no cooldown check. Binary Edges' blink
     * to the thrown blade and the Astrolabe's Supernova are this. With {@code auto} false the ability's
     * first active tick does not open it: an effect does, when it is ready (the thrown blade stuck).
     */
    public record Recast(ResourceLocation move, int window, boolean auto) {
        public static final Codec<Recast> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("move").forGetter(Recast::move),
                Codec.intRange(1, 1200).fieldOf("window").forGetter(Recast::window),
                Codec.BOOL.optionalFieldOf("auto", true).forGetter(Recast::auto)
        ).apply(i, Recast::new));

        /** A second press the ability's first active tick opens. */
        public Recast(ResourceLocation move, int window) {
            this(move, window, true);
        }
    }

    /**
     * A passive: an attack within {@code window} ticks after a dash ends starts the chain at {@code move}
     * instead of its first move. The dash attack's own window (the dash's last ticks and the first ticks
     * after it) comes first, so this covers the rest of the window. The Binary Edges' Weave: L4 within 10
     * ticks.
     */
    public record DashFollow(ResourceLocation move, int window) {
        public static final Codec<DashFollow> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("move").forGetter(DashFollow::move),
                Codec.intRange(1, 100).fieldOf("window").forGetter(DashFollow::window)
        ).apply(i, DashFollow::new));
    }

    /**
     * A second blade in the off hand, drawn while the weapon is held: {@code item} is what the off hand shows
     * (never in any inventory), {@code blade} where its striking part lies on that item's sprite. When the
     * off blade is away (thrown), {@code solo} names the animations the main hand plays instead of ones
     * that need both blades, by id (the Binary Edges' left hook becomes the right one).
     */
    public record OffHand(ResourceLocation item, Blade blade, Map<ResourceLocation, ResourceLocation> solo) {
        public static final Codec<OffHand> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("item").forGetter(OffHand::item),
                Blade.CODEC.fieldOf("blade").forGetter(OffHand::blade),
                Codec.unboundedMap(ResourceLocation.CODEC, ResourceLocation.CODEC).optionalFieldOf("solo", Map.of())
                        .forGetter(OffHand::solo)
        ).apply(i, OffHand::new));

        public OffHand {
            solo = Map.copyOf(solo);
        }

        /** The animation the main hand plays for {@code animation} while the off blade is away. */
        public ResourceLocation soloAnimation(ResourceLocation animation) {
            return solo.getOrDefault(animation, animation);
        }
    }

    /**
     * Where the striking part lies on the item's 32x32 sprite, in texels (x right, y down, texel centres
     * at .5): from {@code guard} to {@code tip} along the weapon, {@code halfWidth} texels from that line
     * to its upper edge. Meridian: the crossguard to the point of the blade. A hammer: the length of its
     * head along the haft, so a trail sweeps the whole head.
     */
    public record Blade(List<Float> guard, List<Float> tip, float halfWidth) {
        public static final Blade MERIDIAN = new Blade(List.of(11f, 20f), List.of(31.2f, 0.8f), 3.2f);
        private static final Codec<List<Float>> TEXEL = Codec.FLOAT.listOf().validate(l -> l.size() == 2
                ? DataResult.success(l) : DataResult.error(() -> "A texel is [x, y], got " + l));
        public static final Codec<Blade> CODEC = RecordCodecBuilder.create(i -> i.group(
                TEXEL.fieldOf("guard").forGetter(Blade::guard),
                TEXEL.fieldOf("tip").forGetter(Blade::tip),
                Codec.floatRange(0f, 16f).fieldOf("half_width").forGetter(Blade::halfWidth)
        ).apply(i, Blade::new));

        public Blade {
            guard = List.copyOf(guard);
            tip = List.copyOf(tip);
        }
    }

    /** The blade layout the effects follow: this weapon's, or Meridian's. */
    public Blade bladeOrDefault() {
        return blade.orElse(Blade.MERIDIAN);
    }
}
