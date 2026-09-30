package com.cosmicbreach.combat.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

import java.util.Optional;

/**
 * One move's frame data and effects, loaded from {@code data/<ns>/combat/moves/**.json}.
 * The id is the file path (e.g. {@code cosmicbreach:meridian/l1}), so it is not stored here.
 * {@code landAnimation} is what a plunge plays when it lands ({@code land_animation}, optional).
 * {@code traits} are the optional behaviour fields ({@link MoveTraits}: hyper armor, cancel windows, root,
 * walk speed, effects, sounds), read from the same JSON object.
 */
public record MoveDef(
        MoveKind kind,
        Timing timing,
        Hit hit,
        HitShape hitbox,
        Optional<ResourceLocation> next,
        ResourceLocation animation,
        Optional<ResourceLocation> landAnimation,
        double lunge,
        Optional<Charge> charge,
        Optional<Launch> launch,
        Optional<Wave> wave,
        Optional<Slash> slash,
        MoveTraits traits
) {
    public static final Codec<MoveDef> CODEC = RecordCodecBuilder.create(i -> i.group(
            MoveKind.CODEC.fieldOf("kind").forGetter(MoveDef::kind),
            Timing.CODEC.fieldOf("timing").forGetter(MoveDef::timing),
            Hit.CODEC.fieldOf("hit").forGetter(MoveDef::hit),
            HitShape.CODEC.fieldOf("hitbox").forGetter(MoveDef::hitbox),
            ResourceLocation.CODEC.optionalFieldOf("next").forGetter(MoveDef::next),
            ResourceLocation.CODEC.fieldOf("animation").forGetter(MoveDef::animation),
            ResourceLocation.CODEC.optionalFieldOf("land_animation").forGetter(MoveDef::landAnimation),
            Codec.DOUBLE.optionalFieldOf("lunge", 0.0).forGetter(MoveDef::lunge),
            Charge.CODEC.optionalFieldOf("charge").forGetter(MoveDef::charge),
            Launch.CODEC.optionalFieldOf("launch").forGetter(MoveDef::launch),
            Wave.CODEC.optionalFieldOf("wave").forGetter(MoveDef::wave),
            Slash.CODEC.optionalFieldOf("slash").forGetter(MoveDef::slash),
            MoveTraits.MAP_CODEC.forGetter(MoveDef::traits)
    ).apply(i, MoveDef::new));

    /** A move without a landing animation (every kind but a plunge). */
    public MoveDef(MoveKind kind, Timing timing, Hit hit, HitShape hitbox, Optional<ResourceLocation> next,
                   ResourceLocation animation, double lunge, Optional<Charge> charge, Optional<Launch> launch,
                   Optional<Wave> wave, Optional<Slash> slash) {
        this(kind, timing, hit, hitbox, next, animation, Optional.empty(), lunge, charge, launch, wave, slash);
    }

    /** A move with none of the optional behaviour fields ({@link MoveTraits#NONE}). */
    public MoveDef(MoveKind kind, Timing timing, Hit hit, HitShape hitbox, Optional<ResourceLocation> next,
                   ResourceLocation animation, Optional<ResourceLocation> landAnimation, double lunge,
                   Optional<Charge> charge, Optional<Launch> launch, Optional<Wave> wave, Optional<Slash> slash) {
        this(kind, timing, hit, hitbox, next, animation, landAnimation, lunge, charge, launch, wave, slash, MoveTraits.NONE);
    }

    /** This move with other behaviour fields. */
    public MoveDef withTraits(MoveTraits newTraits) {
        return new MoveDef(kind, timing, hit, hitbox, next, animation, landAnimation, lunge, charge, launch, wave, slash,
                newTraits);
    }

    /** Startup, active and recovery ticks. A plunge's active phase lasts until landing instead. */
    public record Timing(int startup, int active, int recovery) {
        public static final Codec<Timing> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, 400).fieldOf("startup").forGetter(Timing::startup),
                Codec.intRange(0, 400).fieldOf("active").forGetter(Timing::active),
                Codec.intRange(0, 400).fieldOf("recovery").forGetter(Timing::recovery)
        ).apply(i, Timing::new));

        public int total() {
            return startup + active + recovery;
        }
    }

    /**
     * Damage and feel. {@code mvMax} is the charged ceiling or the plunge cap (0 means "same as mv");
     * {@code hits} is how many times one target can be hit by one use, {@code hitInterval} the ticks between.
     * Several hits with no interval are spread evenly over the active ticks ({@link #spread}): the Binary
     * Edges' Cross Cut is two hits of 0.6 over its two active ticks, its Gyre four of 0.5 over three. Motion
     * value and Impact are per hit.
     */
    public record Hit(double mv, double mvMax, double mvPerBlock, double impact, int resonance,
                      int hitstop, int hits, int hitInterval) {
        public static final Codec<Hit> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("mv").forGetter(Hit::mv),
                Codec.DOUBLE.optionalFieldOf("mv_max", 0.0).forGetter(Hit::mvMax),
                Codec.DOUBLE.optionalFieldOf("mv_per_block", 0.0).forGetter(Hit::mvPerBlock),
                Codec.DOUBLE.fieldOf("impact").forGetter(Hit::impact),
                Codec.INT.optionalFieldOf("resonance", 0).forGetter(Hit::resonance),
                Codec.INT.optionalFieldOf("hitstop", 2).forGetter(Hit::hitstop),
                Codec.intRange(1, 32).optionalFieldOf("hits", 1).forGetter(Hit::hits),
                Codec.intRange(0, 100).optionalFieldOf("hit_interval", 0).forGetter(Hit::hitInterval)
        ).apply(i, Hit::new));

        /**
         * True if the hits are spread over the active ticks: more than one and no interval given. Hit k
         * (from 0) lands on active tick round(k x (active - 1) / (hits - 1)), so the first lands on the
         * first active tick and the last on the last, and a target inside the whole time takes all of them.
         */
        public boolean spread() {
            return hits > 1 && hitInterval == 0;
        }

        /**
         * How many hits a target inside the hitbox takes on active tick {@code activeTick} of {@code activeTicks}:
         * for spread hits their share of that tick (0 on some ticks, more than 1 when there are more hits than
         * ticks); otherwise 1 (the ledger keeps the count and the interval).
         */
        public int hitsAt(int activeTick, int activeTicks) {
            if (!spread()) {
                return 1;
            }
            int span = Math.max(1, activeTicks) - 1;
            int count = 0;
            for (int k = 0; k < hits; k++) {
                // round half up of k * span / (hits - 1), in integers
                int tick = (2 * k * span + (hits - 1)) / (2 * (hits - 1));
                if (tick == activeTick) {
                    count++;
                }
            }
            return count;
        }

        public double mvCap() {
            return mvMax > 0 ? mvMax : mv;
        }
    }

    /**
     * Hold at least {@code min} ticks (counted from the press) to release; {@code full} is maximum charge.
     * {@code animation} is the looping hold pose played while charging (optional); {@code color} tints the
     * light gathering on the weapon while it charges (optional; Meridian's gold when left out).
     */
    public record Charge(int min, int full, Optional<ResourceLocation> animation, Optional<Integer> color) {
        public static final Codec<Charge> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("min").forGetter(Charge::min),
                Codec.INT.fieldOf("full").forGetter(Charge::full),
                ResourceLocation.CODEC.optionalFieldOf("animation").forGetter(Charge::animation),
                Slash.COLOR_CODEC.optionalFieldOf("color").forGetter(Charge::color)
        ).apply(i, Charge::new));

        /** A charge without its own hold pose. */
        public Charge(int min, int full) {
            this(min, full, Optional.empty());
        }

        /** A charge in Meridian's gold. */
        public Charge(int min, int full, Optional<ResourceLocation> animation) {
            this(min, full, animation, Optional.empty());
        }
    }

    /**
     * Launches targets with poise at or under {@code maxPoise} {@code height} blocks up and holds them
     * Suspended for {@code suspendTicks}. Holding the ability button lifts the user {@code rise} blocks.
     */
    public record Launch(double height, int suspendTicks, double maxPoise, double rise) {
        public static final Codec<Launch> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("height").forGetter(Launch::height),
                Codec.INT.fieldOf("suspend_ticks").forGetter(Launch::suspendTicks),
                Codec.DOUBLE.fieldOf("max_poise").forGetter(Launch::maxPoise),
                Codec.DOUBLE.optionalFieldOf("rise", 0.0).forGetter(Launch::rise)
        ).apply(i, Launch::new));
    }

    /** A ground wave that travels {@code length} blocks ahead after the hit, dealing {@code mv}. */
    public record Wave(double length, double mv) {
        public static final Codec<Wave> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("length").forGetter(Wave::length),
                Codec.DOUBLE.fieldOf("mv").forGetter(Wave::mv)
        ).apply(i, Wave::new));
    }

    /**
     * Renderer hints for the slash arc: the plane rolled {@code roll} degrees around forward
     * (0 flat, 90 vertical), swept from {@code from} to {@code to} degrees (negative is the attacker's left).
     * With a second blade in the off hand ({@link WeaponDef.OffHand}), {@code blades} says which of them trail
     * (the main hand's, the off hand's or both) and {@code off_color} is the off blade's trail colour.
     */
    public record Slash(float roll, float from, float to, float radius, float width, int color, Blades blades,
                        Optional<Integer> offColor) {
        public static final Codec<Integer> COLOR_CODEC = Codec.STRING.comapFlatMap(Slash::parseColor,
                c -> String.format("#%06X", c & 0xFFFFFF));
        public static final Codec<Slash> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.optionalFieldOf("roll", 0f).forGetter(Slash::roll),
                Codec.FLOAT.fieldOf("from").forGetter(Slash::from),
                Codec.FLOAT.fieldOf("to").forGetter(Slash::to),
                Codec.FLOAT.fieldOf("radius").forGetter(Slash::radius),
                Codec.FLOAT.optionalFieldOf("width", 1.0f).forGetter(Slash::width),
                COLOR_CODEC.optionalFieldOf("color", 0xFFFFFF).forGetter(Slash::color),
                Blades.CODEC.optionalFieldOf("blades", Blades.MAIN).forGetter(Slash::blades),
                COLOR_CODEC.optionalFieldOf("off_color").forGetter(Slash::offColor)
        ).apply(i, Slash::new));

        /** A slash of the main hand's blade. */
        public Slash(float roll, float from, float to, float radius, float width, int color) {
            this(roll, from, to, radius, width, color, Blades.MAIN, Optional.empty());
        }

        /** The off blade's trail colour: its own, or the main one's. */
        public int offColorOrMain() {
            return offColor.orElse(color);
        }

        /** Which blades a slash trails. */
        public enum Blades implements StringRepresentable {
            MAIN("main"),
            OFF("off"),
            BOTH("both");

            public static final Codec<Blades> CODEC = StringRepresentable.fromEnum(Blades::values);
            private final String serializedName;

            Blades(String serializedName) {
                this.serializedName = serializedName;
            }

            public boolean main() {
                return this != OFF;
            }

            public boolean off() {
                return this != MAIN;
            }

            @Override
            public String getSerializedName() {
                return serializedName;
            }
        }

        private static DataResult<Integer> parseColor(String s) {
            String hex = s.startsWith("#") ? s.substring(1) : s;
            try {
                return DataResult.success(Integer.parseInt(hex, 16) & 0xFFFFFF);
            } catch (NumberFormatException e) {
                return DataResult.error(() -> "Not a hex color: " + s);
            }
        }
    }
}
