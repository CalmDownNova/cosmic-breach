package com.cosmicbreach.combat.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

import java.util.List;
import java.util.Optional;

/**
 * How a move behaves beyond its frame data. Every field is optional in the move's JSON and sits at its
 * top level next to {@code timing} and {@code hit}; leaving them all out gives {@link #NONE}, the
 * behaviour every move had before these existed.
 *
 * @param hyperArmor extra poise while the move is in startup or active (a plunge's dive counts as
 *                   active): Impact taken then has to reach the player's poise plus this to stagger
 *                   ({@code hyper_armor}; the Comet Maul's attacks have 30)
 * @param cancels    extra cancel windows on top of the engine's (a dash or parry from the 3rd recovery
 *                   tick, an ability from the 1st); unlike those they may open during startup or active
 * @param root       no walking or jumping while the move runs, startup to the end of recovery
 * @param movement   walk speed multiplier while the move runs (1 = free, 0.5 = half speed); ignored when rooted
 * @param effects    the move's effects, by id ({@link MoveEffect}), run by their handlers
 * @param sound      sounds that replace the engine's defaults for this move
 * @param invulnerable i-frames from the move's start to the end of this phase ({@code "invulnerable": "startup"}:
 *                   the Binary Edges' blink is untouchable for its 6 ticks of travel); empty for none
 */
public record MoveTraits(double hyperArmor, List<CancelWindow> cancels, boolean root, float movement,
                         List<MoveEffect> effects, Optional<Sounds> sound, Optional<Stage> invulnerable) {
    public static final MoveTraits NONE = new MoveTraits(0.0, List.of(), false, 1.0f, List.of(), Optional.empty());

    /** The fields of a move's JSON this reads (flat, beside the others). */
    public static final MapCodec<MoveTraits> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.doubleRange(0.0, 1000.0).optionalFieldOf("hyper_armor", 0.0).forGetter(MoveTraits::hyperArmor),
            CancelWindow.CODEC.listOf().optionalFieldOf("cancels", List.of()).forGetter(MoveTraits::cancels),
            Codec.BOOL.optionalFieldOf("root", false).forGetter(MoveTraits::root),
            Codec.floatRange(0.0f, 2.0f).optionalFieldOf("movement", 1.0f).forGetter(MoveTraits::movement),
            MoveEffect.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(MoveTraits::effects),
            Sounds.CODEC.optionalFieldOf("sound").forGetter(MoveTraits::sound),
            Stage.CODEC.optionalFieldOf("invulnerable").forGetter(MoveTraits::invulnerable)
    ).apply(i, MoveTraits::new));

    public MoveTraits {
        cancels = List.copyOf(cancels);
        effects = List.copyOf(effects);
    }

    /** Behaviour without i-frames of its own. */
    public MoveTraits(double hyperArmor, List<CancelWindow> cancels, boolean root, float movement,
                      List<MoveEffect> effects, Optional<Sounds> sound) {
        this(hyperArmor, cancels, root, movement, effects, sound, Optional.empty());
    }

    /** True if the move makes its user invulnerable while it is in {@code stage}. */
    public boolean invulnerableIn(Stage stage) {
        return invulnerable.isPresent() && stage.ordinal() <= invulnerable.get().ordinal();
    }

    /** Walk speed multiplier while the move runs: 0 when rooted. */
    public float walkMultiplier() {
        return root ? 0.0f : movement;
    }

    /** The effect with this id, if the move has it. */
    public Optional<MoveEffect> effect(ResourceLocation id) {
        return effects.stream().filter(e -> e.id().equals(id)).findFirst();
    }

    /** True if one of this move's own windows lets {@code by} cancel it at this point. */
    public boolean cancelOpen(CancelBy by, Stage stage, int tick) {
        for (CancelWindow window : cancels) {
            if (window.by() == by && window.opens(stage, tick)) {
                return true;
            }
        }
        return false;
    }

    /** The input that ends a move early. */
    public enum CancelBy implements StringRepresentable {
        DASH("dash"),
        PARRY("parry"),
        ABILITY("ability"),
        /** The attack button: the chain's next move starts at once. */
        ATTACK("attack");

        public static final Codec<CancelBy> CODEC = StringRepresentable.fromEnum(CancelBy::values);
        private final String serializedName;

        CancelBy(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }

    /** The three phases of a move's frame data (a plunge's dive is its active phase). */
    public enum Stage implements StringRepresentable {
        STARTUP("startup"),
        ACTIVE("active"),
        RECOVERY("recovery");

        public static final Codec<Stage> CODEC = StringRepresentable.fromEnum(Stage::values);
        private final String serializedName;

        Stage(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }

    /**
     * {@code by} may cancel the move from tick {@code from} (0-based) of {@code phase} on, and through every
     * later phase: {@code {"by": "dash", "phase": "active", "from": 11}} opens on the 12th active tick.
     */
    public record CancelWindow(CancelBy by, Stage phase, int from) {
        public static final Codec<CancelWindow> CODEC = RecordCodecBuilder.create(i -> i.group(
                CancelBy.CODEC.fieldOf("by").forGetter(CancelWindow::by),
                Stage.CODEC.fieldOf("phase").forGetter(CancelWindow::phase),
                Codec.intRange(0, 400).optionalFieldOf("from", 0).forGetter(CancelWindow::from)
        ).apply(i, CancelWindow::new));

        public boolean opens(Stage stage, int tick) {
            int order = Integer.compare(stage.ordinal(), phase.ordinal());
            return order > 0 || (order == 0 && tick >= from);
        }
    }

    /**
     * Sound events (by id) a move plays instead of the engine's: {@code swing} on its first active tick
     * (instead of the light or heavy swing), {@code hit} for every hit it lands (a crit keeps the crit
     * sound), {@code slam} where it strikes the ground (its first active tick, or a plunge's landing,
     * whether it hits anything or not) and {@code charge}, the loop while its charge is held.
     */
    public record Sounds(Optional<ResourceLocation> swing, Optional<ResourceLocation> hit, Optional<ResourceLocation> slam,
                         Optional<ResourceLocation> charge) {
        public static final Codec<Sounds> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.optionalFieldOf("swing").forGetter(Sounds::swing),
                ResourceLocation.CODEC.optionalFieldOf("hit").forGetter(Sounds::hit),
                ResourceLocation.CODEC.optionalFieldOf("slam").forGetter(Sounds::slam),
                ResourceLocation.CODEC.optionalFieldOf("charge").forGetter(Sounds::charge)
        ).apply(i, Sounds::new));
    }
}
